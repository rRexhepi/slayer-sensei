package com.slayercompanion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.slayercompanion.SlayerPayout.Diaries;
import org.junit.Test;

/** Points per task against the tables on the OSRS Wiki "Slayer reward point" page and the master pages. */
public class SlayerPayoutTest
{
	private static final Diaries BOTH = new Diaries(true, true);

	/** The wiki's columns: every task, 10th, 50th, 100th, 250th and 1,000th, from task 5 on. */
	private static void assertRow(SlayerAssigner master, Diaries diaries, int... points)
	{
		final int[] tasks = {5, 10, 50, 100, 250, 1000};
		for (int i = 0; i < tasks.length; i++)
		{
			assertEquals(master + " task " + tasks[i], points[i], SlayerPayout.payout(master, tasks[i], diaries));
		}
	}

	@Test
	public void pointsMatchTheWikiTable()
	{
		assertRow(SlayerAssigner.TURAEL, BOTH, 0, 0, 0, 0, 0, 0);
		assertRow(SlayerAssigner.SPRIA, BOTH, 0, 0, 0, 0, 0, 0);
		assertRow(SlayerAssigner.MAZCHNA, Diaries.NONE, 6, 30, 90, 150, 210, 300);
		assertRow(SlayerAssigner.VANNAKA, Diaries.NONE, 8, 40, 120, 200, 280, 400);
		assertRow(SlayerAssigner.CHAELDAR, Diaries.NONE, 10, 50, 150, 250, 350, 500);
		assertRow(SlayerAssigner.NIEVE, Diaries.NONE, 12, 60, 180, 300, 420, 600);
		assertRow(SlayerAssigner.NIEVE, new Diaries(true, false), 15, 75, 225, 375, 525, 750);
		assertRow(SlayerAssigner.DURADEL, BOTH, 15, 75, 225, 375, 525, 750);
		assertRow(SlayerAssigner.KONAR, Diaries.NONE, 18, 90, 270, 450, 630, 900);
		assertRow(SlayerAssigner.KONAR, new Diaries(false, true), 20, 100, 300, 500, 700, 1000);
		assertRow(SlayerAssigner.KRYSTILIA, Diaries.NONE, 25, 125, 375, 625, 875, 1250);
		// Mortimer pays only through his points modifier, never times a milestone
		assertRow(SlayerAssigner.MORTIMER, BOTH, 0, 0, 0, 0, 0, 0);
	}

	@Test
	public void eachDiaryRaisesOnlyItsOwnMaster()
	{
		assertEquals(12, SlayerPayout.basePoints(SlayerAssigner.NIEVE, new Diaries(false, true)));
		assertEquals(18, SlayerPayout.basePoints(SlayerAssigner.KONAR, new Diaries(true, false)));
		assertEquals(15, SlayerPayout.basePoints(SlayerAssigner.DURADEL, Diaries.NONE));
	}

	@Test
	public void theFirstFourTasksOfAStreakPayNothing()
	{
		for (int task = 0; task <= 4; task++)
		{
			assertEquals(0, SlayerPayout.payout(SlayerAssigner.KRYSTILIA, task, Diaries.NONE));
		}
		assertEquals(25, SlayerPayout.payout(SlayerAssigner.KRYSTILIA, 5, Diaries.NONE));
	}

	@Test
	public void aTaskOnSeveralIntervalsTakesTheLargestMultiplier()
	{
		assertEquals(1, SlayerPayout.multiplier(9));
		assertEquals(5, SlayerPayout.multiplier(60));
		assertEquals(15, SlayerPayout.multiplier(150));
		assertEquals(25, SlayerPayout.multiplier(200));
		assertEquals(35, SlayerPayout.multiplier(500));
		assertEquals(35, SlayerPayout.multiplier(750));
		assertEquals(50, SlayerPayout.multiplier(2000));
		assertTrue(SlayerPayout.isMilestone(30));
		assertFalse(SlayerPayout.isMilestone(31));
		assertFalse(SlayerPayout.isMilestone(0));
	}

	@Test
	public void aThousandTaskStreakAveragesWhatTheWikiSays()
	{
		// "Krystilia gives 25 points per task. After completing 1000 of her tasks in a row, a total of
		// 44,375 points are earned", leaving out that the first tasks pay nothing
		int points = 0;
		for (int task = 1; task <= 1000; task++)
		{
			points += SlayerPayout.basePoints(SlayerAssigner.KRYSTILIA, Diaries.NONE) * SlayerPayout.multiplier(task);
		}
		assertEquals(44375, points);
	}

	@Test
	public void theRoutinesMilestoneTasksAverageTheirMultipliers()
	{
		// Every 10th task over 1,000: 80 pay 5 times, 8 pay 15, 8 pay 25, 3 pay 35 and 1 pays 50
		assertEquals((80 * 5 + 8 * 15 + 8 * 25 + 3 * 35 + 50) / 100.0, SlayerPayout.averageMultiplier(10), 1e-12);
		assertEquals(8.75, SlayerPayout.averageMultiplier(10), 1e-12);
		// Every 50th: 8 pay 15, 8 pay 25, 3 pay 35 and 1 pays 50
		assertEquals(23.75, SlayerPayout.averageMultiplier(50), 1e-12);
		// The wiki's 1.775 times the base points over every task of a 1,000 task streak
		assertEquals(1.775, (900 + 100 * SlayerPayout.averageMultiplier(10)) / 1000, 1e-12);
	}
}
