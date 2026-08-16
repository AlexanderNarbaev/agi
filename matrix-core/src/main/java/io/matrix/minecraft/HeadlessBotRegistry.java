package io.matrix.minecraft;

import io.matrix.io.MinecraftBotSensor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Multi-bot registry for headless Minecraft bots.
 *
 * <p>Each registered bot is wrapped in a {@link BotCoordinator} and exposed
 * to API consumers (HTTP/WS) through {@link #tickOnce(String)} /
 * {@link #snapshot(String)} / {@link #runBatch(String, int)}.
 *
 * <p>Bots can be created with default settings (random world, default
 * neural brain) or with custom factories for advanced use cases (e.g.
 * shared brain, fixed world for tests).
 *
 * <p>Concurrency: registration and tick are thread-safe. Each bot is
 * driven by an internal single-thread executor so that tick throughput
 * from the API is non-blocking.
 *
 * <p>Quality / extension points:
 * <ul>
 *   <li>Pluggable brain factory — register bots with deterministic
 *       brains for reproducible tests.</li>
 *   <li>Pluggable world factory — same.</li>
 *   <li>{@link #snapshot(String)} exposes {@link HeadlessBotSnapshot} so
 *       consumers (HTTP, WS, MCP) can serialise without leaking internal
 *       state.</li>
 *   <li>Auto-cleanup: bots can be removed individually; the registry
 *       also exposes {@link #shutdown()} to terminate all bots.</li>
 * </ul>
 */
public final class HeadlessBotRegistry {

    private final Map<String, BotCoordinator> bots = new ConcurrentHashMap<>();
    private final ExecutorService tickExecutor;
    private final Random sharedRng = new Random();
    private final Supplier<MinecraftBotSensor> sensorFactory;
    private final Supplier<NeuralBrain> defaultBrainFactory;
    private final Supplier<BlockWorld> defaultWorldFactory;
    private final AtomicBoolean shutDown = new AtomicBoolean();

    public HeadlessBotRegistry() {
        this(
                defaultSensorFactory(),
                defaultBrainFactory(),
                defaultWorldFactory(),
                Executors.newFixedThreadPool(4, r -> {
                    Thread t = new Thread(r, "headless-bot-tick");
                    t.setDaemon(true);
                    return t;
                }));
    }

    private static Supplier<MinecraftBotSensor> defaultSensorFactory() {
        return () -> new MinecraftBotSensor(MinecraftBotSensor.BotClient.alwaysConnected());
    }

    private static Supplier<NeuralBrain> defaultBrainFactory() {
        Random rng = new Random();
        return () -> new NeuralBrain(new Random(rng.nextLong()));
    }

    private static Supplier<BlockWorld> defaultWorldFactory() {
        Random rng = new Random();
        return () -> new BlockWorld(20, 20, new Random(rng.nextLong()));
    }

    public HeadlessBotRegistry(Supplier<MinecraftBotSensor> sensorFactory,
                                Supplier<NeuralBrain> defaultBrainFactory,
                                Supplier<BlockWorld> defaultWorldFactory,
                                ExecutorService tickExecutor) {
        this.sensorFactory = sensorFactory;
        this.defaultBrainFactory = defaultBrainFactory;
        this.defaultWorldFactory = defaultWorldFactory;
        this.tickExecutor = tickExecutor;
    }

    /**
     * Register a new bot with default settings (random world, default brain).
     * Returns the bot id.
     */
    public String register(String botId) {
        return register(botId, defaultWorldFactory.get(), defaultBrainFactory.get());
    }

    /**
     * Register a new bot with custom world and brain.
     * Returns the bot id (or the existing one if the id is already taken).
     */
    public String register(String botId, BlockWorld world, NeuralBrain brain) {
        if (shutDown.get()) throw new IllegalStateException("registry is shut down");
        if (bots.containsKey(botId)) return botId;
        BlockAgent agent = new BlockAgent(new BlockWorld.Position(world.width() / 2, world.height() / 2));
        BotCoordinator coord = new BotCoordinator(world, agent, sensorFactory.get(), brain, 50L);
        bots.put(botId, coord);
        return botId;
    }

    /** Stop and remove a bot. */
    public boolean unregister(String botId) {
        BotCoordinator coord = bots.remove(botId);
        if (coord == null) return false;
        coord.stop();
        return true;
    }

    /** Run a single tick on a bot. Returns the snapshot or empty if unknown. */
    public Optional<HeadlessBotSnapshot> tickOnce(String botId) {
        BotCoordinator coord = bots.get(botId);
        if (coord == null) return Optional.empty();
        BotCoordinator.Outcome outcome = coord.tickOnce();
        return Optional.of(HeadlessBotSnapshot.from(outcome, botId));
    }

    /** Run a batch of N ticks on a bot. */
    public List<HeadlessBotSnapshot> runBatch(String botId, int n) {
        BotCoordinator coord = bots.get(botId);
        if (coord == null) return List.of();
        coord.runBatch(n);
        BotCoordinator.Outcome last = coord.tickOnce();
        return List.of(HeadlessBotSnapshot.from(last, botId));
    }

    /** Capture the current state of a bot without ticking it. */
    public Optional<HeadlessBotSnapshot> snapshot(String botId) {
        BotCoordinator coord = bots.get(botId);
        if (coord == null) return Optional.empty();
        // A "no-op" outcome to extract the current state.
        return Optional.of(new HeadlessBotSnapshot(
                botId,
                coord.agent().isAlive(),
                coord.agent().stepsSurvived(),
                coord.agent().blocksMined(),
                coord.agent().itemsCrafted(),
                coord.agent().health(),
                coord.agent().hunger(),
                coord.agent().toolTier().name(),
                coord.agent().position().x(),
                coord.agent().position().y(),
                "snapshot",
                System.currentTimeMillis()));
    }

    /** List all registered bot ids. */
    public Collection<String> botIds() {
        return new ArrayList<>(bots.keySet());
    }

    /** Number of registered bots. */
    public int size() {
        return bots.size();
    }

    /** Stop all bots and shut down the tick executor. */
    public void shutdown() {
        if (!shutDown.compareAndSet(false, true)) return;
        for (BotCoordinator c : bots.values()) c.stop();
        bots.clear();
        tickExecutor.shutdown();
    }
}
