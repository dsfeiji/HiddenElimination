package com.hiddenelimination.condition;

import com.hiddenelimination.HiddenEliminationPlugin;
import com.hiddenelimination.model.ConditionType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Map;
import java.util.UUID;

/**
 * 状态类隐藏规则的判定逻辑（由 ConditionManager 轮询调用）。
 */
public final class ConditionStateEvaluator {

    private final HiddenEliminationPlugin plugin;
    private final Map<UUID, Long> lastMoveMillisByPlayer;

    public ConditionStateEvaluator(HiddenEliminationPlugin plugin, Map<UUID, Long> lastMoveMillisByPlayer) {
        this.plugin = plugin;
        this.lastMoveMillisByPlayer = lastMoveMillisByPlayer;
    }

    public void recordMovement(Player player, Location from, Location to) {
        if (to == null) {
            return;
        }
        double threshold = plugin.getConfig().getDouble("conditions.stop-moving-horizontal-threshold", 0.01D);
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        if (dx * dx + dz * dz >= threshold * threshold) {
            lastMoveMillisByPlayer.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }

    public void initPlayerMovement(Player player) {
        lastMoveMillisByPlayer.put(player.getUniqueId(), System.currentTimeMillis());
    }

    public void clearPlayer(UUID playerId) {
        lastMoveMillisByPlayer.remove(playerId);
    }

    public void clearAll() {
        lastMoveMillisByPlayer.clear();
    }

    public boolean matches(Player player, ConditionType type) {
        return switch (type) {
            case STAND_ON_GRASS_BLOCK -> isStandingOn(player, Material.GRASS_BLOCK);
            case STAND_ON_STONE -> isStandingOnStone(player);
            case NOT_ON_GRASS_BLOCK -> !isStandingOn(player, Material.GRASS_BLOCK);
            case BLOCK_OVERHEAD -> hasBlockOverhead(player);
            case HOLD_ANY_ITEM -> holdsAnyItem(player);
            case HAS_WEAPON -> hasWeaponInInventory(player);
            case HAS_FOOD -> hasFoodInInventory(player);
            case HAS_ORE -> hasOreInInventory(player);
            case HAS_TOOL -> hasToolInInventory(player);
            case STOP_MOVING -> isStopMoving(player);
            case NOT_SNEAKING -> !player.isSneaking();
            default -> false;
        };
    }

    private boolean isStopMoving(Player player) {
        long idleSeconds = Math.max(1L, plugin.getConfig().getLong("conditions.stop-moving-idle-seconds", 3L));
        long lastMove = lastMoveMillisByPlayer.getOrDefault(player.getUniqueId(), System.currentTimeMillis());
        return System.currentTimeMillis() - lastMove >= idleSeconds * 1000L;
    }

    private Material getBlockBelow(Player player) {
        return player.getLocation().getBlock().getRelative(BlockFace.DOWN).getType();
    }

    private boolean isStandingOn(Player player, Material material) {
        return getBlockBelow(player) == material;
    }

    private boolean isStandingOnStone(Player player) {
        Material below = getBlockBelow(player);
        if (below.isAir()) {
            return false;
        }
        if (Tag.BASE_STONE_OVERWORLD.isTagged(below)) {
            return true;
        }
        String name = below.name();
        return name.contains("STONE")
                || name.contains("COBBLESTONE")
                || name.contains("DEEPSLATE")
                || name.contains("TUFF")
                || name.contains("BLACKSTONE");
    }

    private boolean hasBlockOverhead(Player player) {
        Block headBlock = player.getEyeLocation().getBlock();
        Material type = headBlock.getType();
        return !type.isAir() && type.isSolid();
    }

    private boolean holdsAnyItem(Player player) {
        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();
        return (main != null && main.getType() != Material.AIR)
                || (off != null && off.getType() != Material.AIR);
    }

    private boolean hasWeaponInInventory(Player player) {
        return containsMatching(player, this::isWeapon);
    }

    private boolean hasFoodInInventory(Player player) {
        return containsMatching(player, material -> material.isEdible());
    }

    private boolean hasOreInInventory(Player player) {
        return containsMatching(player, this::isOre);
    }

    private boolean hasToolInInventory(Player player) {
        return containsMatching(player, this::isTool);
    }

    private boolean containsMatching(Player player, java.util.function.Predicate<Material> predicate) {
        PlayerInventory inventory = player.getInventory();
        for (ItemStack stack : inventory.getStorageContents()) {
            if (matches(stack, predicate)) {
                return true;
            }
        }
        if (matches(inventory.getItemInOffHand(), predicate)) {
            return true;
        }
        for (ItemStack armor : inventory.getArmorContents()) {
            if (matches(armor, predicate)) {
                return true;
            }
        }
        return false;
    }

    private boolean matches(ItemStack stack, java.util.function.Predicate<Material> predicate) {
        return stack != null && stack.getType() != Material.AIR && predicate.test(stack.getType());
    }

    private boolean isWeapon(Material material) {
        String name = material.name();
        return name.endsWith("_SWORD") || name.endsWith("_AXE");
    }

    private boolean isOre(Material material) {
        return material == Material.IRON_INGOT
                || material == Material.GOLD_INGOT
                || material == Material.DIAMOND
                || material == Material.COAL
                || material == Material.COPPER_INGOT
                || material == Material.RAW_IRON
                || material == Material.RAW_GOLD
                || material == Material.RAW_COPPER;
    }

    private boolean isTool(Material material) {
        String name = material.name();
        return name.endsWith("_PICKAXE")
                || name.endsWith("_AXE")
                || name.endsWith("_HOE")
                || name.endsWith("_SHOVEL")
                || material == Material.SHEARS;
    }
}
