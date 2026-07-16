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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * MATRIX Spigot Plugin — multi-agent swarm with online training.
 *
 * <p>Each bot is an independent MPDT-driven agent with its own role,
 * tick counter, and feedback buffer. All bots share a single WebSocket
 * connection to matrix-core, differentiated by agentId.
 *
 * <h3>Commands:</h3>
 * <pre>
 * /matrix connect              — connect to matrix-core
 * /matrix list                 — list all running bots
 * /matrix add &lt;name&gt; &lt;role&gt;  — spawn a new bot (miner/crafter/explorer/fighter/generalist)
 * /matrix remove &lt;name&gt;       — remove a bot
 * /matrix switch &lt;name&gt;       — switch to controlling a specific bot
 * /matrix start                — start the default bot (backward compat)
 * /matrix stop                 — stop all bots
 * /matrix status               — show active bot state
 * /matrix train [gens] [pop] [k] — run GA training
 * /matrix save                 — save brain state
 * </pre>
 */
public class MatrixPlugin extends JavaPlugin {

    private final Random rng = new Random();

    private MatrixCoreClient client;
    private boolean running;

    /** All managed bots, keyed by unique name. */
    private final Map<String, BotState> bots = new ConcurrentHashMap<>();

    /** Currently selected bot name (for status/switch). */
    private volatile String activeBotName;

    // --- Config values ---
    private String matrixCoreUrl = "http://localhost:9091";
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

        getLogger().info("MATRIX Neural Plugin v3.0.0 (multi-agent swarm) enabled");
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
        stopAllBots();
        if (client != null) {
            client.stop();
            client.shutdown();
        }
        getLogger().info("MATRIX Neural Plugin disabled");
    }

    // ─────────────────── Configuration ───────────────────

    private void loadConfigValues() {
        // Read from env var first, then config.yml, then default
        String envUrl = System.getenv("MATRIX_CORE_URL");
        if (envUrl != null && !envUrl.isEmpty()) {
            matrixCoreUrl = envUrl;
        } else {
            matrixCoreUrl = getConfig().getString("matrix-core-url", "http://localhost:9091");
        }
        tickInterval = getConfig().getInt("tick-interval", 20);
        autoConnect = getConfig().getBoolean("auto-connect", true);
    }

    // ─────────────────── Connection ───────────────────

    private void connectToCore() {
        getLogger().info("Connecting to matrix-core at " + matrixCoreUrl + "...");

        client.connect(new MatrixCoreClient.ActionCallback() {
            @Override
            public void onAction(String action) {
                String botName = activeBotName;
                if (botName != null) {
                    BotState bot = bots.get(botName);
                    if (bot != null) {
                        bot.lastAction = action;
                    }
                }
                // Also update all bots' last actions (shared callback, one connection)
                for (BotState bot : bots.values()) {
                    bot.lastAction = action;
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

    // ─────────────────── Bot State ───────────────────

    /**
     * Per-bot mutable state tracked by the plugin.
     */
    private static class BotState {
        final String name;
        final AgentRole role;
        Player player;
        int tickCount;
        int blocksMined;
        volatile String lastAction = "STAY";
        final List<FeedbackRecord> feedbackBuffer = new ArrayList<>();
        BukkitRunnable task;
        boolean active;
        long lastStatusTime = 0;

        BotState(String name, AgentRole role, Player player) {
            this.name = name;
            this.role = role;
            this.player = player;
        }
    }

    /** Feedback record for online training. */
    private record FeedbackRecord(long sensorBits, boolean success) {}

    // ─────────────────── Commands ───────────────────

    private void registerCommands() {
        getCommand("matrix").setExecutor((sender, cmd, label, args) -> {
            if (args.length == 0) {
                sender.sendMessage("§e/matrix §f<connect|list|add|remove|switch|start|stop|status|train|save>");
                return true;
            }
            switch (args[0].toLowerCase()) {
                case "connect" -> {
                    connectToCore();
                    sender.sendMessage("§aConnecting to matrix-core...");
                }
                case "list" -> {
                    if (bots.isEmpty()) {
                        sender.sendMessage("§7No bots running. Use §e/matrix add <name> <role>");
                    } else {
                        sender.sendMessage("§6=== Running Bots ===");
                        for (BotState bot : bots.values()) {
                            String marker = bot.name.equals(activeBotName) ? " §a◀ active" : "";
                            sender.sendMessage(String.format(
                                    "  §e%s §7[%s]§f ticks=%d mined=%d action=%s%s",
                                    bot.name, bot.role.name(), bot.tickCount,
                                    bot.blocksMined, bot.lastAction, marker));
                        }
                    }
                }
                case "add" -> {
                    if (args.length < 3) {
                        sender.sendMessage("§cUsage: /matrix add <name> <miner|crafter|explorer|fighter|generalist>");
                        return true;
                    }
                    String name = args[1];
                    String roleStr = args[2];
                    try {
                        AgentRole role = AgentRole.fromString(roleStr);
                        addBot(name, role);
                        sender.sendMessage("§aBot §e" + name + "§a spawned as §e" + role.name());
                    } catch (IllegalArgumentException e) {
                        sender.sendMessage("§cUnknown role: " + roleStr
                                + ". Valid: miner, crafter, explorer, fighter, generalist");
                    }
                }
                case "remove" -> {
                    if (args.length < 2) {
                        sender.sendMessage("§cUsage: /matrix remove <name>");
                        return true;
                    }
                    removeBot(args[1]);
                    sender.sendMessage("§aBot §e" + args[1] + "§a removed.");
                }
                case "switch" -> {
                    if (args.length < 2) {
                        sender.sendMessage("§cUsage: /matrix switch <name>");
                        return true;
                    }
                    String name = args[1];
                    if (!bots.containsKey(name)) {
                        sender.sendMessage("§cBot not found: " + name);
                        return true;
                    }
                    activeBotName = name;
                    sender.sendMessage("§aSwitched to bot §e" + name);
                }
                case "start" -> startBot();
                case "stop" -> stopBot();
                case "status" -> showStatus(sender instanceof Player p ? p : null);
                case "train" -> {
                    int gens = args.length > 1 ? Integer.parseInt(args[1]) : 100;
                    int pop = args.length > 2 ? Integer.parseInt(args[2]) : 50;
                    int k = args.length > 3 ? Integer.parseInt(args[3]) : 20;
                    trainRemotely(gens, pop, k);
                    sender.sendMessage("§aTraining started (async): "
                            + gens + " gen, " + pop + " pop, k=" + k);
                }
                case "save" -> {
                    client.save();
                    sender.sendMessage("§aSaving brain...");
                }
                default -> sender.sendMessage("§cUnknown command. Use: connect|list|add|remove|switch|start|stop|status|train|save");
            }
            return true;
        });
    }

    // ─────────────────── Bot Lifecycle ───────────────────

    /**
     * Adds a new bot with the given name and role.
     *
     * <p>The bot picks the first available online player. If none is online,
     * the bot is created but won't tick until a player becomes available.
     */
    private void addBot(String name, AgentRole role) {
        if (bots.containsKey(name)) {
            getLogger().warning("Bot '" + name + "' already exists. Remove it first.");
            return;
        }

        Player player = getServer().getOnlinePlayers().stream().findFirst().orElse(null);
        if (player == null) {
            getLogger().warning("No players online — bot '" + name + "' needs a player to observe");
            return;
        }

        BotState bot = new BotState(name, role, player);
        bots.put(name, bot);

        if (activeBotName == null) {
            activeBotName = name;
        }

        // Register agent on matrix-core via WebSocket
        if (client.isConnected()) {
            client.start(name);
        }

        // Request role-specific pretrained layers
        int[] layers = role.pretrainedLayers();
        if (layers.length > 0 && client.isConnected()) {
            getLogger().info("Loading pretrained layers for role " + role.name()
                    + ": " + java.util.Arrays.toString(layers));
        }

        // Start tick loop for this bot
        bot.task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!bot.active || bot.player == null || !bot.player.isOnline()) {
                    bot.active = false;
                    return;
                }
                tickBot(bot);
            }
        };
        bot.task.runTaskTimer(this, 0L, tickInterval);
        bot.active = true;

        getLogger().info("Bot '" + name + "' (" + role.name() + ") spawned. "
                + "Total bots: " + bots.size());
    }

    /** Removes a bot by name. */
    private void removeBot(String name) {
        BotState bot = bots.remove(name);
        if (bot != null) {
            bot.active = false;
            if (bot.task != null) {
                bot.task.cancel();
            }
            getLogger().info("Bot '" + name + "' removed. Ticks: " + bot.tickCount
                    + ", mined: " + bot.blocksMined);
        }
        if (name.equals(activeBotName)) {
            // Switch to first remaining bot
            activeBotName = bots.keySet().stream().findFirst().orElse(null);
        }
    }

    /** Stops all running bots. */
    private void stopAllBots() {
        for (BotState bot : bots.values()) {
            bot.active = false;
            if (bot.task != null) {
                bot.task.cancel();
            }
        }
        bots.clear();
        activeBotName = null;
    }

    // Legacy single-bot API (backward compat)
    private void startBot() {
        if (!bots.isEmpty()) {
            getLogger().info("Bots already running: " + bots.size() + " bot(s)");
            return;
        }
        // Create a default GENERALIST bot
        addBot("Bot1", AgentRole.GENERALIST);
    }

    private void stopBot() {
        stopAllBots();
        if (client != null && client.isConnected()) {
            client.stop();
        }
        getLogger().info("All bots stopped.");
    }

    private void showStatus(Player sender) {
        if (bots.isEmpty()) {
            String msg = "MATRIX: No bots running. connected="
                    + (client.isConnected() ? "yes" : "no");
            getLogger().info(msg);
            if (sender != null) sender.sendMessage(msg);
            return;
        }
        BotState active = activeBotName != null ? bots.get(activeBotName) : null;
        if (active != null) {
            long now = System.currentTimeMillis();
            if (sender != null && now - active.lastStatusTime < 5000) {
                sender.sendMessage("§7Status updated " + (now - active.lastStatusTime) / 1000
                        + "s ago. Use /matrix status to force refresh.");
                return;
            }
            active.lastStatusTime = now;
            String msg = String.format(
                    "MATRIX Bot [%s] %s: ticks=%d mined=%d connected=%s action=%s feedback=%d",
                    active.name, active.role.name(),
                    active.tickCount, active.blocksMined,
                    client.isConnected() ? "yes" : "no",
                    active.lastAction,
                    active.feedbackBuffer.size());
            getLogger().info(msg);
            if (sender != null) sender.sendMessage(msg);
        }
        getLogger().info("Total bots: " + bots.size());
        if (sender != null) sender.sendMessage("Total bots: " + bots.size());
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
     * Main tick for a single bot: read sensors → send to matrix-core →
     * execute last known action → record feedback → maybe retrain.
     */
    private void tickBot(BotState bot) {
        bot.tickCount++;
        long tickStart = System.currentTimeMillis();
        long sensors = readSensors(bot.player);

        if (client.isConnected()) {
            client.sendSensors(bot.name, sensors, bot.role.name());
        } else {
            // Fallback: generate a random action locally
            if (bot.tickCount % 5 == 0) {
                bot.lastAction = ALL_ACTIONS[rng.nextInt(ALL_ACTIONS.length)];
            }
        }

        boolean success = executeAction(bot, bot.lastAction);
        recordFeedback(bot, sensors, success);
        if (client.isConnected()) {
            client.sendFeedback(bot.name, sensors, success);
        }

        // Every 100 ticks, try online training if enough feedback collected
        if (bot.tickCount % 100 == 0 && bot.feedbackBuffer.size() >= 20 && client.isConnected()) {
            maybeRetrain(bot);
        }

        long tickDuration = System.currentTimeMillis() - tickStart;

        if (bot.tickCount % 100 == 0) {
            getLogger().info(String.format(
                    "MATRIX [%s/%s] tick=%d mined=%d health=%d hunger=%d tool=%d action=%s dur=%dms connected=%s fb=%d",
                    bot.name, bot.role.name(), bot.tickCount, bot.blocksMined,
                    bot.player != null ? (int) bot.player.getHealth() : 0,
                    bot.player != null ? bot.player.getFoodLevel() : 0,
                    detectToolTier(bot.player),
                    bot.lastAction,
                    tickDuration,
                    client.isConnected() ? "yes" : "no",
                    bot.feedbackBuffer.size()));
        }
    }

    // ─────────────────── Feedback & Online Training ───────────────────

    /**
     * Records whether an action was successful.
     *
     * <p>Success criteria:
     * <ul>
     *   <li>MINE: block was actually broken (blocksMined incremented)</li>
     *   <li>MOVE: target location was passable</li>
     *   <li>EAT/CRAFT/TOOL_UP: always considered successful (side effect)</li>
     *   <li>STAY: always successful</li>
     * </ul>
     */
    private void recordFeedback(BotState bot, long sensorBits, boolean success) {
        bot.feedbackBuffer.add(new FeedbackRecord(sensorBits, success));
        if (bot.feedbackBuffer.size() > 100) {
            bot.feedbackBuffer.remove(0);
        }
    }

    /**
     * Triggers online training on matrix-core with the bot's feedback data.
     */
    private void maybeRetrain(BotState bot) {
        getLogger().info("Triggering online training for " + bot.name
                + " (" + bot.feedbackBuffer.size() + " feedback records)");

        // Send feedback to server for recording, then trigger online training
        CompletableFuture.runAsync(() -> {
            // First send all feedback to the server via a synthetic training request
            // Then trigger the online training
            client.trainOnline(5)
                    .orTimeout(30, TimeUnit.SECONDS)
                    .thenAccept(body -> getLogger().info(
                            "Online training complete for " + bot.name + ": " + truncate(body, 150)))
                    .exceptionally(ex -> {
                        getLogger().warning("Online training failed for "
                                + bot.name + ": " + ex.getMessage());
                        return null;
                    });
        });
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
    private long readSensors(Player player) {
        long bits = 0;
        Location loc = player.getLocation();
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

        double healthRatio = player.getHealth() / 20.0;
        int healthQ = Math.min(4, (int) (healthRatio * 5));
        for (int i = 0; i < 3; i++) {
            if ((healthQ & (1 << i)) != 0) bits |= (1L << (10 + i));
        }

        int foodQ = Math.min(4, player.getFoodLevel() * 5 / 20);
        for (int i = 0; i < 3; i++) {
            if ((foodQ & (1 << i)) != 0) bits |= (1L << (13 + i));
        }

        int toolBits = detectToolTier(player);
        for (int i = 0; i < 3; i++) {
            if ((toolBits & (1 << i)) != 0) bits |= (1L << (16 + i));
        }

        if (player.getInventory().contains(Material.BREAD)) {
            bits |= (1L << 19);
        }

        return bits;
    }

    private int detectToolTier(Player player) {
        PlayerInventory inv = player.getInventory();
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
     * @return true if the action was successful, false otherwise
     */
    private boolean executeAction(BotState bot, String action) {
        if (action == null) action = "STAY";

        return switch (action.toUpperCase()) {
            case "MOVE_N" -> movePlayer(bot.player, 0, -1);
            case "MOVE_S" -> movePlayer(bot.player, 0, 1);
            case "MOVE_W" -> movePlayer(bot.player, -1, 0);
            case "MOVE_E" -> movePlayer(bot.player, 1, 0);
            case "MINE" -> mineBlock(bot);
            case "EAT" -> { eatFood(bot.player); yield true; }
            case "CRAFT" -> craftItem(bot);
            case "TOOL_UP" -> { switchToBestTool(bot.player); yield true; }
            case "STAY" -> true;
            default -> {
                getLogger().fine("Unknown action: " + action);
                yield false;
            }
        };
    }

    private boolean movePlayer(Player player, double dx, double dz) {
        Location loc = player.getLocation();
        Location target = loc.clone().add(dx, 0, dz);

        if (dz < 0) loc.setYaw(0);
        else if (dz > 0) loc.setYaw(180);
        else if (dx < 0) loc.setYaw(90);
        else if (dx > 0) loc.setYaw(-90);

        if (!target.getBlock().getType().isSolid()) {
            getServer().getScheduler().runTask(this,
                    () -> player.teleport(target));
            return true;
        }
        return false;
    }

    private boolean mineBlock(BotState bot) {
        Location loc = bot.player.getLocation();
        Block target = loc.clone().add(0, -1, 0).getBlock();
        if (target.getType() != Material.AIR && target.getType() != Material.BEDROCK) {
            getServer().getScheduler().runTask(this, () -> {
                target.breakNaturally(bot.player.getInventory().getItemInMainHand());
                bot.blocksMined++;
            });
            return true;
        }
        return false;
    }

    private void eatFood(Player player) {
        getServer().getScheduler().runTask(this, () -> {
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && item.getType().isEdible()) {
                    player.getInventory().setItemInMainHand(item);
                    break;
                }
            }
        });
    }

    private void switchToBestTool(Player player) {
        getServer().getScheduler().runTask(this, () -> {
            PlayerInventory inv = player.getInventory();
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

    // ─────────────────── Crafting ───────────────────

    /**
     * Attempts to craft an item from available inventory materials.
     *
     * <p>Priority order: logs → planks → sticks → wooden pickaxe.
     * Returns true if any crafting was performed.
     */
    private boolean craftItem(BotState bot) {
        Player player = bot.player;
        if (player == null || !player.isOnline()) return false;

        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getContents();

        // 1) Log → Planks
        if (hasMaterial(contents, Material.OAK_LOG, 1)) {
            removeMaterial(inv, Material.OAK_LOG, 1);
            giveItem(player, Material.OAK_PLANKS, 4);
            getLogger().fine(bot.name + " crafted 4x Planks from Log");
            return true;
        }
        if (hasMaterial(contents, Material.SPRUCE_LOG, 1)) {
            removeMaterial(inv, Material.SPRUCE_LOG, 1);
            giveItem(player, Material.SPRUCE_PLANKS, 4);
            getLogger().fine(bot.name + " crafted 4x Spruce Planks");
            return true;
        }
        if (hasMaterial(contents, Material.BIRCH_LOG, 1)) {
            removeMaterial(inv, Material.BIRCH_LOG, 1);
            giveItem(player, Material.BIRCH_PLANKS, 4);
            getLogger().fine(bot.name + " crafted 4x Birch Planks");
            return true;
        }

        // 2) Planks → Sticks
        long plankCount = countMaterial(contents, Material.OAK_PLANKS)
                + countMaterial(contents, Material.SPRUCE_PLANKS)
                + countMaterial(contents, Material.BIRCH_PLANKS);
        if (plankCount >= 2) {
            Material plankType = findFirst(contents, Material.OAK_PLANKS, Material.SPRUCE_PLANKS, Material.BIRCH_PLANKS);
            if (plankType != null && removeMaterial(inv, plankType, 2)) {
                giveItem(player, Material.STICK, 4);
                getLogger().fine(bot.name + " crafted 4x Sticks");
                return true;
            }
        }

        // 3) Planks + Sticks → Wooden Pickaxe
        long sticks = countMaterial(contents, Material.STICK);
        plankCount = countMaterial(contents, Material.OAK_PLANKS)
                + countMaterial(contents, Material.SPRUCE_PLANKS)
                + countMaterial(contents, Material.BIRCH_PLANKS);
        if (sticks >= 2 && plankCount >= 3) {
            // Deduct: 2 sticks + 3 planks → wooden pickaxe
            removeMaterial(inv, Material.STICK, 2);
            Material pType = findFirst(contents, Material.OAK_PLANKS, Material.SPRUCE_PLANKS, Material.BIRCH_PLANKS);
            if (pType != null) removeMaterial(inv, pType, 3);
            giveItem(player, Material.WOODEN_PICKAXE, 1);
            getLogger().info(bot.name + " crafted Wooden Pickaxe!");
            return true;
        }

        return false; // nothing craftable
    }

    private boolean hasMaterial(ItemStack[] contents, Material mat, int minCount) {
        return countMaterial(contents, mat) >= minCount;
    }

    private long countMaterial(ItemStack[] contents, Material mat) {
        long total = 0;
        for (ItemStack item : contents) {
            if (item != null && item.getType() == mat) {
                total += item.getAmount();
            }
        }
        return total;
    }

    private boolean removeMaterial(PlayerInventory inv, Material mat, int amount) {
        int remaining = amount;
        for (int i = 0; i < inv.getSize() && remaining > 0; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() == mat) {
                int take = Math.min(remaining, item.getAmount());
                if (take == item.getAmount()) {
                    inv.clear(i);
                } else {
                    item.setAmount(item.getAmount() - take);
                }
                remaining -= take;
            }
        }
        return remaining == 0;
    }

    private void giveItem(Player player, Material mat, int amount) {
        ItemStack leftover = player.getInventory().addItem(new ItemStack(mat, amount)).get(0);
        if (leftover != null) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    private Material findFirst(ItemStack[] contents, Material... candidates) {
        for (Material m : candidates) {
            if (countMaterial(contents, m) > 0) return m;
        }
        return null;
    }

    // ─────────────────── Utilities ───────────────────

    private String truncate(String s, int maxLen) {
        if (s == null) return "null";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
