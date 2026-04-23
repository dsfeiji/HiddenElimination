package com.hiddenelimination.listener;

import com.hiddenelimination.manager.GameManager;
import com.hiddenelimination.manager.SpawnManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;

/**
 * 大厅保护：
 * - 大厅禁止 PVP
 * - 大厅锁定背包，禁止移动/复制物品
 * - 大厅不掉血、不掉饱食度
 * - 大厅掉入虚空时传送回大厅出生点
 */
public final class LobbyProtectionListener implements Listener {

    private final GameManager gameManager;
    private final SpawnManager spawnManager;

    public LobbyProtectionListener(GameManager gameManager, SpawnManager spawnManager) {
        this.gameManager = gameManager;
        this.spawnManager = spawnManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLobbyPvp(EntityDamageByEntityEvent event) {
        if (gameManager.isRunning()) {
            return;
        }

        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLobbyDamage(EntityDamageEvent event) {
        if (gameManager.isRunning()) {
            return;
        }

        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            Location lobby = spawnManager.getLobbyLocation();
            if (lobby != null) {
                player.teleport(lobby);
            }
            player.setFallDistance(0.0F);
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLobbyFood(FoodLevelChangeEvent event) {
        if (gameManager.isRunning()) {
            return;
        }

        if (event.getEntity() instanceof Player) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (gameManager.isRunning()) {
            return;
        }

        if (event.getWhoClicked() instanceof Player) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (gameManager.isRunning()) {
            return;
        }

        if (event.getWhoClicked() instanceof Player) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreative(InventoryCreativeEvent event) {
        if (gameManager.isRunning()) {
            return;
        }

        if (event.getWhoClicked() instanceof Player) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!gameManager.isRunning()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (!gameManager.isRunning()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWeatherChange(WeatherChangeEvent event) {
        if (!isLobbyWorld(event.getWorld())) {
            return;
        }

        if (event.toWeatherState()) {
            event.setCancelled(true);
            spawnManager.enforceLobbyEnvironment();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onThunderChange(ThunderChangeEvent event) {
        if (!isLobbyWorld(event.getWorld())) {
            return;
        }

        if (event.toThunderState()) {
            event.setCancelled(true);
            spawnManager.enforceLobbyEnvironment();
        }
    }

    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }

        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }

        return null;
    }

    private boolean isLobbyWorld(World world) {
        World lobbyWorld = spawnManager.getLobbyWorld();
        return lobbyWorld != null && lobbyWorld.equals(world);
    }
}
