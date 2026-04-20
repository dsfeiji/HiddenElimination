package com.hiddenelimination.manager;

import com.hiddenelimination.HiddenEliminationPlugin;
import com.hiddenelimination.listener.PrepareItemListener;
import com.hiddenelimination.model.GameState;
import com.hiddenelimination.model.PlayerGameData;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core game flow manager.
 */
public final class GameManager {

    private final HiddenEliminationPlugin plugin;
    private final PlayerDataManager playerDataManager;
    private final UIManager uiManager;
    private final SpawnManager spawnManager;
    private final ConditionManager conditionManager;
    private final TaskManager taskManager;
    private final PowerupManager powerupManager;

    private final Set<UUID> activePlayers = ConcurrentHashMap.newKeySet();
    private final List<UUID> eliminationOrder = new ArrayList<>();

    private GameState gameState = GameState.WAITING;

    private BukkitTask borderStartTask;
    private BukkitTask borderAnnounceTask;
    private World borderWorld;
    private double borderResetSize;
    private boolean borderShrinking;
    private long borderShrinkStartMillis;
    private long borderShrinkEndMillis;

    public GameManager(
            HiddenEliminationPlugin plugin,
            PlayerDataManager playerDataManager,
            UIManager uiManager,
            SpawnManager spawnManager,
            ConditionManager conditionManager,
            TaskManager taskManager,
            PowerupManager powerupManager
    ) {
        this.plugin = plugin;
        this.playerDataManager = playerDataManager;
        this.uiManager = uiManager;
        this.spawnManager = spawnManager;
        this.conditionManager = conditionManager;
        this.taskManager = taskManager;
        this.powerupManager = powerupManager;

        this.conditionManager.bindGameManager(this);
    }

    public GameState getGameState() {
        return gameState;
    }

    public boolean isRunning() {
        return gameState == GameState.RUNNING;
    }

    public Set<UUID> getActivePlayersSnapshot() {
        return Set.copyOf(activePlayers);
    }

    public boolean isActivePlayer(UUID playerId) {
        return activePlayers.contains(playerId);
    }

    public boolean isBorderEnabled() {
        return plugin.getConfig().getBoolean("world-border.enabled", false);
    }

    public boolean isBorderShrinking() {
        return borderShrinking;
    }

    public long getBorderSecondsUntilStart() {
        if (!isRunning() || !isBorderEnabled()) {
            return 0L;
        }
        if (borderShrinking || borderShrinkStartMillis <= 0L) {
            return 0L;
        }
        return Math.max(0L, (borderShrinkStartMillis - System.currentTimeMillis()) / 1000L);
    }

    public long getBorderSecondsUntilEnd() {
        if (!isRunning() || !isBorderEnabled() || !borderShrinking || borderShrinkEndMillis <= 0L) {
            return 0L;
        }
        return Math.max(0L, (borderShrinkEndMillis - System.currentTimeMillis()) / 1000L);
    }

    public double getCurrentBorderSize() {
        if (borderWorld == null) {
            return 0.0D;
        }
        return borderWorld.getWorldBorder().getSize();
    }

    public boolean startGame(Player starter) {
        if (gameState != GameState.WAITING) {
            uiManager.error(starter, "当前不在可开局状态。");
            return false;
        }

        if (!spawnManager.resetGameWorldFromTemplateIfEnabled()) {
            uiManager.error(starter, "地图重置失败，请检查模板世界配置。");
            return false;
        }

        List<Player> readyPlayers = playerDataManager.getReadyOnlinePlayers();
        int minReady = Math.max(2, plugin.getConfig().getInt("game.min-ready-players", 2));
        if (readyPlayers.size() < minReady) {
            uiManager.error(starter, "准备人数不足，至少需要 " + minReady + " 人。");
            return false;
        }

        int conditionPoolSize = conditionManager.getConditionPoolSize();
        if (readyPlayers.size() > conditionPoolSize) {
            uiManager.error(starter, "准备人数超过可分配条件数量（最多 " + conditionPoolSize + " 人）。");
            return false;
        }

        World gameWorld = spawnManager.getGameWorld();
        if (gameWorld == null) {
            uiManager.error(starter, "游戏世界不存在，请检查 config.yml 的 game.world。");
            return false;
        }

        resetGameWorldEnvironment(gameWorld);

        activePlayers.clear();
        eliminationOrder.clear();

        for (Player player : readyPlayers) {
            PlayerGameData data = playerDataManager.getOrCreate(player.getUniqueId());
            data.setEliminated(false);
            data.setSpectator(false);
            data.setConditionRevealed(false);
            data.setTaskPoints(0);

            activePlayers.add(player.getUniqueId());

            clearPlayerInventory(player);

            player.setGameMode(GameMode.SURVIVAL);
            if (player.getAttribute(Attribute.MAX_HEALTH) != null) {
                player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
            } else {
                player.setHealth(20.0D);
            }
            player.setFoodLevel(20);
            player.setSaturation(20.0F);
        }

        spawnManager.spreadPlayersToGameWorld(readyPlayers);
        conditionManager.assignHiddenConditions(readyPlayers);

        gameState = GameState.RUNNING;
        conditionManager.startRevealTask();
        powerupManager.startRound();
        taskManager.startRound();
        startBorderShrink(gameWorld);

        uiManager.broadcast(plugin.getConfig().getString("messages.game-start", "游戏开始。"));
        uiManager.broadcast("本局玩家数：" + readyPlayers.size());
        return true;
    }

    public boolean stopGame(Player sender) {
        if (gameState == GameState.WAITING) {
            uiManager.warn(sender, "当前没有进行中的游戏。");
            return false;
        }

        finishGame(null, "管理员结束了游戏。");
        return true;
    }

    public boolean eliminatePlayer(Player player, String reason) {
        if (gameState != GameState.RUNNING) {
            return false;
        }

        UUID playerId = player.getUniqueId();
        if (!activePlayers.contains(playerId)) {
            return false;
        }

        PlayerGameData data = playerDataManager.get(playerId);
        if (data == null || data.isEliminated()) {
            return false;
        }

        data.setEliminated(true);
        data.setSpectator(true);
        eliminationOrder.add(playerId);

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.setGameMode(GameMode.SPECTATOR);
            }
        });

        uiManager.broadcast(player.getName() + " 被淘汰，原因：" + reason);
        checkWinCondition();
        return true;
    }

    public void handlePlayerKilled(Player victim, Player killer) {
        if (killer != null) {
            eliminatePlayer(victim, "被 " + killer.getName() + " 击杀");
        } else {
            eliminatePlayer(victim, "死亡");
        }
    }

    public void handleQuit(Player player) {
        if (gameState != GameState.RUNNING) {
            return;
        }

        if (isActivePlayer(player.getUniqueId())) {
            eliminatePlayer(player, "中途离开游戏");
        }
    }

    public void ensureSpectatorState(Player player) {
        if (gameState != GameState.RUNNING) {
            return;
        }

        PlayerGameData data = playerDataManager.get(player.getUniqueId());
        if (data != null && data.isEliminated() && player.getGameMode() != GameMode.SPECTATOR) {
            player.setGameMode(GameMode.SPECTATOR);
        }
    }

    private void checkWinCondition() {
        if (gameState != GameState.RUNNING) {
            return;
        }

        List<UUID> alive = new ArrayList<>();
        for (UUID playerId : activePlayers) {
            PlayerGameData data = playerDataManager.get(playerId);
            if (data != null && !data.isEliminated()) {
                alive.add(playerId);
            }
        }

        if (alive.size() > 1) {
            return;
        }

        Player winner = null;
        if (alive.size() == 1) {
            winner = plugin.getServer().getPlayer(alive.getFirst());
        }

        finishGame(winner, null);
    }

    public void finishGame(Player winner, String forcedReason) {
        gameState = GameState.ENDING;

        conditionManager.stopRevealTask();
        powerupManager.stopRound();
        taskManager.stopRound();
        stopBorderShrink();

        List<FinalStanding> standings = buildFinalStandings();
        FinalStanding champion = standings.isEmpty() ? null : standings.getFirst();

        if (forcedReason != null) {
            uiManager.broadcast(forcedReason);
        }

        if (champion != null) {
            String template = plugin.getConfig().getString("messages.winner", "恭喜 {player} 获胜！");
            String winnerMessage = template.replace("{player}", champion.playerName());
            String prefixText = plugin.getConfig().getString("messages.prefix", "[隐藏淘汰] ").trim();
            uiManager.showCenterTitleToAll(winnerMessage, prefixText);

            uiManager.broadcast("本局按生存排名与任务积分综合评分决出胜者。");
            uiManager.broadcast("冠军详情：总分=" + champion.compositeScore()
                    + "，积分=" + champion.taskPoints()
                    + "，生存排名=#" + champion.placementRank());

            uiManager.broadcast("本局综合排行榜前3名：");
            int top = Math.min(3, standings.size());
            for (int i = 0; i < top; i++) {
                FinalStanding s = standings.get(i);
                uiManager.broadcast("#" + (i + 1) + " " + s.playerName()
                        + "（总分=" + s.compositeScore()
                        + "，积分=" + s.taskPoints()
                        + "，生存排名=#" + s.placementRank()
                        + "）");
            }
        } else if (winner != null) {
            String template = plugin.getConfig().getString("messages.winner", "恭喜 {player} 获胜！");
            String winnerMessage = template.replace("{player}", winner.getName());
            String prefixText = plugin.getConfig().getString("messages.prefix", "[隐藏淘汰] ").trim();
            uiManager.showCenterTitleToAll(winnerMessage, prefixText);
        } else {
            uiManager.broadcast("本局无胜者。");
        }

        Set<UUID> needBack = new HashSet<>(activePlayers);
        for (Player joined : playerDataManager.getJoinedOnlinePlayers()) {
            needBack.add(joined.getUniqueId());
        }

        for (UUID uuid : needBack) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                continue;
            }
            player.setGameMode(GameMode.ADVENTURE);
            clearPlayerInventory(player);
            giveLobbyItems(player);
            spawnManager.teleportToLobby(player);
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (UUID uuid : needBack) {
                Player player = plugin.getServer().getPlayer(uuid);
                if (player == null || !player.isOnline()) {
                    continue;
                }
                player.setGameMode(GameMode.ADVENTURE);
                spawnManager.teleportToLobby(player);
            }
        }, 20L);

        playerDataManager.resetAllRoundState();
        activePlayers.clear();
        eliminationOrder.clear();

        uiManager.broadcast(plugin.getConfig().getString("messages.game-end", "本局结束。"));
        gameState = GameState.WAITING;
    }

    public void shutdown() {
        conditionManager.stopRevealTask();
        powerupManager.stopRound();
        taskManager.stopRound();
        stopBorderShrink();
    }

    private void startBorderShrink(World world) {
        stopBorderShrink();

        if (!plugin.getConfig().getBoolean("world-border.enabled", false)) {
            return;
        }

        double startSize = Math.max(16.0D, plugin.getConfig().getDouble("world-border.start-size", 2000.0D));
        double endSize = Math.max(16.0D, plugin.getConfig().getDouble("world-border.end-size", 100.0D));
        if (endSize >= startSize) {
            endSize = Math.max(16.0D, startSize - 1.0D);
        }

        long startDelaySeconds = Math.max(0L, plugin.getConfig().getLong("world-border.start-delay-seconds", 0L));
        long durationSeconds = Math.max(30L, plugin.getConfig().getLong("world-border.shrink-duration-seconds", 1200L));
        long announceIntervalSeconds = Math.max(0L, plugin.getConfig().getLong("world-border.announce-interval-seconds", 60L));

        WorldBorder border = world.getWorldBorder();
        if (plugin.getConfig().getBoolean("world-border.use-world-spawn", true)) {
            Location spawn = world.getSpawnLocation();
            border.setCenter(spawn.getX(), spawn.getZ());
        } else {
            double centerX = plugin.getConfig().getDouble("world-border.center-x", 0.0D);
            double centerZ = plugin.getConfig().getDouble("world-border.center-z", 0.0D);
            border.setCenter(centerX, centerZ);
        }

        border.setSize(startSize);
        this.borderWorld = world;
        this.borderResetSize = startSize;
        this.borderShrinking = false;
        this.borderShrinkStartMillis = System.currentTimeMillis() + startDelaySeconds * 1000L;
        this.borderShrinkEndMillis = 0L;

        uiManager.broadcast("世界边界已设置，初始大小：" + (int) startSize);
        if (startDelaySeconds > 0) {
            uiManager.broadcast("世界边界将在 " + startDelaySeconds + " 秒后开始收缩。");
        }

        double finalEndSize = endSize;
        this.borderStartTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (gameState != GameState.RUNNING) {
                return;
            }

            border.setSize(finalEndSize, durationSeconds);
            borderShrinking = true;
            borderShrinkStartMillis = System.currentTimeMillis();
            borderShrinkEndMillis = borderShrinkStartMillis + durationSeconds * 1000L;

            uiManager.broadcast("世界边界开始收缩，目标大小：" + (int) finalEndSize + "，持续：" + durationSeconds + " 秒。");

            if (announceIntervalSeconds > 0L) {
                borderAnnounceTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
                    if (gameState != GameState.RUNNING || !borderShrinking) {
                        if (borderAnnounceTask != null) {
                            borderAnnounceTask.cancel();
                            borderAnnounceTask = null;
                        }
                        return;
                    }

                    long remain = Math.max(0L, (borderShrinkEndMillis - System.currentTimeMillis()) / 1000L);
                    if (remain <= 0L) {
                        borderShrinking = false;
                        uiManager.broadcast("世界边界收缩结束，最终大小：" + (int) finalEndSize);
                        if (borderAnnounceTask != null) {
                            borderAnnounceTask.cancel();
                            borderAnnounceTask = null;
                        }
                        return;
                    }

                    uiManager.broadcast("世界边界收缩中：当前大小 " + (int) border.getSize() + "，剩余 " + formatDuration(remain));
                }, announceIntervalSeconds * 20L, announceIntervalSeconds * 20L);
            }
        }, startDelaySeconds * 20L);
    }

    private void stopBorderShrink() {
        if (borderStartTask != null) {
            borderStartTask.cancel();
            borderStartTask = null;
        }

        if (borderAnnounceTask != null) {
            borderAnnounceTask.cancel();
            borderAnnounceTask = null;
        }

        borderShrinking = false;
        borderShrinkStartMillis = 0L;
        borderShrinkEndMillis = 0L;

        if (borderWorld != null && plugin.getConfig().getBoolean("world-border.reset-after-game", true)) {
            borderWorld.getWorldBorder().setSize(Math.max(16.0D, borderResetSize));
        }

        borderWorld = null;
        borderResetSize = 0.0D;
    }

    private void resetGameWorldEnvironment(World world) {
        boolean resetTime = plugin.getConfig().getBoolean("game.reset-time-each-round", true);
        long startTimeTicks = Math.max(0L, plugin.getConfig().getLong("game.start-time-ticks", 1000L));
        if (resetTime) {
            world.setTime(startTimeTicks);
        }

        boolean clearWeather = plugin.getConfig().getBoolean("game.clear-weather-each-round", true);
        if (clearWeather) {
            world.setStorm(false);
            world.setThundering(false);
            int clearSeconds = (int) Math.max(0L, plugin.getConfig().getLong("game.clear-weather-duration-seconds", 1200L));
            world.setClearWeatherDuration(clearSeconds * 20);
        }
    }

    private String formatDuration(long seconds) {
        long minutes = seconds / 60L;
        long sec = seconds % 60L;
        return String.format("%02d:%02d", minutes, sec);
    }

    private void clearPlayerInventory(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);
    }

    private void giveLobbyItems(Player player) {
        player.getInventory().setItem(PrepareItemListener.READY_ITEM_SLOT, PrepareItemListener.createReadyItem(false));
        if (player.hasPermission("hiddenelimination.admin") || player.isOp()) {
            player.getInventory().setItem(PrepareItemListener.START_ITEM_SLOT, PrepareItemListener.createStartItem());
        }
    }

    private List<FinalStanding> buildFinalStandings() {
        if (activePlayers.isEmpty()) {
            return List.of();
        }

        List<UUID> participants = new ArrayList<>(activePlayers);
        List<UUID> alivePlayers = new ArrayList<>();
        for (UUID playerId : participants) {
            PlayerGameData data = playerDataManager.get(playerId);
            if (data != null && !data.isEliminated()) {
                alivePlayers.add(playerId);
            }
        }

        alivePlayers.sort(Comparator
                .comparingInt((UUID id) -> taskManager.getPlayerTaskPoints(id))
                .reversed()
                .thenComparing(this::resolvePlayerName));

        Map<UUID, Integer> placementByPlayer = new HashMap<>();
        int rankCursor = 1;

        for (UUID aliveId : alivePlayers) {
            placementByPlayer.put(aliveId, rankCursor++);
        }

        for (int i = eliminationOrder.size() - 1; i >= 0; i--) {
            UUID eliminatedId = eliminationOrder.get(i);
            if (!participants.contains(eliminatedId) || placementByPlayer.containsKey(eliminatedId)) {
                continue;
            }
            placementByPlayer.put(eliminatedId, rankCursor++);
        }

        for (UUID playerId : participants) {
            if (!placementByPlayer.containsKey(playerId)) {
                placementByPlayer.put(playerId, rankCursor++);
            }
        }

        int totalPlayers = participants.size();
        int placementWeight = Math.max(0, plugin.getConfig().getInt("result-scoring.placement-weight", 10));
        int taskPointWeight = Math.max(0, plugin.getConfig().getInt("result-scoring.task-points-weight", 1));

        List<FinalStanding> standings = new ArrayList<>();
        for (UUID playerId : participants) {
            int placementRank = placementByPlayer.getOrDefault(playerId, totalPlayers);
            int placementScore = Math.max(1, totalPlayers - placementRank + 1);
            int taskPoints = taskManager.getPlayerTaskPoints(playerId);
            int compositeScore = placementScore * placementWeight + taskPoints * taskPointWeight;
            boolean aliveAtEnd = alivePlayers.contains(playerId);

            standings.add(new FinalStanding(
                    playerId,
                    resolvePlayerName(playerId),
                    placementRank,
                    taskPoints,
                    compositeScore,
                    aliveAtEnd
            ));
        }

        standings.sort(Comparator
                .comparingInt(FinalStanding::compositeScore)
                .reversed()
                .thenComparing(Comparator.comparingInt(FinalStanding::taskPoints).reversed())
                .thenComparingInt(FinalStanding::placementRank)
                .thenComparing(FinalStanding::playerName));

        return standings;
    }

    private String resolvePlayerName(UUID playerId) {
        Player online = plugin.getServer().getPlayer(playerId);
        if (online != null) {
            return online.getName();
        }

        String offlineName = plugin.getServer().getOfflinePlayer(playerId).getName();
        return offlineName == null || offlineName.isBlank() ? playerId.toString() : offlineName;
    }

    private record FinalStanding(
            UUID playerId,
            String playerName,
            int placementRank,
            int taskPoints,
            int compositeScore,
            boolean aliveAtEnd
    ) {
    }
}
