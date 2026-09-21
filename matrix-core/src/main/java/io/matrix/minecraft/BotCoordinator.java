package io.matrix.minecraft;

import io.matrix.io.MinecraftBotSensor;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Headless Minecraft bot coordinator — bridges {@link BlockAgent} (in-process
 * agent) to the matrix-core I/O subsystem (specifically
 * {@link MinecraftBotSensor}) and surfaces a single {@link Outcome}
 * record per tick.
 *
 * <p>This is a quality-first, fast-to-implement headless bot simulator that:
 * <ol>
 *   <li>Runs a {@link BlockAgent} in a {@link BlockWorld} (no Minecraft server).</li>
 *   <li>Emits {@link MinecraftBotSensor.BotEvent} through the sensor so any
 *       consumer (AgentLoop, observability, FROZEN FNL gate) can react.</li>
 *   <li>Calls a pluggable action consumer for monitoring.</li>
 * </ol>
 *
 * <p>Designed for:
 * <ul>
 *   <li>Unit/integration tests — no Minecraft server required.</li>
 *   <li>Smoke benchmarks — evaluate neural brain on realistic agent loops.</li>
 *   <li>Real-bridge prototype — same API, swap in a {@code MatrixCoreClient}
 *       to talk to a real {@code matrix-spigot} instance.</li>
 * </ul>
 *
 * <p>Ref: L25 §3.3, L13 §4 (Minecraft pilot).
 */
public final class BotCoordinator {

    private final BlockWorld world;
    private final BlockAgent agent;
    private final MinecraftBotSensor sensor;
    private final NeuralBrain brain;
    private final long tickIntervalMs;
    private final AtomicLong lastTickMs = new AtomicLong();
    private final AtomicBoolean running = new AtomicBoolean();
    private Thread loopThread;
    private Consumer<Outcome> actionSink = o -> {};
    private volatile Runnable onDeath;
    private volatile int maxTicks = 1000;

    public BotCoordinator(BlockWorld world, BlockAgent agent,
                           MinecraftBotSensor sensor, NeuralBrain brain,
                           long tickIntervalMs) {
        this.world = world;
        this.agent = agent;
        this.sensor = sensor;
        this.brain = brain;
        this.tickIntervalMs = tickIntervalMs;
    }

    public BotCoordinator withActionSink(Consumer<Outcome> sink) {
        this.actionSink = sink == null ? o -> {} : sink;
        return this;
    }

    public BotCoordinator onDeath(Runnable hook) {
        this.onDeath = hook;
        return this;
    }

    public BotCoordinator withMaxTicks(int maxTicks) {
        this.maxTicks = Math.max(1, maxTicks);
        return this;
    }

    /** Start the bot tick loop. */
    public void start() {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("BotCoordinator already running");
        }
        loopThread = new Thread(this::loop, "matrix-bot");
        loopThread.setDaemon(true);
        loopThread.start();
    }

    /** Stop the bot tick loop (idempotent). */
    public void stop() {
        running.set(false);
        if (loopThread != null) {
            try {
                loopThread.join(2000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public boolean isRunning() { return running.get(); }

    public BlockAgent agent() { return agent; }
    public BlockWorld world() { return world; }
    public NeuralBrain brain() { return brain; }

    /** Single manual tick (testable without a thread). */
    public Outcome tickOnce() {
        if (!agent.isAlive()) {
            return Outcome.dead(agent);
        }
        long sensors = agent.encodeSensors(world);
        sensor.enqueue(new MinecraftBotSensor.BotEvent(
                pickKind(sensors), agent.position().x(), agent.position().y(),
                world.height() / 2, 1.0, "tick-" + agent.stepsSurvived()));
        BlockAgent.Action decision = brain.act(sensors);
        String label = performAction(decision);
        Outcome outcome = new Outcome(true, agent, decision, label);
        actionSink.accept(outcome);
        if (!agent.isAlive() && onDeath != null) onDeath.run();
        return outcome;
    }

    /** Run N ticks in the calling thread. */
    public int runBatch(int n) {
        int count = 0;
        for (int i = 0; i < n && agent.isAlive(); i++) {
            tickOnce();
            count++;
            if (count >= maxTicks) break;
        }
        return count;
    }

    private MinecraftBotSensor.BotEvent.Kind pickKind(long sensors) {
        // "block ahead is solid" (bit 9) → block_changed; otherwise movement.
        return ((sensors & (1L << 9)) != 0)
                ? MinecraftBotSensor.BotEvent.Kind.BLOCK_CHANGED
                : MinecraftBotSensor.BotEvent.Kind.MOVED;
    }

    private void loop() {
        int ticks = 0;
        while (running.get() && agent.isAlive() && ticks < maxTicks) {
            long now = System.currentTimeMillis();
            if (now - lastTickMs.get() >= tickIntervalMs) {
                try {
                    tickOnce();
                } catch (RuntimeException re) {
                    // Don't let a transient error kill the loop.
                }
                lastTickMs.set(now);
                ticks++;
            }
            try {
                Thread.sleep(Math.max(1, tickIntervalMs));
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /** Apply a brain decision to the world, returning a human-readable label. */
    private String performAction(BlockAgent.Action decision) {
        if (decision == null) return "no-op";
        if (decision instanceof BlockAgent.Action.Move move) {
            BlockWorld.Position next = moveTarget(move.direction());
            if (world.isSolid(next.x(), next.y())) {
                return "move-blocked-" + move.direction();
            }
            agent.move(next);
            return "move-" + move.direction();
        }
        if (decision instanceof BlockAgent.Action.Mine) {
            BlockWorld.Position p = agent.position();
            BlockWorld.Position target = new BlockWorld.Position(p.x(), p.y() - 1);
            BlockType block = world.get(target.x(), target.y());
            if (block == BlockType.AIR || !block.mineable()) {
                return "mine-fail";
            }
            agent.mineBlock(world, target);
            world.set(target.x(), target.y(), BlockType.AIR);
            return "mine-ok-" + block;
        }
        if (decision instanceof BlockAgent.Action.Eat) {
            agent.eat();
            return "eat";
        }
        if (decision instanceof BlockAgent.Action.Craft) {
            // The agent's upgradeTool handles pickaxe crafting. For other recipes,
            // we just record the attempt and let the brain learn.
            agent.upgradeTool("WOODEN_PICKAXE");
            return "craft-attempt";
        }
        return "unknown";
    }

    private BlockWorld.Position moveTarget(BlockAgent.Direction d) {
        BlockWorld.Position p = agent.position();
        return switch (d) {
            case N -> new BlockWorld.Position(p.x(), p.y() - 1);
            case S -> new BlockWorld.Position(p.x(), p.y() + 1);
            case W -> new BlockWorld.Position(p.x() - 1, p.y());
            case E -> new BlockWorld.Position(p.x() + 1, p.y());
            case STAY -> p;
        };
    }

    // ── Result record ──

    /** Per-tick outcome surfaced to the action sink. */
    public record Outcome(boolean alive, BlockAgent agent, BlockAgent.Action action, String label) {
        public static Outcome dead(BlockAgent a) {
            return new Outcome(false, a, null, "agent-dead");
        }
        public String summary() {
            return "tick=" + agent.stepsSurvived() + " action=" + label
                    + " alive=" + alive + " health=" + agent.health() + "/20"
                    + " hunger=" + agent.hunger() + "/20"
                    + " tool=" + agent.toolTier();
        }
    }
}
