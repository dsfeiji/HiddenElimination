package com.hiddenelimination.condition.impl;

import com.hiddenelimination.condition.EliminationCondition;

public final class JumpCondition implements EliminationCondition {

    @Override
    public String getId() {
        return "jump";
    }

    @Override
    public String getDisplayName() {
        return "跳跃";
    }
}
