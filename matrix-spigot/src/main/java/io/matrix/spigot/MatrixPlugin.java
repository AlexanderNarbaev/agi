package io.matrix.spigot;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * MATRIX Spigot Plugin — bridges matrix-core neural inference
 * with a real Minecraft server via HTTP/WebSocket.
 *
 * <p>Every tick:
 * <ol>
 *   <li>Read Minecraft world state (blocks, inventory, health) as a 20-bit sensor vector</li>
 *   <li>Send sensors to matrix-core via WebSocket for neural inference</li>
 *   <li>Receive action string asynchronously via {@link MatrixCoreClient.ActionCallback}</li>
 *   <li>Execute the action via Bukkit API on the main server thread</li>
 * </ol>
 *
 * <p>Commands:
 * <pre>
 * /matrix connect  — connect to matrix-core
 * /matrix start    — spawn and start the bot
 * /matrix stop     — stop the bot
 * /matrix status   — show bot state and connection status
 * /matrix train [gens] [pop] [k] — run GA training via REST
 * /matrix save     — save brain state via REST
 * </pre>
 *
 * <p>If matrix-core is unreachable, the plugin falls back to a simple
 * random action generator so the bot still does something.
 */
public class MatrixPlugin extends JavaPlugin {

    private final Random rng = new Random();

    private MatrixCoreClient client;
    private Player botPlayer;
    private boolean running;
    private int tickCount;
    private int blocksMined;

    /** Last action received from matrix-core, or generated locally in fallback mode. */
    private volatile String lastAction = "STAY";

    private BukkitRunnable botTask;

    // --- Config values ---
    private String matrixCoreUrl = "http://localhost:9091";
    private String agentId = "MatrixBot1";
    private int tickInterval = 20;
    private boolean autoConnect = true;

    private static final String[] ALL_ACTIONS = {
        "MOVE_N", "MOVE_S", "MOVE_W", "MOVE_E",
        "MINE", "EAT", "STAY"
    };

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigValues();

        getLogger().info("MATRIX Neural Plugin v2.2.0 enabled");
        getLogger().info("Matrix-core URL: " + matrixCoreUrl);

        client = new MatrixCoreClient(matrixCoreUrl);

        if (autoConnect) {
            connectToCore();
        } else {
            getLogger().info("Auto-connect disabled. Use /matrix connect to connect.");
        }

        registerCommands();
    }

    @Override
    public void onDisable() {
        stopBot();
        if (client != null) {
            client.stop();
            client.shutdown();
        }
        getLogger().info("MATRIX Neural Plugin disabled");
    }

    // ─────────────────── Configuration ───────────────────

    private void loadConfigValues() {
        matrixCoreUrl = getConfig().getString("matrix-core-url", "http://localhost:9091");
        agentId = getConfig().getString("agent-id", "MatrixBot1");
        tickInterval = getConfig().getInt("tick-interval", 20);
        autoConnect = getConfig().getBoolean("auto-connect", true);
    }

    // ─────────────────── Connection ───────────────────

    private void connectToCore() {
        getLogger().info("Connecting to matrix-core at " + matrixCoreUrl + "...");

        client.connect(new MatrixCoreClient.ActionCallback() {
            @Override
            public void onAction(String action) {
                lastAction = action;
                if (tickCount % 100 == 0) {
                    getLogger().fine("Action from server: " + action);
                }
            }

            @Override
            public void onStatus(String status) {
                getLogger().info("Matrix status: " + status);
            }

            @Override
            public void onError(String error) {
                getLogger().warning("Matrix error: " + error);
            }
        });
    }

    // ─────────────────── Commands ───────────────────

    private void registerCommands() {
        getCommand("matrix").setExecutor((sender, cmd, label, args) -> {
            if (args.length == 0) {
                sender.sendMessage("Usage: /matrix <connect|start|stop|status|train|save>");
                return true;
            }
            switch (args[0].toLowerCase()) {
                case "connect" -> {
                    connectToCore();
                    sender.sendMessage("Connecting to matrix-core...");
                }
                case "start" -> startBot();
                case "stop" -> stopBot();
                case "status" -> showStatus(sender instanceof Player p ? p : null);
                case "train" -> {
                    int gens = args.length > 1 ? Integer.parseInt(args[1]) : 100;
                    int pop = args.length > 2 ? Integer.parseInt(args[2]) : 50;
                    int k = args.length > 3 ? Integer.parseInt(args[3]) : 20;
                    trainRemotely(gens, pop, k);
                    sender.sendMessage("Training started (async): " + gens + " gen, " + pop + " pop, k=" + k);
                }
                case "save" -> {
                    client.save();
                    sender.sendMessage("Saving brain...");
                }
                default -> sender.sendMessage("Usage: /matrix <connect|start|stop|status|train|save>");
            }
            return true;
        });
    }

    // ─────────────────── Bot Lifecycle ───────────────────

    private void startBot() {
        if (running) {
            getLogger().info("Bot already running");
            return;
        }

        botPlayer = getServer().getOnlinePlayers().stream().findFirst().orElse(null);
        if (botPlayer == null) {
            getLogger().warning("No players online — bot needs a player to observe");
            return;
        }

        running = true;
        tickCount = 0;
        blocksMined = 0;
        lastAction = "STAY";

        if (client.isConnected()) {
            client.start(agentId);
        } else {
            getLogger().warning("Matrix-core not connected — using fallback random actions");
        }

        botTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!running || botPlayer == null || !botPlayer.isOnline()) {
                    stopBot();
                    return;
                }
                tick();
            }
        };
        botTask.runTaskTimer(this, 0L, tickInterval);
        getLogger().info("Bot started. Controlling: " + botPlayer.getName()
                + " (tick interval: " + tickInterval + " ticks)");
    }

    private void stopBot() {
        running = false;
        if (botTask != null) {
            botTask.cancel();
            botTask = null;
        }
        if (client != null && client.isConnected()) {
            client.stop();
        }
        getLogger().info("Bot stopped. Ticks: " + tickCount + ", mined: " + blocksMined);
    }

    private void showStatus(Player sender) {
        String msg = String.format(
                "MATRIX Bot: running=%s, ticks=%d, mined=%d, connected=%s, lastAction=%s",
                running, tickCount, blocksMined,
                client.isConnected() ? "yes" : "no",
                lastAction);
        getLogger().info(msg);
        if (sender != null) sender.sendMessage(msg);
    }

    private void trainRemotely(int generations, int population, int k) {
        getLogger().info("Starting remote GA training: gen=" + generations
                + " pop=" + population + " k=" + k);
        client.train(generations, population, k)
                .orTimeout(120, TimeUnit.SECONDS)
                .thenAccept(body -> getLogger().info("Training complete: " + truncate(body, 200)))
                .exceptionally(ex -> {
                    getLogger().warning("Training failed: " + ex.getMessage());
                    return null;
                });
    }

    // ─────────────────── Tick Logic ───────────────────

    /**
     * Main tick: read sensors → send to matrix-core → execute last known action.
     *
     * <p>Actions come back asynchronously via the WebSocket callback.
     * We always execute the most recent action received.
     * In fallback mode, we generate a random action locally.
     */
    private void tick() {
        tickCount++;
        long tickStart = System.currentTimeMillis();
        long sensors = readSensors();

        if (client.isConnected()) {
            client.sendSensors(sensors);
        } else {
            // Fallback: generate a random action locally
            if (tickCount % 5 == 0) {
                lastAction = ALL_ACTIONS[rng.nextInt(ALL_ACTIONS.length)];
            }
        }

        executeAction(lastAction);

        long tickDuration = System.currentTimeMillis() - tickStart;

        if (tickCount % 100 == 0) {
            getLogger().info(String.format(
                    "MATRIX tick=%d mined=%d health=%d hunger=%d tool=%d action=%s duration=%dms connected=%s",
                    tickCount, blocksMined,
                    botPlayer != null ? (int) botPlayer.getHealth() : 0,
                    botPlayer != null ? botPlayer.getFoodLevel() : 0,
                    detectToolTier(),
                    lastAction,
                    tickDuration,
                    client.isConnected() ? "yes" : "no"));
        }
    }

    // ─────────────────── Sensor Reading ───────────────────

    /**
     * Reads Minecraft world state as a 20-bit sensor vector.
     *
     * <p>Bits 0-8:   5x5 vision grid (solid blocks, center excluded)
     * <br>Bit 9:      block ahead is solid
     * <br>Bits 10-12: health quartile (0-4)
     * <br>Bits 13-15: hunger quartile (0-4)
     * <br>Bits 16-18: tool tier (0-4)
     * <br>Bit 19:     has food in inventory
     */
    private long readSensors() {
        long bits = 0;
        Location loc = botPlayer.getLocation();
        World world = loc.getWorld();
        int px = loc.getBlockX();
        int py = loc.getBlockY();
        int pz = loc.getBlockZ();

        int bitIndex = 0;
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                if (dx == 0 && dz == 0) continue;
                Block block = world.getBlockAt(px + dx, py, pz + dz);
                if (block.getType().isSolid()) {
                    bits |= (1L << bitIndex);
                }
                bitIndex++;
            }
        }

        Block ahead = world.getBlockAt(px, py, pz - 1);
        if (ahead.getType().isSolid()) bits |= (1L << 9);

        double healthRatio = botPlayer.getHealth() / 20.0;
        int healthQ = Math.min(4, (int) (healthRatio * 5));
        for (int i = 0; i < 3; i++) {
            if ((healthQ & (1 << i)) != 0) bits |= (1L << (10 + i));
        }

        int foodQ = Math.min(4, botPlayer.getFoodLevel() * 5 / 20);
        for (int i = 0; i < 3; i++) {
            if ((foodQ & (1 << i)) != 0) bits |= (1L << (13 + i));
        }

        int toolBits = detectToolTier();
        for (int i = 0; i < 3; i++) {
            if ((toolBits & (1 << i)) != 0) bits |= (1L << (16 + i));
        }

        if (botPlayer.getInventory().contains(Material.BREAD)) {
            bits |= (1L << 19);
        }

        return bits;
    }

    private int detectToolTier() {
        PlayerInventory inv = botPlayer.getInventory();
        Material mainHand = inv.getItemInMainHand().getType();
        if (mainHand == Material.DIAMOND_PICKAXE) return 4;
        if (mainHand == Material.IRON_PICKAXE) return 3;
        if (mainHand == Material.STONE_PICKAXE) return 2;
        if (mainHand == Material.WOODEN_PICKAXE) return 1;
        return 0;
    }

    // ─────────────────── Action Execution ───────────────────

    /**
     * Executes a string action in the Minecraft world.
     *
     * <p>Action mapping:
     * <ul>
     *   <li>MOVE_N / MOVE_S / MOVE_W / MOVE_E → teleport in that direction</li>
     *   <li>MINE → break block below the player</li>
     *   <li>EAT → switch to and eat food from inventory</li>
     *   <li>CRAFT → log only (crafting is complex in real Minecraft)</li>
     *   <li>TOOL_UP → switch to best pickaxe in inventory</li>
     *   <li>STAY → do nothing</li>
     * </ul>
     */
    private void executeAction(String action) {
        if (action == null) action = "STAY";

        switch (action.toUpperCase()) {
            case "MOVE_N" -> movePlayer(0, -1);
            case "MOVE_S" -> movePlayer(0, 1);
            case "MOVE_W" -> movePlayer(-1, 0);
            case "MOVE_E" -> movePlayer(1, 0);
            case "MINE" -> mineBlock();
            case "EAT" -> eatFood();
            case "CRAFT" -> {
                if (tickCount % 100 == 0) {
                    getLogger().info("CRAFT action received — crafting via matrix-core (not implemented locally)");
                }
            }
            case "TOOL_UP" -> switchToBestTool();
            case "STAY" -> { /* do nothing */ }
            default -> getLogger().fine("Unknown action: " + action);
        }
    }

    private void movePlayer(double dx, double dz) {
        Location loc = botPlayer.getLocation();
        Location target = loc.clone().add(dx, 0, dz);

        // Set yaw to face movement direction
        if (dz < 0) loc.setYaw(0);       // North
        else if (dz > 0) loc.setYaw(180); // South
        else if (dx < 0) loc.setYaw(90);  // West
        else if (dx > 0) loc.setYaw(-90); // East

        if (!target.getBlock().getType().isSolid()) {
            getServer().getScheduler().runTask(this,
                    () -> botPlayer.teleport(target));
        }
    }

    private void mineBlock() {
        Location loc = botPlayer.getLocation();
        Block target = loc.clone().add(0, -1, 0).getBlock();
        if (target.getType() != Material.AIR && target.getType() != Material.BEDROCK) {
            getServer().getScheduler().runTask(this, () -> {
                target.breakNaturally(botPlayer.getInventory().getItemInMainHand());
                blocksMined++;
            });
        }
    }

    private void eatFood() {
        getServer().getScheduler().runTask(this, () -> {
            for (ItemStack item : botPlayer.getInventory().getContents()) {
                if (item != null && item.getType().isEdible()) {
                    botPlayer.getInventory().setItemInMainHand(item);
                    // Attempt to eat by right-clicking
                    botPlayer.getInventory().getItemInMainHand();
                    break;
                }
            }
        });
    }

    private void switchToBestTool() {
        getServer().getScheduler().runTask(this, () -> {
            PlayerInventory inv = botPlayer.getInventory();
            Material best = null;
            int bestTier = -1;

            for (ItemStack item : inv.getContents()) {
                if (item == null) continue;
                int tier = toolTierValue(item.getType());
                if (tier > bestTier) {
                    bestTier = tier;
                    best = item.getType();
                }
            }

            if (best != null && best != inv.getItemInMainHand().getType()) {
                // Find slot with the best tool and switch to it
                for (int i = 0; i < inv.getSize(); i++) {
                    ItemStack item = inv.getItem(i);
                    if (item != null && item.getType() == best) {
                        inv.setHeldItemSlot(i < 9 ? i
                                : findHotbarSlot(inv, best));
                        break;
                    }
                }
            }
        });
    }

    private int toolTierValue(Material mat) {
        return switch (mat) {
            case DIAMOND_PICKAXE, DIAMOND_AXE, DIAMOND_SHOVEL -> 4;
            case IRON_PICKAXE, IRON_AXE, IRON_SHOVEL -> 3;
            case STONE_PICKAXE, STONE_AXE, STONE_SHOVEL -> 2;
            case WOODEN_PICKAXE, WOODEN_AXE, WOODEN_SHOVEL -> 1;
            default -> 0;
        };
    }

    private int findHotbarSlot(PlayerInventory inv, Material mat) {
        for (int i = 0; i < 9; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() == mat) return i;
        }
        return inv.getHeldItemSlot();
    }

    // ─────────────────── Utilities ───────────────────

    private String truncate(String s, int maxLen) {
        if (s == null) return "null";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
