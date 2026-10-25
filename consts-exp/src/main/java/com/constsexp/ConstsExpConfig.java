package com.constsexp;

import java.awt.Color;
import net.runelite.api.SoundEffectID;
import net.runelite.api.SoundEffectVolume;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

/**
 * Sections in the order of the sidebar's tabs, where ConstsExpPlugin puts them: one per alert (Alerts), the skills'
 * (Skills), XP per Hour and Tick Pendulum (Tick & XP), then Timing, Sounds and Screen Flash (General); only Tick &
 * XP's and General's start open. RuneLite's Configuration panel lists them in the same order with no tabs, so each
 * name reads on its own.
 * Every alert's section has the same items in the same order: Enabled (which only the Custom preset reads), Dismiss
 * on Click, Color, then whatever is its own. Some key names are older than this layout: Aggro's start with timer,
 * Salvage's with sailing, and two sections keep their old keys, Timing's general and Woodcutting's forestry.
 */
@ConfigGroup(ConstsExpConfig.GROUP)
public interface ConstsExpConfig extends Config
{
    String GROUP = "constsexp";
    String MASTER_KEY = "masterSwitch";
    String PRESET_KEY = "preset";
    // Not a setting: Custom's on/off set, saved when you leave Custom for a preset.
    String CUSTOM_SNAPSHOT_KEY = "customSnapshot";
    String ALERT_SOUND_KEY = "alertSound";
    String CUSTOM_SOUND_KEY = "customSoundId";
    String SOUND_VOLUME_KEY = "soundVolume";

    @ConfigItem(
        keyName = MASTER_KEY,
        name = "Master Switch",
        description = "Off silences consts-EXP: no alerts, no status panel, no area lines, no tree timer, no GOTR Autopilot. The plugin itself stays on, so its EXP icon stays in the sidebar.",
        position = -1
    )
    default boolean masterSwitch()
    {
        return true;
    }

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
        name = "Health",
        description = "Alert while Hitpoints are below a threshold",
        position = 1,
        closedByDefault = true
    )
    String healthSection = "health";

    @ConfigSection(
        name = "Prayer",
        description = "Alert while Prayer is below a threshold",
        position = 2,
        closedByDefault = true
    )
    String prayerSection = "prayer";

    @ConfigSection(
        name = "Aggro",
        description = "The aggro timer, the unaggressive area and its lines",
        position = 3,
        closedByDefault = true
    )
    String aggroSection = "aggro";

    @ConfigSection(
        name = "Potion",
        description = "Alert when a potion or effect is about to expire",
        position = 4,
        closedByDefault = true
    )
    String potionSection = "potion";

    @ConfigSection(
        name = "Cannon",
        description = "Alert when your cannon is out of ammo",
        position = 5,
        closedByDefault = true
    )
    String cannonSection = "cannon";

    @ConfigSection(
        name = "Idle",
        description = "Alert when nothing has happened for a while",
        position = 6,
        closedByDefault = true
    )
    String idleSection = "idle";

    @ConfigSection(
        name = "Logout",
        description = "Warn before the inactivity logout",
        position = 7,
        closedByDefault = true
    )
    String logoutSection = "logout";

    @ConfigSection(
        name = "Inventory Full",
        description = "Alert when your inventory is too full to hold more",
        position = 8,
        closedByDefault = true
    )
    String inventoryFullSection = "inventoryFull";

    @ConfigSection(
        name = "Random Event",
        description = "Your own random events",
        position = 9,
        closedByDefault = true
    )
    String randomSection = "random";

    @ConfigSection(
        name = "Woodcutting",
        description = "Forestry events, the ones you pick",
        position = 10,
        closedByDefault = true
    )
    String woodcuttingSection = "forestry";

    @ConfigSection(
        name = "Sailing: Salvage",
        description = "Alert when a shipwreck is depleted or there is nothing left to sort",
        position = 11,
        closedByDefault = true
    )
    String salvageSection = "salvage";

    @ConfigSection(
        name = "Sailing: Cargo Hold",
        description = "Alert when the cargo hold is full",
        position = 12,
        closedByDefault = true
    )
    String cargoSection = "cargo";

    @ConfigSection(
        name = "Runecraft",
        description = "GOTR Autopilot: the next step in Guardians of the Rift, a path to it and the timers",
        position = 13,
        closedByDefault = true
    )
    String runecraftSection = "runecraft";

    @ConfigSection(
        name = "XP per Hour",
        description = "XP per hour for the skill you're training, in the status panel",
        position = 14
    )
    String xpSection = "xp";

    @ConfigSection(
        name = "Tick Pendulum",
        description = "A pendulum in the status panel: each swing is one tick's click window, and the lines in its middle glow when it's time to click",
        position = 15
    )
    String tickSection = "tick";

    @ConfigSection(
        name = "Timing",
        description = "Click pause, quiet after dismiss, give up after and startup delay",
        position = 16
    )
    String timingSection = "general";

    @ConfigSection(
        name = "Sounds",
        description = "What alerts sound like, and how often",
        position = 17
    )
    String soundsSection = "sounds";

    @ConfigSection(
        name = "Screen Flash",
        description = "The flash over the game screen while an alert fires",
        position = 18
    )
    String flashSection = "flash";

    // Tick & XP

    @ConfigItem(
        keyName = "xpPerHour",
        name = "Enabled",
        description = "Show XP per hour for the skill you're training under the alerts in the status panel: the last skill to gain XP, Hitpoints and Slayer only if no other has. Shift + right-click the panel and Reset XP/h to set it back to zero after a break",
        section = xpSection,
        position = 0
    )
    default boolean xpPerHour()
    {
        return true;
    }

    @ConfigItem(
        keyName = "tickPendulum",
        name = "Enabled",
        description = "Show the tick pendulum under the alerts in the status panel. Click while the ball is between the two lines; left of it, your ticks in a row; right, your ping",
        section = tickSection,
        position = 0
    )
    default boolean tickPendulum()
    {
        return false;
    }

    @ConfigItem(
        keyName = "pendulumRhythm",
        name = "Rhythm",
        description = "Single click: one click a swing (2-tick teaks, cooking). Flick: two clicks in the same swing (prayer flicking). Every other swing: only the swing your hitsplat shows in takes a click (2-tick harpooning)",
        section = tickSection,
        position = 1
    )
    default TickPendulum.Rhythm pendulumRhythm()
    {
        return TickPendulum.Rhythm.SINGLE;
    }

    @Range(max = 500)
    @Units(Units.MILLISECONDS)
    @ConfigItem(
        keyName = "yourPing",
        name = "Your Ping",
        description = "0 learns your ping from your prayer and run toggles, starting each world from a guess. Set it to use your own ping instead",
        section = tickSection,
        position = 2
    )
    default int yourPing()
    {
        return 0;
    }

    // General

    @Range(min = 0, max = 60_000)
    @Units(Units.MILLISECONDS)
    @ConfigItem(
        keyName = "interactionPauseMs",
        name = "Click Pause",
        description = "Silence alerts for this long after each click or key press, except alerts with Dismiss on Click off",
        section = timingSection,
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
        section = timingSection,
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
        section = timingSection,
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
        section = timingSection,
        position = 3
    )
    default int startupDelayMs()
    {
        return 8000;
    }

    @ConfigItem(
        keyName = "enableAllSoundAlerts",
        name = "Enabled",
        description = "Play a sound while an alert is firing",
        section = soundsSection,
        position = 0
    )
    default boolean enableAllSoundAlerts()
    {
        return true;
    }

    @ConfigItem(
        keyName = ALERT_SOUND_KEY,
        name = "Sound",
        description = "What alerts sound like: the system beep, or one of the game's own sounds. Picking one plays it once.",
        section = soundsSection,
        position = 1
    )
    default AlertSound alertSound()
    {
        return AlertSound.SYSTEM_BEEP;
    }

    @Range(min = -1, max = 100_000)
    @ConfigItem(
        keyName = CUSTOM_SOUND_KEY,
        name = "Custom Sound ID",
        description = "The game sound effect to play when Sound is Custom sound ID (the OSRS Wiki lists them)",
        section = soundsSection,
        position = 2
    )
    default int customSoundId()
    {
        return SoundEffectID.TOWN_CRIER_BELL_DING;
    }

    @Range(min = 1, max = SoundEffectVolume.HIGH)
    @ConfigItem(
        keyName = SOUND_VOLUME_KEY,
        name = "Volume",
        description = "How loud a game sound plays (1-127), even with the game's own sound effects turned off",
        section = soundsSection,
        position = 3
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
        section = soundsSection,
        position = 4
    )
    default int repeatIntervalMs()
    {
        return 1200;
    }

    @ConfigItem(
        keyName = "enableScreenFlash",
        name = "Enabled",
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
        name = "Interval",
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
        name = "Opacity",
        description = "Screen flash opacity (kept translucent)",
        section = flashSection,
        position = 2
    )
    default int flashOpacity()
    {
        return 50;
    }

    // Health, Prayer, Aggro, Potion, Cannon

    @ConfigItem(
        keyName = "enableHealthAlert",
        name = "Enabled",
        description = "Alert while Hitpoints are below the threshold (Custom preset only)",
        section = healthSection,
        position = 0
    )
    default boolean enableHealthAlert()
    {
        return true;
    }

    @ConfigItem(
        keyName = "healthDismissOnClick",
        name = "Dismiss on Click",
        description = "A click or key press silences this alert. Off, it keeps going through clicks",
        section = healthSection,
        position = 1
    )
    default boolean healthDismissOnClick()
    {
        return true;
    }

    @ConfigItem(
        keyName = "healthFlashColor",
        name = "Color",
        description = "Screen flash and panel color for Health",
        section = healthSection,
        position = 2
    )
    default Color healthFlashColor()
    {
        return new Color(75, 205, 90);
    }

    @Range(min = 1, max = 99)
    @ConfigItem(
        keyName = "healthThreshold",
        name = "Threshold",
        description = "Alert when Hitpoints are below this",
        section = healthSection,
        position = 3
    )
    default int healthThreshold()
    {
        return 25;
    }

    @ConfigItem(
        keyName = "enablePrayerAlert",
        name = "Enabled",
        description = "Alert while Prayer is below the threshold (Custom preset only)",
        section = prayerSection,
        position = 0
    )
    default boolean enablePrayerAlert()
    {
        return true;
    }

    @ConfigItem(
        keyName = "prayerDismissOnClick",
        name = "Dismiss on Click",
        description = "A click or key press silences this alert. Off, it keeps going through clicks",
        section = prayerSection,
        position = 1
    )
    default boolean prayerDismissOnClick()
    {
        return true;
    }

    @ConfigItem(
        keyName = "prayerFlashColor",
        name = "Color",
        description = "Screen flash and panel color for Prayer",
        section = prayerSection,
        position = 2
    )
    default Color prayerFlashColor()
    {
        return new Color(88, 190, 255);
    }

    @Range(min = 1, max = 99)
    @ConfigItem(
        keyName = "prayerThreshold",
        name = "Threshold",
        description = "Alert when Prayer is below this",
        section = prayerSection,
        position = 3
    )
    default int prayerThreshold()
    {
        return 20;
    }

    @ConfigItem(
        keyName = "enableTimerAlert",
        name = "Enabled",
        description = "Alert when the aggro timer runs out (Custom preset only). OSRS mode tracks the game's unaggressive area and timer, based on RuneLite's NPC Aggression Timer by Woox",
        section = aggroSection,
        position = 0
    )
    default boolean enableTimerAlert()
    {
        return true;
    }

    @ConfigItem(
        keyName = "aggroDismissOnClick",
        name = "Dismiss on Click",
        description = "A click or key press silences this alert. Off, it keeps going through clicks",
        section = aggroSection,
        position = 1
    )
    default boolean aggroDismissOnClick()
    {
        return true;
    }

    @ConfigItem(
        keyName = "timerFlashColor",
        name = "Color",
        description = "Screen flash and panel color for Aggro",
        section = aggroSection,
        position = 2
    )
    default Color timerFlashColor()
    {
        return new Color(210, 95, 95);
    }

    @ConfigItem(
        keyName = "timerMode",
        name = "Mode",
        description = "OSRS: the game's 10-minute timer, restarted when you leave the unaggressive area. Custom Minutes: a repeating interval",
        section = aggroSection,
        position = 3
    )
    default TimerMode timerMode()
    {
        return TimerMode.OSRS_AGGRO_10_MIN;
    }

    @Range(min = 1, max = 180)
    @ConfigItem(
        keyName = "timerMinutes",
        name = "Custom Minutes",
        description = "Minutes between alerts when Mode is Custom Minutes",
        section = aggroSection,
        position = 4
    )
    default int timerMinutes()
    {
        return 10;
    }

    // The area lines and their default colors come from RuneLite's NPC Aggression Timer (BSD 2-Clause,
    // Copyright (c) 2018, Woox); see AggroArea.java.
    @ConfigItem(
        keyName = "showAggroAreaLines",
        name = "Show Area Lines",
        description = "In OSRS mode, outline the unaggressive area on the ground. Walk out of it to restart the 10-minute timer",
        section = aggroSection,
        position = 5
    )
    default boolean showAggroAreaLines()
    {
        return true;
    }

    @Alpha
    @ConfigItem(
        keyName = "aggroAreaColor",
        name = "Aggressive Color",
        description = "Area line color while NPCs are still aggressive",
        section = aggroSection,
        position = 6
    )
    default Color aggroAreaColor()
    {
        return new Color(0x64FFFF00, true);
    }

    @Alpha
    @ConfigItem(
        keyName = "unaggroAreaColor",
        name = "Unaggressive Color",
        description = "Area line color once NPCs have stopped being aggressive",
        section = aggroSection,
        position = 7
    )
    default Color unaggroAreaColor()
    {
        return new Color(0xFFFF00);
    }

    @ConfigItem(
        keyName = "enablePotionExpiryAlert",
        name = "Enabled",
        description = "Alert when chat says a potion or effect is about to expire (Custom preset only)",
        section = potionSection,
        position = 0
    )
    default boolean enablePotionExpiryAlert()
    {
        return true;
    }

    @ConfigItem(
        keyName = "potionDismissOnClick",
        name = "Dismiss on Click",
        description = "A click or key press silences this alert. Off, it keeps going through clicks",
        section = potionSection,
        position = 1
    )
    default boolean potionDismissOnClick()
    {
        return true;
    }

    @ConfigItem(
        keyName = "potionExpiryFlashColor",
        name = "Color",
        description = "Screen flash and panel color for Potion",
        section = potionSection,
        position = 2
    )
    default Color potionExpiryFlashColor()
    {
        return new Color(0, 0, 0);
    }

    @ConfigItem(
        keyName = "enableCannonAlert",
        name = "Enabled",
        description = "Alert when chat says 'Your cannon is out of ammo!' (Custom preset only)",
        section = cannonSection,
        position = 0
    )
    default boolean enableCannonAlert()
    {
        return true;
    }

    @ConfigItem(
        keyName = "cannonDismissOnClick",
        name = "Dismiss on Click",
        description = "A click or key press silences this alert. Off, it keeps going through clicks",
        section = cannonSection,
        position = 1
    )
    default boolean cannonDismissOnClick()
    {
        return true;
    }

    @ConfigItem(
        keyName = "cannonFlashColor",
        name = "Color",
        description = "Screen flash and panel color for Cannon",
        section = cannonSection,
        position = 2
    )
    default Color cannonFlashColor()
    {
        return new Color(255, 140, 0);
    }

    // Idle, Logout, Inventory Full

    @ConfigItem(
        keyName = "enableIdleAlert",
        name = "Enabled",
        description = "Alert when nothing has happened for the threshold (Custom preset only)",
        section = idleSection,
        position = 0
    )
    default boolean enableIdleAlert()
    {
        return true;
    }

    @ConfigItem(
        keyName = "idleDismissOnClick",
        name = "Dismiss on Click",
        description = "A click or key press silences this alert. Off, it keeps going through clicks",
        section = idleSection,
        position = 1
    )
    default boolean idleDismissOnClick()
    {
        return true;
    }

    @ConfigItem(
        keyName = "idleFlashColor",
        name = "Color",
        description = "Screen flash and panel color for Idle",
        section = idleSection,
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
        name = "Threshold",
        description = "Alert after this much time without movement, new animations, combat or XP",
        section = idleSection,
        position = 3
    )
    default int idleThresholdMs()
    {
        return 6000;
    }

    @ConfigItem(
        keyName = "idleRequireNoAnimation",
        name = "Suppress While Animating",
        description = "Only alert once your character stops animating, so a repeating animation (chopping, mining, fishing, fighting) holds it off. Every preset but Custom always does this.",
        section = idleSection,
        position = 4
    )
    default boolean idleRequireNoAnimation()
    {
        return true;
    }

    @ConfigItem(
        keyName = "enableLogoutWarning",
        name = "Enabled",
        description = "Warn before the inactivity logout. Any click or key press restarts it (Custom preset only)",
        section = logoutSection,
        position = 0
    )
    default boolean enableLogoutWarning()
    {
        return true;
    }

    @ConfigItem(
        keyName = "logoutWarningFlashColor",
        name = "Color",
        description = "Screen flash and panel color for Logout",
        section = logoutSection,
        position = 1
    )
    default Color logoutWarningFlashColor()
    {
        return new Color(255, 171, 94);
    }

    @Range(min = 60_000, max = 1_800_000)
    @Units(Units.MILLISECONDS)
    @ConfigItem(
        keyName = "logoutWarningMs",
        name = "Warn After",
        description = "Warn after this long without a click or key press (24 minutes default)",
        section = logoutSection,
        position = 2
    )
    default int logoutWarningMs()
    {
        return 1_440_000;
    }

    @ConfigItem(
        keyName = "enableInventoryFullAlert",
        name = "Enabled",
        description = "Alert when chat says your inventory is too full to hold more (Custom preset only)",
        section = inventoryFullSection,
        position = 0
    )
    default boolean enableInventoryFullAlert()
    {
        return true;
    }

    @ConfigItem(
        keyName = "inventoryFullDismissOnClick",
        name = "Dismiss on Click",
        description = "A click or key press silences this alert. Off, it keeps going through clicks",
        section = inventoryFullSection,
        position = 1
    )
    default boolean inventoryFullDismissOnClick()
    {
        return true;
    }

    @ConfigItem(
        keyName = "inventoryFullFlashColor",
        name = "Color",
        description = "Screen flash and panel color for Inventory Full",
        section = inventoryFullSection,
        position = 2
    )
    default Color inventoryFullFlashColor()
    {
        return new Color(220, 60, 220);
    }

    // Random event

    @ConfigItem(
        keyName = "enableRandomEventAlert",
        name = "Enabled",
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
        name = "Dismiss on Click",
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
        name = "Color",
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
        name = "Cooldown",
        description = "Minimum time between random event alerts",
        section = randomSection,
        position = 3
    )
    default int randomEventCooldownMs()
    {
        return 20_000;
    }

    // Woodcutting

    @ConfigItem(
        keyName = "enableForestryAlert",
        name = "Enabled",
        description = "Alert when a Forestry event you picked below starts nearby (Custom preset only)",
        section = woodcuttingSection,
        position = 0
    )
    default boolean enableForestryAlert()
    {
        return true;
    }

    @ConfigItem(
        keyName = "forestryDismissOnClick",
        name = "Dismiss on Click",
        description = "A click or key press silences this alert. Off, it keeps going through clicks",
        section = woodcuttingSection,
        position = 1
    )
    default boolean forestryDismissOnClick()
    {
        return true;
    }

    @ConfigItem(
        keyName = "forestryFlashColor",
        name = "Color",
        description = "Screen flash and panel color for Forestry",
        section = woodcuttingSection,
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
        section = woodcuttingSection,
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
        section = woodcuttingSection,
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
        section = woodcuttingSection,
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
        section = woodcuttingSection,
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
        section = woodcuttingSection,
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
        section = woodcuttingSection,
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
        section = woodcuttingSection,
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
        section = woodcuttingSection,
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
        section = woodcuttingSection,
        position = 11
    )
    default boolean forestryEnchantmentRitual()
    {
        return true;
    }

    // From Tree Despawn Timer (BSD 2-Clause, Copyright (c) 2023, Jason O'Neill); see TreeTimer.java. Not an alert: no
    // preset touches it.
    @ConfigItem(
        keyName = "showTreeTimer",
        name = "Tree Timer",
        description = "A pie on each tree that empties as it nears falling, whatever the preset (the Master Switch hides it too). A tree that was already being chopped when you got there shows none, since its time can't be known",
        section = woodcuttingSection,
        position = 12
    )
    default boolean showTreeTimer()
    {
        return true;
    }

    // Sailing

    @ConfigItem(
        keyName = "enableSailingAlert",
        name = "Enabled",
        description = "Alert when a shipwreck is depleted or you have no more salvage to sort (Custom preset only)",
        section = salvageSection,
        position = 0
    )
    default boolean enableSailingAlert()
    {
        return true;
    }

    @ConfigItem(
        keyName = "salvageDismissOnClick",
        name = "Dismiss on Click",
        description = "A click or key press silences this alert. Off, it keeps going through clicks",
        section = salvageSection,
        position = 1
    )
    default boolean salvageDismissOnClick()
    {
        return true;
    }

    @ConfigItem(
        keyName = "sailingFlashColor",
        name = "Color",
        description = "Screen flash and panel color for Salvage",
        section = salvageSection,
        position = 2
    )
    default Color sailingFlashColor()
    {
        return new Color(0, 200, 220);
    }

    @ConfigItem(
        keyName = "enableCargoHoldAlert",
        name = "Enabled",
        description = "Alert when chat says the cargo hold is full, e.g. your crew on the salvaging hook can't salvage (Custom preset only)",
        section = cargoSection,
        position = 0
    )
    default boolean enableCargoHoldAlert()
    {
        return true;
    }

    @ConfigItem(
        keyName = "cargoDismissOnClick",
        name = "Dismiss on Click",
        description = "A click or key press silences this alert. Off, it keeps going through clicks",
        section = cargoSection,
        position = 1
    )
    default boolean cargoDismissOnClick()
    {
        return true;
    }

    @ConfigItem(
        keyName = "cargoHoldFlashColor",
        name = "Color",
        description = "Screen flash and panel color for Cargo Hold",
        section = cargoSection,
        position = 2
    )
    default Color cargoHoldFlashColor()
    {
        return new Color(150, 110, 255);
    }

    // Runecraft. GOTR Autopilot (BSD 2-Clause, Copyright (c) 2026, FabulousOtter), in the gotr package. Not an alert: no
    // preset touches it, and its switch is its only setting.

    @ConfigItem(
        keyName = "gotrAutopilot",
        name = "GOTR Autopilot",
        description = "In Guardians of the Rift, a panel with the next step and the game's timers, an outline on what to click and a path to it. It plays the Mass strategy",
        section = runecraftSection,
        position = 0
    )
    default boolean gotrAutopilot()
    {
        return true;
    }
}
