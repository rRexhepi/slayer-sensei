package com.slayercompanion;

import static com.slayercompanion.SlayerPoolTest.entry;
import static com.slayercompanion.SlayerPoolTest.rating;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.slayercompanion.SlayerGoalModel.Offer;
import com.slayercompanion.SlayerPool.Status;
import com.slayercompanion.data.TaskRating;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class SlayerGoalModelTest
{
	/** A good XP task of 40 minutes and a poor one of 12, equally likely. */
	private static final SlayerPool PAIR = SlayerPool.of(SlayerAssigner.DURADEL, Arrays.asList(
			entry(1, 1, rating(5, 1, 3), Status.OPEN),
			entry(2, 1, rating(1, 5, 5), Status.OPEN)));

	@Test
	public void taskTimeScalesWithTheMastersAmount()
	{
		// Speed 3 is about 40 minutes, 6 of them between tasks, at the amount the rating was judged at
		assertEquals(34, SlayerGoalModel.killMinutes(entry(1, 1, rating(1, 1, 3), Status.OPEN)), 1e-9);
		// Half the kills, half the killing
		assertEquals(17, SlayerGoalModel.killMinutes(new SlayerPool.Entry(1, "Half", 1, 40, 60, 100, Status.OPEN, rating(1, 1, 3))), 1e-9);
		// Boss tasks have no amount, the player picks
		assertEquals(94, SlayerGoalModel.killMinutes(new SlayerPool.Entry(1, "Boss", 1, -1, -1, 0, Status.OPEN, rating(1, 1, 1))), 1e-9);
		// A filler task's own estimate replaces the speed rating and the amounts: 9 minutes, 6 of them between tasks
		assertEquals(3, SlayerGoalModel.killMinutes(new SlayerPool.Entry(1, "Filler", 1, 15, 30, 100, Status.OPEN, rating(1, 1, 5), 9)), 1e-9);
	}

	@Test
	public void fillerTasksAreTimedByTheirOwnEstimate()
	{
		// At Turael's spot the cows take the 7 minutes the wiki's tips suggest, cave bugs 12, whatever other masters assign
		final SlayerPool aya = SlayerPool.of(SlayerAssigner.TURAEL, SlayerMasterRankerTest.DATA, SlayerMasterRankerTest.maxed(),
				java.util.Collections.<Integer>emptySet(), SlayerMasterRankerTest.RATINGS);
		for (SlayerPool.Entry e : aya.getEntries())
		{
			if (e.getName().equals("Cows")) assertEquals(1, SlayerGoalModel.killMinutes(e), 1e-9);
			if (e.getName().equals("Cave Bugs")) assertEquals(6, SlayerGoalModel.killMinutes(e), 1e-9);
			assertTrue(e.getName(), e.getMinutes() > 0);
		}
		// Elsewhere the speed rating and the amounts decide
		final SlayerPool konar = SlayerPool.of(SlayerAssigner.KONAR, SlayerMasterRankerTest.DATA, SlayerMasterRankerTest.maxed(),
				java.util.Collections.<Integer>emptySet(), SlayerMasterRankerTest.RATINGS);
		for (SlayerPool.Entry e : konar.getEntries()) assertEquals(e.getName(), 0, e.getMinutes(), 0);
	}

	@Test
	public void theRateWeighsTasksByTimeAndTheGapsEarnNothing()
	{
		// XP: (0.5 * 34 * 5 + 0.5 * 6 * 1) / (0.5 * 40 + 0.5 * 12)
		assertEquals(88 / 26.0, SlayerGoalModel.rate(PAIR, SlayerGoal.XP), 1e-9);
		// Profit runs the other way: the long task is the poor one
		assertEquals((0.5 * 34 + 0.5 * 6 * 5) / 26, SlayerGoalModel.rate(PAIR, SlayerGoal.PROFIT), 1e-9);
		// Slayer points: tasks finished per hour
		assertEquals(60 / 26.0, SlayerGoalModel.rate(PAIR, SlayerGoal.POINTS), 1e-9);
		// Unrated and unavailable tasks are left out
		final SlayerPool mixed = SlayerPool.of(SlayerAssigner.DURADEL, Arrays.asList(PAIR.getEntries().get(0), PAIR.getEntries().get(1),
				entry(3, 5, null, Status.OPEN), entry(4, 5, rating(1, 1, 1), Status.BLOCKED), entry(5, 5, rating(1, 1, 1), Status.NEEDS_LEVEL)));
		assertEquals(SlayerGoalModel.rate(PAIR, SlayerGoal.XP), SlayerGoalModel.rate(mixed, SlayerGoal.XP), 1e-9);
	}

	@Test
	public void blocksGoToCommonLongPoorTasksFirst()
	{
		final SlayerPool pool = SlayerPool.of(SlayerAssigner.DURADEL, Arrays.asList(
				entry(1, 6, rating(1, 1, 1), Status.OPEN),
				entry(2, 6, rating(1, 1, 5), Status.OPEN),
				entry(3, 6, rating(5, 1, 3), Status.BLOCKED)));
		// The current block on the good task is lifted first, then the long poor task goes before the short one
		assertEquals(Arrays.asList(1), rows(SlayerGoalModel.bestBlocks(pool, SlayerGoal.XP, 1)));
		// Picking stops once a block no longer helps: the good task is never blocked
		assertEquals(Arrays.asList(1, 2), rows(SlayerGoalModel.bestBlocks(pool, SlayerGoal.XP, 3)));
		// For Slayer points the slowest task goes first
		assertEquals(Arrays.asList(1), rows(SlayerGoalModel.bestBlocks(pool, SlayerGoal.POINTS, 1)));
		assertTrue(SlayerGoalModel.bestBlocks(pool, SlayerGoal.XP, 0).isEmpty());
	}

	@Test
	public void skipsDropTheWorstOffersWithinTheBudget()
	{
		final SlayerPool pool = SlayerPool.of(SlayerAssigner.DURADEL, Arrays.asList(
				entry(1, 1, rating(1, 1, 1), Status.OPEN),
				entry(2, 1, rating(5, 1, 3), Status.OPEN)));
		final List<Offer> offers = SlayerGoalModel.offers(pool);
		final double plain = (0.5 * 94 + 0.5 * 34 * 5) / (0.5 * 100 + 0.5 * 40);
		assertEquals(plain, SlayerGoalModel.rateWithSkips(offers, SlayerGoal.XP, 0, 0), 1e-9);
		// Skipping half of all offers drops the poor task, at 3 minutes a skip
		assertEquals(0.5 * 34 * 5 / (0.5 * 40 + 0.5 * 3), SlayerGoalModel.rateWithSkips(offers, SlayerGoal.XP, 0, 0.5), 1e-9);
		// A smaller budget skips part of it
		assertEquals((0.25 * 94 + 0.5 * 34 * 5) / (0.25 * 100 + 0.5 * 40 + 0.25 * 3),
				SlayerGoalModel.rateWithSkips(offers, SlayerGoal.XP, 0, 0.25), 1e-9);
		// A budget beyond the poor task never skips the good one
		assertEquals(0.5 * 34 * 5 / (0.5 * 40 + 0.5 * 3), SlayerGoalModel.rateWithSkips(offers, SlayerGoal.XP, 0, 0.9), 1e-9);
		// The bonus is added to every rating
		assertEquals((0.5 * 94 * 1.5 + 0.5 * 34 * 5.5) / 70, SlayerGoalModel.rate(offers, SlayerGoal.XP, 0.5, 0), 1e-9);
	}

	@Test
	public void choosingFromSeveralTasksTakesTheBest()
	{
		final SlayerPool pool = SlayerPool.of(SlayerAssigner.MORTIMER, Arrays.asList(
				entry(1, 2, rating(1, 1, 3), Status.OPEN),
				entry(2, 1, rating(3, 1, 3), Status.OPEN),
				entry(3, 1, rating(5, 1, 3), Status.OPEN)));
		final List<Offer> offers = SlayerGoalModel.offers(pool);
		// Two different tasks per offer: the 5 comes up in 7 offers of 12, otherwise the 3 is taken
		final List<Offer> picked = SlayerGoalModel.bestOf(offers, SlayerGoal.XP, 2);
		assertEquals(2, picked.size());
		assertEquals(5, picked.get(1).getEntry().rating(SlayerGoal.XP));
		assertEquals(7 / 12.0, picked.get(1).getChance(), 1e-9);
		assertEquals(5 / 12.0, picked.get(0).getChance(), 1e-9);
		// Equal task lengths, so the rate is the picked ratings' mean times the killing share
		assertEquals((7 / 12.0 * 5 + 5 / 12.0 * 3) * 34 / 40, SlayerGoalModel.rateBestOf(offers, SlayerGoal.XP, 0, 2), 1e-9);
		// One choice is the plain rate, more choices than tasks take the best task every time
		assertEquals(SlayerGoalModel.rate(offers, SlayerGoal.XP, 0, 0), SlayerGoalModel.rateBestOf(offers, SlayerGoal.XP, 0, 1), 1e-9);
		assertEquals(5 * 34 / 40.0, SlayerGoalModel.rateBestOf(offers, SlayerGoal.XP, 0, 5), 1e-9);
		assertEquals(7 / 12.0, SlayerGoalModel.share(picked, SlayerGoal.XP, 4), 1e-9);
	}

	@Test
	public void whatIfRatesScaleTimeYieldAndTravel()
	{
		final List<Offer> offers = SlayerGoalModel.offers(PAIR);
		// No change gives the plain rate
		for (SlayerGoal goal : SlayerGoal.values())
		{
			assertEquals(SlayerGoalModel.rate(offers, goal, 0, 0),
					SlayerGoalModel.rate(offers, goal, e -> 1, e -> 1, e -> SlayerGoalModel.OVERHEAD_MINUTES), 1e-12);
		}
		// Halving the good task's killing time: XP (0.5 * 34 * 5 + 0.5 * 6 * 1) / (0.5 * 17 + 0.5 * 6 + 6)
		assertEquals(88 / 17.5, SlayerGoalModel.rate(offers, SlayerGoal.XP, e -> e.getTaskRow() == 1 ? 0.5 : 1, e -> 1,
				e -> SlayerGoalModel.OVERHEAD_MINUTES), 1e-9);
		// The same time saved raises every goal's rate by the same share
		final double xp = gainOfHalf(offers, SlayerGoal.XP);
		assertTrue(xp > 0);
		assertEquals(xp, gainOfHalf(offers, SlayerGoal.PROFIT), 1e-9);
		assertEquals(xp, gainOfHalf(offers, SlayerGoal.POINTS), 1e-9);
		// More yield raises XP and profit but not tasks an hour
		assertEquals(SlayerGoalModel.rate(offers, SlayerGoal.POINTS, 0, 0),
				SlayerGoalModel.rate(offers, SlayerGoal.POINTS, e -> 1, e -> 2, e -> SlayerGoalModel.OVERHEAD_MINUTES), 1e-12);
		assertEquals(2 * SlayerGoalModel.rate(offers, SlayerGoal.XP, 0, 0),
				SlayerGoalModel.rate(offers, SlayerGoal.XP, e -> 1, e -> 2, e -> SlayerGoalModel.OVERHEAD_MINUTES), 1e-9);
	}

	private static double gainOfHalf(List<Offer> offers, SlayerGoal goal)
	{
		final double base = SlayerGoalModel.rate(offers, goal, 0, 0);
		return SlayerGoalModel.rate(offers, goal, e -> e.getTaskRow() == 1 ? 0.5 : 1, e -> 1, e -> SlayerGoalModel.OVERHEAD_MINUTES) / base - 1;
	}

	@Test
	public void theSkipModelSaysWhichTasksAndHowOften()
	{
		final SlayerPool pool = SlayerPool.of(SlayerAssigner.DURADEL, Arrays.asList(
				entry(1, 1, rating(1, 1, 1), Status.OPEN),
				entry(2, 1, rating(5, 1, 3), Status.OPEN)));
		final SlayerGoalModel.Skips skips = SlayerGoalModel.skips(SlayerGoalModel.offers(pool), SlayerGoal.XP, 0, 0.9);
		// Only the poor task, and only as often as it comes up
		assertEquals(0.5, skips.getShare(), 1e-9);
		assertEquals(Arrays.asList(1), rows(skips.getTasks()));
		assertEquals(SlayerGoalModel.rateWithSkips(SlayerGoalModel.offers(pool), SlayerGoal.XP, 0, 0.9), skips.getRate(), 0);
		// No budget, no skips
		assertTrue(SlayerGoalModel.skips(SlayerGoalModel.offers(pool), SlayerGoal.XP, 0, 0).getTasks().isEmpty());
	}

	@Test
	public void theRoutineIsValuedByItsWholeCycle()
	{
		// Every 10th task: nine filler tasks of 10 minutes and a milestone task of 30, which pays 18 points times 8.75
		assertEquals(60 * 157.5 / 120, SlayerGoalModel.cycleRate(10, 30, 9, 157.5), 1e-9);
		// Every 50th: 49 filler tasks for one milestone
		assertEquals(60 * 18 * 23.75 / 520, SlayerGoalModel.cycleRate(10, 30, 49, 18 * 23.75), 1e-9);

		// Over the two lists: a filler task of 22 minutes and three of 12, a milestone task of 100 minutes and one of 40
		final SlayerPool fillers = SlayerPool.of(SlayerAssigner.TURAEL, Arrays.asList(
				entry(1, 1, rating(1, 1, 4), Status.OPEN),
				entry(2, 3, rating(1, 1, 5), Status.OPEN)));
		final SlayerPool milestones = SlayerPool.of(SlayerAssigner.KONAR, Arrays.asList(
				entry(3, 1, rating(1, 1, 1), Status.OPEN),
				entry(4, 1, rating(1, 1, 3), Status.OPEN)));
		assertEquals(14.5, SlayerGoalModel.minutesPerTask(SlayerGoalModel.offers(fillers)), 1e-9);
		assertEquals(70, SlayerGoalModel.minutesPerTask(SlayerGoalModel.offers(milestones)), 1e-9);
		final double base = SlayerGoalModel.cycleRate(fillers, milestones, 9, 157.5);
		assertEquals(60 * 157.5 / (9 * 14.5 + 70), base, 1e-9);
		// The points only set the size of the rate
		assertEquals(2 * base, SlayerGoalModel.cycleRate(fillers, milestones, 9, 315), 1e-9);

		// Blocks at both masters on one scale. The filler block saves 2.5 minutes nine times a cycle, the milestone block 30 once.
		final double fillerBlock = SlayerGoalModel.cycleRate(fillers.withBlocks(Collections.singletonList(1)), milestones, 9, 157.5) / base - 1;
		final double milestoneBlock = SlayerGoalModel.cycleRate(fillers, milestones.withBlocks(Collections.singletonList(3)), 9, 157.5) / base - 1;
		assertEquals(200.5 / 178 - 1, fillerBlock, 1e-9);
		assertEquals(200.5 / 170.5 - 1, milestoneBlock, 1e-9);
		// The milestone block helps more, but per point, at 40 against 80, the filler block comes first
		assertTrue(milestoneBlock > fillerBlock);
		assertTrue(fillerBlock / 40 > milestoneBlock / 80);
		// Every 50th task puts even more of the time at the filler master
		final double base50 = SlayerGoalModel.cycleRate(fillers, milestones, 49, 427.5);
		final SlayerPool fillerBlocked = fillers.withBlocks(Collections.singletonList(1));
		final SlayerPool milestoneBlocked = milestones.withBlocks(Collections.singletonList(3));
		assertEquals(780.5 / 658 - 1, SlayerGoalModel.cycleRate(fillerBlocked, milestones, 49, 427.5) / base50 - 1, 1e-9);
		assertEquals(780.5 / 750.5 - 1, SlayerGoalModel.cycleRate(fillers, milestoneBlocked, 49, 427.5) / base50 - 1, 1e-9);
		// A reward that changes how tasks go changes the minutes the same way: halving the 22 minute task's killing
		assertEquals(14.5 - 0.25 * 16 * 0.5, SlayerGoalModel.minutesPerTask(SlayerGoalModel.offers(fillers), e -> e.getTaskRow() == 1 ? 0.5 : 1,
				e -> SlayerGoalModel.OVERHEAD_MINUTES), 1e-9);
	}

	@Test
	public void aTaskTheWikiSaysToDoIsNeverSkippedOrBlockedForXp()
	{
		// Two fair tasks beside an excellent one: the goal model would block and skip either, but not the one the wiki says to do
		final SlayerPool pool = SlayerPool.of(SlayerAssigner.DURADEL, Arrays.asList(
				new SlayerPool.Entry(1, "Excellent", 10, 100, 100, 100, Status.OPEN, rating(5, 5, 3), 0, null),
				new SlayerPool.Entry(2, "Wiki do", 10, 100, 100, 100, Status.OPEN, rating(3, 3, 3), 0, TaskRating.XpAdvice.DO),
				new SlayerPool.Entry(3, "Unflagged", 10, 100, 100, 100, Status.OPEN, rating(3, 3, 3), 0, null)));
		assertEquals(Arrays.asList(3), rows(SlayerGoalModel.bestBlocks(pool, SlayerGoal.XP, 2)));
		assertEquals(Arrays.asList(3), rows(SlayerGoalModel.skips(SlayerGoalModel.offers(pool), SlayerGoal.XP, 0, 0.9).getTasks()));
		// The wiki's advice is for XP only
		assertEquals(Arrays.asList(3, 2), rows(SlayerGoalModel.bestBlocks(pool, SlayerGoal.PROFIT, 2)));
		assertEquals(Arrays.asList(2, 3), rows(SlayerGoalModel.skips(SlayerGoalModel.offers(pool), SlayerGoal.PROFIT, 0, 0.9).getTasks()));
		// A task it says to skip counts as poor at best for XP, one to do as fair at least
		final SlayerPool.Entry skip = new SlayerPool.Entry(4, "Barraged", 1, 100, 100, 100, Status.OPEN, rating(5, 3, 3), 0,
				TaskRating.XpAdvice.SKIP);
		assertEquals(2, skip.rating(SlayerGoal.XP));
		assertEquals(3, skip.rating(SlayerGoal.PROFIT));
		assertEquals(3, new SlayerPool.Entry(5, "Quick", 1, 100, 100, 100, Status.OPEN, rating(2, 4, 5), 0, TaskRating.XpAdvice.DO)
				.rating(SlayerGoal.XP));
	}

	@Test
	public void bossTasksAreNeverBlocked()
	{
		// "The only way to block a boss is by toggling the ability off" (wiki Boss Slayer)
		final SlayerPool pool = SlayerPool.of(SlayerAssigner.DURADEL, Arrays.asList(
				new SlayerPool.Entry(1, "Boss", 12, -1, -1, 0, Status.OPEN, rating(1, 1, 1)),
				entry(2, 1, rating(5, 1, 3), Status.OPEN)));
		assertTrue(pool.find(1).isBoss());
		assertTrue(SlayerGoalModel.bestBlocks(pool, SlayerGoal.XP, 7).isEmpty());
	}

	private static List<Integer> rows(List<SlayerPool.Entry> entries)
	{
		final List<Integer> out = new ArrayList<>();
		for (SlayerPool.Entry e : entries) out.add(e.getTaskRow());
		return out;
	}
}
