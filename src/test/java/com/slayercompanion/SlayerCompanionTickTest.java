package com.slayercompanion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import com.google.gson.Gson;
import com.slayercompanion.data.SlayerTask;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import org.junit.Test;

/**
 * The plugin's own tick and session handlers on a task in progress. A game tick also arrives during a loading screen, on
 * every teleport, cave entrance and region boundary, and a table read can fail. Such a tick must leave the task, its rate,
 * the new-task window, the marker and the sidebar as they are. The client here answers only what that tick may ask, and
 * the config answers nothing unless a test lets it, so a tick that went on would fail the test.
 */
public class SlayerCompanionTickTest
{
	private static final SlayerDataset DATASET = new SlayerDataset(new Gson());
	private static final WorldPoint CATACOMBS = new WorldPoint(1636, 3673, 0);

	@Test
	public void aTickDuringALoadingScreenChangesNothing() throws Exception
	{
		final Harness plugin = new Harness(GameState.LOADING, null);
		plugin.onTask();
		plugin.tick();
		plugin.assertTaskKept();
	}

	@Test
	public void aTickWhoseReadFailsChangesNothing() throws Exception
	{
		final Map<Integer, Integer> varps = new HashMap<>();
		varps.put(VarPlayerID.SLAYER_COUNT, 112);
		varps.put(VarPlayerID.SLAYER_TARGET, 42);
		final Harness plugin = new Harness(GameState.LOGGED_IN, varps);
		plugin.failTableReads = true;
		plugin.onTask();
		plugin.tick();
		plugin.assertTaskKept();
	}

	/** Logged in, no kills left is no task on that same tick. The streak and points are read again at once. */
	@Test
	public void noKillsLeftEndsTheTaskOnTheSameTick() throws Exception
	{
		final Map<Integer, Integer> varps = new HashMap<>();
		varps.put(VarPlayerID.SLAYER_COUNT, 0);
		varps.put(VarPlayerID.SLAYER_TARGET, 42);
		final Harness plugin = new Harness(GameState.LOGGED_IN, varps);
		plugin.configAnswers = true;
		plugin.varbits.put(VarbitID.SLAYER_MASTER, SlayerAssigner.DURADEL.getId());
		plugin.varbits.put(VarbitID.SLAYER_TASKS_COMPLETED, 48);
		plugin.varbits.put(VarbitID.SLAYER_POINTS, 536);
		plugin.onTask();
		plugin.tick();
		assertNull(plugin.get("taskName"));
		assertNull(plugin.get("currentTask"));
		assertNull(plugin.get("taskAmount"));
		assertEquals(0, plugin.get("taskProgress"));
		assertEquals(-1, plugin.get("lastProgressTick"));
		assertNull(plugin.get("mapPoint"));
		assertEquals("48", plugin.get("taskStreak"));
		assertEquals("536", plugin.get("slayerPoints"));
		assertEquals(SlayerAssigner.DURADEL, plugin.get("taskMaster"));
	}

	/**
	 * Logging out, hopping and switching profiles clear everything at once, the master and the streak kind too, since no
	 * tick clears them while the player is not logged in. The name the reader kept is forgotten as well.
	 */
	@Test
	public void theEndOfASessionClearsEverything() throws Exception
	{
		for (String end : new String[]{"logout", "hop", "profile switch"})
		{
			final Harness plugin = new Harness(GameState.LOGIN_SCREEN, null);
			plugin.configAnswers = true;
			plugin.onTask();
			plugin.set("streakKind", SlayerAssigner.Streak.WILDERNESS);
			plugin.set("activeProfileKey", "profile-a");
			final SlayerTaskReader reader = (SlayerTaskReader) plugin.get("taskReader");
			assertEquals("Abyssal Demons", reader.name(SlayerCacheFixture.get(), 42, 0));

			switch (end)
			{
				case "logout":
					plugin.plugin.onGameStateChanged(gameState(GameState.LOGIN_SCREEN));
					break;
				case "hop":
					plugin.plugin.onGameStateChanged(gameState(GameState.HOPPING));
					break;
				default:
					plugin.plugin.onRuneScapeProfileChanged(new RuneScapeProfileChanged("profile-a", "profile-b"));
					break;
			}
			assertNull(end, plugin.get("taskName"));
			assertNull(end, plugin.get("currentTask"));
			assertNull(end, plugin.get("taskAmount"));
			assertNull(end, plugin.get("taskStreak"));
			assertNull(end, plugin.get("slayerPoints"));
			assertNull(end, plugin.get("taskMaster"));
			assertEquals(end, SlayerAssigner.Streak.MAIN, plugin.get("streakKind"));
			assertEquals(end, 0, plugin.get("taskProgress"));
			assertNull(end, plugin.get("mapPoint"));
			try
			{
				reader.name(UNREADABLE, 42, 0);
				fail(end + " kept the name the reader looked up");
			}
			catch (IllegalStateException expected)
			{
				// Forgotten, so the next login reads the tables again
			}
		}
	}

	/** Tables that cannot be read. */
	private static final SlayerGameData.Db UNREADABLE = new SlayerGameData.Db()
	{
		@Override
		public List<Integer> rows(int table)
		{
			throw new IllegalStateException("table not readable");
		}

		@Override
		public Object[] field(int row, int column, int tupleIndex)
		{
			throw new IllegalStateException("table not readable");
		}
	};

	private static GameStateChanged gameState(GameState state)
	{
		final GameStateChanged event = new GameStateChanged();
		event.setGameState(state);
		return event;
	}

	/**
	 * The plugin with a fake client and config and the world map's markers. Nothing else is injected, so a tick that used
	 * anything else would throw.
	 */
	private static final class Harness
	{
		final SlayerCompanionPlugin plugin = new SlayerCompanionPlugin();
		final GameState state;
		final Map<Integer, Integer> varps;
		final Map<Integer, Integer> varbits = new HashMap<>();
		boolean failTableReads;
		/** Off, any config read fails the test. On, every setting is off. */
		boolean configAnswers;
		final SlayerTask dossier = DATASET.lookupOrFallback("Abyssal demons");
		final TaskMapPoint marker = new TaskMapPoint(CATACOMBS, "Slayer task: Abyssal Demons");
		final Instant started = Instant.now().minusSeconds(600);

		Harness(GameState state, Map<Integer, Integer> varps) throws Exception
		{
			this.state = state;
			this.varps = varps;
			set("client", Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Client.class},
				(proxy, method, args) -> client(method, args)));
			set("config", Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{SlayerCompanionConfig.class},
				(proxy, method, args) -> config(method)));
			set("worldMapPointManager", new WorldMapPointManager());
		}

		/** Abyssal demons, 112 of 150 left, 38 killed so far, the marker up and the new-task window open. */
		void onTask() throws Exception
		{
			set("taskName", "Abyssal Demons");
			set("currentTask", dossier);
			set("taskAmount", "112");
			set("taskInitialAmount", "150");
			set("taskStreak", "47");
			set("slayerPoints", "521");
			set("taskMaster", SlayerAssigner.KONAR);
			set("taskIntroUntilTick", 500);
			set("lastProgressTick", 400);
			set("taskProgress", 38);
			set("lastRemainingAmount", 112);
			set("lastInitialAmount", 150);
			set("trackingStart", started);
			((WorldMapPointManager) get("worldMapPointManager")).add(marker);
			set("mapPoint", marker);
		}

		void tick()
		{
			plugin.onGameTick(new GameTick());
		}

		void assertTaskKept() throws Exception
		{
			assertEquals("Abyssal Demons", get("taskName"));
			assertSame(dossier, get("currentTask"));
			assertEquals("112", get("taskAmount"));
			assertEquals("150", get("taskInitialAmount"));
			assertEquals("47", get("taskStreak"));
			assertEquals("521", get("slayerPoints"));
			assertEquals(SlayerAssigner.KONAR, get("taskMaster"));
			assertEquals("the new-task window", 500, get("taskIntroUntilTick"));
			assertEquals(400, get("lastProgressTick"));
			assertEquals("the rate", 38, get("taskProgress"));
			assertEquals(112, get("lastRemainingAmount"));
			assertEquals(150, get("lastInitialAmount"));
			assertSame(started, get("trackingStart"));
			assertSame("the marker", marker, get("mapPoint"));
		}

		private Object client(Method method, Object[] args)
		{
			switch (method.getName())
			{
				case "getGameState":
					return state;
				case "getVarpValue":
					if (varps != null) return varps.getOrDefault((Integer) args[0], 0);
					break;
				case "getVarbitValue":
					if (varps != null) return varbits.getOrDefault((Integer) args[0], 0);
					break;
				case "getDBRowsByValue":
					if (failTableReads) throw new IllegalStateException("table not readable");
					break;
				case "hashCode":
					return System.identityHashCode(this);
				case "toString":
					return "client " + state;
				default:
					break;
			}
			throw new AssertionError("a tick on " + state + " asked the client for " + method.getName());
		}

		private Object config(Method method)
		{
			if (!configAnswers)
			{
				throw new AssertionError("a tick on " + state + " read the setting " + method.getName());
			}
			return method.getReturnType() == boolean.class ? Boolean.FALSE : null;
		}

		void set(String field, Object value) throws Exception
		{
			final Field f = SlayerCompanionPlugin.class.getDeclaredField(field);
			f.setAccessible(true);
			f.set(plugin, value);
		}

		Object get(String field) throws Exception
		{
			final Field f = SlayerCompanionPlugin.class.getDeclaredField(field);
			f.setAccessible(true);
			return f.get(plugin);
		}
	}
}
