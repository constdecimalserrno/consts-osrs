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
    CHINNING("Chinning",
        AlertType.HEALTH, AlertType.PRAYER, AlertType.IDLE, AlertType.AGGRO,
        AlertType.LOGOUT, AlertType.POTION, AlertType.RANDOM),
    // Crabs stop attacking after ten minutes, so Aggro is the one that matters. No Prayer: crabbing doesn't use it.
    CRABBING("Crabbing",
        AlertType.HEALTH, AlertType.IDLE, AlertType.AGGRO, AlertType.LOGOUT, AlertType.POTION, AlertType.RANDOM),
    MINING("Mining",
        AlertType.IDLE, AlertType.LOGOUT, AlertType.RANDOM, AlertType.INVENTORY_FULL),
    WOODCUTTING("Woodcutting",
        AlertType.IDLE, AlertType.LOGOUT, AlertType.RANDOM, AlertType.INVENTORY_FULL, AlertType.FORESTRY),
    FISHING("Fishing",
        AlertType.IDLE, AlertType.LOGOUT, AlertType.RANDOM, AlertType.INVENTORY_FULL),
    // On a hook yourself: the wreck running dry, a full inventory and a full hold all need you.
    SALVAGING_ACTIVE("Salvaging Active",
        AlertType.LOGOUT, AlertType.RANDOM, AlertType.INVENTORY_FULL, AlertType.SALVAGE, AlertType.CARGO),
    // Crewmates on the hooks: the only thing that needs you is a full cargo hold.
    SALVAGING_AFK("Salvaging AFK",
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
