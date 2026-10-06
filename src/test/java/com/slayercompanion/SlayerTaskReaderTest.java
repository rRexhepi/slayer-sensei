package com.slayercompanion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;

/**
 * The task read from the game, against the dump of the live cache. The dossier, the gear tables and the places are keyed
 * by the names RuneLite's Slayer plugin stores, so every name the reader can produce is checked against the names the
 * guard tests accept, and every key those tables hold must be one the reader can produce.
 */
public class SlayerTaskReaderTest
{
	private static final SlayerCacheFixture CACHE = SlayerCacheFixture.get();

	@Test
	public void everyTaskAndBossNameIsOneTheTablesAreKeyedBy() throws Exception
	{
		final Set<String> runeLite = SlayerRequirementsTest.runeLiteTaskNames();
		final Set<String> cache = SlayerRequirementsTest.cacheTaskNames();
		final Set<String> derived = new HashSet<>();
		// Every task id a master's SLAYER_TARGET can hold. Boss rows have the id -1 and are reached through the boss list.
		final Map<Integer, String> byRow = new HashMap<>();
		int tasks = 0;
		for (int row : CACHE.rows(DBTableID.SlayerTask.ID))
		{
			final int id = CACHE.intValue(row, "COL_ID");
			// The "Boss" row's id is the boss task's target, which names its boss instead, as checked below
			if (id <= 0 || id == SlayerTaskReader.BOSS_TASK) continue;
			final String name = SlayerTaskReader.taskName(CACHE, id, 0);
			assertNotNull("task id " + id, name);
			// The name is the row's own, unchanged, as RuneLite's Slayer plugin stores it
			assertEquals(CACHE.column(row, "COL_NAME_UPPERCASE")[0], name);
			assertTrue(name, cache.contains(SlayerRequirements.normalize(name)));
			derived.add(SlayerRequirements.normalize(name));
			byRow.put(row, name);
			tasks++;
		}
		// Every boss SLAYER_TARGET_BOSSID can name, through the boss list
		int bosses = 0;
		for (int row : CACHE.rows(DBTableID.SlayerTaskSublist.ID))
		{
			final int bossId = CACHE.intValue(row, "COL_TASK_SUBTABLE_ID");
			final String name = SlayerTaskReader.taskName(CACHE, SlayerTaskReader.BOSS_TASK, bossId);
			assertNotNull("boss " + bossId, name);
			assertTrue(name + " is not a task name in RuneLite's Slayer Task enum", runeLite.contains(SlayerRequirements.normalize(name)));
			assertTrue(name, cache.contains(SlayerRequirements.normalize(name)));
			derived.add(SlayerRequirements.normalize(name));
			bosses++;
		}
		assertTrue(tasks > 100);
		assertEquals("every boss the game lists", 33, bosses);
		// Every task a master assigns comes out as a name RuneLite's Slayer plugin knows. The boss task goes by its boss.
		for (int entry : CACHE.rows(DBTableID.SlayerMasterTask.ID))
		{
			final int taskRow = CACHE.intValue(entry, "COL_TASK");
			if (CACHE.intValue(taskRow, "COL_ID") == SlayerTaskReader.BOSS_TASK) continue;
			final String name = byRow.get(taskRow);
			assertNotNull("pool row " + taskRow, name);
			assertTrue(name + " is not a task name in RuneLite's Slayer Task enum", runeLite.contains(SlayerRequirements.normalize(name)));
		}
		// And every key the gear tables hold is a name the reader can produce, so none silently stops matching
		final Set<String> keys = new LinkedHashSet<>(SlayerRequirements.taskKeys());
		keys.addAll(SlayerRequirements.recommendedKeys());
		keys.addAll(SlayerRequirements.undeadKeys());
		for (String key : keys)
		{
			assertTrue("'" + key + "' is no name the game gives a task", derived.contains(key));
		}
	}

	@Test
	public void namesComeOutInTheCachesOwnCasing()
	{
		assertEquals("Abyssal Demons", SlayerTaskReader.taskName(CACHE, 42, 0));
		assertEquals("Mutated Zygomites", SlayerTaskReader.taskName(CACHE, 74, 0));
		assertEquals("Metal Dragons", SlayerTaskReader.taskName(CACHE, 127, 0));
		// Boss tasks: the target says boss, the boss id says which
		assertEquals("Cerberus", SlayerTaskReader.taskName(CACHE, SlayerTaskReader.BOSS_TASK, 20));
		assertEquals("The Alchemical Hydra", SlayerTaskReader.taskName(CACHE, SlayerTaskReader.BOSS_TASK, 24));
		assertEquals("Barrows brothers", SlayerTaskReader.taskName(CACHE, SlayerTaskReader.BOSS_TASK, 17));
		// Ids the tables do not hold give no task
		assertNull(SlayerTaskReader.taskName(CACHE, 9_999, 0));
		assertNull(SlayerTaskReader.taskName(CACHE, SlayerTaskReader.BOSS_TASK, 999));
		assertNull(SlayerTaskReader.taskName(CACHE, 0, 0));
	}

	@Test
	public void theTaskComesWithItsKillsAndTheAmountAssigned()
	{
		final SlayerTaskReader reader = new SlayerTaskReader();
		final SlayerTaskReader.Task task = reader.read(varps(42, 112, 150), varbits(0, 0, 0, 0), CACHE);
		assertEquals(new SlayerTaskReader.Task("Abyssal Demons", 112, 150), task);
		assertEquals("Cerberus", reader.read(varps(SlayerTaskReader.BOSS_TASK, 30, 30), varbits(20, 0, 0, 0), CACHE).getName());
		// No kills left is no task, as RuneLite's Slayer plugin reads it, and nothing shows while the tables have no row
		assertNull(reader.read(varps(42, 0, 150), varbits(0, 0, 0, 0), CACHE));
		assertNull(reader.read(varps(9_999, 40, 40), varbits(0, 0, 0, 0), CACHE));
	}

	/** The SlayerModifiers table: modifier 2 is "Slayer Quantity Change", which moves the amount up or down. */
	@Test
	public void aQuantityModifierMovesTheAmountAssigned()
	{
		final SlayerTaskReader reader = new SlayerTaskReader();
		assertEquals(170, reader.read(varps(42, 112, 150), varbits(0, SlayerTaskReader.QUANTITY_MODIFIER, 20, 0), CACHE)
			.getInitialAmount());
		assertEquals(130, reader.read(varps(42, 112, 150), varbits(0, SlayerTaskReader.QUANTITY_MODIFIER, 20, 1), CACHE)
			.getInitialAmount());
		// Any other modifier leaves it alone
		assertEquals(150, reader.read(varps(42, 112, 150), varbits(0, 1, 20, 0), CACHE).getInitialAmount());
	}

	/**
	 * The reader is asked every tick, and RuneLite's Slayer plugin reads the tables only when one of its varps changes. So
	 * the name is kept for its SLAYER_TARGET and boss id, and the tables are read again only when one of them changes. A
	 * lookup that finds nothing or throws is never kept, and the end of a session forgets the name.
	 */
	@Test
	public void theTablesAreReadOnlyWhenTheTargetOrTheBossChanges()
	{
		final CountingDb db = new CountingDb();
		final SlayerTaskReader reader = new SlayerTaskReader();
		assertEquals("Abyssal Demons", reader.read(varps(42, 112, 150), varbits(0, 0, 0, 0), db).getName());
		assertTrue(db.reads > 0);
		// More ticks on the task, kills going down and a quantity modifier set, read no table
		int reads = db.reads;
		assertEquals("Abyssal Demons", reader.read(varps(42, 111, 150), varbits(0, 0, 0, 0), db).getName());
		assertEquals(new SlayerTaskReader.Task("Abyssal Demons", 110, 170),
			reader.read(varps(42, 110, 150), varbits(0, SlayerTaskReader.QUANTITY_MODIFIER, 20, 0), db));
		assertEquals(reads, db.reads);
		// No kills left reads no table either, and the next task on the same target still finds its name kept
		assertNull(reader.read(varps(42, 0, 150), varbits(0, 0, 0, 0), db));
		assertEquals("Abyssal Demons", reader.read(varps(42, 150, 150), varbits(0, 0, 0, 0), db).getName());
		assertEquals(reads, db.reads);

		// The next task is usually another ordinary one, so the boss id stays 0 and only the target changes
		assertEquals("Mutated Zygomites", reader.read(varps(74, 80, 80), varbits(0, 0, 0, 0), db).getName());
		assertTrue(db.reads > reads);
		reads = db.reads;
		assertEquals("Abyssal Demons", reader.read(varps(42, 150, 150), varbits(0, 0, 0, 0), db).getName());
		assertTrue(db.reads > reads);
		reads = db.reads;

		// A new target reads them again, and so does another boss on the boss target
		assertEquals("Cerberus", reader.read(varps(SlayerTaskReader.BOSS_TASK, 30, 30), varbits(20, 0, 0, 0), db).getName());
		assertTrue(db.reads > reads);
		reads = db.reads;
		assertEquals("The Alchemical Hydra", reader.read(varps(SlayerTaskReader.BOSS_TASK, 30, 30), varbits(24, 0, 0, 0), db).getName());
		assertTrue(db.reads > reads);
		reads = db.reads;
		assertEquals("The Alchemical Hydra", reader.read(varps(SlayerTaskReader.BOSS_TASK, 29, 30), varbits(24, 0, 0, 0), db).getName());
		assertEquals(reads, db.reads);

		// A target the tables do not hold is not kept. It never shows the last name, and every tick looks again
		assertNull(reader.read(varps(9_999, 40, 40), varbits(0, 0, 0, 0), db));
		reads = db.reads;
		assertNull(reader.read(varps(9_999, 40, 40), varbits(0, 0, 0, 0), db));
		assertTrue(db.reads > reads);

		// Nor is a lookup that throws. It goes to the caller, and the next tick looks again and finds the name
		db.failing = true;
		try
		{
			reader.read(varps(42, 112, 150), varbits(0, 0, 0, 0), db);
			fail("a failed table read is the caller's to handle");
		}
		catch (IllegalStateException expected)
		{
			// The plugin keeps what it shows and reads again next tick
		}
		db.failing = false;
		reads = db.reads;
		assertEquals("Abyssal Demons", reader.read(varps(42, 112, 150), varbits(0, 0, 0, 0), db).getName());
		assertTrue(db.reads > reads);
		reads = db.reads;
		assertEquals("Abyssal Demons", reader.read(varps(42, 111, 150), varbits(0, 0, 0, 0), db).getName());
		assertEquals(reads, db.reads);

		// The session ends, so the name kept is forgotten
		reader.clear();
		assertEquals("Abyssal Demons", reader.read(varps(42, 111, 150), varbits(0, 0, 0, 0), db).getName());
		assertTrue(db.reads > reads);
	}

	/** The fixture's tables, counting every read and throwing while {@link #failing}, as a client read might. */
	private static final class CountingDb implements SlayerGameData.Db
	{
		int reads;
		boolean failing;

		@Override
		public List<Integer> rows(int table)
		{
			read();
			return CACHE.rows(table);
		}

		@Override
		public Object[] field(int row, int column, int tupleIndex)
		{
			read();
			return CACHE.field(row, column, tupleIndex);
		}

		@Override
		public List<Integer> rowsByValue(int table, int column, int tupleIndex, Object value)
		{
			read();
			return CACHE.rowsByValue(table, column, tupleIndex, value);
		}

		private void read()
		{
			reads++;
			if (failing) throw new IllegalStateException("table not readable");
		}
	}

	private static IntUnaryOperator varps(int target, int left, int assigned)
	{
		final Map<Integer, Integer> values = new HashMap<>();
		values.put(VarPlayerID.SLAYER_TARGET, target);
		values.put(VarPlayerID.SLAYER_COUNT, left);
		values.put(VarPlayerID.SLAYER_COUNT_ORIGINAL, assigned);
		return varp -> values.getOrDefault(varp, 0);
	}

	private static IntUnaryOperator varbits(int bossId, int modifier, int change, int negative)
	{
		final Map<Integer, Integer> values = new HashMap<>();
		values.put(VarbitID.SLAYER_TARGET_BOSSID, bossId);
		values.put(VarbitID.SLAYER_MODIFIER_ID, modifier);
		values.put(VarbitID.SLAYER_MODIFIER_VALUE, change);
		values.put(VarbitID.SLAYER_MODIFIER_NEGATIVE, negative);
		return varbit -> values.getOrDefault(varbit, 0);
	}
}
