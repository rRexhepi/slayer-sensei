package com.slayercompanion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;

/** Where the plan's "Path to" button sends Shortest Path: the master the goal says to visit next. */
public class MasterRouteTest
{
	private static final WorldPoint BURTHORPE = new WorldPoint(2931, 3536, 0);
	private static final WorldPoint DRAYNOR = new WorldPoint(3091, 3267, 0);
	private static final WorldPoint MOUNT_KARUULM = new WorldPoint(1309, 3785, 0);
	private static final WorldPoint SHILO_UPSTAIRS = new WorldPoint(2869, 2982, 1);

	private static SlayerPlanBuilder.View plan(SlayerPointsPlannerTest.Account account, SlayerGoal goal, SlayerMasterAccess.State access)
	{
		return SlayerPlanBuilder.build(account, SlayerPointsPlannerTest.DATA, SlayerPointsPlannerTest.DATASET,
				new SlayerPlanBuilder.Settings(SlayerMaster.KONAR, 0, goal, BoostInterval.EVERY_10TH, true), access);
	}

	private static SlayerPointsPlannerTest.Account streak(int streak)
	{
		return new SlayerPointsPlannerTest.Account().withVarbit(VarbitID.SLAYER_POINTS, 521).withVarbit(VarbitID.SLAYER_TASKS_COMPLETED, streak);
	}

	@Test
	public void forSlayerPointsAFillerTaskGoesToAyaOrSpria()
	{
		// Task 208 in progress: the next is 209, a filler task. Spria gives tasks too, so Shortest Path picks the closer one.
		final MasterRoute.Target during208 = MasterRoute.target(plan(streak(207).onTask(SlayerAssigner.TURAEL, 207), SlayerGoal.POINTS,
				SlayerMasterAccessTest.maxed()));
		assertEquals("Path to Aya or Spria", during208.getLabel());
		assertEquals(Arrays.asList(BURTHORPE, DRAYNOR), during208.getPoints());
		assertEquals("Draw the route to Aya in Burthorpe or Spria in Draynor Village with Shortest Path. It picks the closer one.",
				during208.getTooltip());
		// Between tasks after 208: the next is 209 too
		assertEquals(during208, MasterRoute.target(plan(streak(208), SlayerGoal.POINTS, SlayerMasterAccessTest.maxed())));
	}

	@Test
	public void forSlayerPointsAMilestoneGoesToTheMasterWhoPaysMost()
	{
		// After 209 the next is 210, a milestone, and Konar pays most for it
		final MasterRoute.Target milestone = MasterRoute.target(plan(streak(209), SlayerGoal.POINTS, SlayerMasterAccessTest.maxed()));
		assertEquals("Path to Konar", milestone.getLabel());
		assertEquals(Collections.singletonList(MOUNT_KARUULM), milestone.getPoints());
		assertEquals("Draw the route to Konar in Mount Karuulm with Shortest Path", milestone.getTooltip());
		// The same with task 209 in progress
		assertEquals(milestone, MasterRoute.target(plan(streak(208).onTask(SlayerAssigner.TURAEL, 208), SlayerGoal.POINTS,
				SlayerMasterAccessTest.maxed())));
	}

	@Test
	public void withoutSpriaOnlyTuraelsSpot()
	{
		// A new account: Turael, no Spria, and a milestone no master it can use pays for stays with Turael too
		final MasterRoute.Target fresh = MasterRoute.target(plan(streak(9), SlayerGoal.POINTS, SlayerMasterAccessTest.fresh()));
		assertEquals("Path to Turael", fresh.getLabel());
		assertEquals(Collections.singletonList(BURTHORPE), fresh.getPoints());
		assertTrue(fresh.isRoutable());
	}

	@Test
	public void forXpAndProfitTheBestRankedMaster()
	{
		final MasterRoute.Target xp = MasterRoute.target(plan(streak(47), SlayerGoal.XP, SlayerMasterAccessTest.maxed()));
		assertEquals("Path to Kuradal", xp.getLabel());
		assertEquals(Collections.singletonList(SHILO_UPSTAIRS), xp.getPoints());
		// Mortimer is best for profit, and Shortest Path has no way into Wyrmscraig Cavern, so the button says so
		final MasterRoute.Target profit = MasterRoute.target(plan(streak(47), SlayerGoal.PROFIT, SlayerMasterAccessTest.maxed()));
		assertEquals("No path to Mortimer", profit.getLabel());
		assertFalse(profit.isRoutable());
		assertEquals("Shortest Path cannot reach Mortimer in Wyrmscraig Cavern.", profit.getTooltip());
	}

	@Test
	public void noMasterWithoutAPlanOrTheMastersState()
	{
		assertNull(MasterRoute.target(null));
		// Without master state there is no routine and no ranking
		assertNull(MasterRoute.target(plan(streak(47), SlayerGoal.POINTS, null)));
		assertNull(MasterRoute.target(plan(streak(47), SlayerGoal.XP, null)));
	}

	@Test
	public void everyMasterButMortimerHasATileShortestPathReaches()
	{
		for (SlayerAssigner master : SlayerAssigner.values())
		{
			if (master == SlayerAssigner.MORTIMER) assertNull(MasterRoute.spot(master));
			else assertNotNull(master.name(), MasterRoute.spot(master));
		}
		// The wiki's tile for Krystilia is blocked in Shortest Path's map, so the one south of it
		assertEquals(new WorldPoint(3109, 3515, 0), MasterRoute.spot(SlayerAssigner.KRYSTILIA));
		assertEquals(SHILO_UPSTAIRS, MasterRoute.spot(SlayerAssigner.DURADEL));
		assertNull(MasterRoute.spot(null));
	}
}
