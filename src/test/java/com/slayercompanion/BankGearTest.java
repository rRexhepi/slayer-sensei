package com.slayercompanion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Item;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

/** What the bank memory keeps of a bank, how it is stored, and when it reads the bank, for one character and the next. */
public class BankGearTest
{
	/** Bones and sharks, a rock hammer, 120 ice coolers, an Arclight, and a stack with no nose pegs left in it. */
	private static final Item[] BANK = {
		new Item(ItemID.BONES, 2_000), new Item(ItemID.SLAYER_ROCK_HAMMER, 1), new Item(ItemID.SHARK, 300),
		new Item(ItemID.SLAYER_ICY_WATER, 120), new Item(ItemID.ARCLIGHT, 1), new Item(ItemID.SLAYER_NOSEPEG, 0),
	};

	@Test
	public void onlyTheGearChecksItemsAreKept()
	{
		final BankGear gear = BankGear.of(BANK);
		assertEquals(1, gear.quantity(ItemID.SLAYER_ROCK_HAMMER));
		assertEquals(120, gear.quantity(ItemID.SLAYER_ICY_WATER));
		assertEquals(1, gear.quantity(ItemID.ARCLIGHT));
		// Bones and food are not what the check looks for, and an empty stack holds nothing
		assertEquals(0, gear.quantity(ItemID.BONES));
		assertEquals(0, gear.quantity(ItemID.SHARK));
		assertEquals(0, gear.quantity(ItemID.SLAYER_NOSEPEG));
		// Rock hammer 4162, ice cooler 6696 and Arclight 19675, lowest id first
		assertEquals("4162:1,6696:120,19675:1", gear.encode());
		assertEquals(1, gear.count(SlayerRequirements.ANY_ROCK_HAMMER));
		assertEquals(1, gear.count(SlayerRequirements.DEMONBANE));
	}

	/** The ids kept are exactly those the item groups list, every group's and no others. */
	@Test
	public void theKeptIdsAreTheItemGroupsIds() throws Exception
	{
		final Set<Integer> listed = new HashSet<>();
		int groups = 0;
		for (Field field : SlayerRequirements.class.getDeclaredFields())
		{
			if (!Modifier.isStatic(field.getModifiers()) || field.getType() != SlayerRequirements.ItemGroup.class) continue;
			field.setAccessible(true);
			listed.addAll(((SlayerRequirements.ItemGroup) field.get(null)).ids());
			groups++;
		}
		assertTrue(groups > 30);
		assertEquals(listed, SlayerRequirements.gearIds());
	}

	@Test
	public void theStoredFormIsIdAndQuantityPairs()
	{
		assertNull("nothing stored, so the bank has not been seen", BankGear.decode(null));
		final BankGear gear = BankGear.of(BANK);
		assertEquals(gear, BankGear.decode(gear.encode()));
		// A bank holding none of the items has still been seen
		final BankGear none = BankGear.of(new Item[]{new Item(ItemID.BONES, 5)});
		assertEquals("none", none.encode());
		assertEquals(none, BankGear.decode("none"));
		assertEquals(0, BankGear.decode("none").count(SlayerRequirements.ROCK_HAMMERS));
		// Parts it cannot read, and items no group lists, are skipped and the rest kept
		assertEquals(BankGear.of(new Item[]{new Item(ItemID.SLAYER_ROCK_HAMMER, 1)}),
			BankGear.decode("junk, " + ItemID.SLAYER_ROCK_HAMMER + ":1 ," + ItemID.BONES + ":9,7:x,:3"));
	}

	// ---- the memory of the character logged in ----

	/** A character's stored text by RS profile key, as the RS profile config keeps it. */
	private static final class Config
	{
		final Map<String, String> stored = new HashMap<>();
		String profile = "profile-a";
		int saves;

		BankGear.Memory memory()
		{
			return new BankGear.Memory(() -> profile, () -> stored.get(profile), value ->
			{
				saves++;
				stored.put(profile, value);
			});
		}
	}

	@Test
	public void theBankIsReadOnlyAfterItChanged()
	{
		final Config config = new Config();
		final BankGear.Memory memory = config.memory();
		assertFalse(memory.waiting());
		assertFalse(memory.read(BANK));
		assertNull(memory.get());
		assertEquals(0, config.saves);
		memory.bankChanged();
		memory.bankChanged();
		assertTrue(memory.waiting());
		assertTrue(memory.read(BANK));
		assertFalse("read once, however often it changed", memory.waiting());
		assertFalse(memory.read(BANK));
		assertEquals(1, config.saves);
	}

	@Test
	public void eachCharacterHasItsOwnBankLoadedWhenTheCharacterChanges()
	{
		final Config config = new Config();
		final BankGear.Memory memory = config.memory();
		memory.bankChanged();
		memory.read(BANK);
		assertEquals(BankGear.of(BANK).encode(), config.stored.get("profile-a"));
		config.profile = "profile-b";
		assertNull("the next character's bank has not been seen", memory.get());
		config.profile = "profile-a";
		assertEquals(BankGear.of(BANK), memory.get());
		// Another session reads what was stored
		assertEquals(BankGear.of(BANK), config.memory().get());
	}

	@Test
	public void theBankIsReplacedEachTimeAndStoredOnlyWhenItHoldsSomethingNew()
	{
		final Config config = new Config();
		final BankGear.Memory memory = config.memory();
		memory.bankChanged();
		memory.read(BANK);
		// The hammer was taken out. What the bank holds replaces what was remembered, and is not added to it
		final Item[] after = {new Item(ItemID.SLAYER_ICY_WATER, 120), new Item(ItemID.ARCLIGHT, 1), new Item(ItemID.SHARK, 280)};
		memory.bankChanged();
		assertTrue(memory.read(after));
		assertEquals(0, memory.get().quantity(ItemID.SLAYER_ROCK_HAMMER));
		assertEquals(2, config.saves);
		// Only food changed, which is not kept, so nothing is written
		memory.bankChanged();
		assertFalse(memory.read(new Item[]{new Item(ItemID.SLAYER_ICY_WATER, 120), new Item(ItemID.ARCLIGHT, 1)}));
		assertEquals(2, config.saves);
		// A bank with none of the items is seen all the same
		memory.bankChanged();
		assertTrue(memory.read(new Item[]{new Item(ItemID.SHARK, 280)}));
		assertEquals("none", config.stored.get("profile-a"));
	}

	@Test
	public void withoutACharacterTheChangeWaits()
	{
		final Config config = new Config();
		final BankGear.Memory memory = config.memory();
		config.profile = null;
		memory.bankChanged();
		assertFalse(memory.read(BANK));
		assertNull(memory.get());
		assertTrue(memory.waiting());
		assertEquals(0, config.saves);
	}
}
