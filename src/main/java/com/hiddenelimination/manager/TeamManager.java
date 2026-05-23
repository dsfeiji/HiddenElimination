package com.hiddenelimination.manager;

import com.hiddenelimination.HiddenEliminationPlugin;
import com.hiddenelimination.model.ConditionType;
import com.hiddenelimination.model.GameModeType;
import com.hiddenelimination.model.PlayerGameData;
import com.hiddenelimination.model.TeamData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TeamManager {

    private static final int MAX_TEAMS = 6;
    private static final ChatColor[] TEAM_COLORS = {
            ChatColor.RED, ChatColor.BLUE, ChatColor.GREEN, ChatColor.YELLOW,
            ChatColor.GOLD, ChatColor.AQUA
    };
    private static final String[] TEAM_NAMES = {"红队", "蓝队", "绿队", "黄队", "金队", "青队"};
    private static final String[] TEAM_COLOR_KEYS = {"red", "blue", "green", "yellow", "gold", "aqua"};

    private static final String TEAM_MENU_TITLE = ChatColor.GOLD + "选择队伍";
    private static final int TEAM_COMPASS_SLOT = 0;

    private final NamespacedKey teamCompassKey;

    private final HiddenEliminationPlugin plugin;
    private final PlayerDataManager playerDataManager;
    private final UIManager uiManager;

    private final Map<Integer, TeamData> teamsById = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> teamIdByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, TeamData> activeTeamsByPlayer = new ConcurrentHashMap<>();

    private GameManager gameManager;

    public TeamManager(HiddenEliminationPlugin plugin, PlayerDataManager playerDataManager, UIManager uiManager) {
        this.plugin = plugin;
        this.playerDataManager = playerDataManager;
        this.uiManager = uiManager;
        this.teamCompassKey = new NamespacedKey(plugin, "team_compass");
    }

    public void bindGameManager(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    public boolean isTeamMode() {
        return plugin.getConfig().getString("game.mode", "free_for_all").equalsIgnoreCase("team");
    }

    public GameModeType getGameMode() {
        return GameModeType.fromConfig(plugin.getConfig().getString("game.mode", "free_for_all"));
    }

    public int getAvailableTeamCount() {
        return Math.min(MAX_TEAMS, Math.max(1,
                plugin.getConfig().getInt("game.available-team-count", MAX_TEAMS)));
    }

    public boolean joinTeam(Player player, int teamId) {
        if (gameManager != null && gameManager.isRunning()) {
            uiManager.error(player, "游戏进行中，无法切换队伍");
            return false;
        }

        if (teamId < 0 || teamId >= getAvailableTeamCount()) {
            uiManager.error(player, "无效的队伍编号，可选 0-" + (getAvailableTeamCount() - 1));
            return false;
        }

        int maxSize = plugin.getConfig().getInt("game.team-max-size", 4);
        long currentCount = countTeamMembers(teamId);
        if (currentCount >= maxSize) {
            uiManager.error(player, TEAM_NAMES[teamId] + " 已满（上限 " + maxSize + " 人）");
            return false;
        }

        int existingTeam = teamIdByPlayer.getOrDefault(player.getUniqueId(), -1);
        if (existingTeam == teamId) {
            uiManager.warn(player, "你已在 " + TEAM_NAMES[teamId] + " 中");
            return false;
        }

        if (existingTeam >= 0) {
            leaveTeamInternal(player.getUniqueId());
        }

        teamIdByPlayer.put(player.getUniqueId(), teamId);
        PlayerGameData data = playerDataManager.getOrCreate(player.getUniqueId());
        data.setTeamId(teamId);

        String displayName = TEAM_COLORS[teamId] + TEAM_NAMES[teamId] + ChatColor.RESET;
        uiManager.info(player, "你已加入 " + displayName);

        if (gameManager != null && gameManager.isRunning()) {
            rebuildActiveTeams();
        }

        return true;
    }

    public boolean joinTeamByColor(Player player, String colorKey) {
        for (int i = 0; i < TEAM_COLOR_KEYS.length && i < getAvailableTeamCount(); i++) {
            if (TEAM_COLOR_KEYS[i].equalsIgnoreCase(colorKey)) {
                return joinTeam(player, i);
            }
        }
        uiManager.error(player, "无效的队伍颜色，可选：red/blue/green/yellow/gold/aqua");
        return false;
    }

    public boolean leaveTeam(Player player) {
        if (gameManager != null && gameManager.isRunning()) {
            uiManager.error(player, "游戏进行中，无法退队");
            return false;
        }

        int teamId = teamIdByPlayer.getOrDefault(player.getUniqueId(), -1);
        if (teamId < 0) {
            uiManager.warn(player, "你未加入任何队伍");
            return false;
        }

        leaveTeamInternal(player.getUniqueId());
        uiManager.info(player, "你已退出 " + TEAM_COLORS[teamId] + TEAM_NAMES[teamId]);
        return true;
    }

    private void leaveTeamInternal(UUID playerId) {
        int teamId = teamIdByPlayer.remove(playerId);
        PlayerGameData data = playerDataManager.get(playerId);
        if (data != null) {
            data.setTeamId(-1);
        }
    }

    public TeamData getPlayerTeam(UUID playerId) {
        if (gameManager == null || !gameManager.isRunning()) {
            return null;
        }
        return activeTeamsByPlayer.get(playerId);
    }

    public TeamData getTeam(int teamId) {
        if (gameManager == null || !gameManager.isRunning()) {
            return null;
        }
        return teamsById.get(teamId);
    }

    public List<TeamData> getActiveTeams() {
        return new ArrayList<>(teamsById.values());
    }

    public List<TeamData> getActiveTeamsSorted() {
        List<TeamData> list = new ArrayList<>(teamsById.values());
        list.sort((a, b) -> {
            if (a.isEliminated() != b.isEliminated()) {
                return Boolean.compare(a.isEliminated(), b.isEliminated());
            }
            int aliveCmp = Integer.compare(b.getAliveCount(), a.getAliveCount());
            if (aliveCmp != 0) return aliveCmp;
            return Integer.compare(a.getTeamId(), b.getTeamId());
        });
        return list;
    }

    public int getTeamIdForPlayer(UUID playerId) {
        return teamIdByPlayer.getOrDefault(playerId, -1);
    }

    public List<String> getTeamInfoLines(int teamId) {
        List<String> lines = new ArrayList<>();
        if (teamId < 0 || teamId >= getAvailableTeamCount()) {
            return lines;
        }

        ChatColor color = TEAM_COLORS[teamId];
        String name = TEAM_NAMES[teamId];

        List<UUID> members = new ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : teamIdByPlayer.entrySet()) {
            if (entry.getValue() == teamId) {
                members.add(entry.getKey());
            }
        }

        lines.add(color + "=== " + name + " " + ChatColor.GRAY + "(" + members.size() + "人) ===");
        for (UUID uuid : members) {
            Player online = plugin.getServer().getPlayer(uuid);
            if (online != null) {
                PlayerGameData data = playerDataManager.get(uuid);
                boolean ready = data != null && data.isReady();
                String readyMark = ready ? ChatColor.GREEN + " [已准备]" : ChatColor.GRAY + " [未准备]";
                lines.add(color + "  - " + ChatColor.WHITE + online.getName() + readyMark);
            }
        }
        return lines;
    }

    public Map<Integer, String> getTeamListDisplay() {
        Map<Integer, String> result = new LinkedHashMap<>();
        for (int i = 0; i < getAvailableTeamCount(); i++) {
            long count = countTeamMembers(i);
            String entry = TEAM_COLORS[i] + TEAM_NAMES[i] + ChatColor.WHITE + " - " + ChatColor.GREEN + count + "人";
            result.put(i, entry);
        }
        return result;
    }

    private long countTeamMembers(int teamId) {
        return teamIdByPlayer.values().stream().filter(id -> id == teamId).count();
    }

    public String validateForStart(List<Player> readyPlayers) {
        int minTeamSize = Math.max(2, plugin.getConfig().getInt("game.min-team-size", 2));
        int minTeams = Math.max(2, plugin.getConfig().getInt("game.min-teams", 2));

        int availableCount = getAvailableTeamCount();
        List<Integer> activeTeamIds = new ArrayList<>();

        for (int i = 0; i < availableCount; i++) {
            long count = countTeamMembers(i);
            if (count > 0) {
                activeTeamIds.add(i);
            }
        }

        if (activeTeamIds.size() < minTeams) {
            return "至少需要 " + minTeams + " 支队伍有人加入";
        }

        for (int teamId : activeTeamIds) {
            long readyInTeam = countTeamReadyMembers(teamId, readyPlayers);
            if (readyInTeam < minTeamSize) {
                return TEAM_NAMES[teamId] + " 已准备人数不足（需要 " + minTeamSize + " 人）";
            }
        }

        for (Player player : readyPlayers) {
            if (teamIdByPlayer.getOrDefault(player.getUniqueId(), -1) < 0) {
                return player.getName() + " 未加入任何队伍";
            }
        }

        return null;
    }

    private long countTeamReadyMembers(int teamId, List<Player> readyPlayers) {
        return readyPlayers.stream()
                .filter(p -> teamIdByPlayer.getOrDefault(p.getUniqueId(), -1) == teamId)
                .count();
    }

    public void rebuildActiveTeams() {
        teamsById.clear();
        activeTeamsByPlayer.clear();

        int availableCount = getAvailableTeamCount();
        Map<Integer, Set<UUID>> memberMap = new LinkedHashMap<>();

        for (var entry : teamIdByPlayer.entrySet()) {
            int teamId = entry.getValue();
            if (teamId < 0 || teamId >= availableCount) {
                continue;
            }
            if (gameManager == null || !gameManager.isActivePlayer(entry.getKey())) {
                continue;
            }
            memberMap.computeIfAbsent(teamId, k -> new LinkedHashSet<>()).add(entry.getKey());
        }

        for (int teamId = 0; teamId < availableCount; teamId++) {
            Set<UUID> members = memberMap.get(teamId);
            if (members == null || members.isEmpty()) {
                continue;
            }
            Set<UUID> aliveSet = new LinkedHashSet<>();
            for (UUID uuid : members) {
                PlayerGameData data = playerDataManager.get(uuid);
                if (data != null && !data.isEliminated()) {
                    aliveSet.add(uuid);
                }
            }
            TeamData team = new TeamData(teamId, TEAM_COLORS[teamId], TEAM_NAMES[teamId], members);
            team.setAliveMemberIds(aliveSet);
            teamsById.put(teamId, team);

            for (UUID uuid : members) {
                activeTeamsByPlayer.put(uuid, team);
            }
        }
    }

    public void initTeamsFromLobby() {
        teamsById.clear();
        activeTeamsByPlayer.clear();
    }

    public boolean doesTriggerEliminateTeam(UUID playerId, ConditionType action) {
        if (!isTeamMode()) {
            return false;
        }

        if (gameManager == null || !gameManager.isRunning()) {
            return false;
        }

        TeamData team = getPlayerTeam(playerId);
        if (team == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        for (UUID memberId : team.getMemberIds()) {
            PlayerGameData data = playerDataManager.get(memberId);
            if (data == null || data.isEliminated()) {
                continue;
            }
            if (!data.isConditionRevealed()) {
                continue;
            }
            ConditionType assigned = data.getAssignedCondition();
            if (assigned == null || assigned != action) {
                continue;
            }
            if (data.getConditionActiveAtMillis() > now) {
                continue;
            }
            return true;
        }
        return false;
    }

    public void eliminateEntireTeam(int teamId, UUID triggerPlayerId, ConditionType triggeredCondition) {
        TeamData team = teamsById.get(teamId);
        if (team == null || team.isEliminated()) {
            return;
        }

        for (UUID memberId : new ArrayList<>(team.getAliveMemberIds())) {
            if (memberId.equals(triggerPlayerId)) {
                continue;
            }
            Player member = plugin.getServer().getPlayer(memberId);
            if (member != null && member.isOnline()) {
                gameManager.eliminatePlayerSilent(member, "同队成员触发条件：" + triggeredCondition.getDisplayName());
            } else {
                PlayerGameData data = playerDataManager.get(memberId);
                if (data != null && !data.isEliminated()) {
                    data.setEliminated(true);
                    data.setSpectator(true);
                }
            }
            team.memberEliminated(memberId);
        }

        team.memberEliminated(triggerPlayerId);
        team.setEliminated(true);
        team.setEliminatedAtMillis(System.currentTimeMillis());

        String triggerName = resolvePlayerName(triggerPlayerId);
        uiManager.broadcast(team.getTeamColor() + team.getDisplayName() + ChatColor.RESET
                + " 因成员 " + ChatColor.WHITE + triggerName
                + ChatColor.RESET + " 触发条件 " + ChatColor.GREEN + triggeredCondition.getDisplayName()
                + ChatColor.RESET + " 全员淘汰！");
    }

    public long getAliveTeamCount() {
        return teamsById.values().stream()
                .filter(t -> !t.isEliminated() && t.getAliveCount() > 0)
                .count();
    }

    public TeamData getOnlyAliveTeam() {
        TeamData result = null;
        for (TeamData team : teamsById.values()) {
            if (!team.isEliminated() && team.getAliveCount() > 0) {
                if (result != null) {
                    return null;
                }
                result = team;
            }
        }
        return result;
    }

    public String resolvePlayerName(UUID playerId) {
        Player online = plugin.getServer().getPlayer(playerId);
        if (online != null) {
            return online.getName();
        }
        String offlineName = plugin.getServer().getOfflinePlayer(playerId).getName();
        return offlineName == null || offlineName.isBlank() ? playerId.toString() : offlineName;
    }

    public void handleQuit(UUID playerId) {
        TeamData team = activeTeamsByPlayer.get(playerId);
        if (team != null) {
            team.memberEliminated(playerId);
        }
    }

    public void roundFinished() {
        teamsById.clear();
        activeTeamsByPlayer.clear();
    }

    public static ChatColor getTeamColor(int teamId) {
        if (teamId < 0 || teamId >= TEAM_COLORS.length) {
            return ChatColor.WHITE;
        }
        return TEAM_COLORS[teamId];
    }

    public static String getTeamName(int teamId) {
        if (teamId < 0 || teamId >= TEAM_NAMES.length) {
            return "个人";
        }
        return TEAM_NAMES[teamId];
    }

    public static String getTeamColorKey(int teamId) {
        if (teamId < 0 || teamId >= TEAM_COLOR_KEYS.length) {
            return "white";
        }
        return TEAM_COLOR_KEYS[teamId];
    }

    public void giveTeamCompass(Player player) {
        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta meta = compass.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.setDisplayName(ChatColor.GOLD + "模式 & 队伍");
        meta.setLore(List.of(
                ChatColor.GRAY + "右键打开菜单",
                ChatColor.GRAY + "查看模式 / 选择队伍"
        ));
        meta.getPersistentDataContainer().set(teamCompassKey, PersistentDataType.BYTE, (byte) 1);
        compass.setItemMeta(meta);
        player.getInventory().setItem(TEAM_COMPASS_SLOT, compass);
    }

    public boolean isTeamCompass(ItemStack item) {
        if (item == null || item.getType() != Material.COMPASS || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        Byte marker = meta.getPersistentDataContainer().get(teamCompassKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    public void openTeamSelectMenu(Player player) {
        if (gameManager != null && gameManager.isRunning()) {
            uiManager.warn(player, "游戏进行中，无法切换");
            return;
        }

        int size = 9;
        Inventory menu = Bukkit.createInventory(player, size, TEAM_MENU_TITLE);
        boolean isAdmin = player.hasPermission("hiddenelimination.admin") || player.isOp();

        if (isTeamMode()) {
            int availableCount = getAvailableTeamCount();
            int currentTeam = teamIdByPlayer.getOrDefault(player.getUniqueId(), -1);

            for (int i = 0; i < availableCount; i++) {
                ChatColor color = TEAM_COLORS[i];
                String name = TEAM_NAMES[i];
                long count = countTeamMembers(i);
                boolean isCurrent = (i == currentTeam);

                Material icon = switch (i) {
                    case 0 -> Material.RED_WOOL;
                    case 1 -> Material.BLUE_WOOL;
                    case 2 -> Material.LIME_WOOL;
                    case 3 -> Material.YELLOW_WOOL;
                    case 4 -> Material.ORANGE_WOOL;
                    case 5 -> Material.LIGHT_BLUE_WOOL;
                    default -> Material.WHITE_WOOL;
                };

                ItemStack item = new ItemStack(icon);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(color + name);
                    List<String> lore = new ArrayList<>();
                    lore.add(ChatColor.GRAY + "当前人数：" + ChatColor.GREEN + count);
                    if (isCurrent) {
                        lore.add(ChatColor.GREEN + "你已在此队伍中");
                    }
                    int maxSize = plugin.getConfig().getInt("game.team-max-size", 4);
                    if (count >= maxSize) {
                        lore.add(ChatColor.RED + "队伍已满");
                    } else {
                        lore.add(ChatColor.DARK_GRAY + "点击加入");
                    }
                    meta.setLore(lore);
                    meta.getPersistentDataContainer().set(teamCompassKey, PersistentDataType.BYTE, (byte) 1);
                    item.setItemMeta(meta);
                }
                menu.setItem(i, item);
            }

            if (currentTeam >= 0) {
                menu.setItem(7, createLeaveTeamItem());
            }
        } else {
            ItemStack info = new ItemStack(Material.PAPER);
            ItemMeta meta = info.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.WHITE + "当前模式：" + ChatColor.YELLOW + "个人混战");
                meta.setLore(List.of(ChatColor.GRAY + "个人混战模式下无需选择队伍"));
                info.setItemMeta(meta);
            }
            menu.setItem(4, info);
        }

        if (isAdmin) {
            boolean isTeam = isTeamMode();
            ItemStack toggle = new ItemStack(isTeam ? Material.REDSTONE_BLOCK : Material.EMERALD_BLOCK);
            ItemMeta meta = toggle.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(isTeam
                        ? ChatColor.LIGHT_PURPLE + "切换为" + ChatColor.WHITE + "个人混战"
                        : ChatColor.LIGHT_PURPLE + "切换为" + ChatColor.YELLOW + "团队对抗");
                meta.setLore(List.of(ChatColor.GRAY + "仅管理员可操作", ChatColor.DARK_GRAY + "点击切换游戏模式"));
                meta.getPersistentDataContainer().set(teamCompassKey, PersistentDataType.BYTE, (byte) 1);
                toggle.setItemMeta(meta);
            }
            menu.setItem(8, toggle);
        }

        player.openInventory(menu);
    }

    public boolean handleTeamMenuClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (event.getView() == null || !TEAM_MENU_TITLE.equals(event.getView().getTitle())) {
            return false;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return true;
        }
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return true;
        }

        int slot = event.getRawSlot();
        boolean isAdmin = player.hasPermission("hiddenelimination.admin") || player.isOp();

        if (slot == 8 && isAdmin) {
            toggleGameMode(player);
            player.closeInventory();
            return true;
        }

        if (slot == 7 && isTeamMode()) {
            int currentTeam = teamIdByPlayer.getOrDefault(player.getUniqueId(), -1);
            if (currentTeam >= 0) {
                leaveTeam(player);
                player.closeInventory();
                return true;
            }
        }

        if (isTeamMode()) {
            int availableCount = getAvailableTeamCount();
            if (slot >= 0 && slot < availableCount) {
                joinTeam(player, slot);
                player.closeInventory();
                return true;
            }
        }

        return true;
    }

    public boolean isTeamMenu(String title) {
        return TEAM_MENU_TITLE.equals(title);
    }

    private void toggleGameMode(Player player) {
        boolean wasTeam = isTeamMode();
        String newMode = wasTeam ? "free_for_all" : "team";
        plugin.getConfig().set("game.mode", newMode);
        plugin.saveConfig();
        String displayName = wasTeam ? "个人混战" : "团队对抗";
        uiManager.broadcast(ChatColor.GOLD + "游戏模式已切换为：" + ChatColor.YELLOW + displayName);
    }

    private ItemStack createLeaveTeamItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "退出当前队伍");
            meta.setLore(List.of(ChatColor.GRAY + "点击离开当前队伍"));
            meta.getPersistentDataContainer().set(teamCompassKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }
}
