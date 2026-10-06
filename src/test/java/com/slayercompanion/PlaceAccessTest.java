package com.slayercompanion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.slayercompanion.data.Access;
import com.slayercompanion.data.SlayerLocation;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import org.junit.Test;

/** Whether a place is open for an account, decided from its requirements, the quest states and the real levels alone. */
public class PlaceAccessTest
{
	private static Access access(List<String> finished, List<String> started, Map<String, Integer> skills, String note)
	{
		final Access access = new Access();
		access.setQuestsFinished(finished);
		access.setQuestsStarted(started);
		access.setSkills(skills);
		access.setNote(note);
		access.setSource("test");
		return access;
	}

	private static Access finished(String... quests)
	{
		return access(Arrays.asList(quests), null, null, null);
	}

	private static Access started(String... quests)
	{
		return access(null, Arrays.asList(quests), null, null);
	}

	private static Access level(String skill, int level)
	{
		return access(null, null, Collections.singletonMap(skill, level), null);
	}

	private static PlaceAccess.Account quests(Object... nameAndState)
	{
		final Map<String, QuestState> states = new HashMap<>();
		for (int i = 0; i + 1 < nameAndState.length; i += 2)
		{
			states.put((String) nameAndState[i], (QuestState) nameAndState[i + 1]);
		}
		return new PlaceAccess.Account(states, levels());
	}

	/** Every real level 1, then the given ones. */
	private static int[] levels(Object... skillAndLevel)
	{
		final int[] levels = new int[Skill.values().length];
		Arrays.fill(levels, 1);
		for (int i = 0; i + 1 < skillAndLevel.length; i += 2)
		{
			levels[((Skill) skillAndLevel[i]).ordinal()] = (Integer) skillAndLevel[i + 1];
		}
		return levels;
	}

	private static PlaceAccess.Verdict check(Access access, PlaceAccess.Account account)
	{
		return PlaceAccess.check(Collections.singletonList(access), account);
	}

	@Test
	public void aQuestThatMustBeFinishedLocksUntilItIs()
	{
		final Access tower = finished("PRIEST_IN_PERIL");
		for (QuestState state : new QuestState[]{QuestState.NOT_STARTED, QuestState.IN_PROGRESS})
		{
			final PlaceAccess.Verdict verdict = check(tower, quests("PRIEST_IN_PERIL", state));
			assertTrue(state.name(), verdict.isLocked());
			assertEquals("Needs Priest in Peril", verdict.needs());
		}
		final PlaceAccess.Verdict done = check(tower, quests("PRIEST_IN_PERIL", QuestState.FINISHED));
		assertFalse(done.isLocked());
		assertNull(done.needs());
		assertSame("open with nothing to say", PlaceAccess.Verdict.OPEN, done);
	}

	@Test
	public void aQuestThatMustBeStartedLocksOnlyBeforeItIsStarted()
	{
		final Access dungeon = started("WATERFALL_QUEST");
		assertEquals("Needs Waterfall Quest started", check(dungeon, quests("WATERFALL_QUEST", QuestState.NOT_STARTED)).needs());
		assertFalse(check(dungeon, quests("WATERFALL_QUEST", QuestState.IN_PROGRESS)).isLocked());
		assertFalse(check(dungeon, quests("WATERFALL_QUEST", QuestState.FINISHED)).isLocked());
	}

	@Test
	public void aRealLevelBelowTheRequirementLocks()
	{
		final Access island = level("SAILING", 60);
		final PlaceAccess.Verdict low = check(island, new PlaceAccess.Account(Collections.emptyMap(), levels(Skill.SAILING, 59)));
		assertEquals("Needs 60 Sailing", low.needs());
		assertFalse(check(island, new PlaceAccess.Account(Collections.emptyMap(), levels(Skill.SAILING, 60))).isLocked());
		assertFalse(check(island, new PlaceAccess.Account(Collections.emptyMap(), levels(Skill.SAILING, 99))).isLocked());
	}

	@Test
	public void whatIsNotKnownLocksNothing()
	{
		// A quest that was not read, a level that is not loaded yet, and an account read before anything
		assertFalse(check(finished("PRIEST_IN_PERIL"), quests()).isLocked());
		assertFalse(check(level("SAILING", 60), new PlaceAccess.Account(Collections.emptyMap(), levels(Skill.SAILING, 0))).isLocked());
		assertFalse(check(level("SAILING", 60), new PlaceAccess.Account(Collections.emptyMap(), new int[0])).isLocked());
		assertFalse(check(finished("PRIEST_IN_PERIL"), PlaceAccess.Account.UNKNOWN).isLocked());
	}

	@Test
	public void aNameTheClientDoesNotKnowLocksNothingAndDoesNotThrow()
	{
		// Data newer than the client may name a quest or skill this client has no constant for
		final Access future = access(Collections.singletonList("A_QUEST_FROM_THE_FUTURE"), Collections.singletonList("ANOTHER_ONE"),
				Collections.singletonMap("FUTURE_SKILL", 50), null);
		final PlaceAccess.Account account = quests("A_QUEST_FROM_THE_FUTURE", QuestState.NOT_STARTED, "ANOTHER_ONE", QuestState.NOT_STARTED);
		assertFalse(check(future, account).isLocked());
		assertNull(PlaceAccess.quest("A_QUEST_FROM_THE_FUTURE"));
		assertNull(PlaceAccess.skill("FUTURE_SKILL"));
		assertNull(PlaceAccess.quest(null));
		final PlaceAccess.Account read = PlaceAccess.Account.read(Arrays.asList("A_QUEST_FROM_THE_FUTURE", "PRIEST_IN_PERIL"),
				quest -> QuestState.FINISHED, null);
		assertEquals("only the known quest is read", Collections.singleton("PRIEST_IN_PERIL"), read.getQuests().keySet());
	}

	@Test
	public void everyMissingRequirementIsNamedOnce()
	{
		final List<Access> place = Arrays.asList(
				access(Collections.singletonList("PRIEST_IN_PERIL"), Collections.singletonList("SINS_OF_THE_FATHER"),
						Collections.singletonMap("SAILING", 60), null),
				// The task's own requirement there asks again for a quest the place already names
				access(Collections.singletonList("SINS_OF_THE_FATHER"), Collections.singletonList("PRIEST_IN_PERIL"), null, null));
		final PlaceAccess.Account account = new PlaceAccess.Account(quests("PRIEST_IN_PERIL", QuestState.IN_PROGRESS,
				"SINS_OF_THE_FATHER", QuestState.NOT_STARTED).getQuests(), levels(Skill.SAILING, 50));
		final PlaceAccess.Verdict verdict = PlaceAccess.check(place, account);
		assertEquals(Arrays.asList("Priest in Peril", "Sins of the Father", "60 Sailing"), verdict.getMissing());
		assertEquals("Needs Priest in Peril, Sins of the Father and 60 Sailing", verdict.needs());
		assertEquals("Needs Priest in Peril, Sins of the Father and 60 Sailing.", verdict.sentence());
	}

	@Test
	public void aNoteIsShownAndNeverLocks()
	{
		final Access gwd = access(null, null, null, "Needs 60 Strength or 60 Agility, boosts count.");
		final PlaceAccess.Verdict open = check(gwd, PlaceAccess.Account.UNKNOWN);
		assertFalse(open.isLocked());
		assertNull(open.needs());
		assertEquals("Needs 60 Strength or 60 Agility, boosts count.", open.sentence());
		final Access both = access(Collections.singletonList("PRIEST_IN_PERIL"), null, null, "A note.");
		assertEquals("Needs Priest in Peril. A note.", check(both, quests("PRIEST_IN_PERIL", QuestState.NOT_STARTED)).sentence());
	}

	private static final String JSON = "{\"locations\": ["
			+ "{\"key\": \"slayer_tower\", \"label\": \"Slayer Tower\","
			+ " \"access\": {\"questsFinished\": [\"PRIEST_IN_PERIL\"], \"source\": \"test\"},"
			+ " \"tasks\": [{\"taskId\": 48, \"access\": {\"questsStarted\": [\"SINS_OF_THE_FATHER\"], \"source\": \"test\"}},"
			+ "   {\"taskId\": 41}]},"
			+ "{\"key\": \"open_field\", \"label\": \"Open field\", \"tasks\": [{\"taskId\": 41}]},"
			+ "{\"key\": \"other\", \"label\": \"Other\", \"tasks\": [{\"taskId\": 99, \"access\": {\"questsFinished\": [\"DRAGON_SLAYER_II\"], \"source\": \"test\"}}]}"
			+ "]}";

	@Test
	public void aPlaceNeedsItsOwnAccessAndThatOfTheTaskThere()
	{
		final SlayerLocations data = new SlayerLocations(new Gson(), new StringReader(JSON));
		final SlayerLocation tower = data.byKey("slayer_tower");
		assertEquals(2, PlaceAccess.of(tower, 48).size());
		assertEquals("another task there needs only the place's own", 1, PlaceAccess.of(tower, 41).size());
		assertTrue(PlaceAccess.of(data.byKey("open_field"), 41).isEmpty());
		assertTrue(PlaceAccess.of(null, 41).isEmpty());
		final PlaceAccess.Account account = quests("PRIEST_IN_PERIL", QuestState.FINISHED, "SINS_OF_THE_FATHER", QuestState.NOT_STARTED);
		assertEquals("Needs Sins of the Father started", PlaceAccess.check(PlaceAccess.of(tower, 48), account).needs());
		assertFalse(PlaceAccess.check(PlaceAccess.of(tower, 41), account).isLocked());
	}

	@Test
	public void onlyTheQuestsATasksPlacesNameAreRead()
	{
		final SlayerLocations data = new SlayerLocations(new Gson(), new StringReader(JSON));
		final List<SlayerLocation> places = new ArrayList<>(data.all());
		assertEquals(new LinkedHashSet<>(Arrays.asList("PRIEST_IN_PERIL", "SINS_OF_THE_FATHER")), PlaceAccess.quests(places, 48));
		assertEquals(Collections.singleton("PRIEST_IN_PERIL"), PlaceAccess.quests(places, 41));
		final List<Quest> asked = new ArrayList<>();
		final PlaceAccess.Account account = PlaceAccess.Account.read(PlaceAccess.quests(places, 41), quest ->
		{
			asked.add(quest);
			return QuestState.IN_PROGRESS;
		}, levels());
		assertEquals(Collections.singletonList(Quest.PRIEST_IN_PERIL), asked);
		assertEquals(QuestState.IN_PROGRESS, account.getQuests().get("PRIEST_IN_PERIL"));
	}

	@Test
	public void aQuestWhoseStateCannotBeReadStaysUnknown()
	{
		final PlaceAccess.Account account = PlaceAccess.Account.read(Arrays.asList("PRIEST_IN_PERIL", "LOST_CITY"), quest ->
		{
			if (quest == Quest.PRIEST_IN_PERIL) throw new IllegalStateException("script failed");
			return QuestState.NOT_STARTED;
		}, levels());
		assertFalse(account.getQuests().containsKey("PRIEST_IN_PERIL"));
		assertFalse(check(finished("PRIEST_IN_PERIL"), account).isLocked());
		assertTrue(check(finished("LOST_CITY"), account).isLocked());
	}

	@Test
	public void theLevelsAreCopiedSoTheAccountCanBeCompared()
	{
		final int[] live = levels(Skill.AGILITY, 50);
		final PlaceAccess.Account before = PlaceAccess.Account.read(Collections.emptyList(), quest -> null, live);
		live[Skill.AGILITY.ordinal()] = 51;
		final PlaceAccess.Account after = PlaceAccess.Account.read(Collections.emptyList(), quest -> null, live);
		assertFalse("a level gained is a change", before.equals(after));
		assertEquals(after, PlaceAccess.Account.read(Collections.emptyList(), quest -> null, live));
	}
}
