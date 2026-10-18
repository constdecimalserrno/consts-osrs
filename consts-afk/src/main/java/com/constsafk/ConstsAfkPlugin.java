package com.constsafk;

import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Toolkit;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.StatChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseListener;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

@PluginDescriptor(
    name = "consts-AFK",
    description = "AFK alerts with presets for combat, skilling, woodcutting and Sailing: Forestry events, cargo hold full, salvage, inventory full, health, prayer, aggro and more",
    tags = {"afk", "combat", "skilling", "woodcutting", "forestry", "sailing", "cargo", "salvage", "prayer", "health", "idle", "aggro", "logout", "potion", "random", "cannon", "inventory", "preset"}
)
public class ConstsAfkPlugin extends Plugin
{
    static final long LOGOUT_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(25L);

    private static final long MIN_REPEAT_INTERVAL_MS = 100L;
    private static final long MIN_FLASH_INTERVAL_MS = 50L;
    private static final long AGGRO_FLASH_WINDOW_MS = 1500L;
    private static final long BURST_FLASH_WINDOW_MS = 1500L;
    private static final long BURST_PANEL_WINDOW_MS = BURST_FLASH_WINDOW_MS * 10L;
    // A Forestry event whose markers all vanished this recently is the same event coming back (a sapling withering,
    // a loading screen), not a new one.
    static final long FORESTRY_REARM_MS = 30_000L;

    private static final Set<AlertType> LOOP_ALERTS = EnumSet.of(
        AlertType.HEALTH, AlertType.PRAYER, AlertType.IDLE, AlertType.LOGOUT);
    private static final Set<AlertType> BURST_ALERTS = EnumSet.of(
        AlertType.POTION, AlertType.RANDOM, AlertType.CANNON, AlertType.INVENTORY_FULL, AlertType.SALVAGE, AlertType.CARGO,
        AlertType.FORESTRY);

    private static final Set<ChatMessageType> TRIGGER_CHAT_TYPES = EnumSet.of(
        ChatMessageType.GAMEMESSAGE, ChatMessageType.SPAM, ChatMessageType.MESBOX);
    // Lowercase fragments of the game messages that start each chat-driven burst.
    private static final Map<AlertType, List<String>> CHAT_TRIGGERS = Map.of(
        AlertType.POTION, List.of("is about to"),
        AlertType.CANNON, List.of("your cannon is out of ammo"),
        AlertType.INVENTORY_FULL, List.of("your inventory is too full to hold any more"),
        AlertType.SALVAGE, List.of("you salvage all you can from the shipwreck", "you have no more salvage to sort"),
        // "Your crewmate on the salvaging hook cannot salvage as the cargo hold is full." (spam filter channel)
        AlertType.CARGO, List.of("cargo hold is full"));
    private static final Set<String> RANDOM_EVENT_NPC_NAMES = Set.of(
        "bee keeper", "capt' arnav", "certer", "count check", "dunce", "drill demon", "dr jekyll", "drunken dwarf",
        "evil bob", "flippa", "freaky forester", "frog", "genie", "giles", "hyde", "leo", "miles", "mime",
        "mr. mordaut", "mr mordaut", "mysterious old man", "molly", "niles", "pillory guard", "postie pete",
        "prince", "princess", "prison pete", "quiz master", "rick turpentine", "sandwich lady", "security guard",
        "sergeant damien", "strange plant", "tilt");

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private ConstsAfkConfig config;

    @Inject
    private ConfigManager configManager;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private MouseManager mouseManager;

    @Inject
    private ConstsAfkFlashOverlay flashOverlay;

    @Inject
    private ConstsAfkStatusOverlay statusOverlay;

    private final Map<AlertType, LoopAlert> loops = new EnumMap<>(AlertType.class);
    private final Map<AlertType, BurstAlert> bursts = new EnumMap<>(AlertType.class);
    private final Map<Skill, Integer> skillXpCache = new EnumMap<>(Skill.class);
    // Markers of each Forestry event on screen now, and when each event's last marker went away.
    private final Map<ForestryEvent, Integer> forestryMarkers = new EnumMap<>(ForestryEvent.class);
    private final Map<ForestryEvent, Long> forestryGoneSince = new EnumMap<>(ForestryEvent.class);
    private volatile ForestryEvent lastForestryEvent;

    private ScheduledExecutorService scheduler;
    private volatile ScheduledFuture<?> aggroFuture;

    private volatile boolean sessionActive;
    private volatile long startupDelayUntilMillis;
    private volatile long alertsPausedUntilMillis;
    private volatile long lastUserInteractionMillis = System.currentTimeMillis();
    private long lastActivityMillis = System.currentTimeMillis();
    private int lastKeyboardIdleTicks = -1;
    private int lastPlayerAnimation = -1;
    private WorldPoint lastPlayerWorldPoint;
    private volatile boolean playerMovingThisTick;
    private long lastRandomEventTriggerMillis;

    private WorldPoint aggroAnchor;
    private volatile long nextAggroAlertMillis;
    private volatile long aggroFlashUntilMillis;
    private volatile boolean aggroAlertPending;
    private volatile boolean aggroExpiredAwaitingReset;

    private final MouseListener clickListener = new MouseAdapter()
    {
        @Override
        public MouseEvent mousePressed(MouseEvent mouseEvent)
        {
            onUserInteraction();
            return mouseEvent;
        }
    };

    public ConstsAfkPlugin()
    {
        LOOP_ALERTS.forEach(type -> loops.put(type, new LoopAlert(type)));
        BURST_ALERTS.forEach(type -> bursts.put(type, new BurstAlert(type)));
    }

    @Provides
    ConstsAfkConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(ConstsAfkConfig.class);
    }

    @Override
    protected void startUp()
    {
        overlayManager.add(flashOverlay);
        overlayManager.add(statusOverlay);
        // Index 0 sees clicks before any other listener can consume them.
        mouseManager.registerMouseListener(0, clickListener);
        startupDelayUntilMillis = System.currentTimeMillis() + Math.max(0, config.startupDelayMs());
        clientThread.invokeLater(() ->
        {
            if (client.getGameState() == GameState.LOGGED_IN)
            {
                startSession();
            }
        });
    }

    @Override
    protected void shutDown()
    {
        endSession();
        mouseManager.unregisterMouseListener(clickListener);
        overlayManager.remove(flashOverlay);
        overlayManager.remove(statusOverlay);
        shutdownScheduler();
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        GameState state = event.getGameState();
        if (state == GameState.LOGGED_IN)
        {
            if (!sessionActive)
            {
                startSession();
            }
        }
        else if (state == GameState.LOADING)
        {
            // Loading screens (teleports, region crossings) keep the session. The scene reloads, so any Forestry event
            // on screen counts as just gone: seen again after the load, it is the same event.
            markForestryEventsGone();
        }
        else
        {
            endSession();
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!ConstsAfkConfig.GROUP.equals(event.getGroup()))
        {
            return;
        }

        clientThread.invokeLater(() ->
        {
            resetAlertState();
            evaluateAlerts();
        });
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        updateActivityFromHybridSignals();
        evaluateAlerts();
    }

    @Subscribe
    public void onStatChanged(StatChanged event)
    {
        updateActivityFromXp(event.getSkill(), event.getXp());
    }

    @Subscribe
    public void onClientTick(ClientTick event)
    {
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }

        // The keyboard idle counter only drops when a key is pressed. Clicks come in through clickListener.
        int keyboardIdleTicks = client.getKeyboardIdleTicks();
        if (lastKeyboardIdleTicks >= 0 && keyboardIdleTicks < lastKeyboardIdleTicks)
        {
            onUserInteraction();
        }
        lastKeyboardIdleTicks = keyboardIdleTicks;
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        if (!isTracking() || !TRIGGER_CHAT_TYPES.contains(event.getType()))
        {
            return;
        }

        for (AlertType type : matchChatTriggers(event.getMessage()))
        {
            bursts.get(type).trigger();
        }
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned event)
    {
        NPC npc = event.getNpc();
        forestryMarkerAppeared(ForestryEvent.forNpc(npc.getId()));
        maybeTriggerRandomEventAlert(npc);
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event)
    {
        forestryMarkerGone(ForestryEvent.forNpc(event.getNpc().getId()));
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event)
    {
        forestryMarkerAppeared(ForestryEvent.forObject(event.getGameObject().getId()));
    }

    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned event)
    {
        forestryMarkerGone(ForestryEvent.forObject(event.getGameObject().getId()));
    }

    @Subscribe
    public void onNpcChanged(NpcChanged event)
    {
        maybeTriggerRandomEventAlert(event.getNpc());
    }

    static Set<AlertType> matchChatTriggers(String rawMessage)
    {
        Set<AlertType> matches = EnumSet.noneOf(AlertType.class);
        if (rawMessage == null)
        {
            return matches;
        }

        String message = Text.removeTags(rawMessage).toLowerCase(Locale.ENGLISH);
        CHAT_TRIGGERS.forEach((type, fragments) ->
        {
            if (fragments.stream().anyMatch(message::contains))
            {
                matches.add(type);
            }
        });
        return matches;
    }

    static boolean matchesRandomEventNpcName(String rawName)
    {
        return rawName != null
            && RANDOM_EVENT_NPC_NAMES.contains(Text.removeTags(rawName).trim().toLowerCase(Locale.ENGLISH));
    }

    boolean isAlertEnabled(AlertType type)
    {
        Preset preset = config.preset();
        return (preset != null ? preset : Preset.CUSTOM).enables(type, config);
    }

    void applyPreset(Preset preset)
    {
        configManager.setConfiguration(ConstsAfkConfig.GROUP, ConstsAfkConfig.PRESET_KEY, preset);
    }

    /** Whether the alert is raising the alarm right now, as shown by its pulsing status panel bar. */
    boolean isAlertFiring(AlertType type)
    {
        long now = System.currentTimeMillis();
        BurstAlert burst = bursts.get(type);
        return burst != null ? burst.isPanelLit(now) : isFlashing(type, now);
    }

    List<Color> getCurrentFlashColors()
    {
        if (!config.enableScreenFlash() || !isTracking() || areAlertsPaused())
        {
            return Collections.emptyList();
        }

        long now = System.currentTimeMillis();
        long interval = Math.max(MIN_FLASH_INTERVAL_MS, config.flashIntervalMs());
        List<Color> colors = new ArrayList<>();
        for (AlertType type : AlertType.values())
        {
            if (isFlashing(type, now) && type.isFlashPhaseOn(now, interval))
            {
                colors.add(type.getFlashColor(config));
            }
        }
        return colors;
    }

    private boolean isFlashing(AlertType type, long now)
    {
        if (type == AlertType.AGGRO)
        {
            return aggroExpiredAwaitingReset || now < aggroFlashUntilMillis;
        }

        LoopAlert loop = loops.get(type);
        return loop != null ? loop.isRunning() : bursts.get(type).isFlashing(now);
    }

    private void startSession()
    {
        sessionActive = true;
        resetAlertState();
        markUserInteractionNow();
        resetActivityTracking();
        lastKeyboardIdleTicks = client.getKeyboardIdleTicks();
        evaluateAlerts();
    }

    private void endSession()
    {
        sessionActive = false;
        resetAlertState();
        skillXpCache.clear();
        forestryMarkers.clear();
        forestryGoneSince.clear();
        lastPlayerWorldPoint = null;
        lastPlayerAnimation = -1;
        lastKeyboardIdleTicks = -1;
    }

    private void resetAlertState()
    {
        loops.values().forEach(LoopAlert::stop);
        bursts.values().forEach(BurstAlert::clear);
        stopAggroTimer();
        aggroExpiredAwaitingReset = false;
        aggroAlertPending = false;
        aggroFlashUntilMillis = 0L;
        aggroAnchor = null;
        alertsPausedUntilMillis = 0L;
        lastRandomEventTriggerMillis = 0L;
        playerMovingThisTick = false;
    }

    private boolean isTracking()
    {
        return sessionActive && client.getGameState() == GameState.LOGGED_IN && !isStartupDelayActive();
    }

    private void evaluateAlerts()
    {
        if (!isTracking())
        {
            return;
        }

        long now = System.currentTimeMillis();
        loops.get(AlertType.HEALTH).setFiring(isAlertEnabled(AlertType.HEALTH)
            && client.getBoostedSkillLevel(Skill.HITPOINTS) < config.healthThreshold());
        loops.get(AlertType.PRAYER).setFiring(isAlertEnabled(AlertType.PRAYER)
            && client.getBoostedSkillLevel(Skill.PRAYER) < config.prayerThreshold());
        loops.get(AlertType.IDLE).setFiring(isAlertEnabled(AlertType.IDLE)
            && !(config.idleRequireNoAnimation() && getCurrentAnimationId() != -1)
            && now - lastActivityMillis >= config.idleThresholdMs());
        loops.get(AlertType.LOGOUT).setFiring(isAlertEnabled(AlertType.LOGOUT)
            && now - lastUserInteractionMillis >= Math.max(60_000L, config.logoutWarningMs()));
        evaluateAggroTimer();
    }

    private void onUserInteraction()
    {
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }

        markUserInteractionNow();
        loops.get(AlertType.LOGOUT).stop();
        bursts.values().forEach(BurstAlert::acknowledge);

        long pauseMs = Math.max(0, config.interactionPauseMs());
        if (pauseMs > 0)
        {
            alertsPausedUntilMillis = System.currentTimeMillis() + pauseMs;
        }
    }

    private boolean emitsDuringPause(AlertType type)
    {
        switch (type)
        {
            case HEALTH:
                return config.healthEmitImmediately();
            case PRAYER:
                return config.prayerEmitImmediately();
            case POTION:
                return config.potionEmitImmediately();
            case CANNON:
                return config.cannonEmitImmediately();
            default:
                return false;
        }
    }

    private int burstIterations(AlertType type)
    {
        switch (type)
        {
            case POTION:
                return config.potionExpiryAlertIterations();
            case RANDOM:
                return config.randomEventAlertIterations();
            case CANNON:
                return config.cannonAlertIterations();
            case INVENTORY_FULL:
                return config.inventoryFullAlertIterations();
            case SALVAGE:
                return config.sailingAlertIterations();
            case CARGO:
                return config.cargoHoldAlertIterations();
            case FORESTRY:
                return config.forestryAlertIterations();
            default:
                return 1;
        }
    }

    private void beep()
    {
        if (config.enableAllSoundAlerts() && !isStartupDelayActive())
        {
            Toolkit.getDefaultToolkit().beep();
        }
    }

    // Activity tracking

    @SuppressWarnings("deprecation") // OVERALL is deprecated but still part of Skill.values()
    private static boolean isTrainableSkill(Skill skill)
    {
        return skill != null && skill != Skill.OVERALL;
    }

    private void updateActivityFromXp(Skill skill, int currentXp)
    {
        if (!isTrainableSkill(skill) || currentXp < 0)
        {
            return;
        }

        Integer previousXp = skillXpCache.put(skill, currentXp);
        if (previousXp != null && currentXp > previousXp)
        {
            markActivityNow();
        }
    }

    private void updateActivityFromHybridSignals()
    {
        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null)
        {
            playerMovingThisTick = false;
            return;
        }

        boolean active = false;
        boolean moved = false;

        WorldPoint currentPoint = localPlayer.getWorldLocation();
        if (currentPoint != null)
        {
            maybeAutoResetAggroFromMovement(currentPoint);

            if (lastPlayerWorldPoint != null && !lastPlayerWorldPoint.equals(currentPoint))
            {
                active = true;
                moved = true;
            }
            lastPlayerWorldPoint = currentPoint;
        }

        playerMovingThisTick = moved;

        // A repeating animation (e.g. woodcutting) only counts once; XP drops cover the rest.
        int currentAnimation = localPlayer.getAnimation();
        if (currentAnimation != -1 && currentAnimation != lastPlayerAnimation)
        {
            active = true;
        }
        lastPlayerAnimation = currentAnimation;

        if (localPlayer.getInteracting() != null && !localPlayer.getInteracting().isDead())
        {
            active = true;
        }

        if (active)
        {
            markActivityNow();
        }
    }

    private void resetActivityTracking()
    {
        markActivityNow();
        skillXpCache.clear();
        playerMovingThisTick = false;
        lastPlayerWorldPoint = null;
        lastPlayerAnimation = -1;

        Player localPlayer = client.getLocalPlayer();
        if (localPlayer != null)
        {
            lastPlayerWorldPoint = localPlayer.getWorldLocation();
            lastPlayerAnimation = localPlayer.getAnimation();
            if (aggroAnchor == null)
            {
                aggroAnchor = lastPlayerWorldPoint;
            }
        }

        for (Skill skill : Skill.values())
        {
            int xp = isTrainableSkill(skill) ? client.getSkillExperience(skill) : -1;
            if (xp >= 0)
            {
                skillXpCache.put(skill, xp);
            }
        }
    }

    private void markActivityNow()
    {
        lastActivityMillis = System.currentTimeMillis();
    }

    private void markUserInteractionNow()
    {
        lastUserInteractionMillis = System.currentTimeMillis();
    }

    // Random events

    private void maybeTriggerRandomEventAlert(NPC npc)
    {
        // Forestry NPCs are not random events, though Pheasant Control's is also called Freaky Forester.
        if (npc == null || !isTracking() || !isAlertEnabled(AlertType.RANDOM)
            || ForestryEvent.forNpc(npc.getId()) != null || !isLikelyRandomEventNpc(npc))
        {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastRandomEventTriggerMillis < Math.max(0L, config.randomEventCooldownMs()))
        {
            return;
        }

        lastRandomEventTriggerMillis = now;
        bursts.get(AlertType.RANDOM).trigger();
    }

    private boolean isLikelyRandomEventNpc(NPC npc)
    {
        if (matchesRandomEventNpcName(npc.getName()))
        {
            return true;
        }

        NPCComposition composition = npc.getTransformedComposition();
        if (composition == null)
        {
            composition = npc.getComposition();
        }
        if (composition == null)
        {
            return false;
        }

        String[] actions = composition.getActions();
        if (hasAction(actions, "dismiss"))
        {
            return true;
        }

        // Fallback: a talkable, non-combat NPC that is following the local player.
        Player localPlayer = client.getLocalPlayer();
        boolean followsLocalPlayer = localPlayer != null && npc.getInteracting() == localPlayer;
        return followsLocalPlayer
            && hasAction(actions, "talk-to")
            && !hasAction(actions, "attack")
            && npc.getCombatLevel() <= 0;
    }

    private static boolean hasAction(String[] actions, String actionName)
    {
        if (actions == null)
        {
            return false;
        }

        for (String action : actions)
        {
            if (actionName.equalsIgnoreCase(action))
            {
                return true;
            }
        }
        return false;
    }

    // Forestry events

    private void forestryMarkerAppeared(ForestryEvent forestryEvent)
    {
        if (forestryEvent == null || forestryMarkers.merge(forestryEvent, 1, Integer::sum) > 1)
        {
            return;
        }

        // First marker on screen: a new event, unless this one only just went away.
        Long goneSince = forestryGoneSince.remove(forestryEvent);
        if (goneSince != null && System.currentTimeMillis() - goneSince < FORESTRY_REARM_MS)
        {
            return;
        }

        if (isTracking() && isAlertEnabled(AlertType.FORESTRY) && forestryEvent.isWanted(config))
        {
            lastForestryEvent = forestryEvent;
            bursts.get(AlertType.FORESTRY).trigger();
        }
    }

    private void forestryMarkerGone(ForestryEvent forestryEvent)
    {
        Integer markers = forestryEvent != null ? forestryMarkers.get(forestryEvent) : null;
        if (markers == null)
        {
            return;
        }

        if (markers > 1)
        {
            forestryMarkers.put(forestryEvent, markers - 1);
        }
        else
        {
            forestryMarkers.remove(forestryEvent);
            forestryGoneSince.put(forestryEvent, System.currentTimeMillis());
        }
    }

    private void markForestryEventsGone()
    {
        long now = System.currentTimeMillis();
        forestryMarkers.keySet().forEach(forestryEvent -> forestryGoneSince.put(forestryEvent, now));
        forestryMarkers.clear();
    }

    /** The Forestry event that last raised the alert, named on the panel while it fires. */
    ForestryEvent getLastForestryEvent()
    {
        return lastForestryEvent;
    }

    // Aggro timer

    private void evaluateAggroTimer()
    {
        if (!isAlertEnabled(AlertType.AGGRO))
        {
            stopAggroTimer();
            aggroExpiredAwaitingReset = false;
            aggroAlertPending = false;
            aggroFlashUntilMillis = 0L;
            return;
        }

        if (aggroAlertPending)
        {
            fireAggroAlert();
            return;
        }

        if (!aggroExpiredAwaitingReset)
        {
            startAggroTimer();
        }
    }

    private void startAggroTimer()
    {
        if (isActive(aggroFuture))
        {
            return;
        }

        if (config.timerMode() == TimerMode.OSRS_AGGRO_10_MIN && aggroAnchor == null)
        {
            Player localPlayer = client.getLocalPlayer();
            if (localPlayer != null)
            {
                aggroAnchor = localPlayer.getWorldLocation();
            }
        }

        scheduleAggroAlert(getAggroIntervalMillis());
    }

    private void scheduleAggroAlert(long delayMs)
    {
        long boundedDelay = Math.max(1000L, delayMs);
        nextAggroAlertMillis = System.currentTimeMillis() + boundedDelay;
        aggroFuture = scheduler().schedule(this::onAggroTimerDue, boundedDelay, TimeUnit.MILLISECONDS);
    }

    private void onAggroTimerDue()
    {
        if (!sessionActive || !isAlertEnabled(AlertType.AGGRO))
        {
            nextAggroAlertMillis = 0L;
            return;
        }

        if (config.timerMode() == TimerMode.OSRS_AGGRO_10_MIN)
        {
            // Aggro has worn off: keep alerting until the player walks away or resets the timer.
            aggroExpiredAwaitingReset = true;
            aggroAlertPending = false;
            nextAggroAlertMillis = 0L;
            aggroFuture = scheduleRepeating(this::onAggroExpiredTick);
            return;
        }

        fireAggroAlert();
        scheduleAggroAlert(getAggroIntervalMillis());
    }

    private void onAggroExpiredTick()
    {
        if (!areAlertsPaused() && !playerMovingThisTick)
        {
            beep();
        }
    }

    /** Beeps once, or holds the alert until the player is no longer clicking or moving. */
    private void fireAggroAlert()
    {
        if (areAlertsPaused() || playerMovingThisTick)
        {
            aggroAlertPending = true;
            return;
        }

        aggroAlertPending = false;
        beep();
        aggroFlashUntilMillis = System.currentTimeMillis() + AGGRO_FLASH_WINDOW_MS;
    }

    private void maybeAutoResetAggroFromMovement(WorldPoint currentPoint)
    {
        if (!isAlertEnabled(AlertType.AGGRO) || config.timerMode() != TimerMode.OSRS_AGGRO_10_MIN)
        {
            return;
        }

        if (aggroAnchor == null)
        {
            aggroAnchor = currentPoint;
            return;
        }

        int thresholdTiles = Math.max(5, config.aggroResetDistanceTiles());
        int dx = currentPoint.getX() - aggroAnchor.getX();
        int dy = currentPoint.getY() - aggroAnchor.getY();
        if (aggroAnchor.getPlane() != currentPoint.getPlane() || dx * dx + dy * dy >= thresholdTiles * thresholdTiles)
        {
            resetAggroTimer(currentPoint);
        }
    }

    void resetAggroTimer()
    {
        Player localPlayer = client.getLocalPlayer();
        resetAggroTimer(localPlayer != null ? localPlayer.getWorldLocation() : null);
    }

    private void resetAggroTimer(WorldPoint newAnchor)
    {
        stopAggroTimer();
        aggroExpiredAwaitingReset = false;
        aggroAlertPending = false;
        aggroFlashUntilMillis = 0L;
        aggroAnchor = newAnchor;

        if (isTracking() && isAlertEnabled(AlertType.AGGRO))
        {
            startAggroTimer();
        }
    }

    private void stopAggroTimer()
    {
        cancel(aggroFuture);
        aggroFuture = null;
        nextAggroAlertMillis = 0L;
    }

    long getAggroIntervalMillis()
    {
        if (config.timerMode() == TimerMode.OSRS_AGGRO_10_MIN)
        {
            return TimeUnit.MINUTES.toMillis(10L);
        }
        return TimeUnit.MINUTES.toMillis(Math.max(1, config.timerMinutes()));
    }

    // Status panel readouts

    long getNextAggroAlertMillis()
    {
        return nextAggroAlertMillis;
    }

    long getIdleDurationMillis()
    {
        return System.currentTimeMillis() - lastActivityMillis;
    }

    long getLogoutTimerRemainingMillis()
    {
        return Math.max(0L, LOGOUT_TIMEOUT_MS - (System.currentTimeMillis() - lastUserInteractionMillis));
    }

    long getPauseRemainingMillis()
    {
        return Math.max(0L, alertsPausedUntilMillis - System.currentTimeMillis());
    }

    int getCurrentAnimationId()
    {
        Player localPlayer = client.getLocalPlayer();
        return localPlayer != null ? localPlayer.getAnimation() : -1;
    }

    private boolean areAlertsPaused()
    {
        return System.currentTimeMillis() < alertsPausedUntilMillis;
    }

    private boolean isStartupDelayActive()
    {
        return System.currentTimeMillis() < startupDelayUntilMillis;
    }

    // Scheduling

    private synchronized ScheduledExecutorService scheduler()
    {
        if (scheduler == null || scheduler.isShutdown())
        {
            scheduler = Executors.newSingleThreadScheduledExecutor(r ->
            {
                Thread thread = new Thread(r, "consts-afk-alerts");
                thread.setDaemon(true);
                return thread;
            });
        }
        return scheduler;
    }

    private synchronized void shutdownScheduler()
    {
        if (scheduler != null)
        {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    private ScheduledFuture<?> scheduleRepeating(Runnable action)
    {
        long interval = Math.max(MIN_REPEAT_INTERVAL_MS, config.repeatIntervalMs());
        return scheduler().scheduleAtFixedRate(action, 0L, interval, TimeUnit.MILLISECONDS);
    }

    private static boolean isActive(ScheduledFuture<?> future)
    {
        return future != null && !future.isDone();
    }

    private static void cancel(ScheduledFuture<?> future)
    {
        if (future != null)
        {
            future.cancel(false);
        }
    }

    /** Beeps every repeat interval for as long as its condition holds (Health, Prayer, Idle, Logout). */
    private final class LoopAlert
    {
        private final AlertType type;
        private ScheduledFuture<?> future;

        private LoopAlert(AlertType type)
        {
            this.type = type;
        }

        synchronized void setFiring(boolean firing)
        {
            if (!firing)
            {
                stop();
            }
            else if (!isActive(future))
            {
                future = scheduleRepeating(() ->
                {
                    if (emitsDuringPause(type) || !areAlertsPaused())
                    {
                        beep();
                    }
                });
            }
        }

        synchronized void stop()
        {
            cancel(future);
            future = null;
        }

        synchronized boolean isRunning()
        {
            return isActive(future);
        }
    }

    /** Beeps a set number of times after a one-off trigger such as a chat message, until a click acknowledges it. */
    private final class BurstAlert
    {
        private final AlertType type;
        private ScheduledFuture<?> future;
        private int remaining;
        private volatile long flashUntilMillis;
        private volatile long panelUntilMillis;

        private BurstAlert(AlertType type)
        {
            this.type = type;
        }

        synchronized void trigger()
        {
            if (!isAlertEnabled(type))
            {
                return;
            }

            stop();
            remaining = Math.max(1, burstIterations(type));
            future = scheduleRepeating(this::tick);
        }

        private synchronized void tick()
        {
            if (!sessionActive || !isAlertEnabled(type) || remaining <= 0)
            {
                stop();
                return;
            }

            if (!emitsDuringPause(type) && areAlertsPaused())
            {
                return;
            }

            long now = System.currentTimeMillis();
            beep();
            flashUntilMillis = now + BURST_FLASH_WINDOW_MS;
            panelUntilMillis = now + BURST_PANEL_WINDOW_MS;
            if (--remaining <= 0)
            {
                stop();
            }
        }

        /** Clicks and key presses silence the burst unless the alert is set to emit through interaction. */
        synchronized void acknowledge()
        {
            if (!emitsDuringPause(type))
            {
                clear();
            }
        }

        synchronized void clear()
        {
            stop();
            flashUntilMillis = 0L;
            panelUntilMillis = 0L;
        }

        boolean isFlashing(long now)
        {
            return now < flashUntilMillis;
        }

        boolean isPanelLit(long now)
        {
            return now < panelUntilMillis;
        }

        private void stop()
        {
            cancel(future);
            future = null;
            remaining = 0;
        }
    }
}
