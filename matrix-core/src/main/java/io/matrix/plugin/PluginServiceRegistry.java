package io.matrix.plugin;

import io.matrix.contract.Plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * W51 — the services and event handlers the plugins registered, and who owns them.
 *
 * <p><b>What this replaces.</b> The repository's existing registries — {@code ActionRegistry},
 * {@code AlgorithmRegistry}, {@code SignalModuleRegistry}, {@code DocumentExtractorRegistry},
 * the {@code Tool} contract — all have the same shape: an interface, a {@code register()} and a
 * duplicate-refusal rule. None of them records WHO registered what, and therefore none of
 * them can clean up after a plugin. That is the W51 requirement "a plugin must be removable
 * without leaving registered services, handlers, processes or resources behind" made
 * concrete: removal needs ownership.</p>
 *
 * <p><b>Ownership is by plugin identity.</b> Every registration names the plugin that made it.
 * {@link #removeAll(String)} returns exactly what was removed — the names of the services and
 * the handlers — so that the caller can record that the removal happened, and so that a test
 * can assert that nothing was left behind.</p>
 *
 * <p><b>A plugin that is not registered cannot register anything.</b> The registry refuses a
 * registration from an unknown plugin, because a registration nobody owns is exactly the thing
 * that cannot be cleaned up later.</p>
 *
 * <p><b>The registry does not execute anything.</b> It holds references and names them; who
 * calls a service is the runtime's business. Keeping the registry free of invocation means a
 * plugin cannot cause a call by being registered, and the capability check happens at the
 * call site rather than here.</p>
 */
public final class PluginServiceRegistry {

    /**
     * A handler of an event.
     *
     * @param eventType the type of the event it handles
     * @param handler   what it does with the event
     */
    public record EventHandler(String eventType, Function<Object, Object> handler) {
        public EventHandler {
            if (eventType == null || eventType.isBlank()) {
                throw new IllegalArgumentException("event type must not be blank");
            }
            if (handler == null) {
                throw new IllegalArgumentException("event handler must not be null");
            }
        }
    }

    /**
     * What a removal took away.
     *
     * @param services  the names of the services that were removed
     * @param handlers  the event types whose handlers were removed
     */
    public record Removal(List<String> services, List<String> handlers) {
        public Removal {
            services = services == null ? List.of() : List.copyOf(services);
            handlers = handlers == null ? List.of() : List.copyOf(handlers);
        }

        /** True when nothing was left behind. */
        public boolean clean() {
            return services.isEmpty() && handlers.isEmpty();
        }
    }

    private final Map<String, Map<String, Object>> services = new LinkedHashMap<>();
    private final Map<String, Map<String, EventHandler>> handlers = new LinkedHashMap<>();
    private final PluginRegistry plugins;

    /**
     * Creates a registry.
     *
     * @param plugins the registry that decides which plugins exist
     */
    public PluginServiceRegistry(PluginRegistry plugins) {
        this.plugins = plugins;
    }

    /**
     * Registers a service on behalf of a plugin.
     *
     * @param pluginId the plugin that owns the service
     * @param name     the name of the service
     * @param service  the service
     * @throws IllegalArgumentException when the plugin is unknown or the name is taken
     */
    public synchronized void registerService(String pluginId, String name, Object service) {
        requirePlugin(pluginId);
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("service name must not be blank");
        }
        if (service == null) {
            throw new IllegalArgumentException("service must not be null");
        }
        Map<String, Object> owned = services.computeIfAbsent(pluginId, k -> new LinkedHashMap<>());
        if (owned.containsKey(name)) {
            throw new IllegalArgumentException("the service " + name + " is already registered");
        }
        owned.put(name, service);
    }

    /**
     * Registers an event handler on behalf of a plugin.
     *
     * @param pluginId  the plugin that owns the handler
     * @param eventType the type of the event
     * @param handler   the handler
     * @throws IllegalArgumentException when the plugin is unknown or the event type is taken
     */
    public synchronized void registerHandler(String pluginId, String eventType,
                                             Function<Object, Object> handler) {
        requirePlugin(pluginId);
        EventHandler record = new EventHandler(eventType, handler);
        Map<String, EventHandler> owned =
                handlers.computeIfAbsent(pluginId, k -> new LinkedHashMap<>());
        if (owned.containsKey(eventType)) {
            throw new IllegalArgumentException("a handler for " + eventType + " is already registered");
        }
        owned.put(eventType, record);
    }

    /**
     * The service with the given name.
     *
     * @param name the name of the service
     * @return the service, when it is registered
     */
    /**
     * Calls a registered service, and refuses when its owner may not.
     *
     * <p><b>Why this method exists.</b> The registry used to hand out a service to anyone
     * who asked, and the capability of the owning plugin was checked only at activation. A
     * grant can be narrowed afterwards — a plugin that held a capability when it was
     * activated may not hold it now — and a narrowing that is not enforced at the call is
     * not a narrowing at all. This is the enforcement point: the grant is consulted on
     * EVERY call, not once.</p>
     *
     * <p>A refusal is a refusal, not an exception: the caller receives an empty optional and
     * the reason, and the service is not invoked.</p>
     *
     * @param name       the name of the service
     * @param capability the capability the call needs
     * @param call       what to do with the service
     * @param at         when the call was made, recorded in the audit
     * @return the result of the call, or an empty optional when the call was refused
     */
    public synchronized <T> Optional<T> invoke(String name, String capability,
                                                Function<Object, T> call, String at) {
        if (call == null) {
            throw new IllegalArgumentException("the call must not be null");
        }
        Optional<Object> service = service(name);
        if (service.isEmpty()) {
            return Optional.empty();
        }
        String pluginId = ownerOfService(name).orElse(null);
        if (pluginId == null) {
            return Optional.empty();
        }
        Plugin owner = plugins.plugin(pluginId);
        if (owner == null) {
            return Optional.empty();
        }
        if (!owner.holds(capability)) {
            plugins.audit().record(pluginId, "CALL", PluginAuditRecord.OUTCOME_REFUSED,
                    "the call to " + name + " needed the capability " + capability
                            + ", which the plugin does not hold", at);
            return Optional.empty();
        }
        if (!owner.serving()) {
            plugins.audit().record(pluginId, "CALL", PluginAuditRecord.OUTCOME_REFUSED,
                    "the call to " + name + " was refused: the plugin is in the stage "
                            + owner.stage() + " and does not serve", at);
            return Optional.empty();
        }
        return Optional.ofNullable(call.apply(service.get()));
    }

    public synchronized Optional<Object> service(String name) {
        for (Map<String, Object> owned : services.values()) {
            Object service = owned.get(name);
            if (service != null) {
                return Optional.of(service);
            }
        }
        return Optional.empty();
    }

    /**
     * The handlers of the given event type.
     *
     * @param eventType the type of the event
     * @return the handlers, in registration order
     */
    public synchronized List<EventHandler> handlersOf(String eventType) {
        List<EventHandler> found = new ArrayList<>();
        for (Map<String, EventHandler> owned : handlers.values()) {
            EventHandler handler = owned.get(eventType);
            if (handler != null) {
                found.add(handler);
            }
        }
        return List.copyOf(found);
    }

    /**
     * The plugin that owns a service.
     *
     * @param name the name of the service
     * @return the owning plugin, when the service is registered
     */
    public synchronized Optional<String> ownerOfService(String name) {
        for (Map.Entry<String, Map<String, Object>> entry : services.entrySet()) {
            if (entry.getValue().containsKey(name)) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    /**
     * Removes everything a plugin registered.
     *
     * @param pluginId the plugin whose registrations are removed
     * @return what was removed
     */
    public synchronized Removal removeAll(String pluginId) {
        Map<String, Object> ownedServices = services.remove(pluginId);
        Map<String, EventHandler> ownedHandlers = handlers.remove(pluginId);
        List<String> serviceNames = ownedServices == null ? List.of() : List.copyOf(ownedServices.keySet());
        List<String> eventTypes = ownedHandlers == null ? List.of() : List.copyOf(ownedHandlers.keySet());
        return new Removal(serviceNames, eventTypes);
    }

    /** The number of registered services. */
    public synchronized int serviceCount() {
        return services.values().stream().mapToInt(Map::size).sum();
    }

    /** The number of registered handlers. */
    public synchronized int handlerCount() {
        return handlers.values().stream().mapToInt(Map::size).sum();
    }

    private void requirePlugin(String pluginId) {
        if (plugins == null || plugins.plugin(pluginId) == null) {
            throw new IllegalArgumentException("the plugin " + pluginId + " is not registered");
        }
    }
}
