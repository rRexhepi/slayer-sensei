package com.slayercompanion;

import static com.slayercompanion.SlayerPoolTest.entry;
import static com.slayercompanion.SlayerPoolTest.rating;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.slayercompanion.SlayerPointsPlanner.Candidate;
import com.slayercompanion.SlayerPool.Status;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.Skill;
import net.runelite.api.gameval.DBTableID;
import org.junit.Test;

/** How each way of spending points is valued for a goal. */
public class SlayerSpendTest
{
	private static final SlayerGameData DATA = SlayerMasterRankerTest.DATA;
	private static final SlayerDataset DATASET = new SlayerDataset(new Gson());

	/** A long excellent XP task and a short poor one. */
	private static final SlayerPool PAIR = SlayerPool.of(SlayerAssigner.DURADEL, Arrays.asList(
			entry(1, 1, rating(5, 1, 2), Status.OPEN),
			entry(2, 1, rating(1, 5, 5), Status.OPEN)));

	@Test
	public void aLongerTaskHelpsWhenItBeatsTheAverageAndNeverHelpsPoints()
	{
		final SlayerPool longer = PAIR.map(e -> e.getTaskRow() == 1 ? e.withAmounts(200, 200) : e);
		assertTrue(SlayerSpend.change(PAIR, longer, SlayerGoal.XP) > 0);
		assertTrue(SlayerSpend.change(PAIR, longer, SlayerGoal.PROFIT) < 0);
		assertTrue(SlayerSpend.change(PAIR, longer, SlayerGoal.POINTS) < 0);
	}

	@Test
	public void helmetTasksComeFromTheGearCheck()
	{
		for (String task : Arrays.asList("Aberrant Spectres", "Banshees", "Dust Devils", "Smoke Devils", "Wall Beasts", "Sourhogs"))
		{
			assertTrue(task, SlayerSpend.helmetTask(named(task)));
		}
		assertFalse(SlayerSpend.helmetTask(named("Kurask")));
	}

	private static SlayerPool.Entry named(String name)
	{
		return new SlayerPool.Entry(1, name, 1, 100, 100, 100, Status.OPEN, rating(1, 1, 1));
	}

	@Test
	public void ratingsReadAsWords()
	{
		assertEquals("an excellent XP task", SlayerSpend.judge(entry(1, 1, rating(5, 1, 1), Status.OPEN), SlayerGoal.XP));
		assertEquals("a very poor money task", SlayerSpend.judge(entry(1, 1, rating(5, 1, 1), Status.OPEN), SlayerGoal.PROFIT));
		assertEquals("a very slow task", SlayerSpend.judge(entry(1, 1, rating(5, 1, 1), Status.OPEN), SlayerGoal.POINTS));
		assertEquals("a task of average length", SlayerSpend.judge(entry(1, 1, rating(5, 1, 3), Status.OPEN), SlayerGoal.POINTS));
		assertEquals("A and B", SlayerSpend.join(Arrays.asList("A", "B")));
		assertEquals("A, B and C", SlayerSpend.join(Arrays.asList("A", "B", "C")));
		assertEquals("Konar's", SlayerSpend.possessive("Konar"));
		assertEquals("Spria's", SlayerSpend.possessive("Spria"));
	}

	@Test
	public void theMissingLevelIsTheFirstTheTaskNeeds()
	{
		final int kraken = DBTableID.SlayerTask.Row.SLAYER_TARGET_CAVEKRAKEN;
		final int[] levels = SlayerMasterRankerTest.levels(99);
		levels[Skill.SLAYER.ordinal()] = 86;
		assertEquals("87 Slayer", SlayerSpend.missingLevel(DATA, player(levels, 126), SlayerAssigner.KONAR, kraken));
		levels[Skill.SLAYER.ordinal()] = 99;
		levels[Skill.MAGIC.ordinal()] = 49;
		assertEquals("50 Magic", SlayerSpend.missingLevel(DATA, player(levels, 126), SlayerAssigner.KONAR, kraken));
		levels[Skill.MAGIC.ordinal()] = 99;
		assertEquals("combat level 80", SlayerSpend.missingLevel(DATA, player(levels, 79), SlayerAssigner.KONAR, kraken));
		// Krystilia does not check combat level
		assertNull(SlayerSpend.missingLevel(DATA, player(levels, 79), SlayerAssigner.KRYSTILIA, kraken));
		levels[Skill.STRENGTH.ordinal()] = 59;
		levels[Skill.AGILITY.ordinal()] = 59;
		assertEquals("60 Strength or 60 Agility", SlayerSpend.missingLevel(DATA, player(levels, 126), SlayerAssigner.DURADEL,
				DBTableID.SlayerTask.Row.SLAYER_TARGET_SPIRITUALGWD));
	}

	private static SlayerPool.Player player(int[] levels, int combat)
	{
		return new SlayerPool.Player(levels, combat, false, Collections.<Integer>emptySet(), Collections.<Integer>emptySet());
	}

	private static List<Candidate> at(SlayerAssigner master, SlayerGoal goal)
	{
		final SlayerPool pool = SlayerPool.of(master, DATA, SlayerMasterRankerTest.maxed(), Collections.<Integer>emptySet(),
				SlayerMasterRankerTest.RATINGS);
		final SlayerMaster list = SlayerMaster.forAssigner(master);
		final SlayerBlockAdvisor.Advice advice = SlayerBlockAdvisor.advise(list, pool, goal, list.getBlockVarbits().length,
				Collections.<Integer>emptyList());
		return SlayerSpend.candidates(new SlayerSpend.Context(goal, master, master.getDisplayName(), pool, DATA, SlayerMasterRankerTest.maxed(),
				Collections.<Integer>emptySet(), list.getBlockVarbits().length, advice, Collections.<Integer>emptySet(),
				SlayerPlanBuilder.dossier(DATASET), SlayerShopItem.Sightings.NONE, null, SlayerPlanBuilder.skipBudget(goal, master,
				SlayerPayout.Diaries.NONE, 0)), SlayerReward::getFallbackName, r -> null, i -> null);
	}

	private static Candidate find(List<Candidate> candidates, String name)
	{
		for (Candidate c : candidates)
		{
			if (c.getName().equals(name)) return c;
		}
		return null;
	}

	@Test
	public void documentedEffectsFollowTheGoal()
	{
		final List<Candidate> xp = at(SlayerAssigner.DURADEL, SlayerGoal.XP);
		final List<Candidate> profit = at(SlayerAssigner.DURADEL, SlayerGoal.PROFIT);
		final List<Candidate> points = at(SlayerAssigner.DURADEL, SlayerGoal.POINTS);
		// Superiors are worth XP, a little loot, and only time for Slayer points
		assertTrue(find(xp, "Bigger and Badder").getGain() > find(profit, "Bigger and Badder").getGain());
		assertEquals(0, find(points, "Bigger and Badder").getGain(), 0);
		// The herb sack only helps profit
		assertTrue(find(profit, "Herb sack").getGain() > SlayerPointsPlanner.NOTICEABLE);
		assertEquals(0, find(xp, "Herb sack").getGain(), 0);
		// The helmet and the rune pouch speed tasks up, so they help all three
		for (List<Candidate> goal : Arrays.asList(xp, profit, points))
		{
			assertTrue(find(goal, "Malevolent Masquerade").getGain() > SlayerPointsPlanner.NOTICEABLE);
			assertTrue(find(goal, "Rune pouch").getGain() > SlayerPointsPlanner.NOTICEABLE);
			// Neither helps any goal enough to list: preference, Fletching and ironman rewards
			assertEquals(0, find(goal, "Task Storage").getGain(), 0);
			assertEquals(0, find(goal, "Broader Fletching").getGain(), 0);
			assertEquals(0, find(goal, "Duly Noted").getGain(), 0);
			assertEquals(0, find(goal, "Double Trouble").getGain(), 0);
			// The looting bag only on Krystilia's tasks
			assertEquals(0, find(goal, "Looting bag").getGain(), 0);
		}
		assertTrue(find(at(SlayerAssigner.KRYSTILIA, SlayerGoal.PROFIT), "Looting bag").getGain() > SlayerPointsPlanner.NOTICEABLE);
	}

	@Test
	public void theListIsJudgedWithTheAdvisedBlocksInPlace()
	{
		// Metal dragons are a poor XP task the advice blocks at Konar, so lengthening them is not worth anything there
		final List<Candidate> xp = at(SlayerAssigner.KONAR, SlayerGoal.XP);
		assertTrue(find(xp, "Block Metal Dragons") != null);
		assertEquals(0, find(xp, "Pedal to the Metals").getGain(), 0);
		// For profit they are a good money task, but the wiki discourages extending them, so the extension is never worth points
		final List<Candidate> profit = at(SlayerAssigner.KONAR, SlayerGoal.PROFIT);
		assertTrue(find(profit, "Pedal to the Metals").getGain() <= 0);
		// Skeletal wyverns are a good money task the wiki has nothing against, so they are lengthened
		assertTrue(find(profit, "Wyver-nother one").getGain() > SlayerPointsPlanner.NOTICEABLE);
	}

	@Test
	public void onlyTasksRatedGoodAreWorthMoreTime()
	{
		assertTrue(SlayerSpend.extendable(entry(1, 1, rating(4, 1, 3), Status.OPEN), SlayerGoal.XP));
		assertFalse(SlayerSpend.extendable(entry(1, 1, rating(3, 5, 3), Status.OPEN), SlayerGoal.XP));
		assertTrue(SlayerSpend.extendable(entry(1, 1, rating(3, 5, 3), Status.OPEN), SlayerGoal.PROFIT));
		final com.slayercompanion.data.TaskRating discouraged = rating(5, 5, 3);
		discouraged.setExtensionDiscouraged(true);
		assertFalse(SlayerSpend.extendable(entry(1, 1, discouraged, Status.OPEN), SlayerGoal.PROFIT));
		assertFalse(SlayerSpend.extendable(entry(1, 1, null, Status.OPEN), SlayerGoal.XP));
	}

	@Test
	public void pointsHaveOneLineForEveryExtension()
	{
		final List<Candidate> points = at(SlayerAssigner.KONAR, SlayerGoal.POINTS);
		assertTrue(Double.isNaN(find(points, "Augment My Abbies").getGain()));
		final Candidate summary = find(points, "Task extensions");
		assertEquals(SlayerPointsPlanner.Type.SUMMARY, summary.getType());
		assertTrue(summary.getGain() < 0);
		assertNull(find(at(SlayerAssigner.KONAR, SlayerGoal.XP), "Task extensions"));
	}

	@Test
	public void theRoutineValuesBlocksAtBothListsOnItsCycle()
	{
		// Konar takes the milestone tasks, Aya the nine filler tasks between them. Neither list has a block yet.
		final SlayerPool konar = SlayerPool.of(SlayerAssigner.KONAR, DATA, SlayerMasterRankerTest.maxed(), Collections.<Integer>emptySet(),
				SlayerMasterRankerTest.RATINGS);
		final SlayerPool aya = SlayerPool.of(SlayerAssigner.TURAEL, DATA, SlayerMasterRankerTest.maxed(), Collections.<Integer>emptySet(),
				SlayerMasterRankerTest.RATINGS);
		final SlayerBlockAdvisor.Advice konarBlocks = SlayerBlockAdvisor.advise(SlayerMaster.KONAR, konar, SlayerGoal.POINTS, 7,
				Collections.<Integer>emptyList());
		final SlayerBlockAdvisor.Advice ayaBlocks = SlayerBlockAdvisor.advise(SlayerMaster.TURAEL, aya, SlayerGoal.POINTS, 7,
				Collections.<Integer>emptyList());
		final double points = 18 * SlayerPayout.averageMultiplier(10);
		final SlayerSpend.Routine routine = new SlayerSpend.Routine(9, points, "Aya", aya, 7, ayaBlocks);
		final List<Candidate> all = SlayerSpend.candidates(new SlayerSpend.Context(SlayerGoal.POINTS, SlayerAssigner.KONAR, "Konar", konar, DATA,
				SlayerMasterRankerTest.maxed(), Collections.<Integer>emptySet(), 7, konarBlocks, Collections.<Integer>emptySet(),
				SlayerPlanBuilder.dossier(DATASET), SlayerShopItem.Sightings.NONE, routine, 0), SlayerReward::getFallbackName, r -> null, i -> null);

		// Each list's first block, valued on the whole cycle against today's rate, at its own master's cost
		final double base = SlayerGoalModel.cycleRate(aya, konar, 9, points);
		final int bugs = ayaBlocks.getAddRows().get(0);
		final Candidate ayaFirst = find(all, "Block Cave Bugs at Aya");
		assertEquals(bugs, (int) DATA.taskRowForId(63));
		assertEquals(40, ayaFirst.getCost());
		assertEquals(SlayerMaster.TURAEL, ayaFirst.getList());
		assertEquals((SlayerGoalModel.cycleRate(aya.withBlocks(Collections.singletonList(bugs)), konar, 9, points) - base) / base,
				ayaFirst.getGain(), 1e-12);
		final int hydras = konarBlocks.getAddRows().get(0);
		final Candidate konarFirst = find(all, "Block Hydras at Konar");
		assertEquals(80, konarFirst.getCost());
		assertEquals(SlayerMaster.KONAR, konarFirst.getList());
		assertEquals((SlayerGoalModel.cycleRate(aya, konar.withBlocks(Collections.singletonList(hydras)), 9, points) - base) / base,
				konarFirst.getGain(), 1e-12);
		// Every block either list's advice puts forward is there once, one per open slot
		int konarLines = 0;
		int ayaLines = 0;
		for (Candidate c : all)
		{
			if (c.getType() != SlayerPointsPlanner.Type.BLOCK) continue;
			if (c.getList() == SlayerMaster.KONAR) konarLines++;
			if (c.getList() == SlayerMaster.TURAEL) ayaLines++;
			assertTrue(c.getGain() > 0);
		}
		assertEquals(konarBlocks.getAddRows().size(), konarLines);
		assertEquals(7, ayaLines);
		// A reward for a filler task counts on the routine: with no slot on Aya's list her lizards stay, and Konar never
		// gives them. With the slots, lizards are among the blocks, so the reward is worth nothing there.
		final SlayerBlockAdvisor.Advice noSlots = SlayerBlockAdvisor.advise(SlayerMaster.TURAEL, aya, SlayerGoal.POINTS, 0,
				Collections.<Integer>emptyList());
		final List<Candidate> unblocked = SlayerSpend.candidates(new SlayerSpend.Context(SlayerGoal.POINTS, SlayerAssigner.KONAR, "Konar", konar,
				DATA, SlayerMasterRankerTest.maxed(), Collections.<Integer>emptySet(), 7, konarBlocks, Collections.<Integer>emptySet(),
				SlayerPlanBuilder.dossier(DATASET), SlayerShopItem.Sightings.NONE, new SlayerSpend.Routine(9, points, "Aya", aya, 0, noSlots), 0),
				SlayerReward::getFallbackName, r -> null, i -> null);
		assertTrue(find(unblocked, "Reptile Freezer").getGain() > 0);
		assertEquals(0, find(all, "Reptile Freezer").getGain(), 0);
		assertEquals(0, find(at(SlayerAssigner.KONAR, SlayerGoal.POINTS), "Reptile Freezer").getGain(), 0);
	}

	/** Stop the Wyvern for XP at Steve's spot over this list, with this many open slots and the block advice for them. */
	private static Candidate wyvernBlock(SlayerPool pool, int slots)
	{
		final SlayerBlockAdvisor.Advice advice = SlayerBlockAdvisor.advise(SlayerMaster.NIEVE, pool, SlayerGoal.XP, slots,
				Collections.<Integer>emptyList());
		final List<Candidate> all = SlayerSpend.candidates(new SlayerSpend.Context(SlayerGoal.XP, SlayerAssigner.NIEVE, "Steve", pool, DATA,
				SlayerMasterRankerTest.maxed(), Collections.<Integer>emptySet(), slots, advice, Collections.<Integer>emptySet(),
				SlayerPlanBuilder.dossier(DATASET), SlayerShopItem.Sightings.NONE, null, 0), SlayerReward::getFallbackName, r -> null, i -> null);
		final SlayerPointsPlanner.Plan plan = SlayerPointsPlanner.plan(all, 50000, SlayerPointsPlanner.Reserve.NONE, null, SlayerGoal.XP);
		final Candidate wyvern = find(all, "Stop the Wyvern");
		// A purchase worth nothing is never listed, so the plan names it only when it helps
		boolean listed = false;
		for (List<SlayerPointsPlanner.Line> lines : Arrays.asList(plan.getBuyNow(), plan.getSaveFor(), plan.getLater(), plan.getAvoid()))
		{
			for (SlayerPointsPlanner.Line l : lines) listed |= l.getName().equals(wyvern.getName());
		}
		assertEquals(wyvern.getGain() > SlayerPointsPlanner.NOTICEABLE, listed);
		return wyvern;
	}

	@Test
	public void stopTheWyvernIsWorthNothingWithSlotsToSpare()
	{
		// Fossil wyverns are the one task here worth a block for XP, so one of seven slots holds them and six are left over. Stop
		// the Wyvern only frees a slot nothing else needs.
		final SlayerPool pool = SlayerPool.of(SlayerAssigner.NIEVE, Arrays.asList(
				new SlayerPool.Entry(DBTableID.SlayerTask.Row.SLAYER_TARGET_FOSSILWYVERNS, "Fossil Island Wyverns", 5, 100, 100, 100, Status.OPEN,
						rating(1, 3, 3)),
				entry(1, 10, rating(5, 3, 3), Status.OPEN),
				entry(2, 10, rating(5, 3, 3), Status.OPEN)));
		assertEquals(Collections.singletonList("Fossil Island Wyverns"),
				SlayerBlockAdvisor.advise(SlayerMaster.NIEVE, pool, SlayerGoal.XP, 7, Collections.<Integer>emptyList()).getAdd());
		assertEquals(0, wyvernBlock(pool, 7).getGain(), SlayerPointsPlanner.NOTICEABLE);
		// With no slot to hold them it is worth their block
		assertTrue(wyvernBlock(pool, 0).getGain() > SlayerPointsPlanner.NOTICEABLE);
	}

	@Test
	public void linesEndWithAFullStopAndUseWholeNumbers()
	{
		for (SlayerAssigner master : SlayerAssigner.values())
		{
			for (SlayerGoal goal : SlayerGoal.values())
			{
				for (Candidate c : at(master, goal))
				{
					for (String text : Arrays.asList(c.getWhy(), c.getNote()))
					{
						if (text == null) continue;
						assertTrue(text, text.endsWith("."));
						assertFalse(text, text.matches(".*[0-9]\\.[0-9].*"));
					}
				}
			}
		}
	}
}
