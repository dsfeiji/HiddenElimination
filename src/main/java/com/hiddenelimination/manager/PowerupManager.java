package com.hiddenelimination.manager;

import com.hiddenelimination.HiddenEliminationPlugin;
import com.hiddenelimination.model.ConditionType;
import com.hiddenelimination.model.PlayerGameData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PowerupManager {

    private static final String MENU_TITLE = ChatColor.GOLD + "道具兑换";
    private static final String RULE_TOOL_MENU_TITLE = ChatColor.YELLOW + "规则道具";
    private static final String PROBE_MENU_TITLE = ChatColor.YELLOW + "规则试探";
    private static final String ARMOR_MENU_TITLE = ChatColor.GREEN + "积分兑换装备强化";
    private static final String POINT_ITEM_MENU_TITLE = ChatColor.LIGHT_PURPLE + "积分兑换道具";
    private static final String ORE_MENU_TITLE = ChatColor.AQUA + "矿物兑换积分";
    private static final int MENU_SIZE = 27;

    private static final int SLOT_RULE_TOOL = 10;
    private static final int SLOT_ARMOR_SET = 12;
    private static final int SLOT_POINT_ITEMS = 14;
    private static final int SLOT_ORE_EXCHANGE = 16;
    private static final int SLOT_RULE_SHIELD = 13;
    private static final int SLOT_FAKE_BROADCAST = 16;
    private static final int SLOT_INFO = 22;
    private static final int SLOT_BACK = 22;

    private static final int[] PROBE_SELECT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 23, 24, 25,
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26
    };
    private static final int[] ARMOR_SELECT_SLOTS = {10, 11, 12, 14, 15, 16};
    private static final int[] ORE_SELECT_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21};

    private final HiddenEliminationPlugin plugin;
    private final PlayerDataManager playerDataManager;
    private final UIManager uiManager;
    private final Random random = new Random();

    private final NamespacedKey compassKey;
    private final NamespacedKey probeConditionKey;
    private final NamespacedKey armorSetKey;
    private final NamespacedKey pointItemKey;
    private final NamespacedKey oreExchangeKey;

    private final Map<UUID, Integer> choiceTokens = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> probeCredits = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> shieldCredits = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> misleadCredits = new ConcurrentHashMap<>();

    private final Map<UUID, Long> shieldActiveUntilMillis = new ConcurrentHashMap<>();
    private final Map<UUID, Long> shieldCooldownUntilMillis = new ConcurrentHashMap<>();

    private GameManager gameManager;
    private ConditionManager conditionManager;
    private TaskManager taskManager;
    private int pendingFakeBroadcastCount;

    public PowerupManager(HiddenEliminationPlugin plugin, PlayerDataManager playerDataManager, UIManager uiManager) {
        this.plugin = plugin;
        this.playerDataManager = playerDataManager;
        this.uiManager = uiManager;
        this.compassKey = new NamespacedKey(plugin, "powerup_compass");
        this.probeConditionKey = new NamespacedKey(plugin, "probe_condition");
        this.armorSetKey = new NamespacedKey(plugin, "armor_set");
        this.pointItemKey = new NamespacedKey(plugin, "point_item");
        this.oreExchangeKey = new NamespacedKey(plugin, "ore_exchange");
    }

    public void bindGameManager(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    public void bindConditionManager(ConditionManager conditionManager) {
        this.conditionManager = conditionManager;
    }

    public void bindTaskManager(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    public void startRound() {
        choiceTokens.clear();
        probeCredits.clear();
        shieldCredits.clear();
        misleadCredits.clear();
        shieldActiveUntilMillis.clear();
        shieldCooldownUntilMillis.clear();
        pendingFakeBroadcastCount = 0;

        if (gameManager == null) {
            return;
        }

        for (UUID playerId : gameManager.getActivePlayersSnapshot()) {
            choiceTokens.put(playerId, 0);
            probeCredits.put(playerId, 0);
            shieldCredits.put(playerId, 0);
            misleadCredits.put(playerId, 0);

            Player online = plugin.getServer().getPlayer(playerId);
            if (online != null && online.isOnline()) {
                giveMenuCompass(online);
            }
        }
    }

    public void stopRound() {
        choiceTokens.clear();
        probeCredits.clear();
        shieldCredits.clear();
        misleadCredits.clear();
        shieldActiveUntilMillis.clear();
        shieldCooldownUntilMillis.clear();
        pendingFakeBroadcastCount = 0;
    }

    public void rewardFirstFinisher(Player player) {
        UUID playerId = player.getUniqueId();
        String mode = plugin.getConfig().getString("powerups.first-finisher-reward-mode", "choice");

        if ("random".equalsIgnoreCase(mode)) {
            int pick = random.nextInt(3);
            if (pick == 0) {
                probeCredits.merge(playerId, 1, Integer::sum);
                uiManager.success(player, "你是本轮任务第一个完成者，随机获得：规则试探 x1");
            } else if (pick == 1) {
                shieldCredits.merge(playerId, 1, Integer::sum);
                uiManager.success(player, "你是本轮任务第一个完成者，随机获得：规则屏蔽 x1");
            } else {
                misleadCredits.merge(playerId, 1, Integer::sum);
                uiManager.success(player, "你是本轮任务第一个完成者，随机获得：误导广播 x1");
            }
        } else {
            choiceTokens.merge(playerId, 1, Integer::sum);
            uiManager.success(player, "你是本轮任务第一个完成者，获得 1 次道具兑换权（右键指南针打开菜单）");
        }

        giveMenuCompass(player);
    }

    public boolean handleCompassUse(Player player, ItemStack usedItem) {
        if (!isMenuCompass(usedItem)) {
            return false;
        }

        openExchangeMenu(player);
        return true;
    }

    public boolean handleMenuClick(InventoryClickEvent event) {
        if (event.getView() == null) {
            return false;
        }

        String title = event.getView().getTitle();
        if (MENU_TITLE.equals(title)) {
            return handleMainMenuClick(event);
        }
        if (RULE_TOOL_MENU_TITLE.equals(title)) {
            return handleRuleToolMenuClick(event);
        }
        if (PROBE_MENU_TITLE.equals(title)) {
            return handleProbeMenuClick(event);
        }
        if (ARMOR_MENU_TITLE.equals(title)) {
            return handleArmorMenuClick(event);
        }
        if (POINT_ITEM_MENU_TITLE.equals(title)) {
            return handlePointItemMenuClick(event);
        }
        if (ORE_MENU_TITLE.equals(title)) {
            return handleOreMenuClick(event);
        }

        return false;
    }

    public boolean handleMenuDrag(InventoryDragEvent event) {
        if (event.getView() == null) {
            return false;
        }

        String title = event.getView().getTitle();
        if (!MENU_TITLE.equals(title)
                && !RULE_TOOL_MENU_TITLE.equals(title)
                && !PROBE_MENU_TITLE.equals(title)
                && !ARMOR_MENU_TITLE.equals(title)
                && !POINT_ITEM_MENU_TITLE.equals(title)
                && !ORE_MENU_TITLE.equals(title)) {
            return false;
        }

        event.setCancelled(true);
        return true;
    }

    public boolean isMenuCompass(ItemStack item) {
        if (item == null || item.getType() != Material.COMPASS) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        Byte marker = meta.getPersistentDataContainer().get(compassKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    public boolean consumeShieldIfActive(Player player) {
        long now = System.currentTimeMillis();
        long activeUntil = shieldActiveUntilMillis.getOrDefault(player.getUniqueId(), 0L);
        return activeUntil > now;
    }

    public void onRuleRevealed(ConditionType realCondition) {
        if (pendingFakeBroadcastCount <= 0) {
            return;
        }

        pendingFakeBroadcastCount--;
        ConditionType fake = randomConditionExcluding(realCondition);
        if (fake == null) {
            return;
        }

        if (conditionManager != null) {
            conditionManager.revealFakeCondition(fake);
        } else {
            uiManager.broadcast("[规则] 规则公开：" + fake.getDisplayName());
        }
    }

    private boolean handleMainMenuClick(InventoryClickEvent event) {
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return true;
        }

        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return true;
        }

        int slot = event.getRawSlot();
        switch (slot) {
            case SLOT_RULE_TOOL -> openRuleToolMenu(player);
            case SLOT_ARMOR_SET -> openArmorSetMenu(player);
            case SLOT_POINT_ITEMS -> openPointItemMenu(player);
            case SLOT_ORE_EXCHANGE -> openOreExchangeMenu(player);
            default -> {
                return true;
            }
        }
        return true;
    }

    private boolean handleRuleToolMenuClick(InventoryClickEvent event) {
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return true;
        }

        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return true;
        }

        int slot = event.getRawSlot();
        if (slot == SLOT_BACK) {
            openExchangeMenu(player);
            return true;
        }

        switch (slot) {
            case SLOT_RULE_TOOL -> openRuleProbeMenu(player);
            case SLOT_RULE_SHIELD -> {
                tryUseRuleShield(player);
                openRuleToolMenu(player);
            }
            case SLOT_FAKE_BROADCAST -> {
                tryUseMisleadBroadcast(player);
                openRuleToolMenu(player);
            }
            default -> {
                return true;
            }
        }

        return true;
    }

    private boolean handleProbeMenuClick(InventoryClickEvent event) {
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return true;
        }

        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return true;
        }

        int slot = event.getRawSlot();
        if (slot == SLOT_BACK) {
            openRuleToolMenu(player);
            return true;
        }

        ItemStack clicked = event.getCurrentItem();
        ConditionType selected = extractProbeCondition(clicked);
        if (selected == null) {
            return true;
        }

        tryUseRuleProbe(player, selected);
        openRuleProbeMenu(player);
        return true;
    }

    private boolean handleArmorMenuClick(InventoryClickEvent event) {
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return true;
        }

        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return true;
        }

        int slot = event.getRawSlot();
        if (slot == SLOT_BACK) {
            openExchangeMenu(player);
            return true;
        }

        ItemStack clicked = event.getCurrentItem();
        ArmorSet armorSet = extractArmorSet(clicked);
        if (armorSet == null) {
            return true;
        }

        tryBuyArmorSet(player, armorSet);
        openArmorSetMenu(player);
        return true;
    }

    private boolean handleOreMenuClick(InventoryClickEvent event) {
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return true;
        }

        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return true;
        }

        int slot = event.getRawSlot();
        if (slot == SLOT_BACK) {
            openExchangeMenu(player);
            return true;
        }

        ItemStack clicked = event.getCurrentItem();
        OreExchange oreExchange = extractOreExchange(clicked);
        if (oreExchange == null) {
            return true;
        }

        if (oreExchange == OreExchange.ALL) {
            tryExchangeAllOres(player);
        } else {
            tryExchangeOre(player, oreExchange);
        }

        openOreExchangeMenu(player);
        return true;
    }

    private boolean handlePointItemMenuClick(InventoryClickEvent event) {
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return true;
        }
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return true;
        }

        int slot = event.getRawSlot();
        if (slot == SLOT_BACK) {
            openExchangeMenu(player);
            return true;
        }

        ItemStack clicked = event.getCurrentItem();
        PointItem pointItem = extractPointItem(clicked);
        if (pointItem == null) {
            return true;
        }
        tryUsePointItem(player, pointItem);
        openPointItemMenu(player);
        return true;
    }

    private void openExchangeMenu(Player player) {
        Inventory menu = Bukkit.createInventory(player, MENU_SIZE, MENU_TITLE);
        int taskPoints = getPlayerTaskPoints(player.getUniqueId());

        menu.setItem(SLOT_RULE_TOOL, createMenuItem(
                Material.PAPER,
                ChatColor.YELLOW + "规则道具",
                List.of(
                        ChatColor.GRAY + "进入二级页面使用规则类道具",
                        ChatColor.GRAY + "包含：规则试探/规则屏蔽/误导广播",
                        ChatColor.DARK_GRAY + "点击进入"
                )
        ));

        menu.setItem(SLOT_ARMOR_SET, createMenuItem(
                Material.IRON_CHESTPLATE,
                ChatColor.GREEN + "积分兑换装备强化",
                List.of(
                        ChatColor.GRAY + "使用任务积分强化当前穿戴护甲",
                        ChatColor.GRAY + "当前积分：" + ChatColor.LIGHT_PURPLE + taskPoints,
                        ChatColor.DARK_GRAY + "点击进入装备强化"
                )
        ));

        menu.setItem(SLOT_POINT_ITEMS, createMenuItem(
                Material.ENDER_EYE,
                ChatColor.LIGHT_PURPLE + "积分兑换道具",
                List.of(
                        ChatColor.GRAY + "使用积分兑换功能型道具",
                        ChatColor.GRAY + "当前积分：" + ChatColor.LIGHT_PURPLE + taskPoints,
                        ChatColor.DARK_GRAY + "点击进入道具兑换"
                )
        ));

        menu.setItem(SLOT_ORE_EXCHANGE, createMenuItem(
                Material.EMERALD,
                ChatColor.AQUA + "矿物兑换积分",
                List.of(
                        ChatColor.GRAY + "将背包矿物兑换为任务积分",
                        ChatColor.GRAY + "当前积分：" + ChatColor.LIGHT_PURPLE + taskPoints,
                        ChatColor.DARK_GRAY + "点击进入矿物兑换"
                )
        ));

        menu.setItem(SLOT_INFO, createMenuItem(
                Material.COMPASS,
                ChatColor.GOLD + "兑换信息",
                List.of(
                        ChatColor.GRAY + "通用兑换券：" + ChatColor.GREEN + choiceTokens.getOrDefault(player.getUniqueId(), 0),
                        ChatColor.GRAY + "任务积分：" + ChatColor.LIGHT_PURPLE + taskPoints,
                        ChatColor.GRAY + "奖励模式：" + ChatColor.AQUA + plugin.getConfig().getString("powerups.first-finisher-reward-mode", "choice"),
                        ChatColor.DARK_GRAY + "每轮任务第一个完成者可获得道具权限"
                )
        ));

        player.openInventory(menu);
    }

    private void openPointItemMenu(Player player) {
        Inventory menu = Bukkit.createInventory(player, MENU_SIZE, POINT_ITEM_MENU_TITLE);
        int points = getPlayerTaskPoints(player.getUniqueId());
        PointItem[] pointItems = PointItem.values();
        for (int i = 0; i < pointItems.length && i < ARMOR_SELECT_SLOTS.length; i++) {
            PointItem pointItem = pointItems[i];
            menu.setItem(ARMOR_SELECT_SLOTS[i], createPointItem(pointItem, points));
        }

        menu.setItem(4, createMenuItem(
                Material.EXPERIENCE_BOTTLE,
                ChatColor.LIGHT_PURPLE + "当前积分：" + points,
                List.of(
                        ChatColor.GRAY + "道具效果立即生效",
                        ChatColor.DARK_GRAY + "点击下方道具进行兑换"
                )
        ));

        menu.setItem(SLOT_BACK, createMenuItem(
                Material.ARROW,
                ChatColor.YELLOW + "返回兑换菜单",
                List.of(ChatColor.GRAY + "你的积分：" + ChatColor.LIGHT_PURPLE + points)
        ));
        player.openInventory(menu);
    }

    private void openRuleToolMenu(Player player) {
        Inventory menu = Bukkit.createInventory(player, MENU_SIZE, RULE_TOOL_MENU_TITLE);
        long cooldownRemain = getShieldCooldownRemainSeconds(player.getUniqueId());

        menu.setItem(SLOT_RULE_TOOL, createMenuItem(
                Material.PAPER,
                ChatColor.YELLOW + "规则试探",
                List.of(
                        ChatColor.GRAY + "从已公开规则中选择一条进行检验（是/否）",
                        ChatColor.GRAY + "当前可用：" + ChatColor.GREEN + getProbeCredits(player.getUniqueId()),
                        ChatColor.DARK_GRAY + "点击后进入规则选择"
                )
        ));

        menu.setItem(SLOT_RULE_SHIELD, createMenuItem(
                Material.SHIELD,
                ChatColor.AQUA + "规则屏蔽",
                List.of(
                        ChatColor.GRAY + "在持续时间内触发自己规则不会淘汰",
                        ChatColor.GRAY + "当前可用：" + ChatColor.GREEN + getShieldCredits(player.getUniqueId()),
                        ChatColor.GRAY + "冷却剩余：" + ChatColor.YELLOW + cooldownRemain + " 秒",
                        ChatColor.DARK_GRAY + "点击立即使用"
                )
        ));

        menu.setItem(SLOT_FAKE_BROADCAST, createMenuItem(
                Material.NOTE_BLOCK,
                ChatColor.LIGHT_PURPLE + "误导广播",
                List.of(
                        ChatColor.GRAY + "下一次规则公开时额外插入一条假线索",
                        ChatColor.GRAY + "当前可用：" + ChatColor.GREEN + getMisleadCredits(player.getUniqueId()),
                        ChatColor.DARK_GRAY + "点击立即使用"
                )
        ));

        menu.setItem(SLOT_BACK, createMenuItem(
                Material.ARROW,
                ChatColor.YELLOW + "返回兑换菜单",
                List.of(
                        ChatColor.GRAY + "通用兑换券：" + ChatColor.GREEN + choiceTokens.getOrDefault(player.getUniqueId(), 0)
                )
        ));

        player.openInventory(menu);
    }

    private void openRuleProbeMenu(Player player) {
        Inventory menu = Bukkit.createInventory(player, MENU_SIZE, PROBE_MENU_TITLE);

        List<ConditionType> revealed = getRevealedConditionOptions();
        if (revealed.isEmpty()) {
            menu.setItem(13, createMenuItem(
                    Material.BARRIER,
                    ChatColor.RED + "暂无可检验规则",
                    List.of(
                            ChatColor.GRAY + "目前还没有公开规则",
                            ChatColor.DARK_GRAY + "请等待规则公开后再使用"
                    )
            ));
        } else {
            int limit = Math.min(revealed.size(), PROBE_SELECT_SLOTS.length);
            for (int i = 0; i < limit; i++) {
                ConditionType condition = revealed.get(i);
                menu.setItem(PROBE_SELECT_SLOTS[i], createProbeConditionItem(condition));
            }
        }

        menu.setItem(SLOT_BACK, createMenuItem(
                Material.ARROW,
                ChatColor.YELLOW + "返回规则道具",
                List.of(
                        ChatColor.GRAY + "可用次数：" + ChatColor.GREEN + getProbeCredits(player.getUniqueId())
                )
        ));

        player.openInventory(menu);
    }

    private void openArmorSetMenu(Player player) {
        Inventory menu = Bukkit.createInventory(player, MENU_SIZE, ARMOR_MENU_TITLE);
        int points = getPlayerTaskPoints(player.getUniqueId());

        ArmorSet[] sets = ArmorSet.values();
        int limit = Math.min(sets.length, ARMOR_SELECT_SLOTS.length);
        for (int i = 0; i < limit; i++) {
            ArmorSet set = sets[i];
            menu.setItem(ARMOR_SELECT_SLOTS[i], createArmorSetItem(set, points));
        }

        menu.setItem(4, createMenuItem(
                Material.EXPERIENCE_BOTTLE,
                ChatColor.LIGHT_PURPLE + "当前积分：" + points,
                List.of(
                        ChatColor.GRAY + "点击后对已穿戴护甲附魔强化",
                        ChatColor.DARK_GRAY + "不会发放整套装备"
                )
        ));

        menu.setItem(SLOT_BACK, createMenuItem(
                Material.ARROW,
                ChatColor.YELLOW + "返回兑换菜单",
                List.of(
                        ChatColor.GRAY + "你的积分：" + ChatColor.LIGHT_PURPLE + points
                )
        ));

        player.openInventory(menu);
    }

    private void openOreExchangeMenu(Player player) {
        Inventory menu = Bukkit.createInventory(player, MENU_SIZE, ORE_MENU_TITLE);
        int points = getPlayerTaskPoints(player.getUniqueId());

        int index = 0;
        for (OreExchange ore : OreExchange.values()) {
            if (ore == OreExchange.ALL) {
                continue;
            }
            if (index >= ORE_SELECT_SLOTS.length) {
                break;
            }
            menu.setItem(ORE_SELECT_SLOTS[index], createOreExchangeItem(player, ore));
            index++;
        }

        menu.setItem(4, createMenuItem(
                Material.EXPERIENCE_BOTTLE,
                ChatColor.LIGHT_PURPLE + "一键全部兑换",
                List.of(
                        ChatColor.GRAY + "可将背包所有可兑换矿物换成积分",
                        ChatColor.GRAY + "当前积分：" + ChatColor.LIGHT_PURPLE + points,
                        ChatColor.DARK_GRAY + "点击执行"
                )
        ));
        ItemStack allItem = menu.getItem(4);
        if (allItem != null && allItem.getItemMeta() != null) {
            ItemMeta allMeta = allItem.getItemMeta();
            allMeta.getPersistentDataContainer().set(oreExchangeKey, PersistentDataType.STRING, OreExchange.ALL.name());
            allItem.setItemMeta(allMeta);
            menu.setItem(4, allItem);
        }

        menu.setItem(SLOT_BACK, createMenuItem(
                Material.ARROW,
                ChatColor.YELLOW + "返回兑换菜单",
                List.of(
                        ChatColor.GRAY + "你的积分：" + ChatColor.LIGHT_PURPLE + points
                )
        ));

        player.openInventory(menu);
    }

    private ItemStack createProbeConditionItem(ConditionType condition) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.setDisplayName(ChatColor.YELLOW + condition.getDisplayName());
        meta.setLore(List.of(
                ChatColor.GRAY + "点击检验该规则是否属于你",
                ChatColor.DARK_GRAY + "将消耗 1 次规则试探"
        ));
        meta.getPersistentDataContainer().set(probeConditionKey, PersistentDataType.STRING, condition.name());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createArmorSetItem(ArmorSet armorSet, int currentPoints) {
        int price = armorSet.price(plugin);
        boolean affordable = currentPoints >= price;

        ItemStack item = new ItemStack(armorSet.iconMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.setDisplayName(ChatColor.GREEN + armorSet.displayName);
        meta.setLore(List.of(
                ChatColor.GRAY + "价格：" + ChatColor.LIGHT_PURPLE + price + " 积分",
                ChatColor.GRAY + "效果：保护 " + armorSet.protectionLevel + "，耐久 " + armorSet.unbreakingLevel,
                ChatColor.GRAY + "对象：当前已穿戴护甲（不会发放新装备）",
                affordable ? ChatColor.GREEN + "可兑换" : ChatColor.RED + "积分不足",
                ChatColor.DARK_GRAY + "点击立即兑换"
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(armorSetKey, PersistentDataType.STRING, armorSet.name());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPointItem(PointItem pointItem, int currentPoints) {
        int price = pointItem.price(plugin);
        boolean affordable = currentPoints >= price;
        ItemStack item = new ItemStack(pointItem.iconMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + pointItem.displayName);
        meta.setLore(List.of(
                ChatColor.GRAY + "价格：" + ChatColor.LIGHT_PURPLE + price + " 积分",
                ChatColor.GRAY + pointItem.description,
                affordable ? ChatColor.GREEN + "可兑换" : ChatColor.RED + "积分不足",
                ChatColor.DARK_GRAY + "点击立即使用"
        ));
        meta.getPersistentDataContainer().set(pointItemKey, PersistentDataType.STRING, pointItem.name());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createOreExchangeItem(Player player, OreExchange oreExchange) {
        int unitPoints = oreExchange.points(plugin);
        int count = countMaterial(player.getInventory(), oreExchange.material);
        int totalPoints = count * unitPoints;

        ItemStack item = new ItemStack(oreExchange.iconMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.setDisplayName(ChatColor.AQUA + oreExchange.displayName);
        meta.setLore(List.of(
                ChatColor.GRAY + "单价：" + ChatColor.LIGHT_PURPLE + unitPoints + " 积分/个",
                ChatColor.GRAY + "持有：" + ChatColor.GREEN + count,
                ChatColor.GRAY + "可兑换：" + ChatColor.LIGHT_PURPLE + totalPoints + " 积分",
                ChatColor.DARK_GRAY + "点击兑换该矿物（全部数量）"
        ));
        meta.getPersistentDataContainer().set(oreExchangeKey, PersistentDataType.STRING, oreExchange.name());
        item.setItemMeta(meta);
        return item;
    }

    private ConditionType extractProbeCondition(ItemStack item) {
        if (item == null) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }

        String key = meta.getPersistentDataContainer().get(probeConditionKey, PersistentDataType.STRING);
        if (key == null || key.isBlank()) {
            return null;
        }

        try {
            return ConditionType.valueOf(key);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private ArmorSet extractArmorSet(ItemStack item) {
        if (item == null) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }

        String key = meta.getPersistentDataContainer().get(armorSetKey, PersistentDataType.STRING);
        if (key == null || key.isBlank()) {
            return null;
        }

        try {
            return ArmorSet.valueOf(key);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private OreExchange extractOreExchange(ItemStack item) {
        if (item == null) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }

        String key = meta.getPersistentDataContainer().get(oreExchangeKey, PersistentDataType.STRING);
        if (key == null || key.isBlank()) {
            return null;
        }

        try {
            return OreExchange.valueOf(key);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private PointItem extractPointItem(ItemStack item) {
        if (item == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        String key = meta.getPersistentDataContainer().get(pointItemKey, PersistentDataType.STRING);
        if (key == null || key.isBlank()) {
            return null;
        }
        try {
            return PointItem.valueOf(key);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private List<ConditionType> getRevealedConditionOptions() {
        if (conditionManager == null) {
            return List.of();
        }

        Set<ConditionType> deduplicated = new LinkedHashSet<>();
        for (ConditionManager.RevealedCondition revealed : conditionManager.getRevealedConditionsSnapshot()) {
            deduplicated.add(revealed.conditionType());
        }
        return new ArrayList<>(deduplicated);
    }

    private void tryUseRuleProbe(Player player, ConditionType asked) {
        if (!consumeCreditFor(player.getUniqueId(), PowerType.RULE_PROBE)) {
            uiManager.warn(player, "可用次数不足，任务第一名可以获得道具兑换权");
            return;
        }

        PlayerGameData data = playerDataManager.get(player.getUniqueId());
        boolean yes = data != null && asked == data.getAssignedCondition();

        uiManager.info(player, "规则试探：'" + asked.getDisplayName() + "' 是否属于你？");
        uiManager.info(player, yes ? ChatColor.GREEN + "结果：是" : ChatColor.RED + "结果：否");
    }

    private void tryUseRuleShield(Player player) {
        long cooldownRemain = getShieldCooldownRemainSeconds(player.getUniqueId());
        if (cooldownRemain > 0) {
            uiManager.warn(player, "规则屏蔽仍在冷却中，剩余 " + cooldownRemain + " 秒");
            return;
        }

        if (!consumeCreditFor(player.getUniqueId(), PowerType.RULE_SHIELD)) {
            uiManager.warn(player, "可用次数不足，任务第一名可以获得道具兑换权");
            return;
        }

        int duration = Math.max(1, plugin.getConfig().getInt("powerups.rule-shield.duration-seconds", 12));
        int cooldown = Math.max(duration, plugin.getConfig().getInt("powerups.rule-shield.cooldown-seconds", 120));

        long now = System.currentTimeMillis();
        shieldActiveUntilMillis.put(player.getUniqueId(), now + duration * 1000L);
        shieldCooldownUntilMillis.put(player.getUniqueId(), now + cooldown * 1000L);

        uiManager.success(player, "规则屏蔽已激活，持续 " + duration + " 秒，冷却 " + cooldown + " 秒");
    }

    private void tryUseMisleadBroadcast(Player player) {
        if (!consumeCreditFor(player.getUniqueId(), PowerType.FAKE_BROADCAST)) {
            uiManager.warn(player, "可用次数不足，任务第一名可以获得道具兑换权");
            return;
        }

        pendingFakeBroadcastCount++;
        uiManager.success(player, "误导广播已准备，将在下一次规则公开时生效");
    }

    private void tryUsePointItem(Player player, PointItem pointItem) {
        PlayerGameData data = playerDataManager.get(player.getUniqueId());
        if (data == null || data.isEliminated()) {
            uiManager.warn(player, "当前状态无法兑换该道具。");
            return;
        }
        int price = pointItem.price(plugin);
        if (data.getTaskPoints() < price) {
            uiManager.warn(player, "积分不足，需要 " + price + " 积分。");
            return;
        }

        boolean used = switch (pointItem) {
            case HIGHLIGHT_ALL_PLAYERS -> useHighlightAllPlayers(player);
            case SWAP_RANDOM_PLAYER -> useSwapRandomPlayer(player);
            case MINERAL_LOOTBOX -> useMineralLootbox(player);
            case PARDON_TASK_PENALTY -> useTaskPenaltyPardon(player);
            case READ_PHD -> useReadPhd(player, price);
        };
        if (!used) {
            return;
        }
        data.deductTaskPoints(price);
        uiManager.success(player, "道具已使用：" + pointItem.displayName + "，消耗 " + price + " 积分。");
    }

    private boolean useHighlightAllPlayers(Player player) {
        if (gameManager == null) {
            return false;
        }
        int durationSeconds = Math.max(3, plugin.getConfig().getInt("powerups.point-items.highlight-duration-seconds", 12));
        for (UUID playerId : gameManager.getActivePlayersSnapshot()) {
            Player target = plugin.getServer().getPlayer(playerId);
            if (target == null || !target.isOnline()) {
                continue;
            }
            target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, durationSeconds * 20, 0, false, false, true));
        }
        uiManager.broadcast("[道具] " + player.getName() + " 使用了全员高亮（" + durationSeconds + "秒）。");
        return true;
    }

    private boolean useSwapRandomPlayer(Player player) {
        if (gameManager == null) {
            return false;
        }
        List<Player> candidates = new ArrayList<>();
        for (UUID playerId : gameManager.getActivePlayersSnapshot()) {
            if (playerId.equals(player.getUniqueId())) {
                continue;
            }
            Player target = plugin.getServer().getPlayer(playerId);
            if (target != null && target.isOnline()) {
                candidates.add(target);
            }
        }
        if (candidates.isEmpty()) {
            uiManager.warn(player, "当前没有可互换位置的玩家。");
            return false;
        }
        Collections.shuffle(candidates, random);
        Player target = candidates.getFirst();
        Location playerLoc = player.getLocation().clone();
        Location targetLoc = target.getLocation().clone();
        player.teleport(targetLoc);
        target.teleport(playerLoc);
        uiManager.broadcast("[道具] " + player.getName() + " 与 " + target.getName() + " 互换了位置！");
        return true;
    }

    private boolean useMineralLootbox(Player player) {
        PlayerInventory inventory = player.getInventory();
        int picks = 1 + random.nextInt(2);
        int totalGiven = 0;
        for (int i = 0; i < picks; i++) {
            MineralLoot loot = MineralLoot.values()[random.nextInt(MineralLoot.values().length)];
            int amount = loot.minAmount + random.nextInt(loot.maxAmount - loot.minAmount + 1);
            ItemStack stack = new ItemStack(loot.material, amount);
            Map<Integer, ItemStack> remain = inventory.addItem(stack);
            if (!remain.isEmpty()) {
                for (ItemStack item : remain.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                }
            }
            totalGiven += amount;
        }
        uiManager.info(player, "矿物盲盒开启成功，共获得 " + totalGiven + " 个矿物。");
        return true;
    }

    private boolean useTaskPenaltyPardon(Player player) {
        if (taskManager == null) {
            uiManager.warn(player, "任务系统不可用，无法赦免。");
            return false;
        }
        boolean granted = taskManager.grantPenaltyPardon(player.getUniqueId());
        if (!granted) {
            uiManager.warn(player, "当前无法使用赦免（请在对局进行中使用）。");
            return false;
        }
        uiManager.info(player, "已获得本次任务失败惩罚赦免。");
        return true;
    }

    private boolean useReadPhd(Player player, int price) {
        int goodWeight = Math.max(0, plugin.getConfig().getInt("powerups.read-phd.probability.good", 75));
        int badWeight = Math.max(0, plugin.getConfig().getInt("powerups.read-phd.probability.bad", 25));
        if (pickByWeight(goodWeight, badWeight) == 0) {
            return applyReadPhdGoodEffect(player, price);
        }
        return applyReadPhdBadEffect(player);
    }

    private boolean applyReadPhdGoodEffect(Player player, int price) {
        int armorWeight = Math.max(0, plugin.getConfig().getInt("powerups.read-phd.good-effects.armor", 40));
        int ruleTokenWeight = Math.max(0, plugin.getConfig().getInt("powerups.read-phd.good-effects.rule-token", 30));
        int pointsWeight = Math.max(0, plugin.getConfig().getInt("powerups.read-phd.good-effects.points", 30));
        int goodChoice = pickByWeight(armorWeight, ruleTokenWeight, pointsWeight);

        if (goodChoice == 0) {
            if (giveRandomArmorForEmptySlot(player)) {
                uiManager.success(player, "读博结果：欧皇附体，补全了一件护甲。");
                return true;
            }
            // 若没有空护甲位，回退到积分奖励
        }
        if (goodChoice == 1) {
            choiceTokens.merge(player.getUniqueId(), 1, Integer::sum);
            uiManager.success(player, "读博结果：获得 1 次规则类道具使用权。");
            return true;
        }

        PlayerGameData data = playerDataManager.get(player.getUniqueId());
        if (data == null) {
            return false;
        }
        int gain = Math.max(1, (int) Math.round(price * (0.5 + random.nextDouble())));
        data.addTaskPoints(gain);
        uiManager.success(player, "读博结果：获得 +" + gain + " 积分。");
        return true;
    }

    private boolean applyReadPhdBadEffect(Player player) {
        int losePointsWeight = Math.max(0, plugin.getConfig().getInt("powerups.read-phd.bad-effects.lose-points", 25));
        int summonMobWeight = Math.max(0, plugin.getConfig().getInt("powerups.read-phd.bad-effects.summon-mob", 25));
        int tntWeight = Math.max(0, plugin.getConfig().getInt("powerups.read-phd.bad-effects.tnt", 25));
        int loseItemWeight = Math.max(0, plugin.getConfig().getInt("powerups.read-phd.bad-effects.lose-item", 25));
        int roll = pickByWeight(losePointsWeight, summonMobWeight, tntWeight, loseItemWeight);
        PlayerGameData data = playerDataManager.get(player.getUniqueId());
        switch (roll) {
            case 0 -> {
                if (data == null) {
                    return false;
                }
                int lose = 5 + random.nextInt(6);
                data.deductTaskPoints(lose);
                uiManager.warn(player, "读博翻车：失去 " + lose + " 积分。");
                return true;
            }
            case 1 -> {
                EntityType[] mobs = new EntityType[]{EntityType.SILVERFISH, EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER};
                EntityType chosen = mobs[random.nextInt(mobs.length)];
                player.getWorld().spawnEntity(player.getLocation(), chosen);
                uiManager.warn(player, "读博翻车：你身边出现了 " + chosen.name() + "！");
                return true;
            }
            case 2 -> {
                TNTPrimed tnt = (TNTPrimed) player.getWorld().spawnEntity(player.getLocation(), EntityType.TNT);
                tnt.setFuseTicks(60);
                uiManager.warn(player, "读博翻车：脚下出现了点燃的 TNT！");
                return true;
            }
            default -> {
                if (removeRandomArmorOrHotbarItem(player)) {
                    uiManager.warn(player, "读博翻车：你随机失去了一件护甲或一格快捷栏物品。");
                } else {
                    uiManager.warn(player, "读博翻车：但你没有可失去的装备或快捷栏物品。");
                }
                return true;
            }
        }
    }

    private int pickByWeight(int... weights) {
        int total = 0;
        for (int weight : weights) {
            total += Math.max(0, weight);
        }
        if (total <= 0) {
            return 0;
        }
        int roll = random.nextInt(total);
        int cumulative = 0;
        for (int i = 0; i < weights.length; i++) {
            cumulative += Math.max(0, weights[i]);
            if (roll < cumulative) {
                return i;
            }
        }
        return 0;
    }

    private boolean giveRandomArmorForEmptySlot(Player player) {
        PlayerInventory inv = player.getInventory();
        List<Integer> emptySlots = new ArrayList<>();
        if (inv.getHelmet() == null || inv.getHelmet().getType() == Material.AIR) {
            emptySlots.add(0);
        }
        if (inv.getChestplate() == null || inv.getChestplate().getType() == Material.AIR) {
            emptySlots.add(1);
        }
        if (inv.getLeggings() == null || inv.getLeggings().getType() == Material.AIR) {
            emptySlots.add(2);
        }
        if (inv.getBoots() == null || inv.getBoots().getType() == Material.AIR) {
            emptySlots.add(3);
        }
        if (emptySlots.isEmpty()) {
            return false;
        }
        int slotType = emptySlots.get(random.nextInt(emptySlots.size()));
        int tier = random.nextInt(5); // 0: leather, 1: chain, 2: golden, 3: iron, 4: diamond
        Material armor = resolveArmorMaterial(slotType, tier);
        ItemStack item = new ItemStack(armor);
        switch (slotType) {
            case 0 -> inv.setHelmet(item);
            case 1 -> inv.setChestplate(item);
            case 2 -> inv.setLeggings(item);
            case 3 -> inv.setBoots(item);
            default -> {
                return false;
            }
        }
        return true;
    }

    private Material resolveArmorMaterial(int slotType, int tier) {
        return switch (slotType) {
            case 0 -> switch (tier) {
                case 0 -> Material.LEATHER_HELMET;
                case 1 -> Material.CHAINMAIL_HELMET;
                case 2 -> Material.GOLDEN_HELMET;
                case 3 -> Material.IRON_HELMET;
                default -> Material.DIAMOND_HELMET;
            };
            case 1 -> switch (tier) {
                case 0 -> Material.LEATHER_CHESTPLATE;
                case 1 -> Material.CHAINMAIL_CHESTPLATE;
                case 2 -> Material.GOLDEN_CHESTPLATE;
                case 3 -> Material.IRON_CHESTPLATE;
                default -> Material.DIAMOND_CHESTPLATE;
            };
            case 2 -> switch (tier) {
                case 0 -> Material.LEATHER_LEGGINGS;
                case 1 -> Material.CHAINMAIL_LEGGINGS;
                case 2 -> Material.GOLDEN_LEGGINGS;
                case 3 -> Material.IRON_LEGGINGS;
                default -> Material.DIAMOND_LEGGINGS;
            };
            default -> switch (tier) {
                case 0 -> Material.LEATHER_BOOTS;
                case 1 -> Material.CHAINMAIL_BOOTS;
                case 2 -> Material.GOLDEN_BOOTS;
                case 3 -> Material.IRON_BOOTS;
                default -> Material.DIAMOND_BOOTS;
            };
        };
    }

    private boolean removeRandomArmorOrHotbarItem(Player player) {
        PlayerInventory inv = player.getInventory();
        List<Runnable> removals = new ArrayList<>();
        if (inv.getHelmet() != null && inv.getHelmet().getType() != Material.AIR) {
            removals.add(() -> inv.setHelmet(null));
        }
        if (inv.getChestplate() != null && inv.getChestplate().getType() != Material.AIR) {
            removals.add(() -> inv.setChestplate(null));
        }
        if (inv.getLeggings() != null && inv.getLeggings().getType() != Material.AIR) {
            removals.add(() -> inv.setLeggings(null));
        }
        if (inv.getBoots() != null && inv.getBoots().getType() != Material.AIR) {
            removals.add(() -> inv.setBoots(null));
        }
        for (int i = 0; i <= 8; i++) {
            ItemStack slot = inv.getItem(i);
            int index = i;
            if (slot != null && slot.getType() != Material.AIR && !isPowerupCompass(slot)) {
                removals.add(() -> inv.setItem(index, null));
            }
        }
        if (removals.isEmpty()) {
            return false;
        }
        removals.get(random.nextInt(removals.size())).run();
        return true;
    }

    private boolean isPowerupCompass(ItemStack item) {
        if (item == null || item.getType() != Material.COMPASS || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        Byte marker = meta.getPersistentDataContainer().get(compassKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    private void tryExchangeOre(Player player, OreExchange oreExchange) {
        if (oreExchange == OreExchange.ALL) {
            tryExchangeAllOres(player);
            return;
        }

        PlayerGameData data = playerDataManager.get(player.getUniqueId());
        if (data == null) {
            uiManager.warn(player, "未找到玩家积分数据");
            return;
        }

        PlayerInventory inventory = player.getInventory();
        int amount = countMaterial(inventory, oreExchange.material);
        if (amount <= 0) {
            uiManager.warn(player, "你没有可兑换的" + oreExchange.displayName);
            return;
        }

        int removed = removeMaterial(inventory, oreExchange.material, amount);
        if (removed <= 0) {
            uiManager.warn(player, "兑换失败，请重试");
            return;
        }

        int gained = removed * oreExchange.points(plugin);
        data.addTaskPoints(gained);
        uiManager.success(player, "兑换成功：" + oreExchange.displayName + " x" + removed + "，+" + gained + " 积分");
    }

    private void tryExchangeAllOres(Player player) {
        PlayerGameData data = playerDataManager.get(player.getUniqueId());
        if (data == null) {
            uiManager.warn(player, "未找到玩家积分数据");
            return;
        }

        PlayerInventory inventory = player.getInventory();
        int totalGained = 0;
        int totalRemoved = 0;

        for (OreExchange oreExchange : OreExchange.values()) {
            if (oreExchange == OreExchange.ALL) {
                continue;
            }

            int amount = countMaterial(inventory, oreExchange.material);
            if (amount <= 0) {
                continue;
            }

            int removed = removeMaterial(inventory, oreExchange.material, amount);
            if (removed <= 0) {
                continue;
            }

            totalRemoved += removed;
            totalGained += removed * oreExchange.points(plugin);
        }

        if (totalRemoved <= 0 || totalGained <= 0) {
            uiManager.warn(player, "背包中没有可兑换矿物");
            return;
        }

        data.addTaskPoints(totalGained);
        uiManager.success(player, "已一键兑换矿物 x" + totalRemoved + "，+" + totalGained + " 积分");
    }

    private int countMaterial(PlayerInventory inventory, Material material) {
        int total = 0;
        for (ItemStack content : inventory.getContents()) {
            if (content == null || content.getType() != material) {
                continue;
            }
            total += content.getAmount();
        }
        return total;
    }

    private int removeMaterial(PlayerInventory inventory, Material material, int amount) {
        int left = amount;
        ItemStack[] contents = inventory.getContents();

        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack content = contents[i];
            if (content == null || content.getType() != material) {
                continue;
            }

            int stackAmount = content.getAmount();
            if (stackAmount <= left) {
                left -= stackAmount;
                contents[i] = null;
            } else {
                content.setAmount(stackAmount - left);
                contents[i] = content;
                left = 0;
            }
        }

        inventory.setContents(contents);
        return amount - left;
    }

    private void tryBuyArmorSet(Player player, ArmorSet armorSet) {
        PlayerGameData data = playerDataManager.get(player.getUniqueId());
        if (data == null) {
            uiManager.warn(player, "未找到玩家积分数据");
            return;
        }
        if (data.isEliminated()) {
            uiManager.warn(player, "已淘汰状态无法兑换强化");
            return;
        }

        int price = armorSet.price(plugin);
        int points = data.getTaskPoints();
        if (points < price) {
            uiManager.warn(player, "积分不足，需要 " + price + " 积分");
            return;
        }

        int upgradedCount = enchantEquippedArmor(player, armorSet);
        if (upgradedCount <= 0) {
            uiManager.warn(player, "你当前没有穿戴可强化的护甲。");
            return;
        }

        data.deductTaskPoints(price);
        uiManager.success(player, "强化成功：" + armorSet.displayName + "，强化部位 " + upgradedCount
                + " 件，消耗 " + price + " 积分，剩余 " + data.getTaskPoints() + " 积分");
    }

    private int enchantEquippedArmor(Player player, ArmorSet armorSet) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] equipped = new ItemStack[]{
                inventory.getHelmet(),
                inventory.getChestplate(),
                inventory.getLeggings(),
                inventory.getBoots()
        };
        int upgradedCount = 0;
        for (ItemStack armor : equipped) {
            if (armor == null || armor.getType() == Material.AIR) {
                continue;
            }
            armor.addUnsafeEnchantment(Enchantment.PROTECTION, armorSet.protectionLevel);
            armor.addUnsafeEnchantment(Enchantment.UNBREAKING, armorSet.unbreakingLevel);
            upgradedCount++;
        }
        return upgradedCount;
    }

    private boolean consumeCreditFor(UUID playerId, PowerType type) {
        Map<UUID, Integer> typedMap = switch (type) {
            case RULE_PROBE -> probeCredits;
            case RULE_SHIELD -> shieldCredits;
            case FAKE_BROADCAST -> misleadCredits;
        };

        int owned = typedMap.getOrDefault(playerId, 0);
        if (owned > 0) {
            typedMap.put(playerId, owned - 1);
            return true;
        }

        int generic = choiceTokens.getOrDefault(playerId, 0);
        if (generic > 0) {
            choiceTokens.put(playerId, generic - 1);
            return true;
        }

        return false;
    }

    private int getProbeCredits(UUID playerId) {
        return probeCredits.getOrDefault(playerId, 0) + choiceTokens.getOrDefault(playerId, 0);
    }

    private int getShieldCredits(UUID playerId) {
        return shieldCredits.getOrDefault(playerId, 0) + choiceTokens.getOrDefault(playerId, 0);
    }

    private int getMisleadCredits(UUID playerId) {
        return misleadCredits.getOrDefault(playerId, 0) + choiceTokens.getOrDefault(playerId, 0);
    }

    private int getPlayerTaskPoints(UUID playerId) {
        PlayerGameData data = playerDataManager.get(playerId);
        return data == null ? 0 : data.getTaskPoints();
    }

    private long getShieldCooldownRemainSeconds(UUID playerId) {
        long remain = shieldCooldownUntilMillis.getOrDefault(playerId, 0L) - System.currentTimeMillis();
        if (remain <= 0L) {
            return 0L;
        }
        return (remain + 999L) / 1000L;
    }

    private ConditionType randomConditionExcluding(ConditionType excluded) {
        List<ConditionType> pool = new ArrayList<>(List.of(ConditionType.values()));
        Set<ConditionType> unavailable = new LinkedHashSet<>();
        unavailable.add(excluded);

        if (gameManager != null) {
            for (UUID playerId : gameManager.getActivePlayersSnapshot()) {
                PlayerGameData data = playerDataManager.get(playerId);
                if (data == null || data.getAssignedCondition() == null) {
                    continue;
                }
                unavailable.add(data.getAssignedCondition());
            }
        }

        if (conditionManager != null) {
            for (ConditionManager.RevealedCondition revealed : conditionManager.getRevealedConditionsSnapshot()) {
                unavailable.add(revealed.conditionType());
            }
        }

        pool.removeIf(unavailable::contains);
        if (pool.isEmpty()) {
            return null;
        }
        return pool.get(random.nextInt(pool.size()));
    }

    private void giveMenuCompass(Player player) {
        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta meta = compass.getItemMeta();
        if (meta == null) {
            return;
        }

        meta.setDisplayName(ChatColor.GOLD + "道具指南针");
        meta.setLore(List.of(
                ChatColor.GRAY + "右键打开道具兑换菜单",
                ChatColor.GRAY + "该指南针无追踪敌人功能"
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(compassKey, PersistentDataType.BYTE, (byte) 1);

        if (meta instanceof CompassMeta compassMeta) {
            compassMeta.setLodestoneTracked(false);
        }

        compass.setItemMeta(meta);

        int slot = Math.max(0, Math.min(8, plugin.getConfig().getInt("powerups.menu-compass-slot", 8)));
        player.getInventory().setItem(slot, compass);
    }

    private ItemStack createMenuItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private enum PowerType {
        RULE_PROBE,
        RULE_SHIELD,
        FAKE_BROADCAST
    }

    private enum OreExchange {
        ALL("全部矿物", "all", 0, Material.EXPERIENCE_BOTTLE, Material.EXPERIENCE_BOTTLE),
        COAL("煤炭", "coal", 1, Material.COAL, Material.COAL),
        IRON_INGOT("铁锭", "iron_ingot", 2, Material.IRON_INGOT, Material.IRON_INGOT),
        GOLD_INGOT("金锭", "gold_ingot", 3, Material.GOLD_INGOT, Material.GOLD_INGOT),
        DIAMOND("钻石", "diamond", 4, Material.DIAMOND, Material.DIAMOND);

        private final String displayName;
        private final String configKey;
        private final int defaultPoints;
        private final Material iconMaterial;
        private final Material material;

        OreExchange(String displayName, String configKey, int defaultPoints, Material iconMaterial, Material material) {
            this.displayName = displayName;
            this.configKey = configKey;
            this.defaultPoints = defaultPoints;
            this.iconMaterial = iconMaterial;
            this.material = material;
        }

        private int points(HiddenEliminationPlugin plugin) {
            return Math.max(0, plugin.getConfig().getInt("powerups.ore-exchange-points." + configKey, defaultPoints));
        }
    }

    private enum PointItem {
        HIGHLIGHT_ALL_PLAYERS("高亮所有玩家", "highlight_all_players", 12, Material.SPECTRAL_ARROW, "使所有存活玩家发光一段时间"),
        SWAP_RANDOM_PLAYER("随机互换位置", "swap_random_player", 10, Material.ENDER_PEARL, "与随机一名存活玩家互换位置"),
        MINERAL_LOOTBOX("矿物盲盒", "mineral_lootbox", 8, Material.CHEST, "随机获得若干矿物资源"),
        PARDON_TASK_PENALTY("赦免任务惩罚", "pardon_task_penalty", 9, Material.TOTEM_OF_UNDYING, "免除本次任务失败惩罚一次"),
        READ_PHD("读博", "read_phd", 10, Material.ENCHANTED_BOOK, "75%正面效果，25%负面效果的随机事件");

        private final String displayName;
        private final String configKey;
        private final int defaultPrice;
        private final Material iconMaterial;
        private final String description;

        PointItem(String displayName, String configKey, int defaultPrice, Material iconMaterial, String description) {
            this.displayName = displayName;
            this.configKey = configKey;
            this.defaultPrice = defaultPrice;
            this.iconMaterial = iconMaterial;
            this.description = description;
        }

        private int price(HiddenEliminationPlugin plugin) {
            return Math.max(0, plugin.getConfig().getInt("powerups.point-item-prices." + configKey, defaultPrice));
        }
    }

    private enum MineralLoot {
        AMETHYST_SHARD(Material.AMETHYST_SHARD, 6, 18),
        RAW_COPPER(Material.RAW_COPPER, 5, 16),
        RAW_GOLD(Material.RAW_GOLD, 1, 4),
        REDSTONE(Material.REDSTONE, 8, 20),
        LAPIS(Material.LAPIS_LAZULI, 6, 16);

        private final Material material;
        private final int minAmount;
        private final int maxAmount;

        MineralLoot(Material material, int minAmount, int maxAmount) {
            this.material = material;
            this.minAmount = minAmount;
            this.maxAmount = maxAmount;
        }
    }

    private enum ArmorSet {
        LEATHER(
                "基础强化 I",
                "leather",
                8,
                Material.LEATHER_CHESTPLATE,
                1,
                1
        ),
        CHAINMAIL(
                "基础强化 II",
                "chainmail",
                12,
                Material.CHAINMAIL_CHESTPLATE,
                2,
                1
        ),
        GOLDEN(
                "防护强化 I",
                "golden",
                16,
                Material.GOLDEN_CHESTPLATE,
                2,
                2
        ),
        IRON(
                "防护强化 II",
                "iron",
                22,
                Material.IRON_CHESTPLATE,
                3,
                2
        ),
        DIAMOND(
                "防护强化 III",
                "diamond",
                30,
                Material.DIAMOND_CHESTPLATE,
                3,
                3
        ),
        NETHERITE(
                "终极强化",
                "netherite",
                40,
                Material.NETHERITE_CHESTPLATE,
                4,
                3
        );

        private final String displayName;
        private final String configKey;
        private final int defaultPrice;
        private final Material iconMaterial;
        private final int protectionLevel;
        private final int unbreakingLevel;

        ArmorSet(
                String displayName,
                String configKey,
                int defaultPrice,
                Material iconMaterial,
                int protectionLevel,
                int unbreakingLevel
        ) {
            this.displayName = displayName;
            this.configKey = configKey;
            this.defaultPrice = defaultPrice;
            this.iconMaterial = iconMaterial;
            this.protectionLevel = protectionLevel;
            this.unbreakingLevel = unbreakingLevel;
        }

        private int price(HiddenEliminationPlugin plugin) {
            return Math.max(0, plugin.getConfig().getInt("powerups.armor-set-prices." + configKey, defaultPrice));
        }
    }
}
