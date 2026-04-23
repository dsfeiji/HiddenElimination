package com.hiddenelimination.model;

import java.util.UUID;

/**
 * 玩家在一局游戏中的状态数据。
 */
public final class PlayerGameData {

    private final UUID playerId;

    /** 是否加入房间 */
    private boolean joined;

    /** 是否准备 */
    private boolean ready;

    /** 是否被淘汰 */
    private boolean eliminated;

    /** 是否已切换旁观 */
    private boolean spectator;

    /** 分配到的隐藏淘汰条件 */
    private ConditionType assignedCondition;

    /** 该玩家的条件是否已被系统公开 */
    private boolean conditionRevealed;
    /** 条件公开后何时开始生效（毫秒时间戳） */
    private long conditionActiveAtMillis;

    /** Global task score in current round */
    private int taskPoints;
    /** Historical total earned task points in current round */
    private int totalEarnedTaskPoints;

    /** Completed global task count in current round */
    private int completedTaskCount;

    /** Remaining task lives in current round */
    private int taskLivesRemaining;
    /** 回合开始时间（毫秒） */
    private long roundStartMillis;
    /** 被淘汰时间（毫秒，未淘汰为0） */
    private long eliminatedAtMillis;

    public PlayerGameData(UUID playerId) {
        this.playerId = playerId;
        this.joined = false;
        this.ready = false;
        this.eliminated = false;
        this.spectator = false;
        this.assignedCondition = null;
        this.conditionRevealed = false;
        this.conditionActiveAtMillis = 0L;
        this.taskPoints = 0;
        this.totalEarnedTaskPoints = 0;
        this.completedTaskCount = 0;
        this.taskLivesRemaining = 0;
        this.roundStartMillis = 0L;
        this.eliminatedAtMillis = 0L;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public boolean isJoined() {
        return joined;
    }

    public void setJoined(boolean joined) {
        this.joined = joined;
    }

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }

    public boolean isEliminated() {
        return eliminated;
    }

    public void setEliminated(boolean eliminated) {
        this.eliminated = eliminated;
    }

    public boolean isSpectator() {
        return spectator;
    }

    public void setSpectator(boolean spectator) {
        this.spectator = spectator;
    }

    public ConditionType getAssignedCondition() {
        return assignedCondition;
    }

    public void setAssignedCondition(ConditionType assignedCondition) {
        this.assignedCondition = assignedCondition;
    }

    public boolean isConditionRevealed() {
        return conditionRevealed;
    }

    public void setConditionRevealed(boolean conditionRevealed) {
        this.conditionRevealed = conditionRevealed;
    }

    public long getConditionActiveAtMillis() {
        return conditionActiveAtMillis;
    }

    public void setConditionActiveAtMillis(long conditionActiveAtMillis) {
        this.conditionActiveAtMillis = Math.max(0L, conditionActiveAtMillis);
    }

    public int getTaskPoints() {
        return taskPoints;
    }

    public void setTaskPoints(int taskPoints) {
        this.taskPoints = Math.max(0, taskPoints);
    }

    public void addTaskPoints(int delta) {
        if (delta <= 0) {
            return;
        }
        this.taskPoints += delta;
        this.totalEarnedTaskPoints += delta;
    }

    public void deductTaskPoints(int delta) {
        if (delta <= 0) {
            return;
        }
        this.taskPoints = Math.max(0, this.taskPoints - delta);
    }

    public int getCompletedTaskCount() {
        return completedTaskCount;
    }

    public int getTotalEarnedTaskPoints() {
        return totalEarnedTaskPoints;
    }

    public void setTotalEarnedTaskPoints(int totalEarnedTaskPoints) {
        this.totalEarnedTaskPoints = Math.max(0, totalEarnedTaskPoints);
    }

    public void incrementCompletedTaskCount() {
        this.completedTaskCount++;
    }

    public int getTaskLivesRemaining() {
        return taskLivesRemaining;
    }

    public void setTaskLivesRemaining(int taskLivesRemaining) {
        this.taskLivesRemaining = Math.max(0, taskLivesRemaining);
    }

    public int consumeTaskLife() {
        if (taskLivesRemaining > 0) {
            taskLivesRemaining--;
        }
        return taskLivesRemaining;
    }

    public long getRoundStartMillis() {
        return roundStartMillis;
    }

    public void setRoundStartMillis(long roundStartMillis) {
        this.roundStartMillis = Math.max(0L, roundStartMillis);
    }

    public long getEliminatedAtMillis() {
        return eliminatedAtMillis;
    }

    public void setEliminatedAtMillis(long eliminatedAtMillis) {
        this.eliminatedAtMillis = Math.max(0L, eliminatedAtMillis);
    }

    /**
     * 每局结束后重置局内状态，保留是否加入房间。
     */
    public void resetRoundState() {
        this.ready = false;
        this.eliminated = false;
        this.spectator = false;
        this.assignedCondition = null;
        this.conditionRevealed = false;
        this.conditionActiveAtMillis = 0L;
        this.taskPoints = 0;
        this.totalEarnedTaskPoints = 0;
        this.completedTaskCount = 0;
        this.taskLivesRemaining = 0;
        this.roundStartMillis = 0L;
        this.eliminatedAtMillis = 0L;
    }
}
