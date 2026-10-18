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

@ConfigGroup(ConstsAfkConfig.GROUP)
public interface ConstsAfkConfig extends Config
{
    // Group name from before the consts-AFK rename, kept so saved settings carry over.
    String GROUP = "afkcombatalerts";
    String PRESET_KEY = "preset";
    String SIDEBAR_GUIDE_KEY = "showSidebarGuide";
    String ALERT_SOUND_KEY = "alertSound";
    String CUSTOM_SOUND_KEY = "customSoundId";
    String SOUND_VOLUME_KEY = "soundVolume";

    @ConfigItem(
        keyName = PRESET_KEY,
        name = "Preset",
        description = "Which alerts are on. Custom uses each section's Enable toggle; every other preset overrides those toggles.",
        position = 0
    )
    default Preset preset()
    {
        return Preset.CUSTOM;
    }

    @ConfigSection(
        name = "General",
        description = "Interaction pause, startup delay and panel options",
        position = 1
    )
    String generalSection = "general";

    @ConfigSection(
        name = "Screen Flash",
        description = "Screen flash behavior",
        position = 2
    )
    String flashSection = "flash";

    @ConfigSection(
        name = "Sound",
        description = "Alert beep options",
        position = 3
    )
    String soundSection = "sound";

    @ConfigSection(
        name = "Health Alert",
        description = "Alert while Hitpoints are below a threshold",
        position = 4
    )
    String healthSection = "health";

    @ConfigSection(
        name = "Prayer Alert",
        description = "Alert while Prayer is below a threshold",
        position = 5
    )
    String prayerSection = "prayer";

    @ConfigSection(
        name = "Idle Alert",
        description = "Alert when no activity is detected",
        position = 6
    )
    String idleSection = "idle";

    @ConfigSection(
        name = "Aggro Alert",
        description = "OSRS aggro timer (10 min) or custom interval",
        position = 7
    )
    String aggroSection = "aggro";

    @ConfigSection(
        name = "Logout Alert",
        description = "Warn before the inactivity logout",
        position = 8
    )
    String logoutSection = "logout";

    @ConfigSection(
        name = "Potion Expiry Alert",
        description = "Alert when a boost or effect is about to expire",
        position = 9
    )
    String potionSection = "potion";

    @ConfigSection(
        name = "Random Event Alert",
        description = "Alert when a random event NPC appears",
        position = 10
    )
    String randomSection = "random";

    @ConfigSection(
        name = "Cannon Alert",
        description = "Alert when your cannon is out of ammo",
        position = 11
    )
    String cannonSection = "cannon";

    @ConfigSection(
        name = "Inventory Full Alert",
        description = "Alert when your inventory is too full to hold more",
        position = 12
    )
    String inventoryFullSection = "inventoryFull";

    @ConfigSection(
        name = "Sailing Alerts",
        description = "Shipwreck salvage and cargo hold alerts",
        position = 13
    )
    String sailingSection = "sailing";

    @ConfigSection(
        name = "Forestry Alert",
        description = "Alert when a Forestry event starts nearby, for the events you pick",
        position = 14
    )
    String forestrySection = "forestry";

    // General

    @Range(min = 0, max = 60_000)
    @Units(Units.MILLISECONDS)
    @ConfigItem(
        keyName = "interactionPauseMs",
        name = "Interaction Pause",
        description = "Silence alerts for this long after each click or key press",
        section = generalSection,
        position = 0
    )
    default int interactionPauseMs()
    {
        return 3000;
    }

    @Range(min = 0, max = 120_000)
    @Units(Units.MILLISECONDS)
    @ConfigItem(
        keyName = "startupDelayMs",
        name = "Startup Delay",
        description = "Wait this long after the plugin starts before any alert can fire",
        section = generalSection,
        position = 1
    )
    default int startupDelayMs()
    {
        return 8000;
    }

    @ConfigItem(
        keyName = "showAnimationId",
        name = "Show Animation ID",
        description = "Show your current animation id in the status panel (handy for tuning the idle alert)",
        section = generalSection,
        position = 2
    )
    default boolean showAnimationId()
    {
        return false;
    }

    @ConfigItem(
        keyName = SIDEBAR_GUIDE_KEY,
        name = "Show Sidebar Guide",
        description = "A consts-AFK tab in the sidebar with a preset switcher and a short training guide for each preset",
        section = generalSection,
        position = 3
    )
    default boolean showSidebarGuide()
    {
        return true;
    }

    // Screen flash

    @ConfigItem(
        keyName = "enableScreenFlash",
        name = "Enable Screen Flash",
        description = "Flash the game screen while an alert is firing",
        section = flashSection,
        position = 0
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
        section = flashSection,
        position = 1
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
        section = flashSection,
        position = 2
    )
    default int flashOpacity()
    {
        return 50;
    }

    // Sound

    @ConfigItem(
        keyName = "enableAllSoundAlerts",
        name = "Enable Sounds",
        description = "Beep while an alert is firing",
        section = soundSection,
        position = 0
    )
    default boolean enableAllSoundAlerts()
    {
        return true;
    }

    @Range(min = 100, max = 60_000)
    @Units(Units.MILLISECONDS)
    @ConfigItem(
        keyName = "repeatIntervalMs",
        name = "Repeat Interval",
        description = "Time between beeps while an alert is firing",
        section = soundSection,
        position = 1
    )
    default int repeatIntervalMs()
    {
        return 1200;
    }

    @ConfigItem(
        keyName = ALERT_SOUND_KEY,
        name = "Alert Sound",
        description = "What alerts sound like: the system beep, or one of the game's own sounds. Picking one plays it once.",
        section = soundSection,
        position = 2
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
        section = soundSection,
        position = 3
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
        section = soundSection,
        position = 4
    )
    default int soundVolume()
    {
        return SoundEffectVolume.MEDIUM_HIGH;
    }

    // Health

    @ConfigItem(
        keyName = "enableHealthAlert",
        name = "Enable Health Alert",
        description = "Alert while Hitpoints are below the threshold (Custom preset only)",
        section = healthSection,
        position = 0
    )
    default boolean enableHealthAlert()
    {
        return true;
    }

    @Range(min = 1, max = 99)
    @ConfigItem(
        keyName = "healthThreshold",
        name = "Health Threshold",
        description = "Alert when Hitpoints are below this value",
        section = healthSection,
        position = 1
    )
    default int healthThreshold()
    {
        return 25;
    }

    @ConfigItem(
        keyName = "healthFlashColor",
        name = "Health Flash Color",
        description = "Screen flash and panel color for Health alerts",
        section = healthSection,
        position = 2
    )
    default Color healthFlashColor()
    {
        return new Color(75, 205, 90);
    }

    @ConfigItem(
        keyName = "healthEmitImmediately",
        name = "Emit Immediately",
        description = "Keep beeping through the post-interaction pause",
        section = healthSection,
        position = 3
    )
    default boolean healthEmitImmediately()
    {
        return false;
    }

    // Prayer

    @ConfigItem(
        keyName = "enablePrayerAlert",
        name = "Enable Prayer Alert",
        description = "Alert while Prayer is below the threshold (Custom preset only)",
        section = prayerSection,
        position = 0
    )
    default boolean enablePrayerAlert()
    {
        return true;
    }

    @Range(min = 1, max = 99)
    @ConfigItem(
        keyName = "prayerThreshold",
        name = "Prayer Threshold",
        description = "Alert when Prayer is below this value",
        section = prayerSection,
        position = 1
    )
    default int prayerThreshold()
    {
        return 20;
    }

    @ConfigItem(
        keyName = "prayerFlashColor",
        name = "Prayer Flash Color",
        description = "Screen flash and panel color for Prayer alerts",
        section = prayerSection,
        position = 2
    )
    default Color prayerFlashColor()
    {
        return new Color(88, 190, 255);
    }

    @ConfigItem(
        keyName = "prayerEmitImmediately",
        name = "Emit Immediately",
        description = "Keep beeping through the post-interaction pause",
        section = prayerSection,
        position = 3
    )
    default boolean prayerEmitImmediately()
    {
        return false;
    }

    // Idle

    @ConfigItem(
        keyName = "enableIdleAlert",
        name = "Enable Idle Alert",
        description = "Alert when no activity is detected for the threshold (Custom preset only)",
        section = idleSection,
        position = 0
    )
    default boolean enableIdleAlert()
    {
        return true;
    }

    @Range(min = 1000, max = 600_000)
    @Units(Units.MILLISECONDS)
    @ConfigItem(
        keyName = "idleThresholdMs",
        name = "Idle Threshold",
        description = "Alert after this much time without movement, new animations, combat or XP",
        section = idleSection,
        position = 1
    )
    default int idleThresholdMs()
    {
        return 6000;
    }

    @ConfigItem(
        keyName = "idleRequireNoAnimation",
        name = "Suppress While Animating",
        description = "Only alert once your character stops animating, so a repeating animation (chopping, mining, fishing, fighting) holds the alert off. Every preset but Custom always does this.",
        section = idleSection,
        position = 2
    )
    default boolean idleRequireNoAnimation()
    {
        return true;
    }

    @ConfigItem(
        keyName = "idleFlashColor",
        name = "Idle Flash Color",
        description = "Screen flash and panel color for Idle alerts",
        section = idleSection,
        position = 3
    )
    default Color idleFlashColor()
    {
        return new Color(255, 210, 80);
    }

    // Aggro

    @ConfigItem(
        keyName = "enableTimerAlert",
        name = "Enable Aggro Alert",
        description = "Alert when the aggro timer runs out (Custom preset only)",
        section = aggroSection,
        position = 0
    )
    default boolean enableTimerAlert()
    {
        return true;
    }

    @ConfigItem(
        keyName = "timerMode",
        name = "Aggro Mode",
        description = "OSRS 10-minute aggro timing, or a repeating custom interval",
        section = aggroSection,
        position = 1
    )
    default TimerMode timerMode()
    {
        return TimerMode.OSRS_AGGRO_10_MIN;
    }

    @Range(min = 1, max = 180)
    @ConfigItem(
        keyName = "timerMinutes",
        name = "Custom Aggro Minutes",
        description = "Minutes between alerts when Aggro Mode is Custom Minutes",
        section = aggroSection,
        position = 2
    )
    default int timerMinutes()
    {
        return 10;
    }

    @Range(min = 5, max = 250)
    @ConfigItem(
        keyName = "aggroResetDistanceTiles",
        name = "Auto Reset Distance",
        description = "In OSRS aggro mode, moving this many tiles from the aggro anchor restarts the 10-minute timer",
        section = aggroSection,
        position = 3
    )
    default int aggroResetDistanceTiles()
    {
        return 20;
    }

    @ConfigItem(
        keyName = "timerFlashColor",
        name = "Aggro Flash Color",
        description = "Screen flash and panel color for Aggro alerts",
        section = aggroSection,
        position = 4
    )
    default Color timerFlashColor()
    {
        return new Color(210, 95, 95);
    }

    // Logout

    @ConfigItem(
        keyName = "enableLogoutWarning",
        name = "Enable Logout Warning",
        description = "Warn when approaching the inactivity logout (Custom preset only)",
        section = logoutSection,
        position = 0
    )
    default boolean enableLogoutWarning()
    {
        return true;
    }

    @Range(min = 60_000, max = 1_800_000)
    @Units(Units.MILLISECONDS)
    @ConfigItem(
        keyName = "logoutWarningMs",
        name = "Logout Warning Time",
        description = "Warn after this long without a click or key press (24 minutes default)",
        section = logoutSection,
        position = 1
    )
    default int logoutWarningMs()
    {
        return 1_440_000;
    }

    @ConfigItem(
        keyName = "logoutWarningFlashColor",
        name = "Logout Flash Color",
        description = "Screen flash and panel color for the logout warning",
        section = logoutSection,
        position = 2
    )
    default Color logoutWarningFlashColor()
    {
        return new Color(255, 171, 94);
    }

    // Potion expiry

    @ConfigItem(
        keyName = "enablePotionExpiryAlert",
        name = "Enable Potion Expiry Alert",
        description = "Alert when chat says something 'is about to' expire (Custom preset only)",
        section = potionSection,
        position = 0
    )
    default boolean enablePotionExpiryAlert()
    {
        return true;
    }

    @Range(min = 1, max = 20)
    @ConfigItem(
        keyName = "potionExpiryAlertIterations",
        name = "Alert Iterations",
        description = "How many beeps per expiry warning",
        section = potionSection,
        position = 1
    )
    default int potionExpiryAlertIterations()
    {
        return 9;
    }

    @ConfigItem(
        keyName = "potionExpiryFlashColor",
        name = "Potion Expiry Flash Color",
        description = "Screen flash and panel color for expiry alerts",
        section = potionSection,
        position = 2
    )
    default Color potionExpiryFlashColor()
    {
        return new Color(0, 0, 0);
    }

    @ConfigItem(
        keyName = "potionEmitImmediately",
        name = "Emit Immediately",
        description = "Keep beeping through the post-interaction pause, and don't let clicks cancel the alert",
        section = potionSection,
        position = 3
    )
    default boolean potionEmitImmediately()
    {
        return false;
    }

    // Random event

    @ConfigItem(
        keyName = "enableRandomEventAlert",
        name = "Enable Random Event Alert",
        description = "Alert when a random event NPC appears (Custom preset only)",
        section = randomSection,
        position = 0
    )
    default boolean enableRandomEventAlert()
    {
        return true;
    }

    @Range(min = 1, max = 20)
    @ConfigItem(
        keyName = "randomEventAlertIterations",
        name = "Alert Iterations",
        description = "How many beeps per random event",
        section = randomSection,
        position = 1
    )
    default int randomEventAlertIterations()
    {
        return 9;
    }

    @Range(min = 0, max = 120_000)
    @Units(Units.MILLISECONDS)
    @ConfigItem(
        keyName = "randomEventCooldownMs",
        name = "Trigger Cooldown",
        description = "Minimum time between random event alerts",
        section = randomSection,
        position = 2
    )
    default int randomEventCooldownMs()
    {
        return 20_000;
    }

    @ConfigItem(
        keyName = "randomEventFlashColor",
        name = "Random Event Flash Color",
        description = "Screen flash and panel color for random event alerts",
        section = randomSection,
        position = 3
    )
    default Color randomEventFlashColor()
    {
        return new Color(255, 255, 255);
    }

    // Cannon

    @ConfigItem(
        keyName = "enableCannonAlert",
        name = "Enable Cannon Alert",
        description = "Alert when chat says 'Your cannon is out of ammo!' (Custom preset only)",
        section = cannonSection,
        position = 0
    )
    default boolean enableCannonAlert()
    {
        return true;
    }

    @Range(min = 1, max = 500)
    @ConfigItem(
        keyName = "cannonAlertIterations",
        name = "Alert Iterations",
        description = "How many beeps per out-of-ammo warning",
        section = cannonSection,
        position = 1
    )
    default int cannonAlertIterations()
    {
        return 100;
    }

    @ConfigItem(
        keyName = "cannonFlashColor",
        name = "Cannon Flash Color",
        description = "Screen flash and panel color for cannon alerts",
        section = cannonSection,
        position = 2
    )
    default Color cannonFlashColor()
    {
        return new Color(255, 140, 0);
    }

    @ConfigItem(
        keyName = "cannonEmitImmediately",
        name = "Emit Immediately",
        description = "Keep beeping through the post-interaction pause, and don't let clicks cancel the alert",
        section = cannonSection,
        position = 3
    )
    default boolean cannonEmitImmediately()
    {
        return false;
    }

    // Inventory full

    @ConfigItem(
        keyName = "enableInventoryFullAlert",
        name = "Enable Inventory Full Alert",
        description = "Alert when chat says your inventory is too full to hold more (Custom preset only)",
        section = inventoryFullSection,
        position = 0
    )
    default boolean enableInventoryFullAlert()
    {
        return true;
    }

    @Range(min = 1, max = 500)
    @ConfigItem(
        keyName = "inventoryFullAlertIterations",
        name = "Alert Iterations",
        description = "How many beeps per inventory full warning",
        section = inventoryFullSection,
        position = 1
    )
    default int inventoryFullAlertIterations()
    {
        return 100;
    }

    @ConfigItem(
        keyName = "inventoryFullFlashColor",
        name = "Inventory Full Flash Color",
        description = "Screen flash and panel color for inventory full alerts",
        section = inventoryFullSection,
        position = 2
    )
    default Color inventoryFullFlashColor()
    {
        return new Color(220, 60, 220);
    }

    // Sailing

    @ConfigItem(
        keyName = "enableSailingAlert",
        name = "Enable Salvage Alert",
        description = "Alert when a shipwreck is depleted or you have no more salvage to sort (Custom preset only)",
        section = sailingSection,
        position = 0
    )
    default boolean enableSailingAlert()
    {
        return true;
    }

    @Range(min = 1, max = 500)
    @ConfigItem(
        keyName = "sailingAlertIterations",
        name = "Salvage Alert Iterations",
        description = "How many beeps per salvage warning",
        section = sailingSection,
        position = 1
    )
    default int sailingAlertIterations()
    {
        return 100;
    }

    @ConfigItem(
        keyName = "sailingFlashColor",
        name = "Salvage Flash Color",
        description = "Screen flash and panel color for salvage alerts",
        section = sailingSection,
        position = 2
    )
    default Color sailingFlashColor()
    {
        return new Color(0, 200, 220);
    }

    @ConfigItem(
        keyName = "enableCargoHoldAlert",
        name = "Enable Cargo Hold Full Alert",
        description = "Alert when chat says the cargo hold is full, e.g. your crew on the salvaging hook can't salvage (Custom preset only)",
        section = sailingSection,
        position = 3
    )
    default boolean enableCargoHoldAlert()
    {
        return true;
    }

    @Range(min = 1, max = 500)
    @ConfigItem(
        keyName = "cargoHoldAlertIterations",
        name = "Cargo Hold Alert Iterations",
        description = "How many beeps per cargo hold full warning",
        section = sailingSection,
        position = 4
    )
    default int cargoHoldAlertIterations()
    {
        return 100;
    }

    @ConfigItem(
        keyName = "cargoHoldFlashColor",
        name = "Cargo Hold Flash Color",
        description = "Screen flash and panel color for cargo hold alerts",
        section = sailingSection,
        position = 5
    )
    default Color cargoHoldFlashColor()
    {
        return new Color(150, 110, 255);
    }

    // Forestry

    @ConfigItem(
        keyName = "enableForestryAlert",
        name = "Enable Forestry Alert",
        description = "Alert when a Forestry event you picked below starts nearby (Custom preset only)",
        section = forestrySection,
        position = 0
    )
    default boolean enableForestryAlert()
    {
        return true;
    }

    @Range(min = 1, max = 500)
    @ConfigItem(
        keyName = "forestryAlertIterations",
        name = "Alert Iterations",
        description = "How many beeps per Forestry event",
        section = forestrySection,
        position = 1
    )
    default int forestryAlertIterations()
    {
        return 30;
    }

    @ConfigItem(
        keyName = "forestryFlashColor",
        name = "Forestry Flash Color",
        description = "Screen flash and panel color for Forestry alerts",
        section = forestrySection,
        position = 2
    )
    default Color forestryFlashColor()
    {
        return new Color(170, 240, 40);
    }

    @ConfigItem(
        keyName = "forestryRisingRoots",
        name = "Rising Roots",
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
        name = "Struggling Sapling",
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
        name = "Flowering Tree",
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
        name = "Woodcutting Leprechaun",
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
        name = "Beehive",
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
        name = "Friendly Ent",
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
        name = "Poachers",
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
        name = "Pheasant Control",
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
        name = "Enchantment Ritual",
        description = "Alert when an Enchantment Ritual event starts: a dryad's ritual needs the right circle",
        section = forestrySection,
        position = 11
    )
    default boolean forestryEnchantmentRitual()
    {
        return true;
    }
}
