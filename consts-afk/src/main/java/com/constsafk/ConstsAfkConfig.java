package com.constsafk;

import java.awt.Color;
import net.runelite.api.SoundEffectID;
import net.runelite.api.SoundEffectVolume;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

/**
 * Six sections: General, then one per kind of play. Every alert has the same block, in the same order: its on/off
 * (which only the Custom preset reads), Dismiss on Click, Color, then whatever is its own. The key names predate
 * this layout and stay, so saved settings carry over.
 */
@ConfigGroup(ConstsAfkConfig.GROUP)
public interface ConstsAfkConfig extends Config
{
    // Group name from before the consts-AFK rename, kept so saved settings carry over.
    String GROUP = "afkcombatalerts";
    String PRESET_KEY = "preset";
    String ALERT_SOUND_KEY = "alertSound";
    String CUSTOM_SOUND_KEY = "customSoundId";
    String SOUND_VOLUME_KEY = "soundVolume";

    @ConfigItem(
        keyName = PRESET_KEY,
        name = "Preset",
        description = "Which alerts are on. Custom uses each alert's own on/off; every other preset overrides them.",
        position = 0
    )
    default Preset preset()
    {
        return Preset.CUSTOM;
    }

    @ConfigSection(
        name = "General",
        description = "Clicks, sounds, screen flash and the status panel",
        position = 1
    )
    String generalSection = "general";

    @ConfigSection(
        name = "Combat",
        description = "Health, Prayer, Aggro, Potion and Cannon",
        position = 2
    )
    String combatSection = "combat";

    @ConfigSection(
        name = "AFK",
        description = "Idle, Logout and Inventory Full",
        position = 3
    )
    String afkSection = "afk";

    @ConfigSection(
        name = "Random Event",
        description = "Your own random events",
        position = 4
    )
    String randomSection = "random";

    @ConfigSection(
        name = "Forestry",
        description = "Forestry events, the ones you pick",
        position = 5
    )
    String forestrySection = "forestry";

    @ConfigSection(
        name = "Sailing",
        description = "Shipwreck salvage and the cargo hold",
        position = 6
    )
    String sailingSection = "sailing";

    // General

    @Range(min = 0, max = 60_000)
    @Units(Units.MILLISECONDS)
    @ConfigItem(
        keyName = "interactionPauseMs",
        name = "Click Pause",
        description = "Silence alerts for this long after each click or key press, except alerts with Dismiss on Click off",
        section = generalSection,
        position = 0
    )
    default int interactionPauseMs()
    {
        return 3000;
    }

    @Range(min = 0, max = 900)
    @Units(Units.SECONDS)
    @ConfigItem(
        keyName = "quietAfterDismissSeconds",
        name = "Quiet After Dismiss",
        description = "A one-off alert you click away stays quiet this long even if its message comes again, so you can sort or bank in peace",
        section = generalSection,
        position = 1
    )
    default int quietAfterDismissSeconds()
    {
        return 60;
    }

    @Range(min = 5, max = 1800)
    @Units(Units.SECONDS)
    @ConfigItem(
        keyName = "giveUpAfterSeconds",
        name = "Give Up After",
        description = "A one-off alert nobody clicks away stops after this long",
        section = generalSection,
        position = 2
    )
    default int giveUpAfterSeconds()
    {
        return 120;
    }

    @Range(min = 0, max = 120_000)
    @Units(Units.MILLISECONDS)
    @ConfigItem(
        keyName = "startupDelayMs",
        name = "Startup Delay",
        description = "Wait this long after the plugin starts before any alert can fire",
        section = generalSection,
        position = 3
    )
    default int startupDelayMs()
    {
        return 8000;
    }

    @ConfigItem(
        keyName = "enableAllSoundAlerts",
        name = "Sounds",
        description = "Play a sound while an alert is firing",
        section = generalSection,
        position = 4
    )
    default boolean enableAllSoundAlerts()
    {
        return true;
    }

    @ConfigItem(
        keyName = ALERT_SOUND_KEY,
        name = "Alert Sound",
        description = "What alerts sound like: the system beep, or one of the game's own sounds. Picking one plays it once.",
        section = generalSection,
        position = 5
    )
    default AlertSound alertSound()
    {
        return AlertSound.SYSTEM_BEEP;
    }

    @Range(min = -1, max = 100_000)
    @ConfigItem(
        keyName = CUSTOM_SOUND_KEY,
        name = "Custom Sound ID",
        description = "The game sound effect to play when Alert Sound is Custom sound ID (the OSRS Wiki lists them)",
        section = generalSection,
        position = 6
    )
    default int customSoundId()
    {
        return SoundEffectID.TOWN_CRIER_BELL_DING;
    }

    @Range(min = 1, max = SoundEffectVolume.HIGH)
    @ConfigItem(
        keyName = SOUND_VOLUME_KEY,
        name = "Game Sound Volume",
        description = "How loud a game sound plays (1-127), even with the game's own sound effects turned off",
        section = generalSection,
        position = 7
    )
    default int soundVolume()
    {
        return SoundEffectVolume.MEDIUM_HIGH;
    }

    @Range(min = 100, max = 60_000)
    @Units(Units.MILLISECONDS)
    @ConfigItem(
        keyName = "repeatIntervalMs",
        name = "Repeat Interval",
        description = "Time between sounds while an alert is firing",
        section = generalSection,
        position = 8
    )
    default int repeatIntervalMs()
    {
        return 1200;
    }

    @ConfigItem(
        keyName = "enableScreenFlash",
        name = "Screen Flash",
        description = "Flash the game screen while an alert is firing",
        section = generalSection,
        position = 9
    )
    default boolean enableScreenFlash()
    {
        return true;
    }

    @Range(min = 100, max = 2000)
    @Units(Units.MILLISECONDS)
    @ConfigItem(
        keyName = "flashIntervalMs",
        name = "Flash Interval",
        description = "How fast the screen flash toggles",
        section = generalSection,
        position = 10
    )
    default int flashIntervalMs()
    {
        return 300;
    }

    @Range(min = 10, max = 180)
    @ConfigItem(
        keyName = "flashOpacity",
        name = "Flash Opacity",
        description = "Screen flash opacity (kept translucent)",
        section = generalSection,
        position = 11
    )
    default int flashOpacity()
    {
        return 50;
    }

    @ConfigItem(
        keyName = "showAnimationId",
        name = "Show Animation ID",
        description = "Always show your current animation in the status panel; it shows anyway while Idle waits for it to stop",
        section = generalSection,
        position = 12
    )
    default boolean showAnimationId()
    {
        return false;
    }

    // Combat

    @ConfigItem(
        keyName = "enableHealthAlert",
        name = "Health Alert",
        description = "Alert while Hitpoints are below the threshold (Custom preset only)",
        section = combatSection,
        position = 0
    )
    default boolean enableHealthAlert()
    {
        return true;
    }

    @ConfigItem(
        keyName = "healthDismissOnClick",
        name = "Health: Dismiss on Click",
        description = "A click or key press silences this alert. Off, it keeps going through clicks",
        section = combatSection,
        position = 1
    )
    default boolean healthDismissOnClick()
    {
        return true;
    }

    @ConfigItem(
        keyName = "healthFlashColor",
        name = "Health: Color",
        description = "Screen flash and panel color for Health",
        section = combatSection,
        position = 2
    )
    default Color healthFlashColor()
    {
        return new Color(75, 205, 90);
    }

    @Range(min = 1, max = 99)
    @ConfigItem(
        keyName = "healthThreshold",
        name = "Health: Threshold",
        description = "Alert when Hitpoints are below this",
        section = combatSection,
        position = 3
    )
    default int healthThreshold()
    {
        return 25;
    }

    @ConfigItem(
        keyName = "enablePrayerAlert",
        name = "Prayer Alert",
        description = "Alert while Prayer is below the threshold (Custom preset only)",
        section = combatSection,
        position = 4
    )
    default boolean enablePrayerAlert()
    {
        return true;
    }

    @ConfigItem(
        keyName = "prayerDismissOnClick",
        name = "Prayer: Dismiss on Click",
        description = "A click or key press silences this alert. Off, it keeps going through clicks",
        section = combatSection,
        position = 5
    )
    default boolean prayerDismissOnClick()
    {
        return true;
    }

    @ConfigItem(
        keyName = "prayerFlashColor",
        name = "Prayer: Color",
        description = "Screen flash and panel color for Prayer",
        section = combatSection,
        position = 6
    )
    default Color prayerFlashColor()
    {
        return new Color(88, 190, 255);
    }

    @Range(min = 1, max = 99)
    @ConfigItem(
        keyName = "prayerThreshold",
        name = "Prayer: Threshold",
        description = "Alert when Prayer is below this",
        section = combatSection,
        position = 7
    )
    default int prayerThreshold()
    {
        return 20;
    }

    @ConfigItem(
        keyName = "enableTimerAlert",
        name = "Aggro Alert",
        description = "Alert when the aggro timer runs out (Custom preset only)",
        section = combatSection,
        position = 8
    )
    default boolean enableTimerAlert()
    {
        return true;
    }

    @ConfigItem(
        keyName = "aggroDismissOnClick",
        name = "Aggro: Dismiss on Click",
        description = "A click or key press silences this alert. Off, it keeps going through clicks",
        section = combatSection,
        position = 9
    )
    default boolean aggroDismissOnClick()
    {
        return true;
    }

    @ConfigItem(
        keyName = "timerFlashColor",
        name = "Aggro: Color",
        description = "Screen flash and panel color for Aggro",
        section = combatSection,
        position = 10
    )
    default Color timerFlashColor()
    {
        return new Color(210, 95, 95);
    }

    @ConfigItem(
        keyName = "timerMode",
        name = "Aggro: Mode",
        description = "OSRS 10-minute aggro timing, or a repeating custom interval",
        section = combatSection,
        position = 11
    )
    default TimerMode timerMode()
    {
        return TimerMode.OSRS_AGGRO_10_MIN;
    }

    @Range(min = 1, max = 180)
    @ConfigItem(
        keyName = "timerMinutes",
        name = "Aggro: Custom Minutes",
        description = "Minutes between alerts when Mode is Custom Minutes",
        section = combatSection,
        position = 12
    )
    default int timerMinutes()
    {
        return 10;
    }

    @Range(min = 5, max = 250)
    @ConfigItem(
        keyName = "aggroResetDistanceTiles",
        name = "Aggro: Reset Distance",
        description = "In OSRS mode, moving this many tiles from where the timer started restarts it",
        section = combatSection,
        position = 13
    )
    default int aggroResetDistanceTiles()
    {
        return 20;
    }

    @ConfigItem(
        keyName = "enablePotionExpiryAlert",
        name = "Potion Expiry Alert",
        description = "Alert when chat says a potion or effect is about to expire (Custom preset only)",
        section = combatSection,
        position = 14
    )
    default boolean enablePotionExpiryAlert()
    {
        return true;
    }

    @ConfigItem(
        keyName = "potionDismissOnClick",
        name = "Potion: Dismiss on Click",
        description = "A click or key press silences this alert. Off, it keeps going through clicks",
        section = combatSection,
        position = 15
    )
    default boolean potionDismissOnClick()
    {
        return true;
    }

    @ConfigItem(
        keyName = "potionExpiryFlashColor",
        name = "Potion: Color",
        description = "Screen flash and panel color for Potion",
        section = combatSection,
        position = 16
    )
    default Color potionExpiryFlashColor()
    {
        return new Color(0, 0, 0);
    }

    @ConfigItem(
        keyName = "enableCannonAlert",
        name = "Cannon Alert",
        description = "Alert when chat says 'Your cannon is out of ammo!' (Custom preset only)",
        section = combatSection,
        position = 17
    )
    default boolean enableCannonAlert()
    {
        return true;
    }

    @ConfigItem(
        keyName = "cannonDismissOnClick",
        name = "Cannon: Dismiss on Click",
        description = "A click or key press silences this alert. Off, it keeps going through clicks",
        section = combatSection,
        position = 18
    )
    default boolean cannonDismissOnClick()
    {
        return true;
    }

    @ConfigItem(
        keyName = "cannonFlashColor",
        name = "Cannon: Color",
        description = "Screen flash and panel color for Cannon",
        section = combatSection,
        position = 19
    )
    default Color cannonFlashColor()
    {
        return new Color(255, 140, 0);
    }

    // AFK

    @ConfigItem(
        keyName = "enableIdleAlert",
        name = "Idle Alert",
        description = "Alert when nothing has happened for the threshold (Custom preset only)",
        section = afkSection,
        position = 0
    )
    default boolean enableIdleAlert()
    {
        return true;
    }

    @ConfigItem(
        keyName = "idleDismissOnClick",
        name = "Idle: Dismiss on Click",
        description = "A click or key press silences this alert. Off, it keeps going through clicks",
        section = afkSection,
        position = 1
    )
    default boolean idleDismissOnClick()
    {
        return true;
    }

    @ConfigItem(
        keyName = "idleFlashColor",
        name = "Idle: Color",
        description = "Screen flash and panel color for Idle",
        section = afkSection,
        position = 2
    )
    default Color idleFlashColor()
    {
        return new Color(255, 210, 80);
    }

    @Range(min = 1000, max = 600_000)
    @Units(Units.MILLISECONDS)
    @ConfigItem(
        keyName = "idleThresholdMs",
        name = "Idle: Threshold",
        description = "Alert after this much time without movement, new animations, combat or XP",
        section = afkSection,
        position = 3
    )
    default int idleThresholdMs()
    {
        return 6000;
    }

    @ConfigItem(
        keyName = "idleRequireNoAnimation",
        name = "Idle: Suppress While Animating",
        description = "Only alert once your character stops animating, so a repeating animation (chopping, mining, fishing, fighting) holds it off. Every preset but Custom always does this.",
        section = afkSection,
        position = 4
    )
    default boolean idleRequireNoAnimation()
    {
        return true;
    }

    @ConfigItem(
        keyName = "enableLogoutWarning",
        name = "Logout Alert",
        description = "Warn before the inactivity logout. Any click or key press restarts it (Custom preset only)",
        section = afkSection,
        position = 5
    )
    default boolean enableLogoutWarning()
    {
        return true;
    }

    @ConfigItem(
        keyName = "logoutWarningFlashColor",
        name = "Logout: Color",
        description = "Screen flash and panel color for Logout",
        section = afkSection,
        position = 6
    )
    default Color logoutWarningFlashColor()
    {
        return new Color(255, 171, 94);
    }

    @Range(min = 60_000, max = 1_800_000)
    @Units(Units.MILLISECONDS)
    @ConfigItem(
        keyName = "logoutWarningMs",
        name = "Logout: Warn After",
        description = "Warn after this long without a click or key press (24 minutes default)",
        section = afkSection,
        position = 7
    )
    default int logoutWarningMs()
    {
        return 1_440_000;
    }

    @ConfigItem(
        keyName = "enableInventoryFullAlert",
        name = "Inventory Full Alert",
        description = "Alert when chat says your inventory is too full to hold more (Custom preset only)",
        section = afkSection,
        position = 8
    )
    default boolean enableInventoryFullAlert()
    {
        return true;
    }

    @ConfigItem(
        keyName = "inventoryFullDismissOnClick",
        name = "Inventory Full: Dismiss on Click",
        description = "A click or key press silences this alert. Off, it keeps going through clicks",
        section = afkSection,
        position = 9
    )
    default boolean inventoryFullDismissOnClick()
    {
        return true;
    }

    @ConfigItem(
        keyName = "inventoryFullFlashColor",
        name = "Inventory Full: Color",
        description = "Screen flash and panel color for Inventory Full",
        section = afkSection,
        position = 10
    )
    default Color inventoryFullFlashColor()
    {
        return new Color(220, 60, 220);
    }

    // Random event

    @ConfigItem(
        keyName = "enableRandomEventAlert",
        name = "Random Event Alert",
        description = "Alert when a random event targets you, not someone else (Custom preset only)",
        section = randomSection,
        position = 0
    )
    default boolean enableRandomEventAlert()
    {
        return true;
    }

    @ConfigItem(
        keyName = "randomDismissOnClick",
        name = "Random Event: Dismiss on Click",
        description = "A click or key press silences this alert. Off, it keeps going through clicks",
        section = randomSection,
        position = 1
    )
    default boolean randomDismissOnClick()
    {
        return true;
    }

    @ConfigItem(
        keyName = "randomEventFlashColor",
        name = "Random Event: Color",
        description = "Screen flash and panel color for Random Event",
        section = randomSection,
        position = 2
    )
    default Color randomEventFlashColor()
    {
        return new Color(255, 255, 255);
    }

    @Range(min = 0, max = 120_000)
    @Units(Units.MILLISECONDS)
    @ConfigItem(
        keyName = "randomEventCooldownMs",
        name = "Random Event: Cooldown",
        description = "Minimum time between random event alerts",
        section = randomSection,
        position = 3
    )
    default int randomEventCooldownMs()
    {
        return 20_000;
    }

    // Forestry

    @ConfigItem(
        keyName = "enableForestryAlert",
        name = "Forestry Alert",
        description = "Alert when a Forestry event you picked below starts nearby (Custom preset only)",
        section = forestrySection,
        position = 0
    )
    default boolean enableForestryAlert()
    {
        return true;
    }

    @ConfigItem(
        keyName = "forestryDismissOnClick",
        name = "Forestry: Dismiss on Click",
        description = "A click or key press silences this alert. Off, it keeps going through clicks",
        section = forestrySection,
        position = 1
    )
    default boolean forestryDismissOnClick()
    {
        return true;
    }

    @ConfigItem(
        keyName = "forestryFlashColor",
        name = "Forestry: Color",
        description = "Screen flash and panel color for Forestry",
        section = forestrySection,
        position = 2
    )
    default Color forestryFlashColor()
    {
        return new Color(170, 240, 40);
    }

    @ConfigItem(
        keyName = "forestryRisingRoots",
        name = "Forestry: Rising Roots",
        description = "Alert when a Rising Roots event starts: anima-infused roots sprout around the tree",
        section = forestrySection,
        position = 3
    )
    default boolean forestryRisingRoots()
    {
        return true;
    }

    @ConfigItem(
        keyName = "forestryStrugglingSapling",
        name = "Forestry: Struggling Sapling",
        description = "Alert when a Struggling Sapling event starts: a sapling needs the right mulch",
        section = forestrySection,
        position = 4
    )
    default boolean forestryStrugglingSapling()
    {
        return true;
    }

    @ConfigItem(
        keyName = "forestryFloweringTree",
        name = "Forestry: Flowering Tree",
        description = "Alert when a Flowering Tree event starts: flowering bushes need pollinating",
        section = forestrySection,
        position = 5
    )
    default boolean forestryFloweringTree()
    {
        return true;
    }

    @ConfigItem(
        keyName = "forestryLeprechaun",
        name = "Forestry: Woodcutting Leprechaun",
        description = "Alert when a Woodcutting Leprechaun event starts: a leprechaun shows up by the trees",
        section = forestrySection,
        position = 6
    )
    default boolean forestryLeprechaun()
    {
        return true;
    }

    @ConfigItem(
        keyName = "forestryBeehive",
        name = "Forestry: Beehive",
        description = "Alert when a Beehive event starts: a swarm needs a hive built",
        section = forestrySection,
        position = 7
    )
    default boolean forestryBeehive()
    {
        return true;
    }

    @ConfigItem(
        keyName = "forestryFriendlyEnt",
        name = "Forestry: Friendly Ent",
        description = "Alert when a Friendly Ent event starts: entlings need pruning",
        section = forestrySection,
        position = 8
    )
    default boolean forestryFriendlyEnt()
    {
        return true;
    }

    @ConfigItem(
        keyName = "forestryPoachers",
        name = "Forestry: Poachers",
        description = "Alert when a Poachers event starts: a fox is caught in a poacher's trap",
        section = forestrySection,
        position = 9
    )
    default boolean forestryPoachers()
    {
        return true;
    }

    @ConfigItem(
        keyName = "forestryPheasantControl",
        name = "Forestry: Pheasant Control",
        description = "Alert when a Pheasant Control event starts: the Freaky Forester wants pheasant eggs",
        section = forestrySection,
        position = 10
    )
    default boolean forestryPheasantControl()
    {
        return true;
    }

    @ConfigItem(
        keyName = "forestryEnchantmentRitual",
        name = "Forestry: Enchantment Ritual",
        description = "Alert when an Enchantment Ritual event starts: a dryad's ritual needs the right circle",
        section = forestrySection,
        position = 11
    )
    default boolean forestryEnchantmentRitual()
    {
        return true;
    }

    // Sailing

    @ConfigItem(
        keyName = "enableSailingAlert",
        name = "Salvage Alert",
        description = "Alert when a shipwreck is depleted or you have no more salvage to sort (Custom preset only)",
        section = sailingSection,
        position = 0
    )
    default boolean enableSailingAlert()
    {
        return true;
    }

    @ConfigItem(
        keyName = "salvageDismissOnClick",
        name = "Salvage: Dismiss on Click",
        description = "A click or key press silences this alert. Off, it keeps going through clicks",
        section = sailingSection,
        position = 1
    )
    default boolean salvageDismissOnClick()
    {
        return true;
    }

    @ConfigItem(
        keyName = "sailingFlashColor",
        name = "Salvage: Color",
        description = "Screen flash and panel color for Salvage",
        section = sailingSection,
        position = 2
    )
    default Color sailingFlashColor()
    {
        return new Color(0, 200, 220);
    }

    @ConfigItem(
        keyName = "enableCargoHoldAlert",
        name = "Cargo Hold Alert",
        description = "Alert when chat says the cargo hold is full, e.g. your crew on the salvaging hook can't salvage (Custom preset only)",
        section = sailingSection,
        position = 3
    )
    default boolean enableCargoHoldAlert()
    {
        return true;
    }

    @ConfigItem(
        keyName = "cargoDismissOnClick",
        name = "Cargo Hold: Dismiss on Click",
        description = "A click or key press silences this alert. Off, it keeps going through clicks",
        section = sailingSection,
        position = 4
    )
    default boolean cargoDismissOnClick()
    {
        return true;
    }

    @ConfigItem(
        keyName = "cargoHoldFlashColor",
        name = "Cargo Hold: Color",
        description = "Screen flash and panel color for Cargo Hold",
        section = sailingSection,
        position = 5
    )
    default Color cargoHoldFlashColor()
    {
        return new Color(150, 110, 255);
    }
}
