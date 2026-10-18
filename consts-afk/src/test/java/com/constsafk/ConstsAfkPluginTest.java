package com.constsafk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import javax.swing.JComboBox;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ConstsAfkPluginTest
{
    private static final String CARGO_FULL_MESSAGE = "Your crewmate on the salvaging hook cannot salvage as the cargo hold is full.";

    private ConstsAfkPlugin plugin;
    private Client client;
    private ConstsAfkConfig config;
    private ConfigManager configManager;
    private Player player;

    @Before
    public void setUp() throws Exception
    {
        plugin = new ConstsAfkPlugin();
        client = mock(Client.class);
        config = mock(ConstsAfkConfig.class);
        configManager = mock(ConfigManager.class);
        player = mock(Player.class);
        ClientThread clientThread = mock(ClientThread.class);

        setField("client", client);
        setField("config", config);
        setField("configManager", configManager);
        setField("clientThread", clientThread);
        setField("sessionActive", true);

        when(config.preset()).thenReturn(Preset.CUSTOM);
        when(config.enableTimerAlert()).thenReturn(true);
        when(config.enableCargoHoldAlert()).thenReturn(true);
        when(config.enablePotionExpiryAlert()).thenReturn(true);
        when(config.aggroResetDistanceTiles()).thenReturn(20);
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
    public void chinningPresetShouldCoverCombatAlertsWithoutTheCannon()
    {
        when(config.preset()).thenReturn(Preset.CHINNING);

        assertEquals(EnumSet.of(AlertType.HEALTH, AlertType.PRAYER, AlertType.IDLE, AlertType.AGGRO, AlertType.LOGOUT,
            AlertType.POTION, AlertType.RANDOM), enabledAlerts());
    }

    @Test
    public void salvagingActivePresetShouldCoverTheHookTheInventoryAndTheHold()
    {
        when(config.preset()).thenReturn(Preset.SALVAGING_ACTIVE);

        assertEquals(EnumSet.of(AlertType.LOGOUT, AlertType.RANDOM, AlertType.INVENTORY_FULL, AlertType.SALVAGE,
            AlertType.CARGO), enabledAlerts());
    }

    @Test
    public void crabbingPresetShouldCoverAggroWithoutPrayer()
    {
        when(config.preset()).thenReturn(Preset.CRABBING);

        assertEquals(EnumSet.of(AlertType.HEALTH, AlertType.IDLE, AlertType.AGGRO, AlertType.LOGOUT, AlertType.POTION,
            AlertType.RANDOM), enabledAlerts());
    }

    @Test
    public void miningAndFishingPresetsShouldCoverTheSkillingAlerts()
    {
        Set<AlertType> skilling = EnumSet.of(AlertType.IDLE, AlertType.LOGOUT, AlertType.RANDOM, AlertType.INVENTORY_FULL);

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
    public void woodcuttingPresetShouldAddForestryToTheSkillingAlerts()
    {
        when(config.preset()).thenReturn(Preset.WOODCUTTING);

        assertEquals(EnumSet.of(AlertType.IDLE, AlertType.LOGOUT, AlertType.RANDOM, AlertType.INVENTORY_FULL,
            AlertType.FORESTRY), enabledAlerts());
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
        verify(configManager).setConfiguration(ConstsAfkConfig.GROUP, ConstsAfkConfig.PRESET_KEY, Preset.SALVAGING_AFK);
    }

    // Chat triggers

    @Test
    public void cargoHoldFullMessageShouldTriggerCargoAlert()
    {
        assertEquals(EnumSet.of(AlertType.CARGO), ConstsAfkPlugin.matchChatTriggers(CARGO_FULL_MESSAGE));
        assertEquals(EnumSet.of(AlertType.CARGO), ConstsAfkPlugin.matchChatTriggers("<col=ef1020>Your cargo hold is full.</col>"));
    }

    @Test
    public void chatTriggersShouldMatchEachBurstAlert()
    {
        assertEquals(EnumSet.of(AlertType.SALVAGE),
            ConstsAfkPlugin.matchChatTriggers("You salvage all you can from the shipwreck before it is reclaimed by the sea."));
        assertEquals(EnumSet.of(AlertType.SALVAGE),
            ConstsAfkPlugin.matchChatTriggers("You have no more salvage to sort."));
        assertEquals(EnumSet.of(AlertType.INVENTORY_FULL),
            ConstsAfkPlugin.matchChatTriggers("Your inventory is too full to hold any more logs."));
        assertEquals(EnumSet.of(AlertType.CANNON),
            ConstsAfkPlugin.matchChatTriggers("Your cannon is out of ammo!"));
        assertEquals(EnumSet.of(AlertType.POTION),
            ConstsAfkPlugin.matchChatTriggers("<col=ff0000>Your divine ranging potion is about to expire.</col>"));
    }

    @Test
    public void chatTriggersShouldIgnoreUnrelatedMessages()
    {
        assertTrue(ConstsAfkPlugin.matchChatTriggers("You drink some of your super combat potion.").isEmpty());
        assertTrue(ConstsAfkPlugin.matchChatTriggers("Welcome to Old School RuneScape.").isEmpty());
        assertTrue(ConstsAfkPlugin.matchChatTriggers(null).isEmpty());
    }

    @Test
    public void randomEventMatcherShouldDetectKnownNpcNames()
    {
        assertTrue(ConstsAfkPlugin.matchesRandomEventNpcName("Genie"));
        assertTrue(ConstsAfkPlugin.matchesRandomEventNpcName("Sandwich lady"));
        assertTrue(ConstsAfkPlugin.matchesRandomEventNpcName("<col=ffff00>Freaky Forester</col>"));
        assertTrue(ConstsAfkPlugin.matchesRandomEventNpcName("Mr. Mordaut"));
        assertFalse(ConstsAfkPlugin.matchesRandomEventNpcName("Banker"));
        assertFalse(ConstsAfkPlugin.matchesRandomEventNpcName(null));
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
    public void emitImmediatelyBurstShouldSurviveClicks() throws Exception
    {
        when(config.potionEmitImmediately()).thenReturn(true);

        plugin.onChatMessage(chat(ChatMessageType.GAMEMESSAGE, "Your stamina potion is about to expire."));
        awaitTrue("potion alert should start firing", () -> plugin.isAlertFiring(AlertType.POTION));

        invokePrivate("onUserInteraction");

        assertTrue("Emit Immediately alerts are not cancelled by clicks", plugin.isAlertFiring(AlertType.POTION));
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
        goneSince.put(ForestryEvent.RISING_ROOTS, System.currentTimeMillis() - ConstsAfkPlugin.FORESTRY_REARM_MS - 1L);
        plugin.onGameObjectSpawned(objectSpawned(ObjectID.GATHERING_EVENT_RISING_ROOTS));

        awaitTrue("roots after a long gap are a new event", () -> plugin.isAlertFiring(AlertType.FORESTRY));
    }

    @Test
    public void pheasantControlForesterShouldNotCountAsARandomEvent() throws Exception
    {
        when(config.preset()).thenReturn(Preset.WOODCUTTING);
        pickAllForestryEvents();

        plugin.onNpcSpawned(new NpcSpawned(npc(NpcID.GATHERING_EVENT_PHEASANT_FORESTER, "Freaky Forester")));

        awaitTrue("Pheasant Control should raise the forestry alert", () -> plugin.isAlertFiring(AlertType.FORESTRY));
        assertFalse("its Freaky Forester is not the random event", plugin.isAlertFiring(AlertType.RANDOM));
    }

    @Test
    public void forestryNpcsShouldLeaveOtherNpcsToTheRandomAlert()
    {
        assertNull(ForestryEvent.forNpc(0));
        assertTrue(ConstsAfkPlugin.matchesRandomEventNpcName("Freaky Forester"));
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
        setField("nextAggroAlertMillis", 1234L);

        invokePrivate("onAggroTimerDue");

        assertTrue((boolean) getField("aggroExpiredAwaitingReset"));
        assertEquals(0L, plugin.getNextAggroAlertMillis());

        invokePrivate("evaluateAggroTimer");
        assertEquals("evaluating must not restart an expired timer", 0L, plugin.getNextAggroAlertMillis());
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
    public void aggroShouldReanchorAfterWalkingPastResetDistance() throws Exception
    {
        WorldPoint anchor = new WorldPoint(3200, 3200, 0);
        WorldPoint movedFar = new WorldPoint(3225, 3200, 0);
        when(config.timerMode()).thenReturn(TimerMode.OSRS_AGGRO_10_MIN);
        setField("aggroAnchor", anchor);

        invokePrivate("maybeAutoResetAggroFromMovement", movedFar);

        assertEquals(movedFar, getField("aggroAnchor"));
        assertTrue("timer should restart", plugin.getNextAggroAlertMillis() > System.currentTimeMillis());
    }

    @Test
    public void aggroShouldKeepAnchorForShortMoves() throws Exception
    {
        WorldPoint anchor = new WorldPoint(3200, 3200, 0);
        when(config.timerMode()).thenReturn(TimerMode.OSRS_AGGRO_10_MIN);
        setField("aggroAnchor", anchor);
        setField("nextAggroAlertMillis", 1234L);

        invokePrivate("maybeAutoResetAggroFromMovement", new WorldPoint(3210, 3200, 0));

        assertEquals(anchor, getField("aggroAnchor"));
        assertEquals(1234L, plugin.getNextAggroAlertMillis());
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

    // Lifecycle

    @Test
    public void startUpShouldSeedPlayerLocationAndAggroAnchor() throws Exception
    {
        setField("sessionActive", false);
        setField("overlayManager", mock(OverlayManager.class));
        setField("mouseManager", mock(MouseManager.class));
        setField("flashOverlay", mock(ConstsAfkFlashOverlay.class));
        setField("statusOverlay", mock(ConstsAfkStatusOverlay.class));
        setField("clientToolbar", mock(ClientToolbar.class));

        plugin.startUp();

        assertTrue((boolean) getField("sessionActive"));
        assertEquals(player.getWorldLocation(), getField("lastPlayerWorldPoint"));
        assertEquals(player.getWorldLocation(), getField("aggroAnchor"));
    }

    // Sidebar guide

    @Test
    public void everyPresetShouldHaveAGuide()
    {
        for (Preset preset : Preset.values())
        {
            PresetGuide guide = PresetGuide.of(preset);
            assertTrue(preset + " has no guide", guide != null && !guide.getWhere().isEmpty()
                && !guide.getAlerts().isEmpty() && !guide.getTune().isEmpty());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void guidePanelShouldApplyWhatYouPickButNotWhatItShows() throws Exception
    {
        ConstsAfkGuidePanel panel = new ConstsAfkGuidePanel(plugin);
        panel.showPreset(Preset.MINING);
        verify(configManager, never()).setConfiguration(any(), any(), any(Object.class));

        Field box = ConstsAfkGuidePanel.class.getDeclaredField("presetBox");
        box.setAccessible(true);
        ((JComboBox<Preset>) box.get(panel)).setSelectedItem(Preset.FISHING);

        verify(configManager).setConfiguration(ConstsAfkConfig.GROUP, ConstsAfkConfig.PRESET_KEY, Preset.FISHING);
    }

    @Test
    public void sidebarGuideShouldFollowItsSetting() throws Exception
    {
        ClientToolbar toolbar = mock(ClientToolbar.class);
        setField("overlayManager", mock(OverlayManager.class));
        setField("mouseManager", mock(MouseManager.class));
        setField("flashOverlay", mock(ConstsAfkFlashOverlay.class));
        setField("statusOverlay", mock(ConstsAfkStatusOverlay.class));
        setField("clientToolbar", toolbar);
        when(config.showSidebarGuide()).thenReturn(true);

        plugin.startUp();
        verify(toolbar, times(1)).addNavigation(any(NavigationButton.class));

        when(config.showSidebarGuide()).thenReturn(false);
        invokePrivate("updateGuideButton");
        verify(toolbar, times(1)).removeNavigation(any(NavigationButton.class));

        plugin.shutDown();
        verify(toolbar, times(1)).removeNavigation(any(NavigationButton.class));
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
        Method method = ConstsAfkPlugin.class.getDeclaredMethod(methodName, types);
        method.setAccessible(true);
        return method.invoke(plugin, args);
    }

    private void setField(String fieldName, Object value) throws Exception
    {
        Field field = ConstsAfkPlugin.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(plugin, value);
    }

    private Object getField(String fieldName) throws Exception
    {
        Field field = ConstsAfkPlugin.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(plugin);
    }
}
