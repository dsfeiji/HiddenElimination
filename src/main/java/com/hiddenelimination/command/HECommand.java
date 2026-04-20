package com.hiddenelimination.command;

import com.hiddenelimination.manager.GameManager;
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
            "join", "leave", "ready", "unready", "start", "stop", "status", "setlobby", "help"
    );

    private final PlayerDataManager playerDataManager;
    private final UIManager uiManager;
    private final SpawnManager spawnManager;
    private final GameManager gameManager;

    public HECommand(
            PlayerDataManager playerDataManager,
            UIManager uiManager,
            SpawnManager spawnManager,
            GameManager gameManager
    ) {
        this.playerDataManager = playerDataManager;
        this.uiManager = uiManager;
        this.spawnManager = spawnManager;
        this.gameManager = gameManager;
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

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("/he join - 加入房间");
        sender.sendMessage("/he leave - 离开房间");
        sender.sendMessage("/he ready - 准备");
        sender.sendMessage("/he unready - 取消准备");
        sender.sendMessage("/he start - 开始游戏（管理员）");
        sender.sendMessage("/he stop - 结束游戏（管理员）");
        sender.sendMessage("/he setlobby - 设置大厅（管理员）");
        sender.sendMessage("/he status - 查看当前状态");
    }
}