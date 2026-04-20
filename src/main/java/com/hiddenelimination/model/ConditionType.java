package com.hiddenelimination.model;

/**
 * 支持的淘汰条件类型。
 */
public enum ConditionType {
    JUMP("跳跃"),
    OPEN_INVENTORY("打开背包"),
    EAT_FOOD("进食"),
    ATTACK_PLAYER("攻击玩家"),
    BREAK_BLOCK("破坏方块"),
    PLACE_BLOCK("放置方块"),
    SPRINT("开始冲刺"),
    SNEAK("开始潜行"),
    DROP_ITEM("丢弃物品"),
    OPEN_CHEST("打开箱子"),
    ENTER_WATER("进入水中"),
    USE_CRAFTING_TABLE("打开工作台"),
    USE_FURNACE("打开熔炉"),
    EQUIP_ARMOR("穿上护甲"),
    TAKE_DAMAGE("受到任意伤害"),
    TAKE_FALL_DAMAGE("受到摔落伤害"),
    PICKUP_ITEM("捡起物品");

    private final String displayName;

    ConditionType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
