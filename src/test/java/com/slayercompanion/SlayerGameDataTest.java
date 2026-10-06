package com.slayercompanion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.DBTableID.SlayerUnlock.Row;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;

/** SlayerGameData against a trimmed dump of the live cache (see tools/cache-dump). */
public class SlayerGameDataTest
{
	private static final SlayerCacheFixture FIXTURE = SlayerCacheFixture.get();
	private static final SlayerGameData DATA = SlayerGameData.load(FIXTURE);

	@Test
	public void loadsTheRealTables()
	{
		assertNotNull(DATA);
		assertEquals(FIXTURE.table(DBTableID.SlayerUnlock.ID).rows.size(), DATA.unlocks().size());
		assertEquals("Bigger and Badder", DATA.unlock(Row.SLAYER_REWARDS_UNLOCK_SUPERIORMOBS).getName());
		assertEquals(50, DATA.unlock(Row.SLAYER_REWARDS_UNLOCK_SUPERIORMOBS).getCost());
	}

	@Test
	public void unlockBitIsABitIndexAcrossTheUnlockVarps()
	{
		// Bigger and Badder sits on bit 35: bit 3 of the second unlock varp, where its varbit lives
		assertEquals(35, DATA.unlock(Row.SLAYER_REWARDS_UNLOCK_SUPERIORMOBS).getBit());
		final SlayerCacheFixture.Varbit varbit = FIXTURE.varbit(VarbitID.SLAYER_UNLOCK_SUPERIORMOBS);
		assertEquals(VarPlayerID.SLAYER_REWARDS_UNLOCKS1, varbit.varp);
		assertEquals(3, varbit.lsb);

		final Map<Integer, Integer> varps = new HashMap<>();
		varps.put(VarPlayerID.SLAYER_REWARDS_UNLOCKS1, 1 << 3);
		assertTrue(SlayerGameData.ownsBit(v -> varps.getOrDefault(v, 0), 35));
		// The same bit of the first varp is 'Shroom Sprayer, and bit 67 is past the last reward
		assertFalse(SlayerGameData.ownsBit(v -> varps.getOrDefault(v, 0), 3));
		assertFalse(SlayerGameData.ownsBit(v -> -1, 96));
		assertFalse(SlayerGameData.ownsBit(v -> -1, -1));
	}

	@Test
	public void everyBitLandsOnTheVarbitNamedLikeItsRow()
	{
		// e.g. SLAYER_REWARDS_LONGER_ABYSSALDEMONS sits on SLAYER_LONGER_ABYSSALDEMONS
		for (int row : FIXTURE.table(DBTableID.SlayerUnlock.ID).rows)
		{
			final int bit = DATA.unlock(row).getBit();
			final SlayerCacheFixture.Varbit varbit = varbitAt(bit);
			assertNotNull(FIXTURE.rowName(row) + " bit " + bit + " has no varbit", varbit);
			final String stem = FIXTURE.rowName(row).replace("SLAYER_REWARDS_", "").replace("SLAYER_", "").replace("_", "");
			// A few varbits are named differently from their rows
			final String varbitStem = varbit.name.replace("SLAYER_", "").replace("UNLOCK_LONGER", "LONGER")
					.replace("WEIGHTED_LONGER", "WEIGHTED").replace("WILDY_EXTRATASKS", "WILDERNESS_NEWTASKS").replace("_", "");
			assertTrue(FIXTURE.rowName(row) + " vs " + varbit.name, stem.startsWith(varbitStem) || varbitStem.startsWith(stem));
		}
	}

	static SlayerCacheFixture.Varbit varbitAt(int bit)
	{
		for (SlayerCacheFixture.Varbit v : FIXTURE.varbits())
		{
			if (v.varp == SlayerGameData.UNLOCK_VARPS[bit / 32] && v.lsb == bit % 32 && v.msb == bit % 32) return v;
		}
		return null;
	}

	@Test
	public void listColumnsKeepEveryEntry()
	{
		// Pedal to the Metals names every metal dragon task. Reading only the first entry lost the rest.
		final List<Integer> metal = DATA.unlock(Row.SLAYER_REWARDS_LONGER_METALDRAGONS).getRelatedTaskRows();
		assertTrue(metal.containsAll(Arrays.asList(
				DBTableID.SlayerTask.Row.SLAYER_TARGET_METALDRAGONS, DBTableID.SlayerTask.Row.SLAYER_TARGET_BRONZEDRAGONS,
				DBTableID.SlayerTask.Row.SLAYER_TARGET_IRONDRAGONS, DBTableID.SlayerTask.Row.SLAYER_TARGET_STEELDRAGONS,
				DBTableID.SlayerTask.Row.SLAYER_TARGET_MITHRILDRAGONS, DBTableID.SlayerTask.Row.SLAYER_TARGET_RUNEDRAGONS)));
		assertEquals(4, DATA.unlock(Row.SLAYER_REWARDS_UNLOCK_WILDERNESS_NEWTASKS).getRelatedTaskRows().size());
		// Rewards without a task have an empty list, not a -1
		assertTrue(DATA.unlock(Row.SLAYER_REWARDS_UNLOCK_SUPERIORMOBS).getRelatedTaskRows().isEmpty());
	}

	@Test
	public void rewardsTakenOutOfTheShopAreUnlisted()
	{
		assertFalse(DATA.unlock(Row.SLAYER_REWARDS_UNLOCK_MITHRILDRAGONS).isListed());
		assertFalse(DATA.unlock(Row.SLAYER_REWARDS_LONGER_MITHRILDRAGONS).isListed());
		assertFalse(DATA.unlock(Row.SLAYER_REWARDS_LONGER_ADAMANTDRAGONS).isListed());
		assertFalse(DATA.unlock(Row.SLAYER_REWARDS_LONGER_RUNEDRAGONS).isListed());
		int listed = 0;
		for (SlayerGameData.Unlock u : DATA.unlocks())
		{
			if (u.isListed()) listed++;
		}
		assertEquals(DATA.unlocks().size() - 4, listed);
	}

	@Test
	public void masterIdsMatchTheirPoolRows()
	{
		final Map<String, SlayerMaster> prefixes = new HashMap<>();
		for (SlayerMaster m : SlayerMaster.values())
		{
			prefixes.put(m == SlayerMaster.KRYSTILIA ? "KRYSTILLIA_" : m.name() + "_", m);
		}
		int checked = 0;
		for (Field f : DBTableID.SlayerMasterTask.Row.class.getFields())
		{
			final SlayerMaster master = prefixes.get(f.getName().substring(0, f.getName().indexOf('_') + 1));
			if (master == null) continue;
			final int row = (Integer) staticValue(f);
			assertEquals(f.getName(), master.getId(), FIXTURE.intValue(row, "COL_MASTER_ID"));
			checked++;
		}
		assertTrue(checked > 300);
		for (SlayerMaster m : SlayerMaster.values())
		{
			assertFalse(m.name(), DATA.pool(m).isEmpty());
			assertEquals(m, SlayerMaster.forId(m.getId()));
		}
		// Spria has her own pool but uses Turael's block list
		assertNull(SlayerMaster.forId(9));
		assertEquals(SlayerMaster.TURAEL, SlayerMaster.forAssigner(SlayerAssigner.SPRIA));
		assertEquals(25, DATA.pool(SlayerAssigner.SPRIA.getId()).size());
		assertEquals(29, DATA.pool(SlayerAssigner.MORTIMER.getId()).size());
		for (SlayerAssigner a : SlayerAssigner.values())
		{
			assertFalse(a.name(), DATA.pool(a.getId()).isEmpty());
		}
	}

	@Test
	public void requirementsAndAmountsComeFromTheCache()
	{
		final int kraken = DBTableID.SlayerTask.Row.SLAYER_TARGET_CAVEKRAKEN;
		// (87 Slayer, 50 Magic) and combat level 80
		assertEquals(80, DATA.combatLevel(kraken));
		assertEquals(Arrays.asList(new SlayerGameData.StatLevel(18, 87), new SlayerGameData.StatLevel(6, 50)), DATA.statsAll(kraken));
		assertTrue(DATA.statsAny(kraken).isEmpty());
		// The God Wars Dungeon takes 60 Strength or 60 Agility
		assertEquals(Arrays.asList(new SlayerGameData.StatLevel(2, 60), new SlayerGameData.StatLevel(16, 60)),
				DATA.statsAny(DBTableID.SlayerTask.Row.SLAYER_TARGET_SPIRITUALGWD));
		assertEquals(0, DATA.combatLevel(DBTableID.SlayerTask.Row.SLAYER_TARGET_RATS));

		SlayerGameData.PoolEntry metal = null;
		for (SlayerGameData.PoolEntry e : DATA.pool(SlayerMaster.KONAR))
		{
			if (e.getTaskRow() == DBTableID.SlayerTask.Row.SLAYER_TARGET_METALDRAGONS) metal = e;
		}
		assertEquals(30, metal.getMinAmount());
		assertEquals(40, metal.getMaxAmount());
		// The largest regular master's average: Duradel's 130 to 200 abyssal demons, not Mortimer's
		assertEquals(165, DATA.typicalAmount(DBTableID.SlayerTask.Row.SLAYER_TARGET_ABYSSALDEMONS), 0.001);
		// Only Mortimer assigns venators
		assertEquals(150, DATA.typicalAmount(DBTableID.SlayerTask.Row.SLAYER_TARGET_VENATORS), 0.001);
		// The player picks how many bosses
		assertEquals(0, DATA.typicalAmount(DBTableID.SlayerTask.Row.SLAYER_TARGET_BOSS), 0.001);
	}

	@Test
	public void konarPoolMatchesTheCache()
	{
		final List<SlayerGameData.PoolEntry> pool = DATA.pool(SlayerMaster.KONAR);
		assertEquals(39, pool.size());
		int total = 0;
		SlayerGameData.PoolEntry metal = null;
		SlayerGameData.PoolEntry boss = null;
		for (SlayerGameData.PoolEntry e : pool)
		{
			total += e.getWeight();
			if (e.getTaskRow() == DBTableID.SlayerTask.Row.SLAYER_TARGET_METALDRAGONS) metal = e;
			if (e.getTaskRow() == DBTableID.SlayerTask.Row.SLAYER_TARGET_BOSS) boss = e;
		}
		assertEquals(250, total);
		assertEquals(15, metal.getWeight());
		assertEquals(-1, metal.getUnlockRow());
		assertEquals(Row.SLAYER_REWARDS_UNLOCK_BOSSES, boss.getUnlockRow());
		assertTrue(DATA.gatesPool(Row.SLAYER_REWARDS_UNLOCK_BOSSES));
		assertTrue(DATA.gatesPool(Row.SLAYER_REWARDS_UNLOCK_WILDERNESS_NEWTASKS));
		// Weighting and extension rewards do not gate any pool row
		assertFalse(DATA.gatesPool(Row.SLAYER_REWARDS_WEIGHTED_FROST_DRAGONS));
		assertFalse(DATA.gatesPool(Row.SLAYER_REWARDS_LONGER_METALDRAGONS));
	}

	@Test
	public void blockListsStoreTheTaskIdAndHaveRoomForIt()
	{
		int maxId = 0;
		for (int row : FIXTURE.table(DBTableID.SlayerTask.ID).rows)
		{
			maxId = Math.max(maxId, FIXTURE.intValue(row, "COL_ID"));
		}
		for (SlayerMaster m : SlayerMaster.values())
		{
			// Mortimer has two slots and no diary slot (wiki Slayer Rewards)
			assertEquals(m.name(), m == SlayerMaster.MORTIMER ? 2 : 7, m.getBlockVarbits().length);
			assertEquals(m.name(), m != SlayerMaster.MORTIMER, m.isDiarySlot());
			for (int id : m.getBlockVarbits())
			{
				final SlayerCacheFixture.Varbit v = FIXTURE.varbit(id);
				assertTrue(m + " slot varbit " + id, v != null && maxId < 1 << (v.msb - v.lsb + 1));
			}
		}
		assertEquals(DBTableID.SlayerTask.Row.SLAYER_TARGET_GARGOYLES, DATA.taskRowForId(46));
		assertEquals("Gargoyles", DATA.taskName(DATA.taskRowForId(46)));
		assertEquals(DBTableID.SlayerTask.Row.SLAYER_TARGET_BOSS, DATA.taskRowForId(98));
		// Boss sub-tasks carry no id
		assertEquals(-1, DATA.taskRowForId(-1));
	}

	@Test
	public void slayerLevelComesFromTheStatTuples()
	{
		assertEquals(75, DATA.slayerLevel(DBTableID.SlayerTask.Row.SLAYER_TARGET_GARGOYLES));
		// (87 Slayer, 50 Magic) and (78 Slayer, 73 Sailing): only the Slayer tuple counts
		assertEquals(87, DATA.slayerLevel(DBTableID.SlayerTask.Row.SLAYER_TARGET_CAVEKRAKEN));
		assertEquals(78, DATA.slayerLevel(DBTableID.SlayerTask.Row.SLAYER_TARGET_AQUANITES));
		assertEquals(0, DATA.slayerLevel(DBTableID.SlayerTask.Row.SLAYER_TARGET_FROST_DRAGONS));
		assertEquals(0, DATA.slayerLevel(DBTableID.SlayerTask.Row.SLAYER_TARGET_BLACKDRAGONS));
		assertEquals(Arrays.asList(DBTableID.SlayerTask.Row.SLAYER_TARGET_GARGOYLES), DATA.taskRowsNamed("gargoyles"));
	}

	@Test
	public void extensionsComeFromTheTaskRows()
	{
		// Augment My Abbies: abyssal demons become 200 to 250 at every master (SlayerTask COL_EXTENSION_MIN_MAX)
		final SlayerGameData.Extension abbies = DATA.extensionOf(DBTableID.SlayerTask.Row.SLAYER_TARGET_ABYSSALDEMONS);
		assertEquals(Row.SLAYER_REWARDS_LONGER_ABYSSALDEMONS, abbies.getUnlockRow());
		assertEquals(200, abbies.extendedMin(120));
		assertEquals(250, abbies.extendedMax(170));
		// Gryphon and on adds 80 to the master's own amount (COL_EXTENSION_ADDITIVE)
		final SlayerGameData.Extension gryphons = DATA.extensionOf(DBTableID.SlayerTask.Row.SLAYER_TARGET_GRYPHONS);
		assertTrue(gryphons.isAdditive());
		assertEquals(150, gryphons.extendedMin(70));
		assertEquals(210, gryphons.extendedMax(130));
		// Pedal to the Metals lengthens the metal dragon task to 150 to 200, and the old per-metal rows too
		boolean metal = false;
		for (SlayerGameData.Extension e : DATA.extensions(Row.SLAYER_REWARDS_LONGER_METALDRAGONS))
		{
			if (e.getTaskRow() == DBTableID.SlayerTask.Row.SLAYER_TARGET_METALDRAGONS)
			{
				metal = true;
				assertEquals(150, e.getMin());
				assertEquals(200, e.getMax());
			}
		}
		assertTrue(metal);
		assertNull(DATA.extensionOf(DBTableID.SlayerTask.Row.SLAYER_TARGET_KURASK));
		assertTrue(DATA.extensions(Row.SLAYER_REWARDS_UNLOCK_SUPERIORMOBS).isEmpty());
	}

	@Test
	public void weightingsAndBlockersComeFromTheTaskRows()
	{
		// Chance of Heavy Frost raises frost dragons from 5 to 8, Stop the Wyvern keeps fossil wyverns out
		final List<SlayerGameData.Weighting> frost = DATA.weightings(Row.SLAYER_REWARDS_WEIGHTED_FROST_DRAGONS);
		assertEquals(1, frost.size());
		assertEquals(DBTableID.SlayerTask.Row.SLAYER_TARGET_FROST_DRAGONS, frost.get(0).getTaskRow());
		assertEquals(8, frost.get(0).getWeight());
		assertEquals(Arrays.asList(DBTableID.SlayerTask.Row.SLAYER_TARGET_FOSSILWYVERNS),
				DATA.blockedBy(Row.SLAYER_REWARDS_UNLOCK_FOSSILWYVERNBLOCK));
	}

	@Test
	public void shopTabsComeFromTheListPosition()
	{
		assertEquals(SlayerGameData.TAB_UNLOCK, DATA.unlock(Row.SLAYER_REWARDS_UNLOCK_SUPERIORMOBS).getTab());
		assertEquals(SlayerGameData.TAB_EXTEND, DATA.unlock(Row.SLAYER_REWARDS_LONGER_ABYSSALDEMONS).getTab());
		assertEquals(SlayerGameData.TAB_COSMETIC, DATA.unlock(Row.SLAYER_REWARDS_UNLOCK_HELM_BLACK).getTab());
		assertEquals(-1, DATA.unlock(Row.SLAYER_REWARDS_LONGER_RUNEDRAGONS).getTab());
	}

	@Test
	public void theBuyTabComesFromItsEnums()
	{
		// Client script 322 reads enum 840 for each slot's item and enum 842 for its cost
		final Map<Integer, Integer> items = DATA.shopItems();
		assertEquals(Arrays.asList(ItemID.SLAYER_RING_8, ItemID.SLAYER_BROAD_BOLT, ItemID.SLAYER_BROAD_ARROWS, ItemID.SLAYER_HERB_SACK,
				ItemID.BH_RUNE_POUCH, ItemID.LOOTING_BAG), new java.util.ArrayList<>(items.keySet()));
		assertEquals(750, (int) items.get(ItemID.SLAYER_HERB_SACK));
		assertEquals(750, (int) items.get(ItemID.BH_RUNE_POUCH));
		assertEquals(10, (int) items.get(ItemID.LOOTING_BAG));
		assertEquals(75, (int) items.get(ItemID.SLAYER_RING_8));
		// Without the enums the tab is just empty
		final SlayerGameData noEnums = SlayerGameData.load(new SlayerGameData.Db()
		{
			@Override
			public List<Integer> rows(int table)
			{
				return FIXTURE.rows(table);
			}

			@Override
			public Object[] field(int row, int column, int tupleIndex)
			{
				return FIXTURE.field(row, column, tupleIndex);
			}
		});
		assertNotNull(noEnums);
		assertTrue(noEnums.shopItems().isEmpty());
	}

	@Test
	public void unreadableTablesFallBackInsteadOfThrowing()
	{
		assertNull(SlayerGameData.load(new SlayerGameData.Db()
		{
			@Override
			public List<Integer> rows(int table)
			{
				return FIXTURE.rows(table);
			}

			@Override
			public Object[] field(int row, int column, int tupleIndex)
			{
				throw new IllegalArgumentException("tuple index too large");
			}
		}));
	}

	private static Object staticValue(Field f)
	{
		try
		{
			return f.get(null);
		}
		catch (IllegalAccessException e)
		{
			throw new AssertionError(e);
		}
	}
}
