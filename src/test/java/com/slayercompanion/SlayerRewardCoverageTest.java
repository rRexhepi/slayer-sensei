package com.slayercompanion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.DBTableID.SlayerUnlock.Row;
import org.junit.Test;

/**
 * Keeps {@link SlayerReward} in step with the reward shop. A game update that adds a reward fails
 * here once the RuneLite api names its row, or once the fixture is regenerated from the new cache.
 */
public class SlayerRewardCoverageTest
{
	/**
	 * Rows the advice layer leaves out on purpose. On 27 August 2025 the metal dragons became one task
	 * and these four rewards left the shop (OSRS Wiki, Slayer Rewards changes). The rows stay in the
	 * cache with list position (-1, -1), which SlayerGameData reads as unlisted.
	 */
	private static final Map<Integer, String> IGNORED = new HashMap<>();

	static
	{
		IGNORED.put(Row.SLAYER_REWARDS_UNLOCK_MITHRILDRAGONS, "I Hope You Mith Me");
		IGNORED.put(Row.SLAYER_REWARDS_LONGER_MITHRILDRAGONS, "I Really Mith You");
		IGNORED.put(Row.SLAYER_REWARDS_LONGER_ADAMANTDRAGONS, "Ada'mind Some More");
		IGNORED.put(Row.SLAYER_REWARDS_LONGER_RUNEDRAGONS, "RUUUUUNE");
	}

	private static final SlayerCacheFixture FIXTURE = SlayerCacheFixture.get();
	private static final SlayerGameData DATA = SlayerGameData.load(FIXTURE);

	@Test
	public void everyNamedRowIsCoveredOrIgnored()
	{
		final Set<Integer> covered = rewardRows();
		int rows = 0;
		for (Field f : Row.class.getDeclaredFields())
		{
			final int m = f.getModifiers();
			if (!Modifier.isStatic(m) || f.getType() != int.class) continue;
			final int row = rowValue(f);
			rows++;
			assertTrue("SlayerUnlock row " + f.getName() + " is new. Add it to SlayerReward or to IGNORED with a reason.",
					covered.contains(row) || IGNORED.containsKey(row));
			assertFalse(f.getName() + " is both covered and ignored", covered.contains(row) && IGNORED.containsKey(row));
		}
		assertEquals(rows, covered.size() + IGNORED.size());
	}

	@Test
	public void everyCacheRowIsCoveredOrIgnored()
	{
		final Set<Integer> covered = rewardRows();
		for (int row : FIXTURE.table(DBTableID.SlayerUnlock.ID).rows)
		{
			assertTrue("Cache row " + row + " (" + FIXTURE.rowName(row) + ") is not in SlayerReward",
					covered.contains(row) || IGNORED.containsKey(row));
		}
	}

	@Test
	public void ignoredRowsAreOutOfTheShop()
	{
		for (Map.Entry<Integer, String> e : IGNORED.entrySet())
		{
			final SlayerGameData.Unlock unlock = DATA.unlock(e.getKey());
			assertEquals(e.getValue(), unlock.getName());
			assertFalse(e.getValue() + " is back in the shop. Add it to SlayerReward.", unlock.isListed());
		}
		for (SlayerReward r : SlayerReward.values())
		{
			assertTrue(r + " left the shop", DATA.unlock(r.getRow()).isListed());
		}
	}

	@Test
	public void fallbackNamesAndCostsMatchTheCache()
	{
		for (SlayerReward r : SlayerReward.values())
		{
			final SlayerGameData.Unlock unlock = DATA.unlock(r.getRow());
			assertNotNull(r.name(), unlock);
			assertEquals(r.name(), unlock.getName(), r.getFallbackName());
			assertEquals(r.name(), unlock.getCost(), r.getFallbackCost());
		}
	}

	@Test
	public void fallbackVarbitsSitOnTheRowsBit()
	{
		for (SlayerReward r : SlayerReward.values())
		{
			final int bit = DATA.unlock(r.getRow()).getBit();
			final SlayerCacheFixture.Varbit varbit = FIXTURE.varbit(r.getFallbackVarbit());
			assertNotNull(r.name(), varbit);
			assertEquals(r.name(), SlayerGameData.UNLOCK_VARPS[bit / 32], varbit.varp);
			assertEquals(r.name(), bit % 32, varbit.lsb);
			assertEquals(r.name(), bit % 32, varbit.msb);
		}
	}

	@Test
	public void slayerLevelsMatchTheTasksTheRewardNames()
	{
		for (SlayerReward r : SlayerReward.values())
		{
			final SlayerReward.Kind kind = r.getKind();
			// Rewards that only change the pool wait for the level of the task itself, from the cache
			if (kind == SlayerReward.Kind.TASK_UNLOCK || kind == SlayerReward.Kind.WEIGHTING || kind == SlayerReward.Kind.COSMETIC)
			{
				continue;
			}
			int expected = 0;
			for (int task : DATA.unlock(r.getRow()).getRelatedTaskRows())
			{
				expected = Math.max(expected, DATA.slayerLevel(task));
			}
			assertEquals(r.name(), expected, r.getSlayerLevel());
		}
	}

	/** The kinds follow the shop's own tabs and the cache's task columns, so a reward cannot be valued the wrong way. */
	@Test
	public void kindsMatchTheCache()
	{
		for (SlayerReward r : SlayerReward.values())
		{
			final SlayerGameData.Unlock unlock = DATA.unlock(r.getRow());
			final SlayerReward.Kind kind = r.getKind();
			assertEquals(r.name(), kind == SlayerReward.Kind.EXTENSION, unlock.getTab() == SlayerGameData.TAB_EXTEND);
			assertEquals(r.name(), kind == SlayerReward.Kind.COSMETIC, unlock.getTab() == SlayerGameData.TAB_COSMETIC);
			// Every extension lengthens its task in the cache, and nothing else does
			assertEquals(r.name(), kind == SlayerReward.Kind.EXTENSION, !DATA.extensions(r.getRow()).isEmpty());
			assertEquals(r.name(), kind == SlayerReward.Kind.WEIGHTING, !DATA.weightings(r.getRow()).isEmpty());
			assertEquals(r.name(), kind == SlayerReward.Kind.BLOCKER, !DATA.blockedBy(r.getRow()).isEmpty());
			// Task unlocks gate some master's pool, except one that no master needs yet
			if (DATA.gatesPool(r.getRow())) assertEquals(r.name(), SlayerReward.Kind.TASK_UNLOCK, kind);
		}
	}

	@Test
	public void taskNamesExistInTheCache()
	{
		for (SlayerReward r : SlayerReward.values())
		{
			if (r.getTask() == null) continue;
			assertFalse(r.name() + " task '" + r.getTask() + "'", DATA.taskRowsNamed(r.getTask()).isEmpty());
		}
	}

	private static Set<Integer> rewardRows()
	{
		final Set<Integer> rows = new HashSet<>();
		for (SlayerReward r : SlayerReward.values())
		{
			assertTrue(r + " shares a row", rows.add(r.getRow()));
		}
		return rows;
	}

	private static int rowValue(Field f)
	{
		try
		{
			return f.getInt(null);
		}
		catch (IllegalAccessException e)
		{
			throw new AssertionError(e);
		}
	}
}
