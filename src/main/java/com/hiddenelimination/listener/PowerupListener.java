package com.hiddenelimination.listener;

import com.hiddenelimination.manager.GameManager;
import com.hiddenelimination.manager.PowerupManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class PowerupListener implements Listener {

    private final GameManager gameManager;
    private final PowerupManager powerupManager;

    public PowerupListener(GameManager gameManager, PowerupManager powerupManager) {
        this.gameManager = gameManager;
        this.powerupManager = powerupManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (!gameManager.isRunning()) {
            return;
        }

        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        if (!gameManager.isActivePlayer(player.getUniqueId())) {
            return;
        }

        ItemStack used = event.getItem();
        if (!powerupManager.handleCompassUse(player, used)) {
            return;
        }

        event.setCancelled(true);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        powerupManager.handleMenuClick(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        powerupManager.handleMenuDrag(event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onDropCompass(PlayerDropItemEvent event) {
        if (!gameManager.isRunning()) {
            return;
        }

        Player player = event.getPlayer();
        if (!gameManager.isActivePlayer(player.getUniqueId())) {
            return;
        }

        ItemStack dropped = event.getItemDrop().getItemStack();
        if (!powerupManager.isMenuCompass(dropped)) {
            return;
        }

        event.setCancelled(true);
    }
}