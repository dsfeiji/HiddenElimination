package com.hiddenelimination.manager;

import com.hiddenelimination.HiddenEliminationPlugin;
import com.hiddenelimination.model.ConditionType;
import com.hiddenelimination.model.PlayerGameData;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class ConditionManager {

    public record RevealedCondition(ConditionType conditionType, boolean fake, long activeAtMillis) {
    }

    private final HiddenEliminationPlugin plugin;
    private final PlayerDataManager playerDataManager;
    private final UIManager uiManager;
    private final Random random = new Random();

    private final List<RevealedCondition> revealedConditions = new ArrayList<>();
    private final Set<ConditionType> triggeredConditions = new HashSet<>();

    private GameManager gameManager;
    private TaskManager taskManager;
    private PowerupManager powerupManager;
    private BukkitTask revealTask;
    private long revealIntervalSeconds = 180L;
    private long nextRevealEpochSecond = 0L;
    private static final long RULE_ENABLE_DELAY_MILLIS = 3000L;

    public ConditionManager(HiddenEliminationPlugin plugin, PlayerDataManager playerDataManager, UIManager uiManager) {
        this.plugin = plugin;
        this.playerDataManager = playerDataManager;
        this.uiManager = uiManager;
    }

    public void bindGameManager(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    public void bindTaskManager(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    public void bindPowerupManager(PowerupManager powerupManager) {
        this.powerupManager = powerupManager;
    }

    public int getConditionPoolSize() {
        return ConditionType.values().length;
    }

    public void assignHiddenConditions(List<Player> players) {
        revealedConditions.clear();
        triggeredConditions.clear();

        List<ConditionType> pool = new ArrayList<>(List.of(ConditionType.values()));
        Collections.shuffle(pool, random);

        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            PlayerGameData data = playerDataManager.getOrCreate(player.getUniqueId());

            ConditionType uniqueCondition = pool.get(i);
            data.setAssignedCondition(uniqueCondition);
            data.setConditionRevealed(false);
            data.setConditionActiveAtMillis(0L);

            uiManager.info(player, "你的隐藏淘汰条件已分配。");
        }
    }

    public void startRevealTask(long intervalSeconds) {
        stopRevealTask();

        this.revealIntervalSeconds = Math.max(10L, intervalSeconds);
        long intervalTicks = revealIntervalSeconds * 20L;
        this.nextRevealEpochSecond = nowSecond() + revealIntervalSeconds;

        this.revealTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                () -> {
                    revealRandomCondition();
                    nextRevealEpochSecond = nowSecond() + revealIntervalSeconds;
                },
                intervalTicks,
                intervalTicks
        );
    }

    public void stopRevealTask() {
        if (revealTask != null) {
            revealTask.cancel();
            revealTask = null;
        }
        nextRevealEpochSecond = 0L;
    }

    public void revealFakeCondition(ConditionType conditionType) {
        if (gameManager == null || !gameManager.isRunning() || conditionType == null) {
            return;
        }
        revealedConditions.add(new RevealedCondition(conditionType, true, System.currentTimeMillis()));
        uiManager.broadcast("[规则] 规则公开：" + conditionType.getDisplayName());
    }

    public void revealRandomCondition() {
        if (gameManager == null || !gameManager.isRunning()) {
            return;
        }

        Set<UUID> activePlayers = gameManager.getActivePlayersSnapshot();
        List<PlayerGameData> unrevealedAlive = new ArrayList<>();

        for (UUID playerId : activePlayers) {
            PlayerGameData data = playerDataManager.get(playerId);
            if (data == null || data.isEliminated() || data.isConditionRevealed() || data.getAssignedCondition() == null) {
                continue;
            }
            unrevealedAlive.add(data);
        }

        if (unrevealedAlive.isEmpty()) {
            return;
        }

        PlayerGameData selected = unrevealedAlive.get(random.nextInt(unrevealedAlive.size()));
        selected.setConditionRevealed(true);
        long activeAt = System.currentTimeMillis() + RULE_ENABLE_DELAY_MILLIS;
        selected.setConditionActiveAtMillis(activeAt);

        ConditionType conditionType = selected.getAssignedCondition();
        revealedConditions.add(new RevealedCondition(conditionType, false, activeAt));

        uiManager.broadcast("[规则] 规则公开：" + conditionType.getDisplayName() + "（3秒后生效）");
        uiManager.showRuleRevealTitleToAll(conditionType.getDisplayName(), 3);
        uiManager.playSoundToAll(Sound.BLOCK_BELL_USE, 0.9F, 1.2F);
        if (powerupManager != null) {
            powerupManager.onRuleRevealed(conditionType);
        }
    }

    public boolean handleConditionTrigger(Player player, ConditionType actionType) {
        if (gameManager == null || !gameManager.isRunning()) {
            return false;
        }

        UUID playerId = player.getUniqueId();
        if (!gameManager.isActivePlayer(playerId)) {
            return false;
        }

        PlayerGameData data = playerDataManager.get(playerId);
        if (data == null || data.isEliminated()) {
            return false;
        }

        if (taskManager != null) {
            taskManager.handleConditionAction(player, actionType);
        }

        if (!data.isConditionRevealed()) {
            return false;
        }

        ConditionType assigned = data.getAssignedCondition();
        if (assigned == null || assigned != actionType) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (data.getConditionActiveAtMillis() > now) {
            long remain = Math.max(1L, (data.getConditionActiveAtMillis() - now + 999L) / 1000L);
            uiManager.warn(player, "[规则] 你的公开规则将在 " + remain + " 秒后生效。");
            return false;
        }

        if (powerupManager != null && powerupManager.consumeShieldIfActive(player)) {
            return false;
        }

        boolean eliminated = gameManager.eliminatePlayer(player, "触发已公开淘汰条件：" + actionType.getDisplayName());
        if (eliminated) {
            triggeredConditions.add(actionType);
        }
        return eliminated;
    }

    public List<RevealedCondition> getRevealedConditionsSnapshot() {
        return Collections.unmodifiableList(new ArrayList<>(revealedConditions));
    }

    public Set<ConditionType> getTriggeredConditionsSnapshot() {
        return Set.copyOf(triggeredConditions);
    }

    public long getSecondsUntilNextReveal() {
        if (revealTask == null || nextRevealEpochSecond <= 0L || gameManager == null || !gameManager.isRunning()) {
            return 0L;
        }

        long remain = nextRevealEpochSecond - nowSecond();
        return Math.max(0L, remain);
    }

    private long nowSecond() {
        return System.currentTimeMillis() / 1000L;
    }
}
