package com.hiddenelimination.condition.impl;

import com.hiddenelimination.condition.EliminationCondition;

public final class ConsumeItemCondition implements EliminationCondition {

    @Override
    public String getId() {
        return "consume_item";
    }

    @Override
    public String getDisplayName() {
        return "食用物品";
    }
}
