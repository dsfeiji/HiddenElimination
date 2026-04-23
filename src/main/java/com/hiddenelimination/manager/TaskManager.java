package com.hiddenelimination.manager;

import com.hiddenelimination.HiddenEliminationPlugin;
import com.hiddenelimination.model.ConditionType;
import com.hiddenelimination.model.PlayerGameData;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class TaskManager {

    public enum TaskType {
        EAT_ANY("吃东西", 1),
        KILL_ZOMBIE("击杀僵尸", 2),
        KILL_CREEPER("击杀苦力怕", 2),
        KILL_SKELETON("击杀小白", 2),
        EAT_ROTTEN_FLESH("吃腐肉", 2),
        JUMP("跳跃", 1),
        ADV_A_SEEDY_PLACE("获得【开垦荒地】成就", 2),
        OPEN_DOOR("打开一扇门", 1),
        CRAFT_LIGHTNING_ROD("制作避雷针", 3),
        CRAFT_SPYGLASS("制作望远镜", 3),
        CRAFT_COMPASS("制作指南针", 2),
        CRAFT_CLOCK("制作钟", 2),
        CRAFT_CAMPFIRE("制作篝火", 2),
        CRAFT_RAIL("制作铁轨", 2),
        CRAFT_POWERED_RAIL("制作动力铁轨", 3),
        CRAFT_CHEST_BOAT("制作箱船", 3),
        STAND_ON_BEDROCK("站在基岩上", 2),
        CRAFT_ANVIL("制作铁砧", 3),
        KILL_PLAYER("击杀玩家", 3),
        TOUCH_PLAYER("与其他玩家贴贴", 1),
        SWIM("游泳", 1),
        CLIMB("攀爬", 1),
        ADV_TAKE_AIM("获得【瞄准目标】成就", 2),
        PRESS_BUTTON("按下按钮", 1),
        USE_LEVER("按下拉杆", 1),
        ADV_WHENCE_CAME_YOU("获得【我从哪里来】成就", 3),
        DIE("死亡", 3),
        CRAFT_IRON_PICKAXE("制作铁镐", 2),
        CRAFT_IRON_BLOCK("制作铁块", 3),
        STAND_ON_FARMLAND("站在耕地上", 1),
        STAND_ON_DIRT_PATH("站在草径上", 1),
        STAND_ON_SMOOTH_STONE("站在平滑石头上", 2),
        STAND_ON_POLISHED_GRANITE("站在磨制花岗岩上", 2),
        STAND_ON_POLISHED_DIORITE("站在磨制闪长岩上", 2);

        private final String displayName;
        private final int difficultyTier;

        TaskType(String displayName, int difficultyTier) {
            this.displayName = displayName;
            this.difficultyTier = difficultyTier;
        }

        public String displayName() {
            return displayName;
        }

        public int difficultyTier() {
            return difficultyTier;
        }
    }

    public record GlobalTask(int index, TaskType taskType, int requiredCount, int timeLimitSeconds, int difficultyTier) {
    }

    private static final String ADV_A_SEEDY_PLACE = "minecraft:husbandry/plant_seed";
    private static final String ADV_TAKE_AIM = "minecraft:adventure/shoot_arrow";
    private static final Set<String> ADV_WHENCE_CAME_YOU_KEYS = Set.of(
            "minecraft:nether/root",
            "minecraft:story/root"
    );

    private final HiddenEliminationPlugin plugin;
    private final PlayerDataManager playerDataManager;
    private final UIManager uiManager;
    private final Random random = new Random();

    private final Map<UUID, Integer> progressByPlayer = new ConcurrentHashMap<>();
    private final List<UUID> completionOrder = new ArrayList<>();

    private GameManager gameManager;
    private PowerupManager powerupManager;
    private BukkitTask nextTaskPublishTask;
    private BukkitTask taskDeadlineTask;

    private GlobalTask currentTask;
    private TaskType lastTaskType;
    private int taskSequence;
    private long roundStartEpochSecond;
    private long nextTaskPublishEpochSecond;
    private long taskDeadlineEpochSecond;

    public TaskManager(HiddenEliminationPlugin plugin, PlayerDataManager playerDataManager, UIManager uiManager) {
        this.plugin = plugin;
        this.playerDataManager = playerDataManager;
        this.uiManager = uiManager;
    }

    public void bindGameManager(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    public void bindPowerupManager(PowerupManager powerupManager) {
        this.powerupManager = powerupManager;
    }

    public void startRound() {
        stopRound();

        if (!isEnabled()) {
            return;
        }

        this.roundStartEpochSecond = nowSecond();
        this.taskSequence = 0;

        int initialLives = gameManager == null
                ? Math.max(1, plugin.getConfig().getInt("tasks.lives-per-player", 3))
                : gameManager.getRoundInitialLives();
        if (gameManager != null) {
            for (UUID playerId : gameManager.getActivePlayersSnapshot()) {
                PlayerGameData data = playerDataManager.get(playerId);
                if (data == null) {
                    continue;
                }
                data.setTaskLivesRemaining(initialLives);
                data.setTaskPoints(0);
            }
        }

        scheduleNextTaskPublish();
    }

    public void stopRound() {
        if (nextTaskPublishTask != null) {
            nextTaskPublishTask.cancel();
            nextTaskPublishTask = null;
        }

        if (taskDeadlineTask != null) {
            taskDeadlineTask.cancel();
            taskDeadlineTask = null;
        }

        currentTask = null;
        progressByPlayer.clear();
        completionOrder.clear();
        roundStartEpochSecond = 0L;
        nextTaskPublishEpochSecond = 0L;
        taskDeadlineEpochSecond = 0L;
    }

    public void handleConditionAction(Player player, ConditionType conditionType) {
        if (!isEnabled()) {
            return;
        }

        switch (conditionType) {
            case JUMP -> markProgress(player, TaskType.JUMP);
            case EAT_FOOD -> markProgress(player, TaskType.EAT_ANY);
            case ENTER_WATER -> markProgress(player, TaskType.SWIM);
            default -> {
            }
        }
    }

    public void handleConsumeItem(Player player, Material material) {
        if (!isEnabled()) {
            return;
        }

        if (material == Material.ROTTEN_FLESH) {
            markProgress(player, TaskType.EAT_ROTTEN_FLESH);
        }
    }

    public void handleEntityKill(Player killer, EntityType entityType) {
        if (!isEnabled()) {
            return;
        }

        if (entityType == EntityType.ZOMBIE) {
            markProgress(killer, TaskType.KILL_ZOMBIE);
            return;
        }

        if (entityType == EntityType.CREEPER) {
            markProgress(killer, TaskType.KILL_CREEPER);
            return;
        }

        if (entityType == EntityType.SKELETON) {
            markProgress(killer, TaskType.KILL_SKELETON);
        }
    }

    public void handlePlayerKill(Player killer) {
        if (!isEnabled()) {
            return;
        }

        markProgress(killer, TaskType.KILL_PLAYER);
    }

    public void handlePlayerDeath(Player player) {
        if (!isEnabled()) {
            return;
        }

        markProgress(player, TaskType.DIE);
    }

    public void handleAdvancement(Player player, String advancementKey) {
        if (!isEnabled()) {
            return;
        }

        String normalized = advancementKey.toLowerCase(Locale.ROOT);
        if (ADV_A_SEEDY_PLACE.equals(normalized)) {
            markProgress(player, TaskType.ADV_A_SEEDY_PLACE);
            return;
        }

        if (ADV_TAKE_AIM.equals(normalized)) {
            markProgress(player, TaskType.ADV_TAKE_AIM);
            return;
        }

        if (ADV_WHENCE_CAME_YOU_KEYS.contains(normalized)) {
            markProgress(player, TaskType.ADV_WHENCE_CAME_YOU);
        }
    }

    public void handleCraft(Player player, Material resultType) {
        if (!isEnabled()) {
            return;
        }

        switch (resultType) {
            case LIGHTNING_ROD -> markProgress(player, TaskType.CRAFT_LIGHTNING_ROD);
            case SPYGLASS -> markProgress(player, TaskType.CRAFT_SPYGLASS);
            case COMPASS -> markProgress(player, TaskType.CRAFT_COMPASS);
            case CLOCK -> markProgress(player, TaskType.CRAFT_CLOCK);
            case CAMPFIRE -> markProgress(player, TaskType.CRAFT_CAMPFIRE);
            case RAIL -> markProgress(player, TaskType.CRAFT_RAIL);
            case POWERED_RAIL -> markProgress(player, TaskType.CRAFT_POWERED_RAIL);
            case ANVIL -> markProgress(player, TaskType.CRAFT_ANVIL);
            case IRON_PICKAXE -> markProgress(player, TaskType.CRAFT_IRON_PICKAXE);
            case IRON_BLOCK -> markProgress(player, TaskType.CRAFT_IRON_BLOCK);
            default -> {
                String name = resultType.name();
                if (name.endsWith("_CHEST_BOAT") || name.endsWith("_CHEST_RAFT")) {
                    markProgress(player, TaskType.CRAFT_CHEST_BOAT);
                }
            }
        }
    }

    public void handleInteractBlock(Player player, Material blockType) {
        if (!isEnabled()) {
            return;
        }

        String name = blockType.name();
        if (name.endsWith("_DOOR")) {
            markProgress(player, TaskType.OPEN_DOOR);
        }
        if (name.endsWith("_BUTTON")) {
            markProgress(player, TaskType.PRESS_BUTTON);
        }
        if (blockType == Material.LEVER) {
            markProgress(player, TaskType.USE_LEVER);
        }
    }

    public void handleStandOnBlock(Player player, Material blockType) {
        if (!isEnabled()) {
            return;
        }

        switch (blockType) {
            case BEDROCK -> markProgress(player, TaskType.STAND_ON_BEDROCK);
            case FARMLAND -> markProgress(player, TaskType.STAND_ON_FARMLAND);
            case DIRT_PATH -> markProgress(player, TaskType.STAND_ON_DIRT_PATH);
            case SMOOTH_STONE -> markProgress(player, TaskType.STAND_ON_SMOOTH_STONE);
            case POLISHED_GRANITE -> markProgress(player, TaskType.STAND_ON_POLISHED_GRANITE);
            case POLISHED_DIORITE -> markProgress(player, TaskType.STAND_ON_POLISHED_DIORITE);
            default -> {
            }
        }
    }

    public void handleSwimState(Player player) {
        if (!isEnabled()) {
            return;
        }

        if (player.isSwimming()) {
            markProgress(player, TaskType.SWIM);
        }
    }

    public void handleClimbState(Player player) {
        if (!isEnabled()) {
            return;
        }

        if (player.isClimbing()) {
            markProgress(player, TaskType.CLIMB);
        }
    }

    public void handlePlayerContact(Player player) {
        if (!isEnabled()) {
            return;
        }

        for (Entity entity : player.getNearbyEntities(0.6D, 1.0D, 0.6D)) {
            if (entity instanceof Player other && !other.getUniqueId().equals(player.getUniqueId())) {
                markProgress(player, TaskType.TOUCH_PLAYER);
                return;
            }
        }
    }

    public boolean hasActiveTask() {
        return currentTask != null && taskDeadlineTask != null;
    }

    public String getCurrentTaskDisplay() {
        GlobalTask task = currentTask;
        if (task == null) {
            return "无";
        }
        return task.taskType().displayName() + " [难度 " + task.difficultyTier() + "]";
    }

    public int getCurrentTaskRequiredCount() {
        GlobalTask task = currentTask;
        return task == null ? 0 : task.requiredCount();
    }

    public int getPlayerProgress(UUID playerId) {
        if (currentTask == null) {
            return 0;
        }
        return progressByPlayer.getOrDefault(playerId, 0);
    }

    public int getPlayerTaskPoints(UUID playerId) {
        PlayerGameData data = playerDataManager.get(playerId);
        return data == null ? 0 : data.getTaskPoints();
    }

    public int getPlayerTotalEarnedTaskPoints(UUID playerId) {
        PlayerGameData data = playerDataManager.get(playerId);
        return data == null ? 0 : data.getTotalEarnedTaskPoints();
    }

    public int getPlayerTaskLives(UUID playerId) {
        PlayerGameData data = playerDataManager.get(playerId);
        return data == null ? 0 : data.getTaskLivesRemaining();
    }

    public long getSecondsUntilTaskDeadline() {
        if (!hasActiveTask() || taskDeadlineEpochSecond <= 0L) {
            return 0L;
        }
        return Math.max(0L, taskDeadlineEpochSecond - nowSecond());
    }

    public long getSecondsUntilNextTaskPublish() {
        if (!isEnabled()) {
            return 0L;
        }

        if (nextTaskPublishTask == null || nextTaskPublishEpochSecond <= 0L || gameManager == null || !gameManager.isRunning()) {
            return 0L;
        }
        return Math.max(0L, nextTaskPublishEpochSecond - nowSecond());
    }

    private void scheduleNextTaskPublish() {
        if (!isEnabled()) {
            return;
        }

        if (gameManager == null || !gameManager.isRunning()) {
            return;
        }

        long minInterval = Math.max(60L, plugin.getConfig().getLong("tasks.publish-interval-seconds-min", 60L));
        long maxInterval = Math.max(minInterval, plugin.getConfig().getLong("tasks.publish-interval-seconds-max", 60L));
        long delaySeconds = randomLong(minInterval, maxInterval);

        if (nextTaskPublishTask != null) {
            nextTaskPublishTask.cancel();
        }

        nextTaskPublishEpochSecond = nowSecond() + delaySeconds;
        nextTaskPublishTask = plugin.getServer().getScheduler().runTaskLater(plugin, this::publishTask, delaySeconds * 20L);
    }

    private void publishTask() {
        nextTaskPublishTask = null;
        nextTaskPublishEpochSecond = 0L;

        if (!isEnabled()) {
            return;
        }

        if (gameManager == null || !gameManager.isRunning()) {
            return;
        }

        if (currentTask != null) {
            return;
        }

        int difficultyTier = resolveDifficultyTier();
        TaskType taskType = randomTaskType(difficultyTier);
        int requiredCount = 1;
        int timeLimitSeconds = resolveTimeLimitSeconds(difficultyTier);

        this.currentTask = new GlobalTask(++taskSequence, taskType, requiredCount, timeLimitSeconds, difficultyTier);
        this.progressByPlayer.clear();
        this.completionOrder.clear();

        Set<UUID> activePlayers = gameManager.getActivePlayersSnapshot();
        for (UUID playerId : activePlayers) {
            PlayerGameData data = playerDataManager.get(playerId);
            if (data != null && !data.isEliminated()) {
                progressByPlayer.put(playerId, 0);
            }
        }

        if (progressByPlayer.isEmpty()) {
            clearCurrentTask();
            scheduleNextTaskPublish();
            return;
        }

        uiManager.broadcast("[任务] 全员任务 #" + currentTask.index() + " 已发布，难度 " + difficultyTier + "。");
        uiManager.broadcast("[任务] 内容：" + taskType.displayName() + "（限时 " + timeLimitSeconds + " 秒）");
        uiManager.playSoundToAll(Sound.BLOCK_NOTE_BLOCK_PLING, 0.8F, 1.4F);

        taskDeadlineEpochSecond = nowSecond() + timeLimitSeconds;
        taskDeadlineTask = plugin.getServer().getScheduler().runTaskLater(plugin, this::handleTaskDeadline, timeLimitSeconds * 20L);

        lastTaskType = taskType;
    }

    private void handleTaskDeadline() {
        taskDeadlineTask = null;

        GlobalTask task = currentTask;
        if (task == null || gameManager == null || !gameManager.isRunning()) {
            clearCurrentTask();
            return;
        }

        int penalty = Math.max(0, plugin.getConfig().getInt("tasks.failure-point-penalty", 4));
        int lifeLostPlayers = 0;
        int eliminatedCount = 0;

        for (UUID playerId : gameManager.getActivePlayersSnapshot()) {
            PlayerGameData data = playerDataManager.get(playerId);
            if (data == null || data.isEliminated() || completionOrder.contains(playerId)) {
                continue;
            }

            Player player = plugin.getServer().getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                continue;
            }

            data.deductTaskPoints(penalty);
            int leftLives = data.consumeTaskLife();
            lifeLostPlayers++;

            if (leftLives <= 0) {
                boolean eliminated = gameManager.eliminatePlayer(player, "任务失败且生命耗尽：" + task.taskType().displayName());
                if (eliminated) {
                    eliminatedCount++;
                }
            } else {
                uiManager.warn(player, "任务失败：-" + penalty + " 积分，剩余生命 " + leftLives);
            }
        }

        uiManager.broadcast("[任务] 任务 #" + task.index() + " 结算："
                + lifeLostPlayers + " 人扣除生命，"
                + eliminatedCount + " 人淘汰。");
        clearCurrentTask();
        scheduleNextTaskPublish();
    }

    private void markProgress(Player player, TaskType action) {
        if (!hasActiveTask()) {
            return;
        }

        GlobalTask task = currentTask;
        if (task == null || task.taskType() != action) {
            return;
        }

        UUID playerId = player.getUniqueId();
        if (completionOrder.contains(playerId) || !progressByPlayer.containsKey(playerId)) {
            return;
        }

        if (gameManager == null || !gameManager.isActivePlayer(playerId)) {
            return;
        }

        PlayerGameData data = playerDataManager.get(playerId);
        if (data == null || data.isEliminated()) {
            return;
        }

        progressByPlayer.put(playerId, task.requiredCount());

        int rank = completionOrder.size() + 1;
        completionOrder.add(playerId);

        int gainedPoints = calculatePoints(rank, task.difficultyTier());
        data.addTaskPoints(gainedPoints);
        data.incrementCompletedTaskCount();

        if (rank == 1 && powerupManager != null) {
            powerupManager.rewardFirstFinisher(player);
        }

        uiManager.success(player, "任务完成，排名 #" + rank + "，+" + gainedPoints + " 积分。");
        uiManager.broadcast("[任务] " + player.getName() + " 完成了任务 #" + task.index() + "，排名 #" + rank + "。");

        if (allAlivePlayersFinished()) {
            uiManager.broadcast("[任务] 所有存活玩家都完成了本任务，本轮无人扣命。");
            clearCurrentTask();
            scheduleNextTaskPublish();
        }
    }

    private void clearCurrentTask() {
        if (taskDeadlineTask != null) {
            taskDeadlineTask.cancel();
            taskDeadlineTask = null;
        }

        currentTask = null;
        progressByPlayer.clear();
        completionOrder.clear();
        taskDeadlineEpochSecond = 0L;
    }

    private boolean allAlivePlayersFinished() {
        if (gameManager == null || !gameManager.isRunning()) {
            return false;
        }

        for (UUID playerId : gameManager.getActivePlayersSnapshot()) {
            PlayerGameData data = playerDataManager.get(playerId);
            if (data == null || data.isEliminated()) {
                continue;
            }

            if (!completionOrder.contains(playerId)) {
                return false;
            }
        }

        return true;
    }

    private TaskType randomTaskType(int difficultyTier) {
        List<TaskType> pool = new ArrayList<>();
        for (TaskType type : TaskType.values()) {
            if (type.difficultyTier() == difficultyTier) {
                pool.add(type);
            }
        }

        if (pool.isEmpty()) {
            return TaskType.JUMP;
        }

        if (pool.size() > 1 && lastTaskType != null) {
            pool.remove(lastTaskType);
        }

        return pool.get(random.nextInt(pool.size()));
    }

    private int resolveDifficultyTier() {
        int easyOnlyTaskCount = Math.max(0, plugin.getConfig().getInt("tasks.easy-only-task-count", 4));
        int nextTaskIndex = taskSequence + 1;
        if (nextTaskIndex <= easyOnlyTaskCount) {
            return 1;
        }

        long upgradeInterval = Math.max(60L, plugin.getConfig().getLong("tasks.difficulty-upgrade-interval-seconds", 360L));
        long elapsed = Math.max(0L, nowSecond() - roundStartEpochSecond);

        int tier = 1 + (int) (elapsed / upgradeInterval);
        return Math.min(3, Math.max(1, tier));
    }

    private int resolveTimeLimitSeconds(int difficultyTier) {
        String path = switch (difficultyTier) {
            case 1 -> "tasks.time-limit-seconds.easy";
            case 2 -> "tasks.time-limit-seconds.medium";
            default -> "tasks.time-limit-seconds.hard";
        };

        return Math.max(15, plugin.getConfig().getInt(path, 90 - (difficultyTier - 1) * 15));
    }

    private int calculatePoints(int rank, int difficultyTier) {
        int rankBase;
        if (rank == 1) {
            rankBase = plugin.getConfig().getInt("tasks.points.rank-1", 10);
        } else if (rank == 2) {
            rankBase = plugin.getConfig().getInt("tasks.points.rank-2", 7);
        } else if (rank == 3) {
            rankBase = plugin.getConfig().getInt("tasks.points.rank-3", 5);
        } else {
            rankBase = plugin.getConfig().getInt("tasks.points.rank-other", 3);
        }

        int perTierBonus = Math.max(0, plugin.getConfig().getInt("tasks.points.difficulty-bonus-per-tier", 2));
        return rankBase + Math.max(0, difficultyTier - 1) * perTierBonus;
    }

    private long randomLong(long min, long max) {
        if (min >= max) {
            return min;
        }
        return ThreadLocalRandom.current().nextLong(min, max + 1);
    }

    private long nowSecond() {
        return System.currentTimeMillis() / 1000L;
    }

    private boolean isEnabled() {
        return plugin.getConfig().getBoolean("tasks.enabled", true);
    }
}
