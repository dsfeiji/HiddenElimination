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

    /** Global task score in current round */
    private int taskPoints;

    /** Completed global task count in current round */
    private int completedTaskCount;

    /** Remaining task lives in current round */
    private int taskLivesRemaining;

    public PlayerGameData(UUID playerId) {
        this.playerId = playerId;
        this.joined = false;
        this.ready = false;
        this.eliminated = false;
        this.spectator = false;
        this.assignedCondition = null;
        this.conditionRevealed = false;
        this.taskPoints = 0;
        this.completedTaskCount = 0;
        this.taskLivesRemaining = 0;
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

    /**
     * 每局结束后重置局内状态，保留是否加入房间。
     */
    public void resetRoundState() {
        this.ready = false;
        this.eliminated = false;
        this.spectator = false;
        this.assignedCondition = null;
        this.conditionRevealed = false;
        this.taskPoints = 0;
        this.completedTaskCount = 0;
        this.taskLivesRemaining = 0;
    }
}
