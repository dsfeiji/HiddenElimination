package com.hiddenelimination.listener;

import com.hiddenelimination.manager.GameManager;
import com.hiddenelimination.manager.PlayerDataManager;
import com.hiddenelimination.manager.SpawnManager;
import com.hiddenelimination.manager.UIManager;
import com.hiddenelimination.model.PlayerGameData;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 玩家进出监听：
 * - 进服统一强制回大厅
 * - 运行中离线视为淘汰
 */
public final class PlayerJoinQuitListener implements Listener {

    private final PlayerDataManager playerDataManager;
    private final SpawnManager spawnManager;
    private final UIManager uiManager;
    private final GameManager gameManager;

    public PlayerJoinQuitListener(
            PlayerDataManager playerDataManager,
            SpawnManager spawnManager,
            UIManager uiManager,
            GameManager gameManager
    ) {
        this.playerDataManager = playerDataManager;
        this.spawnManager = spawnManager;
        this.uiManager = uiManager;
        this.gameManager = gameManager;
    }

    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerGameData data = playerDataManager.getOrCreate(player.getUniqueId());

        // 每次进服都强制回大厅
        spawnManager.teleportToLobby(player);

        // 统一进入大厅状态
        data.setJoined(true);
        data.setReady(false);

        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);
        player.getInventory().setItem(PrepareItemListener.READY_ITEM_SLOT, PrepareItemListener.createReadyItem(false));

        if (player.hasPermission("hiddenelimination.admin") || player.isOp()) {
            player.getInventory().setItem(PrepareItemListener.START_ITEM_SLOT, PrepareItemListener.createStartItem());
        }

        // 如果当前有对局在进行，玩家只能在大厅等待下一局
        if (gameManager.isRunning()) {
            player.setGameMode(GameMode.ADVENTURE);
            uiManager.info(player, "当前对局进行中，你已被传送到大厅等待下一局");
            return;
        }

        // 非对局中，正常初始化
        data.setEliminated(false);
        data.setSpectator(false);
        data.setConditionRevealed(false);
        data.setAssignedCondition(null);

        player.setGameMode(GameMode.ADVENTURE);
        uiManager.info(player, "欢迎来到 HiddenElimination，已传送到大厅");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (gameManager.isRunning()) {
            gameManager.handleQuit(player);
            return;
        }

        playerDataManager.leave(player);
    }
}