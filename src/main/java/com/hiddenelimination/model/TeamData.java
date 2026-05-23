package com.hiddenelimination.model;

import org.bukkit.ChatColor;

import java.util.Set;
import java.util.UUID;

public final class TeamData {

    private final int teamId;
    private final ChatColor teamColor;
    private final String displayName;
    private final Set<UUID> memberIds;

    private Set<UUID> aliveMemberIds;
    private int teamPoints;
    private int completedTaskCount;
    private boolean eliminated;
    private long eliminatedAtMillis;

    public TeamData(int teamId, ChatColor teamColor, String displayName, Set<UUID> memberIds) {
        this.teamId = teamId;
        this.teamColor = teamColor;
        this.displayName = displayName;
        this.memberIds = memberIds;
        this.aliveMemberIds = memberIds;
        this.teamPoints = 0;
        this.completedTaskCount = 0;
        this.eliminated = false;
        this.eliminatedAtMillis = 0L;
    }

    public int getTeamId() {
        return teamId;
    }

    public ChatColor getTeamColor() {
        return teamColor;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Set<UUID> getMemberIds() {
        return memberIds;
    }

    public Set<UUID> getAliveMemberIds() {
        return aliveMemberIds;
    }

    public void setAliveMemberIds(Set<UUID> aliveMemberIds) {
        this.aliveMemberIds = aliveMemberIds;
    }

    public int getTeamPoints() {
        return teamPoints;
    }

    public void setTeamPoints(int teamPoints) {
        this.teamPoints = Math.max(0, teamPoints);
    }

    public void addTeamPoints(int delta) {
        if (delta > 0) {
            this.teamPoints += delta;
        }
    }

    public void deductTeamPoints(int delta) {
        if (delta > 0) {
            this.teamPoints = Math.max(0, this.teamPoints - delta);
        }
    }

    public int getCompletedTaskCount() {
        return completedTaskCount;
    }

    public void incrementCompletedTaskCount() {
        this.completedTaskCount++;
    }

    public boolean isEliminated() {
        return eliminated;
    }

    public void setEliminated(boolean eliminated) {
        this.eliminated = eliminated;
    }

    public long getEliminatedAtMillis() {
        return eliminatedAtMillis;
    }

    public void setEliminatedAtMillis(long eliminatedAtMillis) {
        this.eliminatedAtMillis = Math.max(0L, eliminatedAtMillis);
    }

    public int getAliveCount() {
        return aliveMemberIds.size();
    }

    public int getTotalCount() {
        return memberIds.size();
    }

    public boolean isMember(UUID playerId) {
        return memberIds.contains(playerId);
    }

    public boolean isAlive(UUID playerId) {
        return aliveMemberIds.contains(playerId);
    }

    public void memberEliminated(UUID playerId) {
        aliveMemberIds.remove(playerId);
    }

    public String getColoredDisplayName() {
        return teamColor + displayName + ChatColor.RESET;
    }
}
