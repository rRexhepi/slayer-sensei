package com.slayercompanion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.slayercompanion.SlayerPool.Status;
import com.slayercompanion.data.TaskRating;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import net.runelite.api.Skill;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.DBTableID.SlayerUnlock.Row;
import org.junit.Test;

/** Which tasks a master can give this player, against the live cache dump and the dossier's ratings. */
public class SlayerPoolTest
{
	private static final SlayerGameData DATA = SlayerMasterRankerTest.DATA;

	private static SlayerPool pool(SlayerAssigner master, SlayerPool.Player player, Integer... blocked)
	{
		return SlayerPool.of(master, DATA, player, new HashSet<>(Arrays.asList(blocked)), SlayerMasterRankerTest.RATINGS);
	}

	private static SlayerPool.Player player(int[] levels, int combat, boolean checksOff, Set<Integer> owned, Set<Integer> removed)
	{
		return new SlayerPool.Player(levels, combat, checksOff, owned, removed);
	}

	private static int[] levelsWith(Skill skill, int level)
	{
		final int[] levels = SlayerMasterRankerTest.levels(99);
		levels[skill.ordinal()] = level;
		return levels;
	}

	private static Status status(SlayerPool pool, int taskRow)
	{
		return pool.find(taskRow).getStatus();
	}

	@Test
	public void konarsPoolMatchesTheCache()
	{
		final SlayerPool konar = pool(SlayerAssigner.KONAR, SlayerMasterRankerTest.maxed());
		assertEquals(39, konar.getEntries().size());
		// 250 in all. Like a Boss, Seeing Red, Watch the Birdie, Reptile Got Ripped, Basilocked, Actual
		// Vampyre Slayer and Warped Reality gate 40 of it.
		assertEquals(210, konar.openWeight());
		assertEquals(210, konar.assignableWeight());
		assertEquals(Status.NEEDS_UNLOCK, status(konar, DBTableID.SlayerTask.Row.SLAYER_TARGET_BOSS));
		// Every rated task the master can give is rated, so the expected rating is a weighted mean of 1 to 5
		final double xp = konar.expected(SlayerGoal.XP);
		assertTrue(String.valueOf(xp), xp > 1 && xp < 5);
		assertEquals(15 / 210.0, konar.chance(konar.find(DBTableID.SlayerTask.Row.SLAYER_TARGET_METALDRAGONS)), 1e-9);
	}

	@Test
	public void ownedUnlocksOpenTheirTasks()
	{
		final Set<Integer> owned = new HashSet<>(Collections.singletonList(Row.SLAYER_REWARDS_UNLOCK_BOSSES));
		final SlayerPool konar = pool(SlayerAssigner.KONAR, SlayerMasterRankerTest.maxed(owned));
		assertEquals(Status.OPEN, status(konar, DBTableID.SlayerTask.Row.SLAYER_TARGET_BOSS));
		assertEquals(218, konar.openWeight());
		// Mortimer assigns gryphons, aquanites and basilisks without their unlocks (wiki Mortimer)
		final SlayerPool mortimer = pool(SlayerAssigner.MORTIMER, SlayerMasterRankerTest.maxed());
		assertEquals(mortimer.getEntries().size(), mortimer.open().size());
	}

	@Test
	public void slayerAndOtherStatLevelsAreChecked()
	{
		// 87 Slayer and 50 Magic for cave kraken
		assertEquals(Status.NEEDS_LEVEL, status(pool(SlayerAssigner.KONAR, player(levelsWith(Skill.MAGIC, 49), 126, false,
				Collections.<Integer>emptySet(), Collections.<Integer>emptySet())), DBTableID.SlayerTask.Row.SLAYER_TARGET_CAVEKRAKEN));
		final SlayerPool slayer86 = pool(SlayerAssigner.KONAR, player(levelsWith(Skill.SLAYER, 86), 126, false,
				Collections.<Integer>emptySet(), Collections.<Integer>emptySet()));
		assertEquals(Status.NEEDS_LEVEL, status(slayer86, DBTableID.SlayerTask.Row.SLAYER_TARGET_CAVEKRAKEN));
		assertEquals(Status.OPEN, status(slayer86, DBTableID.SlayerTask.Row.SLAYER_TARGET_ABYSSALDEMONS));
		// One of 60 Strength or 60 Agility for the God Wars Dungeon
		final int[] weak = levelsWith(Skill.STRENGTH, 59);
		assertEquals(Status.OPEN, status(pool(SlayerAssigner.DURADEL, player(weak, 126, false,
				Collections.<Integer>emptySet(), Collections.<Integer>emptySet())), DBTableID.SlayerTask.Row.SLAYER_TARGET_SPIRITUALGWD));
		weak[Skill.AGILITY.ordinal()] = 59;
		assertEquals(Status.NEEDS_LEVEL, status(pool(SlayerAssigner.DURADEL, player(weak, 126, false,
				Collections.<Integer>emptySet(), Collections.<Integer>emptySet())), DBTableID.SlayerTask.Row.SLAYER_TARGET_SPIRITUALGWD));
	}

	@Test
	public void combatLevelIsCheckedUnlessSwitchedOffOrAtKrystilia()
	{
		final SlayerPool.Player combat70 = player(SlayerMasterRankerTest.levels(99), 70, false,
				Collections.<Integer>emptySet(), Collections.<Integer>emptySet());
		// Black demons need combat level 80
		assertEquals(Status.NEEDS_LEVEL, status(pool(SlayerAssigner.KONAR, combat70), DBTableID.SlayerTask.Row.SLAYER_TARGET_BLACKDEMONS));
		// Krystilia "will assign any monster regardless of your combat level" (wiki Krystilia)
		assertEquals(Status.OPEN, status(pool(SlayerAssigner.KRYSTILIA, combat70), DBTableID.SlayerTask.Row.SLAYER_TARGET_BLACKDEMONS));
		// The player can tell the masters to ignore combat level (COMBAT_LEVEL_REQS_DISABLED)
		assertEquals(Status.OPEN, status(pool(SlayerAssigner.KONAR, player(SlayerMasterRankerTest.levels(99), 70, true,
				Collections.<Integer>emptySet(), Collections.<Integer>emptySet())), DBTableID.SlayerTask.Row.SLAYER_TARGET_BLACKDEMONS));
	}

	@Test
	public void blocksAndBlockersAreKeptApart()
	{
		final int fossil = DBTableID.SlayerTask.Row.SLAYER_TARGET_FOSSILWYVERNS;
		final int metal = DBTableID.SlayerTask.Row.SLAYER_TARGET_METALDRAGONS;
		final SlayerPool konar = pool(SlayerAssigner.KONAR, player(SlayerMasterRankerTest.levels(99), 126, false,
				Collections.<Integer>emptySet(), Collections.singleton(fossil)), metal);
		assertEquals(Status.REMOVED, status(konar, fossil));
		assertEquals(Status.BLOCKED, status(konar, metal));
		assertTrue(konar.find(metal).isAssignable());
		assertFalse(konar.find(fossil).isAssignable());
		assertEquals(15, konar.blockedWeight());
		assertEquals(210 - 5, konar.assignableWeight());
		assertEquals(210 - 5 - 15, konar.openWeight());
		// Lifting the blocks keeps the blocker
		final SlayerPool lifted = konar.withBlocks(Collections.<Integer>emptyList());
		assertEquals(Status.OPEN, status(lifted, metal));
		assertEquals(Status.REMOVED, status(lifted, fossil));
		assertEquals(0, lifted.chance(lifted.find(fossil)), 0);
	}

	@Test
	public void krystiliaUsesTheWildernessRatings()
	{
		final int bloodveld = DBTableID.SlayerTask.Row.SLAYER_TARGET_BLOODVELD;
		final TaskRating wild = pool(SlayerAssigner.KRYSTILIA, SlayerMasterRankerTest.maxed()).find(bloodveld).getRating();
		final TaskRating usual = pool(SlayerAssigner.KONAR, SlayerMasterRankerTest.maxed()).find(bloodveld).getRating();
		assertEquals(1, wild.getSpeed());
		assertEquals(4, usual.getSpeed());
		assertEquals(5, usual.forGoal(SlayerGoal.XP));
		assertEquals(4, usual.forGoal(SlayerGoal.POINTS));
	}

	@Test
	public void expectedAndShareCountOnlyWhatTheMasterCanGive()
	{
		final SlayerPool pool = SlayerPool.of(SlayerAssigner.KONAR, Arrays.asList(
				entry(1, 3, rating(5, 1, 4), Status.OPEN),
				entry(2, 1, rating(1, 5, 2), Status.OPEN),
				entry(3, 6, rating(1, 1, 1), Status.BLOCKED),
				entry(4, 6, null, Status.OPEN)));
		// (3 * 5 + 1 * 1) / 4, the unrated task left out
		assertEquals(4.0, pool.expected(SlayerGoal.XP), 1e-9);
		assertEquals(0.75, pool.share(SlayerGoal.XP, 4), 1e-9);
		assertEquals(0.25, pool.share(SlayerGoal.PROFIT, 4), 1e-9);
		assertEquals(10, pool.openWeight());
		assertEquals(16, pool.assignableWeight());
		assertNull(pool.find(5));
		assertEquals(-1, SlayerPool.of(SlayerAssigner.KONAR, Collections.<SlayerPool.Entry>emptyList()).expected(SlayerGoal.XP), 0);
	}

	static TaskRating rating(int xp, int gp, int speed)
	{
		final TaskRating r = new TaskRating();
		r.setXp(xp);
		r.setGp(gp);
		r.setSpeed(speed);
		r.setBasis("test");
		return r;
	}

	/** A task of 100 kills, judged at 100. */
	static SlayerPool.Entry entry(int row, int weight, TaskRating rating, Status status)
	{
		return new SlayerPool.Entry(row, "Task " + row, weight, 100, 100, 100, status, rating);
	}
}
