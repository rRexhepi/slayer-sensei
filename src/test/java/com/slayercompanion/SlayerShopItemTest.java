package com.slayercompanion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

/** The Buy tab items the plan recommends, against the cache dump, and how they are sighted. */
public class SlayerShopItemTest
{
	private static final SlayerGameData DATA = SlayerMasterRankerTest.DATA;

	@Test
	public void fallbackCostsMatchTheCache()
	{
		for (SlayerShopItem item : SlayerShopItem.values())
		{
			assertEquals(item.name(), DATA.shopItems().get(item.getItemId()), Integer.valueOf(item.getFallbackCost()));
		}
	}

	/** The rest of the tab is used up, so it is left out: Slayer rings run out of charges, bolts and arrows are fired. */
	@Test
	public void onlyItemsThatLastAreListed()
	{
		final Set<Integer> listed = new HashSet<>();
		for (SlayerShopItem item : SlayerShopItem.values()) listed.add(item.getItemId());
		final Set<Integer> usedUp = new HashSet<>(Arrays.asList(ItemID.SLAYER_RING_8, ItemID.SLAYER_BROAD_BOLT, ItemID.SLAYER_BROAD_ARROWS));
		for (int sold : DATA.shopItems().keySet())
		{
			assertTrue("Buy tab item " + sold + " is new. List it in SlayerShopItem or say why it is left out.",
					listed.contains(sold) || usedUp.contains(sold));
		}
	}

	@Test
	public void upgradesAndNotesCountAsOwning()
	{
		assertTrue(SlayerShopItem.HERB_SACK.seenIn(id -> id == ItemID.SLAYER_HERB_SACK_SILK_OPEN));
		assertTrue(SlayerShopItem.RUNE_POUCH.seenIn(id -> id == ItemID.DIVINE_RUNE_POUCH));
		assertTrue(SlayerShopItem.RUNE_POUCH.seenIn(id -> id == ItemID.PROMISSORY_NOTE_RUNEPOUCH));
		assertTrue(SlayerShopItem.LOOTING_BAG.seenIn(id -> id == ItemID.LOOTING_BAG_OPEN));
		assertFalse(SlayerShopItem.RUNE_POUCH.seenIn(id -> id == ItemID.SLAYER_HERB_SACK));
	}

	@Test
	public void sightingsAreKeptAsText()
	{
		final SlayerShopItem.Sightings none = SlayerShopItem.Sightings.decode(null);
		assertEquals(SlayerShopItem.Sightings.NONE, none);
		assertEquals("", none.encode());
		final SlayerShopItem.Sightings seen = none.with(EnumSet.of(SlayerShopItem.RUNE_POUCH), true);
		assertEquals("RUNE_POUCH,bank", seen.encode());
		assertEquals(seen, SlayerShopItem.Sightings.decode(seen.encode()));
		assertTrue(seen.owns(SlayerShopItem.RUNE_POUCH));
		assertFalse(seen.owns(SlayerShopItem.HERB_SACK));
		// Once seen, always seen
		assertTrue(seen.with(Collections.<SlayerShopItem>emptySet(), false).isBankSeen());
		// An item a later version drops does not lose the rest
		assertEquals(seen, SlayerShopItem.Sightings.decode("GONE_ITEM, RUNE_POUCH ,bank"));
	}
}
