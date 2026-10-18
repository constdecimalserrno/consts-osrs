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
        AlertType.HEALTH, AlertType.PRAYER, AlertType.POTION, AlertType.AGGRO, AlertType.LOGOUT),
    // Crabs stop attacking after ten minutes, so Aggro is the one that matters. No Prayer: crabbing doesn't use it.
    CRABBING("Crabbing",
        AlertType.HEALTH, AlertType.IDLE, AlertType.AGGRO, AlertType.LOGOUT, AlertType.POTION),
    MINING("Mining",
        AlertType.IDLE, AlertType.INVENTORY_FULL),
    WOODCUTTING("Woodcutting",
        AlertType.IDLE, AlertType.INVENTORY_FULL, AlertType.FORESTRY),
    FISHING("Fishing",
        AlertType.IDLE, AlertType.INVENTORY_FULL),
    // On a hook yourself: a full inventory sends you sorting, and Salvage says the sorting or the wreck is done.
    SALVAGING_ACTIVE("Salvaging Active",
        AlertType.INVENTORY_FULL, AlertType.SALVAGE),
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

    /** Every preset is for AFK play, so Idle waits for the animation to stop; Custom asks the Idle section. */
    boolean holdsIdleWhileAnimating(ConstsAfkConfig config)
    {
        return this != CUSTOM || config.idleRequireNoAnimation();
    }

    @Override
    public String toString()
    {
        return label;
    }
}
