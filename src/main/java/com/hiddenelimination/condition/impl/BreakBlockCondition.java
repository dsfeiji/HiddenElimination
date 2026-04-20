package com.hiddenelimination.condition.impl;

import com.hiddenelimination.condition.EliminationCondition;

public final class BreakBlockCondition implements EliminationCondition {

    @Override
    public String getId() {
        return "break_block";
    }

    @Override
    public String getDisplayName() {
        return "破坏方块";
    }
}
