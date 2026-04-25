package com.hiddenelimination.command;

import com.hiddenelimination.manager.GameManager;
import com.hiddenelimination.manager.LobbyPanelManager;
import com.hiddenelimination.manager.PlayerDataManager;
import com.hiddenelimination.manager.SpawnManager;
import com.hiddenelimination.manager.UIManager;
import com.hiddenelimination.model.PlayerGameData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * /he 命令。
 */
public final class HECommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUB_COMMANDS = Arrays.asList(
            "join", "leave", "ready", "unready", "start", "stop", "status", "setlobby",
            "setlives", "setduration", "setreveal", "panel", "help"
    );

    private final PlayerDataManager playerDataManager;
    private final UIManager uiManager;
    private final SpawnManager spawnManager;
    private final GameManager gameManager;
    private final LobbyPanelManager lobbyPanelManager;

    public HECommand(
            PlayerDataManager playerDataManager,
            UIManager uiManager,
            SpawnManager spawnManager,
            GameManager gameManager,
            LobbyPanelManager lobbyPanelManager
    ) {
        this.playerDataManager = playerDataManager;
        this.uiManager = uiManager;
        this.spawnManager = spawnManager;
        this.gameManager = gameManager;
        this.lobbyPanelManager = lobbyPanelManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            sendHelp(sender);
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("该命令只能由玩家执行");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "join" -> handleJoin(player);
            case "leave" -> handleLeave(player);
            case "ready" -> handleReady(player, true);
            case "unready" -> handleReady(player, false);
            case "start" -> handleStart(player);
            case "stop" -> handleStop(player);
            case "status" -> uiManager.sendStatus(player, playerDataManager, gameManager.getGameState());
            case "setlobby" -> handleSetLobby(player);
            case "setlives" -> handleSetLives(player, args);
            case "setduration" -> handleSetDuration(player, args);
            case "setreveal" -> handleSetReveal(player, args);
            case "panel" -> handlePanel(player, args);
            default -> sendHelp(sender);
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String current = args[0].toLowerCase(Locale.ROOT);
            return SUB_COMMANDS.stream().filter(s -> s.startsWith(current)).collect(Collectors.toList());
        }
        if (args.length == 2 && "panel".equalsIgnoreCase(args[0])) {
            String current = args[1].toLowerCase(Locale.ROOT);
            return Arrays.asList("sethere", "rebuild", "cleanup").stream()
                    .filter(s -> s.startsWith(current))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private void handleJoin(Player player) {
        if (gameManager.isRunning()) {
            uiManager.error(player, "游戏已开始，无法中途加入");
            return;
        }

        playerDataManager.join(player);
        uiManager.success(player, "加入房间成功");
    }

    private void handleLeave(Player player) {
        if (gameManager.isRunning()) {
            gameManager.handleQuit(player);
        }
        playerDataManager.leave(player);
        uiManager.warn(player, "你已离开房间");
    }

    private void handleReady(Player player, boolean ready) {
        if (gameManager.isRunning()) {
            uiManager.error(player, "游戏进行中，无法修改准备状态");
            return;
        }

        PlayerGameData data = playerDataManager.getOrCreate(player.getUniqueId());
        if (!data.isJoined()) {
            data.setJoined(true);
        }

        playerDataManager.setReady(player, ready);
        if (ready) {
            uiManager.success(player, "你已准备");
        } else {
            uiManager.warn(player, "你已取消准备");
        }
    }

    private void handleStart(Player player) {
        if (!player.hasPermission("hiddenelimination.admin") && !player.isOp()) {
            uiManager.error(player, "你没有权限开始游戏");
            return;
        }

        boolean started = gameManager.startGame(player);
        if (!started) {
            uiManager.warn(player, "开始游戏失败，请检查状态或准备人数");
        }
    }

    private void handleStop(Player player) {
        if (!player.hasPermission("hiddenelimination.admin") && !player.isOp()) {
            uiManager.error(player, "你没有权限结束游戏");
            return;
        }

        boolean stopped = gameManager.stopGame(player);
        if (!stopped) {
            uiManager.warn(player, "当前没有进行中的游戏");
        }
    }

    private void handleSetLobby(Player player) {
        if (!player.hasPermission("hiddenelimination.admin") && !player.isOp()) {
            uiManager.error(player, "你没有权限设置大厅");
            return;
        }

        spawnManager.setLobby(player.getLocation());
        uiManager.success(player, "大厅坐标已保存");
    }

    private void handleSetLives(Player player, String[] args) {
        if (!player.hasPermission("hiddenelimination.admin") && !player.isOp()) {
            uiManager.error(player, "你没有权限设置局内参数");
            return;
        }
        if (args.length < 2) {
            uiManager.warn(player, "用法：/he setlives <1-20>");
            return;
        }
        try {
            int lives = Integer.parseInt(args[1]);
            if (lives < 1 || lives > 20) {
                uiManager.warn(player, "初始命数范围应为 1-20");
                return;
            }
            gameManager.setLobbyInitialLivesOverride(lives);
            uiManager.success(player, "本局初始命数已设为 " + lives);
        } catch (NumberFormatException ex) {
            uiManager.warn(player, "请输入有效整数。");
        }
    }

    private void handleSetDuration(Player player, String[] args) {
        if (!player.hasPermission("hiddenelimination.admin") && !player.isOp()) {
            uiManager.error(player, "你没有权限设置局内参数");
            return;
        }
        if (args.length < 2) {
            uiManager.warn(player, "用法：/he setduration <秒数，0为不限时>");
            return;
        }
        try {
            long seconds = Long.parseLong(args[1]);
            if (seconds < 0 || seconds > 7200) {
                uiManager.warn(player, "总时长范围应为 0-7200 秒");
                return;
            }
            gameManager.setLobbyRoundDurationSecondsOverride(seconds);
            uiManager.success(player, "本局总时长已设为 " + (seconds == 0 ? "不限时" : seconds + " 秒"));
        } catch (NumberFormatException ex) {
            uiManager.warn(player, "请输入有效整数。");
        }
    }

    private void handleSetReveal(Player player, String[] args) {
        if (!player.hasPermission("hiddenelimination.admin") && !player.isOp()) {
            uiManager.error(player, "你没有权限设置局内参数");
            return;
        }
        if (args.length < 2) {
            uiManager.warn(player, "用法：/he setreveal <秒数>");
            return;
        }
        try {
            long seconds = Long.parseLong(args[1]);
            if (seconds < 10 || seconds > 1200) {
                uiManager.warn(player, "揭示间隔范围应为 10-1200 秒");
                return;
            }
            gameManager.setLobbyRevealIntervalSecondsOverride(seconds);
            uiManager.success(player, "本局规则揭示间隔已设为 " + seconds + " 秒");
        } catch (NumberFormatException ex) {
            uiManager.warn(player, "请输入有效整数。");
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("/he join - 加入房间");
        sender.sendMessage("/he leave - 离开房间");
        sender.sendMessage("/he ready - 准备");
        sender.sendMessage("/he unready - 取消准备");
        sender.sendMessage("/he start - 开始游戏（管理员）");
        sender.sendMessage("/he stop - 结束游戏（管理员）");
        sender.sendMessage("/he setlobby - 设置大厅（管理员）");
        sender.sendMessage("/he setlives <n> - 设置本局初始命数（管理员）");
        sender.sendMessage("/he setduration <sec> - 设置本局总时长，0不限时（管理员）");
        sender.sendMessage("/he setreveal <sec> - 设置本局规则揭示间隔（管理员）");
        sender.sendMessage("/he panel <sethere|rebuild|cleanup> - 管理大厅交互设置面板（管理员）");
        sender.sendMessage("/he status - 查看当前状态");
    }

    private void handlePanel(Player player, String[] args) {
        if (!player.hasPermission("hiddenelimination.admin") && !player.isOp()) {
            uiManager.error(player, "你没有权限管理大厅面板");
            return;
        }
        if (args.length < 2) {
            uiManager.warn(player, "用法：/he panel <sethere|rebuild|cleanup>");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "sethere" -> {
                boolean success = lobbyPanelManager.setAnchor(player.getLocation());
                if (!success) {
                    uiManager.error(player, "设置面板锚点失败：当前位置无效。");
                    return;
                }
                lobbyPanelManager.rebuildPanel();
                uiManager.success(player, "已将大厅面板锚点设置为当前位置并重建。");
            }
            case "rebuild" -> {
                lobbyPanelManager.rebuildPanel();
                uiManager.success(player, "大厅设置面板已重建。");
            }
            case "cleanup" -> {
                lobbyPanelManager.cleanupPanelEntities();
                uiManager.success(player, "已清理大厅设置面板实体。");
            }
            default -> uiManager.warn(player, "用法：/he panel <sethere|rebuild|cleanup>");
        }
    }
}