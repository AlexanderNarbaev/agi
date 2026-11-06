package io.matrix.security;

import io.quarkus.runtime.Startup;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.util.Locale;
import org.jboss.logging.Logger;

/**
 * The startup guard of the MATRIX application (RECON-W43.0).
 *
 * <p><b>Why this exists, and what it caught.</b> The first version of the
 * boundary guarded the eleven RAW listeners, and the boundary check script
 * proved that a raw listener in network-exposed mode without a credential
 * refuses to start. But the Quarkus application — the one that actually
 * serves the workbench — binds through {@code quarkus.http.host}, and
 * nothing consulted the boundary there. The check script found it: the
 * process started, exposed, without a credential, and the script reported
 * it. The guard was in the code and was not in the path.
 *
 * <p>So the application itself now refuses to start when it has been
 * declared network-exposed and no credential is configured. Declaring the
 * mode is an operator's statement that the process is reachable; a process
 * that is reachable and cannot tell who is asking is not a process that
 * should be running.
 *
 * <p><b>What it reports.</b> The mode, the bind address the application
 * will use, and the reason it stopped — so that the operator can fix the
 * configuration rather than guess at it.
 *
 * <p><b>Why {@code @Startup}.</b> The first version of this class had no
 * such annotation, and the boundary check script found the consequence: a
 * CDI bean with no injection point is created lazily, so the observer never
 * fired and the application started exposed to the network without a
 * credential — the guard was in the code and was not in the path. The
 * annotation forces the bean to exist before the application serves
 * anything.
 */
@ApplicationScoped
@Startup
public class StartupGuard {

    private static final Logger LOG = Logger.getLogger(StartupGuard.class);

    /**
     * Checks the boundary before the application serves anything.
     *
     * @param event the startup event
     */
    void onStart(@Observes StartupEvent event) {
        NetworkExposure exposure = NetworkExposure.current();
        if (!exposure.isNetworkExposed()) {
            LOG.infof("the security boundary: LOCAL_ONLY — the service binds %s and no "
                    + "credential is required", exposure.bindAddress());
            return;
        }
        if (!exposure.hasToken()) {
            throw new IllegalStateException(
                    "refusing to start the MATRIX application exposed to the network without a "
                            + "credential: set " + NetworkExposure.TOKEN_PROPERTY + " (or "
                            + NetworkExposure.TOKEN_ENVIRONMENT + ")");
        }
        LOG.infof("the security boundary: NETWORK_EXPOSED — a credential is configured and every "
                + "state-changing request must present it");
    }

    /**
     * The address the application will bind, for the startup log.
     *
     * @return the address
     */
    public static String configuredHost() {
        String host = System.getProperty("quarkus.http.host");
        if (host == null || host.isBlank()) {
            host = System.getenv("QUARKUS_HTTP_HOST");
        }
        return host == null || host.isBlank()
                ? NetworkExposure.LOOPBACK
                : host.strip().toLowerCase(Locale.ROOT);
    }
}
