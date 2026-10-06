package com.slayercompanion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.slayercompanion.SlayerBlockAdvisor.Advice;
import com.slayercompanion.SlayerBlockAdvisor.Misfit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import net.runelite.api.Skill;
import net.runelite.api.gameval.DBTableID.SlayerTask.Row;
import org.junit.Test;

/** Block advice at one master, against the live cache dump and the dossier's ratings. */
public class SlayerBlockAdvisorTest
{
	private static Advice advise(SlayerMaster master, SlayerPool.Player player, SlayerGoal goal, int slots, Integer... blocked)
	{
		final List<Integer> rows = Arrays.asList(blocked);
		final SlayerPool pool = SlayerPool.of(master.assigner(), SlayerMasterRankerTest.DATA, player, new HashSet<>(rows),
				SlayerMasterRankerTest.RATINGS);
		return SlayerBlockAdvisor.advise(master, pool, goal, slots, rows);
	}

	@Test
	public void konarForXpKeepsTheRightBlocksAndFlagsTheRest()
	{
		final Advice advice = advise(SlayerMaster.KONAR, SlayerMasterRankerTest.maxed(), SlayerGoal.XP, 7, Row.SLAYER_TARGET_DAGANNOTH,
				Row.SLAYER_TARGET_CAVEKRAKEN);
		assertEquals(80, advice.getCost());
		assertEquals(7, advice.getSlots());
		assertEquals(Collections.singletonList("Cave Kraken"), advice.getKeep());
		assertEquals(Arrays.asList("Waterfiends", "Metal Dragons", "Fossil Island Wyverns", "Hydras", "Drakes", "Wyrms"), advice.getAdd());
		// Dagannoth is an 85k XP an hour cannon task the wiki's Slayer guide says to do
		assertEquals(Collections.singletonList(new Misfit("Dagannoth", "the wiki's Slayer guide says to do it for XP")), advice.getMisfits());
		// Fire giants are fair and the wiki's advice on them is conditional, so a block that lowers the rate does not call them good
		assertEquals(Collections.singletonList(new Misfit("Fire Giants", "a block on it lowers your XP rate")),
				advise(SlayerMaster.KONAR, SlayerMasterRankerTest.maxed(), SlayerGoal.XP, 7, Row.SLAYER_TARGET_FIREGIANTS).getMisfits());
		// Drakes, which the wiki says to skip or block for XP, stay blocked
		assertEquals(Collections.singletonList("Drakes"),
				advise(SlayerMaster.KONAR, SlayerMasterRankerTest.maxed(), SlayerGoal.XP, 7, Row.SLAYER_TARGET_DRAKES).getKeep());
	}

	@Test
	public void theGoalDecidesTheBlocks()
	{
		final SlayerPool.Player maxed = SlayerMasterRankerTest.maxed();
		// Profit blocks the money losers, Slayer points the slow tasks
		final List<String> profit = advise(SlayerMaster.KONAR, maxed, SlayerGoal.PROFIT, 7).getAdd();
		assertTrue(profit.toString(), profit.containsAll(Arrays.asList("Kalphites", "Dagannoth")));
		assertFalse(profit.toString(), profit.contains("Hydras"));
		final List<String> points = advise(SlayerMaster.KONAR, maxed, SlayerGoal.POINTS, 7).getAdd();
		assertTrue(points.toString(), points.containsAll(Arrays.asList("Hydras", "Drakes")));
		// Tasks the wiki's task summary says to block for XP at Duradel
		final List<String> xp = advise(SlayerMaster.DURADEL, maxed, SlayerGoal.XP, 7).getAdd();
		assertTrue(xp.toString(), xp.containsAll(Arrays.asList("Cave Kraken", "Fossil Island Wyverns", "Metal Dragons")));
		assertFalse(xp.toString(), xp.contains("Smoke Devils"));
	}

	@Test
	public void onlyTheOpenSlotsAreFilled()
	{
		final Advice two = advise(SlayerMaster.KONAR, SlayerMasterRankerTest.maxed(), SlayerGoal.XP, 2);
		assertEquals(Arrays.asList("Cave Kraken", "Waterfiends"), two.getAdd());
		final Advice none = advise(SlayerMaster.KONAR, SlayerMasterRankerTest.maxed(), SlayerGoal.XP, 0);
		assertTrue(none.getAdd().isEmpty());
		// Mortimer charges 120 a block (wiki Slayer Master)
		assertEquals(120, advise(SlayerMaster.MORTIMER, SlayerMasterRankerTest.maxed(), SlayerGoal.XP, 2).getCost());
	}

	@Test
	public void helpfulBlocksStayAndFreeSlotsAreFilled()
	{
		// At Kuradal kurask is a poor XP task but not among the seven best blocks. Blocks cost points and
		// unblocking refunds nothing, so it stays and the other six slots are filled around it.
		final Advice advice = advise(SlayerMaster.DURADEL, SlayerMasterRankerTest.maxed(), SlayerGoal.XP, 7, Row.SLAYER_TARGET_KURASK);
		assertEquals(Collections.singletonList("Kurask"), advice.getKeep());
		assertEquals(6, advice.getAdd().size());
		assertFalse(advice.getAdd().contains("Kurask"));
		assertTrue(advice.getMisfits().isEmpty());
	}

	@Test
	public void aBlockIsJudgedOnTopOfTheOthers()
	{
		// Task 2 is poor but helps only once the very poor, very long task 1 is blocked too. Judged alone it would be
		// lifted and then put straight back. Judged on top of the other block it stays, and nothing is asked twice.
		final SlayerPool pool = SlayerPool.of(SlayerAssigner.KONAR, Arrays.asList(
				SlayerPoolTest.entry(1, 10, SlayerPoolTest.rating(1, 1, 1), SlayerPool.Status.BLOCKED),
				SlayerPoolTest.entry(2, 5, SlayerPoolTest.rating(2, 1, 3), SlayerPool.Status.BLOCKED),
				SlayerPoolTest.entry(3, 10, SlayerPoolTest.rating(3, 1, 4), SlayerPool.Status.OPEN),
				SlayerPoolTest.entry(4, 10, SlayerPoolTest.rating(5, 1, 4), SlayerPool.Status.OPEN)));
		assertTrue(SlayerGoalModel.rate(pool.withBlocks(Collections.singletonList(2)), SlayerGoal.XP)
				< SlayerGoalModel.rate(pool.withBlocks(Collections.<Integer>emptyList()), SlayerGoal.XP));
		final Advice advice = SlayerBlockAdvisor.advise(SlayerMaster.KONAR, pool, SlayerGoal.XP, 2, Arrays.asList(1, 2));
		assertEquals(Arrays.asList("Task 1", "Task 2"), advice.getKeep());
		assertTrue(advice.getAdd().isEmpty());
		assertTrue(advice.getMisfits().isEmpty());
	}

	@Test
	public void aFullListSwapsOnlyForAMuchBetterBlock()
	{
		// Seven mild blocks fill every slot while a common, long, poor task waits
		final List<SlayerPool.Entry> entries = new ArrayList<>();
		entries.add(SlayerPoolTest.entry(1, 8, SlayerPoolTest.rating(1, 1, 1), SlayerPool.Status.OPEN));
		final List<Integer> blocked = new ArrayList<>();
		for (int row = 2; row <= 8; row++)
		{
			entries.add(SlayerPoolTest.entry(row, 1, SlayerPoolTest.rating(2, 1, 4), SlayerPool.Status.BLOCKED));
			blocked.add(row);
		}
		entries.add(SlayerPoolTest.entry(9, 20, SlayerPoolTest.rating(4, 1, 3), SlayerPool.Status.OPEN));
		Advice advice = SlayerBlockAdvisor.advise(SlayerMaster.KONAR, SlayerPool.of(SlayerAssigner.KONAR, entries), SlayerGoal.XP, 7, blocked);
		assertEquals(Collections.singletonList("Task 1"), advice.getAdd());
		assertEquals(6, advice.getKeep().size());
		assertEquals(Collections.singletonList(new Misfit("Task 2", "a weaker block than Task 1")), advice.getMisfits());
		// A waiting task no worse than the blocks already there is not worth the points
		entries.set(0, SlayerPoolTest.entry(1, 1, SlayerPoolTest.rating(2, 1, 4), SlayerPool.Status.OPEN));
		advice = SlayerBlockAdvisor.advise(SlayerMaster.KONAR, SlayerPool.of(SlayerAssigner.KONAR, entries), SlayerGoal.XP, 7, blocked);
		assertTrue(advice.getAdd().isEmpty());
		assertEquals(7, advice.getKeep().size());
		assertEquals("Every open slot holds a useful block.\nAlready blocked and right: Task 2, Task 3, Task 4, Task 5, Task 6, Task 7, Task 8.",
				SlayerPlanText.blockAdvice(advice, "Konar", null));
	}

	@Test
	public void aBlockOnATaskYouCannotGetIsWasted()
	{
		final int[] levels = SlayerMasterRankerTest.levels(99);
		levels[Skill.SLAYER.ordinal()] = 90;
		final SlayerPool.Player slayer90 = new SlayerPool.Player(levels, 126, false, Collections.<Integer>emptySet(),
				Collections.<Integer>emptySet());
		final Advice advice = advise(SlayerMaster.KONAR, slayer90, SlayerGoal.XP, 7, Row.SLAYER_TARGET_HYDRAS);
		assertEquals(Collections.singletonList(new Misfit("Hydras", "you cannot be given it anyway")), advice.getMisfits());
		assertFalse(advice.getAdd().contains("Hydras"));
	}
}
