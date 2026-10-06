package com.slayercompanion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.events.ConfigChanged;
import org.junit.Test;

/**
 * The plugin's bank memory on logged-in ticks without a task. The bank is looked at only after it changed, on a tick its
 * interface is open, and read at most once a tick. The gear check counts what the bank of the character logged in held.
 * The client here answers only what such a tick reads, the bank's interface and the bank, and counts the last two.
 */
public class SlayerCompanionBankTest
{
	/** Coins and a rock hammer. Only the hammer is kept. */
	private static final Item[] BANK = {new Item(ItemID.COINS, 1_000_000), new Item(ItemID.SLAYER_ROCK_HAMMER, 1)};

	@Test
	public void nothingLooksAtTheBankUntilItChanges() throws Exception
	{
		final Harness plugin = new Harness();
		plugin.open = true;
		plugin.tick();
		// The inventory and the worn items are the gear check's own, not the bank's
		plugin.changed(InventoryID.INV);
		plugin.changed(InventoryID.WORN);
		plugin.tick();
		assertEquals(0, plugin.widgetLooks);
		assertEquals(0, plugin.bankReads);
		assertTrue(plugin.stored.isEmpty());
	}

	@Test
	public void aChangeIsReadOnTheNextTickOnceHoweverOftenItChanged() throws Exception
	{
		final Harness plugin = new Harness();
		plugin.open = true;
		plugin.changed(InventoryID.BANK);
		plugin.changed(InventoryID.BANK);
		plugin.changed(InventoryID.BANK);
		assertEquals("nothing is read before the tick", 0, plugin.bankReads);
		plugin.tick();
		assertEquals(1, plugin.bankReads);
		assertEquals("4162:1", plugin.stored.get("profile-a"));
		plugin.tick();
		assertEquals("read again only after the next change", 1, plugin.bankReads);
		assertEquals(1, plugin.widgetLooks);
	}

	@Test
	public void aChangeWhileTheBankIsClosedWaitsUntilItIsOpen() throws Exception
	{
		final Harness plugin = new Harness();
		plugin.changed(InventoryID.BANK);
		plugin.tick();
		plugin.tick();
		assertEquals(0, plugin.bankReads);
		assertTrue(plugin.stored.isEmpty());
		plugin.open = true;
		plugin.tick();
		assertEquals(1, plugin.bankReads);
		assertEquals("4162:1", plugin.stored.get("profile-a"));
	}

	@Test
	public void theGearCheckCountsTheBankOfTheCharacterLoggedIn() throws Exception
	{
		final Harness plugin = new Harness();
		SlayerGearCheck.Result gear = plugin.gear("Gargoyles", "40");
		assertFalse(gear.isBankSeen());
		assertEquals(Collections.singletonList("Missing: rock or granite hammer"), gear.getItems());
		plugin.open = true;
		plugin.changed(InventoryID.BANK);
		plugin.tick();
		gear = plugin.gear("Gargoyles", "40");
		assertTrue(gear.isBankSeen());
		assertEquals(Collections.singletonList("In bank: Rock hammer"), gear.getItems());
		// The next character's bank has not been seen yet
		plugin.profile = "profile-b";
		gear = plugin.gear("Gargoyles", "40");
		assertFalse(gear.isBankSeen());
		assertEquals(Collections.singletonList("Missing: rock or granite hammer"), gear.getItems());
		plugin.profile = "profile-a";
		assertEquals(Collections.singletonList("In bank: Rock hammer"), plugin.gear("Gargoyles", "40").getItems());
	}

	/** Writing the memory posts a config change for its group, which must not be taken for a change of a setting. */
	@Test
	public void storingTheBankChangesNoSetting() throws Exception
	{
		final Harness plugin = new Harness();
		plugin.set("lastSidebarSignature", "shown");
		final ConfigChanged event = new ConfigChanged();
		event.setGroup(SlayerCompanionPlugin.BANK_GEAR_GROUP);
		event.setKey(SlayerCompanionPlugin.BANK_GEAR_KEY);
		event.setNewValue("4162:1");
		plugin.plugin.onConfigChanged(event);
		assertEquals("shown", plugin.get("lastSidebarSignature"));
	}

	/** The plugin with a client that counts what it is asked about the bank, settings that are all off, and stored text by character. */
	private static final class Harness
	{
		final SlayerCompanionPlugin plugin = new SlayerCompanionPlugin();
		final Map<String, String> stored = new HashMap<>();
		String profile = "profile-a";
		boolean open;
		int widgetLooks;
		int bankReads;

		Harness() throws Exception
		{
			set("client", Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Client.class},
				(proxy, method, args) -> client(method, args)));
			set("config", Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{SlayerCompanionConfig.class},
				(proxy, method, args) -> method.getReturnType() == boolean.class ? Boolean.FALSE : null));
			set("bankMemory", new BankGear.Memory(() -> profile, () -> stored.get(profile), value -> stored.put(profile, value)));
		}

		void tick()
		{
			plugin.onGameTick(new GameTick());
		}

		void changed(int containerId)
		{
			plugin.onItemContainerChanged(new ItemContainerChanged(containerId, null));
		}

		/** The gear check of a task with nothing carried, as the tick on that task builds it. */
		SlayerGearCheck.Result gear(String task, String killsLeft) throws Exception
		{
			set("taskName", task);
			set("taskAmount", killsLeft);
			set("assignedAreaId", 0);
			set("gearItems", Collections.<SlayerGearCheck.Held>emptyList());
			set("gearCheckDirty", false);
			final Method update = SlayerCompanionPlugin.class.getDeclaredMethod("updateGearCheck");
			update.setAccessible(true);
			update.invoke(plugin);
			return plugin.getGearResult();
		}

		private Object client(Method method, Object[] args)
		{
			switch (method.getName())
			{
				case "getGameState":
					return GameState.LOGGED_IN;
				case "getVarpValue":
				case "getVarbitValue":
					return 0;
				case "getWidget":
					widgetLooks++;
					assertEquals(InterfaceID.Bankmain.ITEMS, args[0]);
					return widget(!open);
				case "getItemContainer":
					bankReads++;
					assertEquals(InventoryID.BANK, args[0]);
					return bank();
				case "hashCode":
					return System.identityHashCode(this);
				case "toString":
					return "client";
				default:
					throw new AssertionError("a tick asked the client for " + method.getName());
			}
		}

		private Widget widget(boolean hidden)
		{
			return (Widget) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Widget.class}, (proxy, method, args) ->
			{
				if ("isHidden".equals(method.getName())) return hidden;
				throw new AssertionError("the bank's interface was asked for " + method.getName());
			});
		}

		private ItemContainer bank()
		{
			return (ItemContainer) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ItemContainer.class},
				(proxy, method, args) ->
				{
					if ("getItems".equals(method.getName())) return BANK.clone();
					throw new AssertionError("the bank was asked for " + method.getName());
				});
		}

		void set(String field, Object value) throws Exception
		{
			final Field f = SlayerCompanionPlugin.class.getDeclaredField(field);
			f.setAccessible(true);
			f.set(plugin, value);
		}

		Object get(String field) throws Exception
		{
			final Field f = SlayerCompanionPlugin.class.getDeclaredField(field);
			f.setAccessible(true);
			return f.get(plugin);
		}
	}
}
