package com.hiddenelimination.listener;

import com.hiddenelimination.manager.ConditionManager;
import com.hiddenelimination.manager.GameManager;
import com.hiddenelimination.manager.SpawnManager;
import com.hiddenelimination.manager.TaskManager;
import com.hiddenelimination.model.ConditionType;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerStatisticIncrementEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Gameplay listener for hidden conditions and global tasks.
 */
public final class GameListener implements Listener {

    private final GameManager gameManager;
    private final ConditionManager conditionManager;
    private final SpawnManager spawnManager;
    private final TaskManager taskManager;

    public GameListener(GameManager gameManager, ConditionManager conditionManager, SpawnManager spawnManager, TaskManager taskManager) {
        this.gameManager = gameManager;
        this.conditionManager = conditionManager;
        this.spawnManager = spawnManager;
        this.taskManager = taskManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJump(PlayerStatisticIncrementEvent event) {
        if (!gameManager.isRunning()) {
            return;
        }

        if (event.getStatistic() != Statistic.JUMP) {
            return;
        }

        conditionManager.handleConditionTrigger(event.getPlayer(), ConditionType.JUMP);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerAttack(EntityDamageByEntityEvent event) {
        if (!gameManager.isRunning()) {
            return;
        }

        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null) {
            return;
        }

        conditionManager.handleConditionTrigger(attacker, ConditionType.ATTACK_PLAYER);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAnyDamage(EntityDamageEvent event) {
        if (!gameManager.isRunning()) {
            return;
        }

        if (event.getEntity() instanceof Player player) {
            conditionManager.handleConditionTrigger(player, ConditionType.TAKE_DAMAGE);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent event) {
        if (!gameManager.isRunning()) {
            return;
        }

        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            conditionManager.handleConditionTrigger(player, ConditionType.TAKE_FALL_DAMAGE);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!gameManager.isRunning()) {
            return;
        }

        if (event.getEntity() instanceof Player player) {
            conditionManager.handleConditionTrigger(player, ConditionType.PICKUP_ITEM);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnterWater(PlayerMoveEvent event) {
        if (!gameManager.isRunning()) {
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }

        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        Material fromType = from.getBlock().getType();
        Material toType = to.getBlock().getType();

        if (!isWater(fromType) && isWater(toType)) {
            conditionManager.handleConditionTrigger(event.getPlayer(), ConditionType.ENTER_WATER);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMoveTaskChecks(PlayerMoveEvent event) {
        if (!gameManager.isRunning()) {
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }

        Player player = event.getPlayer();

        if (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ()) {
            Material stoodOn = to.getBlock().getRelative(BlockFace.DOWN).getType();
            taskManager.handleStandOnBlock(player, stoodOn);
            taskManager.handlePlayerContact(player);
        }

        taskManager.handleSwimState(player);
        taskManager.handleClimbState(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!gameManager.isRunning()) {
            return;
        }

        if (event.getEntity() instanceof Player) {
            return;
        }

        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }

        taskManager.handleEntityKill(killer, event.getEntityType());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAdvancementDone(PlayerAdvancementDoneEvent event) {
        if (!gameManager.isRunning()) {
            return;
        }

        taskManager.handleAdvancement(event.getPlayer(), event.getAdvancement().getKey().toString());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!gameManager.isRunning()) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack result = event.getRecipe() == null ? null : event.getRecipe().getResult();
        if (result == null) {
            return;
        }

        taskManager.handleCraft(player, result.getType());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTaskInteract(PlayerInteractEvent event) {
        if (!gameManager.isRunning()) {
            return;
        }

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }

        taskManager.handleInteractBlock(event.getPlayer(), clicked.getType());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (!gameManager.isRunning()) {
            return;
        }

        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        taskManager.handlePlayerDeath(victim);
        if (killer != null) {
            taskManager.handlePlayerKill(killer);
        }

        gameManager.handlePlayerKilled(victim, killer);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        if (gameManager.isRunning()) {
            gameManager.ensureSpectatorState(player);
            return;
        }

        Location lobby = spawnManager.getLobbyLocation();
        if (lobby != null) {
            event.setRespawnLocation(lobby);
        }

        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(GameListener.class);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }

            player.setGameMode(GameMode.ADVENTURE);
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            player.getInventory().setItemInOffHand(null);
            player.getInventory().setItem(PrepareItemListener.READY_ITEM_SLOT, PrepareItemListener.createReadyItem(false));
            if (player.hasPermission("hiddenelimination.admin") || player.isOp()) {
                player.getInventory().setItem(PrepareItemListener.START_ITEM_SLOT, PrepareItemListener.createStartItem());
            }
        }, 2L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!gameManager.isRunning()) {
            return;
        }

        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        conditionManager.handleConditionTrigger(player, ConditionType.OPEN_INVENTORY);

        Inventory inventory = event.getInventory();
        if (isChestInventory(inventory)) {
            conditionManager.handleConditionTrigger(player, ConditionType.OPEN_CHEST);
        }

        InventoryType type = inventory.getType();
        if (type == InventoryType.WORKBENCH) {
            conditionManager.handleConditionTrigger(player, ConditionType.USE_CRAFTING_TABLE);
        }

        if (type == InventoryType.FURNACE || type == InventoryType.BLAST_FURNACE || type == InventoryType.SMOKER) {
            conditionManager.handleConditionTrigger(player, ConditionType.USE_FURNACE);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (!gameManager.isRunning()) {
            return;
        }

        if (event.getItem().getType().isEdible()) {
            conditionManager.handleConditionTrigger(event.getPlayer(), ConditionType.EAT_FOOD);
        }

        taskManager.handleConsumeItem(event.getPlayer(), event.getItem().getType());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (gameManager.isRunning()) {
            conditionManager.handleConditionTrigger(event.getPlayer(), ConditionType.BREAK_BLOCK);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (gameManager.isRunning()) {
            conditionManager.handleConditionTrigger(event.getPlayer(), ConditionType.PLACE_BLOCK);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (gameManager.isRunning()) {
            conditionManager.handleConditionTrigger(event.getPlayer(), ConditionType.DROP_ITEM);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (gameManager.isRunning() && event.isSneaking()) {
            conditionManager.handleConditionTrigger(event.getPlayer(), ConditionType.SNEAK);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSprint(PlayerToggleSprintEvent event) {
        if (gameManager.isRunning() && event.isSprinting()) {
            conditionManager.handleConditionTrigger(event.getPlayer(), ConditionType.SPRINT);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onArmorClickEquip(InventoryClickEvent event) {
        if (!gameManager.isRunning()) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (event.isShiftClick()) {
            ItemStack current = event.getCurrentItem();
            if (isArmorItem(current) && canEquipByShift(player, current.getType())) {
                conditionManager.handleConditionTrigger(player, ConditionType.EQUIP_ARMOR);
            }
            return;
        }

        if (event.getSlotType() == InventoryType.SlotType.ARMOR) {
            ItemStack cursor = event.getCursor();
            if (isArmorItem(cursor)) {
                conditionManager.handleConditionTrigger(player, ConditionType.EQUIP_ARMOR);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onArmorRightClickEquip(PlayerInteractEvent event) {
        if (!gameManager.isRunning()) {
            return;
        }

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (!isArmorItem(item)) {
            return;
        }

        Material type = item.getType();
        if (hasEmptyArmorSlotFor(player, type)) {
            conditionManager.handleConditionTrigger(player, ConditionType.EQUIP_ARMOR);
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

    private boolean isChestInventory(Inventory inventory) {
        InventoryType type = inventory.getType();

        if (type == InventoryType.CHEST) {
            return true;
        }

        InventoryHolder holder = inventory.getHolder();
        if (holder instanceof Chest) {
            return true;
        }

        if (holder instanceof BlockState blockState) {
            Block block = blockState.getBlock();
            Material material = block.getType();
            return material == Material.CHEST || material == Material.TRAPPED_CHEST;
        }

        return false;
    }

    private boolean isWater(Material material) {
        return material == Material.WATER || material == Material.BUBBLE_COLUMN;
    }

    private boolean isArmorItem(ItemStack item) {
        if (item == null) {
            return false;
        }

        Material type = item.getType();
        String name = type.name();
        return name.endsWith("_HELMET")
                || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS")
                || name.endsWith("_BOOTS")
                || type == Material.ELYTRA;
    }

    private boolean canEquipByShift(Player player, Material type) {
        return hasEmptyArmorSlotFor(player, type);
    }

    private boolean hasEmptyArmorSlotFor(Player player, Material type) {
        String name = type.name();
        if (name.endsWith("_HELMET")) {
            return player.getInventory().getHelmet() == null;
        }
        if (name.endsWith("_CHESTPLATE") || type == Material.ELYTRA) {
            return player.getInventory().getChestplate() == null;
        }
        if (name.endsWith("_LEGGINGS")) {
            return player.getInventory().getLeggings() == null;
        }
        if (name.endsWith("_BOOTS")) {
            return player.getInventory().getBoots() == null;
        }
        return false;
    }
}
