package com.slayercompanion;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.IntPredicate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Value;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;

/**
 * Items on the reward shop's Buy tab that can be worth points for a goal. The costs come from the cache at
 * runtime ({@link SlayerGameData#shopItems}). The names and costs here are fallbacks, and SlayerShopItemTest
 * keeps them equal to the cache dump. The tab also sells Slayer rings, broad bolts and broad arrows. Those are
 * used up, so the plan never recommends them. What each item is worth for a goal is in {@link SlayerSpend}.
 * <p>
 * Whether the player has one cannot be read from the game unless the bank is open, so it is sighted: an item
 * counts as owned once one of {@link #getOwnedIds} has been seen in the inventory, worn or in the bank.
 */
@Getter
@AllArgsConstructor
enum SlayerShopItem
{
	/** Wiki Herb sack: "Buying one requires level 58 in Herblore". The silklined sack is an upgrade of it. */
	HERB_SACK(ItemID.SLAYER_HERB_SACK, "Herb sack", 750, Skill.HERBLORE, 58, new int[]{
			ItemID.SLAYER_HERB_SACK, ItemID.SLAYER_HERB_SACK_OPEN, ItemID.SLAYER_HERB_SACK_SILK, ItemID.SLAYER_HERB_SACK_SILK_OPEN},
			"Tithe Farm sells it for 250 of its points"),
	/** The locked, divine and noted pouches all count. Wiki Rune pouch: "Players cannot own more than one rune pouch". */
	RUNE_POUCH(ItemID.BH_RUNE_POUCH, "Rune pouch", 750, null, 0, new int[]{
			ItemID.BH_RUNE_POUCH, ItemID.BH_RUNE_POUCH_TROUVER, ItemID.DIVINE_RUNE_POUCH, ItemID.DIVINE_RUNE_POUCH_TROUVER,
			ItemID.PROMISSORY_NOTE_RUNEPOUCH},
			"A rune pouch note from the Grand Exchange or 75 Last Man Standing points also gets you one"),
	/** Wiki Looting bag: it drops from "any monster in the Wilderness" except a few. */
	LOOTING_BAG(ItemID.LOOTING_BAG, "Looting bag", 10, null, 0, new int[]{
			ItemID.LOOTING_BAG, ItemID.LOOTING_BAG_OPEN, ItemID.PROMISSORY_NOTE_LOOTINGBAG},
			"Wilderness monsters drop it too");

	/** The item the Buy tab sells, the key of its cost in the cache. */
	private final int itemId;
	private final String fallbackName;
	private final int fallbackCost;
	/** The skill buying or using it needs, or null. */
	private final Skill skill;
	private final int level;
	/** Every item that means the player already has one. */
	private final int[] ownedIds;
	/** A cheaper way to get one, or another fact every line should carry. */
	private final String fact;

	/** Whether a container holds one, e.g. {@code container::contains}. */
	boolean seenIn(IntPredicate contains)
	{
		for (int id : ownedIds)
		{
			if (contains.test(id)) return true;
		}
		return false;
	}

	/** What has been seen of one character's items, kept per character. */
	@Value
	static class Sightings
	{
		static final Sightings NONE = new Sightings(Collections.<SlayerShopItem>emptySet(), false);

		/** Items seen in the inventory, worn or in the bank at least once. */
		Set<SlayerShopItem> seen;
		/** Whether the bank has been seen open at least once, so an item not in it is likely not owned. */
		boolean bankSeen;

		boolean owns(SlayerShopItem item)
		{
			return seen.contains(item);
		}

		Sightings with(Set<SlayerShopItem> more, boolean bank)
		{
			final Set<SlayerShopItem> all = seen.isEmpty() ? EnumSet.noneOf(SlayerShopItem.class) : EnumSet.copyOf(seen);
			all.addAll(more);
			return new Sightings(Collections.unmodifiableSet(all), bankSeen || bank);
		}

		/** The stored form: item names separated by commas, then "bank" when the bank has been seen. */
		String encode()
		{
			final StringBuilder sb = new StringBuilder();
			for (SlayerShopItem item : SlayerShopItem.values())
			{
				if (!seen.contains(item)) continue;
				if (sb.length() > 0) sb.append(',');
				sb.append(item.name());
			}
			if (bankSeen) sb.append(sb.length() > 0 ? "," : "").append(BANK);
			return sb.toString();
		}

		/** Reads {@link #encode}. Unknown names are skipped, so a removed item does not lose the rest. */
		static Sightings decode(String stored)
		{
			if (stored == null || stored.trim().isEmpty()) return NONE;
			final Set<SlayerShopItem> seen = EnumSet.noneOf(SlayerShopItem.class);
			boolean bank = false;
			for (String part : stored.split(","))
			{
				final String name = part.trim();
				if (BANK.equals(name))
				{
					bank = true;
					continue;
				}
				for (SlayerShopItem item : SlayerShopItem.values())
				{
					if (item.name().equals(name)) seen.add(item);
				}
			}
			return new Sightings(Collections.unmodifiableSet(seen), bank);
		}

		private static final String BANK = "bank";
	}
}
