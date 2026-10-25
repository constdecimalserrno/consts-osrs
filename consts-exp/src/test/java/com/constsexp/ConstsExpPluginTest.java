package com.constsexp;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.constsexp.gotr.GotrAutopilot;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.SoundEffectID;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigDescriptor;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.game.WorldService;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.components.colorpicker.ColorPickerManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.MouseManager;
import net.runelite.client.ui.overlay.OverlayManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class ConstsExpPluginTest
{
    private static final String CARGO_FULL_MESSAGE = "Your crewmate on the salvaging hook cannot salvage as the cargo hold is full.";

    private ConstsExpPlugin plugin;
    private Client client;
    private ConstsExpConfig config;
    private ConfigManager configManager;
    private Player player;

    @Before
    public void setUp() throws Exception
    {
        plugin = new ConstsExpPlugin();
        client = mock(Client.class);
        config = mock(ConstsExpConfig.class);
        configManager = mock(ConfigManager.class);
        player = mock(Player.class);
        ClientThread clientThread = mock(ClientThread.class);

        setField("client", client);
        setField("config", config);
        setField("configManager", configManager);
        setField("clientThread", clientThread);
        setField("treeTimer", new TreeTimer(client));
        setField("sessionActive", true);
        setField("gotrAutopilot", mock(GotrAutopilot.class));

        when(config.masterSwitch()).thenReturn(true);
        when(config.preset()).thenReturn(Preset.CUSTOM);
        when(config.enableTimerAlert()).thenReturn(true);
        when(config.giveUpAfterSeconds()).thenReturn(120);
        // Every alert's Dismiss on Click defaults on; a mock's false would flip them all.
        when(config.healthDismissOnClick()).thenReturn(true);
        when(config.prayerDismissOnClick()).thenReturn(true);
        when(config.idleDismissOnClick()).thenReturn(true);
        when(config.aggroDismissOnClick()).thenReturn(true);
        when(config.potionDismissOnClick()).thenReturn(true);
        when(config.randomDismissOnClick()).thenReturn(true);
        when(config.cannonDismissOnClick()).thenReturn(true);
        when(config.inventoryFullDismissOnClick()).thenReturn(true);
        when(config.salvageDismissOnClick()).thenReturn(true);
        when(config.cargoDismissOnClick()).thenReturn(true);
        when(config.forestryDismissOnClick()).thenReturn(true);
        when(config.enableCargoHoldAlert()).thenReturn(true);
        when(config.enablePotionExpiryAlert()).thenReturn(true);
        when(config.interactionPauseMs()).thenReturn(3000);
        when(config.logoutWarningMs()).thenReturn(1_440_000);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getLocalPlayer()).thenReturn(player);
        when(player.getWorldLocation()).thenReturn(new WorldPoint(3200, 3200, 0));

        doAnswer(invocation ->
        {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(clientThread).invokeLater(any(Runnable.class));
    }

    @After
    public void tearDown() throws Exception
    {
        invokePrivate("shutdownScheduler");
    }

    // Guard lures

    @Test
    @SuppressWarnings("unchecked")
    public void guardLuresShowInPortPiscariliusWhileBothSwitchesAreOnAndClearOnAHop()
    {
        List<NPC> seen = new java.util.ArrayList<>(List.of(GuardLuresTest.npc(6980, new WorldPoint(1777, 3746, 0), 0)));
        IndexedObjectSet<NPC> npcs = mock(IndexedObjectSet.class);
        when(npcs.iterator()).thenAnswer(invocation -> List.copyOf(seen).iterator());
        WorldView worldView = mock(WorldView.class);
        when(worldView.npcs()).thenReturn((IndexedObjectSet) npcs);
        // The tree timer reads the players.
        IndexedObjectSet<Player> players = mock(IndexedObjectSet.class);
        when(players.iterator()).thenAnswer(invocation -> List.<Player>of().iterator());
        when(worldView.players()).thenReturn((IndexedObjectSet) players);
        when(client.getTopLevelWorldView()).thenReturn(worldView);
        when(player.getWorldLocation()).thenReturn(new WorldPoint(1790, 3740, 0));

        plugin.onGameTick(new GameTick());
        assertFalse("Guard Lures off", plugin.isGuardLuresShown());

        when(config.guardLures()).thenReturn(true);
        plugin.onGameTick(new GameTick());
        assertTrue(plugin.isGuardLuresShown());
        assertTrue(plugin.getGuardLures().isEastLured());

        // Gone from sight: it stays lured.
        seen.clear();
        plugin.onGameTick(new GameTick());
        assertTrue(plugin.getGuardLures().isEastLured());

        // The Master Switch hides the rows on its tick.
        when(config.masterSwitch()).thenReturn(false);
        plugin.onGameTick(new GameTick());
        assertFalse(plugin.isGuardLuresShown());
        when(config.masterSwitch()).thenReturn(true);

        // Logging in, before the local player is there: nothing, and no NullPointerException.
        when(client.getLocalPlayer()).thenReturn(null);
        plugin.onGameTick(new GameTick());
        assertFalse(plugin.isGuardLuresShown());

        // Another world's guards start not lured.
        plugin.onGameStateChanged(gameState(GameState.HOPPING));
        assertFalse(plugin.getGuardLures().isEastLured());
    }

    // XP per hour

    @Test
    public void xpPerHourCountsEachDropFromTheFirstTickClearsAtTheLoginScreenAndAtShutDown() throws Exception
    {
        // At login the client may still read 0 XP; the login's burst of real values before the first tick isn't drops.
        plugin.onGameStateChanged(gameState(GameState.LOGGED_IN));
        plugin.onStatChanged(new StatChanged(Skill.MINING, 50_000, 60, 60));
        plugin.onStatChanged(new StatChanged(Skill.MINING, 50_035, 60, 60));
        assertNull(plugin.getXpRates().shown());

        when(client.getSkillExperience(Skill.MINING)).thenReturn(50_035);
        plugin.onGameTick(new GameTick());
        plugin.onStatChanged(new StatChanged(Skill.MINING, 50_070, 60, 60));
        plugin.onStatChanged(new StatChanged(Skill.MINING, 50_105, 60, 60));
        assertEquals(Skill.MINING, plugin.getXpRates().shown());
        assertEquals(70, plugin.getXpRates().gained(Skill.MINING));

        // A hop keeps it, and waits for a tick again; the login screen clears it.
        plugin.onGameStateChanged(gameState(GameState.HOPPING));
        plugin.onGameStateChanged(gameState(GameState.LOGGED_IN));
        plugin.onStatChanged(new StatChanged(Skill.MINING, 50_105, 60, 60));
        assertEquals(70, plugin.getXpRates().gained(Skill.MINING));
        plugin.onGameStateChanged(gameState(GameState.LOGIN_SCREEN));
        assertNull(plugin.getXpRates().shown());

        // Off and on again, maybe on another account: it starts again.
        plugin.getXpRates().gain(Skill.MINING, 35, 0);
        setField("overlayManager", mock(OverlayManager.class));
        setField("mouseManager", mock(MouseManager.class));
        setField("clientToolbar", mock(ClientToolbar.class));
        setField("eventBus", mock(EventBus.class));
        plugin.shutDown();
        assertNull(plugin.getXpRates().shown());
    }

    // Tick pendulum

    @Test
    public void theTickPendulumCountsClicksOnlyWhileItsOn() throws Exception
    {
        setField("worldService", mock(WorldService.class));
        plugin.onGameTick(new GameTick());
        plugin.onMenuOptionClicked(click("Walk here"));
        assertEquals(0, plugin.getPendulum().run());

        when(config.tickPendulum()).thenReturn(true);
        when(config.pendulumRhythm()).thenReturn(TickPendulum.Rhythm.SINGLE);
        plugin.onGameTick(new GameTick());
        plugin.onMenuOptionClicked(click("Walk here"));
        assertEquals(1, plugin.getPendulum().run());
    }

    @Test
    public void prayerAndRunTogglesAreTheLabelledClicks()
    {
        assertTrue(ConstsExpPlugin.isLabelled("Activate"));
        assertTrue(ConstsExpPlugin.isLabelled("Deactivate"));
        assertTrue(ConstsExpPlugin.isLabelled("Toggle Run"));
        assertFalse(ConstsExpPlugin.isLabelled("Walk here"));
        assertFalse(ConstsExpPlugin.isLabelled("Chop down"));
    }

    private static MenuOptionClicked click(String option)
    {
        MenuEntry entry = mock(MenuEntry.class);
        when(entry.getOption()).thenReturn(option);
        return new MenuOptionClicked(entry);
    }

    // Presets

    @Test
    public void customPresetShouldFollowPerAlertToggles()
    {
        assertTrue(plugin.isAlertEnabled(AlertType.CARGO));
        assertFalse("Health toggle is off in this mock", plugin.isAlertEnabled(AlertType.HEALTH));

        when(config.enableCargoHoldAlert()).thenReturn(false);
        assertFalse(plugin.isAlertEnabled(AlertType.CARGO));
    }

    @Test
    public void salvagingAfkPresetShouldOnlyEnableCargoHold()
    {
        when(config.preset()).thenReturn(Preset.SALVAGING_AFK);
        when(config.enableCargoHoldAlert()).thenReturn(false);
        when(config.enableHealthAlert()).thenReturn(true);

        assertEquals(EnumSet.of(AlertType.CARGO), enabledAlerts());
    }

    @Test
    public void chinningPresetShouldBeHealthPrayerPotionAggroAndLogout()
    {
        when(config.preset()).thenReturn(Preset.CHINNING);

        assertEquals(EnumSet.of(AlertType.HEALTH, AlertType.PRAYER, AlertType.POTION, AlertType.AGGRO,
            AlertType.LOGOUT), enabledAlerts());
    }

    @Test
    public void salvagingActivePresetShouldBeInventoryFullAndSalvage()
    {
        when(config.preset()).thenReturn(Preset.SALVAGING_ACTIVE);

        assertEquals(EnumSet.of(AlertType.INVENTORY_FULL, AlertType.SALVAGE), enabledAlerts());
    }

    @Test
    public void crabbingPresetShouldCoverAggroWithoutPrayer()
    {
        when(config.preset()).thenReturn(Preset.CRABBING);

        assertEquals(EnumSet.of(AlertType.HEALTH, AlertType.IDLE, AlertType.AGGRO, AlertType.LOGOUT, AlertType.POTION),
            enabledAlerts());
    }

    @Test
    public void miningAndFishingPresetsShouldBeIdleAndInventoryFull()
    {
        Set<AlertType> skilling = EnumSet.of(AlertType.IDLE, AlertType.INVENTORY_FULL);

        when(config.preset()).thenReturn(Preset.MINING);
        assertEquals(skilling, enabledAlerts());

        when(config.preset()).thenReturn(Preset.FISHING);
        assertEquals(skilling, enabledAlerts());
    }

    @Test
    public void presetsShouldBeInDropdownOrder()
    {
        assertEquals("[Custom, Chinning, Crabbing, Mining, Woodcutting, Fishing, Salvaging Active, Salvaging AFK]",
            Arrays.toString(Preset.values()));
    }

    @Test
    public void woodcuttingPresetShouldBeIdleInventoryFullAndForestry()
    {
        when(config.preset()).thenReturn(Preset.WOODCUTTING);

        assertEquals(EnumSet.of(AlertType.IDLE, AlertType.INVENTORY_FULL, AlertType.FORESTRY), enabledAlerts());
    }

    @Test
    public void noPresetShouldIncludeRandomEvents()
    {
        for (Preset preset : Preset.values())
        {
            when(config.preset()).thenReturn(preset);
            if (preset != Preset.CUSTOM)
            {
                assertFalse(preset + " includes Random", plugin.isAlertEnabled(AlertType.RANDOM));
            }
        }
    }

    @Test
    public void presetsShouldHoldIdleWhileAnimatingAndCustomShouldAsk() throws Exception
    {
        when(config.idleThresholdMs()).thenReturn(6000);
        setField("lastActivityMillis", System.currentTimeMillis() - 60_000L);
        when(player.getAnimation()).thenReturn(867); // chopping

        when(config.preset()).thenReturn(Preset.WOODCUTTING);
        invokePrivate("evaluateAlerts");
        assertFalse("a preset holds Idle while you are still chopping", plugin.isAlertFiring(AlertType.IDLE));

        when(player.getAnimation()).thenReturn(-1);
        invokePrivate("evaluateAlerts");
        assertTrue("Idle fires once the animation stops", plugin.isAlertFiring(AlertType.IDLE));

        when(config.preset()).thenReturn(Preset.CUSTOM);
        when(config.enableIdleAlert()).thenReturn(true);
        when(config.idleRequireNoAnimation()).thenReturn(false);
        when(player.getAnimation()).thenReturn(867);
        invokePrivate("evaluateAlerts");
        assertTrue("Custom with the option off alerts mid-animation", plugin.isAlertFiring(AlertType.IDLE));
    }

    // The rules the status panel reads from the plugin

    @Test
    public void idleShouldBeHeldByAnAnimationWhereThePresetHoldsIt()
    {
        when(player.getAnimation()).thenReturn(867); // chopping
        when(config.preset()).thenReturn(Preset.WOODCUTTING);
        assertTrue("a preset holds Idle while you chop", plugin.isIdleHeldByAnimation());

        when(player.getAnimation()).thenReturn(-1);
        assertFalse("not animating, nothing holds it", plugin.isIdleHeldByAnimation());

        when(config.preset()).thenReturn(Preset.CUSTOM);
        when(config.idleRequireNoAnimation()).thenReturn(false);
        when(player.getAnimation()).thenReturn(867);
        assertFalse("Custom with Suppress While Animating off doesn't hold it", plugin.isIdleHeldByAnimation());
    }

    @Test
    public void logoutShouldWarnOnTheClockThePanelReads() throws Exception
    {
        // Warn After at 30 minutes, past the 25-minute logout.
        when(config.enableLogoutWarning()).thenReturn(true);
        when(config.logoutWarningMs()).thenReturn(1_800_000);
        assertEquals(1_800_000L, plugin.getLogoutWarningMillis());

        setField("lastUserInteractionMillis", System.currentTimeMillis() - 1_620_000L);
        long since = plugin.getMillisSinceUserInteraction();
        assertTrue("27 minutes since the last click, was " + since, since >= 1_620_000L && since < 1_630_000L);
        invokePrivate("evaluateAlerts");
        assertFalse("not warned yet at 27 of 30 minutes", plugin.isAlertFiring(AlertType.LOGOUT));

        setField("lastUserInteractionMillis", System.currentTimeMillis() - 1_800_000L);
        invokePrivate("evaluateAlerts");
        assertTrue("warned at 30 minutes", plugin.isAlertFiring(AlertType.LOGOUT));
    }

    @Test
    public void theClickPauseShouldStopTheScreenFlashButNotTheAlert() throws Exception
    {
        when(config.enableScreenFlash()).thenReturn(true);
        when(config.enableIdleAlert()).thenReturn(true);
        when(config.idleThresholdMs()).thenReturn(6000);
        when(player.getAnimation()).thenReturn(-1);
        setField("lastActivityMillis", System.currentTimeMillis() - 60_000L);
        invokePrivate("evaluateAlerts");
        assertTrue("Idle flashes the screen", plugin.isFlashingScreen(AlertType.IDLE, System.currentTimeMillis()));

        // Idle dismisses on click, so the Click Pause holds its flash; the alert itself carries on.
        invokePrivate("onUserInteraction");
        assertTrue("still firing, so still lit in the panel", plugin.isAlertFiring(AlertType.IDLE));
        assertFalse("not flashing the screen", plugin.isFlashingScreen(AlertType.IDLE, System.currentTimeMillis()));
    }

    @Test
    public void thePanelsPulseShouldBeTheScreenFlashsPhase()
    {
        // A hand-edited 60 ms interval: the screen flash takes it as it is (its floor is 50), so the panel does too.
        when(config.flashIntervalMs()).thenReturn(60);
        for (long now = 0; now < 480; now += 7)
        {
            assertEquals("at " + now + " ms", AlertType.IDLE.isFlashPhaseOn(now, 60),
                plugin.isFlashPhaseOn(AlertType.IDLE, now));
        }
    }

    @Test
    public void leavingCustomShouldSaveItsToggles()
    {
        when(configManager.getConfiguration(ConstsExpConfig.GROUP, "enableHealthAlert")).thenReturn("false");
        when(configManager.getConfiguration(ConstsExpConfig.GROUP, "enableCannonAlert")).thenReturn("true");

        plugin.onConfigChanged(presetChange("CUSTOM", "WOODCUTTING"));

        ArgumentCaptor<String> saved = ArgumentCaptor.forClass(String.class);
        verify(configManager).setConfiguration(eq(ConstsExpConfig.GROUP), eq(ConstsExpConfig.CUSTOM_SNAPSHOT_KEY),
            saved.capture());
        assertTrue(saved.getValue().contains("enableHealthAlert=false"));
        assertTrue(saved.getValue().contains("enableCannonAlert=true"));
        assertTrue("never set is saved as default", saved.getValue().contains("enableIdleAlert=;"));
    }

    @Test
    public void returningToCustomShouldRestoreItsToggles()
    {
        when(configManager.getConfiguration(ConstsExpConfig.GROUP, ConstsExpConfig.CUSTOM_SNAPSHOT_KEY))
            .thenReturn("enableHealthAlert=false;enableCannonAlert=;notAToggle=true");

        plugin.onConfigChanged(presetChange("WOODCUTTING", "CUSTOM"));

        verify(configManager).setConfiguration(ConstsExpConfig.GROUP, "enableHealthAlert", "false");
        verify(configManager).unsetConfiguration(ConstsExpConfig.GROUP, "enableCannonAlert");
        verify(configManager, never()).setConfiguration(ConstsExpConfig.GROUP, "notAToggle", "true");
    }

    @Test
    public void resetShouldPutEveryCustomToggleBackEvenWhenThePresetResetsLast()
    {
        // The saved settings, after: in Custom, Cannon off, then Woodcutting.
        Map<String, String> stored = new HashMap<>();
        stored.put(ConstsExpConfig.PRESET_KEY, "WOODCUTTING");
        stored.put("enableCannonAlert", "false");
        stored.put(ConstsExpConfig.CUSTOM_SNAPSHOT_KEY, "enableCannonAlert=false");
        when(configManager.getConfiguration(eq(ConstsExpConfig.GROUP), anyString()))
            .thenAnswer(invocation -> stored.get(invocation.<String>getArgument(1)));
        doAnswer(invocation -> stored.put(invocation.getArgument(1), invocation.getArgument(2)))
            .when(configManager).setConfiguration(eq(ConstsExpConfig.GROUP), anyString(), anyString());
        doAnswer(invocation -> stored.remove(invocation.<String>getArgument(1)))
            .when(configManager).unsetConfiguration(eq(ConstsExpConfig.GROUP), anyString());

        // Reset unsets each setting, here in the bad order: Cannon first, then the preset, whose return to Custom brings
        // Custom's Cannon off back.
        stored.remove("enableCannonAlert");
        stored.remove(ConstsExpConfig.PRESET_KEY);
        plugin.onConfigChanged(presetChange("WOODCUTTING", null));
        plugin.resetConfiguration();

        assertNull("Cannon is back to its default", stored.get("enableCannonAlert"));
        assertNull("the saved Custom goes too", stored.get(ConstsExpConfig.CUSTOM_SNAPSHOT_KEY));
    }

    @Test
    public void presetToPresetShouldLeaveCustomAlone()
    {
        plugin.onConfigChanged(presetChange("WOODCUTTING", "MINING"));

        verify(configManager, never()).setConfiguration(eq(ConstsExpConfig.GROUP),
            eq(ConstsExpConfig.CUSTOM_SNAPSHOT_KEY), any(String.class));
        verify(configManager, never()).getConfiguration(ConstsExpConfig.GROUP, ConstsExpConfig.CUSTOM_SNAPSHOT_KEY);
    }

    @Test
    public void everyAlertsOnOffShouldBePartOfCustom()
    {
        for (java.lang.reflect.Method method : ConstsExpConfig.class.getDeclaredMethods())
        {
            ConfigItem item = method.getAnnotation(ConfigItem.class);
            if (item != null && item.keyName().startsWith("enable") && !item.keyName().equals("enableAllSoundAlerts")
                && !item.keyName().equals("enableScreenFlash"))
            {
                assertTrue(item.keyName(), ConstsExpPlugin.CUSTOM_TOGGLE_KEYS.contains(item.keyName()));
            }
        }
        assertEquals(AlertType.values().length, ConstsExpPlugin.CUSTOM_TOGGLE_KEYS.size());
    }

    @Test
    public void unreadablePresetShouldFallBackToCustom()
    {
        when(config.preset()).thenReturn(null);
        assertTrue(plugin.isAlertEnabled(AlertType.CARGO));
    }

    @Test
    public void applyPresetShouldPersistSelection()
    {
        plugin.applyPreset(Preset.SALVAGING_AFK);
        verify(configManager).setConfiguration(ConstsExpConfig.GROUP, ConstsExpConfig.PRESET_KEY, Preset.SALVAGING_AFK);
    }

    // Chat triggers

    @Test
    public void cargoHoldFullMessageShouldTriggerCargoAlert()
    {
        assertEquals(EnumSet.of(AlertType.CARGO), ConstsExpPlugin.matchChatTriggers(CARGO_FULL_MESSAGE));
        assertEquals(EnumSet.of(AlertType.CARGO), ConstsExpPlugin.matchChatTriggers("<col=ef1020>Your cargo hold is full.</col>"));
    }

    @Test
    public void chatTriggersShouldMatchEachBurstAlert()
    {
        assertEquals(EnumSet.of(AlertType.SALVAGE),
            ConstsExpPlugin.matchChatTriggers("You salvage all you can from the shipwreck before it is reclaimed by the sea."));
        assertEquals(EnumSet.of(AlertType.SALVAGE),
            ConstsExpPlugin.matchChatTriggers("You have no more salvage to sort."));
        assertEquals(EnumSet.of(AlertType.INVENTORY_FULL),
            ConstsExpPlugin.matchChatTriggers("Your inventory is too full to hold any more logs."));
        assertEquals(EnumSet.of(AlertType.CANNON),
            ConstsExpPlugin.matchChatTriggers("Your cannon is out of ammo!"));
        assertEquals(EnumSet.of(AlertType.POTION),
            ConstsExpPlugin.matchChatTriggers("<col=ff0000>Your divine ranging potion is about to expire.</col>"));
    }

    @Test
    public void chatTriggersShouldIgnoreUnrelatedMessages()
    {
        assertTrue(ConstsExpPlugin.matchChatTriggers("You drink some of your super combat potion.").isEmpty());
        assertTrue(ConstsExpPlugin.matchChatTriggers("Welcome to Old School RuneScape.").isEmpty());
        assertTrue(ConstsExpPlugin.matchChatTriggers(null).isEmpty());
    }

    @Test
    public void randomEventMatcherShouldDetectKnownNpcNames()
    {
        assertTrue(ConstsExpPlugin.matchesRandomEventNpcName("Genie"));
        assertTrue(ConstsExpPlugin.matchesRandomEventNpcName("Sandwich lady"));
        assertTrue(ConstsExpPlugin.matchesRandomEventNpcName("<col=ffff00>Freaky Forester</col>"));
        assertTrue(ConstsExpPlugin.matchesRandomEventNpcName("Mr. Mordaut"));
        assertFalse(ConstsExpPlugin.matchesRandomEventNpcName("Banker"));
        assertFalse(ConstsExpPlugin.matchesRandomEventNpcName(null));
    }

    // Bursts

    @Test
    public void cargoHoldMessageShouldFireUntilClicked() throws Exception
    {
        plugin.onChatMessage(chat(ChatMessageType.SPAM, CARGO_FULL_MESSAGE));
        awaitTrue("cargo alert should start firing", () -> plugin.isAlertFiring(AlertType.CARGO));

        invokePrivate("onUserInteraction");

        assertFalse("a click should acknowledge the cargo alert", plugin.isAlertFiring(AlertType.CARGO));
    }

    @Test
    public void disabledAlertShouldIgnoreItsChatTrigger() throws Exception
    {
        when(config.preset()).thenReturn(Preset.SALVAGING_AFK);

        plugin.onChatMessage(chat(ChatMessageType.GAMEMESSAGE, "Your super attack potion is about to expire."));
        Thread.sleep(100);

        assertFalse(plugin.isAlertFiring(AlertType.POTION));
    }

    @Test
    public void publicChatShouldNotTriggerAlerts() throws Exception
    {
        plugin.onChatMessage(chat(ChatMessageType.PUBLICCHAT, CARGO_FULL_MESSAGE));
        Thread.sleep(100);

        assertFalse(plugin.isAlertFiring(AlertType.CARGO));
    }

    @Test
    public void alertWithDismissOnClickOffShouldSurviveClicks() throws Exception
    {
        when(config.potionDismissOnClick()).thenReturn(false);

        plugin.onChatMessage(chat(ChatMessageType.GAMEMESSAGE, "Your stamina potion is about to expire."));
        awaitTrue("potion alert should start firing", () -> plugin.isAlertFiring(AlertType.POTION));

        invokePrivate("onUserInteraction");

        assertTrue("with Dismiss on Click off, a click doesn't cancel it", plugin.isAlertFiring(AlertType.POTION));
    }

    @Test
    public void dismissedAlertShouldStayQuietWhenItsMessageRepeats() throws Exception
    {
        when(config.quietAfterDismissSeconds()).thenReturn(60);
        when(config.interactionPauseMs()).thenReturn(0);

        plugin.onChatMessage(chat(ChatMessageType.SPAM, CARGO_FULL_MESSAGE));
        awaitTrue("cargo alert should start firing", () -> plugin.isAlertFiring(AlertType.CARGO));
        invokePrivate("onUserInteraction");

        plugin.onChatMessage(chat(ChatMessageType.SPAM, CARGO_FULL_MESSAGE));
        Thread.sleep(150);

        assertFalse("the same message within Quiet After Dismiss is ignored", plugin.isAlertFiring(AlertType.CARGO));
    }

    @Test
    public void aClickShouldNotQuietAnAlertThatWasNotSounding() throws Exception
    {
        when(config.quietAfterDismissSeconds()).thenReturn(60);
        when(config.interactionPauseMs()).thenReturn(0);

        invokePrivate("onUserInteraction");
        plugin.onChatMessage(chat(ChatMessageType.SPAM, CARGO_FULL_MESSAGE));

        awaitTrue("clicking around beforehand doesn't snooze the alert", () -> plugin.isAlertFiring(AlertType.CARGO));
    }

    @Test
    public void loadingScreenShouldKeepBurstsButLogoutShouldClearThem() throws Exception
    {
        plugin.onChatMessage(chat(ChatMessageType.SPAM, CARGO_FULL_MESSAGE));
        awaitTrue("cargo alert should start firing", () -> plugin.isAlertFiring(AlertType.CARGO));

        plugin.onGameStateChanged(gameState(GameState.LOADING));
        assertTrue("a loading screen is not a logout", plugin.isAlertFiring(AlertType.CARGO));

        plugin.onGameStateChanged(gameState(GameState.LOGIN_SCREEN));
        assertFalse(plugin.isAlertFiring(AlertType.CARGO));
    }

    // Forestry

    @Test
    public void forestryMarkersShouldMapToTheirEvents()
    {
        assertEquals(ForestryEvent.LEPRECHAUN, ForestryEvent.forNpc(NpcID.GATHERING_EVENT_WOODCUTTING_LEPRECHAUN));
        assertEquals(ForestryEvent.PHEASANT_CONTROL, ForestryEvent.forNpc(NpcID.GATHERING_EVENT_PHEASANT_FORESTER));
        assertEquals(ForestryEvent.FLOWERING_TREE, ForestryEvent.forNpc(NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL05));
        assertEquals(ForestryEvent.RISING_ROOTS, ForestryEvent.forObject(ObjectID.GATHERING_EVENT_RISING_ROOTS_SPECIAL));
        assertEquals(ForestryEvent.STRUGGLING_SAPLING, ForestryEvent.forObject(ObjectID.GATHERING_EVENT_SAPLING_WITHERING_2X2));
        assertNull("a saved sapling means the event is over", ForestryEvent.forObject(ObjectID.GATHERING_EVENT_SAPLING_SAVED_2X2));
        assertNull("the trap is not what announces Poachers", ForestryEvent.forNpc(NpcID.GATHERING_EVENT_POACHERS_TRAP));
    }

    @Test
    public void forestryEventShouldFireAndNameItself() throws Exception
    {
        pickAllForestryEvents();

        plugin.onNpcSpawned(new NpcSpawned(npc(NpcID.GATHERING_EVENT_WOODCUTTING_LEPRECHAUN, "Leprechaun")));

        awaitTrue("forestry alert should start firing", () -> plugin.isAlertFiring(AlertType.FORESTRY));
        assertEquals(ForestryEvent.LEPRECHAUN, plugin.getLastForestryEvent());
    }

    @Test
    public void unpickedForestryEventShouldNotFire() throws Exception
    {
        pickAllForestryEvents();
        when(config.forestryLeprechaun()).thenReturn(false);

        plugin.onNpcSpawned(new NpcSpawned(npc(NpcID.GATHERING_EVENT_WOODCUTTING_LEPRECHAUN, "Leprechaun")));
        Thread.sleep(100);

        assertFalse(plugin.isAlertFiring(AlertType.FORESTRY));
    }

    @Test
    public void forestryEventShouldFireOnceForAllItsMarkers() throws Exception
    {
        pickAllForestryEvents();
        when(config.interactionPauseMs()).thenReturn(0);

        plugin.onNpcSpawned(new NpcSpawned(npc(NpcID.GATHERING_EVENT_ENTLINGS_NPC_01, "Entling")));
        awaitTrue("forestry alert should start firing", () -> plugin.isAlertFiring(AlertType.FORESTRY));
        invokePrivate("onUserInteraction");

        plugin.onNpcSpawned(new NpcSpawned(npc(NpcID.GATHERING_EVENT_ENTLINGS_NPC_01, "Entling")));
        Thread.sleep(100);

        assertFalse("a second entling is the same Friendly Ent event", plugin.isAlertFiring(AlertType.FORESTRY));
    }

    @Test
    public void witheringSaplingShouldNotRefireItsEvent() throws Exception
    {
        pickAllForestryEvents();
        when(config.interactionPauseMs()).thenReturn(0);

        plugin.onGameObjectSpawned(objectSpawned(ObjectID.GATHERING_EVENT_SAPLING_2X2));
        awaitTrue("forestry alert should start firing", () -> plugin.isAlertFiring(AlertType.FORESTRY));
        invokePrivate("onUserInteraction");

        plugin.onGameObjectDespawned(objectDespawned(ObjectID.GATHERING_EVENT_SAPLING_2X2));
        plugin.onGameObjectSpawned(objectSpawned(ObjectID.GATHERING_EVENT_SAPLING_WITHERING_2X2));
        Thread.sleep(100);

        assertFalse("the same sapling changing state is not a new event", plugin.isAlertFiring(AlertType.FORESTRY));
    }

    @Test
    public void loadingScreenShouldNotRefireTheSameForestryEvent() throws Exception
    {
        pickAllForestryEvents();
        when(config.interactionPauseMs()).thenReturn(0);
        NPC dryad = npc(NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_DRYAD, "Dryad");

        plugin.onNpcSpawned(new NpcSpawned(dryad));
        awaitTrue("forestry alert should start firing", () -> plugin.isAlertFiring(AlertType.FORESTRY));
        invokePrivate("onUserInteraction");

        plugin.onGameStateChanged(gameState(GameState.LOADING));
        plugin.onNpcSpawned(new NpcSpawned(dryad));
        Thread.sleep(100);

        assertFalse("the ritual seen again after a load is the same event", plugin.isAlertFiring(AlertType.FORESTRY));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void forestryEventShouldRefireOnceItHasBeenGoneAWhile() throws Exception
    {
        pickAllForestryEvents();
        when(config.interactionPauseMs()).thenReturn(0);

        plugin.onGameObjectSpawned(objectSpawned(ObjectID.GATHERING_EVENT_RISING_ROOTS));
        awaitTrue("forestry alert should start firing", () -> plugin.isAlertFiring(AlertType.FORESTRY));
        invokePrivate("onUserInteraction");
        plugin.onGameObjectDespawned(objectDespawned(ObjectID.GATHERING_EVENT_RISING_ROOTS));

        Map<ForestryEvent, Long> goneSince = (Map<ForestryEvent, Long>) getField("forestryGoneSince");
        goneSince.put(ForestryEvent.RISING_ROOTS, System.currentTimeMillis() - ConstsExpPlugin.FORESTRY_REARM_MS - 1L);
        plugin.onGameObjectSpawned(objectSpawned(ObjectID.GATHERING_EVENT_RISING_ROOTS));

        awaitTrue("roots after a long gap are a new event", () -> plugin.isAlertFiring(AlertType.FORESTRY));
    }

    @Test
    public void pheasantControlForesterShouldNotCountAsARandomEvent() throws Exception
    {
        // Custom, so both alerts are on: Random is in no preset.
        when(config.enableRandomEventAlert()).thenReturn(true);
        pickAllForestryEvents();

        NPC forester = npc(NpcID.GATHERING_EVENT_PHEASANT_FORESTER, "Freaky Forester");
        plugin.onNpcSpawned(new NpcSpawned(forester));
        plugin.onInteractingChanged(new InteractingChanged(forester, player));

        awaitTrue("Pheasant Control should raise the forestry alert", () -> plugin.isAlertFiring(AlertType.FORESTRY));
        assertFalse("its Freaky Forester is not the random event", plugin.isAlertFiring(AlertType.RANDOM));
    }

    @Test
    public void forestryNpcsShouldLeaveOtherNpcsToTheRandomAlert()
    {
        assertNull(ForestryEvent.forNpc(0));
        assertTrue(ConstsExpPlugin.matchesRandomEventNpcName("Freaky Forester"));
    }

    // Random events

    @Test
    public void yourRandomEventShouldFire() throws Exception
    {
        when(config.enableRandomEventAlert()).thenReturn(true);

        plugin.onInteractingChanged(new InteractingChanged(npc(1, "Genie"), player));

        awaitTrue("a random targeting you is yours", () -> plugin.isAlertFiring(AlertType.RANDOM));
    }

    @Test
    public void someoneElsesRandomEventShouldNotFire() throws Exception
    {
        when(config.enableRandomEventAlert()).thenReturn(true);
        NPC genie = npc(1, "Genie");

        plugin.onNpcSpawned(new NpcSpawned(genie));
        plugin.onInteractingChanged(new InteractingChanged(genie, mock(Player.class)));
        Thread.sleep(100);

        assertFalse("a random targeting another player is theirs", plugin.isAlertFiring(AlertType.RANDOM));
    }

    @Test
    public void talkingToSomeoneElsesRandomEventShouldNotFire() throws Exception
    {
        when(config.enableRandomEventAlert()).thenReturn(true);
        NPC genie = npc(1, "Genie");
        when(player.getInteracting()).thenReturn(genie);

        plugin.onInteractingChanged(new InteractingChanged(genie, player));
        Thread.sleep(100);

        assertFalse("it faces you because you clicked it, not because it is yours", plugin.isAlertFiring(AlertType.RANDOM));
    }

    // Sounds

    @Test
    public void gameSoundShouldPlayAtTheChosenVolume() throws Exception
    {
        runOnClientThread();
        when(config.enableAllSoundAlerts()).thenReturn(true);
        when(config.alertSound()).thenReturn(AlertSound.COINS);
        when(config.soundVolume()).thenReturn(96);

        invokePrivate("beep");

        verify(client).playSoundEffect(SoundEffectID.GE_COIN_TINKLE, 96);
    }

    @Test
    public void customSoundShouldPlayItsIdWithTheVolumeKeptInRange() throws Exception
    {
        runOnClientThread();
        when(config.enableAllSoundAlerts()).thenReturn(true);
        when(config.alertSound()).thenReturn(AlertSound.CUSTOM);
        when(config.customSoundId()).thenReturn(3813);
        when(config.soundVolume()).thenReturn(500);

        invokePrivate("beep");

        verify(client).playSoundEffect(3813, 127);
    }

    @Test
    public void systemBeepShouldNotTouchTheGamesSounds() throws Exception
    {
        runOnClientThread();
        when(config.enableAllSoundAlerts()).thenReturn(true);
        when(config.alertSound()).thenReturn(AlertSound.SYSTEM_BEEP);

        invokePrivate("beep");

        verify(client, never()).playSoundEffect(anyInt(), anyInt());
    }

    @Test
    public void pickingASoundShouldPlayItOnce() throws Exception
    {
        runOnClientThread();
        when(config.alertSound()).thenReturn(AlertSound.TOWN_CRIER_BELL);
        when(config.soundVolume()).thenReturn(96);
        ConfigChanged changed = new ConfigChanged();
        changed.setGroup(ConstsExpConfig.GROUP);
        changed.setKey(ConstsExpConfig.ALERT_SOUND_KEY);

        plugin.onConfigChanged(changed);

        verify(client).playSoundEffect(SoundEffectID.TOWN_CRIER_BELL_DING, 96);
    }

    // Activity tracking

    @Test
    public void hybridSignalsShouldMarkActivityOnMovement() throws Exception
    {
        setField("lastPlayerWorldPoint", new WorldPoint(3200, 3200, 0));
        setField("lastActivityMillis", 1L);
        when(player.getWorldLocation()).thenReturn(new WorldPoint(3201, 3200, 0));
        when(player.getAnimation()).thenReturn(-1);

        invokePrivate("updateActivityFromHybridSignals");

        assertTrue((long) getField("lastActivityMillis") > 1L);
    }

    @Test
    public void hybridSignalsShouldMarkActivityOnNewAnimation() throws Exception
    {
        setField("lastPlayerWorldPoint", new WorldPoint(3200, 3200, 0));
        setField("lastPlayerAnimation", -1);
        setField("lastActivityMillis", 1L);
        when(player.getAnimation()).thenReturn(1234);

        invokePrivate("updateActivityFromHybridSignals");

        assertTrue((long) getField("lastActivityMillis") > 1L);
    }

    @Test
    public void hybridSignalsShouldNotMarkActivityForStaleAnimationAlone() throws Exception
    {
        setField("lastPlayerWorldPoint", new WorldPoint(3200, 3200, 0));
        setField("lastPlayerAnimation", 1234);
        setField("lastActivityMillis", 123456L);
        when(player.getAnimation()).thenReturn(1234);

        invokePrivate("updateActivityFromHybridSignals");

        assertEquals(123456L, getField("lastActivityMillis"));
    }

    @Test
    public void hybridSignalsShouldMarkActivityOnInteractingTarget() throws Exception
    {
        Actor target = mock(Actor.class);
        setField("lastPlayerWorldPoint", new WorldPoint(3200, 3200, 0));
        setField("lastActivityMillis", 1L);
        when(player.getAnimation()).thenReturn(-1);
        when(player.getInteracting()).thenReturn(target);

        invokePrivate("updateActivityFromHybridSignals");

        assertTrue((long) getField("lastActivityMillis") > 1L);
    }

    @Test
    public void hybridSignalsShouldNotMarkActivityWhenNoSignals() throws Exception
    {
        setField("lastPlayerWorldPoint", new WorldPoint(3200, 3200, 0));
        setField("lastActivityMillis", 123456L);
        when(player.getAnimation()).thenReturn(-1);

        invokePrivate("updateActivityFromHybridSignals");

        assertEquals(123456L, getField("lastActivityMillis"));
    }

    // Aggro timer

    @Test
    public void aggroIntervalShouldBeTenMinutesInOsrsMode()
    {
        when(config.timerMode()).thenReturn(TimerMode.OSRS_AGGRO_10_MIN);
        assertEquals(TimeUnit.MINUTES.toMillis(10L), plugin.getAggroIntervalMillis());
    }

    @Test
    public void aggroIntervalShouldUseCustomMinutesInCustomMode()
    {
        when(config.timerMode()).thenReturn(TimerMode.CUSTOM_MINUTES);
        when(config.timerMinutes()).thenReturn(7);
        assertEquals(TimeUnit.MINUTES.toMillis(7L), plugin.getAggroIntervalMillis());
    }

    @Test
    public void expiredAggroShouldWaitForResetInOsrsMode() throws Exception
    {
        when(config.timerMode()).thenReturn(TimerMode.OSRS_AGGRO_10_MIN);
        long expiredAt = System.currentTimeMillis() - 1000L;
        aggroArea().reset(new WorldPoint(3200, 3200, 0), expiredAt - AggroArea.AGGRESSIVE_TIME_MS);

        invokePrivate("evaluateAggroTimer");

        assertTrue((boolean) getField("aggroExpiredAwaitingReset"));
        assertTrue(plugin.isAlertFiring(AlertType.AGGRO));

        invokePrivate("evaluateAggroTimer");
        assertEquals("evaluating must not restart an expired timer", expiredAt, plugin.getNextAggroAlertMillis());
        assertTrue(plugin.isAlertFiring(AlertType.AGGRO));
    }

    @Test
    public void expiredAggroShouldStayAlertingWhileMoving() throws Exception
    {
        when(config.timerMode()).thenReturn(TimerMode.OSRS_AGGRO_10_MIN);
        setField("playerMovingThisTick", true);

        invokePrivate("onAggroTimerDue");

        assertFalse((boolean) getField("aggroAlertPending"));
        assertTrue((boolean) getField("aggroExpiredAwaitingReset"));
        assertTrue(plugin.isAlertFiring(AlertType.AGGRO));
    }

    @Test
    public void leavingBothAreasShouldEndTheAlertAndRestartTheTimer() throws Exception
    {
        when(config.timerMode()).thenReturn(TimerMode.OSRS_AGGRO_10_MIN);
        WorldPoint older = new WorldPoint(3200, 3200, 0);
        WorldPoint newer = new WorldPoint(3215, 3200, 0);
        WorldPoint outsideBoth = new WorldPoint(3207, 3215, 0);
        long longAgo = System.currentTimeMillis() - AggroArea.AGGRESSIVE_TIME_MS - 1000L;
        aggroArea().reset(older, longAgo);
        aggroArea().reset(newer, longAgo);
        invokePrivate("evaluateAggroTimer");
        assertTrue(plugin.isAlertFiring(AlertType.AGGRO));

        when(player.getWorldLocation()).thenReturn(outsideBoth);
        invokePrivate("updateAggroArea");

        assertArrayEquals(new WorldPoint[]{newer, outsideBoth}, aggroArea().getSafeCenters());
        assertFalse((boolean) getField("aggroExpiredAwaitingReset"));
        assertFalse(plugin.isAlertFiring(AlertType.AGGRO));
        assertTrue("timer should restart",
            plugin.getNextAggroAlertMillis() > System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(9L));
    }

    @Test
    public void walkingWithinEitherAreaShouldKeepTheTimer() throws Exception
    {
        when(config.timerMode()).thenReturn(TimerMode.OSRS_AGGRO_10_MIN);
        aggroArea().reset(new WorldPoint(3200, 3200, 0), 1234L);
        aggroArea().reset(new WorldPoint(3215, 3200, 0), 1234L);
        long endMillis = plugin.getNextAggroAlertMillis();

        // Left the newer area, still inside the older one: 25 tiles from where the timer started, and no reset.
        when(player.getWorldLocation()).thenReturn(new WorldPoint(3190, 3200, 0));
        invokePrivate("updateAggroArea");

        assertEquals(endMillis, plugin.getNextAggroAlertMillis());
    }

    @Test
    public void resetAggroTimerShouldStartAFreshCenterWhereYouStand() throws Exception
    {
        when(config.timerMode()).thenReturn(TimerMode.OSRS_AGGRO_10_MIN);
        WorldPoint here = new WorldPoint(3200, 3200, 0);
        when(player.getWorldLocation()).thenReturn(here);
        invokePrivate("onAggroTimerDue");

        plugin.resetAggroTimer();

        assertEquals(here, aggroArea().getSafeCenters()[1]);
        assertFalse(plugin.isAlertFiring(AlertType.AGGRO));
        assertTrue(plugin.getNextAggroAlertMillis() > System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(9L));
    }

    @Test
    public void customMinutesShouldKeepItsIntervalWhateverTheArea() throws Exception
    {
        when(config.timerMode()).thenReturn(TimerMode.CUSTOM_MINUTES);
        when(config.timerMinutes()).thenReturn(7);

        invokePrivate("evaluateAggroTimer");
        long nextAlert = plugin.getNextAggroAlertMillis();
        assertTrue(nextAlert > System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(6L));

        // A teleport moves the area; the interval doesn't care.
        when(player.getWorldLocation()).thenReturn(new WorldPoint(3200, 3200, 0));
        invokePrivate("updateAggroArea");
        when(player.getWorldLocation()).thenReturn(new WorldPoint(3300, 3300, 0));
        invokePrivate("updateAggroArea");

        assertTrue(aggroArea().hasArea());
        assertEquals(nextAlert, plugin.getNextAggroAlertMillis());

        plugin.resetAggroTimer();
        assertEquals("a reset leaves the area alone", new WorldPoint(3300, 3300, 0), aggroArea().getSafeCenters()[1]);
        assertTrue(plugin.getNextAggroAlertMillis() >= nextAlert);
    }

    @Test
    public void areaLinesShouldShowOnlyWhileOsrsAggroIsOn()
    {
        when(config.showAggroAreaLines()).thenReturn(true);
        when(config.timerMode()).thenReturn(TimerMode.OSRS_AGGRO_10_MIN);
        assertTrue(plugin.shouldShowAggroAreaLines());

        when(config.preset()).thenReturn(Preset.MINING);
        assertFalse("Mining has no Aggro", plugin.shouldShowAggroAreaLines());

        when(config.preset()).thenReturn(Preset.CRABBING);
        assertTrue(plugin.shouldShowAggroAreaLines());

        when(config.timerMode()).thenReturn(TimerMode.CUSTOM_MINUTES);
        assertFalse("a custom interval has no area", plugin.shouldShowAggroAreaLines());

        when(config.timerMode()).thenReturn(TimerMode.OSRS_AGGRO_10_MIN);
        when(config.showAggroAreaLines()).thenReturn(false);
        assertFalse(plugin.shouldShowAggroAreaLines());
    }

    @Test
    public void aggroAreaShouldSurviveARelog() throws Exception
    {
        Map<String, Object> saved = new HashMap<>();
        backProfileConfigWith(saved);
        when(config.timerMode()).thenReturn(TimerMode.OSRS_AGGRO_10_MIN);
        WorldPoint older = new WorldPoint(3200, 3200, 0);
        WorldPoint newer = new WorldPoint(3215, 3200, 0);
        when(player.getWorldLocation()).thenReturn(older);
        plugin.resetAggroTimer();
        when(player.getWorldLocation()).thenReturn(newer);
        plugin.resetAggroTimer();
        long endMillis = plugin.getNextAggroAlertMillis();

        plugin.onGameStateChanged(gameState(GameState.HOPPING));
        assertTrue("HOPPING alone leaves the area alone", aggroArea().isCalibrated());

        plugin.onGameStateChanged(gameState(GameState.LOGIN_SCREEN));
        assertFalse(aggroArea().hasArea());
        assertEquals(4, saved.size());

        plugin.onGameStateChanged(gameState(GameState.LOGGING_IN));
        plugin.onGameStateChanged(gameState(GameState.LOGGED_IN));

        assertArrayEquals(new WorldPoint[]{older, newer}, aggroArea().getSafeCenters());
        long restoredEnd = plugin.getNextAggroAlertMillis();
        assertTrue("the time left carries over", restoredEnd >= endMillis && restoredEnd <= endMillis + 5000L);
    }

    @Test
    public void savingTheAggroAreaShouldNotCountAsASettingsChange() throws Exception
    {
        when(config.timerMode()).thenReturn(TimerMode.OSRS_AGGRO_10_MIN);
        invokePrivate("onAggroTimerDue");
        ConfigChanged changed = new ConfigChanged();
        changed.setGroup(ConstsExpConfig.GROUP);
        changed.setProfile("rsprofile.test");
        changed.setKey(AggroArea.CENTER1_KEY);

        plugin.onConfigChanged(changed);

        assertTrue("a settings change would have reset the alert", plugin.isAlertFiring(AlertType.AGGRO));
    }

    // Interaction and logout

    @Test
    public void interactionShouldResetLogoutTimerEvenWithNoPauseWindow() throws Exception
    {
        when(config.interactionPauseMs()).thenReturn(0);
        setField("lastUserInteractionMillis", 1L);

        invokePrivate("onUserInteraction");

        assertTrue((long) getField("lastUserInteractionMillis") > 1L);
        assertEquals(0L, plugin.getPauseRemainingMillis());
    }

    @Test
    public void keyPressShouldResetLogoutTimer() throws Exception
    {
        long oldInteraction = System.currentTimeMillis() - 120_000L;
        setField("lastUserInteractionMillis", oldInteraction);
        setField("lastKeyboardIdleTicks", 40);
        when(client.getKeyboardIdleTicks()).thenReturn(0);

        plugin.onClientTick(null);

        assertTrue((long) getField("lastUserInteractionMillis") > oldInteraction);
    }

    @Test
    public void idleKeyboardShouldNotResetLogoutTimer() throws Exception
    {
        long oldInteraction = System.currentTimeMillis() - 120_000L;
        setField("lastUserInteractionMillis", oldInteraction);
        setField("lastKeyboardIdleTicks", 10);
        when(client.getKeyboardIdleTicks()).thenReturn(11);

        plugin.onClientTick(null);

        assertEquals(oldInteraction, getField("lastUserInteractionMillis"));
    }

    @Test
    public void logoutTimerShouldStartNearTwentyFiveMinutes() throws Exception
    {
        setField("lastUserInteractionMillis", System.currentTimeMillis());

        long remaining = plugin.getLogoutTimerRemainingMillis();

        assertTrue(remaining >= TimeUnit.MINUTES.toMillis(24L) + TimeUnit.SECONDS.toMillis(55L));
        assertTrue(remaining <= TimeUnit.MINUTES.toMillis(25L));
    }

    @Test
    public void logoutTimerShouldClampToZero() throws Exception
    {
        setField("lastUserInteractionMillis", System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(26L));
        assertEquals(0L, plugin.getLogoutTimerRemainingMillis());
    }

    // Master Switch

    @Test
    public void masterSwitchOffEndsTheSessionAndLoggingInDoesNotStartOne() throws Exception
    {
        runClientThreadNow();
        when(config.masterSwitch()).thenReturn(false);

        plugin.onConfigChanged(masterSwitchChange("true", "false"));
        assertFalse((boolean) getField("sessionActive"));

        plugin.onGameStateChanged(gameState(GameState.LOGIN_SCREEN));
        plugin.onGameStateChanged(gameState(GameState.LOGGED_IN));
        assertFalse((boolean) getField("sessionActive"));
        assertTrue(plugin.getCurrentFlashColors().isEmpty());
        assertFalse(plugin.shouldShowAggroAreaLines());
    }

    @Test
    public void masterSwitchOnStartsAFreshSession() throws Exception
    {
        runClientThreadNow();
        setField("sessionActive", false);
        setField("lastActivityMillis", 1L);

        plugin.onConfigChanged(masterSwitchChange("false", "true"));

        assertTrue((boolean) getField("sessionActive"));
        // Idle counts from now, not from before the switch went off.
        assertTrue((long) getField("lastActivityMillis") > 1L);
    }

    // GOTR Autopilot

    @Test
    public void gotrAutopilotRunsOnlyWhileItsSwitchAndTheMasterSwitchAreOn() throws Exception
    {
        GotrAutopilot autopilot = (GotrAutopilot) getField("gotrAutopilot");

        when(config.gotrAutopilot()).thenReturn(true);
        plugin.onConfigChanged(gotrAutopilotChange("false", "true"));
        verify(autopilot).setRunning(true);

        when(config.masterSwitch()).thenReturn(false);
        plugin.onConfigChanged(masterSwitchChange("true", "false"));
        verify(autopilot).setRunning(false);

        when(config.masterSwitch()).thenReturn(true);
        when(config.gotrAutopilot()).thenReturn(false);
        plugin.onConfigChanged(gotrAutopilotChange("true", "false"));
        verify(autopilot, times(2)).setRunning(false);
    }

    @Test
    public void gotrAutopilotStartsWithThePluginAndStopsWithIt() throws Exception
    {
        GotrAutopilot autopilot = (GotrAutopilot) getField("gotrAutopilot");
        when(config.gotrAutopilot()).thenReturn(true);
        setField("overlayManager", mock(OverlayManager.class));
        setField("mouseManager", mock(MouseManager.class));
        setField("clientToolbar", mock(ClientToolbar.class));
        setField("eventBus", mock(EventBus.class));
        setField("colorPickerManager", mock(ColorPickerManager.class));
        when(configManager.getConfigDescriptor(any())).thenReturn(
            new ConfigDescriptor(ConstsExpConfig.class.getAnnotation(ConfigGroup.class), List.of(), List.of()));
        when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);

        plugin.startUp();
        verify(autopilot).setRunning(true);

        plugin.shutDown();
        verify(autopilot).setRunning(false);
    }

    // Lifecycle

    @Test
    public void startUpShouldSeedPlayerLocationAndWaitToFindTheAggroArea() throws Exception
    {
        setField("sessionActive", false);
        setField("overlayManager", mock(OverlayManager.class));
        setField("mouseManager", mock(MouseManager.class));
        setField("flashOverlay", mock(ConstsExpFlashOverlay.class));
        setField("statusOverlay", mock(ConstsExpStatusOverlay.class));
        setField("aggroAreaOverlay", mock(ConstsExpAggroAreaOverlay.class));
        setField("treeTimerOverlay", mock(ConstsExpTreeTimerOverlay.class));
        setField("clientToolbar", mock(ClientToolbar.class));
        setField("colorPickerManager", mock(ColorPickerManager.class));
        setField("eventBus", mock(EventBus.class));
        when(configManager.getConfigDescriptor(any())).thenReturn(
            new ConfigDescriptor(ConstsExpConfig.class.getAnnotation(ConfigGroup.class), List.of(), List.of()));
        when(config.timerMode()).thenReturn(TimerMode.OSRS_AGGRO_10_MIN);

        plugin.startUp();

        assertTrue((boolean) getField("sessionActive"));
        assertEquals(player.getWorldLocation(), getField("lastPlayerWorldPoint"));
        // Like NPC Aggression Timer: no area, and so no timer, until a teleport or a reset shows where it is.
        assertFalse(aggroArea().hasArea());
        assertEquals(0L, plugin.getNextAggroAlertMillis());
    }

    private AggroArea aggroArea()
    {
        return plugin.getAggroArea();
    }

    /** Keeps RuneScape profile config in the given map, so what logging out saves, logging in can read. */
    private void backProfileConfigWith(Map<String, Object> saved)
    {
        doAnswer(invocation ->
        {
            saved.put(invocation.getArgument(1), invocation.getArgument(2));
            return null;
        }).when(configManager).setRSProfileConfiguration(eq(ConstsExpConfig.GROUP), anyString(), any());
        doAnswer(invocation ->
        {
            saved.remove(invocation.<String>getArgument(1));
            return null;
        }).when(configManager).unsetRSProfileConfiguration(eq(ConstsExpConfig.GROUP), anyString());
        when(configManager.<Object>getRSProfileConfiguration(eq(ConstsExpConfig.GROUP), anyString(), any(Type.class)))
            .thenAnswer(invocation -> saved.get(invocation.<String>getArgument(1)));
    }

    private Set<AlertType> enabledAlerts()
    {
        Set<AlertType> enabled = EnumSet.noneOf(AlertType.class);
        for (AlertType type : AlertType.values())
        {
            if (plugin.isAlertEnabled(type))
            {
                enabled.add(type);
            }
        }
        return enabled;
    }

    private void runOnClientThread() throws Exception
    {
        ClientThread clientThread = (ClientThread) getField("clientThread");
        doAnswer(invocation ->
        {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(clientThread).invoke(any(Runnable.class));
    }

    private void pickAllForestryEvents()
    {
        when(config.enableForestryAlert()).thenReturn(true);
        when(config.forestryRisingRoots()).thenReturn(true);
        when(config.forestryStrugglingSapling()).thenReturn(true);
        when(config.forestryFloweringTree()).thenReturn(true);
        when(config.forestryLeprechaun()).thenReturn(true);
        when(config.forestryBeehive()).thenReturn(true);
        when(config.forestryFriendlyEnt()).thenReturn(true);
        when(config.forestryPoachers()).thenReturn(true);
        when(config.forestryPheasantControl()).thenReturn(true);
        when(config.forestryEnchantmentRitual()).thenReturn(true);
    }

    private static NPC npc(int id, String name)
    {
        NPC npc = mock(NPC.class);
        when(npc.getId()).thenReturn(id);
        when(npc.getName()).thenReturn(name);
        return npc;
    }

    private static GameObjectSpawned objectSpawned(int id)
    {
        GameObjectSpawned event = new GameObjectSpawned();
        event.setGameObject(gameObject(id));
        return event;
    }

    private static GameObjectDespawned objectDespawned(int id)
    {
        GameObjectDespawned event = new GameObjectDespawned();
        event.setGameObject(gameObject(id));
        return event;
    }

    private static GameObject gameObject(int id)
    {
        GameObject object = mock(GameObject.class);
        when(object.getId()).thenReturn(id);
        return object;
    }

    private void runClientThreadNow() throws Exception
    {
        doAnswer(invocation ->
        {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when((ClientThread) getField("clientThread")).invokeLater(any(Runnable.class));
    }

    private static ConfigChanged masterSwitchChange(String from, String to)
    {
        ConfigChanged changed = new ConfigChanged();
        changed.setGroup(ConstsExpConfig.GROUP);
        changed.setKey(ConstsExpConfig.MASTER_KEY);
        changed.setOldValue(from);
        changed.setNewValue(to);
        return changed;
    }

    private static ConfigChanged gotrAutopilotChange(String from, String to)
    {
        ConfigChanged changed = new ConfigChanged();
        changed.setGroup(ConstsExpConfig.GROUP);
        changed.setKey("gotrAutopilot");
        changed.setOldValue(from);
        changed.setNewValue(to);
        return changed;
    }

    private static ConfigChanged presetChange(String from, String to)
    {
        ConfigChanged changed = new ConfigChanged();
        changed.setGroup(ConstsExpConfig.GROUP);
        changed.setKey(ConstsExpConfig.PRESET_KEY);
        changed.setOldValue(from);
        changed.setNewValue(to);
        return changed;
    }

    private static ChatMessage chat(ChatMessageType type, String message)
    {
        return new ChatMessage(null, type, "", message, "", 0);
    }

    private static GameStateChanged gameState(GameState state)
    {
        GameStateChanged event = new GameStateChanged();
        event.setGameState(state);
        return event;
    }

    private static void awaitTrue(String message, BooleanSupplier condition) throws InterruptedException
    {
        long deadline = System.currentTimeMillis() + 2000L;
        while (!condition.getAsBoolean())
        {
            if (System.currentTimeMillis() > deadline)
            {
                fail(message);
            }
            Thread.sleep(10L);
        }
    }

    private Object invokePrivate(String methodName, Object... args) throws Exception
    {
        Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++)
        {
            types[i] = args[i].getClass();
        }
        Method method = ConstsExpPlugin.class.getDeclaredMethod(methodName, types);
        method.setAccessible(true);
        return method.invoke(plugin, args);
    }

    private void setField(String fieldName, Object value) throws Exception
    {
        Field field = ConstsExpPlugin.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(plugin, value);
    }

    private Object getField(String fieldName) throws Exception
    {
        Field field = ConstsExpPlugin.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(plugin);
    }
}
