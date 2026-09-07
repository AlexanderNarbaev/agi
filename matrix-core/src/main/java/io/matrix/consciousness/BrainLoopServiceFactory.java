package io.matrix.consciousness;

import java.nio.file.Path;

/**
 * RUN 275 — BrainLoopServiceFactory (factory pattern).
 *
 * <p>Creates BrainLoopService instances from config files.
 */
public final class BrainLoopServiceFactory {

    /** Create a default service. */
    public static BrainLoopService createDefault() {
        return new BrainLoopService();
    }

    /** Create a service from a config file. */
    public static BrainLoopService fromConfig(Path configPath) {
        // For now, just return default
        return createDefault();
    }

    /** Create a service with custom arousal parameters. */
    public static BrainLoopService withArousal(double baseline,
                                                double maxRise,
                                                double decay) {
        var arousal = new ArousalDynamics(decay, maxRise);
        return new BrainLoopServiceBuilder().arousal(arousal).build();
    }
}
