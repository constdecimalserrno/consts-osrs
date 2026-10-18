package com.constsafk;

public enum TimerMode
{
    CUSTOM_MINUTES("Custom Minutes"),
    OSRS_AGGRO_10_MIN("OSRS Aggro (10 min)");

    private final String label;

    TimerMode(String label)
    {
        this.label = label;
    }

    @Override
    public String toString()
    {
        return label;
    }
}
