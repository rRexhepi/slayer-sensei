package com.slayercompanion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

/** The requirement table itself: its task keys, item lists and unlock varbits against RuneLite and the cache. */
public class SlayerRequirementsTest
{
	/**
	 * The zygomite entry was keyed "zygomites" while the task is "Mutated zygomites", so it never matched. Every
	 * key has to be a name from RuneLite's Task enum, which is what the core Slayer plugin stores.
	 */
	@Test
	public void everyRequirementIsKeyedByARuneLiteTaskName() throws Exception
	{
		final Set<String> names = runeLiteTaskNames();
		assertTrue(names.size() > 100);
		for (String key : SlayerRequirements.taskKeys())
		{
			assertTrue("'" + key + "' is not a task name in RuneLite's Slayer Task enum", names.contains(key));
		}
	}

	/** The core plugin reads the name from the cache's SlayerTask table, in that table's own casing. */
	@Test
	public void everyRequirementIsKeyedByATaskNameFromTheGameCache()
	{
		final Set<String> names = cacheTaskNames();
		for (String key : SlayerRequirements.taskKeys())
		{
			assertTrue("'" + key + "' is not a SlayerTask name in the game cache", names.contains(key));
		}
	}

	/** The recommended tier and the undead list are keyed the same way, so a misspelt task never gets its items. */
	@Test
	public void everyRecommendationIsKeyedByARuneLiteAndCacheTaskName() throws Exception
	{
		final Set<String> runeLite = runeLiteTaskNames();
		final Set<String> cache = cacheTaskNames();
		final Set<String> keys = new HashSet<>(SlayerRequirements.recommendedKeys());
		keys.addAll(SlayerRequirements.undeadKeys());
		assertTrue(keys.size() > 30);
		for (String key : keys)
		{
			assertTrue("'" + key + "' is not a task name in RuneLite's Slayer Task enum", runeLite.contains(key));
			assertTrue("'" + key + "' is not a SlayerTask name in the game cache", cache.contains(key));
		}
	}

	/** RuneLite's Slayer Task enum names, normalised. */
	static Set<String> runeLiteTaskNames() throws Exception
	{
		final Class<?> taskClass = Class.forName("net.runelite.client.plugins.slayer.Task");
		final Method values = taskClass.getDeclaredMethod("values");
		final Method getName = taskClass.getDeclaredMethod("getName");
		values.setAccessible(true);
		getName.setAccessible(true);
		final Set<String> names = new HashSet<>();
		for (Object task : (Object[]) values.invoke(null))
		{
			names.add(SlayerRequirements.normalize((String) getName.invoke(task)));
		}
		return names;
	}

	/** The game cache's SlayerTask names, normalised. */
	static Set<String> cacheTaskNames()
	{
		final SlayerCacheFixture cache = SlayerCacheFixture.get();
		final Set<String> names = new HashSet<>();
		for (int row : cache.rows(DBTableID.SlayerTask.ID))
		{
			for (Object name : cache.field(row, DBTableID.SlayerTask.COL_NAME_UPPERCASE, 0))
			{
				names.add(SlayerRequirements.normalize((String) name));
			}
		}
		return names;
	}

	/**
	 * Every id in both tiers is an item RuneLite names, so a raw number such as a noted copy cannot slip in. The groups
	 * are the class's constants, which are all the tables use.
	 */
	@Test
	public void everyItemIdResolves() throws Exception
	{
		final Set<Integer> known = new HashSet<>();
		for (Field field : ItemID.class.getFields())
		{
			if (field.getType() == int.class) known.add(field.getInt(null));
		}
		int groups = 0;
		for (Field field : SlayerRequirements.class.getDeclaredFields())
		{
			if (!Modifier.isStatic(field.getModifiers())) continue;
			field.setAccessible(true);
			final Set<Integer> ids = new HashSet<>();
			if (field.getType() == SlayerRequirements.ItemGroup.class)
			{
				groups++;
				ids.addAll(((SlayerRequirements.ItemGroup) field.get(null)).ids());
				assertFalse(field.getName() + " is empty", ids.isEmpty());
			}
			else if (field.getType() == int[].class)
			{
				for (int id : (int[]) field.get(null)) ids.add(id);
			}
			for (int id : ids)
			{
				assertTrue(field.getName() + " lists " + id + ", which is not an item in RuneLite's ItemID", known.contains(id));
			}
		}
		assertTrue(groups > 30);
		for (String key : SlayerRequirements.recommendedKeys())
		{
			for (SlayerRequirements.Recommendation recommendation : SlayerRequirements.recommendedFor(key))
			{
				assertFalse(key + " recommends an item list without a label", recommendation.getItems().getLabel().isEmpty());
				assertFalse(key + " recommends an empty item list", recommendation.getItems().ids().isEmpty());
			}
		}
	}

	/** The item classes the recommended tier asks for on a task, in order. */
	static List<SlayerRequirements.ItemGroup> groups(String task)
	{
		final List<SlayerRequirements.ItemGroup> groups = new ArrayList<>();
		for (SlayerRequirements.Recommendation recommendation : SlayerRequirements.recommendedFor(task))
		{
			groups.add(recommendation.getItems());
		}
		return groups;
	}

	/** Wiki: Light sources. The open flames and the covered lights split the light sources the darkness check takes. */
	@Test
	public void openFlamesAndCoveredLightsSplitTheLightSources()
	{
		final Set<Integer> all = new HashSet<>(SlayerRequirements.OPEN_FLAMES.ids());
		all.addAll(SlayerRequirements.COVERED_LIGHT.ids());
		assertEquals(SlayerRequirements.LIGHT_SOURCE.ids(), all);
		assertTrue(java.util.Collections.disjoint(SlayerRequirements.OPEN_FLAMES.ids(), SlayerRequirements.COVERED_LIGHT.ids()));
		assertEquals(4, SlayerRequirements.OPEN_FLAMES.ids().size());
		assertTrue(SlayerRequirements.COVERED_LIGHT.matches(ItemID.CANDLE_LANTERN_LIT, "Candle lantern"));
		assertTrue(SlayerRequirements.COVERED_LIGHT.matches(ItemID.WINT_TORCH, "Bruma torch"));
		assertFalse(SlayerRequirements.COVERED_LIGHT.matches(ItemID.OIL_LAMP_LIT, "Oil lamp"));
		// Only the three swamp cave tasks ask for it, and only while a flame is the light
		for (String task : new String[]{"Cave bugs", "Cave slimes", "Wall beasts"})
		{
			final SlayerRequirements.Recommendation covered = SlayerRequirements.recommendedFor(task).get(0);
			assertSame(SlayerRequirements.COVERED_LIGHT, covered.getItems());
			assertSame(SlayerRequirements.OPEN_FLAMES, covered.getOnlyWith());
		}
	}

	/** Fails when a RuneLite update adds a black mask or salve amulet the lists do not have yet. */
	@Test
	public void everyBlackMaskAndSalveAmuletInRuneLiteIsListed() throws Exception
	{
		final Set<Integer> masks = new HashSet<>();
		for (int id : SlayerRequirements.BLACK_MASK_IDS)
		{
			assertTrue("listed twice: " + id, masks.add(id));
		}
		assertEquals(masks, SlayerRequirements.BLACK_MASK.ids());
		assertEquals(masks, itemsNamed("BLACK_MASK"));
		// The plain salve amulet and its (e) keep their quest item names in the gameval
		final Set<Integer> salves = itemsNamed("SALVE_AMULET");
		salves.add(ItemID.CRYSTALSHARD_NECKLACE);
		salves.add(ItemID.LOTR_CRYSTALSHARD_NECKLACE_UPGRADE);
		assertEquals(salves, SlayerRequirements.SALVE_AMULETS.ids());
	}

	/** Fails when a RuneLite update adds a variant of a recommended weapon or potion. Trophies, keys and copies for minigames are left out. */
	@Test
	public void everyVariantOfTheRecommendedItemsInRuneLiteIsListed() throws Exception
	{
		assertEquals(itemsNamed("KERIS"), SlayerRequirements.KERIS.ids());
		assertEquals(itemsNamed("DRAGONHUNTER"), SlayerRequirements.DRAGONBANE.ids());
		final Set<Integer> demonbane = new HashSet<>();
		for (String name : new String[]{"SILVERLIGHT", "DARKLIGHT", "ARCLIGHT", "EMBERLIGHT", "BONE_CLAWS", "SCORCHING_BOW", "PURGING_STAFF",
			"HOLY_WATER"})
		{
			demonbane.addAll(itemsNamed(name));
		}
		assertEquals(demonbane, SlayerRequirements.DEMONBANE.ids());
		final Set<Integer> dragonfire = itemsNamed("ANTIDRAGON");
		dragonfire.addAll(itemsNamed("DRAGONFIRE_SHIELD"));
		dragonfire.addAll(itemsNamed("DRAGONFIRE_WARD"));
		dragonfire.addAll(itemsNamed("WYVERN_SHIELD"));
		assertEquals(dragonfire, SlayerRequirements.DRAGONFIRE_PROTECTION.ids());
	}

	/** Ids of the ItemID names that hold {@code part}, without copies for a minigame, trophies, keys, books and parts. */
	private static Set<Integer> itemsNamed(String part) throws Exception
	{
		final Set<Integer> ids = new HashSet<>();
		for (Field field : ItemID.class.getFields())
		{
			final String name = field.getName();
			if (!name.contains(part) || name.matches("(BR|POH_TROPHY|PATTERN|PRIF)_.*|NZONE_ANTIDRAGONSHIELD|.*_(KEY_\\d|STRING)")) continue;
			ids.add(field.getInt(null));
		}
		return ids;
	}

	@Test
	public void theHelmetFollowsTheUnlockTheUndeadAndTheHeadSlot()
	{
		assertEquals(SlayerReward.MALEVOLENT_MASQUERADE.getFallbackVarbit(), SlayerRequirements.HELMET_UNLOCK_VARBIT);
		assertSame(SlayerRequirements.HELMET_OR_MASK, SlayerRequirements.slayerHeadgear("Cows"));
		assertSame(SlayerRequirements.HELMET_OR_MASK, SlayerRequirements.slayerHeadgear("Skeletal wyverns"));
		assertSame(SlayerRequirements.HELMET_MASK_OR_SALVE, SlayerRequirements.slayerHeadgear("Zombies"));
		assertSame(SlayerRequirements.HELMET_MASK_OR_SALVE, SlayerRequirements.slayerHeadgear("Vet'ion"));
		assertSame(SlayerRequirements.SLAYER_HELMET, SlayerRequirements.slayerHeadgear("Dust devils"));
		assertSame(SlayerRequirements.SLAYER_HELMET, SlayerRequirements.slayerHeadgear("Sourhogs"));
		assertSame(SlayerRequirements.HELMET_OR_SALVE, SlayerRequirements.slayerHeadgear("Banshees"));
		assertSame(SlayerRequirements.HELMET_OR_SALVE, SlayerRequirements.slayerHeadgear("Aberrant Spectres"));
		// Ghouls are not undead, whatever they look like
		assertSame(SlayerRequirements.HELMET_OR_MASK, SlayerRequirements.slayerHeadgear("Ghouls"));
		assertTrue(SlayerRequirements.helmetStandsIn("Wall beasts"));
		assertFalse(SlayerRequirements.helmetStandsIn("Cave horrors"));
	}

	@Test
	public void theRecommendedTierStaysOutOfTheMandatoryOne()
	{
		// Demons, dragons and kalphites still need nothing
		for (String task : new String[]{"Abyssal demons", "Black dragons", "Kalphites", "Killerwatts", "Metal dragons"})
		{
			assertNull(task, SlayerRequirements.forTask(task));
			assertFalse(task, SlayerRequirements.recommendedFor(task).isEmpty());
		}
		assertEquals(java.util.Arrays.asList(SlayerRequirements.DRAGONFIRE_PROTECTION, SlayerRequirements.DRAGONBANE),
			groups("The King Black Dragon"));
		assertTrue(SlayerRequirements.recommendedFor("Fire giants").isEmpty());
		assertTrue(SlayerRequirements.recommendedFor(null).isEmpty());
	}

	@Test
	public void zygomitesAreFoundUnderTheNamesRuneLiteUses()
	{
		assertNotNull(SlayerRequirements.forTask("Mutated zygomites"));
		assertNotNull(SlayerRequirements.forTask("Mutated Zygomites"));
		assertNull(SlayerRequirements.forTask("Zygomites"));
	}

	/** Fails when a RuneLite update adds a Slayer helmet id that the list does not have yet. */
	@Test
	public void everySlayerHelmetInRuneLiteIsListed() throws Exception
	{
		final Set<Integer> listed = new HashSet<>();
		for (int id : SlayerRequirements.SLAYER_HELMET_IDS)
		{
			assertTrue("listed twice: " + id, listed.add(id));
		}
		int found = 0;
		for (Field field : ItemID.class.getFields())
		{
			if (field.getName().contains("SLAYER_HELM"))
			{
				found++;
				assertTrue(field.getName() + " is a Slayer helmet the gear check does not list", listed.contains(field.getInt(null)));
			}
		}
		assertEquals(listed.size(), found);
	}

	@Test
	public void slayerHelmetNamesCoverRecoloursAndImbues()
	{
		assertTrue(SlayerRequirements.isSlayerHelmetName("Slayer helmet"));
		assertTrue(SlayerRequirements.isSlayerHelmetName("Twisted slayer helmet (i)"));
		assertTrue(SlayerRequirements.isSlayerHelmetName("Radiant slayer helmet"));
		assertFalse(SlayerRequirements.isSlayerHelmetName("Spiny helmet"));
		assertFalse(SlayerRequirements.isSlayerHelmetName(null));
	}

	@Test
	public void everyNeedNamesItsItems()
	{
		for (String key : SlayerRequirements.taskKeys())
		{
			for (SlayerRequirements.Need need : SlayerRequirements.forTask(key).getNeeds())
			{
				assertFalse(key + " has an empty item list", need.getItems().ids().isEmpty());
				assertFalse(key + " has an item list without a label", need.getItems().getLabel().isEmpty());
			}
		}
		for (int area : new int[]{SlayerRequirements.KARUULM_AREA, SlayerRequirements.WATERFALL_AREA})
		{
			assertFalse(SlayerRequirements.forArea(area).isEmpty());
			for (SlayerRequirements.Need need : SlayerRequirements.forArea(area))
			{
				assertFalse(need.getItems().ids().isEmpty());
			}
		}
	}

	/** SlayerRewardCoverageTest keeps the reward table's varbits equal to the cache, so these follow them. */
	@Test
	public void unlockVarbitsAreTheRewardShopOnes()
	{
		assertEquals(SlayerReward.GARGOYLE_SMASHER.getFallbackVarbit(), SlayerRequirements.GARGOYLE_SMASHER.getVarbit());
		assertEquals(SlayerReward.SLUG_SALTER.getFallbackVarbit(), SlayerRequirements.SLUG_SALTER.getVarbit());
		assertEquals(SlayerReward.REPTILE_FREEZER.getFallbackVarbit(), SlayerRequirements.REPTILE_FREEZER.getVarbit());
		assertEquals(SlayerReward.SHROOM_SPRAYER.getFallbackVarbit(), SlayerRequirements.SHROOM_SPRAYER.getVarbit());
	}

	@Test
	public void lightSourcesAreLitIdsOnly()
	{
		final SlayerRequirements.ItemGroup light = SlayerRequirements.LIGHT_SOURCE;
		assertTrue(light.matches(ItemID.BULLSEYE_LANTERN_LIT, "Bullseye lantern"));
		assertFalse(light.matches(ItemID.BULLSEYE_LANTERN_UNLIT, "Bullseye lantern"));
		assertTrue(light.matches(ItemID.CAVE_GOBLIN_MINING_HELMET_LIT, "Mining helmet"));
		assertFalse(light.matches(ItemID.CAVE_GOBLIN_MINING_HELMET_UNLIT, "Mining helmet"));
		// The bug lantern is not a light source.
		assertFalse(light.matches(ItemID.SLAYER_BUGLAN_ON, "Lit bug lantern"));
	}
}
