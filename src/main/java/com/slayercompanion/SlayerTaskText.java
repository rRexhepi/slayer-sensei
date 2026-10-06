package com.slayercompanion;

import com.slayercompanion.data.SlayerTask;
import com.slayercompanion.data.TaskRating;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.Skill;

/**
 * The dossier's "Verdict" and "Assigned by" lines, and the dossier's fields in a few words for the compact sidebar.
 * Pure, so the wording is tested without Swing.
 */
final class SlayerTaskText
{
	/** The longest compact item, about what fits on a line of the compact sidebar. A longer field is left to the tooltip. */
	static final int MAX_ITEM = 34;
	/** The longest start of the dossier's place the compact sidebar shows. */
	static final int SHORT_PLACE = 34;
	static final String SUPERIOR_ITEM = "Superior possible";
	private static final Pattern PRAYER = Pattern.compile("\\b(Melee|Missiles|Magic)\\b");
	private static final Pattern SPELLS = Pattern.compile("\\b(air|water|earth|fire) spells\\b");
	private static final Pattern ATTACK = Pattern.compile("\\b(?:weak(?:est)? to|low) (crush|slash|stab)\\b|\\b(crush|slash|stab) attacks\\b");
	/** Where the name of a place ends in the dossier's text. */
	private static final Pattern PLACE_END = Pattern.compile("[(,;.]| or | for ");

	private SlayerTaskText()
	{
	}

	/**
	 * The Verdict line: the task judged for the goal in words from its ratings, then the dossier's note. Krystilia's
	 * Wilderness version is judged by its own ratings. Ratings are never shown as numbers. Where the wiki's Slayer guide
	 * gives advice on the task for XP, the XP part cites it, except on Krystilia's Wilderness tasks, which it does not
	 * cover.
	 */
	static String verdict(SlayerTask task, SlayerGoal goal, boolean wilderness)
	{
		return verdict(task, goal, wilderness, false);
	}

	/**
	 * The Verdict line for the task in progress, given by {@code master}, or null for an unknown one. Under Slayer
	 * points a filler task from Turael's spot or Spria is judged by its filler time, as the routine's block advice is,
	 * rather than by its general speed.
	 */
	static String verdict(SlayerTask task, SlayerGoal goal, SlayerAssigner master)
	{
		final boolean wilderness = master != null && master.getStreak() == SlayerAssigner.Streak.WILDERNESS;
		return verdict(task, goal, wilderness, master == SlayerAssigner.TURAEL || master == SlayerAssigner.SPRIA);
	}

	private static String verdict(SlayerTask task, SlayerGoal goal, boolean wilderness, boolean fillerMaster)
	{
		if (task == null) return null;
		TaskRating rating = task.getRating();
		final boolean wild = wilderness && rating != null && rating.getWilderness() != null;
		if (wild) rating = rating.getWilderness();
		final String first = fillerMaster && goal == SlayerGoal.POINTS && rating != null && rating.getFiller() != null
				? filler(rating.getFiller()) : null;
		final String judged = judge(rating, goal, !wilderness, first);
		final String line = judged == null ? "Not rated yet." : (wild ? "In the Wilderness: " + lower(judged) : judged);
		final String note = task.getNote();
		return note == null || note.trim().isEmpty() ? line : line + "\n" + note.trim();
	}

	/**
	 * A filler task in words from its filler time, never the minutes: "A quick filler task.", "A filler task of average
	 * length." or "One of the slower filler tasks.", by the cut points next to the scale in {@link TaskRating.Filler}.
	 */
	static String filler(TaskRating.Filler filler)
	{
		if (filler.getMinutes() <= TaskRating.Filler.QUICK) return "A quick filler task.";
		if (filler.getMinutes() >= TaskRating.Filler.SLOW) return "One of the slower filler tasks.";
		return "A filler task of average length.";
	}

	/**
	 * "Good for XP. Fair for profit. Slow to finish." with the goal first. For Slayer points the speed leads, since
	 * points come per task. Null when the rating is missing or incomplete.
	 *
	 * @param wikiAdvice whether the XP part may cite the wiki's advice on the task instead of the rating
	 * @param pointsFirst for Slayer points, the first sentence in place of the speed, or null
	 */
	private static String judge(TaskRating rating, SlayerGoal goal, boolean wikiAdvice, String pointsFirst)
	{
		if (rating == null || !rating.isComplete()) return null;
		final String xp = wikiAdvice && rating.getXpAdvice() != null ? SlayerSpend.wikiSays(rating.getXpAdvice())
				: capitalize(SlayerSpend.quality(rating.getXp())) + " for XP.";
		final String gp = capitalize(SlayerSpend.quality(rating.getGp())) + " for profit.";
		final String speed = speed(rating.getSpeed());
		switch (goal)
		{
			case XP:
				return xp + " " + gp + " " + speed + ".";
			case PROFIT:
				return gp + " " + xp + " " + speed + ".";
			default:
				final String first = pointsFirst != null ? pointsFirst
						: speed + ", so " + SlayerSpend.quality(rating.getSpeed()) + " for Slayer points.";
				return first + " " + xp + " " + gp;
		}
	}

	private static String speed(int rating)
	{
		switch (rating)
		{
			case 5:
				return "Very quick to finish";
			case 4:
				return "Quick to finish";
			case 3:
				return "Takes an average time";
			case 2:
				return "Slow to finish";
			default:
				return "Very slow to finish";
		}
	}

	/**
	 * The Assigned by line from the cache: each master that gives the task with its weight, the unlocks some of
	 * them need, and the levels the task needs. Null when the cache has no master giving it, so the dossier's own
	 * text stays.
	 *
	 * @param npc the NPC the player finds at a master's spot, e.g. Kuradal
	 */
	static String assignedBy(SlayerGameData data, int taskRow, Function<SlayerAssigner, String> npc)
	{
		if (data == null || taskRow < 0) return null;
		final List<String> weights = new ArrayList<>();
		final Map<Integer, List<String>> byUnlock = new LinkedHashMap<>();
		boolean wilderness = false;
		for (SlayerAssigner master : SlayerAssigner.values())
		{
			for (SlayerGameData.PoolEntry e : data.pool(master.getId()))
			{
				if (e.getTaskRow() != taskRow) continue;
				weights.add(npc.apply(master) + " " + e.getWeight());
				if (e.getUnlockRow() >= 0) byUnlock.computeIfAbsent(e.getUnlockRow(), k -> new ArrayList<>()).add(npc.apply(master));
				wilderness |= master == SlayerAssigner.KRYSTILIA;
			}
		}
		if (weights.isEmpty()) return null;
		final StringBuilder sb = new StringBuilder("Weight at each master: ").append(SlayerSpend.join(weights)).append('.');
		for (Map.Entry<Integer, List<String>> u : byUnlock.entrySet())
		{
			final SlayerGameData.Unlock unlock = data.unlock(u.getKey());
			final String name = unlock == null || unlock.getName() == null ? "an unlock" : unlock.getName();
			sb.append(' ').append(SlayerSpend.join(u.getValue())).append(u.getValue().size() == 1 ? " needs " : " need ")
					.append(name).append(" first.");
		}
		final String needs = needs(data, taskRow);
		if (needs != null) sb.append(" Needs ").append(needs).append('.');
		if (wilderness) sb.append(" Krystilia's version is in the Wilderness.");
		return sb.toString();
	}

	/**
	 * "87 Slayer, 50 Magic and combat level 80", with a choice of stats last, as in "63 Slayer and combat level 60,
	 * and 60 Strength or 60 Agility". Null when the task needs nothing.
	 */
	private static String needs(SlayerGameData data, int taskRow)
	{
		final List<String> parts = new ArrayList<>();
		for (SlayerGameData.StatLevel s : data.statsAll(taskRow))
		{
			parts.add(s.getLevel() + " " + skill(s.getStat()));
		}
		if (data.combatLevel(taskRow) > 0) parts.add("combat level " + data.combatLevel(taskRow));
		final List<String> any = new ArrayList<>();
		for (SlayerGameData.StatLevel s : data.statsAny(taskRow))
		{
			any.add(s.getLevel() + " " + skill(s.getStat()));
		}
		final String either = String.join(" or ", any);
		if (parts.isEmpty()) return either.isEmpty() ? null : either;
		return SlayerSpend.join(parts) + (either.isEmpty() ? "" : ", and " + either);
	}

	private static String skill(int stat)
	{
		return stat >= 0 && stat < Skill.values().length ? Skill.values()[stat].getName() : "stat " + stat;
	}

	// ---- the compact sidebar ----

	/**
	 * The Verdict's part for the goal in a few words: "Good for XP", "Wiki: skip or block for XP", "Quick to finish" or for
	 * a filler task under Slayer points "Slow filler". The other goals and the note stay in {@link #verdict}.
	 */
	static String compactVerdict(SlayerTask task, SlayerGoal goal, SlayerAssigner master)
	{
		if (task == null) return null;
		final boolean wilderness = master != null && master.getStreak() == SlayerAssigner.Streak.WILDERNESS;
		TaskRating rating = task.getRating();
		final boolean wild = wilderness && rating != null && rating.getWilderness() != null;
		if (wild) rating = rating.getWilderness();
		if (rating == null || !rating.isComplete()) return "Not rated yet";
		final String judged;
		if (goal == SlayerGoal.POINTS && (master == SlayerAssigner.TURAEL || master == SlayerAssigner.SPRIA) && rating.getFiller() != null)
		{
			final int minutes = rating.getFiller().getMinutes();
			judged = minutes <= TaskRating.Filler.QUICK ? "Quick filler" : minutes >= TaskRating.Filler.SLOW ? "Slow filler" : "Average filler";
		}
		else if (goal == SlayerGoal.XP)
		{
			judged = !wilderness && rating.getXpAdvice() != null ? wikiWords(rating.getXpAdvice())
					: capitalize(SlayerSpend.quality(rating.getXp())) + " for XP";
		}
		else if (goal == SlayerGoal.PROFIT)
		{
			judged = capitalize(SlayerSpend.quality(rating.getGp())) + " for profit";
		}
		else
		{
			judged = speed(rating.getSpeed());
		}
		return wild ? "Wilderness: " + lower(judged) : judged;
	}

	private static String wikiWords(TaskRating.XpAdvice advice)
	{
		switch (advice)
		{
			case DO:
				return "Wiki: do it for XP";
			case SKIP:
				return "Wiki: skip or block for XP";
			default:
				return "Wiki: do not unlock for XP";
		}
	}

	/** A dossier field as it is, without its full stop, when it is a few plain words. Null when it takes sentences. */
	static String shortField(String text)
	{
		if (text == null) return null;
		String t = text.trim();
		if (t.endsWith(".")) t = t.substring(0, t.length() - 1).trim();
		if (t.isEmpty() || t.contains(";") || t.contains(":") || t.contains(". ") || t.contains(" - ") || t.contains("\u2013")
				|| t.contains("\u2014"))
		{
			return null;
		}
		return t;
	}

	/** {@code label} and a short field, when together they fit on a compact line. Null otherwise. */
	private static String item(String label, String field)
	{
		return field == null || label.length() + field.length() > MAX_ITEM ? null : label + field;
	}

	/**
	 * "Pray melee", "Pray missiles" or "Pray magic" when the dossier's prayer starts with that protection prayer and names
	 * no other, "No prayer needed" when it starts with none. Null when it takes a sentence.
	 */
	static String prayerItem(String text)
	{
		if (text == null) return null;
		final String t = text.trim();
		if (t.toLowerCase(Locale.ROOT).startsWith("none")) return "No prayer needed";
		final Set<String> named = new HashSet<>();
		final Matcher m = PRAYER.matcher(t);
		while (m.find()) named.add(m.group(1));
		if (named.size() != 1) return null;
		final String prayer = named.iterator().next();
		return t.startsWith("Protect from " + prayer) ? "Pray " + prayer.toLowerCase(Locale.ROOT) : null;
	}

	/**
	 * "Weak: demonbane" or "Weak: water spells, demonbane": up to two of the spells, slayer weapons and melee styles the
	 * dossier's weakness names as working. Null when it names none of them.
	 */
	static String weaknessItem(String text)
	{
		if (text == null) return null;
		final String t = text.toLowerCase(Locale.ROOT);
		final List<String> found = new ArrayList<>();
		final Matcher spells = SPELLS.matcher(t);
		if (spells.find()) found.add(spells.group(1) + " spells");
		if (t.contains("demonbane")) found.add("demonbane");
		if (t.contains("dragonbane")) found.add("dragonbane");
		final Matcher attack = ATTACK.matcher(t);
		if (attack.find()) found.add(attack.group(1) != null ? attack.group(1) : attack.group(2));
		return found.isEmpty() ? null : "Weak: " + String.join(", ", found.subList(0, Math.min(2, found.size())));
	}

	/** "Style: melee or ranged" when the dossier's kill style takes a few words, else null. */
	static String styleItem(String text)
	{
		final String style = shortField(text);
		return style == null ? null : item("Style: ", lower(style));
	}

	/** "Attacks: melee" when the dossier's enemy attacks take a few words, else null. */
	static String attacksItem(String text)
	{
		final String attacks = shortField(text);
		return attacks == null ? null : item("Attacks: ", lower(attacks));
	}

	/**
	 * The Cannon and Combat area lines in a few words: "No cannon, multi", "Cannon", "Single combat". A line may be a
	 * place's fact ("Not allowed.", "Multicombat") or one of the dossier's plain words ("banned", "single"). Null when
	 * neither line is one of those.
	 */
	static String cannonItem(String cannonLine, String combatLine)
	{
		final String cannon = cannonWord(cannonLine);
		final String combat = combatWord(combatLine);
		if (cannon == null && combat == null) return null;
		if (cannon == null)
		{
			return "multi".equals(combat) ? "Multicombat" : "single".equals(combat) ? "Single combat" : "Partly multicombat";
		}
		return combat == null ? cannon : cannon + ", " + combat;
	}

	/**
	 * "Cannon" for a place that allows one or a dossier line that only praises it, "No cannon" for one that rules it out.
	 * A line that does both, such as "great in Stronghold Slayer Cave; banned in Catacombs of Kourend", is left to the tooltip.
	 */
	private static String cannonWord(String line)
	{
		if (line == null) return null;
		final String t = line.trim().toLowerCase(Locale.ROOT);
		if (t.startsWith("not allowed") || t.startsWith("not usable") || t.startsWith("banned") || t.equals("single-no")
				|| t.equals("immune to cannons"))
		{
			return "No cannon";
		}
		final boolean mixed = t.contains(";") || t.contains("banned") || t.contains("not usable") || t.contains("not allowed");
		if (t.startsWith("allowed") || !mixed && (t.startsWith("great") || t.startsWith("good") || t.equals("ok"))) return "Cannon";
		return null;
	}

	private static String combatWord(String line)
	{
		if (line == null) return null;
		final String t = line.trim().toLowerCase(Locale.ROOT);
		if (t.equals("multicombat") || t.equals("multi")) return "multi";
		if (t.equals("single combat") || t.equals("single")) return "single";
		return t.startsWith("mixed") ? "partly multi" : null;
	}

	/** The name the dossier's place starts with, e.g. "Catacombs of Kourend" from "Catacombs of Kourend (multi) for XP". */
	static String placeItem(String text)
	{
		if (text == null) return null;
		final Matcher end = PLACE_END.matcher(text);
		final String place = (end.find() ? text.substring(0, end.start()) : text).trim();
		return place.isEmpty() || place.length() > SHORT_PLACE ? null : place;
	}

	private static String capitalize(String text)
	{
		return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
	}

	private static String lower(String text)
	{
		return text.isEmpty() ? text : Character.toLowerCase(text.charAt(0)) + text.substring(1);
	}
}
