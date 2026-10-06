package com.slayercompanion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import org.junit.Test;

/**
 * When the plugin reads what the task's places need. A quest state runs a client script, so only the quests the
 * current task's places name are read: for a new task, after a login, and then at most every QUEST_REFRESH_TICKS ticks.
 * Konar's assigned area is never checked, since she only assigns areas the player can enter. The client here counts
 * every quest read.
 */
public class SlayerCompanionPlaceAccessTest
{
	private static final int SPECTRES = 41;
	private static final int DARK_BEASTS = 66;
	private static final int NIEVE = 6;
	private static final int KONAR = 8;
	private static final int SLAYER_TOWER_AREA = 9;

	private static final String JSON = "{\"locations\": ["
			+ "{\"key\": \"slayer_tower\", \"label\": \"Slayer Tower\", \"areaId\": 9, \"entrance\": [3416, 3535, 0], \"entranceSource\": \"test\","
			+ " \"access\": {\"questsFinished\": [\"PRIEST_IN_PERIL\"], \"source\": \"test\"}},"
			+ "{\"key\": \"mourner_tunnels\", \"label\": \"Mourner Tunnels\", \"entrance\": [2542, 3327, 0], \"entranceSource\": \"test\","
			+ " \"access\": {\"questsStarted\": [\"MOURNINGS_END_PART_II\"], \"source\": \"test\"}}"
			+ "], \"taskLocations\": ["
			+ "{\"taskId\": 41, \"locations\": [\"slayer_tower\"]},"
			+ "{\"taskId\": 66, \"locations\": [\"mourner_tunnels\"]}"
			+ "]}";

	/** The plugin with a client that counts quest reads, a config that answers off, and the places above. */
	private static final class Harness
	{
		final SlayerCompanionPlugin plugin = new SlayerCompanionPlugin();
		final List<Integer> questsRead = new ArrayList<>();
		/** Quest id to the state code the quest script leaves: 1 not started, 2 finished, other in progress. */
		final Map<Integer, Integer> states = new HashMap<>();
		int tick = 1000;
		private int lastQuest;

		Harness() throws Exception
		{
			set("client", Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Client.class}, (proxy, method, args) ->
			{
				switch (method.getName())
				{
					case "runScript":
						final Object[] script = (Object[]) args[0];
						lastQuest = (Integer) script[1];
						questsRead.add(lastQuest);
						return null;
					case "getIntStack":
						return new int[]{states.getOrDefault(lastQuest, 1)};
					case "getTickCount":
						return tick;
					case "getRealSkillLevels":
						final int[] levels = new int[Skill.values().length];
						Arrays.fill(levels, 50);
						return levels;
					case "getGameState":
						return GameState.LOGGED_IN;
					case "hashCode":
						return System.identityHashCode(proxy);
					default:
						throw new AssertionError("the place checks asked the client for " + method.getName());
				}
			}));
			set("config", Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{SlayerCompanionConfig.class},
					(proxy, method, args) -> method.getReturnType() == boolean.class ? Boolean.FALSE : null));
			set("locations", new SlayerLocations(new Gson(), new StringReader(JSON)));
		}

		/** A task from a master, as readAssignedArea leaves it, resolved as a changed task is. */
		void task(int taskId, int masterId, int areaId) throws Exception
		{
			set("liveTaskId", taskId);
			set("liveMasterId", masterId);
			set("assignedAreaId", areaId);
			call("updateDestination");
		}

		void call(String name) throws Exception
		{
			final Method m = SlayerCompanionPlugin.class.getDeclaredMethod(name);
			m.setAccessible(true);
			m.invoke(plugin);
		}

		void set(String field, Object value) throws Exception
		{
			final Field f = SlayerCompanionPlugin.class.getDeclaredField(field);
			f.setAccessible(true);
			f.set(plugin, value);
		}

		TaskPlaces places()
		{
			return plugin.getPlaces();
		}
	}

	@Test
	public void aNewTaskReadsOnlyTheQuestsItsPlacesName() throws Exception
	{
		final Harness h = new Harness();
		h.task(SPECTRES, NIEVE, 0);
		assertEquals(Collections.singletonList(Quest.PRIEST_IN_PERIL.getId()), h.questsRead);
		assertTrue("Priest in Peril is not started", h.places().chosen().isLocked());
		assertEquals("Needs Priest in Peril.", h.places().accessLine());

		h.questsRead.clear();
		h.task(DARK_BEASTS, NIEVE, 0);
		assertEquals("another task reads its own", Collections.singletonList(Quest.MOURNINGS_END_PART_II.getId()), h.questsRead);
	}

	@Test
	public void resolvingTheSameTaskAgainReadsNothing() throws Exception
	{
		final Harness h = new Harness();
		h.task(SPECTRES, NIEVE, 0);
		h.questsRead.clear();
		// A new pick or a config change resolves the place again on the same task
		h.call("updateDestination");
		h.call("updateDestination");
		assertTrue(h.questsRead.isEmpty());
	}

	@Test
	public void theChecksAreReadAgainAtMostEveryRefreshTicks() throws Exception
	{
		final Harness h = new Harness();
		h.task(SPECTRES, NIEVE, 0);
		h.questsRead.clear();
		final int read = h.tick;
		for (int t = read; t < read + SlayerCompanionPlugin.QUEST_REFRESH_TICKS; t++)
		{
			h.tick = t;
			h.call("refreshPlaceAccount");
		}
		assertTrue("never on every tick", h.questsRead.isEmpty());
		h.tick = read + SlayerCompanionPlugin.QUEST_REFRESH_TICKS;
		h.call("refreshPlaceAccount");
		assertEquals(Collections.singletonList(Quest.PRIEST_IN_PERIL.getId()), h.questsRead);
		h.call("refreshPlaceAccount");
		assertEquals("once, then the wait starts over", 1, h.questsRead.size());
	}

	@Test
	public void aQuestFinishedSinceTheLastReadOpensThePlaceAtTheNextRefresh() throws Exception
	{
		final Harness h = new Harness();
		h.task(SPECTRES, NIEVE, 0);
		assertTrue(h.places().chosen().isLocked());
		h.states.put(Quest.PRIEST_IN_PERIL.getId(), 2);
		h.call("refreshPlaceAccount");
		assertTrue("not before the refresh is due", h.places().chosen().isLocked());
		h.tick += SlayerCompanionPlugin.QUEST_REFRESH_TICKS;
		h.call("refreshPlaceAccount");
		assertFalse(h.places().chosen().isLocked());
		assertNull(h.places().accessLine());
	}

	@Test
	public void aLoginReadsTheChecksAgain() throws Exception
	{
		final Harness h = new Harness();
		h.task(SPECTRES, NIEVE, 0);
		h.questsRead.clear();
		// Logging out, hopping and switching profiles reset the task state, and the next login resolves the task anew
		h.call("resetTaskState");
		h.task(SPECTRES, NIEVE, 0);
		assertEquals(Collections.singletonList(Quest.PRIEST_IN_PERIL.getId()), h.questsRead);
	}

	@Test
	public void konarsAreaIsNeverCheckedOrLocked() throws Exception
	{
		final Harness h = new Harness();
		h.task(SPECTRES, KONAR, SLAYER_TOWER_AREA);
		assertTrue("nothing is read for her task", h.questsRead.isEmpty());
		assertNull("her area is not a pick, so it has no lock to show", h.places());
		final Destination d = h.plugin.getDestination();
		assertEquals(Destination.Origin.ASSIGNED, d.getOrigin());
		assertEquals("slayer_tower", d.getKey());
		h.tick += SlayerCompanionPlugin.QUEST_REFRESH_TICKS;
		h.call("refreshPlaceAccount");
		assertTrue(h.questsRead.isEmpty());
	}
}
