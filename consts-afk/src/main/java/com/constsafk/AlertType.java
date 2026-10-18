package com.constsafk;

import java.awt.Color;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Every alert the plugin can raise, in status panel order. The ordinal doubles as the flash phase slot so
 * overlapping alerts strobe out of step instead of blending into one color.
 */
enum AlertType
{
    HEALTH("Health", "Current/max Hitpoints. Alerts below the threshold.",
        ConstsAfkConfig::enableHealthAlert, ConstsAfkConfig::healthFlashColor),
    PRAYER("Prayer", "Current/max Prayer. Alerts below the threshold.",
        ConstsAfkConfig::enablePrayerAlert, ConstsAfkConfig::prayerFlashColor),
    IDLE("Idle", "Time since last activity (movement, animation, combat, XP).",
        ConstsAfkConfig::enableIdleAlert, ConstsAfkConfig::idleFlashColor),
    AGGRO("Aggro", "Time until NPC aggression wears off. Right-click the panel to reset.",
        ConstsAfkConfig::enableTimerAlert, ConstsAfkConfig::timerFlashColor),
    LOGOUT("Logout", "Time until the 25-minute inactivity logout. Clicks and keys reset it.",
        ConstsAfkConfig::enableLogoutWarning, ConstsAfkConfig::logoutWarningFlashColor),
    POTION("Boost", "Lights up when a potion or effect is about to expire.",
        ConstsAfkConfig::enablePotionExpiryAlert, ConstsAfkConfig::potionExpiryFlashColor),
    RANDOM("Random", "Lights up when a random event NPC appears.",
        ConstsAfkConfig::enableRandomEventAlert, ConstsAfkConfig::randomEventFlashColor),
    CANNON("Cannon", "Lights up when your cannon is out of ammo.",
        ConstsAfkConfig::enableCannonAlert, ConstsAfkConfig::cannonFlashColor),
    INVENTORY_FULL("Inv Full", "Lights up when your inventory is too full to hold more.",
        ConstsAfkConfig::enableInventoryFullAlert, ConstsAfkConfig::inventoryFullFlashColor),
    SALVAGE("Salvage", "Lights up when a shipwreck is depleted or you run out of salvage to sort.",
        ConstsAfkConfig::enableSailingAlert, ConstsAfkConfig::sailingFlashColor),
    CARGO("Cargo", "Lights up when your crew can't salvage because the cargo hold is full.",
        ConstsAfkConfig::enableCargoHoldAlert, ConstsAfkConfig::cargoHoldFlashColor),
    FORESTRY("Forestry", "Lights up when a Forestry event you picked starts nearby, and names it.",
        ConstsAfkConfig::enableForestryAlert, ConstsAfkConfig::forestryFlashColor);

    private static final int PHASE_SLOTS = values().length;

    private final String label;
    private final String description;
    private final Predicate<ConstsAfkConfig> toggle;
    private final Function<ConstsAfkConfig, Color> flashColor;

    AlertType(String label, String description, Predicate<ConstsAfkConfig> toggle, Function<ConstsAfkConfig, Color> flashColor)
    {
        this.label = label;
        this.description = description;
        this.toggle = toggle;
        this.flashColor = flashColor;
    }

    String getLabel()
    {
        return label;
    }

    String getDescription()
    {
        return description;
    }

    /** The alert's own Enable checkbox, which only applies under the Custom preset. */
    boolean isToggledOn(ConstsAfkConfig config)
    {
        return toggle.test(config);
    }

    Color getFlashColor(ConstsAfkConfig config)
    {
        return flashColor.apply(config);
    }

    /** True during the bright half of this alert's flash cycle. */
    boolean isFlashPhaseOn(long nowMillis, long intervalMillis)
    {
        long interval = Math.max(1L, intervalMillis);
        long offset = ordinal() * Math.max(1L, interval / PHASE_SLOTS);
        return ((nowMillis + offset) % (interval * 2L)) < interval;
    }
}
