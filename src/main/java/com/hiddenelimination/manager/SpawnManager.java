package com.hiddenelimination.manager;

import com.hiddenelimination.HiddenEliminationPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * 出生点和大厅传送管理。
 */
public final class SpawnManager {

    private final HiddenEliminationPlugin plugin;
    private final Random random = new Random();

    public SpawnManager(HiddenEliminationPlugin plugin) {
        this.plugin = plugin;
    }

    public void setLobby(Location location) {
        plugin.getConfig().set("lobby.world", location.getWorld() == null ? null : location.getWorld().getName());
        plugin.getConfig().set("lobby.x", location.getX());
        plugin.getConfig().set("lobby.y", location.getY());
        plugin.getConfig().set("lobby.z", location.getZ());
        plugin.getConfig().set("lobby.yaw", location.getYaw());
        plugin.getConfig().set("lobby.pitch", location.getPitch());
        plugin.saveConfig();
    }

    public Location getLobbyLocation() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("lobby");
        if (section == null) {
            return fallbackLobby();
        }

        String worldName = section.getString("world");
        if (worldName == null || worldName.isBlank()) {
            return fallbackLobby();
        }

        World world = resolveWorld(worldName);
        if (world == null) {
            return fallbackLobby();
        }

        return new Location(
                world,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw"),
                (float) section.getDouble("pitch")
        );
    }

    public World getGameWorld() {
        String worldName = plugin.getConfig().getString("game.world", "world");
        if (worldName == null || worldName.isBlank()) {
            return fallbackWorld();
        }

        World world = resolveWorld(worldName);
        if (world != null) {
            return world;
        }

        return fallbackWorld();
    }

    /**
     * 每局开前可选地图重置：
     * - 从 game.world-template 拷贝到 game.world
     */
    public boolean resetGameWorldFromTemplateIfEnabled() {
        boolean enabled = plugin.getConfig().getBoolean("game.reset-world-each-round", false);
        if (!enabled) {
            return true;
        }

        String worldName = plugin.getConfig().getString("game.world", "world");
        if (worldName == null || worldName.isBlank()) {
            plugin.getLogger().warning("[地图重置] game.world 未配置");
            return false;
        }

        String templateName = plugin.getConfig().getString("game.world-template", worldName + "_template");
        if (templateName == null || templateName.isBlank()) {
            plugin.getLogger().warning("[地图重置] game.world-template 未配置");
            return false;
        }

        File worldContainer = Bukkit.getWorldContainer();
        Path targetPath = new File(worldContainer, worldName).toPath();
        Path templatePath = new File(worldContainer, templateName).toPath();

        if (!Files.exists(templatePath)) {
            plugin.getLogger().warning("[地图重置] 模板世界不存在: " + templatePath);
            return false;
        }

        // 卸载目标世界并清人
        World loaded = Bukkit.getWorld(worldName);
        if (loaded != null) {
            Location lobby = getLobbyLocation();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getWorld().equals(loaded) && lobby != null) {
                    online.teleport(lobby);
                }
            }

            boolean unloaded = Bukkit.unloadWorld(loaded, false);
            if (!unloaded) {
                plugin.getLogger().warning("[地图重置] 卸载世界失败: " + worldName);
                return false;
            }
        }

        try {
            deleteDirectory(targetPath);
            copyDirectory(templatePath, targetPath);
        } catch (IOException e) {
            plugin.getLogger().severe("[地图重置] 文件操作失败: " + e.getMessage());
            return false;
        }

        World recreated = Bukkit.createWorld(WorldCreator.name(worldName));
        if (recreated == null) {
            plugin.getLogger().severe("[地图重置] 重建世界失败: " + worldName);
            return false;
        }

        plugin.getLogger().info("[地图重置] 已从模板重置世界: " + worldName + " <- " + templateName);
        return true;
    }

    public void teleportToLobby(Player player) {
        Location lobby = getLobbyLocation();
        if (lobby != null) {
            player.teleport(lobby);
        }
    }

    public void teleportAllToLobby(List<Player> players) {
        for (Player player : players) {
            teleportToLobby(player);
        }
    }

    public void spreadPlayersToGameWorld(List<Player> players) {
        World gameWorld = getGameWorld();
        if (gameWorld == null) {
            return;
        }

        int spreadRadius = Math.max(16, plugin.getConfig().getInt("game.spread-radius", 200));
        for (Player player : players) {
            player.teleport(randomSpawn(gameWorld, spreadRadius, player));
        }
    }

    private Location randomSpawn(World world, int spreadRadius, Player player) {
        // Avoid watchdog stalls: only probe already-loaded chunks while selecting random spawns.
        final int maxAttempts = 48;
        for (int i = 0; i < maxAttempts; i++) {
            int x = random.nextInt(spreadRadius * 2 + 1) - spreadRadius;
            int z = random.nextInt(spreadRadius * 2 + 1) - spreadRadius;
            int chunkX = x >> 4;
            int chunkZ = z >> 4;

            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                continue;
            }

            int y = world.getHighestBlockYAt(x, z) + 1;
            return new Location(world, x + 0.5D, y, z + 0.5D);
        }

        // Fallback to world spawn if no loaded candidate chunk is found.
        Location fallback = world.getSpawnLocation().clone();
        int y = world.getHighestBlockYAt(fallback) + 1;
        fallback.setY(y);
        plugin.getLogger().warning("[出生分散] 未找到已加载随机区块，玩家 " + player.getName() + " 回退到世界出生点。");
        return fallback.add(0.5D, 0.0D, 0.5D);
    }

    private World resolveWorld(String worldName) {
        World loaded = Bukkit.getWorld(worldName);
        if (loaded != null) {
            return loaded;
        }

        File worldDir = new File(Bukkit.getWorldContainer(), worldName);
        if (worldDir.exists() && worldDir.isDirectory()) {
            return Bukkit.createWorld(WorldCreator.name(worldName));
        }

        return null;
    }

    private Location fallbackLobby() {
        World fallback = fallbackWorld();
        return fallback == null ? null : fallback.getSpawnLocation();
    }

    private World fallbackWorld() {
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
    }

    private void deleteDirectory(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }

        try (var walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException io) {
                throw io;
            }
            throw e;
        }
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        try (var walk = Files.walk(source)) {
            walk.forEach(sourcePath -> {
                try {
                    Path relative = source.relativize(sourcePath);
                    Path targetPath = target.resolve(relative);

                    if (Files.isDirectory(sourcePath)) {
                        Files.createDirectories(targetPath);
                    } else {
                        Files.createDirectories(targetPath.getParent());
                        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException io) {
                throw io;
            }
            throw e;
        }
    }
}