package com.slayercompanion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.slayercompanion.SlayerMasterAccess.Option;
import com.slayercompanion.SlayerMasterAccess.State;
import com.slayercompanion.SlayerPayout.Diaries;
import com.slayercompanion.SlayerPointsRoutine.Advice;
import com.slayercompanion.SlayerPointsRoutine.Ranked;
import com.slayercompanion.SlayerPointsRoutine.Streaks;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class SlayerPointsRoutineTest
{
	private static final List<Option> MAXED = SlayerMasterAccess.options(SlayerMasterAccessTest.maxed());

	private static Streaks idle(int streak)
	{
		return new Streaks(streak, 0, 0, false);
	}

	private static Streaks onTask(int streak, SlayerAssigner master)
	{
		return new Streaks(streak, 7, master.getId(), true);
	}

	private static Advice advise(Streaks streaks, BoostInterval boost)
	{
		return SlayerPointsRoutine.advise(streaks, MAXED, Diaries.NONE, boost);
	}

	private static List<String> names(List<Ranked> ranking)
	{
		final List<String> out = new ArrayList<>();
		for (Ranked r : ranking) out.add(r.getNpc() + " " + r.getPoints());
		return out;
	}

	@Test
	public void betweenMilestonesTheNextTaskGoesToAya()
	{
		final Advice a = advise(idle(47), BoostInterval.EVERY_10TH);
		assertNull(a.getCurrent());
		assertEquals("Get task 48 from Aya in Burthorpe or Spria in Draynor Village. It pays no points but still counts toward your streak.",
				a.getNext());
		assertNull(a.getCaveat());
		assertEquals("Task 50, 3 tasks from now. Konar pays 270 for it.", a.getMilestone());
		assertEquals("Changing a task with Aya resets your streak to 0.", a.getWarning());
		assertEquals(48, a.getNextTask());
		assertTrue(a.getRanking().isEmpty());
	}

	@Test
	public void aMilestoneListsEveryPayingMasterHighestFirst()
	{
		final Advice a = advise(idle(49), BoostInterval.EVERY_10TH);
		assertEquals("Task 50 is a milestone. Get it from the master who pays most: "
				+ "Konar 270, Kuradal 225, Steve 180, Chaeldar 150, Vannaka 120, Mazchna 90.", a.getNext());
		// Konar is the wiki's pick for 50th tasks
		assertNull(a.getCaveat());
		assertEquals("After task 50, the next is task 60. Konar pays 90 for it.", a.getMilestone());
		// Krystilia and Mortimer are on other streaks, Aya and Spria pay nothing
		assertEquals(Arrays.asList("Konar 270", "Kuradal 225", "Steve 180", "Chaeldar 150", "Vannaka 120", "Mazchna 90"),
				names(a.getRanking()));
	}

	@Test
	public void konarOnATenthTaskGetsTheWikisCaveat()
	{
		final Advice a = advise(idle(59), BoostInterval.EVERY_10TH);
		assertEquals("Task 60 is a milestone. Get it from the master who pays most: "
				+ "Konar 90, Kuradal 75, Steve 60, Chaeldar 50, Vannaka 40, Mazchna 30.", a.getNext());
		assertEquals("Konar gives many unfavourable tasks, so the wiki suggests another master for 10th tasks. "
				+ "Kuradal at 75 is the next best.", a.getCaveat());
	}

	@Test
	public void everyFiftiethLeavesTenthTasksAtAya()
	{
		Advice a = advise(idle(59), BoostInterval.EVERY_50TH);
		assertEquals("Get task 60 from Aya in Burthorpe or Spria in Draynor Village. It pays no points but still counts toward your streak.",
				a.getNext());
		assertEquals("Task 100, 41 tasks from now. Konar pays 450 for it.", a.getMilestone());
		a = advise(idle(99), BoostInterval.EVERY_50TH);
		assertEquals("After task 100, the next is task 150. Konar pays 270 for it.", a.getMilestone());
	}

	@Test
	public void theTaskInProgressComesFirst()
	{
		// Task 48 from Aya is in progress, so the next one to get is 49
		Advice a = advise(onTask(47, SlayerAssigner.TURAEL), BoostInterval.EVERY_10TH);
		assertEquals("Aya pays no points for task 48 of your streak. It still counts toward the streak.", a.getCurrent());
		assertEquals(49, a.getNextTask());
		assertEquals("Task 50, 2 tasks after this one. Konar pays 270 for it.", a.getMilestone());

		a = advise(onTask(49, SlayerAssigner.KONAR), BoostInterval.EVERY_10TH);
		assertEquals("Konar pays 270 for task 50 of your streak.", a.getCurrent());
		assertEquals(51, a.getNextTask());
		assertEquals("Task 60, 10 tasks after this one. Konar pays 90 for it.", a.getMilestone());

		a = advise(onTask(49, SlayerAssigner.DURADEL), BoostInterval.EVERY_10TH);
		assertEquals("Kuradal pays 225 for task 50 of your streak.", a.getCurrent());
	}

	@Test
	public void krystiliaAndMortimerTasksLeaveTheStreakAlone()
	{
		Advice a = advise(onTask(47, SlayerAssigner.KRYSTILIA), BoostInterval.EVERY_10TH);
		// Her Wilderness streak is at 7, so this is her task 8
		assertEquals("Krystilia pays 25 for task 8 of her Wilderness streak. That streak is counted apart from this one.",
				a.getCurrent());
		assertEquals(48, a.getNextTask());

		a = advise(new Streaks(47, 9, SlayerAssigner.KRYSTILIA.getId(), true), BoostInterval.EVERY_10TH);
		assertTrue(a.getCurrent().startsWith("Krystilia pays 125 for task 10 "));

		a = advise(onTask(47, SlayerAssigner.MORTIMER), BoostInterval.EVERY_10TH);
		assertEquals("Mortimer pays points only through his points modifier. His tasks do not count toward your streak.",
				a.getCurrent());
		assertEquals(48, a.getNextTask());
	}

	@Test
	public void anUnknownMasterStillCountsTheTask()
	{
		final Advice a = advise(new Streaks(47, 0, 0, true), BoostInterval.EVERY_10TH);
		assertEquals("This is task 48 of your streak. What it pays depends on the master who gave it.", a.getCurrent());
		assertEquals(49, a.getNextTask());
	}

	@Test
	public void aNewStreakExplainsTheUnpaidStart()
	{
		final Advice a = SlayerPointsRoutine.advise(idle(0), SlayerMasterAccess.options(SlayerMasterAccessTest.fresh()),
				Diaries.NONE, BoostInterval.EVERY_10TH);
		assertEquals("Get task 1 from Turael in Burthorpe. The first 4 tasks of a streak pay no points anywhere.", a.getNext());
		assertEquals("No master you can use pays points yet. Mazchna needs combat level 20.", a.getMilestone());
		assertEquals("Changing a task with Turael resets your streak to 0.", a.getWarning());

		final Advice konarStart = advise(onTask(2, SlayerAssigner.KONAR), BoostInterval.EVERY_10TH);
		assertEquals("Task 3 of your streak pays nothing. The first 4 tasks of a streak never pay.", konarStart.getCurrent());
	}

	@Test
	public void aMilestoneWithNoPayingMasterStaysWithTurael()
	{
		final Advice a = SlayerPointsRoutine.advise(idle(9), SlayerMasterAccess.options(SlayerMasterAccessTest.fresh()),
				Diaries.NONE, BoostInterval.EVERY_10TH);
		assertEquals("Task 10 is a milestone, but no master you can use pays points yet. Get it from Turael in Burthorpe.",
				a.getNext());
	}

	@Test
	public void diariesAndTiesRankAsTheGamePays()
	{
		// With the elite Western Provinces diary Steve matches Kuradal. The lower master comes first on a tie.
		final List<Ranked> ranking = SlayerPointsRoutine.rank(10, MAXED, new Diaries(true, true));
		assertEquals(Arrays.asList("Konar 100", "Steve 75", "Kuradal 75", "Chaeldar 50", "Vannaka 40", "Mazchna 30"),
				names(ranking));
	}

	@Test
	public void onlyUsableMastersAreRanked()
	{
		// Combat 80 without Shilo Village: Konar, Chaeldar, Vannaka and Mazchna
		final State s = new State(80, 60, true, true, false, 0, false, 0, 0, false, Diaries.NONE);
		final List<Ranked> ranking = SlayerPointsRoutine.rank(50, SlayerMasterAccess.options(s), Diaries.NONE);
		assertEquals(Arrays.asList("Konar 270", "Chaeldar 150", "Vannaka 120", "Mazchna 90"), names(ranking));
	}

	// ---- the compact sidebar ----

	@Test
	public void theCompactRoutineNamesTheNextTaskAndTheMilestone()
	{
		Advice a = advise(idle(47), BoostInterval.EVERY_10TH);
		assertEquals("NEXT TASK", a.getHeading());
		assertEquals(Arrays.asList("#48 Aya or Spria, 0 pts", "#50 Konar, 270 pts"), a.getItems());
		// With task 48 in progress the next one is 49
		a = advise(onTask(47, SlayerAssigner.TURAEL), BoostInterval.EVERY_10TH);
		assertEquals(Arrays.asList("#49 Aya or Spria, 0 pts", "#50 Konar, 270 pts"), a.getItems());
		// Every 50th: the 10th tasks stay with Aya
		a = advise(idle(59), BoostInterval.EVERY_50TH);
		assertEquals(Arrays.asList("#60 Aya or Spria, 0 pts", "#100 Konar, 450 pts"), a.getItems());
	}

	@Test
	public void beforeAMilestoneTheCompactRoutineListsTheBestPayers()
	{
		Advice a = advise(idle(49), BoostInterval.EVERY_10TH);
		assertEquals("NEXT TASK #50", a.getHeading());
		assertEquals(Arrays.asList("Konar 270", "Kuradal 225", "Steve 180"), a.getItems());
		// Konar on a 10th task carries the wiki's warning
		a = advise(idle(59), BoostInterval.EVERY_10TH);
		assertEquals(Arrays.asList("Konar 90", "Kuradal 75", "Steve 60", "Wiki: avoid Konar for 10ths"), a.getItems());
	}

	@Test
	public void aNewAccountsCompactRoutineSaysNoMasterPaysYet()
	{
		Advice a = SlayerPointsRoutine.advise(idle(0), SlayerMasterAccess.options(SlayerMasterAccessTest.fresh()), Diaries.NONE,
				BoostInterval.EVERY_10TH);
		assertEquals("NEXT TASK", a.getHeading());
		assertEquals(Arrays.asList("#1 Turael, 0 pts", "No master pays yet"), a.getItems());
		// A milestone nobody pays for yet stays with Turael
		a = SlayerPointsRoutine.advise(idle(9), SlayerMasterAccess.options(SlayerMasterAccessTest.fresh()), Diaries.NONE,
				BoostInterval.EVERY_10TH);
		assertEquals("NEXT TASK", a.getHeading());
		assertEquals(Arrays.asList("#10 Turael, 0 pts", "No master pays yet"), a.getItems());
	}

	// ---- forecast ----

	@Test
	public void forecastWalksTheUpcomingTaskNumbers()
	{
		// Streak 47: tasks 48 and 49 at Aya pay nothing, task 50 at Konar pays 270
		assertEquals(3, SlayerPointsForecast.withRoutine(119, idle(47), MAXED, Diaries.NONE, BoostInterval.EVERY_10TH));
		assertEquals(13, SlayerPointsForecast.withRoutine(271, idle(47), MAXED, Diaries.NONE, BoostInterval.EVERY_10TH));
		// Every task at Konar: 18, 18, then 270 on the 50th
		assertEquals(3, SlayerPointsForecast.atMaster(119, SlayerAssigner.KONAR, idle(47), Diaries.NONE));
		assertEquals(6, SlayerPointsForecast.atMaster(100, SlayerAssigner.KONAR, idle(51), Diaries.NONE));
		// The first four tasks of a streak pay nothing: 0, 0, 0, 0, then 18 each
		assertEquals(5, SlayerPointsForecast.atMaster(18, SlayerAssigner.KONAR, idle(0), Diaries.NONE));
		assertEquals(0, SlayerPointsForecast.atMaster(0, SlayerAssigner.KONAR, idle(0), Diaries.NONE));
	}

	@Test
	public void forecastCountsTheTaskInProgress()
	{
		// Konar's task 50 is in progress and pays 270 on its own
		assertEquals(1, SlayerPointsForecast.withRoutine(119, onTask(49, SlayerAssigner.KONAR), MAXED, Diaries.NONE,
				BoostInterval.EVERY_10TH));
		// A milestone done at Aya pays nothing, so the next milestone is task 60
		assertEquals(11, SlayerPointsForecast.withRoutine(50, onTask(49, SlayerAssigner.TURAEL), MAXED, Diaries.NONE,
				BoostInterval.EVERY_10TH));
		// Krystilia's own streak: her task 8 is in progress, then 9 and 10
		assertEquals(3, SlayerPointsForecast.atMaster(150, SlayerAssigner.KRYSTILIA, onTask(47, SlayerAssigner.KRYSTILIA),
				Diaries.NONE));
	}

	@Test
	public void forecastGivesUpWhenNothingPays()
	{
		assertEquals(-1, SlayerPointsForecast.atMaster(10, SlayerAssigner.TURAEL, idle(47), Diaries.NONE));
		assertEquals(-1, SlayerPointsForecast.withRoutine(10, idle(47), SlayerMasterAccess.options(SlayerMasterAccessTest.fresh()),
				Diaries.NONE, BoostInterval.EVERY_10TH));
	}
}
