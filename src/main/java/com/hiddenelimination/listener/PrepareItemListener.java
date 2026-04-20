package com.hiddenelimination.listener;

import com.hiddenelimination.manager.GameManager;
import com.hiddenelimination.manager.PlayerDataManager;
import com.hiddenelimination.manager.UIManager;
import com.hiddenelimination.model.PlayerGameData;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 大厅准备物品监听：
 * - 右键绿染料：切换准备
 * - 管理员右键下界之星：开始游戏
 */
public final class PrepareItemListener implements Listener {

    public static final int READY_ITEM_SLOT = 4;
    public static final int START_ITEM_SLOT = 8;

    // 防止一次右键触发两次（例如主副手或高频点击）
    private static final long CLICK_DEBOUNCE_MS = 250L;

    private final PlayerDataManager playerDataManager;
    private final GameManager gameManager;
    private final UIManager uiManager;

    private final Map<UUID, Long> lastClickTime = new ConcurrentHashMap<>();

    public PrepareItemListener(PlayerDataManager playerDataManager, GameManager gameManager, UIManager uiManager) {
        this.playerDataManager = playerDataManager;
        this.gameManager = gameManager;
        this.uiManager = uiManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        // 只处理主手，避免副手重复触发
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null) {
            return;
        }

        Player player = event.getPlayer();

        // 短时间内重复触发直接拦截
        if (isDebounced(player.getUniqueId())) {
            denyDefaultUse(event);
            return;
        }

        if (item.getType() == Material.GREEN_DYE) {
            denyDefaultUse(event);
            toggleReady(player);
            return;
        }

        if (item.getType() == Material.NETHER_STAR) {
            denyDefaultUse(event);
            tryStartGame(player);
        }
    }

    private void denyDefaultUse(PlayerInteractEvent event) {
        event.setCancelled(true);
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
    }

    private boolean isDebounced(UUID playerId) {
        long now = System.currentTimeMillis();
        long last = lastClickTime.getOrDefault(playerId, 0L);
        if (now - last < CLICK_DEBOUNCE_MS) {
            return true;
        }
        lastClickTime.put(playerId, now);
        return false;
    }

    private void toggleReady(Player player) {
        if (gameManager.isRunning()) {
            uiManager.error(player, "游戏进行中，无法切换准备");
            return;
        }

        PlayerGameData data = playerDataManager.getOrCreate(player.getUniqueId());
        if (!data.isJoined()) {
            data.setJoined(true);
        }

        boolean newReady = !data.isReady();
        playerDataManager.setReady(player, newReady);

        if (newReady) {
            uiManager.success(player, "你已准备");
            player.getInventory().setItem(READY_ITEM_SLOT, createReadyItem(true));
        } else {
            uiManager.warn(player, "你已取消准备");
            player.getInventory().setItem(READY_ITEM_SLOT, createReadyItem(false));
        }
    }

    private void tryStartGame(Player player) {
        if (!player.hasPermission("hiddenelimination.admin") && !player.isOp()) {
            uiManager.error(player, "你没有权限开始游戏");
            return;
        }

        boolean started = gameManager.startGame(player);
        if (!started) {
            uiManager.warn(player, "开始游戏失败，请检查状态或准备人数");
        }
    }

    public static ItemStack createReadyItem(boolean ready) {
        ItemStack item = new ItemStack(Material.GREEN_DYE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ready ? ChatColor.GREEN + "已准备（右键取消）" : ChatColor.YELLOW + "点击准备");
            meta.setLore(Arrays.asList(ChatColor.GRAY + "右键切换准备状态"));
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack createStartItem() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "开始游戏");
            meta.setLore(Arrays.asList(ChatColor.GRAY + "管理员右键开始"));
            item.setItemMeta(meta);
        }
        return item;
    }
}