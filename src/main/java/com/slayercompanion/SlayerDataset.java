package com.slayercompanion;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.slayercompanion.data.SlayerTask;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Loads the bundled slayer task dataset and looks entries up by the task name the game gives, as
 * {@link SlayerTaskReader} reads it. Lookup is tolerant of singular/plural and composite names.
 */
@Singleton
@Slf4j
public class SlayerDataset
{
	/** The sidebar's Data line for a task with a dossier. */
	static final String DATA_LABEL = "Notes from the OSRS Wiki, checked 2026-10-04";
	/** The Data line for a task the dossier does not cover yet. */
	static final String FALLBACK_LABEL = "General advice, not for this task";

	private final Map<String, SlayerTask> byName = new HashMap<>();
	private final Map<String, SlayerTask> aliases = new HashMap<>();

	@Inject
	SlayerDataset(Gson gson)
	{
		load(gson, "/slayer_tasks.json");
		load(gson, "/slayer_tasks_supplement.json");
		registerVerifiedCannonGuidance();
		registerAliases();
		log.debug("slayer-sensei: loaded {} curated tasks", byName.size());
	}

	private void load(Gson gson, String resource)
	{
		try (InputStream in = SlayerDataset.class.getResourceAsStream(resource))
		{
			if (in != null)
			{
				final List<SlayerTask> tasks = gson.fromJson(
						new InputStreamReader(in, StandardCharsets.UTF_8),
						new TypeToken<List<SlayerTask>>()
						{
						}.getType());
				if (tasks != null)
				{
					for (SlayerTask t : tasks)
					{
						if (t.getTask() != null)
						{
							byName.put(normalize(t.getTask()), t);
						}
					}
				}
			}
		}
		catch (Exception e)
		{
			log.warn("slayer-sensei: failed to load {}", resource, e);
		}
	}

	public SlayerTask lookup(String taskName)
	{
		if (taskName == null || taskName.isEmpty())
		{
			return null;
		}
		final String n = normalize(taskName);
		final SlayerTask exact = byName.get(n);
		if (exact != null)
		{
			return exact;
		}
		return aliases.get(n);
	}

	/**
	 * Used for live assignments. A task the game adds later should still get safe, honest guidance
	 * instead of an empty panel while its detailed dossier is being added. Tests use {@link #lookup}
	 * directly so current-version coverage cannot be hidden by this fallback.
	 */
	public SlayerTask lookupOrFallback(String taskName)
	{
		final SlayerTask curated = lookup(taskName);
		return curated != null ? curated : genericFallback(taskName);
	}

	private static String normalize(String s)
	{
		return s.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ");
	}

	private void registerAliases()
	{
		for (Map.Entry<String, SlayerTask> entry : byName.entrySet())
		{
			final String key = entry.getKey();
			if (!key.contains(" ") || (!key.contains("(") && !key.contains("/")))
			{
				aliases.putIfAbsent(singular(key), entry.getValue());
			}
		}

		alias("cave kraken", "cave kraken kraken");
		alias("kraken", "cave kraken kraken");
		alias("hydras", "hydras regular hydra alchemical hydra");
		alias("hydra", "hydras regular hydra alchemical hydra");
		alias("lizardmen", "lizardmen incl brutes and shamans");
		alias("lizardman", "lizardmen incl brutes and shamans");
		alias("vampyres", "vampyres vyrewatch");
		alias("vyrewatch", "vampyres vyrewatch");
		alias("mutated zygomites", "zygomites");
		alias("maggot king", "the maggot king");
		alias("turoths", "turoth");
		alias("tzhaar", "tzhaar");
	}

	/**
	 * Cannon advice must name the location. In particular, the dark altar prevents a cannon from
	 * being placed anywhere in the Catacombs of Kourend even though most of it is multicombat.
	 */
	private void registerVerifiedCannonGuidance()
	{
		cannon("Aberrant spectres", "great in Stronghold Slayer Cave; banned in Catacombs of Kourend and Slayer Tower");
		cannon("Abyssal demons", "great in Wilderness Slayer Cave (PK risk); banned in Catacombs of Kourend, Slayer Tower and Abyssal Area");
		// The wiki's ankou and skeleton tables mark the Stronghold of Security cannonable, and its own page says so
		cannon("Ankou", "great in Stronghold Slayer Cave, Stronghold of Security or Wilderness Slayer Cave, banned in Catacombs of Kourend");
		cannon("Banshees", "banned in both listed locations: Catacombs of Kourend and Slayer Tower");
		cannon("Black demons", "great at Chasm of Fire or Taverley Dungeon; banned in Catacombs of Kourend");
		cannon("Black dragons", "usable in Taverley Dungeon's task area; banned in Catacombs of Kourend");
		cannon("Bloodveld", "great in Meiyerditch Laboratories or Stronghold Slayer Cave; banned in Catacombs of Kourend, Slayer Tower and God Wars Dungeon");
		cannon("Dagannoth", "great at Lighthouse or in the Dagannoth area of Jormungand's Prison; banned in Catacombs of Kourend");
		cannon("Dust devils", "great in Wilderness Slayer Cave (PK risk); banned in Catacombs of Kourend and the Smoke Dungeon task-only area");
		cannon("Fire giants", "good in Stronghold Slayer Cave or Waterfall Dungeon; banned in Catacombs of Kourend");
		cannon("Greater demons", "good in Chasm of Fire or Wilderness Slayer Cave; banned in Catacombs of Kourend");
		cannon("Hellhounds", "great in Stronghold Slayer Cave; banned in Catacombs of Kourend and Cerberus's Lair");
		cannon("Iron dragons", "usable in Brimhaven Dungeon; banned in Catacombs of Kourend");
		cannon("Jellies", "great in Wilderness Slayer Cave (PK risk); banned in Catacombs of Kourend and Fremennik Slayer Dungeon");
		cannon("Nechryael", "great in Wilderness Slayer Cave (PK risk); banned in Catacombs of Kourend and Slayer Tower");
		cannon("Nechryarch (superior of Nechryael)", "same restriction as its spawn: banned in Catacombs of Kourend and Slayer Tower; usable in Wilderness Slayer Cave");
		cannon("Red dragons", "usable in Brimhaven Dungeon; banned in Catacombs of Kourend");
		cannon("Steel dragons", "usable in Brimhaven Dungeon; banned in Catacombs of Kourend");
		cannon("Hill giants", "great in Giants' Den; banned in Catacombs of Kourend");
		cannon("Lesser demons", "good in Chasm of Fire or Wilderness Slayer Cave; banned in Catacombs of Kourend");
		cannon("Moss giants", "great in Giants' Den; banned in Catacombs of Kourend");
		cannon("Skeletons", "location-dependent, usable in Stronghold of Security, banned in Catacombs of Kourend");
	}

	private void cannon(String canonical, String value)
	{
		final SlayerTask task = byName.get(normalize(canonical));
		if (task != null)
		{
			task.setCannon(value);
		}
	}

	private static SlayerTask genericFallback(String taskName)
	{
		if (taskName == null || taskName.trim().isEmpty())
		{
			return null;
		}

		final SlayerTask task = new SlayerTask();
		task.setTask(taskName.trim());
		task.setAssignedBy("A task the dossier does not cover yet. Which masters give it depends on your levels, quests and unlocks.");
		task.setNote("This task is not in the dossier yet. Do not block it or spend points on it from this alone.");
		// The compact sidebar shows the words before the first full stop as the place
		task.setBestLocation("Not in the dossier yet. Go to the area Konar named, or into the Wilderness for Krystilia. For any other"
				+ " master, check the world map or the Slayer master's tip before you travel.");
		task.setMultiOrSingle("Unknown. Check the area before you bring a cannon or a multi-target setup.");
		task.setStyle("");
		task.setKillStyle("Check the gear and weapon the monster needs before you pick a setup.");
		task.setWeakness("Not known yet. Do not assume an elemental weakness.");
		// Null, since it is not known. Empty would say the monsters have none.
		task.setElementalWeakness(null);
		task.setMonsterAttackStyle("Unknown. Watch its first attack from a safe spot and look the monster up on the wiki before"
				+ " you rely on a prayer.");
		task.setProtectionPrayer("No verified prayer recommendation yet.");
		task.setRequiredItems("Check the Slayer master's tip before you attack. Some Slayer monsters need protective gear or a"
				+ " special weapon.");
		task.setCannon("Unknown. Check that the area allows a cannon.");
		task.setHasSuperior(false);
		task.setNotableDrops("Not listed yet. The Loot Tracker plugin shows what you get.");
		return task;
	}

	private void alias(String alias, String canonical)
	{
		final SlayerTask task = byName.get(normalize(canonical));
		if (task != null)
		{
			aliases.put(normalize(alias), task);
		}
	}

	private static String singular(String value)
	{
		if (value.endsWith("ies"))
		{
			return value.substring(0, value.length() - 3) + "y";
		}
		return value.endsWith("s") ? value.substring(0, value.length() - 1) : value;
	}

	/** The sidebar's Data line: where the notes come from, or that they are general advice for a task without a dossier. */
	public String dataLabel(SlayerTask task)
	{
		return task != null && lookup(task.getTask()) == null ? FALLBACK_LABEL : DATA_LABEL;
	}
}
