package com.hiddenelimination;

import com.hiddenelimination.command.HECommand;
import com.hiddenelimination.listener.GameListener;
import com.hiddenelimination.listener.LobbyPanelListener;
import com.hiddenelimination.listener.LobbyProtectionListener;
import com.hiddenelimination.listener.PlayerJoinQuitListener;
import com.hiddenelimination.listener.PowerupListener;
import com.hiddenelimination.listener.PrepareItemListener;
import com.hiddenelimination.manager.ConditionManager;
import com.hiddenelimination.manager.GameManager;
import com.hiddenelimination.manager.LobbyPanelManager;
import com.hiddenelimination.manager.PlayerDataManager;
import com.hiddenelimination.manager.PowerupManager;
import com.hiddenelimination.manager.SpawnManager;
import com.hiddenelimination.manager.TaskManager;
import com.hiddenelimination.manager.TeamManager;
import com.hiddenelimination.manager.UIManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * HiddenElimination 插件主类。
 */
public final class HiddenEliminationPlugin extends JavaPlugin {

    private PlayerDataManager playerDataManager;
    private UIManager uiManager;
    private SpawnManager spawnManager;
    private ConditionManager conditionManager;
    private PowerupManager powerupManager;
    private GameManager gameManager;
    private TaskManager taskManager;
    private TeamManager teamManager;
    private LobbyPanelManager lobbyPanelManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.playerDataManager = new PlayerDataManager();
        this.uiManager = new UIManager(this);
        this.spawnManager = new SpawnManager(this);
        this.teamManager = new TeamManager(this, playerDataManager, uiManager);
        this.taskManager = new TaskManager(this, playerDataManager, uiManager);
        this.conditionManager = new ConditionManager(this, playerDataManager, uiManager);
        this.powerupManager = new PowerupManager(this, playerDataManager, uiManager);
        this.gameManager = new GameManager(
                this,
                playerDataManager,
                uiManager,
                spawnManager,
                conditionManager,
                taskManager,
                powerupManager
        );
        this.lobbyPanelManager = new LobbyPanelManager(this, spawnManager, gameManager, uiManager);

        this.teamManager.bindGameManager(gameManager);
        this.conditionManager.bindTaskManager(taskManager);
        this.conditionManager.bindPowerupManager(powerupManager);
        this.conditionManager.bindTeamManager(teamManager);
        this.taskManager.bindGameManager(gameManager);
        this.taskManager.bindPowerupManager(powerupManager);
        this.taskManager.bindTeamManager(teamManager);
        this.powerupManager.bindGameManager(gameManager);
        this.powerupManager.bindConditionManager(conditionManager);
        this.powerupManager.bindTaskManager(taskManager);
        this.powerupManager.bindTeamManager(teamManager);
        this.uiManager.bindManagers(playerDataManager, gameManager, conditionManager, taskManager);
        this.uiManager.bindTeamManager(teamManager);
        this.gameManager.bindLobbyPanelManager(lobbyPanelManager);
        this.gameManager.bindTeamManager(teamManager);

        registerCommand();
        registerListeners();

        spawnManager.initializePreparedGameWorldAsync();
        lobbyPanelManager.start();
        uiManager.startUiLoop();
        getServer().getScheduler().runTaskTimer(this, spawnManager::enforceLobbyEnvironment, 0L, 200L);
        getLogger().info("HiddenElimination 已启用");
    }

    @Override
    public void onDisable() {
        if (gameManager != null) {
            gameManager.shutdown();
        }
        if (uiManager != null) {
            uiManager.stopUiLoop();
        }
        if (lobbyPanelManager != null) {
            lobbyPanelManager.shutdown();
        }
        getLogger().info("HiddenElimination 已关闭");
    }

    private void registerCommand() {
        PluginCommand heCommand = getCommand("he");
        if (heCommand == null) {
            getLogger().severe("未在 plugin.yml 中找到 /he 命令定义，插件将被禁用");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        HECommand command = new HECommand(this, playerDataManager, uiManager, spawnManager, gameManager, lobbyPanelManager, teamManager);
        heCommand.setExecutor(command);
        heCommand.setTabCompleter(command);
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(
                new PrepareItemListener(playerDataManager, gameManager, uiManager, teamManager),
                this
        );
        getServer().getPluginManager().registerEvents(
                new PlayerJoinQuitListener(this, playerDataManager, spawnManager, uiManager, gameManager, lobbyPanelManager, teamManager),
                this
        );
        getServer().getPluginManager().registerEvents(
                new GameListener(gameManager, conditionManager, spawnManager, taskManager, teamManager, powerupManager),
                this
        );
        getServer().getPluginManager().registerEvents(
                new LobbyProtectionListener(gameManager, spawnManager),
                this
        );
        getServer().getPluginManager().registerEvents(
                new PowerupListener(gameManager, powerupManager),
                this
        );
        getServer().getPluginManager().registerEvents(
                new LobbyPanelListener(lobbyPanelManager, uiManager),
                this
        );
    }
}
