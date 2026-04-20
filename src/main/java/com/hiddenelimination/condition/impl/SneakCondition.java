package com.hiddenelimination.condition.impl;

import com.hiddenelimination.condition.EliminationCondition;

public final class SneakCondition implements EliminationCondition {

    @Override
    public String getId() {
        return "sneak";
    }

    @Override
    public String getDisplayName() {
        return "潜行";
    }
}
