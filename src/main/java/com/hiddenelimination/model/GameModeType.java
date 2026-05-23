package com.hiddenelimination.model;

public enum GameModeType {
    FREE_FOR_ALL("个人混战"),
    TEAM("团队对抗");

    private final String displayName;

    GameModeType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static GameModeType fromConfig(String key) {
        if ("team".equalsIgnoreCase(key)) {
            return TEAM;
        }
        return FREE_FOR_ALL;
    }
}
