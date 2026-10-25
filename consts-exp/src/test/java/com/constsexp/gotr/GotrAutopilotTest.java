package com.constsexp.gotr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.constsexp.gotr.overlay.InstructionOverlay;
import com.constsexp.gotr.overlay.ItemHighlightOverlay;
import com.constsexp.gotr.overlay.SceneOverlay;
import com.constsexp.gotr.plan.Planner;
import com.constsexp.gotr.plan.PlannerSettings;
import com.constsexp.gotr.plan.Step;
import com.constsexp.gotr.state.GamePhase;
import com.constsexp.gotr.state.Location;
import com.constsexp.gotr.state.Snapshot;
import com.constsexp.gotr.state.StateTracker;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.callback.RenderCallback;
import net.runelite.client.callback.RenderCallbackManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;
import org.junit.Test;

public class GotrAutopilotTest
{
	@Test
	public void guiceGivesItsOverlaysTheOneAutopilot() throws Exception
	{
		Injector injector = Guice.createInjector(binder ->
		{
			binder.bind(Client.class).toInstance(mock(Client.class));
			binder.bind(ClientThread.class).toInstance(mock(ClientThread.class));
			binder.bind(EventBus.class).toInstance(mock(EventBus.class));
			binder.bind(OverlayManager.class).toInstance(mock(OverlayManager.class));
			binder.bind(RenderCallbackManager.class).toInstance(mock(RenderCallbackManager.class));
			binder.bind(ItemManager.class).toInstance(mock(ItemManager.class));
			binder.bind(ConfigManager.class).toInstance(mock(ConfigManager.class));
			binder.bind(ModelOutlineRenderer.class).toInstance(mock(ModelOutlineRenderer.class));
		});
		GotrAutopilot autopilot = injector.getInstance(GotrAutopilot.class);
		assertSame(autopilot, injector.getInstance(GotrAutopilot.class));
		assertSame(autopilot, get(get(autopilot, "sceneOverlay"), "plugin"));
		assertSame(autopilot, get(get(autopilot, "instructionOverlay"), "plugin"));
		assertSame(autopilot, get(get(autopilot, "itemHighlightOverlay"), "plugin"));
	}

	@Test
	public void itsSwitchPutsItAndItsTrackerOnTheEventBusOnceAndTakesThemOff() throws Exception
	{
		GotrAutopilot autopilot = new GotrAutopilot();
		EventBus eventBus = mock(EventBus.class);
		OverlayManager overlays = mock(OverlayManager.class);
		RenderCallbackManager renders = mock(RenderCallbackManager.class);
		StateTracker tracker = mock(StateTracker.class);
		set(autopilot, "client", mock(Client.class));
		set(autopilot, "clientThread", mock(ClientThread.class));
		set(autopilot, "eventBus", eventBus);
		set(autopilot, "overlayManager", overlays);
		set(autopilot, "renderCallbackManager", renders);
		set(autopilot, "tracker", tracker);
		set(autopilot, "instructionOverlay", mock(InstructionOverlay.class));
		set(autopilot, "sceneOverlay", mock(SceneOverlay.class));
		set(autopilot, "itemHighlightOverlay", mock(ItemHighlightOverlay.class));

		autopilot.setRunning(true);
		autopilot.setRunning(true);
		verify(eventBus).register(autopilot);
		verify(eventBus).register(tracker);
		verify(overlays, times(3)).add(any(Overlay.class));
		verify(renders).register(any(RenderCallback.class));

		autopilot.setRunning(false);
		autopilot.setRunning(false);
		verify(eventBus).unregister(autopilot);
		verify(eventBus).unregister(tracker);
		verify(overlays, times(3)).remove(any(Overlay.class));
		verify(tracker).reset();
		assertEquals(Step.NOT_IN_GAME, autopilot.getInstruction().getStep());
	}

	@Test
	public void thePlannerSaysWhatToDoInTheLobbyAndNothingOutside()
	{
		Snapshot lobby = Snapshot.builder().location(Location.LOBBY).phase(GamePhase.COUNTDOWN).build();
		assertEquals(Step.IDLE_LOBBY, new Planner().plan(lobby, new PlannerSettings(false)).getStep());
		assertEquals(Step.NOT_IN_GAME, new Planner().plan(Snapshot.OUTSIDE, new PlannerSettings(false)).getStep());
	}

	@Test
	public void anAccountWithNoSavedPointsStartsUnknownNotWithTheLastAccounts() throws Exception
	{
		Constructor<StateTracker> make = StateTracker.class.getDeclaredConstructor(Client.class, ClientThread.class,
			ItemManager.class, ConfigManager.class);
		make.setAccessible(true);
		StateTracker tracker = make.newInstance(mock(Client.class), mock(ClientThread.class), mock(ItemManager.class),
			mock(ConfigManager.class));
		set(tracker, "savedElementalPoints", 500);
		set(tracker, "savedCatalyticPoints", 400);

		tracker.onRuneScapeProfileChanged(new RuneScapeProfileChanged(null, "rsprofile.b"));

		assertEquals(-1, get(tracker, "savedElementalPoints"));
		assertEquals(-1, get(tracker, "savedCatalyticPoints"));
	}

	private static Object get(Object target, String name) throws Exception
	{
		Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.get(target);
	}

	private static void set(Object target, String name, Object value) throws Exception
	{
		Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}
}
