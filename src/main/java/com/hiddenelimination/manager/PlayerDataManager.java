package com.hiddenelimination.manager;

import com.hiddenelimination.model.PlayerGameData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家局内数据管理器。
 */
public final class PlayerDataManager {

    private final Map<UUID, PlayerGameData> playerDataMap = new ConcurrentHashMap<>();

    public PlayerGameData getOrCreate(UUID playerId) {
        return playerDataMap.computeIfAbsent(playerId, PlayerGameData::new);
    }

    public PlayerGameData get(UUID playerId) {
        return playerDataMap.get(playerId);
    }

    public void join(Player player) {
        PlayerGameData data = getOrCreate(player.getUniqueId());
        data.setJoined(true);
    }

    public void leave(Player player) {
        PlayerGameData data = getOrCreate(player.getUniqueId());
        data.setJoined(false);
        data.resetRoundState();
    }

    public void setReady(Player player, boolean ready) {
        PlayerGameData data = getOrCreate(player.getUniqueId());
        data.setReady(ready);
    }

    public long getJoinedCount() {
        return playerDataMap.values().stream().filter(PlayerGameData::isJoined).count();
    }

    public long getReadyCount() {
        return playerDataMap.values().stream()
                .filter(PlayerGameData::isJoined)
                .filter(PlayerGameData::isReady)
                .count();
    }

    public long getAliveCount() {
        return playerDataMap.values().stream()
                .filter(PlayerGameData::isJoined)
                .filter(data -> !data.isEliminated())
                .count();
    }

    public List<Player> getJoinedOnlinePlayers() {
        List<Player> result = new ArrayList<>();
        for (PlayerGameData data : playerDataMap.values()) {
            if (!data.isJoined()) {
                continue;
            }

            Player player = Bukkit.getPlayer(data.getPlayerId());
            if (player != null && player.isOnline()) {
                result.add(player);
            }
        }
        return result;
    }

    public List<Player> getReadyOnlinePlayers() {
        List<Player> result = new ArrayList<>();
        for (PlayerGameData data : playerDataMap.values()) {
            if (!data.isJoined() || !data.isReady()) {
                continue;
            }

            Player player = Bukkit.getPlayer(data.getPlayerId());
            if (player != null && player.isOnline()) {
                result.add(player);
            }
        }
        return result;
    }

    public void resetAllRoundState() {
        for (PlayerGameData data : playerDataMap.values()) {
            if (data.isJoined()) {
                data.resetRoundState();
            }
        }
    }
}