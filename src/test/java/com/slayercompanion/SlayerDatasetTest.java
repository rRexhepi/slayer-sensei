package com.slayercompanion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.slayercompanion.data.SlayerTask;
import com.slayercompanion.data.TaskRating;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.runelite.api.gameval.DBTableID;
import org.junit.Test;

public class SlayerDatasetTest
{
	private final SlayerDataset dataset = new SlayerDataset(new Gson());

	@Test
	public void aliasesAreExplicitAndDeterministic()
	{
		assertEquals("Hydras (regular Hydra + Alchemical Hydra)", dataset.lookup("Hydra").getTask());
		assertEquals("The Maggot King", dataset.lookup("Maggot King").getTask());
		assertEquals("Nechryael", dataset.lookup("Nechryael").getTask());
		assertEquals("Vampyres / Vyrewatch", dataset.lookup("Vyrewatch").getTask());
		assertNull(dataset.lookup("Nechryael-ish unrelated text"));
	}

	@Test
	public void fireGiantsUseRuneLiteVerifiedCatacombsEntrance()
	{
		final SlayerTask task = dataset.lookup("Fire giants");
		final TaskDestination destination = TaskDestination.forTask(task);
		assertNotNull(destination);
		assertEquals("Catacombs of Kourend", destination.getLabel());
		assertEquals(1636, destination.getPoint().getX());
		assertEquals(3673, destination.getPoint().getY());
	}

	@Test
	public void bloodveldShowsVariantSafeWeaknessAdvice()
	{
		final SlayerTask task = dataset.lookup("Bloodveld");
		assertNotNull(task);
		assertNotNull(task.getWeakness());
		assertTrue(task.getWeakness().contains("Demonbane"));
		assertTrue(task.getWeakness().contains("no elemental weakness"));
	}

	@Test
	public void turothHasDossierAliasesAndMandatoryWeaponAdvice()
	{
		final SlayerTask task = dataset.lookup("Turoth");
		assertNotNull(task);
		assertEquals(task, dataset.lookup("Turoths"));
		assertTrue(task.getRequiredItems().contains("abyssal dagger"));
		assertTrue(task.getCannon().contains("banned"));
		// The gear check takes the weapons themselves (SlayerGearCheckTest)
		assertNotNull(SlayerRequirements.forTask("Turoth"));
	}

	@Test
	public void blackDemonGuideSeparatesCannonLocationsFromCatacombs()
	{
		final SlayerTask task = dataset.lookup("Black demons");
		assertNotNull(task);
		assertTrue(task.getBestLocation().startsWith("Chasm of Fire"));
		assertTrue(task.getBestLocation().contains("does not allow cannons"));
		assertTrue(task.getKillStyle().contains("Catacombs (no cannon)"));
		assertTrue(task.getCannon().contains("banned in Catacombs of Kourend"));
		assertEquals("Demonic gorillas and Skotizo also count toward the task.", task.getNote());

		final TaskDestination destination = TaskDestination.forTask(task);
		assertNotNull(destination);
		assertEquals("Chasm of Fire", destination.getLabel());
	}

	@Test
	public void everyCatacombsGuideExplicitlySaysCannonsAreBanned() throws Exception
	{
		final Class<?> taskClass = Class.forName("net.runelite.client.plugins.slayer.Task");
		final Method valuesMethod = taskClass.getDeclaredMethod("values");
		final Method getNameMethod = taskClass.getDeclaredMethod("getName");
		valuesMethod.setAccessible(true);
		getNameMethod.setAccessible(true);

		for (Object runeLiteTask : (Object[]) valuesMethod.invoke(null))
		{
			final String name = (String) getNameMethod.invoke(runeLiteTask);
			final SlayerTask guide = dataset.lookup(name);
			if (guide != null && guide.getBestLocation().toLowerCase().contains("catacombs of kourend"))
			{
				final String cannon = guide.getCannon().toLowerCase();
				assertTrue(name + " must explicitly forbid a Catacombs cannon: " + guide.getCannon(),
					cannon.contains("catacombs of kourend") && cannon.contains("banned"));
			}
		}
	}

	@Test
	public void hellhoundsAndJelliesUseTheirActualCannonLocations()
	{
		final SlayerTask hellhounds = dataset.lookup("Hellhounds");
		assertTrue(hellhounds.getBestLocation().startsWith("Stronghold Slayer Cave"));
		assertTrue(hellhounds.getCannon().startsWith("great in Stronghold Slayer Cave"));
		assertTrue(hellhounds.getCannon().contains("banned in Catacombs"));

		final SlayerTask jellies = dataset.lookup("Jellies");
		assertTrue(jellies.getCannon().contains("Wilderness Slayer Cave"));
		assertTrue(jellies.getCannon().contains("banned in Catacombs"));
		assertTrue(jellies.getCannon().contains("Fremennik Slayer Dungeon"));
	}

	@Test
	public void everyTaskInThisRuneLiteBuildHasAnExplicitCompleteDossier() throws Exception
	{
		final Class<?> taskClass = Class.forName("net.runelite.client.plugins.slayer.Task");
		final Method valuesMethod = taskClass.getDeclaredMethod("values");
		final Method getNameMethod = taskClass.getDeclaredMethod("getName");
		valuesMethod.setAccessible(true);
		getNameMethod.setAccessible(true);

		final Object[] runeLiteTasks = (Object[]) valuesMethod.invoke(null);
		final List<String> missing = new ArrayList<>();
		final List<String> incomplete = new ArrayList<>();
		for (Object runeLiteTask : runeLiteTasks)
		{
			final String name = (String) getNameMethod.invoke(runeLiteTask);
			final SlayerTask guide = dataset.lookup(name); // strict: does not use the future-task fallback
			if (guide == null)
			{
				missing.add(name);
			}
			else if (!isComplete(guide))
			{
				incomplete.add(name);
			}
		}

		assertEquals("RuneLite task enum changed; re-audit dossier coverage", 152, runeLiteTasks.length);
		assertTrue("Missing explicit Slayer dossiers: " + missing, missing.isEmpty());
		assertTrue("Incomplete Slayer dossiers: " + incomplete, incomplete.isEmpty());
	}

	@Test
	public void aTaskWithoutADossierGetsGeneralAdvice()
	{
		assertNull(dataset.lookup("A future beast"));
		final SlayerTask fallback = dataset.lookupOrFallback("A future beast");
		assertNotNull(fallback);
		assertEquals(SlayerDataset.FALLBACK_LABEL, dataset.dataLabel(fallback));
		assertEquals(SlayerDataset.DATA_LABEL, dataset.dataLabel(dataset.lookup("Abyssal demons")));
		assertTrue(fallback.getRequiredItems().contains("Check the Slayer master's tip"));
		// The compact sidebar's place for it says so, instead of a piece of advice cut short
		assertEquals("Not in the dossier yet", SlayerTaskText.placeItem(fallback.getBestLocation()));
	}

	@Test
	public void turaelsAndSpriasTasksHaveAFillerTime()
	{
		// The routine's filler tasks are timed by the wiki's Turael task completion tips, not by other masters' amounts
		final SlayerGameData data = SlayerGameData.load(SlayerCacheFixture.get());
		final Set<String> fillers = new TreeSet<>();
		for (SlayerAssigner master : Arrays.asList(SlayerAssigner.TURAEL, SlayerAssigner.SPRIA))
		{
			for (SlayerGameData.PoolEntry e : data.pool(master.getId())) fillers.add(data.taskName(e.getTaskRow()));
		}
		assertEquals(25, fillers.size());
		for (String name : fillers)
		{
			final TaskRating.Filler filler = dataset.lookup(name).getRating().getFiller();
			assertNotNull(name, filler);
			assertTrue(name, filler.getMinutes() >= 7 && filler.getMinutes() <= 12);
			assertTrue(name + ": " + filler.getBasis(), filler.getBasis().startsWith("Judgment"));
			assertTrue(name + ": " + filler.getBasis(), filler.getBasis().contains("Turael task completion tips"));
		}
		// The awkward ones the table names take longest, the cannonable ones with a close teleport least
		assertEquals(12, dataset.lookup("Cave Bugs").getRating().getFiller().getMinutes());
		assertEquals(7, dataset.lookup("Cows").getRating().getFiller().getMinutes());
		// Tasks only other masters give have none
		assertNull(dataset.lookup("Abyssal Demons").getRating().getFiller());
		assertNull(dataset.lookup("Kurask").getRating().getFiller());
	}

	@Test
	public void theWikiDiscouragesExtendingMetalDragons()
	{
		assertTrue(dataset.lookup("Metal Dragons").getRating().isExtensionDiscouraged());
		assertFalse(dataset.lookup("Araxytes").getRating().isExtensionDiscouraged());
	}

	@Test
	public void everyTaskInEveryMastersPoolHasAllThreeRatings()
	{
		final SlayerCacheFixture fixture = SlayerCacheFixture.get();
		final SlayerGameData data = SlayerGameData.load(fixture);
		final Set<String> names = new TreeSet<>();
		for (int row : fixture.table(DBTableID.SlayerMasterTask.ID).rows)
		{
			names.add(data.taskName(fixture.intValue(row, "COL_TASK")));
		}
		// A new pool task in a game update fails here until it is rated
		assertEquals(117, names.size());
		final List<String> unrated = new ArrayList<>();
		for (String name : names)
		{
			final SlayerTask task = dataset.lookup(name);
			if (task == null || !rated(task.getRating()) || (task.getRating().getWilderness() != null && !rated(task.getRating().getWilderness())))
			{
				unrated.add(name);
			}
		}
		assertTrue("Pool tasks without all three ratings: " + unrated, unrated.isEmpty());
		// Boss tasks have a dossier entry of their own for the rating
		assertEquals("Boss", dataset.lookup("Boss").getTask());
	}

	@Test
	public void ratingsFollowTheWikisFigures()
	{
		// The wiki's figures, spot checked: smoke devils barrage at 150k XP/h, aviansies 10k, frost dragons 2.4M gp/h
		assertEquals(5, dataset.lookup("Smoke Devils").getRating().getXp());
		assertEquals(1, dataset.lookup("Aviansies").getRating().getXp());
		assertEquals(5, dataset.lookup("Frost Dragons").getRating().getGp());
		// Krystilia's bloodveld are only in the Wilderness God Wars Dungeon, an extremely long task
		assertEquals(1, dataset.lookup("Bloodveld").getRating().getWilderness().getSpeed());
		assertEquals(4, dataset.lookup("Bloodveld").getRating().getSpeed());
	}

	/**
	 * The verdict is worked out from the ratings, so the notes keep facts only: no XP, profit or points opinions
	 * that could disagree with them.
	 */
	@Test
	public void notesCarryFactsNotOpinions() throws Exception
	{
		final Class<?> taskClass = Class.forName("net.runelite.client.plugins.slayer.Task");
		final Method valuesMethod = taskClass.getDeclaredMethod("values");
		final Method getNameMethod = taskClass.getDeclaredMethod("getName");
		valuesMethod.setAccessible(true);
		getNameMethod.setAccessible(true);
		final String opinion = "(?i).*\\b(do it|skip|extend|worth|good|great|excellent|poor|mediocre|top-tier|solid|recommended|"
				+ "keep it|never block|xp:|gp:|points:|money task|low value|high value|filler)\\b.*";
		for (Object runeLiteTask : (Object[]) valuesMethod.invoke(null))
		{
			final SlayerTask task = dataset.lookup((String) getNameMethod.invoke(runeLiteTask));
			if (task == null || task.getNote() == null) continue;
			final String note = task.getNote();
			assertFalse(task.getTask() + ": " + note, note.matches(opinion));
			assertTrue(task.getTask() + ": " + note, note.endsWith("."));
		}
		// A note that was only an opinion is left out. A note that states a fact stays
		assertNull(dataset.lookup("Aberrant spectres").getNote());
		assertNull(dataset.lookup("Fire giants").getNote());
		assertEquals("Kree'arra and his bodyguards count toward the task.", dataset.lookup("Aviansies").getNote());
		assertEquals("The Kraken boss counts toward the task.", dataset.lookup("Cave kraken").getNote());
	}

	/** The cache builds the Assigned by line. The dossier's own text is the fallback and has no placeholder left. */
	@Test
	public void assignedByFallbacksNameTheMasters()
	{
		final SlayerCacheFixture fixture = SlayerCacheFixture.get();
		final SlayerGameData data = SlayerGameData.load(fixture);
		for (int row : fixture.table(DBTableID.SlayerMasterTask.ID).rows)
		{
			final SlayerTask task = dataset.lookup(data.taskName(fixture.intValue(row, "COL_TASK")));
			assertFalse(task.getTask(), task.getAssignedBy().startsWith("Lower-level or Wilderness Slayer masters"));
			assertTrue(task.getTask() + ": " + task.getAssignedBy(), task.getAssignedBy().startsWith("Weight at each master: "));
		}
	}

	private static boolean rated(TaskRating r)
	{
		return r != null && r.isComplete() && present(r.getBasis());
	}

	private static boolean isComplete(SlayerTask task)
	{
		return present(task.getTask())
			&& present(task.getAssignedBy())
			&& present(task.getBestLocation())
			&& present(task.getMultiOrSingle())
			&& present(task.getKillStyle())
			&& present(task.getWeakness())
			&& present(task.getMonsterAttackStyle())
			&& present(task.getProtectionPrayer())
			&& present(task.getRequiredItems())
			&& present(task.getCannon())
			&& present(task.getNotableDrops());
	}

	private static boolean present(String value)
	{
		return value != null && !value.trim().isEmpty();
	}
}
