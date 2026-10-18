package com.constsafk;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Named bundles of alerts, picked from the top of the config panel or by right-clicking the status panel.
 * A preset overrides the per-alert Enable toggles; Custom hands control back to them.
 */
public enum Preset
{
    CUSTOM("Custom"),
    COMBAT("Combat",
        AlertType.HEALTH, AlertType.PRAYER, AlertType.IDLE, AlertType.AGGRO,
        AlertType.LOGOUT, AlertType.POTION, AlertType.RANDOM, AlertType.CANNON),
    SKILLING("Skilling",
        AlertType.IDLE, AlertType.LOGOUT, AlertType.RANDOM, AlertType.INVENTORY_FULL),
    WOODCUTTING("Woodcutting",
        AlertType.IDLE, AlertType.LOGOUT, AlertType.RANDOM, AlertType.INVENTORY_FULL, AlertType.FORESTRY),
    SAILING("Sailing",
        AlertType.LOGOUT, AlertType.RANDOM, AlertType.INVENTORY_FULL, AlertType.SALVAGE, AlertType.CARGO),
    SAILING_AFK("Sailing AFK",
        AlertType.CARGO);

    private final String label;
    private final Set<AlertType> alerts;

    Preset(String label, AlertType... alerts)
    {
        this.label = label;
        Set<AlertType> set = EnumSet.noneOf(AlertType.class);
        Collections.addAll(set, alerts);
        this.alerts = Collections.unmodifiableSet(set);
    }

    boolean enables(AlertType type, ConstsAfkConfig config)
    {
        return this == CUSTOM ? type.isToggledOn(config) : alerts.contains(type);
    }

    @Override
    public String toString()
    {
        return label;
    }
}
