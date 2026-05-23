package com.hiddenelimination.model;

/**
 * 支持的淘汰条件类型。
 */
public enum ConditionType {
    JUMP("跳跃"),
    EAT_FOOD("进食"),
    ATTACK_PLAYER("攻击玩家"),
    BREAK_BLOCK("破坏方块"),
    PLACE_BLOCK("放置方块"),
    SPRINT("开始冲刺"),
    SNEAK("开始潜行"),
    DROP_ITEM("丢弃物品"),
    ENTER_WATER("进入水中"),
    USE_CRAFTING_TABLE("打开工作台"),
    USE_FURNACE("打开熔炉"),
    EQUIP_ARMOR("穿上护甲"),
    TAKE_DAMAGE("受到任意伤害"),
    PICKUP_ITEM("捡起物品"),

    STAND_ON_GRASS_BLOCK("站在草方块上"),
    ATTACK_MOB("攻击生物"),
    HOLD_ANY_ITEM("手持任意物品"),
    STOP_MOVING("停止移动"),
    NOT_SNEAKING("不潜行"),
    TOUCH_PLAYER("与其他玩家贴贴"),
    DIE("死亡"),
    STAND_ON_STONE("站在石头上"),
    NOT_ON_GRASS_BLOCK("没有站在草方块上"),
    NOT_ON_STONE("没有站在石头上"),
    NOT_ON_FARMLAND("没有站在耕地上"),
    BLOCK_OVERHEAD("头顶有方块遮挡"),
    NO_BLOCK_OVERHEAD("头顶无方块遮挡"),
    HAS_WEAPON("背包中有武器"),
    HAS_FOOD("背包中有食物"),
    HAS_ORE("背包中有矿物"),
    HAS_TOOL("背包中有工具"),
    CRAFT_ITEM("合成物品");

    private final String displayName;

    ConditionType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isStatePolled() {
        return switch (this) {
            case STAND_ON_GRASS_BLOCK, STAND_ON_STONE, NOT_ON_GRASS_BLOCK, NOT_ON_STONE, NOT_ON_FARMLAND,
                 BLOCK_OVERHEAD, NO_BLOCK_OVERHEAD, HOLD_ANY_ITEM, HAS_WEAPON, HAS_FOOD, HAS_ORE, HAS_TOOL,
                 STOP_MOVING, NOT_SNEAKING -> true;
            default -> false;
        };
    }
}
