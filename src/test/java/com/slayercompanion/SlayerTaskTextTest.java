package com.slayercompanion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.slayercompanion.data.SlayerTask;
import com.slayercompanion.data.TaskRating;
import java.util.List;
import java.util.function.Function;
import net.runelite.api.gameval.DBTableID;
import org.junit.Test;

/** The dossier's computed Verdict and Assigned by lines. */
public class SlayerTaskTextTest
{
	private static final SlayerDataset DATASET = new SlayerDataset(new Gson());
	private static final SlayerGameData DATA = SlayerMasterRankerTest.DATA;

	private static SlayerTask task(TaskRating rating, String note)
	{
		final SlayerTask task = new SlayerTask();
		task.setTask("Test");
		task.setRating(rating);
		task.setNote(note);
		return task;
	}

	@Test
	public void theVerdictJudgesTheGoalFirstInWords()
	{
		final SlayerTask abyssal = task(SlayerPoolTest.rating(4, 3, 3), null);
		assertEquals("Good for XP. Fair for profit. Takes an average time.", SlayerTaskText.verdict(abyssal, SlayerGoal.XP, false));
		assertEquals("Fair for profit. Good for XP. Takes an average time.", SlayerTaskText.verdict(abyssal, SlayerGoal.PROFIT, false));
		assertEquals("Takes an average time, so fair for Slayer points. Good for XP. Fair for profit.",
				SlayerTaskText.verdict(abyssal, SlayerGoal.POINTS, false));
		final SlayerTask quick = task(SlayerPoolTest.rating(1, 5, 5), null);
		assertEquals("Very quick to finish, so excellent for Slayer points. Very poor for XP. Excellent for profit.",
				SlayerTaskText.verdict(quick, SlayerGoal.POINTS, false));
	}

	@Test
	public void theVerdictCitesTheWikiWhereItGivesXpAdvice()
	{
		// An 80k task the wiki says to block for XP is not called excellent or poor: the XP part cites the guide
		final TaskRating barraged = SlayerPoolTest.rating(5, 3, 3);
		barraged.setXpAdvice(TaskRating.XpAdvice.SKIP);
		assertEquals("The wiki's Slayer guide says to skip or block it for XP. Fair for profit. Takes an average time.",
				SlayerTaskText.verdict(task(barraged, null), SlayerGoal.XP, false));
		assertEquals("Fair for profit. The wiki's Slayer guide says to skip or block it for XP. Takes an average time.",
				SlayerTaskText.verdict(task(barraged, null), SlayerGoal.PROFIT, false));
		final TaskRating trolls = SlayerPoolTest.rating(3, 2, 3);
		trolls.setXpAdvice(TaskRating.XpAdvice.DO);
		assertEquals("The wiki's Slayer guide says to do it for XP. Poor for profit. Takes an average time.",
				SlayerTaskText.verdict(task(trolls, null), SlayerGoal.XP, false));
		final TaskRating warped = SlayerPoolTest.rating(4, 1, 3);
		warped.setXpAdvice(TaskRating.XpAdvice.DONT_UNLOCK);
		assertTrue(SlayerTaskText.verdict(task(warped, null), SlayerGoal.XP, false).startsWith("The wiki's Slayer guide says not to unlock it"));
		// The guide covers the regular masters, not Krystilia's Wilderness tasks
		assertEquals("Excellent for XP. Fair for profit. Takes an average time.", SlayerTaskText.verdict(task(barraged, null), SlayerGoal.XP, true));
		// The dossier's own entries carry it
		assertTrue(SlayerTaskText.verdict(DATASET.lookup("Abyssal Demons"), SlayerGoal.XP, false)
				.startsWith("The wiki's Slayer guide says to skip or block it for XP."));
		assertTrue(SlayerTaskText.verdict(DATASET.lookup("Nechryael"), SlayerGoal.XP, false)
				.startsWith("The wiki's Slayer guide says to do it for XP."));
	}

	@Test
	public void underSlayerPointsAFillerTaskIsJudgedByItsFillerTime()
	{
		final SlayerTask bugs = DATASET.lookup("Cave Bugs");
		final SlayerTask cows = DATASET.lookup("Cows");
		// From Aya at Turael's spot, the filler time speaks in words, never minutes, as the routine's block advice does
		assertEquals("One of the slower filler tasks. Very poor for XP. Very poor for profit.",
				SlayerTaskText.verdict(bugs, SlayerGoal.POINTS, SlayerAssigner.TURAEL));
		assertEquals("A quick filler task. Very poor for XP. Very poor for profit.",
				SlayerTaskText.verdict(cows, SlayerGoal.POINTS, SlayerAssigner.TURAEL));
		assertEquals("A filler task of average length. Very poor for XP. Very poor for profit.",
				SlayerTaskText.verdict(DATASET.lookup("Banshees"), SlayerGoal.POINTS, SlayerAssigner.SPRIA));
		// From another master, with none known, or for another goal, today's wording
		final String general = "Very quick to finish, so excellent for Slayer points. Very poor for XP. Very poor for profit.";
		assertEquals(general, SlayerTaskText.verdict(bugs, SlayerGoal.POINTS, SlayerAssigner.MAZCHNA));
		assertEquals(general, SlayerTaskText.verdict(cows, SlayerGoal.POINTS, (SlayerAssigner) null));
		for (SlayerGoal goal : new SlayerGoal[]{SlayerGoal.XP, SlayerGoal.PROFIT})
		{
			for (SlayerTask task : new SlayerTask[]{bugs, cows})
			{
				assertEquals(SlayerTaskText.verdict(task, goal, false), SlayerTaskText.verdict(task, goal, SlayerAssigner.TURAEL));
			}
		}
		// The words follow the cut points next to the scale
		final TaskRating.Filler filler = new TaskRating.Filler();
		filler.setMinutes(TaskRating.Filler.QUICK);
		assertEquals("A quick filler task.", SlayerTaskText.filler(filler));
		filler.setMinutes(TaskRating.Filler.QUICK + 1);
		assertEquals("A filler task of average length.", SlayerTaskText.filler(filler));
		filler.setMinutes(TaskRating.Filler.SLOW);
		assertEquals("One of the slower filler tasks.", SlayerTaskText.filler(filler));
	}

	@Test
	public void theNoteFollowsAndKrystiliasVersionHasItsOwnRatings()
	{
		final TaskRating usual = SlayerPoolTest.rating(5, 2, 4);
		usual.setWilderness(SlayerPoolTest.rating(2, 1, 1));
		final SlayerTask bloodveld = task(usual, "Mutated bloodveld count too.");
		assertEquals("Excellent for XP. Poor for profit. Quick to finish.\nMutated bloodveld count too.",
				SlayerTaskText.verdict(bloodveld, SlayerGoal.XP, false));
		assertEquals("In the Wilderness: poor for XP. Very poor for profit. Very slow to finish.\nMutated bloodveld count too.",
				SlayerTaskText.verdict(bloodveld, SlayerGoal.XP, true));
		// Unrated, e.g. a boss of its own: the note alone
		assertEquals("Not rated yet.\nCounts on a hellhound task or a boss task.",
				SlayerTaskText.verdict(task(null, "Counts on a hellhound task or a boss task."), SlayerGoal.PROFIT, false));
		assertNull(SlayerTaskText.verdict(null, SlayerGoal.XP, false));
	}

	/** The verdict never shows a rating as a number, for any task in the dossier and any goal. */
	@Test
	public void verdictsUseWordsAndPlainPunctuation()
	{
		final SlayerGameData data = DATA;
		for (int row : SlayerCacheFixture.get().rows(DBTableID.SlayerTask.ID))
		{
			final String name = data.taskName(row);
			final SlayerTask task = name == null ? null : DATASET.lookup(name);
			if (task == null) continue;
			for (SlayerGoal goal : SlayerGoal.values())
			{
				for (boolean wilderness : new boolean[]{false, true})
				{
					final String line = SlayerTaskText.verdict(task, goal, wilderness);
					final String judged = line.split("\n")[0];
					assertFalse(line, judged.matches(".*[0-9].*"));
				}
			}
		}
	}

	// ---- the compact sidebar ----

	@Test
	public void theCompactVerdictIsTheGoalsPartInAFewWords()
	{
		final SlayerTask abyssal = task(SlayerPoolTest.rating(4, 3, 3), "A note that stays in the tooltip.");
		assertEquals("Good for XP", SlayerTaskText.compactVerdict(abyssal, SlayerGoal.XP, SlayerAssigner.KONAR));
		assertEquals("Fair for profit", SlayerTaskText.compactVerdict(abyssal, SlayerGoal.PROFIT, SlayerAssigner.KONAR));
		assertEquals("Takes an average time", SlayerTaskText.compactVerdict(abyssal, SlayerGoal.POINTS, null));
		// The wiki's advice for XP, as the full line cites it
		final TaskRating barraged = SlayerPoolTest.rating(5, 3, 3);
		barraged.setXpAdvice(TaskRating.XpAdvice.SKIP);
		assertEquals("Wiki: skip or block for XP", SlayerTaskText.compactVerdict(task(barraged, null), SlayerGoal.XP, SlayerAssigner.DURADEL));
		final TaskRating trolls = SlayerPoolTest.rating(3, 2, 3);
		trolls.setXpAdvice(TaskRating.XpAdvice.DO);
		assertEquals("Wiki: do it for XP", SlayerTaskText.compactVerdict(task(trolls, null), SlayerGoal.XP, null));
		final TaskRating warped = SlayerPoolTest.rating(4, 1, 3);
		warped.setXpAdvice(TaskRating.XpAdvice.DONT_UNLOCK);
		assertEquals("Wiki: do not unlock for XP", SlayerTaskText.compactVerdict(task(warped, null), SlayerGoal.XP, null));
		// Krystilia's Wilderness version by its own ratings, never the wiki's advice
		final TaskRating usual = SlayerPoolTest.rating(5, 2, 4);
		usual.setWilderness(SlayerPoolTest.rating(2, 1, 1));
		assertEquals("Wilderness: poor for XP", SlayerTaskText.compactVerdict(task(usual, null), SlayerGoal.XP, SlayerAssigner.KRYSTILIA));
		assertEquals("Excellent for XP", SlayerTaskText.compactVerdict(task(barraged, null), SlayerGoal.XP, SlayerAssigner.KRYSTILIA));
		// A filler task under Slayer points, by its filler time
		assertEquals("Slow filler", SlayerTaskText.compactVerdict(DATASET.lookup("Cave Bugs"), SlayerGoal.POINTS, SlayerAssigner.TURAEL));
		assertEquals("Quick filler", SlayerTaskText.compactVerdict(DATASET.lookup("Cows"), SlayerGoal.POINTS, SlayerAssigner.TURAEL));
		assertEquals("Average filler", SlayerTaskText.compactVerdict(DATASET.lookup("Banshees"), SlayerGoal.POINTS, SlayerAssigner.SPRIA));
		assertEquals("Not rated yet", SlayerTaskText.compactVerdict(task(null, "Counts on a hellhound task."), SlayerGoal.PROFIT, null));
		assertNull(SlayerTaskText.compactVerdict(null, SlayerGoal.XP, null));
	}

	@Test
	public void aDossierFieldShowsOnlyWhenAFewWordsSayIt()
	{
		assertEquals("Pray melee", SlayerTaskText.prayerItem("Protect from Melee (nullifies their attacks)"));
		assertEquals("Pray missiles", SlayerTaskText.prayerItem("Protect from Missiles (blocks normal ranged attack)"));
		assertEquals("Pray magic", SlayerTaskText.prayerItem("Protect from Magic."));
		assertEquals("No prayer needed", SlayerTaskText.prayerItem("None strictly needed (low damage); Protect from Magic optional."));
		// Two prayers, or advice that is not a prayer, take a sentence
		assertNull(SlayerTaskText.prayerItem("Protect from Melee at Sulphur or Protect from Magic at Frost; step away from marked specials"));
		assertNull(SlayerTaskText.prayerItem("Protect from Magic or Missiles; prayer does not eliminate all damage."));
		assertNull(SlayerTaskText.prayerItem("varies (Protect from Missiles at Lighthouse, Protect from Melee at Jormungand)"));
		assertNull(SlayerTaskText.prayerItem(null));

		assertEquals("Weak: demonbane", SlayerTaskText.weaknessItem("Demonbane weapons receive their usual bonuses."));
		assertEquals("Weak: crush", SlayerTaskText.weaknessItem("Very low crush Defence; resistant to slash/stab and many ranged setups."));
		assertEquals("Weak: dragonbane", SlayerTaskText.weaknessItem("Draconic, so dragonbane weapons receive their normal bonus."));
		// Spells are the elemental item's, so the weakness names only weapons and melee styles
		assertEquals("Weak: demonbane",
				SlayerTaskText.weaknessItem("Water spells: 50% elemental weakness. Demonbane weapons also receive their usual bonuses."));
		assertEquals("Weak: crush", SlayerTaskText.weaknessItem("Crush attacks and Earth spells are their most effective options."));
		assertNull(SlayerTaskText.weaknessItem("Fire spells exploit their elemental weakness."));
		assertNull(SlayerTaskText.weaknessItem("No special weakness needed."));
		assertNull(SlayerTaskText.weaknessItem("Normal spiders need no special weapon; several bosses favour crush."));

		assertEquals("Style: melee or ranged", SlayerTaskText.styleItem("melee or ranged"));
		assertEquals("Style: magic", SlayerTaskText.styleItem("Magic."));
		assertNull(SlayerTaskText.styleItem("melee (Slayer Tower) or barrage (Catacombs)"));
		assertNull(SlayerTaskText.styleItem("barrage/burst (magic) in multi; melee viable single"));
		assertEquals("Attacks: melee (slash)", SlayerTaskText.attacksItem("melee (slash)"));
		assertNull("too long for one line", SlayerTaskText.attacksItem("melee (magical melee, max 9)"));
		assertNull(SlayerTaskText.attacksItem("mixed - melee and magic/ranged"));

		// The dossier's style, lower case at the start, and the kill style only for an entry without one
		assertEquals("Style: melee or magic (barrage)", SlayerTaskText.styleItem("Melee or magic (barrage)",
				"melee (Slayer Tower) or barrage (Catacombs)"));
		assertEquals("Style: any (demonbane)", SlayerTaskText.styleItem("Any (demonbane)", "melee or ranged"));
		assertEquals("Style: melee or ranged", SlayerTaskText.styleItem("", "melee or ranged"));
		assertEquals("Style: magic", SlayerTaskText.styleItem(null, "Magic."));
		assertNull(SlayerTaskText.styleItem(" ", "melee (Slayer Tower) or barrage (Catacombs)"));

		// The elemental weakness on a line, and when it names the monsters each element is for, too long for one
		assertEquals("Elemental: water 40%", SlayerTaskText.elementalItem("Water 40%"));
		assertEquals("Elemental: air 30 to 50%", SlayerTaskText.elementalItem("Air 30 to 50%"));
		assertEquals("Elemental: fire 30% (Callisto)", SlayerTaskText.elementalItem("Fire 30% (Callisto)"));
		assertEquals(SlayerTaskText.ELEMENTAL_VARIES, SlayerTaskText.elementalItem("Fire 50% (ice trolls), Earth 50% (mountain trolls)"));
		assertEquals("Elemental: varies by type", SlayerTaskText.ELEMENTAL_VARIES);
		assertNull("the monsters have none", SlayerTaskText.elementalItem(""));
		assertNull("not known", SlayerTaskText.elementalItem(null));

		assertEquals("Catacombs of Kourend", SlayerTaskText.placeItem("Catacombs of Kourend (multi, barrage) for XP/points, or Stronghold"
				+ " Slayer Cave (single, cannon)."));
		assertEquals("Giant bats in Taverley Dungeon", SlayerTaskText.placeItem("Giant bats in Taverley Dungeon or near Keep Le Faye."));
		assertNull(SlayerTaskText.placeItem("Use the task location named by Konar/Krystilia if one was assigned; otherwise check the world map."));
	}

	@Test
	public void cannonAndCombatInAFewWords()
	{
		// A place's facts
		assertEquals("No cannon, multi", SlayerTaskText.cannonItem("Not allowed.", "Multicombat"));
		assertEquals("Cannon, single", SlayerTaskText.cannonItem("Allowed. Only in the multi rooms.", "Single combat"));
		assertEquals("No cannon, partly multi", SlayerTaskText.cannonItem("Not allowed.", "Mixed. Some parts are multicombat."));
		// The dossier's plain words
		assertEquals("No cannon, single", SlayerTaskText.cannonItem("banned", "single"));
		assertEquals("Cannon", SlayerTaskText.cannonItem("great", "both"));
		assertEquals("Multicombat", SlayerTaskText.cannonItem("location-dependent", "multi"));
		assertEquals("Partly multicombat", SlayerTaskText.cannonItem(null, "Mixed. Some parts are multicombat."));
		assertEquals("No cannon", SlayerTaskText.cannonItem("not usable in the boss arena", "solo/boss encounter"));
		assertEquals("No cannon, single", SlayerTaskText.cannonItem("banned (the Fremennik Slayer Dungeon does not permit cannons)", "single"));
		assertEquals("Cannon", SlayerTaskText.cannonItem("great but dangerous in the Sophanem multi room", "both"));
		// A line that praises one place and bans another, or only advises, takes a sentence
		assertNull(SlayerTaskText.cannonItem("great in Stronghold Slayer Cave; banned in Catacombs of Kourend", "both"));
		assertNull(SlayerTaskText.cannonItem("not worth setting up", null));
	}

	/** The fight items of a few dossier entries, as the compact sidebar shows them under FIGHT. */
	@Test
	public void theFightItemsOfDossierEntries()
	{
		final SlayerTask demons = DATASET.lookup("Black demons");
		assertEquals("Style: any (demonbane)", SlayerTaskText.styleItem(demons.getStyle(), demons.getKillStyle()));
		assertEquals("Weak: demonbane", SlayerTaskText.weaknessItem(demons.getWeakness()));
		assertEquals("Elemental: water 40%", SlayerTaskText.elementalItem(demons.getElementalWeakness()));
		final SlayerTask giants = DATASET.lookup("Fire giants");
		assertEquals("Style: melee or magic (burst)", SlayerTaskText.styleItem(giants.getStyle(), giants.getKillStyle()));
		assertNull(SlayerTaskText.weaknessItem(giants.getWeakness()));
		assertEquals("Elemental: water 100%", SlayerTaskText.elementalItem(giants.getElementalWeakness()));
		// Ice trolls and one mountain troll are weak to fire, the other mountain troll to earth
		final SlayerTask trolls = DATASET.lookup("Trolls");
		assertEquals("Style: melee or ranged", SlayerTaskText.styleItem(trolls.getStyle(), trolls.getKillStyle()));
		assertEquals(SlayerTaskText.ELEMENTAL_VARIES, SlayerTaskText.elementalItem(trolls.getElementalWeakness()));
		assertEquals("Fire 35 to 100% (level 69 mountain trolls, ice trolls), Earth 50% (level 71 mountain trolls)",
				trolls.getElementalWeakness());
		final SlayerTask vorkath = DATASET.lookup("Vorkath");
		assertEquals("Weak: dragonbane", SlayerTaskText.weaknessItem(vorkath.getWeakness()));
		// None for abyssal demons, so no item
		final SlayerTask abyssal = DATASET.lookup("Abyssal demons");
		assertEquals("Style: melee or magic (barrage)", SlayerTaskText.styleItem(abyssal.getStyle(), abyssal.getKillStyle()));
		assertEquals("", abyssal.getElementalWeakness());
		assertNull(SlayerTaskText.elementalItem(abyssal.getElementalWeakness()));
	}

	/** Every compact item from the dossier fits on a line and uses plain punctuation, for every task, goal and master. */
	@Test
	public void compactDossierItemsAreShortAndPlain()
	{
		final SlayerGameData data = DATA;
		for (int row : SlayerCacheFixture.get().rows(DBTableID.SlayerTask.ID))
		{
			final String name = data.taskName(row);
			final SlayerTask task = name == null ? null : DATASET.lookup(name);
			if (task == null) continue;
			final List<String> items = new java.util.ArrayList<>(java.util.Arrays.asList(SlayerTaskText.prayerItem(task.getProtectionPrayer()),
					SlayerTaskText.weaknessItem(task.getWeakness()), SlayerTaskText.styleItem(task.getKillStyle()),
					SlayerTaskText.styleItem(task.getStyle(), task.getKillStyle()), SlayerTaskText.elementalItem(task.getElementalWeakness()),
					SlayerTaskText.attacksItem(task.getMonsterAttackStyle()), SlayerTaskText.placeItem(task.getBestLocation()), SlayerTaskText.cannonItem(task.getCannon(), task.getMultiOrSingle())));
			// The style and the elemental weakness show for every task the cache knows, the elemental one when it has one
			assertTrue(name, SlayerTaskText.styleItem(task.getStyle(), task.getKillStyle()) != null);
			assertEquals(name, task.getElementalWeakness().isEmpty(), SlayerTaskText.elementalItem(task.getElementalWeakness()) == null);
			for (SlayerGoal goal : SlayerGoal.values())
			{
				for (SlayerAssigner master : new SlayerAssigner[]{null, SlayerAssigner.TURAEL, SlayerAssigner.KONAR, SlayerAssigner.KRYSTILIA})
				{
					items.add(SlayerTaskText.compactVerdict(task, goal, master));
				}
			}
			for (String item : items)
			{
				if (item == null) continue;
				assertTrue(name + ": " + item, item.length() <= SlayerTaskText.MAX_ITEM);
			}
		}
	}

	private static final Function<SlayerAssigner, String> NPC = SlayerAssigner::getDisplayName;

	@Test
	public void assignedByComesFromTheCache()
	{
		assertEquals("Weight at each master: Vannaka 5, Chaeldar 12, Konar 9, Nieve 9, Duradel 12, Krystilia 5 and Mortimer 8."
						+ " Krystilia needs I Wildy More Slayer first. Needs 85 Slayer and combat level 85. Krystilia's version is in the Wilderness.",
				SlayerTaskText.assignedBy(DATA, DBTableID.SlayerTask.Row.SLAYER_TARGET_ABYSSALDEMONS, NPC));
		// The NPC the player finds there, and a choice of stats last
		final List<SlayerMasterAccess.Option> maxed = SlayerMasterAccess.options(SlayerMasterAccessTest.maxed());
		assertEquals("Weight at each master: Vannaka 8, Chaeldar 12, Steve 6, Kuradal 7 and Krystilia 6. Needs 63 Slayer and combat"
						+ " level 60, and 60 Strength or 60 Agility. Krystilia's version is in the Wilderness.",
				SlayerTaskText.assignedBy(DATA, DBTableID.SlayerTask.Row.SLAYER_TARGET_SPIRITUALGWD,
						a -> SlayerMasterAccess.find(maxed, a).getNpc()));
		assertTrue(SlayerTaskText.assignedBy(DATA, DBTableID.SlayerTask.Row.SLAYER_TARGET_BASILISKS, NPC)
				.contains(" Konar, Nieve and Duradel need Basilocked first."));
		// A task no master gives on its own keeps the dossier's text
		assertNull(SlayerTaskText.assignedBy(DATA, DBTableID.SlayerTask.Row.SLAYER_TARGET_IRONDRAGONS, NPC));
		assertNull(SlayerTaskText.assignedBy(null, DBTableID.SlayerTask.Row.SLAYER_TARGET_ABYSSALDEMONS, NPC));
		assertNull(SlayerTaskText.assignedBy(DATA, -1, NPC));
	}

	/** The dossier's own Assigned by text is the fallback, so it carries the cache's weights in the same form. */
	@Test
	public void theFallbackTextMatchesTheCache()
	{
		final SlayerTask abyssal = DATASET.lookup("Abyssal demons");
		assertEquals(SlayerTaskText.assignedBy(DATA, DBTableID.SlayerTask.Row.SLAYER_TARGET_ABYSSALDEMONS, a ->
		{
			switch (a)
			{
				case TURAEL:
					return "Turael/Aya";
				case MAZCHNA:
					return "Mazchna/Achtryn";
				case NIEVE:
					return "Nieve/Steve";
				case DURADEL:
					return "Duradel/Kuradal";
				default:
					return a.getDisplayName();
			}
		}), abyssal.getAssignedBy());
	}
}
