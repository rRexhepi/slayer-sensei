package com.slayercompanion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.slayercompanion.data.SlayerTask;
import com.slayercompanion.data.TaskRating;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;

/**
 * The XP advice at Kuradal against the wiki's task summary for Duradel's tasks (Slayer training, Task summary), kept in
 * the test resource wiki_task_summary_duradel.json with the page and the date it was read. The column's advice is in
 * the ratings data ({@link TaskRating#getXpAdvice}) and the XP advice follows it both ways: a task to do is done, a task
 * to skip or block is skipped or blocked, never extended and never called good, and an unlock it says not to make is
 * never recommended. The XP ratings themselves follow the table's XP per hour for the fastest method, with the cut
 * points in {@link TaskRating}.
 */
public class SlayerTaskSummaryGuardTest
{
	private static final SlayerGameData DATA = SlayerPointsPlannerTest.DATA;
	private static final SlayerDataset DATASET = SlayerPointsPlannerTest.DATASET;

	/** One row of the wiki's table. */
	static final class Row
	{
		String wiki;
		/** The cache name the plugin uses. */
		String task;
		String recommendation;
		String xpPerHour;
		/** do, skip, dontUnlock or none. */
		String forXp;
	}

	static final class Summary
	{
		String page;
		String section;
		String url;
		String read;
		List<Row> tasks;
	}

	static Summary summary()
	{
		try (Reader reader = new InputStreamReader(SlayerTaskSummaryGuardTest.class.getResourceAsStream("/wiki_task_summary_duradel.json"),
				StandardCharsets.UTF_8))
		{
			return new Gson().fromJson(reader, Summary.class);
		}
		catch (IOException ex)
		{
			throw new AssertionError(ex);
		}
	}

	private static List<Row> rows(String forXp)
	{
		final List<Row> out = new ArrayList<>();
		for (Row r : summary().tasks)
		{
			if (r.forXp.equals(forXp)) out.add(r);
		}
		return out;
	}

	@Test
	public void theTableSaysWhereItIsFrom()
	{
		final Summary s = summary();
		assertEquals("Slayer training", s.page);
		assertEquals("Task summary", s.section);
		assertEquals("2026-10-04", s.read);
		assertTrue(s.tasks.size() >= 40);
		// Every row is one of Duradel's tasks in the cache
		final Set<String> duradel = new HashSet<>();
		for (SlayerGameData.PoolEntry e : DATA.pool(SlayerAssigner.DURADEL.getId())) duradel.add(DATA.taskName(e.getTaskRow()));
		for (Row r : s.tasks)
		{
			assertTrue(r.task, duradel.contains(r.task));
			assertTrue(r.forXp, Arrays.asList("do", "skip", "dontUnlock", "none").contains(r.forXp));
		}
		assertEquals(12, rows("do").size());
		assertEquals(20, rows("skip").size());
		assertEquals(7, rows("dontUnlock").size());
	}

	@Test
	public void theRatingsDataCarriesTheColumn()
	{
		for (Row r : summary().tasks)
		{
			final TaskRating.XpAdvice advice = DATASET.lookup(r.task).getRating().getXpAdvice();
			switch (r.forXp)
			{
				case "do":
					assertEquals(r.task, TaskRating.XpAdvice.DO, advice);
					break;
				case "skip":
					assertEquals(r.task, TaskRating.XpAdvice.SKIP, advice);
					break;
				case "dontUnlock":
					assertEquals(r.task, TaskRating.XpAdvice.DONT_UNLOCK, advice);
					break;
				default:
					assertNull(r.task, advice);
			}
		}
	}

	// ---- the XP ratings follow the table ----

	/** A figure, a range such as 100,000 to 110,000, and the method in brackets. */
	private static final Pattern METHOD = Pattern.compile("(\\d{1,3}(?:,\\d{3})+)(?:\\s*[\u2013-]\\s*(\\d{1,3}(?:,\\d{3})+))?(?:\\s*\\(([^)]*)\\))?");
	/** Boss and variant figures, which the rating leaves out. */
	private static final Pattern LEFT_OUT = Pattern.compile("Cerberus|Tormented|Wyrmlings", Pattern.CASE_INSENSITIVE);

	/** The fastest of the table's methods for the regular monster, a range counting as its middle. NaN without a figure. */
	static double xpPerHour(String cell)
	{
		double fastest = Double.NaN;
		final Matcher m = METHOD.matcher(cell);
		while (m.find())
		{
			if (m.group(3) != null && LEFT_OUT.matcher(m.group(3)).find()) continue;
			final double low = Double.parseDouble(m.group(1).replace(",", ""));
			final double value = m.group(2) == null ? low : (low + Double.parseDouble(m.group(2).replace(",", ""))) / 2;
			if (Double.isNaN(fastest) || value > fastest) fastest = value;
		}
		return fastest;
	}

	/** The cut points halfway between the scale's anchors. */
	static int rating(double xpPerHour)
	{
		if (xpPerHour >= 77_500) return 5;
		if (xpPerHour >= 55_000) return 4;
		if (xpPerHour >= 35_000) return 3;
		if (xpPerHour >= 20_000) return 2;
		return 1;
	}

	@Test
	public void theCutPointsReadTheTablesFigures()
	{
		// The method an XP player uses: barraging abyssal demons, not the Venator bow or melee
		assertEquals(80_000, xpPerHour("80,000 (Magic);  53,000 (Venator Bow);  40,000 (Melee)"), 0);
		assertEquals(105_000, xpPerHour("100,000\u2013110,000 (Magic);  60,000 (Venator bow);  35,000 (Melee)"), 0);
		// The regular monster, not the boss or variant
		assertEquals(40_000, xpPerHour("40,000 (Regular);  25,000 (Cerberus)"), 0);
		assertEquals(38_000, xpPerHour("38,000 (Regular) 50,000 (Tormented Demons)"), 0);
		assertEquals(35_000, xpPerHour("53,000 (Wyrmlings Cannon and Venator bow) 35,000"), 0);
		assertTrue(Double.isNaN(xpPerHour("Generally poor, but varies")));
		// The wiki's own numbers the first review named: dark beasts 42k, fire giants and hellhounds 40k are fair
		assertEquals(3, rating(42_000));
		assertEquals(3, rating(40_000));
		assertEquals(5, rating(80_000));
		assertEquals(4, rating(55_000));
		assertEquals(2, rating(30_000));
		assertEquals(1, rating(15_000));
	}

	@Test
	public void xpRatingsFollowTheTable()
	{
		int checked = 0;
		for (Row r : summary().tasks)
		{
			final double xp = xpPerHour(r.xpPerHour);
			if (Double.isNaN(xp)) continue;
			final SlayerTask task = DATASET.lookup(r.task);
			assertNotNull(r.task, task);
			assertEquals(r.task + " at " + r.xpPerHour, rating(xp), task.getRating().getXp());
			checked++;
		}
		assertTrue(checked >= 40);
	}

	// ---- the advice follows the Recommendation column both ways ----

	private static int idOf(String task)
	{
		for (int id = 0; id < 400; id++)
		{
			final int row = DATA.taskRowForId(id);
			if (row >= 0 && task.equals(DATA.taskName(row))) return id;
		}
		throw new AssertionError("No task id for " + task);
	}

	/** A maxed account at Kuradal with points to spare on task {@code task}, with these task ids on Kuradal's block list. */
	private static SlayerPlanBuilder.View kuradal(String task, List<Integer> blocked)
	{
		final SlayerPointsPlannerTest.Account account = new SlayerPointsPlannerTest.Account().withVarbit(VarbitID.SLAYER_POINTS, 5000)
				.withAllSlots().onTask(SlayerAssigner.DURADEL, 11);
		final int[] slots = SlayerMaster.DURADEL.getBlockVarbits();
		for (int i = 0; i < blocked.size(); i++) account.withVarbit(slots[i], blocked.get(i));
		account.varps.put(VarPlayerID.SLAYER_TARGET, idOf(task));
		return SlayerPlanBuilder.build(account, DATA, DATASET, SlayerPointsPlannerTest.pinned(SlayerMaster.DURADEL, SlayerGoal.XP),
				SlayerMasterAccessTest.maxed());
	}

	/** The task ids of the blocks the advice puts forward at Kuradal for XP when nothing is blocked. */
	private static List<Integer> advisedBlocks()
	{
		final List<Integer> ids = new ArrayList<>();
		for (int row : kuradal("Smoke Devils", Collections.<Integer>emptyList()).getBlockAdvice().getAddRows()) ids.add(idOf(DATA.taskName(row)));
		return ids;
	}

	@Test
	public void tasksTheWikiSaysToDoAreDone()
	{
		final List<Integer> advised = advisedBlocks();
		assertEquals(7, advised.size());
		// With nothing blocked, and with the advised blocks in place
		for (List<Integer> blocked : Arrays.asList(Collections.<Integer>emptyList(), advised))
		{
			for (Row r : rows("do"))
			{
				final SlayerPlanBuilder.View view = kuradal(r.task, blocked);
				assertFalse(r.task, view.getBlockAdvice().getAdd().contains(r.task));
				final String line = view.getTaskAdvice();
				assertNotNull(r.task, line);
				assertTrue(r.task + ": " + line, line.startsWith("Do it. The wiki's Slayer guide says to do it for XP."));
			}
		}
	}

	@Test
	public void tasksTheWikiSaysToSkipAreSkippedOrBlockedAndNeverExtended()
	{
		final List<String> plan = planned(kuradalPlan(50000));
		final List<Row> skip = new ArrayList<>(rows("skip"));
		skip.addAll(rows("dontUnlock"));
		for (Row r : skip)
		{
			// Poor at best for XP wherever the list judges it, whatever its XP per hour
			assertTrue(r.task, effectiveXp(r.task) < SlayerSpend.GOOD);
			final String line = kuradal(r.task, Collections.<Integer>emptyList()).getTaskAdvice();
			assertTrue(r.task + ": " + line, line.startsWith("Skip it for 30 points.") || line.startsWith("Block it for 100 points."));
			assertTrue(r.task + ": " + line, line.contains("The wiki's Slayer guide says"));
			assertFalse(r.task + ": " + line, line.contains("Buy ") || line.contains("good XP task") || line.contains("excellent XP task"));
			// Not its extension in the plan either
			final SlayerGameData.Extension x = DATA.extensionOf(DATA.taskRowForId(idOf(r.task)));
			if (x != null) assertFalse(r.task + ": " + plan, plan.contains(DATA.unlock(x.getUnlockRow()).getName()));
		}
		// An 80k task the wiki says to block is not called poor: the line cites the guide
		final String abyssal = kuradal("Abyssal Demons", Collections.<Integer>emptyList()).getTaskAdvice();
		assertEquals("Block it for 100 points. The wiki's Slayer guide says to skip or block it for XP. A block slot is free and you have 5000"
				+ " points.", abyssal);
		assertEquals("The wiki's Slayer guide says to skip or block it for XP. Fair for profit. Takes an average time.",
				SlayerTaskText.verdict(DATASET.lookup("Abyssal Demons"), SlayerGoal.XP, false).split("\n")[0]);
	}

	@Test
	public void unlocksTheWikiSaysNotToMakeAreNeverRecommendedForXp()
	{
		final List<String> doNotUnlock = new ArrayList<>();
		for (SlayerReward r : SlayerReward.values())
		{
			if (r.getKind() != SlayerReward.Kind.TASK_UNLOCK) continue;
			for (Row row : rows("dontUnlock"))
			{
				for (SlayerGameData.PoolEntry e : DATA.pool(SlayerAssigner.DURADEL.getId()))
				{
					if (e.getUnlockRow() == r.getRow() && row.task.equals(DATA.taskName(e.getTaskRow())) && !doNotUnlock.contains(name(r)))
					{
						doNotUnlock.add(name(r));
					}
				}
			}
		}
		assertEquals(7, doNotUnlock.size());
		for (int points : new int[]{521, 5000, 50000})
		{
			final SlayerPointsPlanner.Plan plan = kuradalPlan(points);
			final List<String> planned = planned(plan);
			final List<String> skipThese = new ArrayList<>();
			for (SlayerPointsPlanner.Line l : plan.getAvoid()) skipThese.add(l.getName());
			for (String unlock : doNotUnlock)
			{
				assertFalse(unlock + " at " + points, planned.contains(unlock));
				assertTrue(unlock + " at " + points + ": " + skipThese, skipThese.contains(unlock));
			}
		}
	}

	/** The task's XP rating as Kuradal's list judges it, the wiki's advice included. */
	private static int effectiveXp(String task)
	{
		final SlayerPool pool = SlayerPool.of(SlayerAssigner.DURADEL, DATA, SlayerMasterRankerTest.maxed(), Collections.<Integer>emptySet(),
				SlayerMasterRankerTest.RATINGS);
		for (SlayerPool.Entry e : pool.getEntries())
		{
			if (e.getName().equals(task)) return e.rating(SlayerGoal.XP);
		}
		throw new AssertionError(task);
	}

	private static String name(SlayerReward r)
	{
		return DATA.unlock(r.getRow()).getName();
	}

	private static SlayerPointsPlanner.Plan kuradalPlan(int points)
	{
		return SlayerPlanBuilder.build(new SlayerPointsPlannerTest.Account().withVarbit(VarbitID.SLAYER_POINTS, points).withAllSlots(), DATA,
				DATASET, SlayerPointsPlannerTest.pinned(SlayerMaster.DURADEL, SlayerGoal.XP), SlayerMasterAccessTest.maxed()).getPlan();
	}

	/** Every purchase the plan recommends or saves for. */
	private static List<String> planned(SlayerPointsPlanner.Plan plan)
	{
		final List<String> out = new ArrayList<>();
		for (List<SlayerPointsPlanner.Line> lines : Arrays.asList(plan.getBuyNow(), plan.getSaveFor(), plan.getLater()))
		{
			for (SlayerPointsPlanner.Line l : lines) out.add(l.getName());
		}
		return out;
	}
}
