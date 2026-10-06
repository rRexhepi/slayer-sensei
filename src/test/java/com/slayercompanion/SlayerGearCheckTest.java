package com.slayercompanion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.slayercompanion.SlayerGearCheck.Held;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.IntUnaryOperator;
import net.runelite.api.Item;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;

/** The "Live gear check" line for the inputs the plugin gathers: items, kills left, Konar's area and varbits. */
public class SlayerGearCheckTest
{
	private static final IntUnaryOperator NO_VARBITS = varbit -> 0;
	/** The Wyrmscraig SLAYER_AREA id, a wyrm place with a safe floor. */
	private static final int WYRMSCRAIG_AREA = 56;
	/** Noted rock hammer. RuneLite's gameval has no name for it, the cache gives it this id. */
	private static final int NOTED_ROCK_HAMMER = 4163;
	/** Noted antifire potion(4), the id after the potion's, likewise without a name. */
	private static final int NOTED_ANTIFIRE = 2453;

	private static final Held ROCK_HAMMER = held(ItemID.SLAYER_ROCK_HAMMER, "Rock hammer");
	private static final Held LANTERN = held(ItemID.BULLSEYE_LANTERN_LIT, "Bullseye lantern");
	private static final String KARUULM_BOOTS = "boots of stone, boots of brimstone or granite boots";
	/** Malevolent Masquerade bought, which adds the helmet to every task. */
	private static final IntUnaryOperator HELMET_UNLOCKED = set(VarbitID.SLAYER_HELM_UNLOCKED);
	/** The second sentence every draconic task gets while no dragonbane weapon is held. */
	private static final String BRING_DRAGONBANE = ". Bring a dragonbane weapon";

	@Test
	public void gargoyleSmasherStillNeedsAHammer()
	{
		final IntUnaryOperator smasher = set(VarbitID.SLAYER_AUTOKILL_GARGOYLES);
		// Buying the unlock does not make the check READY. The hammer still has to be there.
		assertEquals("MISSING: a rock hammer or granite hammer, or 40 rock thrownhammers",
			check("Gargoyles", 40, 0, smasher));
		assertEquals("READY: Rock hammer. Gargoyle Smasher is unlocked, so the finishing blow is automatic",
			check("Gargoyles", 40, 0, smasher, ROCK_HAMMER));
		assertEquals("READY: Rock hammer", check("Gargoyles", 40, 0, NO_VARBITS, ROCK_HAMMER));
	}

	@Test
	public void thrownhammersAreCountedAgainstTheKillsLeft()
	{
		assertEquals("MISSING: 14 more rock thrownhammers (have 26, need 40), or a rock hammer or granite hammer",
			check("Gargoyles", 40, 0, NO_VARBITS, held(ItemID.SLAYER_ROCK_THROWNHAMMER, "Rock thrownhammer", 26)));
		assertEquals("READY: 40 rock thrownhammers (need 40)",
			check("Gargoyles", 40, 0, NO_VARBITS, held(ItemID.SLAYER_ROCK_THROWNHAMMER, "Rock thrownhammer", 40)));
		assertEquals("READY: Granite hammer", check("Gargoyles", 40, 0, NO_VARBITS, held(ItemID.GRANITE_HAMMER, "Granite hammer")));
	}

	@Test
	public void slugSalterStillNeedsSalt()
	{
		final IntUnaryOperator salter = set(VarbitID.SLAYER_AUTOKILL_ROCKSLUGS);
		assertEquals("MISSING: 40 bags of salt, or a brine sabre", check("Rockslugs", 40, 0, salter));
		assertEquals("MISSING: 14 more bags of salt (have 26, need 40), or a brine sabre",
			check("Rockslugs", 40, 0, salter, held(ItemID.SLAYER_BAG_OF_SALT, "Bag of salt", 26)));
		assertEquals("READY: 52 bags of salt (need 40). Slug Salter is unlocked, so the finishing blow is automatic",
			check("Rockslugs", 40, 0, salter, held(ItemID.SLAYER_BAG_OF_SALT, "Bag of salt", 52)));
		// The sabre finishes them without salt, so the unlock does not come into it.
		assertEquals("READY: Brine sabre", check("Rockslugs", 40, 0, salter, held(ItemID.OLAF2_BRINE_SABRE, "Brine sabre")));
	}

	@Test
	public void reptileFreezerStillNeedsIceCoolers()
	{
		final IntUnaryOperator freezer = set(VarbitID.SLAYER_AUTOKILL_DESERTLIZARDS);
		// Sulphur and grimy lizards need no coolers, so the line says which lizards it means.
		assertEquals("MISSING: 14 more ice coolers for desert lizards (have 26, need 40)",
			check("Lizards", 40, 0, freezer, held(ItemID.SLAYER_ICY_WATER, "Ice cooler", 26)));
		assertEquals("READY: 40 ice coolers (need 40). Reptile Freezer is unlocked, so the finishing blow is automatic",
			check("Lizards", 40, 0, freezer, held(ItemID.SLAYER_ICY_WATER, "Ice cooler", 40)));
		assertEquals("MISSING: 1 more ice cooler for desert lizards (have 39, need 40)",
			check("Lizards", 40, 0, NO_VARBITS, held(ItemID.SLAYER_ICY_WATER, "Ice cooler", 39)));
	}

	@Test
	public void theCompactItemsNameWhatIsMissingInAFewWords()
	{
		// The counts stay and the place goes, so it fits on one line. The line keeps both.
		final SlayerGearCheck.Result coolers = SlayerGearCheck.evaluate("Lizards", 40, 0, Collections.singletonList(
			held(ItemID.SLAYER_ICY_WATER, "Ice cooler", 26)), NO_VARBITS);
		assertEquals(Collections.singletonList("Missing: 14 ice coolers (26 of 40)"), coolers.getItems());
		assertEquals("MISSING: 14 more ice coolers for desert lizards (have 26, need 40)", coolers.getLine());
		assertFalse(coolers.isReady());
		// The first thing the line names, without its article
		assertEquals(Collections.singletonList("Missing: rock or granite hammer"), items("Gargoyles", 40, 0));
		assertEquals(Collections.singletonList("Missing: 40 bags of salt"), items("Rockslugs", 40, 0));
		assertEquals(Collections.singletonList("Missing: nose peg or Slayer helmet"), items("Aberrant spectres", 50, 0));
		assertEquals(Arrays.asList("Missing: spiny or Slayer helmet", "Missing: lit light source"), items("Wall beasts", 10, 0));
		// What to bring follows what is missing
		assertEquals(Arrays.asList("Missing: boots of stone or similar", "Bring: dragonbane weapon"), items("Wyrms", 100, 0));
		// The place stays when it fits
		assertEquals(Collections.singletonList("Missing: ice coolers"), items("Lizards", 0, 0));
		assertEquals(Collections.singletonList("Missing: 30 fishing explosives"), items("Mogres", 30, 0));
		// Ready, with what is held when that fits
		assertEquals(Collections.singletonList("Ready: Rock hammer"), SlayerGearCheck.evaluate("Gargoyles", 40, 0,
			Collections.singletonList(ROCK_HAMMER), NO_VARBITS).getItems());
		final SlayerGearCheck.Result salt = SlayerGearCheck.evaluate("Rockslugs", 40, 0, Collections.singletonList(
			held(ItemID.SLAYER_BAG_OF_SALT, "Bag of salt", 52)), NO_VARBITS);
		assertEquals(Collections.singletonList("Ready: 52 bags of salt (need 40)"), salt.getItems());
		assertTrue(salt.isReady());
		assertEquals(Collections.singletonList("Ready"), SlayerGearCheck.evaluate("Fire Giants", 50, SlayerRequirements.KARUULM_AREA,
			Collections.<Held>emptyList(), set(VarbitID.KOUREND_DIARY_ELITE_COMPLETE)).getItems());
		assertNull(SlayerGearCheck.evaluate("Fire Giants", 50, 0, Collections.<Held>emptyList(), NO_VARBITS));
	}

	/** Every task either table knows, and a task neither knows, which still gets the helmet. */
	private static List<String> everyKnownTask()
	{
		final List<String> tasks = new ArrayList<>(SlayerRequirements.taskKeys());
		tasks.addAll(SlayerRequirements.recommendedKeys());
		tasks.addAll(SlayerRequirements.undeadKeys());
		tasks.add("Cows");
		return tasks;
	}

	@Test
	public void everyCompactItemIsShortAndPlain()
	{
		for (String task : everyKnownTask())
		{
			for (int area : new int[]{0, SlayerRequirements.KARUULM_AREA, SlayerRequirements.SLEPE_AREA, SlayerRequirements.DARKMEYER_AREA})
			{
				for (int kills : new int[]{40, 1, 0})
				{
					for (IntUnaryOperator varbits : Arrays.asList(NO_VARBITS, HELMET_UNLOCKED))
					{
						// Nothing held, and an open flame as the only light
						for (List<Held> held : Arrays.asList(Collections.<Held>emptyList(), Collections.singletonList(CANDLE)))
						{
							final SlayerGearCheck.Result result = SlayerGearCheck.evaluate(task, kills, area, held, varbits);
							if (result == null) continue;
							assertEquals(SlayerGearCheck.check(task, kills, area, held, varbits), result.getLine());
							for (String item : result.getItems())
							{
								assertTrue(item, item.length() <= SlayerGearCheck.MAX_ITEM);
							}
						}
					}
				}
			}
		}
	}

	private static List<String> items(String task, int kills, int area)
	{
		return items(task, kills, area, NO_VARBITS);
	}

	private static List<String> items(String task, int kills, int area, IntUnaryOperator varbits)
	{
		return SlayerGearCheck.evaluate(task, kills, area, Collections.<Held>emptyList(), varbits).getItems();
	}

	@Test
	public void shroomSprayerStillNeedsASpray()
	{
		final IntUnaryOperator sprayer = set(VarbitID.SLAYER_AUTOKILL_ZYGOMITES);
		assertEquals("MISSING: a fungicide spray", check("Mutated Zygomites", 40, 0, sprayer));
		assertEquals("READY: 40 fungicide spray charges (need 40). 'Shroom Sprayer is unlocked, so the finishing blow is automatic",
			check("Mutated Zygomites", 40, 0, sprayer, held(ItemID.SLAYER_SPRAY_PUMP_10, "Fungicide spray 10"),
				fungicide(), fungicide(), fungicide()));
	}

	@Test
	public void zygomiteChecksRunUnderTheTaskNameRuneLiteStores()
	{
		// The cache's casing, which is what the core Slayer plugin saves, and the Task enum's.
		assertEquals("MISSING: a fungicide spray", check("Mutated Zygomites", 40, 0, NO_VARBITS));
		assertEquals("MISSING: a fungicide spray", check("Mutated zygomites", 40, 0, NO_VARBITS));
	}

	@Test
	public void fungicideChargesComeFromTheSprayNamesAndTheRefills()
	{
		final Held seven = held(ItemID.SLAYER_SPRAY_PUMP_7, "Fungicide spray 7");
		final Held three = held(ItemID.SLAYER_SPRAY_PUMP_3, "Fungicide spray 3");
		assertEquals("READY: 30 fungicide spray charges (need 30)",
			check("Mutated zygomites", 30, 0, NO_VARBITS, seven, three, fungicide(), fungicide()));
		assertEquals("MISSING: 10 more fungicide spray charges (have 30, need 40)",
			check("Mutated zygomites", 40, 0, NO_VARBITS, seven, three, fungicide(), fungicide()));
		// Refills need a spray to go into.
		assertEquals("MISSING: a fungicide spray", check("Mutated zygomites", 40, 0, NO_VARBITS, fungicide()));
		assertEquals(10, SlayerGearCheck.chargesInName("Fungicide spray 10"));
		assertEquals(0, SlayerGearCheck.chargesInName("Fungicide spray 0"));
		assertEquals(4, SlayerGearCheck.chargesInName("Rod of Ivandis (4)"));
		assertEquals(0, SlayerGearCheck.chargesInName("Fungicide"));
		assertEquals(0, SlayerGearCheck.chargesInName(null));
	}

	@Test
	public void unknownKillCountsStillCheckForTheItem()
	{
		assertEquals("MISSING: ice coolers for desert lizards", check("Lizards", 0, 0, NO_VARBITS));
		assertEquals("READY: 5 ice coolers", check("Lizards", 0, 0, NO_VARBITS, held(ItemID.SLAYER_ICY_WATER, "Ice cooler", 5)));
		assertEquals("MISSING: fungicide spray charges",
			check("Mutated zygomites", 0, 0, NO_VARBITS, held(ItemID.SLAYER_SPRAY_PUMP_0, "Fungicide spray 0")));
	}

	@Test
	public void everySlayerHelmetCountsForTheHelmetTasks()
	{
		for (int id : SlayerRequirements.SLAYER_HELMET_IDS)
		{
			for (String task : Arrays.asList("Aberrant spectres", "Banshees", "Dust devils", "Smoke devils", "Wall beasts", "Sourhogs"))
			{
				final String line = check(task, 50, 0, NO_VARBITS, held(id, "Slayer helmet"), LANTERN);
				assertTrue(task + " with helmet " + id + ": " + line, line.startsWith("READY"));
			}
		}
		// A recolour added to the game after this build has an id the list does not know yet.
		assertEquals("READY: Golden slayer helmet (i)",
			check("Aberrant spectres", 50, 0, NO_VARBITS, held(99_999, "Golden slayer helmet (i)")));
		assertEquals("READY: Nose peg", check("Aberrant spectres", 50, 0, NO_VARBITS, held(ItemID.SLAYER_NOSEPEG, "Nose peg")));
		// The goggles do not stand in for the nose peg.
		assertEquals("MISSING: a nose peg or Slayer helmet",
			check("Aberrant spectres", 50, 0, NO_VARBITS, held(ItemID.SLAYER_REINFORCED_GOGGLES, "Reinforced goggles")));
	}

	@Test
	public void copiesThatCannotDoTheJobDoNotCount()
	{
		// A noted hammer keeps the hammer's name.
		assertEquals("MISSING: a rock hammer or granite hammer, or 40 rock thrownhammers",
			check("Gargoyles", 40, 0, NO_VARBITS, held(NOTED_ROCK_HAMMER, "Rock hammer")));
		// "Unlit bug lantern" contains "lit bug lantern".
		assertEquals("MISSING: a lit bug lantern",
			check("Harpie bug swarms", 50, 0, NO_VARBITS, held(ItemID.SLAYER_BUGLAN_OFF, "Unlit bug lantern")));
		assertEquals("READY: Lit bug lantern",
			check("Harpie bug swarms", 50, 0, NO_VARBITS, held(ItemID.SLAYER_BUGLAN_ON, "Lit bug lantern")));
		// Lit and unlit lanterns share their name.
		assertEquals("MISSING: a lit light source",
			check("Cave bugs", 50, 0, NO_VARBITS, held(ItemID.BULLSEYE_LANTERN_UNLIT, "Bullseye lantern")));
		// The quest copy of V's shield cannot be wielded.
		assertEquals("MISSING: a mirror shield or V's shield",
			check("Basilisks", 50, 0, NO_VARBITS, held(ItemID.VIKINGEXILE_V_SHIELD, "V's shield")));
		assertEquals("READY: V's shield", check("Cockatrice", 50, 0, NO_VARBITS, held(ItemID.V_SHIELD, "V's shield")));
	}

	@Test
	public void karuulmCreaturesNeedBootsUnlessTheEliteDiaryIsDone()
	{
		assertEquals("MISSING: " + KARUULM_BOOTS + BRING_DRAGONBANE, check("Drakes", 50, 0, NO_VARBITS));
		assertEquals("READY: Granite boots" + BRING_DRAGONBANE, check("Hydras", 50, 0, NO_VARBITS,
			held(ItemID.GRANITE_BOOTS, "Granite boots")));
		assertEquals("READY: The elite Kourend & Kebos diary protects you from the hot floor" + BRING_DRAGONBANE,
			check("The Alchemical Hydra", 5, 0, set(VarbitID.KOUREND_DIARY_ELITE_COMPLETE)));
	}

	@Test
	public void wyrmsNeedBootsOnlyInTheKaruulmSlayerDungeon()
	{
		// Without Konar's area the place is named, since Wyrmscraig and the others have a safe floor.
		assertEquals("MISSING: " + KARUULM_BOOTS + " for the Karuulm Slayer Dungeon" + BRING_DRAGONBANE, check("Wyrms", 100, 0, NO_VARBITS));
		assertEquals("MISSING: " + KARUULM_BOOTS + BRING_DRAGONBANE, check("Wyrms", 100, SlayerRequirements.KARUULM_AREA, NO_VARBITS));
		// Nothing is mandatory at Wyrmscraig, so there is no READY or MISSING
		assertEquals("Bring a dragonbane weapon", check("Wyrms", 100, WYRMSCRAIG_AREA, NO_VARBITS));
	}

	@Test
	public void konarsKaruulmAreaNeedsBootsForAnyTask()
	{
		assertEquals("MISSING: " + KARUULM_BOOTS, check("Fire Giants", 100, SlayerRequirements.KARUULM_AREA, NO_VARBITS));
		assertEquals("READY: Boots of stone. Bring a demonbane weapon", check("Hellhounds", 100, SlayerRequirements.KARUULM_AREA,
			NO_VARBITS, held(ItemID.BOOTS_OF_STONE, "Boots of stone")));
		assertNull(check("Fire Giants", 100, 0, NO_VARBITS));
	}

	@Test
	public void konarsWaterfallDungeonNeedsARopeUntilTheMediumKandarinDiary()
	{
		assertEquals("MISSING: a rope", check("Fire Giants", 100, SlayerRequirements.WATERFALL_AREA, NO_VARBITS));
		assertEquals("READY: Rope", check("Fire Giants", 100, SlayerRequirements.WATERFALL_AREA, NO_VARBITS, held(ItemID.ROPE, "Rope")));
		assertEquals("READY: The medium Kandarin diary lets you into the Waterfall Dungeon without a rope",
			check("Fire Giants", 100, SlayerRequirements.WATERFALL_AREA, set(VarbitID.KANDARIN_DIARY_MEDIUM_COMPLETE)));
	}

	@Test
	public void darkCavesNeedALitLightSource()
	{
		assertEquals("MISSING: a spiny helmet or Slayer helmet, and a lit light source", check("Wall beasts", 10, 0, NO_VARBITS));
		assertEquals("READY: Spiny helmet and Bullseye lantern", check("Wall beasts", 10, 0, NO_VARBITS,
			held(ItemID.WALLBEAST_SPIKE_HELMET, "Spiny helmet"), LANTERN));
		assertEquals("READY: Spiny helmet. Your Fire of Eternal Light lights the Lumbridge Swamp Caves", check("Wall beasts", 10, 0,
			set(VarbitID.MY2ARM_FIRE_LUMB), held(ItemID.WALLBEAST_SPIKE_HELMET, "Spiny helmet")));
		assertEquals("MISSING: a Slayer bell, and a lit light source", check("Molanisks", 40, 0, NO_VARBITS));
		assertEquals("MISSING: a spade, and a lit light source", check("The Giant Mole", 5, 0, NO_VARBITS));
		assertEquals("READY: Spade. Your Fire of Eternal Light lights the Mole Hole",
			check("The Giant Mole", 5, 0, set(VarbitID.MY2ARM_FIRE_MOLE), held(ItemID.SPADE, "Spade")));
	}

	@Test
	public void caveBugsAndSlimesStillNeedLightInDorgeshKaan()
	{
		// Both of their caves are dark. The Fire of Eternal Light only lights the Lumbridge Swamp Caves.
		assertEquals("MISSING: a lit light source", check("Cave bugs", 20, 0, NO_VARBITS));
		assertEquals("MISSING: a lit light source for the Dorgesh-Kaan South Dungeon. "
				+ "Your Fire of Eternal Light lights the Lumbridge Swamp Caves",
			check("Cave slimes", 20, 0, set(VarbitID.MY2ARM_FIRE_LUMB)));
		assertEquals("READY: Bullseye lantern", check("Cave slimes", 20, 0, set(VarbitID.MY2ARM_FIRE_LUMB), LANTERN));
	}

	@Test
	public void caveHorrorsNeedLightAndTheIconIsAdvice()
	{
		assertEquals("MISSING: a lit light source. No witchwood icon, so keep Protect from Melee on",
			check("Cave horrors", 50, 0, NO_VARBITS));
		assertEquals("READY: Bullseye lantern and Witchwood icon",
			check("Cave horrors", 50, 0, NO_VARBITS, LANTERN, held(ItemID.WITCHWOOD_ICON, "Witchwood icon")));
		assertEquals("READY: Your Fire of Eternal Light lights the Mos Le'Harmless Cave. "
				+ "No witchwood icon, so keep Protect from Melee on",
			check("Cave horrors", 50, 0, set(VarbitID.MY2ARM_FIRE_HORRORS)));
	}

	@Test
	public void vampyresNeedAWeaponOnlyWhereKonarSendsYou()
	{
		// Feral vampyres take damage from any weapon, so a task without an area needs nothing.
		assertNull(check("Vampyres", 100, 0, NO_VARBITS));
		assertEquals("MISSING: an Ivandis flail or better vampyrebane weapon",
			check("Vampyres", 100, SlayerRequirements.SLEPE_AREA, NO_VARBITS, held(ItemID.VAMPYRE_RING, "Efaritay's aid")));
		assertEquals("READY: Efaritay's aid",
			check("Vampyres", 100, SlayerRequirements.DARKMEYER_AREA, NO_VARBITS, held(ItemID.VAMPYRE_RING, "Efaritay's aid")));
		assertEquals("READY: Blisterwood flail",
			check("Vampyres", 100, SlayerRequirements.VAMPYRIUM_AREA, NO_VARBITS, held(ItemID.BLISTERWOOD_FLAIL, "Blisterwood flail")));
		assertEquals("MISSING: an Ivandis flail or better vampyrebane weapon", check("Venators", 60, 0, NO_VARBITS));
	}

	@Test
	public void placeBoundConsumablesNameThePlace()
	{
		assertEquals("MISSING: 30 fishing explosives for Mudskipper Point", check("Mogres", 30, 0, NO_VARBITS));
		assertEquals("MISSING: 10 more fishing explosives for Mudskipper Point (have 20, need 30)",
			check("Mogres", 30, 0, NO_VARBITS, held(ItemID.FISHING_EXPLOSIVE, "Fishing explosive", 20)));
	}

	/** Moved from SlayerDatasetTest, which checked the same through a name-only compatibility method. */
	@Test
	public void mandatoryGearSupportsAlternatives()
	{
		assertEquals("READY: Black slayer helmet (i)",
			check("Aberrant spectres", 50, 0, NO_VARBITS, held(ItemID.SLAYER_HELM_I_BLACK, "Black slayer helmet (i)")));
		assertEquals("READY: Leaf-bladed battleaxe",
			check("Turoth", 60, 0, NO_VARBITS, held(ItemID.LEAFBLADED_BATTLEAXE, "Leaf-bladed battleaxe")));
		assertEquals("READY: Amethyst broad bolts",
			check("Turoth", 60, 0, NO_VARBITS, held(ItemID.SLAYER_BROAD_BOLT_AMETHYST, "Amethyst broad bolts", 100)));
	}

	@Test
	public void otherMandatoryItems()
	{
		assertEquals("READY: Toxic staff (uncharged)",
			check("Turoth", 60, 0, NO_VARBITS, held(ItemID.TOXIC_SOTD, "Toxic staff (uncharged)")));
		assertEquals("MISSING: a leaf-bladed weapon, broad ammo or a Magic Dart staff",
			check("Kurask", 60, 0, NO_VARBITS, held(ItemID.SLAYERGUIDE_BROAD_ARROWS, "Broad arrows")));
		assertEquals("READY: Dragonfire ward" + BRING_DRAGONBANE,
			check("Fossil Island Wyverns", 60, 0, NO_VARBITS, held(ItemID.DRAGONFIRE_WARD_UNCHARGED, "Dragonfire ward")));
		assertEquals("MISSING: an elemental, mind, dragonfire or ancient wyvern shield, or a dragonfire ward" + BRING_DRAGONBANE,
			check("Skeletal Wyverns", 60, 0, NO_VARBITS, held(ItemID.ANTIDRAGONBREATHSHIELD, "Anti-dragon shield")));
		assertEquals("MISSING: a crystal chime", check("Warped Creatures", 100, 0, NO_VARBITS));
		assertEquals("MISSING: Slayer gloves", check("Fever Spiders", 100, 0, NO_VARBITS));
		assertEquals("MISSING: a spade", check("Brine Rats", 100, 0, NO_VARBITS));
		assertEquals("MISSING: a lockpick", check("Magic Axes", 30, 0, NO_VARBITS));
		assertEquals("MISSING: a tortugan shield", check("The Shellbane Gryphon", 5, 0, NO_VARBITS));
		assertEquals("READY: Rock thrownhammer", check("The Grotesque Guardians", 10, 0, NO_VARBITS,
			held(ItemID.SLAYER_ROCK_THROWNHAMMER, "Rock thrownhammer", 3)));
	}

	@Test
	public void tasksWithoutNeedsOrRecommendationsHaveNoLine()
	{
		assertNull(check("Fire Giants", 100, 0, NO_VARBITS));
		assertNull(check("Trolls", 100, 0, NO_VARBITS));
		assertNull(check("Cows", 100, 0, NO_VARBITS));
		assertNull(SlayerGearCheck.check(null, 100, 0, Collections.emptyList(), NO_VARBITS));
		assertFalse(SlayerGearCheck.hasNeeds(null, 0, NO_VARBITS));
		assertFalse(SlayerGearCheck.hasNeeds("Fire Giants", 0, NO_VARBITS));
		assertTrue(SlayerGearCheck.hasNeeds("Fire Giants", SlayerRequirements.KARUULM_AREA, NO_VARBITS));
		// Recommended items alone are enough for a line, and the helmet makes every task have one
		assertTrue(SlayerGearCheck.hasNeeds("Abyssal Demons", 0, NO_VARBITS));
		assertTrue(SlayerGearCheck.hasNeeds("Fire Giants", 0, HELMET_UNLOCKED));
		assertFalse(SlayerGearCheck.hasNeeds(null, 0, HELMET_UNLOCKED));
	}

	/** User-facing text: short plain sentences without em dashes, en dashes or semicolons. */
	@Test
	public void linesUsePlainPunctuation()
	{
		final List<String> lines = new ArrayList<>();
		for (String task : everyKnownTask())
		{
			for (int area : new int[]{0, SlayerRequirements.KARUULM_AREA, SlayerRequirements.WATERFALL_AREA,
				SlayerRequirements.DARKMEYER_AREA, SlayerRequirements.SLEPE_AREA})
			{
				lines.add(check(task, 40, area, NO_VARBITS));
				lines.add(check(task, 40, area, varbit -> 1));
				lines.add(check(task, 40, area, HELMET_UNLOCKED, CANDLE));
			}
		}
		for (String line : lines)
		{
			if (line == null) continue;
			assertTrue(line, line.startsWith("READY: ") || line.startsWith("MISSING: ") || line.startsWith("Bring ")
				|| line.startsWith("You have "));
			assertFalse(line, line.endsWith("."));
		}
		// The helmet only adds to the end. READY or MISSING stays in front, with the same words.
		for (String task : SlayerRequirements.taskKeys())
		{
			final String plain = check(task, 40, 0, NO_VARBITS);
			if (plain == null) continue;
			assertTrue(plain, check(task, 40, 0, HELMET_UNLOCKED).startsWith(plain));
		}
	}

	// ---- the recommended tier ----

	@Test
	public void demonTasksAskForADemonbaneWeaponAndNameTheOneHeld()
	{
		final SlayerGearCheck.Result none = SlayerGearCheck.evaluate("Greater demons", 100, 0, Collections.<Held>emptyList(), NO_VARBITS);
		assertEquals("Bring a demonbane weapon", none.getLine());
		assertEquals(Collections.<String>emptyList(), none.getMissing());
		assertEquals(Collections.singletonList("Bring: demonbane weapon"), none.getBring());
		assertEquals(Collections.singletonList("Bring: demonbane weapon"), none.getItems());
		assertEquals(SlayerGearCheck.Status.BRING, none.getStatus());
		// Nothing mandatory is missing
		assertTrue(none.isReady());

		final SlayerGearCheck.Result arclight = SlayerGearCheck.evaluate("Greater demons", 100, 0,
			Collections.singletonList(held(ItemID.ARCLIGHT, "Arclight")), NO_VARBITS);
		assertEquals("You have Arclight", arclight.getLine());
		assertEquals(Collections.singletonList("Ready: Arclight"), arclight.getItems());
		assertEquals(SlayerGearCheck.Status.READY, arclight.getStatus());
		// An Arclight out of charges works as Darklight does, and holy water and the scorching bow are demonbane too
		assertEquals("You have Arclight (inactive)", check("Abyssal demons", 50, 0, NO_VARBITS, held(ItemID.ARCLIGHT_INACTIVE, "Arclight (inactive)")));
		assertEquals("You have Holy water", check("Lesser demons", 50, 0, NO_VARBITS, held(ItemID.HOLY_WATER, "Holy water", 30)));
		assertEquals("You have Scorching bow", check("Cerberus", 5, 0, NO_VARBITS, held(ItemID.SCORCHING_BOW, "Scorching bow")));
		// Any other weapon is not
		assertEquals("Bring a demonbane weapon", check("Nechryael", 50, 0, NO_VARBITS, held(ItemID.ABYSSAL_WHIP, "Abyssal whip")));
	}

	@Test
	public void dragonTasksAskForDragonfireProtectionAndADragonbaneWeapon()
	{
		assertEquals("Bring an anti-dragon shield or antifire potion, and a dragonbane weapon", check("Black dragons", 100, 0, NO_VARBITS));
		assertEquals(Arrays.asList("Bring: dragonfire protection", "Bring: dragonbane weapon"), items("Black dragons", 100, 0));
		// A shield worn or a potion in the inventory, charged or not
		assertEquals("Bring a dragonbane weapon. You have Super antifire potion(4)", check("Metal dragons", 100, 0, NO_VARBITS,
			held(ItemID._4DOSE3ANTIDRAGON, "Super antifire potion(4)")));
		assertEquals("You have Dragonfire shield and Dragon hunter lance", check("Red dragons", 100, 0, NO_VARBITS,
			held(ItemID.DRAGONFIRE_SHIELD_UNCHARGED, "Dragonfire shield"), held(ItemID.DRAGONHUNTER_LANCE, "Dragon hunter lance")));
		// The elemental shield only stops icy breath, and a noted potion cannot be drunk
		assertEquals("Bring an anti-dragon shield or antifire potion. You have Dragon hunter crossbow (t)", check("Blue dragons", 100, 0,
			NO_VARBITS, held(ItemID.ELEMENTAL_SHIELD, "Elemental shield"), held(NOTED_ANTIFIRE, "Antifire potion(4)", 10),
			held(ItemID.DRAGONHUNTER_XBOW_VORKATH, "Dragon hunter crossbow (t)")));
		// Drakes breathe fire you can step away from, so they only ask for the weapon, and wyverns breathe ice
		assertEquals(Collections.singletonList(SlayerRequirements.DRAGONBANE), SlayerRequirementsTest.groups("Drakes"));
		assertEquals(Collections.singletonList(SlayerRequirements.DRAGONBANE), SlayerRequirementsTest.groups("Skeletal wyverns"));
	}

	@Test
	public void kalphiteAndKillerwattTasksAskForTheirItems()
	{
		assertEquals("Bring a keris or keris partisan", check("Kalphites", 100, 0, NO_VARBITS));
		assertEquals("You have Keris partisan of breaching", check("Scabarites", 100, 0, NO_VARBITS,
			held(ItemID.KERIS_PARTISAN_BREACH, "Keris partisan of breaching")));
		assertEquals("You have Keris(p++)", check("The Kalphite Queen", 5, 0, NO_VARBITS, held(ItemID.CONTACT_KERIS_P__, "Keris(p++)")));
		assertEquals("Bring insulated boots", check("Killerwatts", 50, 0, NO_VARBITS));
		assertEquals("You have Insulated boots", check("Killerwatts", 50, 0, NO_VARBITS, held(ItemID.SLAYER_BOOTS, "Insulated boots")));
	}

	@Test
	public void everyTaskAsksForTheHelmetOnceMalevolentMasqueradeIsBought()
	{
		// A new account without the unlock is not asked
		assertNull(check("Cows", 50, 0, NO_VARBITS));
		assertEquals("Bring a Slayer helmet or black mask", check("Cows", 50, 0, HELMET_UNLOCKED));
		assertEquals("You have Black mask (i)", check("Cows", 50, 0, HELMET_UNLOCKED, held(ItemID.NZONE_BLACK_MASK, "Black mask (i)")));
		assertEquals("You have Twisted slayer helmet (i)", check("Cows", 50, 0, HELMET_UNLOCKED,
			held(ItemID.SLAYER_HELM_I_TWISTED, "Twisted slayer helmet (i)")));
		// After the mandatory part, and with the other recommendations
		assertEquals("READY: Rock hammer. Bring a Slayer helmet or black mask", check("Gargoyles", 40, 0, HELMET_UNLOCKED, ROCK_HAMMER));
		assertEquals("Bring a demonbane weapon, and a Slayer helmet or black mask", check("Abyssal demons", 50, 0, HELMET_UNLOCKED));
		assertEquals(Arrays.asList("Missing: lit light source", "Bring: Slayer helmet or black mask"), SlayerGearCheck.evaluate("Cave bugs",
			20, 0, Collections.<Held>emptyList(), HELMET_UNLOCKED).getItems());
	}

	@Test
	public void theHelmetGivesWayToTheSalveAgainstTheUndeadAndToTheHeadSlotItems()
	{
		// The salve amulet beats the helmet's bonus on undead tasks and does not stack with it
		assertEquals("Bring a Slayer helmet, black mask or salve amulet", check("Ankou", 50, 0, HELMET_UNLOCKED));
		assertEquals("You have Salve amulet(ei)", check("Ankou", 50, 0, HELMET_UNLOCKED, held(ItemID.SW_SALVE_AMULET_E, "Salve amulet(ei)")));
		assertEquals(Collections.singletonList("Bring: Slayer helm, mask or salve"), items("Zombies", 50, 0, HELMET_UNLOCKED));
		// A spiny helmet takes the head slot, so only the Slayer helmet does both jobs
		assertEquals("MISSING: a lit light source. Bring a Slayer helmet", check("Wall beasts", 10, 0, HELMET_UNLOCKED,
			held(ItemID.WALLBEAST_SPIKE_HELMET, "Spiny helmet")));
		assertEquals("READY: Earmuffs. Bring a Slayer helmet or salve amulet", check("Banshees", 50, 0, HELMET_UNLOCKED,
			held(ItemID.SLAYER_EARMUFFS, "Earmuffs")));
		// The red line already asks for the helmet, and a helmet that does the mandatory job is not named twice
		assertEquals(Collections.singletonList("Missing: nose peg or Slayer helmet"), items("Aberrant spectres", 50, 0, HELMET_UNLOCKED));
		assertEquals("READY: Slayer helmet (i)", check("Aberrant spectres", 50, 0, HELMET_UNLOCKED, held(ItemID.SLAYER_HELM_I, "Slayer helmet (i)")));
		assertEquals("READY: Nose peg. You have Salve amulet (e)", check("Aberrant spectres", 50, 0, HELMET_UNLOCKED,
			held(ItemID.SLAYER_NOSEPEG, "Nose peg"), held(ItemID.LOTR_CRYSTALSHARD_NECKLACE_UPGRADE, "Salve amulet (e)")));
	}

	@Test
	public void theReadyItemNamesTheMandatoryItemsFirstWhenAllDoNotFit()
	{
		final SlayerGearCheck.Result salt = SlayerGearCheck.evaluate("Rockslugs", 40, 0, Arrays.asList(held(ItemID.SLAYER_BAG_OF_SALT,
			"Bag of salt", 52), held(ItemID.SLAYER_HELM, "Slayer helmet")), HELMET_UNLOCKED);
		assertEquals("READY: 52 bags of salt (need 40). You have Slayer helmet", salt.getLine());
		assertEquals(Collections.singletonList("Ready: 52 bags of salt (need 40)"), salt.getItems());
		assertEquals(Collections.singletonList("Ready: Rock hammer, Slayer helmet"), SlayerGearCheck.evaluate("Gargoyles", 40, 0,
			Arrays.asList(ROCK_HAMMER, held(ItemID.SLAYER_HELM, "Slayer helmet")), HELMET_UNLOCKED).getItems());
	}

	private static final Held CANDLE = held(ItemID.LIT_CANDLE, "Lit candle");
	private static final String SWAMP_GAS = ". Bring a covered lantern. An open flame sets off the swamp gas in parts of the Lumbridge"
		+ " Swamp Caves and goes out";

	@Test
	public void anOpenFlameInTheSwampCavesAsksForACoveredLantern()
	{
		// The flame lights the cave, so the darkness check is met, but it sets off the gas
		final SlayerGearCheck.Result candle = SlayerGearCheck.evaluate("Cave bugs", 20, 0, Collections.singletonList(CANDLE), NO_VARBITS);
		assertEquals("READY: Lit candle" + SWAMP_GAS, candle.getLine());
		assertEquals(Collections.singletonList("Bring: covered lantern"), candle.getItems());
		assertEquals(SlayerGearCheck.Status.BRING, candle.getStatus());
		assertEquals("READY: Spiny helmet and Lit torch" + SWAMP_GAS, check("Wall beasts", 10, 0, NO_VARBITS,
			held(ItemID.WALLBEAST_SPIKE_HELMET, "Spiny helmet"), held(ItemID.TORCH_LIT, "Lit torch")));
		assertEquals("READY: Oil lamp" + SWAMP_GAS, check("Cave slimes", 20, 0, NO_VARBITS, held(ItemID.OIL_LAMP_LIT, "Oil lamp")));
		// The reason follows every request
		assertEquals("READY: Lit candle. Bring a covered lantern, and a Slayer helmet or black mask. An open flame sets off the swamp gas"
			+ " in parts of the Lumbridge Swamp Caves and goes out", check("Cave bugs", 20, 0, HELMET_UNLOCKED, CANDLE));
		assertEquals(Collections.singletonList(new SlayerCompanionOverlay.GearLine("Bring: covered lantern", java.awt.Color.YELLOW)),
			SlayerCompanionOverlay.gearLines(candle));
	}

	@Test
	public void aCoveredLightOrTheFireOfEternalLightMeansNoLantern()
	{
		// A covered light held alongside the flame is enough, and is not named again
		assertEquals("READY: Lit candle", check("Cave bugs", 20, 0, NO_VARBITS, CANDLE, LANTERN));
		assertEquals("READY: Bullseye lantern", check("Cave bugs", 20, 0, NO_VARBITS, LANTERN, CANDLE));
		assertEquals("READY: Slayer helmet and Lit candle", check("Wall beasts", 10, 0, NO_VARBITS, held(ItemID.SLAYER_HELM, "Slayer helmet"),
			CANDLE, held(ItemID.SEERS_HEADBAND_ELITE, "Kandarin headgear 4")));
		// The Fire of Eternal Light stops the explosions
		assertEquals("READY: Lit candle", check("Cave slimes", 20, 0, set(VarbitID.MY2ARM_FIRE_LUMB), CANDLE));
		// Without any light the red line asks for one, and the lantern is not asked for on top
		assertEquals(Collections.singletonList("Missing: lit light source"), items("Cave bugs", 20, 0));
		// Other dark caves have no swamp gas
		assertEquals("READY: Lit candle. No witchwood icon, so keep Protect from Melee on", check("Cave horrors", 50, 0, NO_VARBITS, CANDLE));
	}

	@Test
	public void theRecommendedTierCanBeTurnedOff()
	{
		// Off, a task with only recommendations has no check at all, and the helmet is not asked for
		assertNull(SlayerGearCheck.evaluate("Greater demons", 100, 0, Collections.<Held>emptyList(), NO_VARBITS, false));
		assertNull(SlayerGearCheck.evaluate("Cows", 50, 0, Collections.<Held>emptyList(), HELMET_UNLOCKED, false));
		assertFalse(SlayerGearCheck.hasNeeds("Abyssal demons", 0, HELMET_UNLOCKED, false));
		assertTrue(SlayerGearCheck.hasNeeds("Cave bugs", 0, NO_VARBITS, false));
		// Mandatory lines stay, without "Bring" or "You have"
		final SlayerGearCheck.Result drakes = SlayerGearCheck.evaluate("Drakes", 50, 0, Collections.singletonList(
			held(ItemID.DRAGONHUNTER_LANCE, "Dragon hunter lance")), HELMET_UNLOCKED, false);
		assertEquals("MISSING: " + KARUULM_BOOTS, drakes.getLine());
		assertEquals(Collections.singletonList("Missing: boots of stone or similar"), drakes.getItems());
		assertEquals(Collections.singletonList(new SlayerCompanionOverlay.GearLine("Missing: boots of stone or similar",
			SlayerCompanionPanel.MISSING_COLOR)), SlayerCompanionOverlay.gearLines(drakes));
		assertEquals("READY: Lit candle", SlayerGearCheck.evaluate("Cave bugs", 20, 0, Collections.singletonList(CANDLE), HELMET_UNLOCKED,
			false).getLine());
		// For every task the line with the tier off is the start of the line with it on
		for (String task : everyKnownTask())
		{
			for (IntUnaryOperator varbits : Arrays.asList(NO_VARBITS, HELMET_UNLOCKED))
			{
				final SlayerGearCheck.Result off = SlayerGearCheck.evaluate(task, 40, 0, Collections.singletonList(CANDLE), varbits, false);
				final SlayerGearCheck.Result on = SlayerGearCheck.evaluate(task, 40, 0, Collections.singletonList(CANDLE), varbits, true);
				if (off == null) continue;
				assertTrue(task, on.getLine().startsWith(off.getLine()));
				assertTrue(task, off.getBring().isEmpty());
				assertEquals(task, on.getMissing(), off.getMissing());
			}
		}
	}

	@Test
	public void theOverlayShowsWhatIsMissingInRedAndWhatToBringInYellow()
	{
		final List<SlayerCompanionOverlay.GearLine> lines = SlayerCompanionOverlay.gearLines(SlayerGearCheck.evaluate("Cave bugs", 20, 0,
			Collections.<Held>emptyList(), HELMET_UNLOCKED));
		assertEquals(Arrays.asList(new SlayerCompanionOverlay.GearLine("Missing: lit light source", SlayerCompanionPanel.MISSING_COLOR),
			new SlayerCompanionOverlay.GearLine("Bring: Slayer helmet or black mask", java.awt.Color.YELLOW)), lines);
		// A lighter red than pure red, which is hard to read on the overlay's background
		assertEquals(new java.awt.Color(255, 80, 80), lines.get(0).getColor());
		assertEquals(Collections.singletonList(new SlayerCompanionOverlay.GearLine("Bring: demonbane weapon", java.awt.Color.YELLOW)),
			SlayerCompanionOverlay.gearLines(SlayerGearCheck.evaluate("Greater demons", 100, 0, Collections.<Held>emptyList(), NO_VARBITS)));
		// Nothing when everything is there, or without a check
		assertTrue(SlayerCompanionOverlay.gearLines(SlayerGearCheck.evaluate("Greater demons", 100, 0,
			Collections.singletonList(held(ItemID.EMBERLIGHT, "Emberlight")), NO_VARBITS)).isEmpty());
		assertTrue(SlayerCompanionOverlay.gearLines(null).isEmpty());
	}

	// ---- the bank ----

	@Test
	public void aMandatoryItemOnlyTheBankHoldsIsToBeBroughtNotMissing()
	{
		final SlayerGearCheck.Result hammer = withBank("Gargoyles", 40, NO_VARBITS, bank(ItemID.SLAYER_ROCK_HAMMER, 1));
		assertEquals("Bring from your bank: Rock hammer", hammer.getLine());
		assertEquals(Collections.singletonList("In bank: Rock hammer"), hammer.getItems());
		assertEquals(Collections.<String>emptyList(), hammer.getMissing());
		assertEquals(SlayerGearCheck.Status.BRING, hammer.getStatus());
		assertEquals(Color.YELLOW, SlayerCompanionPanel.gearColor(hammer.getStatus()));
		assertEquals(Collections.singletonList(new SlayerCompanionOverlay.GearLine("In bank: Rock hammer", Color.YELLOW)),
			SlayerCompanionOverlay.gearLines(hammer));
		assertEquals(Collections.singletonList("In bank: Mirror shield"),
			withBank("Basilisks", 50, NO_VARBITS, bank(ItemID.SLAYER_MIRROR_SHIELD, 1)).getItems());
		// A name too long for the compact item gives way to the group's short name, and the line keeps the name
		final SlayerGearCheck.Result lantern = withBank("Cave horrors", 50, NO_VARBITS, bank(ItemID.ABYSSAL_LANTERN_REDWOOD, 1),
			held(ItemID.WITCHWOOD_ICON, "Witchwood icon"));
		assertEquals(Collections.singletonList("In bank: lit light source"), lantern.getItems());
		assertEquals("Bring from your bank: Abyssal lantern (redwood logs)", lantern.getLine());
	}

	@Test
	public void aMandatoryItemTheBankDoesNotHoldIsStillMissing()
	{
		final SlayerGearCheck.Result none = withBank("Gargoyles", 40, NO_VARBITS, bank(ItemID.SLAYER_ICY_WATER, 100, ItemID.ARCLIGHT, 1));
		assertEquals("MISSING: a rock hammer or granite hammer, or 40 rock thrownhammers", none.getLine());
		assertEquals(Collections.singletonList("Missing: rock or granite hammer"), none.getItems());
		assertEquals(SlayerGearCheck.Status.MISSING, none.getStatus());
		// What the bank holds follows what is missing, in yellow, and the check stays MISSING
		final SlayerGearCheck.Result wallBeasts = withBank("Wall beasts", 10, NO_VARBITS, bank(ItemID.WALLBEAST_SPIKE_HELMET, 1));
		assertEquals("MISSING: a lit light source. Bring from your bank: Spiny helmet", wallBeasts.getLine());
		assertEquals(Arrays.asList("Missing: lit light source", "In bank: Spiny helmet"), wallBeasts.getItems());
		assertEquals(SlayerGearCheck.Status.MISSING, wallBeasts.getStatus());
		assertEquals(Arrays.asList(new SlayerCompanionOverlay.GearLine("Missing: lit light source", SlayerCompanionPanel.MISSING_COLOR),
			new SlayerCompanionOverlay.GearLine("In bank: Spiny helmet", Color.YELLOW)), SlayerCompanionOverlay.gearLines(wallBeasts));
	}

	@Test
	public void aRecommendedItemTheBankHoldsIsNamed()
	{
		final SlayerGearCheck.Result arclight = withBank("Greater demons", 100, NO_VARBITS, bank(ItemID.ARCLIGHT, 1));
		assertEquals("Bring from your bank: Arclight", arclight.getLine());
		assertEquals(Collections.singletonList("Bring from bank: Arclight"), arclight.getItems());
		assertEquals(SlayerGearCheck.Status.BRING, arclight.getStatus());
		assertTrue("nothing mandatory is missing", arclight.isReady());
		// Not in the bank, the class of item is asked for
		assertEquals(Collections.singletonList("Bring: demonbane weapon"),
			withBank("Greater demons", 100, NO_VARBITS, bank(ItemID.SLAYER_ROCK_HAMMER, 1)).getItems());
		// Carried, it is what you have
		assertEquals("You have Emberlight", withBank("Greater demons", 100, NO_VARBITS, bank(ItemID.ARCLIGHT, 1),
			held(ItemID.EMBERLIGHT, "Emberlight")).getLine());
		// The bank's items come before the rest
		final SlayerGearCheck.Result mixed = withBank("Abyssal demons", 50, HELMET_UNLOCKED, bank(ItemID.ARCLIGHT, 1));
		assertEquals("Bring from your bank: Arclight. Bring a Slayer helmet or black mask", mixed.getLine());
		assertEquals(Arrays.asList("Bring from bank: Arclight", "Bring: Slayer helmet or black mask"), mixed.getItems());
		// A long name keeps "From bank" alone, then the group's short name stands in
		final SlayerGearCheck.Result dragons = withBank("Black dragons", 100, NO_VARBITS, bank(ItemID.DRAGONHUNTER_XBOW, 1));
		assertEquals("Bring from your bank: Dragon hunter crossbow. Bring an anti-dragon shield or antifire potion", dragons.getLine());
		assertEquals(Arrays.asList("From bank: Dragon hunter crossbow", "Bring: dragonfire protection"), dragons.getItems());
		assertEquals(Collections.singletonList("From bank: keris or keris partisan"),
			withBank("Kalphites", 100, NO_VARBITS, bank(ItemID.KERIS_PARTISAN_BREACH, 1)).getItems());
	}

	@Test
	public void usedUpItemsCountWhatIsCarriedFirstThenTheBank()
	{
		final Held coolers = held(ItemID.SLAYER_ICY_WATER, "Ice cooler", 26);
		final SlayerGearCheck.Result topUp = withBank("Lizards", 40, NO_VARBITS, bank(ItemID.SLAYER_ICY_WATER, 100), coolers);
		assertEquals("Bring from your bank: 14 more ice coolers for desert lizards (have 26, need 40)", topUp.getLine());
		assertEquals(Collections.singletonList("In bank: 14 ice coolers (26 of 40)"), topUp.getItems());
		assertEquals(SlayerGearCheck.Status.BRING, topUp.getStatus());
		assertFalse(topUp.isReady());
		assertEquals(Collections.singletonList("In bank: 14 ice coolers (26 of 40)"),
			withBank("Lizards", 40, NO_VARBITS, bank(ItemID.SLAYER_ICY_WATER, 14), coolers).getItems());
		// Short even with the bank, so they are missing
		assertEquals("MISSING: 14 more ice coolers for desert lizards (have 26, need 40)",
			withBank("Lizards", 40, NO_VARBITS, bank(ItemID.SLAYER_ICY_WATER, 13), coolers).getLine());
		// Enough carried, so the bank does not come into it
		assertEquals("READY: 40 ice coolers (need 40)", withBank("Lizards", 40, NO_VARBITS, bank(ItemID.SLAYER_ICY_WATER, 100),
			held(ItemID.SLAYER_ICY_WATER, "Ice cooler", 40)).getLine());
		// None carried, or the kills left unknown
		final SlayerGearCheck.Result none = withBank("Lizards", 40, NO_VARBITS, bank(ItemID.SLAYER_ICY_WATER, 100));
		assertEquals("Bring from your bank: 40 ice coolers for desert lizards", none.getLine());
		assertEquals(Collections.singletonList("In bank: 40 ice coolers"), none.getItems());
		assertEquals(Collections.singletonList("In bank: ice coolers"),
			withBank("Lizards", 0, NO_VARBITS, bank(ItemID.SLAYER_ICY_WATER, 100)).getItems());
		assertEquals("Bring from your bank: 30 fishing explosives for Mudskipper Point",
			withBank("Mogres", 30, NO_VARBITS, bank(ItemID.FISHING_EXPLOSIVE, 50)).getLine());
	}

	@Test
	public void aKeptItemFromTheBankComesFirstWhereTheLineNamesItFirst()
	{
		// Gargoyles name the hammer first, so it comes before more thrownhammers
		final Held thrown = held(ItemID.SLAYER_ROCK_THROWNHAMMER, "Rock thrownhammer", 26);
		assertEquals(Collections.singletonList("In bank: Granite hammer"), withBank("Gargoyles", 40, NO_VARBITS,
			bank(ItemID.GRANITE_HAMMER, 1, ItemID.SLAYER_ROCK_THROWNHAMMER, 50), thrown).getItems());
		final SlayerGearCheck.Result more = withBank("Gargoyles", 40, NO_VARBITS, bank(ItemID.SLAYER_ROCK_THROWNHAMMER, 50), thrown);
		assertEquals("Bring from your bank: 14 more rock thrownhammers (have 26, need 40)", more.getLine());
		assertEquals(Collections.singletonList("In bank: 14 rock thrownhammers"), more.getItems());
		// Rockslugs name the salt first, so the bank's salt comes before its brine sabre, which takes over when salt runs short
		final Held salt = held(ItemID.SLAYER_BAG_OF_SALT, "Bag of salt", 26);
		assertEquals(Collections.singletonList("In bank: 14 bags of salt"), withBank("Rockslugs", 40, NO_VARBITS,
			bank(ItemID.SLAYER_BAG_OF_SALT, 20, ItemID.OLAF2_BRINE_SABRE, 1), salt).getItems());
		assertEquals("Bring from your bank: Brine sabre", withBank("Rockslugs", 40, NO_VARBITS,
			bank(ItemID.SLAYER_BAG_OF_SALT, 5, ItemID.OLAF2_BRINE_SABRE, 1), salt).getLine());
	}

	@Test
	public void theSprayComesFromTheBankFirstThenItsCharges()
	{
		// No spray carried: the fullest the bank holds
		final SlayerGearCheck.Result spray = withBank("Mutated zygomites", 40, NO_VARBITS,
			bank(ItemID.SLAYER_SPRAY_PUMP_7, 1, ItemID.SLAYER_SPRAY_PUMP_10, 1, ItemID.SLAYER_FUNGICIDE, 3));
		assertEquals("Bring from your bank: Fungicide spray 10", spray.getLine());
		assertEquals(Collections.singletonList("In bank: Fungicide spray 10"), spray.getItems());
		// A spray carried: the bank's refills and sprays make up the charges
		final Held seven = held(ItemID.SLAYER_SPRAY_PUMP_7, "Fungicide spray 7");
		assertEquals("Bring from your bank: 23 more fungicide spray charges (have 7, need 30)",
			withBank("Mutated zygomites", 30, NO_VARBITS, bank(ItemID.SLAYER_FUNGICIDE, 3), seven).getLine());
		assertEquals("Bring from your bank: 3 more fungicide spray charges (have 7, need 10)",
			withBank("Mutated zygomites", 10, NO_VARBITS, bank(ItemID.SLAYER_SPRAY_PUMP_3, 1), seven).getLine());
		assertEquals("MISSING: 33 more fungicide spray charges (have 7, need 40)",
			withBank("Mutated zygomites", 40, NO_VARBITS, bank(ItemID.SLAYER_FUNGICIDE, 3), seven).getLine());
	}

	@Test
	public void onlyCarriedAndWornItemsMakeTheCheckReady()
	{
		final Held spiny = held(ItemID.WALLBEAST_SPIKE_HELMET, "Spiny helmet");
		final SlayerGearCheck.Result carried = withBank("Wall beasts", 10, NO_VARBITS,
			bank(ItemID.WALLBEAST_SPIKE_HELMET, 1, ItemID.BULLSEYE_LANTERN_LIT, 1), spiny, LANTERN);
		assertEquals("READY: Spiny helmet and Bullseye lantern", carried.getLine());
		assertEquals(SlayerGearCheck.Status.READY, carried.getStatus());
		assertTrue(carried.isReady());
		// One carried and one only in the bank: to bring, and not ready
		final SlayerGearCheck.Result half = withBank("Wall beasts", 10, NO_VARBITS, bank(ItemID.BULLSEYE_LANTERN_LIT, 1), spiny);
		assertEquals("Bring from your bank: Bullseye lantern", half.getLine());
		assertEquals(Collections.singletonList("In bank: Bullseye lantern"), half.getItems());
		assertEquals(SlayerGearCheck.Status.BRING, half.getStatus());
		assertFalse(half.isReady());
		assertNull(half.getReadyItem());
		// An unlock that finishes the monster is said once the hammer is carried
		assertEquals("Bring from your bank: Rock hammer", withBank("Gargoyles", 40, set(VarbitID.SLAYER_AUTOKILL_GARGOYLES),
			bank(ItemID.SLAYER_ROCK_HAMMER, 1)).getLine());
	}

	@Test
	public void notesFollowWhatToBringFromTheBank()
	{
		assertEquals("Bring from your bank: Bullseye lantern. No witchwood icon, so keep Protect from Melee on",
			withBank("Cave horrors", 50, NO_VARBITS, bank(ItemID.BULLSEYE_LANTERN_LIT, 1)).getLine());
		final SlayerGearCheck.Result slimes = withBank("Cave slimes", 20, set(VarbitID.MY2ARM_FIRE_LUMB), bank(ItemID.BULLSEYE_LANTERN_LIT, 1));
		assertEquals("Bring from your bank: Bullseye lantern for the Dorgesh-Kaan South Dungeon. "
			+ "Your Fire of Eternal Light lights the Lumbridge Swamp Caves", slimes.getLine());
		assertEquals(Collections.singletonList("In bank: Bullseye lantern"), slimes.getItems());
	}

	@Test
	public void anItemFromTheBankIsNamedOnce()
	{
		// The helmet does the nose peg's job and is the helmet asked for, so it is named once
		final SlayerGearCheck.Result helmet = withBank("Aberrant spectres", 50, HELMET_UNLOCKED, bank(ItemID.SLAYER_HELM, 1));
		assertEquals("Bring from your bank: Slayer helmet", helmet.getLine());
		assertEquals(Collections.singletonList("In bank: Slayer helmet"), helmet.getItems());
		// A nose peg leaves the salve to be asked for
		final SlayerGearCheck.Result peg = withBank("Aberrant spectres", 50, HELMET_UNLOCKED,
			bank(ItemID.SLAYER_NOSEPEG, 1, ItemID.SW_SALVE_AMULET_E, 1));
		assertEquals("Bring from your bank: Nose peg and Salve amulet(ei)", peg.getLine());
		assertEquals(Arrays.asList("In bank: Nose peg", "Bring from bank: Salve amulet(ei)"), peg.getItems());
	}

	@Test
	public void theBanksLightIsOneThatDoesNotSetOffTheSwampGas()
	{
		// The candle comes first in the list of lights, but the lantern is the one to take
		assertEquals(Collections.singletonList("In bank: Bullseye lantern"), withBank("Cave bugs", 20, NO_VARBITS,
			bank(ItemID.LIT_CANDLE, 1, ItemID.BULLSEYE_LANTERN_LIT, 1)).getItems());
		// Only a flame in the bank: take it, and the covered lantern is asked for as it is with a flame carried
		final SlayerGearCheck.Result flame = withBank("Cave bugs", 20, NO_VARBITS, bank(ItemID.LIT_CANDLE, 1));
		assertEquals("Bring from your bank: Lit candle" + SWAMP_GAS, flame.getLine());
		assertEquals(Arrays.asList("In bank: Lit candle", "Bring: covered lantern"), flame.getItems());
		// A flame carried and a lantern in the bank
		final SlayerGearCheck.Result lantern = withBank("Cave bugs", 20, NO_VARBITS, bank(ItemID.BULLSEYE_LANTERN_LIT, 1), CANDLE);
		assertEquals("READY: Lit candle. Bring from your bank: Bullseye lantern. An open flame sets off the swamp gas in parts of the"
			+ " Lumbridge Swamp Caves and goes out", lantern.getLine());
		assertEquals(Collections.singletonList("Bring from bank: Bullseye lantern"), lantern.getItems());
	}

	/** Before the bank is seen, and with a bank that holds none of the items, the check is the same as without a bank. */
	@Test
	public void anUnseenOrEmptyBankChangesNothing()
	{
		final BankGear empty = BankGear.of(new Item[]{new Item(ItemID.COINS, 100)});
		for (String task : everyKnownTask())
		{
			for (int area : new int[]{0, SlayerRequirements.KARUULM_AREA, SlayerRequirements.SLEPE_AREA})
			{
				for (IntUnaryOperator varbits : Arrays.asList(NO_VARBITS, HELMET_UNLOCKED, set(VarbitID.SLAYER_AUTOKILL_GARGOYLES)))
				{
					for (List<Held> held : Arrays.asList(Collections.<Held>emptyList(), Collections.singletonList(CANDLE),
						Collections.singletonList(held(ItemID.SLAYER_ICY_WATER, "Ice cooler", 26))))
					{
						final SlayerGearCheck.Result before = SlayerGearCheck.evaluate(task, 40, area, held, varbits);
						assertEquals(task, before, SlayerGearCheck.evaluate(task, 40, area, held, null, varbits, true));
						final SlayerGearCheck.Result seen = SlayerGearCheck.evaluate(task, 40, area, held, empty, varbits, true);
						if (before == null)
						{
							assertNull(task, seen);
							continue;
						}
						assertFalse(task, before.isBankSeen());
						assertTrue(task, seen.isBankSeen());
						assertEquals(task, before.getLine(), seen.getLine());
						assertEquals(task, before.getItems(), seen.getItems());
						assertEquals(task, before.getStatus(), seen.getStatus());
						assertEquals(task, before.isReady(), seen.isReady());
					}
				}
			}
		}
	}

	/** Every item the bank can name, alone in the bank, on every task: each compact item still fits on its line. */
	@Test
	public void everyCompactItemFromTheBankIsShort() throws Exception
	{
		final List<Integer> ids = new ArrayList<>(oneIdPerName().values());
		for (String task : everyKnownTask())
		{
			for (int area : new int[]{0, SlayerRequirements.KARUULM_AREA})
			{
				for (int id : ids)
				{
					final SlayerGearCheck.Result result = SlayerGearCheck.evaluate(task, 40, area, Collections.<Held>emptyList(),
						bank(id, 1000), HELMET_UNLOCKED, true);
					if (result == null) continue;
					for (String item : result.getItems())
					{
						assertTrue(task + ": " + item, item.length() <= SlayerGearCheck.MAX_ITEM);
					}
				}
			}
		}
	}

	/** An id for each name the item groups give, since items that share a name read the same. */
	private static java.util.Map<String, Integer> oneIdPerName() throws Exception
	{
		final java.util.Map<String, Integer> ids = new java.util.TreeMap<>();
		for (java.lang.reflect.Field field : SlayerRequirements.class.getDeclaredFields())
		{
			if (field.getType() != SlayerRequirements.ItemGroup.class) continue;
			field.setAccessible(true);
			final SlayerRequirements.ItemGroup group = (SlayerRequirements.ItemGroup) field.get(null);
			for (int id : group.ids())
			{
				ids.putIfAbsent(group.name(id), id);
			}
		}
		assertTrue(ids.size() > 100);
		return ids;
	}

	private static SlayerGearCheck.Result withBank(String task, int killsLeft, IntUnaryOperator varbits, BankGear bank, Held... items)
	{
		return SlayerGearCheck.evaluate(task, killsLeft, 0, Arrays.asList(items), bank, varbits, true);
	}

	/** A bank of these ids and quantities, read as the plugin reads an open bank. */
	private static BankGear bank(int... idsAndQuantities)
	{
		final Item[] items = new Item[idsAndQuantities.length / 2];
		for (int i = 0; i < items.length; i++)
		{
			items[i] = new Item(idsAndQuantities[2 * i], idsAndQuantities[2 * i + 1]);
		}
		return BankGear.of(items);
	}

	private static String check(String task, int killsLeft, int areaId, IntUnaryOperator varbits, Held... items)
	{
		return SlayerGearCheck.check(task, killsLeft, areaId, Arrays.asList(items), varbits);
	}

	private static Held held(int id, String name)
	{
		return held(id, name, 1);
	}

	private static Held held(int id, String name, int quantity)
	{
		return new Held(id, name, quantity);
	}

	private static Held fungicide()
	{
		return held(ItemID.SLAYER_FUNGICIDE, "Fungicide");
	}

	private static IntUnaryOperator set(int... varbits)
	{
		return varbit ->
		{
			for (int set : varbits)
			{
				if (set == varbit) return 1;
			}
			return 0;
		};
	}
}
