package com.constsafk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.input.MouseManager;
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
    public void sailingAfkPresetShouldOnlyEnableCargoHold()
    {
        when(config.preset()).thenReturn(Preset.SAILING_AFK);
        when(config.enableCargoHoldAlert()).thenReturn(false);
        when(config.enableHealthAlert()).thenReturn(true);

        assertEquals(EnumSet.of(AlertType.CARGO), enabledAlerts());
    }

    @Test
    public void combatPresetShouldCoverCombatAlertsOnly()
    {
        when(config.preset()).thenReturn(Preset.COMBAT);

        Set<AlertType> enabled = enabledAlerts();
        assertTrue(enabled.containsAll(EnumSet.of(AlertType.HEALTH, AlertType.PRAYER, AlertType.AGGRO, AlertType.CANNON)));
        assertFalse(enabled.contains(AlertType.CARGO));
        assertFalse(enabled.contains(AlertType.SALVAGE));
    }

    @Test
    public void sailingPresetShouldIncludeSalvageAndCargo()
    {
        when(config.preset()).thenReturn(Preset.SAILING);

        Set<AlertType> enabled = enabledAlerts();
        assertTrue(enabled.containsAll(EnumSet.of(AlertType.SALVAGE, AlertType.CARGO, AlertType.INVENTORY_FULL)));
        assertFalse(enabled.contains(AlertType.PRAYER));
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
        plugin.applyPreset(Preset.SAILING_AFK);
        verify(configManager).setConfiguration(ConstsAfkConfig.GROUP, ConstsAfkConfig.PRESET_KEY, Preset.SAILING_AFK);
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
        when(config.preset()).thenReturn(Preset.SAILING_AFK);

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

        plugin.startUp();

        assertTrue((boolean) getField("sessionActive"));
        assertEquals(player.getWorldLocation(), getField("lastPlayerWorldPoint"));
        assertEquals(player.getWorldLocation(), getField("aggroAnchor"));
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
