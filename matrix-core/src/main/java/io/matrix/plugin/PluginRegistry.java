package io.matrix.plugin;

import io.matrix.contract.Plugin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * W51 — the registry of the plugins the runtime hosts, and of their lifecycle.
 *
 * <p><b>What this replaces.</b> Nothing. The W51 reconnaissance established that MATRIX has
 * no plugin architecture, and that this is a decision the codebase has made explicitly rather
 * than an unfinished implementation: no {@code ServiceLoader} call, no
 * {@code META-INF/services} file, no extension point, no classpath scanning, and no
 * sandbox-capable dependency in any of the sixteen build files. What exists is a family of
 * in-process registries populated by hard-coded constructors inside their own modules. This
 * class is the first of them that can be extended from outside, and it is the one that can
 * give back what it took.</p>
 *
 * <p><b>The registry owns the lifecycle, and it enforces it.</b> A stage change goes through
 * {@link #transition(String, Plugin.Stage, String, String)}, which consults
 * {@link PluginLifecycle} and refuses an illegal move. The refusal is recorded in the audit
 * with its reason, because "the plugin did not move" and "the plugin was never asked" must
 * look different in the record.</p>
 *
 * <p><b>Unloading removes everything.</b> {@link #unload(String, String, String)} moves the
 * plugin to {@link Plugin.Stage#REVOKED} and removes every service and every handler it
 * registered, and it returns what it removed. The W51 mandate is explicit — "a plugin must be
 * removable without leaving registered services, handlers, processes or resources behind" — and
 * a removal that cannot be checked is a promise rather than a guarantee. The test asserts on
 * the counts.</p>
 *
 * <p><b>The registry grants nothing.</b> It holds declarations; it does not decide what a
 * plugin may do. The grant lives in the plugin's own record
 * ({@link Plugin#grantedCapabilities()}), and the runtime checks it at the call.</p>
 *
 * <p><b>Determinism.</b> The registry keeps its plugins in registration order, and the clock
 * is a parameter of every write rather than a call to the system clock.</p>
 */
public final class PluginRegistry {

    private final Map<String, Plugin> plugins = new LinkedHashMap<>();
    private final PluginServiceRegistry services;
    private final PluginAudit audit;

    /**
     * Creates a registry.
     *
     * @param audit where the lifecycle decisions are recorded
     */
    public PluginRegistry(PluginAudit audit) {
        if (audit == null) {
            throw new IllegalArgumentException("audit must not be null");
        }
        this.audit = audit;
        this.services = new PluginServiceRegistry(this);
    }

    /** The registry of the services and handlers the plugins registered. */
    public PluginServiceRegistry services() {
        return services;
    }

    /** The append-only record of the lifecycle decisions. */
    public PluginAudit audit() {
        return audit;
    }

    /**
     * Registers a plugin in the {@link Plugin.Stage#PROPOSED} stage.
     *
     * @param plugin the declaration
     * @param at     when the registration happened
     * @return the registered plugin
     * @throws IllegalArgumentException when a plugin with the same identity is already
     *                                  registered
     */
    public synchronized Plugin register(Plugin plugin, String at) {
        if (plugin == null) {
            throw new IllegalArgumentException("plugin must not be null");
        }
        if (plugins.containsKey(plugin.id())) {
            throw new IllegalArgumentException("the plugin " + plugin.id() + " is already registered");
        }
        Plugin registered = plugin.stage() == Plugin.Stage.PROPOSED ? plugin : plugin.at(Plugin.Stage.PROPOSED);
        plugins.put(registered.id(), registered);
        audit.record(registered.id(), "REGISTER", PluginAuditRecord.OUTCOME_APPLIED,
                "the plugin was registered in the stage " + registered.stage(), at);
        return registered;
    }

    /**
     * The plugin with the given identity.
     *
     * @param pluginId the identity of the plugin
     * @return the plugin, when it is registered
     */
    public synchronized Plugin plugin(String pluginId) {
        return plugins.get(pluginId);
    }

    /**
     * The stage a plugin is in.
     *
     * @param pluginId the identity of the plugin
     * @return the stage, or {@code null} when the plugin is not registered
     */
    public synchronized Plugin.Stage stageOf(String pluginId) {
        Plugin plugin = plugins.get(pluginId);
        return plugin == null ? null : plugin.stage();
    }

    /**
     * Moves a plugin to another stage.
     *
     * @param pluginId the identity of the plugin
     * @param to       the stage to move to
     * @param reason   why
     * @param at       when the move happened
     * @return the plugin in its new stage
     * @throws IllegalStateException when the plugin is unknown or the move is illegal
     */
    public synchronized Plugin transition(String pluginId, Plugin.Stage to, String reason, String at) {
        Plugin plugin = plugins.get(pluginId);
        if (plugin == null) {
            audit.record(pluginId, "TRANSITION", PluginAuditRecord.OUTCOME_REFUSED,
                    "the plugin is not registered", at);
            throw new IllegalStateException("the plugin " + pluginId + " is not registered");
        }
        String refusal = PluginLifecycle.refusal(plugin.stage(), to);
        if (refusal != null) {
            audit.record(pluginId, "TRANSITION", PluginAuditRecord.OUTCOME_REFUSED, refusal, at);
            throw new IllegalStateException(refusal);
        }
        Plugin moved = plugin.at(to);
        plugins.put(pluginId, moved);
        audit.record(pluginId, "TRANSITION", PluginAuditRecord.OUTCOME_APPLIED,
                "the plugin moved to " + to + (reason == null || reason.isBlank() ? "" : ": " + reason),
                at);
        return moved;
    }

    /**
     * Records the capabilities the runtime granted a plugin.
     *
     * <p>The grant is the runtime's decision and it is recorded here, not in the plugin: the
     * plugin declares what it needs, the runtime says what it gets, and the two are kept
     * apart so that a reader can see the difference between a request and a grant.</p>
     *
     * @param pluginId     the identity of the plugin
     * @param capabilities the capabilities the runtime granted
     * @param reason       why
     * @param at           when the grant was made
     * @return the plugin with the grant
     * @throws IllegalStateException when the plugin is unknown
     */
    public synchronized Plugin grant(String pluginId, List<String> capabilities, String reason,
                                     String at) {
        Plugin plugin = plugins.get(pluginId);
        if (plugin == null) {
            audit.record(pluginId, "GRANT", PluginAuditRecord.OUTCOME_REFUSED,
                    "the plugin is not registered", at);
            throw new IllegalStateException("the plugin " + pluginId + " is not registered");
        }
        Plugin granted = plugin.granting(capabilities == null ? List.of() : capabilities);
        plugins.put(pluginId, granted);
        audit.record(pluginId, "GRANT", PluginAuditRecord.OUTCOME_APPLIED,
                "granted " + granted.grantedCapabilities()
                        + (reason == null || reason.isBlank() ? "" : ": " + reason), at);
        return granted;
    }

    /**
     * Removes a plugin and everything it registered.
     *
     * <p>The plugin is moved to {@link Plugin.Stage#REVOKED} first, so that a handler which is
     * still running cannot register something after its own removal.</p>
     *
     * @param pluginId the identity of the plugin
     * @param reason   why the plugin is being removed
     * @param at       when the removal happened
     * @return what was removed
     * @throws IllegalStateException when the plugin is unknown or cannot be revoked
     */
    public synchronized PluginServiceRegistry.Removal unload(String pluginId, String reason,
                                                              String at) {
        Plugin plugin = plugins.get(pluginId);
        if (plugin == null) {
            audit.record(pluginId, "UNLOAD", PluginAuditRecord.OUTCOME_REFUSED,
                    "the plugin is not registered", at);
            throw new IllegalStateException("the plugin " + pluginId + " is not registered");
        }
        if (plugin.stage() != Plugin.Stage.REVOKED && plugin.stage() != Plugin.Stage.RETIRED) {
            String refusal = PluginLifecycle.refusal(plugin.stage(), Plugin.Stage.REVOKED);
            if (refusal != null) {
                audit.record(pluginId, "UNLOAD", PluginAuditRecord.OUTCOME_REFUSED, refusal, at);
                throw new IllegalStateException(refusal);
            }
            plugins.put(pluginId, plugin.at(Plugin.Stage.REVOKED));
            audit.record(pluginId, "REVOKE", PluginAuditRecord.OUTCOME_APPLIED,
                    reason == null || reason.isBlank() ? "the plugin was revoked" : reason, at);
        }
        PluginServiceRegistry.Removal removal = services.removeAll(pluginId);
        audit.record(pluginId, "UNLOAD", PluginAuditRecord.OUTCOME_APPLIED,
                "removed " + removal.services().size() + " service(s) and "
                        + removal.handlers().size() + " handler(s)", at);
        return removal;
    }

    /** Every registered plugin, in registration order. */
    public synchronized List<Plugin> all() {
        return List.copyOf(plugins.values());
    }

    /** The plugins whose results are used. */
    public synchronized List<Plugin> serving() {
        return plugins.values().stream().filter(Plugin::serving).toList();
    }

    /** The number of registered plugins. */
    public synchronized int size() {
        return plugins.size();
    }
}
