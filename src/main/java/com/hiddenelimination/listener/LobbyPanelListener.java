package com.hiddenelimination.listener;

import com.hiddenelimination.manager.LobbyPanelManager;
import com.hiddenelimination.manager.UIManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 大厅面板交互监听。
 */
public final class LobbyPanelListener implements Listener {

    private static final long INTERACT_COOLDOWN_MS = 150L;

    private final LobbyPanelManager lobbyPanelManager;
    private final UIManager uiManager;
    private final Map<UUID, Long> lastInteractAt = new ConcurrentHashMap<>();

    public LobbyPanelListener(LobbyPanelManager lobbyPanelManager, UIManager uiManager) {
        this.lobbyPanelManager = lobbyPanelManager;
        this.uiManager = uiManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        handlePanelInteract(event.getPlayer(), event.getRightClicked(), event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        handlePanelInteract(event.getPlayer(), event.getRightClicked(), event);
    }

    private void handlePanelInteract(Player player, Entity entity, Cancellable event) {
        if (!lobbyPanelManager.isPanelInteraction(entity)) {
            return;
        }
        event.setCancelled(true);

        if (!player.hasPermission("hiddenelimination.admin") && !player.isOp()) {
            uiManager.warn(player, "你没有权限调整大厅参数。");
            return;
        }

        long now = System.currentTimeMillis();
        Long last = lastInteractAt.get(player.getUniqueId());
        if (last != null && now - last < INTERACT_COOLDOWN_MS) {
            return;
        }
        lastInteractAt.put(player.getUniqueId(), now);

        if (!lobbyPanelManager.canAdjustNow()) {
            uiManager.warn(player, "游戏进行中，暂不允许通过大厅面板修改设置。");
            return;
        }

        String result = lobbyPanelManager.adjustByEntity(entity);
        if (result == null) {
            return;
        }
        uiManager.info(player, "[大厅面板] " + result);
    }
}
