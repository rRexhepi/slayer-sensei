package com.slayercompanion;

import com.slayercompanion.SlayerRequirements.ItemGroup;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.EqualsAndHashCode;
import net.runelite.api.Item;

/**
 * The gear check's items in one character's bank, with how many of each, as the bank was when it was last open. Only
 * ids that an item group of {@link SlayerRequirements} lists are kept, so nothing else about the bank is stored. Before
 * the bank is first seen there is none, and the gear check counts only what is carried and worn.
 */
@EqualsAndHashCode
final class BankGear
{
	/** The stored form of a bank that held none of the items, so a bank that was seen is never stored as nothing. */
	private static final String NONE = "none";

	/** How many of each item, lowest id first, so the stored form does not follow the bank's order. */
	private final Map<Integer, Integer> quantities;

	private BankGear(Map<Integer, Integer> quantities)
	{
		this.quantities = Collections.unmodifiableMap(quantities);
	}

	/** The gear check's items among the bank's stacks. A placeholder holds none and is left out. */
	static BankGear of(Item[] items)
	{
		final Map<Integer, Integer> quantities = new TreeMap<>();
		if (items != null)
		{
			final Set<Integer> gear = SlayerRequirements.gearIds();
			for (Item item : items)
			{
				if (item != null) add(quantities, gear, item.getId(), item.getQuantity());
			}
		}
		return new BankGear(quantities);
	}

	private static void add(Map<Integer, Integer> quantities, Set<Integer> gear, int id, int quantity)
	{
		if (quantity <= 0 || !gear.contains(id)) return;
		quantities.merge(id, quantity, (a, b) -> (int) Math.min(Integer.MAX_VALUE, (long) a + b));
	}

	int quantity(int id)
	{
		final Integer quantity = quantities.get(id);
		return quantity == null ? 0 : quantity;
	}

	/** How many of the group's items the bank holds, all of them together. */
	int count(ItemGroup group)
	{
		long count = 0;
		for (int id : group.ids())
		{
			count += quantity(id);
		}
		return (int) Math.min(Integer.MAX_VALUE, count);
	}

	/** The stored form: "id:quantity" for each item, lowest id first and separated by commas, or "none". */
	String encode()
	{
		if (quantities.isEmpty()) return NONE;
		final StringBuilder sb = new StringBuilder();
		for (Map.Entry<Integer, Integer> entry : quantities.entrySet())
		{
			if (sb.length() > 0) sb.append(',');
			sb.append(entry.getKey()).append(':').append(entry.getValue());
		}
		return sb.toString();
	}

	/**
	 * Reads {@link #encode}. Null when nothing is stored, which is the case until the bank is first seen. A part that
	 * cannot be read, or an item no group lists any more, is skipped, so the rest still counts.
	 */
	static BankGear decode(String stored)
	{
		if (stored == null) return null;
		final Map<Integer, Integer> quantities = new TreeMap<>();
		final Set<Integer> gear = SlayerRequirements.gearIds();
		for (String part : stored.split(","))
		{
			final String[] pair = part.trim().split(":");
			if (pair.length != 2) continue;
			try
			{
				add(quantities, gear, Integer.parseInt(pair[0].trim()), Integer.parseInt(pair[1].trim()));
			}
			catch (NumberFormatException ignored)
			{
				// Not a pair encode writes
			}
		}
		return new BankGear(quantities);
	}

	@Override
	public String toString()
	{
		return encode();
	}

	/**
	 * The bank of the character logged in, as it was when last seen. A character's bank is loaded when the character
	 * changes, as the Buy tab's sightings are, and replaced each time the bank shows something new. The bank is read only
	 * after it changed, on a tick it is open.
	 */
	static final class Memory
	{
		private final Supplier<String> profile;
		private final Supplier<String> load;
		private final Consumer<String> save;
		/** The RS profile key of the character whose bank {@link #gear} is. */
		private String loaded;
		private BankGear gear;
		private boolean changed;

		/**
		 * @param profile the RS profile key of the character logged in, or null
		 * @param load the stored bank of the character logged in, or null when none is stored
		 * @param save stores the bank of the character logged in
		 */
		Memory(Supplier<String> profile, Supplier<String> load, Consumer<String> save)
		{
			this.profile = profile;
			this.load = load;
			this.save = save;
		}

		/** The bank changed, or may have. It is read on the next tick it is open. */
		void bankChanged()
		{
			changed = true;
		}

		/** Whether a change waits to be read. Until one does, nothing looks at the bank. */
		boolean waiting()
		{
			return changed;
		}

		/** What the bank of the character logged in held when last seen. Null before that bank is first seen. */
		BankGear get()
		{
			final String key = profile.get();
			if (key == null) return null;
			if (!key.equals(loaded))
			{
				loaded = key;
				gear = decode(load.get());
			}
			return gear;
		}

		/**
		 * Reads the open bank once a change waits, and stores what it holds when that differs from what is remembered.
		 * True when the memory changed, which the first bank a character shows always does.
		 */
		boolean read(Item[] bank)
		{
			if (!changed || profile.get() == null) return false;
			changed = false;
			final BankGear seen = of(bank);
			if (seen.equals(get())) return false;
			gear = seen;
			save.accept(seen.encode());
			return true;
		}
	}
}
