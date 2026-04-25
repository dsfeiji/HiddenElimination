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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 出生点和大厅传送管理。
 */
public final class SpawnManager {

    private static final double MIN_SPAWN_DISTANCE_BLOCKS = 48.0D;
    private static final double MIN_SPAWN_DISTANCE_SQUARED = MIN_SPAWN_DISTANCE_BLOCKS * MIN_SPAWN_DISTANCE_BLOCKS;
    private static final double BORDER_SPAWN_MARGIN_BLOCKS = 24.0D;
    private static final String GENERATED_WORLD_INFIX = "_he_round_";

    private final HiddenEliminationPlugin plugin;
    private final Random random = new Random();
    private final AtomicBoolean preparingWorld = new AtomicBoolean(false);

    private volatile String activeGameWorldName;
    private volatile String preparedGameWorldName;

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
        if (location.getWorld() != null) {
            location.getWorld().setSpawnLocation(location);
        }
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

    public World getLobbyWorld() {
        Location lobby = getLobbyLocation();
        return lobby == null ? null : lobby.getWorld();
    }

    public void enforceLobbyEnvironment() {
        World lobbyWorld = getLobbyWorld();
        if (lobbyWorld == null) {
            return;
        }

        Location lobby = getLobbyLocation();
        if (lobby != null && lobby.getWorld() != null && lobby.getWorld().equals(lobbyWorld)) {
            lobbyWorld.setSpawnLocation(lobby);
        }

        lobbyWorld.setTime(1000L);
        lobbyWorld.setStorm(false);
        lobbyWorld.setThundering(false);
        lobbyWorld.setWeatherDuration(0);
        lobbyWorld.setThunderDuration(0);
    }

    public void initializePreparedGameWorldAsync() {
        if (!plugin.getConfig().getBoolean("game.reset-world-each-round", false)) {
            return;
        }

        ensurePreparedGameWorldAsync();
    }

    public World getGameWorld() {
        if (activeGameWorldName != null && !activeGameWorldName.isBlank()) {
            World active = resolveWorld(activeGameWorldName);
            if (active != null) {
                return active;
            }
        }

        String worldName = getConfiguredBaseWorldName();
        World world = resolveWorld(worldName);
        if (world != null) {
            return world;
        }

        return fallbackWorld();
    }

    public World acquireGameWorldForRound() {
        if (!plugin.getConfig().getBoolean("game.reset-world-each-round", false)) {
            World staticWorld = getGameWorld();
            activeGameWorldName = staticWorld == null ? null : staticWorld.getName();
            return staticWorld;
        }

        String preparedName = preparedGameWorldName;
        if (preparedName == null || preparedName.isBlank()) {
            ensurePreparedGameWorldAsync();
            return null;
        }

        World world = resolveWorld(preparedName);
        if (world == null) {
            preparedGameWorldName = null;
            ensurePreparedGameWorldAsync();
            return null;
        }

        activeGameWorldName = preparedName;
        preparedGameWorldName = null;
        ensurePreparedGameWorldAsync();
        return world;
    }

    public String getPreparedGameWorldStatus() {
        if (!plugin.getConfig().getBoolean("game.reset-world-each-round", false)) {
            return "静态地图模式";
        }

        if (preparedGameWorldName != null) {
            return "已就绪（原版随机世界）";
        }

        if (preparingWorld.get()) {
            return "正在后台准备下一局地图";
        }

        return "下一局地图尚未准备";
    }

    public void onRoundFinished() {
        String retiredWorldName = activeGameWorldName;
        activeGameWorldName = null;

        if (plugin.getConfig().getBoolean("game.reset-world-each-round", false)) {
            if (retiredWorldName != null && !retiredWorldName.isBlank()) {
                cleanupGeneratedWorldAsync(retiredWorldName);
            }
            ensurePreparedGameWorldAsync();
        }
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

        Location spawnCenter = getSpawnCenter(gameWorld);
        int spreadRadius = resolveSpawnRadius(gameWorld);
        List<Location> usedSpawns = new ArrayList<>();
        for (Player player : players) {
            Location spawn = randomSpawn(gameWorld, spawnCenter, spreadRadius, player, usedSpawns);
            usedSpawns.add(spawn);
            player.teleport(spawn);
        }
    }

    private void ensurePreparedGameWorldAsync() {
        if (preparedGameWorldName != null) {
            return;
        }

        if (!preparingWorld.compareAndSet(false, true)) {
            return;
        }

        String targetWorldName = buildGeneratedWorldName();
        long seed = random.nextLong();

        CompletableFuture
                .completedFuture(null)
                .thenCompose(ignored -> createAndWarmupWorldOnMainThread(targetWorldName, seed))
                .whenComplete((ignored, throwable) -> {
                    if (throwable != null) {
                        Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
                        plugin.getLogger().warning("[地图预生成] 预生成失败: " + cause.getMessage());
                        cleanupGeneratedWorldAsync(targetWorldName);
                    } else {
                        preparedGameWorldName = targetWorldName;
                        plugin.getLogger().info("[地图预生成] 下一局地图已准备完成: " + targetWorldName + "，seed=" + seed);
                    }
                    preparingWorld.set(false);
                });
    }

    private CompletableFuture<Void> createAndWarmupWorldOnMainThread(String worldName, long seed) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                WorldCreator creator = WorldCreator.name(worldName);
                creator.seed(seed);
                World world = Bukkit.createWorld(creator);
                if (world == null) {
                    future.completeExceptionally(new IllegalStateException("创建世界失败: " + worldName));
                    return;
                }

                preloadSpawnChunksAsync(world, Math.max(0, plugin.getConfig().getInt("game.preload-radius-chunks", 6)), future);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    private void preloadSpawnChunksAsync(World world, int radiusChunks, CompletableFuture<Void> completeFuture) {
        if (radiusChunks <= 0) {
            completeFuture.complete(null);
            return;
        }

        Location spawn = world.getSpawnLocation();
        int centerChunkX = spawn.getBlockX() >> 4;
        int centerChunkZ = spawn.getBlockZ() >> 4;
        List<int[]> chunks = new ArrayList<>();
        for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
            for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                chunks.add(new int[]{centerChunkX + dx, centerChunkZ + dz});
            }
        }

        int batchSize = Math.max(1, plugin.getConfig().getInt("game.preload-batch-size", 2));
        long intervalTicks = Math.max(1L, plugin.getConfig().getLong("game.preload-batch-interval-ticks", 1L));
        AtomicInteger index = new AtomicInteger(0);
        AtomicInteger pending = new AtomicInteger(0);

        int taskId = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (completeFuture.isDone()) {
                return;
            }

            for (int i = 0; i < batchSize; i++) {
                int current = index.getAndIncrement();
                if (current >= chunks.size()) {
                    break;
                }
                int[] chunk = chunks.get(current);
                pending.incrementAndGet();
                world.getChunkAtAsync(chunk[0], chunk[1], true).whenComplete((loaded, throwable) -> {
                    if (throwable != null) {
                        completeFuture.completeExceptionally(throwable);
                        return;
                    }
                    int left = pending.decrementAndGet();
                    if (index.get() >= chunks.size() && left <= 0) {
                        completeFuture.complete(null);
                    }
                });
            }
        }, 0L, intervalTicks);

        completeFuture.whenComplete((ok, throwable) -> plugin.getServer().getScheduler().cancelTask(taskId));
    }

    private void cleanupGeneratedWorldAsync(String worldName) {
        if (worldName == null || worldName.isBlank() || !isGeneratedWorldName(worldName)) {
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            World loaded = Bukkit.getWorld(worldName);
            if (loaded != null) {
                Location lobby = getLobbyLocation();
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (online.getWorld().equals(loaded) && lobby != null) {
                        online.teleport(lobby);
                    }
                }

                if (!Bukkit.unloadWorld(loaded, false)) {
                    plugin.getLogger().warning("[地图清理] 卸载世界失败: " + worldName);
                    return;
                }
            }

            CompletableFuture.runAsync(() -> {
                try {
                    deleteDirectory(new File(Bukkit.getWorldContainer(), worldName).toPath());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).exceptionally(throwable -> {
                Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
                plugin.getLogger().warning("[地图清理] 删除世界目录失败: " + worldName + "，原因: " + cause.getMessage());
                return null;
            });
        });
    }

    private String buildGeneratedWorldName() {
        return getConfiguredBaseWorldName() + GENERATED_WORLD_INFIX + UUID.randomUUID().toString().replace("-", "");
    }

    private String getConfiguredBaseWorldName() {
        String worldName = plugin.getConfig().getString("game.world", "world");
        return worldName == null || worldName.isBlank() ? "world" : worldName;
    }

    private boolean isGeneratedWorldName(String worldName) {
        return worldName.contains(GENERATED_WORLD_INFIX);
    }

    private Location randomSpawn(World world, Location spawnCenter, int spreadRadius, Player player, List<Location> usedSpawns) {
        final int maxAttempts = 64;
        for (int i = 0; i < maxAttempts; i++) {
            int x = spawnCenter.getBlockX() + random.nextInt(spreadRadius * 2 + 1) - spreadRadius;
            int z = spawnCenter.getBlockZ() + random.nextInt(spreadRadius * 2 + 1) - spreadRadius;
            int chunkX = x >> 4;
            int chunkZ = z >> 4;

            world.getChunkAt(chunkX, chunkZ);

            int y = world.getHighestBlockYAt(x, z) + 1;
            Location candidate = new Location(world, x + 0.5D, y, z + 0.5D);
            if (isFarEnough(candidate, usedSpawns)) {
                return candidate;
            }
        }

        Location fallback = spawnCenter.clone();
        int fallbackIndex = usedSpawns.size();
        double angle = fallbackIndex * (Math.PI * 2.0D / 8.0D);
        double distance = Math.max(MIN_SPAWN_DISTANCE_BLOCKS, spreadRadius / 3.0D);
        int x = fallback.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
        int z = fallback.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
        world.getChunkAt(x >> 4, z >> 4);
        int y = world.getHighestBlockYAt(x, z) + 1;
        plugin.getLogger().warning("[出生分散] 随机出生点不足，玩家 " + player.getName() + " 使用分散回退出生点。");
        return new Location(world, x + 0.5D, y, z + 0.5D);
    }

    private boolean isFarEnough(Location candidate, List<Location> usedSpawns) {
        for (Location usedSpawn : usedSpawns) {
            if (!usedSpawn.getWorld().equals(candidate.getWorld())) {
                continue;
            }

            if (usedSpawn.distanceSquared(candidate) < MIN_SPAWN_DISTANCE_SQUARED) {
                return false;
            }
        }
        return true;
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

    private Location getSpawnCenter(World world) {
        if (plugin.getConfig().getBoolean("world-border.use-world-spawn", true)) {
            return world.getSpawnLocation();
        }

        double centerX = plugin.getConfig().getDouble("world-border.center-x", 0.0D);
        double centerZ = plugin.getConfig().getDouble("world-border.center-z", 0.0D);
        int y = world.getHighestBlockYAt((int) Math.floor(centerX), (int) Math.floor(centerZ)) + 1;
        return new Location(world, centerX, y, centerZ);
    }

    private int resolveSpawnRadius(World world) {
        int configuredRadius = Math.max(16, plugin.getConfig().getInt("game.spread-radius", 200));
        if (!plugin.getConfig().getBoolean("world-border.enabled", false)) {
            return configuredRadius;
        }

        double startSize = Math.max(16.0D, plugin.getConfig().getDouble("world-border.start-size", 2000.0D));
        int borderSafeRadius = (int) Math.floor(startSize / 2.0D - BORDER_SPAWN_MARGIN_BLOCKS);
        if (borderSafeRadius <= 16) {
            return 16;
        }

        return Math.min(configuredRadius, borderSafeRadius);
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

}
