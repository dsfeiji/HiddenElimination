package com.hiddenelimination.manager;

import com.hiddenelimination.HiddenEliminationPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 大厅设置交互面板管理器（TextDisplay + Interaction）。
 */
public final class LobbyPanelManager {

    private static final String CONFIG_ROOT = "lobby-settings-panel";
    private static final String CONFIG_ANCHOR = CONFIG_ROOT + ".anchor";
    private static final String CONFIG_AREA = CONFIG_ROOT + ".area";
    private static final String CONFIG_FACE_TARGET = CONFIG_ROOT + ".face-target";
    private static final String DEFAULT_WORLD = "lobby";
    private static final double DEFAULT_AREA_MIN_X = 3.48D;
    private static final double DEFAULT_AREA_MIN_Y = -56.00D;
    private static final double DEFAULT_AREA_MIN_Z = -9.44D;
    private static final double DEFAULT_AREA_MAX_X = 15.38D;
    private static final double DEFAULT_AREA_MAX_Y = -50.51D;
    private static final double DEFAULT_AREA_MAX_Z = -9.00D;
    private static final double DEFAULT_FACE_X = 9.29D;
    private static final double DEFAULT_FACE_Y = -56.00D;
    private static final double DEFAULT_FACE_Z = 2.98D;

    private final HiddenEliminationPlugin plugin;
    private final SpawnManager spawnManager;
    private final GameManager gameManager;
    private final UIManager uiManager;

    private final org.bukkit.NamespacedKey panelMarkerKey;
    private final org.bukkit.NamespacedKey panelActionKey;
    private final org.bukkit.NamespacedKey panelSettingKey;
    private static final Set<String> PANEL_LABEL_KEYWORDS = Set.of(
            "大厅设置面板", "初始生命值", "对局总时长", "规则揭示间隔", "边界初始大小", "边界最终大小", "任务失败扣分", "[+]", "[-]"
    );

    private final Map<PanelSetting, TextDisplay> valueDisplays = new EnumMap<>(PanelSetting.class);

    private BukkitTask refreshTask;

    public LobbyPanelManager(
            HiddenEliminationPlugin plugin,
            SpawnManager spawnManager,
            GameManager gameManager,
            UIManager uiManager
    ) {
        this.plugin = plugin;
        this.spawnManager = spawnManager;
        this.gameManager = gameManager;
        this.uiManager = uiManager;
        this.panelMarkerKey = new org.bukkit.NamespacedKey(plugin, "lobby_settings_panel");
        this.panelActionKey = new org.bukkit.NamespacedKey(plugin, "lobby_settings_panel_action");
        this.panelSettingKey = new org.bukkit.NamespacedKey(plugin, "lobby_settings_panel_setting");
    }

    public void start() {
        if (refreshTask != null && !refreshTask.isCancelled()) {
            return;
        }
        cleanupPanelEntities();
        rebuildPanel();
        this.refreshTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::refreshPanel, 40L, 40L);
    }

    public void shutdown() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        valueDisplays.clear();
    }

    public boolean setAnchor(Location location) {
        if (location.getWorld() == null) {
            return false;
        }
        plugin.getConfig().set(CONFIG_ANCHOR + ".world", location.getWorld().getName());
        plugin.getConfig().set(CONFIG_ANCHOR + ".x", location.getX());
        plugin.getConfig().set(CONFIG_ANCHOR + ".y", location.getY());
        plugin.getConfig().set(CONFIG_ANCHOR + ".z", location.getZ());
        plugin.getConfig().set(CONFIG_ANCHOR + ".yaw", location.getYaw());
        plugin.getConfig().set(CONFIG_ANCHOR + ".pitch", location.getPitch());
        plugin.saveConfig();
        return true;
    }

    public void rebuildPanel() {
        cleanupPanelEntities();
        valueDisplays.clear();

        Location center = getPanelCenterLocation();
        if (center == null || center.getWorld() == null) {
            return;
        }
        World world = center.getWorld();

        Vector forward = resolveFacingForward(center);
        Vector right = new Vector(-forward.getZ(), 0.0D, forward.getX()).normalize();

        double titleYOffset = plugin.getConfig().getDouble(CONFIG_ROOT + ".layout.title-y-offset", 2.7D);
        double firstRowYOffset = plugin.getConfig().getDouble(CONFIG_ROOT + ".layout.first-row-y-offset", 2.1D);
        double rowGap = plugin.getConfig().getDouble(CONFIG_ROOT + ".layout.row-gap", 0.35D);
        double textForwardOffset = plugin.getConfig().getDouble(CONFIG_ROOT + ".layout.text-forward-offset", 0.7D);
        double buttonHorizontalOffset = plugin.getConfig().getDouble(CONFIG_ROOT + ".layout.button-horizontal-offset", 1.8D);
        double buttonForwardOffset = plugin.getConfig().getDouble(CONFIG_ROOT + ".layout.button-forward-offset", 0.7D);

        boolean useAreaLayout = plugin.getConfig().getBoolean(CONFIG_AREA + ".enabled", true);
        double topY = plugin.getConfig().getDouble(CONFIG_AREA + ".max.y", DEFAULT_AREA_MAX_Y);
        double bottomY = plugin.getConfig().getDouble(CONFIG_AREA + ".min.y", DEFAULT_AREA_MIN_Y);

        Location titleLoc = useAreaLayout
                ? center.clone().add(0.0D, topY - center.getY() + 0.25D, 0.0D).add(forward.clone().multiply(textForwardOffset))
                : center.clone().add(0.0D, titleYOffset, 0.0D).add(forward.clone().multiply(textForwardOffset));
        spawnStaticText(world, titleLoc, ChatColor.GOLD + "" + ChatColor.BOLD + "大厅设置面板", 0.0F, 1.6F);

        PanelSetting[] settings = PanelSetting.values();
        int total = settings.length;
        for (int idx = 0; idx < total; idx++) {
            PanelSetting setting = settings[idx];
            double y = useAreaLayout
                    ? interpolateY(topY, bottomY, idx, total)
                    : center.getY() + firstRowYOffset - idx * rowGap;
            Location rowCenter = center.clone();
            rowCenter.setY(y);

            Location valueLoc = rowCenter.clone().add(forward.clone().multiply(textForwardOffset));
            TextDisplay valueText = spawnStaticText(world, valueLoc, buildSettingLine(setting), 0.0F, 1.35F);
            valueDisplays.put(setting, valueText);

            Location minusLoc = rowCenter.clone()
                    .add(right.clone().multiply(-buttonHorizontalOffset))
                    .add(forward.clone().multiply(buttonForwardOffset));
            spawnButton(world, minusLoc, setting, "minus", ChatColor.RED + "[-]");

            Location plusLoc = rowCenter.clone()
                    .add(right.clone().multiply(buttonHorizontalOffset))
                    .add(forward.clone().multiply(buttonForwardOffset));
            spawnButton(world, plusLoc, setting, "plus", ChatColor.GREEN + "[+]");
        }
    }

    public void cleanupPanelEntities() {
        Location center = getPanelCenterLocation();
        World targetWorld = center == null ? null : center.getWorld();
        double minX = plugin.getConfig().getDouble(CONFIG_AREA + ".min.x", DEFAULT_AREA_MIN_X) - 4.0D;
        double minY = plugin.getConfig().getDouble(CONFIG_AREA + ".min.y", DEFAULT_AREA_MIN_Y) - 4.0D;
        double minZ = plugin.getConfig().getDouble(CONFIG_AREA + ".min.z", DEFAULT_AREA_MIN_Z) - 4.0D;
        double maxX = plugin.getConfig().getDouble(CONFIG_AREA + ".max.x", DEFAULT_AREA_MAX_X) + 4.0D;
        double maxY = plugin.getConfig().getDouble(CONFIG_AREA + ".max.y", DEFAULT_AREA_MAX_Y) + 4.0D;
        double maxZ = plugin.getConfig().getDouble(CONFIG_AREA + ".max.z", DEFAULT_AREA_MAX_Z) + 4.0D;

        for (World world : plugin.getServer().getWorlds()) {
            if (targetWorld != null && !targetWorld.equals(world)) {
                continue;
            }
            for (Entity entity : world.getEntities()) {
                String marker = entity.getPersistentDataContainer().get(panelMarkerKey, PersistentDataType.STRING);
                if ("1".equals(marker)) {
                    entity.remove();
                    continue;
                }
                if (!isLikelyPanelOrphan(entity, minX, minY, minZ, maxX, maxY, maxZ)) {
                    continue;
                }
                entity.remove();
            }
        }
    }

    public boolean isPanelInteraction(Entity entity) {
        if (entity == null || entity.getType() != EntityType.INTERACTION) {
            return false;
        }
        String marker = entity.getPersistentDataContainer().get(panelMarkerKey, PersistentDataType.STRING);
        return "1".equals(marker);
    }

    public boolean canAdjustNow() {
        boolean allowRunning = plugin.getConfig().getBoolean(CONFIG_ROOT + ".allow-adjust-when-running", false);
        return allowRunning || !gameManager.isRunning();
    }

    public String adjustByEntity(Entity entity) {
        if (!isPanelInteraction(entity)) {
            return null;
        }
        String action = entity.getPersistentDataContainer().get(panelActionKey, PersistentDataType.STRING);
        String settingKey = entity.getPersistentDataContainer().get(panelSettingKey, PersistentDataType.STRING);
        if (settingKey == null || settingKey.isBlank()) {
            return null;
        }
        Optional<PanelSetting> settingOpt = PanelSetting.fromKey(settingKey);
        if (settingOpt.isEmpty()) {
            return null;
        }
        int direction = "minus".equalsIgnoreCase(action) ? -1 : 1;
        return adjustSetting(settingOpt.get(), direction);
    }

    public String adjustSetting(PanelSetting setting, int direction) {
        if (direction == 0) {
            return null;
        }
        int current = getDisplayValue(setting);
        int target = clamp(current + setting.step * Integer.signum(direction), setting.min, setting.max);
        if (target == current) {
            return setting.label + " 已达到边界值: " + formatValue(setting, current);
        }

        applyDisplayValue(setting, target);
        plugin.saveConfig();
        refreshPanel();

        return setting.label + " -> " + formatValue(setting, target);
    }

    public void refreshPanel() {
        if (valueDisplays.isEmpty()) {
            return;
        }
        List<PanelSetting> missing = new ArrayList<>();
        for (Map.Entry<PanelSetting, TextDisplay> entry : valueDisplays.entrySet()) {
            TextDisplay display = entry.getValue();
            if (display == null || !display.isValid()) {
                missing.add(entry.getKey());
                continue;
            }
            display.text(net.kyori.adventure.text.Component.text(buildSettingLine(entry.getKey())));
        }
        if (!missing.isEmpty()) {
            rebuildPanel();
        }
    }

    private TextDisplay spawnStaticText(World world, Location loc, String text, float shadowRadius, float scale) {
        TextDisplay display = (TextDisplay) world.spawnEntity(loc, EntityType.TEXT_DISPLAY);
        display.text(net.kyori.adventure.text.Component.text(text));
        display.setBillboard(Display.Billboard.CENTER);
        display.setSeeThrough(false);
        display.setShadowRadius(shadowRadius);
        display.setPersistent(true);
        display.setTransformation(display.getTransformation());
        display.getPersistentDataContainer().set(panelMarkerKey, PersistentDataType.STRING, "1");
        display.setViewRange(24.0F);
        display.setLineWidth(220);
        if (scale != 1.0F) {
            var transform = display.getTransformation();
            transform.getScale().set(scale, scale, scale);
            display.setTransformation(transform);
        }
        return display;
    }

    private void spawnButton(World world, Location loc, PanelSetting setting, String action, String text) {
        spawnStaticText(world, loc.clone().add(0.0D, 0.15D, 0.0D), text, 0.0F, 1.2F);
        Interaction interaction = (Interaction) world.spawnEntity(loc, EntityType.INTERACTION);
        interaction.setResponsive(true);
        interaction.setInteractionWidth(0.8F);
        interaction.setInteractionHeight(0.8F);
        interaction.setPersistent(true);
        interaction.getPersistentDataContainer().set(panelMarkerKey, PersistentDataType.STRING, "1");
        interaction.getPersistentDataContainer().set(panelActionKey, PersistentDataType.STRING, action);
        interaction.getPersistentDataContainer().set(panelSettingKey, PersistentDataType.STRING, setting.key);
    }

    private Location getPanelCenterLocation() {
        if (plugin.getConfig().getBoolean(CONFIG_AREA + ".enabled", true)) {
            String worldName = plugin.getConfig().getString(CONFIG_AREA + ".world", DEFAULT_WORLD);
            World world = worldName == null || worldName.isBlank()
                    ? spawnManager.getLobbyWorld()
                    : plugin.getServer().getWorld(worldName);
            if (world == null) {
                world = spawnManager.getLobbyWorld();
            }
            if (world == null) {
                return null;
            }
            double minX = plugin.getConfig().getDouble(CONFIG_AREA + ".min.x", DEFAULT_AREA_MIN_X);
            double minY = plugin.getConfig().getDouble(CONFIG_AREA + ".min.y", DEFAULT_AREA_MIN_Y);
            double minZ = plugin.getConfig().getDouble(CONFIG_AREA + ".min.z", DEFAULT_AREA_MIN_Z);
            double maxX = plugin.getConfig().getDouble(CONFIG_AREA + ".max.x", DEFAULT_AREA_MAX_X);
            double maxY = plugin.getConfig().getDouble(CONFIG_AREA + ".max.y", DEFAULT_AREA_MAX_Y);
            double maxZ = plugin.getConfig().getDouble(CONFIG_AREA + ".max.z", DEFAULT_AREA_MAX_Z);
            return new Location(
                    world,
                    (minX + maxX) / 2.0D,
                    (minY + maxY) / 2.0D,
                    (minZ + maxZ) / 2.0D
            );
        }

        String worldName = plugin.getConfig().getString(CONFIG_ANCHOR + ".world", "");
        World world = worldName == null || worldName.isBlank()
                ? spawnManager.getLobbyWorld()
                : plugin.getServer().getWorld(worldName);
        if (world == null) {
            world = spawnManager.getLobbyWorld();
        }
        if (world == null) {
            return null;
        }
        double x = plugin.getConfig().getDouble(CONFIG_ANCHOR + ".x", world.getSpawnLocation().getX());
        double y = plugin.getConfig().getDouble(CONFIG_ANCHOR + ".y", world.getSpawnLocation().getY());
        double z = plugin.getConfig().getDouble(CONFIG_ANCHOR + ".z", world.getSpawnLocation().getZ());
        float yaw = (float) plugin.getConfig().getDouble(CONFIG_ANCHOR + ".yaw", 0.0D);
        float pitch = (float) plugin.getConfig().getDouble(CONFIG_ANCHOR + ".pitch", 0.0D);
        return new Location(world, x, y, z, yaw, pitch);
    }

    private Vector resolveFacingForward(Location center) {
        if (plugin.getConfig().isConfigurationSection(CONFIG_FACE_TARGET)) {
            String worldName = plugin.getConfig().getString(CONFIG_FACE_TARGET + ".world", DEFAULT_WORLD);
            World targetWorld = worldName == null || worldName.isBlank()
                    ? center.getWorld()
                    : plugin.getServer().getWorld(worldName);
            if (targetWorld != null && center.getWorld() != null && center.getWorld().equals(targetWorld)) {
                double tx = plugin.getConfig().getDouble(CONFIG_FACE_TARGET + ".x", DEFAULT_FACE_X);
                double tz = plugin.getConfig().getDouble(CONFIG_FACE_TARGET + ".z", DEFAULT_FACE_Z);
                Vector toward = new Vector(tx - center.getX(), 0.0D, tz - center.getZ());
                if (toward.lengthSquared() > 1.0E-6) {
                    return toward.normalize();
                }
            }
        }
        Vector towardDefaultFace = new Vector(DEFAULT_FACE_X - center.getX(), 0.0D, DEFAULT_FACE_Z - center.getZ());
        if (towardDefaultFace.lengthSquared() > 1.0E-6) {
            return towardDefaultFace.normalize();
        }
        return yawToForward(center.getYaw());
    }

    private double interpolateY(double topY, double bottomY, int index, int total) {
        if (total <= 1) {
            return (topY + bottomY) / 2.0D;
        }
        double ratio = (index + 1.0D) / (total + 1.0D);
        return topY - (topY - bottomY) * ratio;
    }

    private Vector yawToForward(float yaw) {
        double radians = Math.toRadians(yaw);
        double x = -Math.sin(radians);
        double z = Math.cos(radians);
        Vector forward = new Vector(x, 0.0D, z);
        return forward.lengthSquared() <= 1.0E-6 ? new Vector(0.0D, 0.0D, 1.0D) : forward.normalize();
    }

    private String buildSettingLine(PanelSetting setting) {
        return ChatColor.AQUA + setting.label + ChatColor.GRAY + ": " + ChatColor.WHITE + formatValue(setting, getDisplayValue(setting));
    }

    private String formatValue(PanelSetting setting, int displayValue) {
        return switch (setting) {
            case ROUND_DURATION_MINUTES -> displayValue == 0 ? "不限时" : displayValue + " 分钟";
            case REVEAL_INTERVAL_MINUTES -> displayValue + " 分钟";
            case BORDER_START_SIZE, BORDER_END_SIZE -> displayValue + " 格";
            default -> Integer.toString(displayValue);
        };
    }

    private int getDisplayValue(PanelSetting setting) {
        return switch (setting) {
            case INITIAL_LIVES -> gameManager.getRoundInitialLives();
            case ROUND_DURATION_MINUTES -> (int) (gameManager.getRoundDurationSeconds() / 60L);
            case REVEAL_INTERVAL_MINUTES -> (int) (gameManager.getRoundRevealIntervalSeconds() / 60L);
            case BORDER_START_SIZE -> (int) Math.round(plugin.getConfig().getDouble("world-border.start-size", 700.0D));
            case BORDER_END_SIZE -> (int) Math.round(plugin.getConfig().getDouble("world-border.end-size", 120.0D));
            case TASK_FAILURE_PENALTY -> Math.max(0, plugin.getConfig().getInt("tasks.failure-point-penalty", 4));
        };
    }

    private void applyDisplayValue(PanelSetting setting, int displayValue) {
        switch (setting) {
            case INITIAL_LIVES -> {
                plugin.getConfig().set("tasks.lives-per-player", displayValue);
                gameManager.setLobbyInitialLivesOverride(displayValue);
            }
            case ROUND_DURATION_MINUTES -> {
                long seconds = displayValue * 60L;
                plugin.getConfig().set("game.round-duration-seconds", seconds);
                gameManager.setLobbyRoundDurationSecondsOverride(seconds);
            }
            case REVEAL_INTERVAL_MINUTES -> {
                long seconds = displayValue * 60L;
                plugin.getConfig().set("game.reveal-interval-seconds", seconds);
                gameManager.setLobbyRevealIntervalSecondsOverride(seconds);
            }
            case BORDER_START_SIZE -> {
                double startSize = displayValue;
                double endSize = plugin.getConfig().getDouble("world-border.end-size", 120.0D);
                if (endSize >= startSize) {
                    endSize = Math.max(10.0D, startSize - 10.0D);
                    plugin.getConfig().set("world-border.end-size", endSize);
                }
                plugin.getConfig().set("world-border.start-size", startSize);
            }
            case BORDER_END_SIZE -> {
                double endSize = displayValue;
                double startSize = plugin.getConfig().getDouble("world-border.start-size", 700.0D);
                if (endSize >= startSize) {
                    endSize = Math.max(10.0D, startSize - 10.0D);
                }
                plugin.getConfig().set("world-border.end-size", endSize);
            }
            case TASK_FAILURE_PENALTY -> plugin.getConfig().set("tasks.failure-point-penalty", displayValue);
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private boolean isLikelyPanelOrphan(Entity entity, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (entity == null) {
            return false;
        }
        if (entity.getType() != EntityType.TEXT_DISPLAY && entity.getType() != EntityType.INTERACTION) {
            return false;
        }
        Location loc = entity.getLocation();
        if (loc.getX() < minX || loc.getX() > maxX || loc.getY() < minY || loc.getY() > maxY || loc.getZ() < minZ || loc.getZ() > maxZ) {
            return false;
        }
        if (entity.getPersistentDataContainer().has(panelActionKey, PersistentDataType.STRING)
                || entity.getPersistentDataContainer().has(panelSettingKey, PersistentDataType.STRING)) {
            return true;
        }
        if (entity instanceof TextDisplay textDisplay) {
            String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(textDisplay.text() == null ? net.kyori.adventure.text.Component.empty() : textDisplay.text());
            for (String keyword : PANEL_LABEL_KEYWORDS) {
                if (plain.contains(keyword)) {
                    return true;
                }
            }
        }
        return false;
    }

    private enum PanelSetting {
        INITIAL_LIVES("initial_lives", "初始生命值", 1, 20, 1),
        ROUND_DURATION_MINUTES("round_duration_minutes", "对局总时长", 0, 180, 1),
        REVEAL_INTERVAL_MINUTES("reveal_interval_minutes", "规则揭示间隔", 1, 180, 1),
        BORDER_START_SIZE("border_start_size", "边界初始大小", 20, 3000, 10),
        BORDER_END_SIZE("border_end_size", "边界最终大小", 10, 3000, 10),
        TASK_FAILURE_PENALTY("task_failure_penalty", "任务失败扣分", 0, 50, 1);

        private final String key;
        private final String label;
        private final int min;
        private final int max;
        private final int step;

        PanelSetting(String key, String label, int min, int max, int step) {
            this.key = key;
            this.label = label;
            this.min = min;
            this.max = max;
            this.step = step;
        }

        private static Optional<PanelSetting> fromKey(String key) {
            String normalized = key == null ? "" : key.toLowerCase(Locale.ROOT);
            for (PanelSetting value : values()) {
                if (value.key.equals(normalized)) {
                    return Optional.of(value);
                }
            }
            return Optional.empty();
        }
    }
}
