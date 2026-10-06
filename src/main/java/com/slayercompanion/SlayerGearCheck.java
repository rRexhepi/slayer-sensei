package com.slayercompanion;

import com.slayercompanion.SlayerRequirements.ItemGroup;
import com.slayercompanion.SlayerRequirements.Need;
import com.slayercompanion.SlayerRequirements.Recommendation;
import com.slayercompanion.SlayerRequirements.Requirement;
import com.slayercompanion.SlayerRequirements.Unlock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Value;

/**
 * The sidebar's "Live gear check" line: what {@link SlayerRequirements} says the task needs and recommends, against
 * what the player carries and wears, and against what the bank held when it was last open. It is pure, so the plugin
 * only gathers the items, the bank it remembers, the kills left, Konar's area and a varbit reader for the unlocks, the
 * elite diary, the Fires of Eternal Light and Malevolent Masquerade. The compact sidebar's items and the scene overlay's
 * lines come from the same look ({@link #evaluate}).
 */
final class SlayerGearCheck
{
	/** A count at the end of a name, as in "Fungicide spray 7" or "Rod of Ivandis (7)". */
	private static final Pattern TRAILING_NUMBER = Pattern.compile("\\(?(\\d{1,6})\\)?\\s*$");
	/**
	 * The longest compact item, about what fits on a line of the compact sidebar. Longer ones leave out the counts or
	 * the place, which the line keeps.
	 */
	static final int MAX_ITEM = 34;
	/** The compact item of a mandatory item that only the bank holds. */
	private static final String IN_BANK = "In bank: ";
	/** The line's sentence for everything to bring from the bank. */
	private static final String FROM_BANK = "Bring from your bank: ";
	/** Shorter names for the item groups whose label is too long for a compact item. The line names every item. */
	private static final Map<ItemGroup, String> SHORT_LABELS = new IdentityHashMap<>();

	static
	{
		SHORT_LABELS.put(SlayerRequirements.EARMUFFS_OR_HELMET, "earmuffs or Slayer helmet");
		SHORT_LABELS.put(SlayerRequirements.SPINY_OR_HELMET, "spiny or Slayer helmet");
		SHORT_LABELS.put(SlayerRequirements.GOGGLES_OR_HELMET, "goggles or Slayer helmet");
		SHORT_LABELS.put(SlayerRequirements.MIRROR_SHIELDS, "mirror or V's shield");
		SHORT_LABELS.put(SlayerRequirements.WYVERN_SHIELDS, "wyvern shield or similar");
		SHORT_LABELS.put(SlayerRequirements.TUROTH_KURASK_WEAPONS, "leaf-bladed weapon");
		SHORT_LABELS.put(SlayerRequirements.KARUULM_BOOTS, "boots of stone or similar");
		SHORT_LABELS.put(SlayerRequirements.ROCK_HAMMERS, "rock or granite hammer");
		SHORT_LABELS.put(SlayerRequirements.ANY_ROCK_HAMMER, "rock or granite hammer");
		SHORT_LABELS.put(SlayerRequirements.VAMPYREBANE, "Ivandis flail or better");
		SHORT_LABELS.put(SlayerRequirements.SILVER_OR_EFARITAY, "silver or Efaritay's aid");
		SHORT_LABELS.put(SlayerRequirements.DRAGONFIRE_PROTECTION, "dragonfire protection");
		SHORT_LABELS.put(SlayerRequirements.HELMET_MASK_OR_SALVE, "Slayer helm, mask or salve");
		SHORT_LABELS.put(SlayerRequirements.HELMET_OR_SALVE, "Slayer helmet or salve");
	}

	/**
	 * How the gear stands: a mandatory item missing, something to bring from the bank or a recommended item to bring,
	 * or everything there.
	 */
	enum Status
	{
		MISSING,
		BRING,
		READY
	}

	/** The line, the compact sidebar's items and the overlay's lines from one look at the items. */
	@Value
	static class Result
	{
		/**
		 * READY or MISSING and the mandatory items when the task has any, then what to bring from the bank, then "Bring"
		 * and the recommended items that are not there with the reasons some carry, then "You have" and the ones that are,
		 * such as "READY: Rock hammer. Bring a Slayer helmet or black mask". When nothing is missing but a mandatory item
		 * is only in the bank there is no READY or MISSING, and the line starts with what to bring from the bank.
		 */
		String line;
		/** Every mandatory item is carried or worn. One that only the bank holds is not missing, and not ready either. */
		boolean ready;
		/** "Missing: 14 ice coolers (26 of 40)" for each missing need. */
		List<String> missing;
		/**
		 * "In bank: Rock hammer" for each mandatory item only the bank holds, "Bring from bank: Arclight" for each
		 * recommended item that is not there and the bank holds, then "Bring: demonbane weapon" for the others.
		 */
		List<String> bring;
		/** "Ready: Rock hammer", or "Ready" when the names do not fit, once nothing is missing or left to bring. Else null. */
		String readyItem;
		/** The bank has been seen, so what it holds counts. Before that only carried and worn items do. */
		boolean bankSeen;

		/** The compact sidebar's items: what is missing, then what to bring, or the one ready item. */
		List<String> getItems()
		{
			if (readyItem != null) return Collections.singletonList(readyItem);
			final List<String> items = new ArrayList<>(missing);
			items.addAll(bring);
			return Collections.unmodifiableList(items);
		}

		Status getStatus()
		{
			return !missing.isEmpty() ? Status.MISSING : !bring.isEmpty() ? Status.BRING : Status.READY;
		}
	}

	/**
	 * What one need lacks of what is carried: the line's words and the compact item's. When the bank holds it, it is
	 * brought from there rather than missing.
	 */
	private static final class Missed
	{
		final String text;
		final String item;
		final boolean inBank;
		/** The item the bank brings, or -1 for a count of used-up items or a need that is missing. */
		final int bankId;

		Missed(String text, String item)
		{
			this(text, item, false, -1);
		}

		private Missed(String text, String item, boolean inBank, int bankId)
		{
			this.text = text;
			this.item = item;
			this.inBank = inBank;
			this.bankId = bankId;
		}

		static Missed banked(String text, String item, int bankId)
		{
			return new Missed(text, item, true, bankId);
		}
	}

	private SlayerGearCheck()
	{
	}

	/** One stack in the inventory or the worn equipment. */
	static final class Held
	{
		final int id;
		final String name;
		final int quantity;

		Held(int id, String name, int quantity)
		{
			this.id = id;
			this.name = name;
			this.quantity = quantity;
		}
	}

	/** What is carried and worn, and what the bank holds once it has been seen. */
	private static final class Stock
	{
		final List<Held> held;
		/** Null before the bank is seen. */
		final BankGear bank;
		/**
		 * Items that bring a warning when carried, such as an open flame in the swamp caves. The bank's item is one of them
		 * only when the bank holds nothing else that does the job.
		 */
		final List<ItemGroup> warned;

		Stock(List<Held> held, BankGear bank, List<ItemGroup> warned)
		{
			this.held = held;
			this.bank = bank;
			this.warned = warned;
		}

		/** The first item of the group the bank holds, in the group's order, passing over a warned item when it holds another. -1 for none. */
		int bankItem(ItemGroup group)
		{
			if (bank == null) return -1;
			int warnedId = -1;
			for (int id : group.ids())
			{
				if (bank.quantity(id) <= 0) continue;
				if (!listedIn(id, warned)) return id;
				if (warnedId < 0) warnedId = id;
			}
			return warnedId;
		}

		int bankCount(ItemGroup group)
		{
			return bank == null ? 0 : bank.count(group);
		}

		/** The charges the bank holds for a need: those in the names of its sprays, and its refills. */
		int bankCharges(Need need)
		{
			if (bank == null) return 0;
			long charges = (long) need.getRefillCharges() * bank.count(need.getRefills());
			for (int id : need.getItems().ids())
			{
				charges += (long) chargesInName(need.getItems().name(id)) * bank.quantity(id);
			}
			return (int) Math.min(Integer.MAX_VALUE, charges);
		}
	}

	/**
	 * Whether the task, or the place Konar assigned, needs or recommends anything. Lets the plugin skip reading the
	 * items.
	 */
	static boolean hasNeeds(String taskName, int assignedAreaId, IntUnaryOperator varbits)
	{
		return hasNeeds(taskName, assignedAreaId, varbits, true);
	}

	/** @param recommend whether the recommended tier is on, as the config sets it. Off, only mandatory items count. */
	static boolean hasNeeds(String taskName, int assignedAreaId, IntUnaryOperator varbits, boolean recommend)
	{
		return taskName != null && (!needs(taskName, assignedAreaId).isEmpty() || !recommended(taskName, varbits, recommend).isEmpty());
	}

	/**
	 * @param killsLeft kills left on the task, 0 or less when unknown
	 * @param assignedAreaId Konar's SLAYER_AREA value, 0 when the master assigned no area
	 * @param held inventory and worn items
	 * @param varbits the current value of a varbit
	 * @return the line, or null when the task needs and recommends nothing
	 */
	static String check(String taskName, int killsLeft, int assignedAreaId, List<Held> held, IntUnaryOperator varbits)
	{
		final Result result = evaluate(taskName, killsLeft, assignedAreaId, held, varbits);
		return result == null ? null : result.getLine();
	}

	/** {@link #check} with the compact sidebar's items. Null when the task needs and recommends nothing. */
	static Result evaluate(String taskName, int killsLeft, int assignedAreaId, List<Held> held, IntUnaryOperator varbits)
	{
		return evaluate(taskName, killsLeft, assignedAreaId, held, varbits, true);
	}

	/**
	 * @param recommend whether the recommended tier is on, as the config sets it. Off, the result is the mandatory tier
	 * alone, with no recommended item to bring and no "You have", and null for a task that needs nothing.
	 */
	static Result evaluate(String taskName, int killsLeft, int assignedAreaId, List<Held> held, IntUnaryOperator varbits,
		boolean recommend)
	{
		return evaluate(taskName, killsLeft, assignedAreaId, held, null, varbits, recommend);
	}

	/**
	 * @param bank what the bank held when it was last open, or null before it is seen, which leaves the check to what is
	 * carried and worn. A mandatory item only the bank holds is to be brought from there rather than missing, and a
	 * recommended item it holds is named. The bank never makes the check READY.
	 */
	static Result evaluate(String taskName, int killsLeft, int assignedAreaId, List<Held> held, BankGear bank,
		IntUnaryOperator varbits, boolean recommend)
	{
		if (taskName == null) return null;
		final List<Need> needs = needs(taskName, assignedAreaId);
		final List<Recommendation> recommended = recommended(taskName, varbits, recommend);
		if (needs.isEmpty() && recommended.isEmpty()) return null;
		final int kills = killsLeft > 0 ? killsLeft : -1;
		final Stock stock = new Stock(held, bank, bank == null ? Collections.<ItemGroup>emptyList() : warned(recommended, varbits));
		final List<String> have = new ArrayList<>();
		final List<String> missing = new ArrayList<>();
		final List<String> missingItems = new ArrayList<>();
		final List<Missed> banked = new ArrayList<>();
		final List<String> notes = new ArrayList<>();
		// The item groups of the missing needs. A recommendation that shares items with one is already asked for.
		final List<ItemGroup> lacking = new ArrayList<>();
		for (Need need : needs)
		{
			// Konar's area is where the kills count, so the place needs no naming once it is known.
			String where = assignedAreaId > 0 ? null : need.getWhere();
			String exemptNote = null;
			if (need.getExemptVarbit() >= 0 && varbits.applyAsInt(need.getExemptVarbit()) > 0)
			{
				if (need.getExemptWhere() == null)
				{
					notes.add(need.getExemptNote());
					continue;
				}
				where = need.getExemptWhere();
				exemptNote = need.getExemptNote();
			}
			final Missed missed = evaluate(need, stock, kills, where, have, notes);
			if (missed == null) continue;
			if (missed.inBank)
			{
				banked.add(missed);
			}
			else
			{
				missing.add(missed.text);
				missingItems.add(missed.item);
				lacking.add(need.getItems());
				if (need.getAlternative() != null) lacking.add(need.getAlternative());
			}
			if (exemptNote != null) notes.add(exemptNote);
		}
		// The mandatory tier's sentence, READY only when every mandatory item is carried or worn
		String status = null;
		if (!missing.isEmpty())
		{
			status = "MISSING: " + sentences(list(missing, true), notes);
		}
		else if (banked.isEmpty() && !needs.isEmpty())
		{
			final Requirement requirement = SlayerRequirements.forTask(taskName);
			final Unlock unlock = requirement == null ? null : requirement.getUnlock();
			if (unlock != null && varbits.applyAsInt(unlock.getVarbit()) > 0 && find(held, unlock.getUses()) != null)
			{
				notes.add(unlock.getName() + " is unlocked, so the finishing blow is automatic");
			}
			status = "READY: " + sentences(have.isEmpty() ? null : list(have, false), notes);
		}

		// What the bank brings, the mandatory items first. Their compact items come before the other items to bring, as in the line.
		final List<String> fromBank = new ArrayList<>();
		final List<String> bringItems = new ArrayList<>();
		for (Missed item : banked)
		{
			fromBank.add(item.text);
			bringItems.add(item.item);
		}
		// The recommended tier, after the mandatory one. An item the mandatory part already names is not named twice, and
		// a conditional one is a warning, so it is not named once it is met.
		final List<String> bring = new ArrayList<>();
		final List<String> labelItems = new ArrayList<>();
		final List<String> reasons = new ArrayList<>();
		final List<String> extra = new ArrayList<>();
		for (Recommendation recommendation : recommended)
		{
			final ItemGroup group = recommendation.getItems();
			final Held item = find(held, group);
			final boolean conditional = recommendation.getOnlyWith() != null;
			if (item != null)
			{
				if (!conditional && !have.contains(item.name) && !extra.contains(item.name)) extra.add(item.name);
			}
			else if (applies(recommendation, held, banked, varbits) && !overlaps(group, lacking))
			{
				final int id = stock.bankItem(group);
				if (id >= 0 && bringsFromBank(banked, id)) continue;
				if (id >= 0)
				{
					fromBank.add(group.name(id));
					bringItems.add(bringFromBank(group, id));
				}
				else
				{
					bring.add(group.getLabel());
					labelItems.add("Bring: " + shortLabel(group));
				}
				if (recommendation.getReason() != null) reasons.add(recommendation.getReason());
			}
		}
		bringItems.addAll(labelItems);
		final List<String> parts = new ArrayList<>();
		if (status != null) parts.add(status);
		if (!fromBank.isEmpty()) parts.add(FROM_BANK + list(fromBank, false));
		// Without READY or MISSING in front, the notes follow what to bring from the bank
		if (status == null) parts.addAll(notes);
		if (!bring.isEmpty()) parts.add("Bring " + list(bring, true));
		parts.addAll(reasons);
		if (!extra.isEmpty()) parts.add("You have " + list(extra, false));

		String readyItem = null;
		if (missingItems.isEmpty() && bringItems.isEmpty())
		{
			final List<String> named = new ArrayList<>(have);
			named.addAll(extra);
			readyItem = readyItem(named, have);
		}
		return new Result(String.join(". ", parts), missing.isEmpty() && banked.isEmpty(), Collections.unmodifiableList(missingItems),
				Collections.unmodifiableList(bringItems), readyItem, bank != null);
	}

	/** "Ready: " and the names, those of the mandatory items alone when all do not fit, or "Ready". */
	private static String readyItem(List<String> all, List<String> mandatory)
	{
		for (List<String> names : Arrays.asList(all, mandatory))
		{
			final String item = "Ready: " + String.join(", ", names);
			if (!names.isEmpty() && item.length() <= MAX_ITEM) return item;
		}
		return "Ready";
	}

	/** The task's recommendations, then the Slayer helmet once Malevolent Masquerade is bought. None while the tier is off. */
	private static List<Recommendation> recommended(String taskName, IntUnaryOperator varbits, boolean recommend)
	{
		if (!recommend) return Collections.emptyList();
		final List<Recommendation> recommendations = new ArrayList<>(SlayerRequirements.recommendedFor(taskName));
		if (varbits.applyAsInt(SlayerRequirements.HELMET_UNLOCK_VARBIT) > 0)
		{
			recommendations.add(Recommendation.of(SlayerRequirements.slayerHeadgear(taskName)));
		}
		return recommendations;
	}

	/**
	 * A conditional recommendation applies while one of its other group is carried, or is to be brought from the bank,
	 * and its varbit is not set.
	 */
	private static boolean applies(Recommendation recommendation, List<Held> held, List<Missed> banked, IntUnaryOperator varbits)
	{
		if (exempt(recommendation, varbits)) return false;
		final ItemGroup onlyWith = recommendation.getOnlyWith();
		if (onlyWith == null || find(held, onlyWith) != null) return true;
		for (Missed item : banked)
		{
			if (onlyWith.ids().contains(item.bankId)) return true;
		}
		return false;
	}

	private static boolean exempt(Recommendation recommendation, IntUnaryOperator varbits)
	{
		return recommendation.getExemptVarbit() >= 0 && varbits.applyAsInt(recommendation.getExemptVarbit()) > 0;
	}

	/** The items the conditional recommendations warn about, such as an open flame in the swamp caves, unless a varbit lifts the warning. */
	private static List<ItemGroup> warned(List<Recommendation> recommended, IntUnaryOperator varbits)
	{
		final List<ItemGroup> warned = new ArrayList<>();
		for (Recommendation recommendation : recommended)
		{
			if (recommendation.getOnlyWith() != null && !exempt(recommendation, varbits)) warned.add(recommendation.getOnlyWith());
		}
		return warned;
	}

	private static boolean listedIn(int id, List<ItemGroup> groups)
	{
		for (ItemGroup group : groups)
		{
			if (group.ids().contains(id)) return true;
		}
		return false;
	}

	/** Whether a mandatory need already brings this item from the bank. */
	private static boolean bringsFromBank(List<Missed> banked, int id)
	{
		for (Missed item : banked)
		{
			if (item.bankId == id) return true;
		}
		return false;
	}

	/** Whether a recommended item class shares an item with a missing need, as the helmet does with a nose peg's need. */
	private static boolean overlaps(ItemGroup group, List<ItemGroup> lacking)
	{
		for (ItemGroup missing : lacking)
		{
			if (!Collections.disjoint(group.ids(), missing.ids())) return true;
		}
		return false;
	}

	/**
	 * "Missing: " and what is missing, with the place and the counts when they fit in {@link #MAX_ITEM}. The counts
	 * go first, so "14 ice coolers" with "for desert lizards" and "(26 of 40)" reads "Missing: 14 ice coolers (26 of 40)".
	 */
	static String missingItem(String what, String where, String counts)
	{
		return fit("Missing: ", what, where, counts);
	}

	/** {@link #missingItem} with another start, such as "In bank: 14 ice coolers (26 of 40)". */
	private static String fit(String start, String what, String where, String counts)
	{
		final String place = where == null ? "" : " " + where;
		final String count = counts == null ? "" : " (" + counts + ")";
		for (String item : new String[]{what + place + count, what + count, what + place})
		{
			if ((start + item).length() <= MAX_ITEM) return start + item;
		}
		return start + what;
	}

	/** A kept item only the bank holds: the line names it, and the compact item too unless the group's short name must stand in. */
	private static Missed keptItem(ItemGroup group, int id, String where)
	{
		final String name = group.name(id);
		final String item = fit(IN_BANK, name, where, null);
		return Missed.banked(name + place(where), item.length() <= MAX_ITEM ? item : fit(IN_BANK, shortLabel(group), where, null), id);
	}

	/**
	 * A recommended item the bank holds, such as "Bring from bank: Arclight". A long name keeps "From bank: " alone, and
	 * the group's short name stands in for a name that still does not fit.
	 */
	private static String bringFromBank(ItemGroup group, int id)
	{
		final String name = group.name(id);
		final String label = shortLabel(group);
		for (String item : new String[]{"Bring from bank: " + name, "From bank: " + name, "Bring from bank: " + label, "From bank: " + label})
		{
			if (item.length() <= MAX_ITEM) return item;
		}
		return "From bank: " + name;
	}

	/** An item group's name in a compact item: its short name, or its label without the leading article. */
	static String shortLabel(ItemGroup group)
	{
		final String label = SHORT_LABELS.get(group);
		if (label != null) return label;
		if (group.getLabel().startsWith("a ")) return group.getLabel().substring(2);
		return group.getLabel().startsWith("an ") ? group.getLabel().substring(3) : group.getLabel();
	}

	/** Charges carried in an item's name, as in "Fungicide spray 7". 0 when the name has none. */
	static int chargesInName(String name)
	{
		if (name == null) return 0;
		final Matcher matcher = TRAILING_NUMBER.matcher(name);
		return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
	}

	/** The task's needs that apply where it is done, then those of Konar's area that the task does not have yet. */
	private static List<Need> needs(String taskName, int assignedAreaId)
	{
		final List<Need> needs = new ArrayList<>();
		final Requirement requirement = SlayerRequirements.forTask(taskName);
		if (requirement != null)
		{
			for (Need need : requirement.getNeeds())
			{
				if (need.appliesIn(assignedAreaId)) needs.add(need);
			}
		}
		for (Need need : SlayerRequirements.forArea(assignedAreaId))
		{
			if (need.appliesIn(assignedAreaId) && !hasGroup(needs, need.getItems())) needs.add(need);
		}
		return needs;
	}

	/** Adds what is carried to {@code have} and returns null, or returns what the bank brings or what is missing. */
	private static Missed evaluate(Need need, Stock stock, int kills, String where, List<String> have, List<String> notes)
	{
		switch (need.getKind())
		{
			case PER_KILL:
				return perKill(need, stock, kills, where, have);
			case CHARGES:
				return charges(need, stock, kills, where, have);
			case ADVICE:
			{
				final Held item = find(stock.held, need.getItems());
				if (item != null) have.add(item.name);
				else notes.add(need.getAdvice());
				return null;
			}
			default:
			{
				final Held item = find(stock.held, need.getItems());
				if (item != null)
				{
					have.add(item.name);
					return null;
				}
				final int id = stock.bankItem(need.getItems());
				if (id >= 0) return keptItem(need.getItems(), id, where);
				return new Missed(need.getItems().getLabel() + place(where), missingItem(shortLabel(need.getItems()), where, null));
			}
		}
	}

	private static Missed perKill(Need need, Stock stock, int kills, String where, List<String> have)
	{
		final ItemGroup alternative = need.getAlternative();
		final Held kept = alternative == null ? null : find(stock.held, alternative);
		if (kept != null)
		{
			have.add(kept.name);
			return null;
		}
		final int count = count(stock.held, need.getItems());
		if (count > 0 && (kills < 0 || count >= kills))
		{
			have.add(count + " " + need.noun(count) + (kills > 0 ? " (need " + kills + ")" : ""));
			return null;
		}
		final Missed fromBank = perKillFromBank(need, stock, kills, where, count);
		if (fromBank != null) return fromBank;
		if (count > 0)
		{
			return new Missed(more(need, count, kills, where) + (alternative == null ? "" : ", or " + alternative.getLabel()),
				moreItem("Missing: ", need, count, kills, where));
		}
		final String counted = kills > 0 ? kills + " " + need.noun(kills) : need.plural();
		final String wanted = counted + place(where);
		if (alternative == null) return new Missed(wanted, missingItem(counted, where, null));
		// The compact item names the first of the two, as the line does
		return need.isAlternativeFirst()
			? new Missed(alternative.getLabel() + ", or " + wanted, missingItem(shortLabel(alternative), null, null))
			: new Missed(wanted + ", or " + alternative.getLabel(), missingItem(counted, where, null));
	}

	/**
	 * What the bank brings for a used-up need: the kept item, or the rest of the count when what is carried and what the
	 * bank holds cover the kills left. The kept item comes first where the line names it first. Null when the bank cannot
	 * bring either.
	 */
	private static Missed perKillFromBank(Need need, Stock stock, int kills, String where, int count)
	{
		final ItemGroup alternative = need.getAlternative();
		final int keptId = alternative == null ? -1 : stock.bankItem(alternative);
		final Missed kept = keptId < 0 ? null : keptItem(alternative, keptId, where);
		if (kept != null && need.isAlternativeFirst()) return kept;
		final int stored = stock.bankCount(need.getItems());
		if (stored <= 0 || (kills > 0 && (long) count + stored < kills)) return kept;
		// With the kills left unknown any count carried would do, so none is carried here
		if (kills < 0) return Missed.banked(need.plural() + place(where), fit(IN_BANK, need.plural(), where, null), -1);
		if (count == 0)
		{
			final String counted = kills + " " + need.noun(kills);
			return Missed.banked(counted + place(where), fit(IN_BANK, counted, where, null), -1);
		}
		return Missed.banked(more(need, count, kills, where), moreItem(IN_BANK, need, count, kills, where), -1);
	}

	private static Missed charges(Need need, Stock stock, int kills, String where, List<String> have)
	{
		boolean holder = false;
		int charges = 0;
		for (Held item : stock.held)
		{
			if (need.getItems().matches(item.id, item.name))
			{
				holder = true;
				charges += chargesInName(item.name) * Math.max(1, item.quantity);
			}
		}
		if (!holder)
		{
			// The spray comes first, the fullest the bank holds, since the group lists them from 10 charges down. Its
			// charges count once it is carried.
			final int spray = stock.bankItem(need.getItems());
			if (spray >= 0) return keptItem(need.getItems(), spray, where);
			return new Missed(need.getItems().getLabel() + place(where), missingItem(shortLabel(need.getItems()), where, null));
		}
		// Refills only help when there is a spray to put them in.
		charges += need.getRefillCharges() * count(stock.held, need.getRefills());
		if (charges > 0 && (kills < 0 || charges >= kills))
		{
			have.add(charges + " " + need.noun(charges) + (kills > 0 ? " (need " + kills + ")" : ""));
			return null;
		}
		final int stored = stock.bankCharges(need);
		if (stored > 0 && kills < 0) return Missed.banked(need.plural() + place(where), fit(IN_BANK, need.plural(), where, null), -1);
		if (stored > 0 && (long) charges + stored >= kills)
		{
			return Missed.banked(more(need, charges, kills, where), moreItem(IN_BANK, need, charges, kills, where), -1);
		}
		if (kills < 0) return new Missed(need.plural() + place(where), missingItem(need.plural(), where, null));
		return new Missed(more(need, charges, kills, where), moreItem("Missing: ", need, charges, kills, where));
	}

	/** "14 more ice coolers for desert lizards (have 26, need 40)" */
	private static String more(Need need, int count, int kills, String where)
	{
		final int more = kills - count;
		return more + " more " + need.noun(more) + place(where) + " (have " + count + ", need " + kills + ")";
	}

	/** The compact item of {@link #more}, such as "Missing: 14 ice coolers (26 of 40)". */
	private static String moreItem(String start, Need need, int count, int kills, String where)
	{
		final int more = kills - count;
		return fit(start, more + " " + need.noun(more), where, count + " of " + kills);
	}

	private static Held find(List<Held> held, ItemGroup group)
	{
		for (Held item : held)
		{
			if (group.matches(item.id, item.name)) return item;
		}
		return null;
	}

	private static int count(List<Held> held, ItemGroup group)
	{
		int count = 0;
		for (Held item : held)
		{
			if (group.matches(item.id, item.name)) count += Math.max(0, item.quantity);
		}
		return count;
	}

	private static boolean hasGroup(List<Need> needs, ItemGroup group)
	{
		for (Need need : needs)
		{
			if (need.getItems() == group) return true;
		}
		return false;
	}

	private static String place(String where)
	{
		return where == null ? "" : " " + where;
	}

	/** "a", "a and b", "a, b and c". Missing items take a serial comma, since their labels hold "or". */
	static String list(List<String> parts, boolean serialComma)
	{
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < parts.size(); i++)
		{
			if (i > 0)
			{
				final boolean last = i == parts.size() - 1;
				sb.append(!last ? ", " : serialComma ? ", and " : " and ");
			}
			sb.append(parts.get(i));
		}
		return sb.toString();
	}

	private static String sentences(String first, List<String> more)
	{
		final List<String> all = new ArrayList<>();
		if (first != null) all.add(first);
		all.addAll(more);
		return String.join(". ", all);
	}
}
