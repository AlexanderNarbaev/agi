package io.matrix.spigot;

import io.matrix.minecraft.NeuralBrain;
import io.matrix.minecraft.BlockAgent;
import io.matrix.minecraft.BlockType;
import io.matrix.minecraft.CraftingSystem;
import io.matrix.minecraft.SurvivalRunner;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * MATRIX Spigot Plugin — bridges the MPDT neural network
 * with a real Minecraft server.
 *
 * <p>The neural brain controls an agent bot. Every tick:
 * <ol>
 * <li>Read Minecraft world state (blocks, inventory, health)</li>
 * <li>Encode as 20-bit sensor vector</li>
 * <li>NeuralBrain.act(sensors) → action</li>
 * <li>Execute action via Bukkit API</li>
 * </ol>
 *
 * <p>Commands:
 * <pre>
 * /matrix start   — spawn and start the bot
 * /matrix stop    — stop the bot
 * /matrix status  — show bot state
 * /matrix train   — run GA training in sandbox
 * </pre>
 */
public class MatrixPlugin extends JavaPlugin {

    private final Random rng = new Random();
    private NeuralBrain brain;
    private Player botPlayer;
    private boolean running;
    private int tickCount;
    private int blocksMined;
    private BukkitRunnable botTask;

    @Override
    public void onEnable() {
        getLogger().info("MATRIX Neural Plugin enabled");
        getLogger().info("Initializing MPDT neural brain...");
        brain = new NeuralBrain(new Random(42));

        getCommand("matrix").setExecutor((sender, cmd, label, args) -> {
            if (args.length == 0) return false;
            switch (args[0].toLowerCase()) {
                case "start" -> startBot();
                case "stop" -> stopBot();
                case "status" -> showStatus(sender instanceof Player p ? p : null);
                case "train" -> trainInSandbox();
                default -> sender.sendMessage("Usage: /matrix <start|stop|status|train>");
            }
            return true;
        });

        getLogger().info("Ready. Use /matrix start to begin.");
    }

    @Override
    public void onDisable() {
        stopBot();
        getLogger().info("MATRIX Neural Plugin disabled");
    }

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
        botTask.runTaskTimer(this, 0L, 20L);
        getLogger().info("Bot started. Controlling: " + botPlayer.getName());
    }

    private void stopBot() {
        running = false;
        if (botTask != null) {
            botTask.cancel();
            botTask = null;
        }
        getLogger().info("Bot stopped. Ticks: " + tickCount + ", mined: " + blocksMined);
    }

    private void showStatus(Player sender) {
        String msg = String.format(
                "MATRIX Bot: running=%s, ticks=%d, mined=%d, brain=%s",
                running, tickCount, blocksMined,
                brain != null ? "initialized" : "uninitialized");
        getLogger().info(msg);
        if (sender != null) sender.sendMessage(msg);
    }

    private void trainInSandbox() {
        getLogger().info("Starting GA training in sandbox (this may take a while)...");
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            long startTime = System.currentTimeMillis();
            io.matrix.MinecraftExperiment.main(new String[0]);
            long elapsed = System.currentTimeMillis() - startTime;
            getLogger().info("Training complete in " + elapsed + "ms. Use /matrix start to deploy.");
        });
    }

    /**
     * Main tick: sensor → action → execute.
     */
    private void tick() {
        tickCount++;
        long tickStart = System.currentTimeMillis();
        long sensors = readSensors();

        BlockAgent.Action action = brain.act(sensors);
        executeAction(action);

        long tickDuration = System.currentTimeMillis() - tickStart;

        if (tickCount % 100 == 0) {
            getLogger().info(String.format(
                    "MATRIX tick=%d mined=%d health=%d hunger=%d tool=%s action=%s duration=%dms",
                    tickCount, blocksMined,
                    botPlayer != null ? (int) botPlayer.getHealth() : 0,
                    botPlayer != null ? botPlayer.getFoodLevel() : 0,
                    detectToolTier(),
                    action.getClass().getSimpleName(),
                    tickDuration));
        }
    }

    /**
     * Reads Minecraft world state as a 20-bit sensor vector.
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
        if (inv.getItemInMainHand().getType() == Material.DIAMOND_PICKAXE) return 4;
        if (inv.getItemInMainHand().getType() == Material.IRON_PICKAXE) return 3;
        if (inv.getItemInMainHand().getType() == Material.STONE_PICKAXE) return 2;
        if (inv.getItemInMainHand().getType() == Material.WOODEN_PICKAXE) return 1;
        return 0;
    }

    /**
     * Executes a neural action in the Minecraft world.
     */
    private void executeAction(BlockAgent.Action action) {
        if (action instanceof BlockAgent.Action.Move move) {
            double yaw = switch (move.direction()) {
                case N -> 0; case S -> 180; case W -> 90; case E -> -90;
                case STAY -> botPlayer.getLocation().getYaw();
            };
            Location loc = botPlayer.getLocation();
            loc.setYaw((float) yaw);

            double dx = 0, dz = 0;
            switch (move.direction()) {
                case N -> dz = -1; case S -> dz = 1;
                case W -> dx = -1; case E -> dx = 1;
            }

            Location target = loc.clone().add(dx, 0, dz);
            if (!target.getBlock().getType().isSolid()) {
                getServer().getScheduler().runTask(this,
                        () -> botPlayer.teleport(target));
            }
        } else if (action instanceof BlockAgent.Action.Mine) {
            Location loc = botPlayer.getLocation();
            Block target = loc.clone().add(0, -1, 0).getBlock();
            if (target.getType() != Material.AIR && target.getType() != Material.BEDROCK) {
                getServer().getScheduler().runTask(this, () -> {
                    target.breakNaturally(botPlayer.getInventory().getItemInMainHand());
                    blocksMined++;
                });
            }
        } else if (action instanceof BlockAgent.Action.Eat) {
            getServer().getScheduler().runTask(this, () -> {
                for (ItemStack item : botPlayer.getInventory().getContents()) {
                    if (item != null && item.getType().isEdible()) {
                        botPlayer.getInventory().setItemInMainHand(item);
                        break;
                    }
                }
            });
        }
    }
}
