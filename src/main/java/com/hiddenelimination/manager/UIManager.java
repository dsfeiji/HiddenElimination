package com.hiddenelimination.manager;

import com.hiddenelimination.HiddenEliminationPlugin;
import com.hiddenelimination.model.GameState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 统一消息与界面管理器（侧边栏 + ActionBar）。
 */
public final class UIManager {

    private final HiddenEliminationPlugin plugin;

    private PlayerDataManager playerDataManager;
    private GameManager gameManager;
    private ConditionManager conditionManager;
    private TaskManager taskManager;

    private BukkitTask uiTask;

    public UIManager(HiddenEliminationPlugin plugin) {
        this.plugin = plugin;
    }

    public void bindManagers(
            PlayerDataManager playerDataManager,
            GameManager gameManager,
            ConditionManager conditionManager,
            TaskManager taskManager
    ) {
        this.playerDataManager = playerDataManager;
        this.gameManager = gameManager;
        this.conditionManager = conditionManager;
        this.taskManager = taskManager;
    }

    public String prefix() {
        return ChatColor.GOLD + plugin.getConfig().getString("messages.prefix", "[隐藏淘汰] ") + ChatColor.RESET;
    }

    public void info(Player player, String message) {
        player.sendMessage(prefix() + ChatColor.WHITE + message);
    }

    public void warn(Player player, String message) {
        player.sendMessage(prefix() + ChatColor.YELLOW + message);
    }

    public void error(Player player, String message) {
        player.sendMessage(prefix() + ChatColor.RED + message);
    }

    public void success(Player player, String message) {
        player.sendMessage(prefix() + ChatColor.GREEN + message);
    }

    public void broadcast(String message) {
        Bukkit.broadcastMessage(prefix() + ChatColor.AQUA + message);
    }

    public void showCenterTitleToAll(String titleText, String subtitleText) {
        Title.Times times = Title.Times.times(
                Duration.ofMillis(500),
                Duration.ofMillis(2500),
                Duration.ofMillis(800)
        );

        Component title = Component.text(titleText == null ? "" : titleText);
        Component subtitle = (subtitleText == null || subtitleText.isBlank())
                ? Component.empty()
                : Component.text(subtitleText);

        Title screenTitle = Title.title(title, subtitle, times);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(screenTitle);
        }
    }

    public void showRuleRevealTitleToAll(String ruleName, int delaySeconds) {
        Title.Times times = Title.Times.times(
                Duration.ofMillis(200),
                Duration.ofMillis(1800),
                Duration.ofMillis(400)
        );
        Title screenTitle = Title.title(
                Component.text(ChatColor.LIGHT_PURPLE + "新规则揭示"),
                Component.text(ChatColor.WHITE + ruleName + ChatColor.GRAY + "（" + delaySeconds + "秒后生效）"),
                times
        );
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(screenTitle);
        }
    }

    public void playSoundToAll(Sound sound, float volume, float pitch) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }

    public void sendStatus(CommandSender sender, PlayerDataManager playerDataManager, GameState gameState) {
        sender.sendMessage(ChatColor.GOLD + "========== 隐藏淘汰 状态 ==========");
        sender.sendMessage(ChatColor.YELLOW + "阶段: " + ChatColor.WHITE + gameState.name());
        sender.sendMessage(ChatColor.YELLOW + "已加入: " + ChatColor.WHITE + playerDataManager.getJoinedCount());
        sender.sendMessage(ChatColor.YELLOW + "已准备: " + ChatColor.WHITE + playerDataManager.getReadyCount());
        sender.sendMessage(ChatColor.YELLOW + "存活: " + ChatColor.WHITE + playerDataManager.getAliveCount());

        if (sender instanceof Player player && taskManager != null) {
            sender.sendMessage(ChatColor.YELLOW + "任务积分: " + ChatColor.WHITE + taskManager.getPlayerTaskPoints(player.getUniqueId()));
            sender.sendMessage(ChatColor.YELLOW + "累计赚取积分: " + ChatColor.WHITE + taskManager.getPlayerTotalEarnedTaskPoints(player.getUniqueId()));
            sender.sendMessage(ChatColor.YELLOW + "任务生命: " + ChatColor.WHITE + taskManager.getPlayerTaskLives(player.getUniqueId()));
            if (taskManager.hasActiveTask()) {
                sender.sendMessage(ChatColor.YELLOW + "当前任务: " + ChatColor.WHITE + taskManager.getCurrentTaskDisplay());
            }
        }
        if (gameManager != null) {
            sender.sendMessage(ChatColor.YELLOW + "本局初始命数: " + ChatColor.WHITE + gameManager.getRoundInitialLives());
            sender.sendMessage(ChatColor.YELLOW + "本局总时长: " + ChatColor.WHITE
                    + (gameManager.getRoundDurationSeconds() > 0 ? gameManager.getRoundDurationSeconds() + "秒" : "不限时"));
            sender.sendMessage(ChatColor.YELLOW + "规则揭示间隔: " + ChatColor.WHITE + gameManager.getRoundRevealIntervalSeconds() + "秒");
        }
    }

    public void startUiLoop() {
        stopUiLoop();

        this.uiTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (playerDataManager == null || gameManager == null || conditionManager == null || taskManager == null) {
                return;
            }

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.isOnline()) {
                    continue;
                }

                if (gameManager.isRunning()) {
                    updateGameSidebar(player);
                    updateGameActionBar(player);
                } else if (gameManager.getGameState() == GameState.WAITING) {
                    updateLobbySidebar(player);
                    clearActionBar(player);
                } else {
                    // ENDING 阶段暂停UI刷新，仅清空 ActionBar。
                    clearActionBar(player);
                }
            }
        }, 20L, 20L);
    }

    public void stopUiLoop() {
        if (uiTask != null) {
            uiTask.cancel();
            uiTask = null;
        }
    }

    private void updateLobbySidebar(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = board.registerNewObjective("he_lobby", "dummy", ChatColor.GOLD + "隐藏淘汰");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        int online = Bukkit.getOnlinePlayers().size();
        long ready = playerDataManager.getReadyCount();
        long joined = playerDataManager.getJoinedCount();

        List<String> lines = List.of(
                ChatColor.GRAY + "----------------",
                ChatColor.YELLOW + "模式: " + ChatColor.WHITE + "大厅",
                ChatColor.YELLOW + "在线: " + ChatColor.GREEN + online,
                ChatColor.YELLOW + "已加入: " + ChatColor.AQUA + joined,
                ChatColor.YELLOW + "已准备: " + ChatColor.GREEN + ready,
                ChatColor.DARK_GRAY + " ",
                ChatColor.GRAY + "右键绿色染料切换准备",
                ChatColor.GRAY + "----------------"
        );

        applyLines(objective, lines);
        player.setScoreboard(board);
    }

    private void updateGameSidebar(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = board.registerNewObjective("he_game", "dummy", ChatColor.RED + "对局进行中");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        long alive = gameManager.getActivePlayersSnapshot().stream()
                .map(playerDataManager::get)
                .filter(data -> data != null && !data.isEliminated())
                .count();

        long total = gameManager.getActivePlayersSnapshot().size();
        long revealedCount = conditionManager.getRevealedConditionsSnapshot().size();

        int points = taskManager.getPlayerTaskPoints(player.getUniqueId());
        int lives = taskManager.getPlayerTaskLives(player.getUniqueId());
        int progress = taskManager.getPlayerProgress(player.getUniqueId());
        int required = taskManager.getCurrentTaskRequiredCount();

        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.GRAY + "----------------");
        lines.add(ChatColor.YELLOW + "存活: " + ChatColor.GREEN + alive + ChatColor.GRAY + "/" + ChatColor.WHITE + total);
        lines.add(ChatColor.DARK_PURPLE + "[规则] 已公开: " + ChatColor.LIGHT_PURPLE + revealedCount);
        lines.add(ChatColor.GREEN + "[任务] 当前积分: " + ChatColor.YELLOW + points);
        lines.add(ChatColor.GREEN + "[任务] 任务生命: " + ChatColor.RED + lives);

        if (taskManager.hasActiveTask()) {
            lines.add(ChatColor.GREEN + "[任务] " + ChatColor.WHITE + taskManager.getCurrentTaskDisplay());
            lines.add(ChatColor.GREEN + "[任务] 进度: " + ChatColor.GREEN + progress + ChatColor.GRAY + "/" + ChatColor.WHITE + required);
            lines.add(ChatColor.GREEN + "[任务] 倒计时: " + ChatColor.RED + formatSeconds(taskManager.getSecondsUntilTaskDeadline()));
        } else {
            lines.add(ChatColor.GREEN + "[任务] 下个任务: " + ChatColor.AQUA + formatSeconds(taskManager.getSecondsUntilNextTaskPublish()));
        }

        lines.add(ChatColor.DARK_GRAY + " ");
        lines.add(ChatColor.DARK_PURPLE + "[规则] 最新公开:");

        List<ConditionManager.RevealedCondition> reveals = conditionManager.getRevealedConditionsSnapshot();
        if (reveals.isEmpty()) {
            lines.add(ChatColor.GRAY + "暂无");
        } else {
            int start = Math.max(0, reveals.size() - 4);
            for (int i = start; i < reveals.size(); i++) {
                ConditionManager.RevealedCondition rc = reveals.get(i);
                boolean triggered = conditionManager.getTriggeredConditionsSnapshot().contains(rc.conditionType());
                String status = triggered ? "已触发" : "未触发";
                ChatColor base = triggered ? ChatColor.GRAY : ChatColor.GREEN;
                lines.add(base + "- " + rc.conditionType().getDisplayName() + " [" + status + "]");
            }
        }

        lines.add(ChatColor.GRAY + "----------------");

        applyLines(objective, lines);
        player.setScoreboard(board);
    }

    private void updateGameActionBar(Player player) {
        long ruleRemain = conditionManager.getSecondsUntilNextReveal();

        String borderText;
        if (!gameManager.isBorderEnabled()) {
            borderText = ChatColor.GRAY + "缩圈开始: --:--";
        } else {
            long untilStart = gameManager.getBorderSecondsUntilStart();
            borderText = ChatColor.AQUA + "缩圈开始: " + ChatColor.YELLOW + formatSeconds(untilStart);
        }

        String taskText = ChatColor.GREEN + "下个任务: " + ChatColor.AQUA + formatSeconds(taskManager.getSecondsUntilNextTaskPublish());

        String text = ChatColor.GOLD + "下次公开规则: " + ChatColor.YELLOW + formatSeconds(ruleRemain)
                + ChatColor.DARK_GRAY + " | " + taskText
                + ChatColor.DARK_GRAY + " | " + borderText;
        player.sendActionBar(Component.text(text));
    }

    private void clearActionBar(Player player) {
        player.sendActionBar(Component.empty());
    }

    private void applyLines(Objective objective, List<String> lines) {
        int score = lines.size();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            line = line + ChatColor.values()[i % ChatColor.values().length];
            objective.getScore(line).setScore(score--);
        }
    }

    private String formatSeconds(long totalSeconds) {
        long minutes = Math.max(0L, totalSeconds) / 60;
        long seconds = Math.max(0L, totalSeconds) % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
}
