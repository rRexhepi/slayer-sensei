package com.slayercompanion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginInstantiationException;
import org.junit.Test;

/**
 * The sidebar's turn-on buttons without a client: the steps the plugin manager is asked for, in order, and what a
 * failure at each step leaves behind.
 */
public class PluginSwitchTest
{
	@PluginDescriptor(name = "Stand-in")
	private static final class StandIn extends Plugin
	{
	}

	/** Records the calls, and starts or fails as told. */
	private static class FakePlugins implements PluginSwitch.Plugins
	{
		final List<String> calls = new ArrayList<>();
		final boolean[] enabled = new boolean[1];
		boolean active;
		PluginInstantiationException startFails;
		RuntimeException startBreaks;

		@Override
		public void setEnabled(Plugin plugin, boolean on)
		{
			calls.add("enable " + on);
			enabled[0] = on;
		}

		@Override
		public boolean start(Plugin plugin) throws PluginInstantiationException
		{
			calls.add("start");
			if (startFails != null) throw startFails;
			if (startBreaks != null) throw startBreaks;
			// As the plugin manager does: a disabled plugin is not started
			if (!enabled[0] || active) return false;
			active = true;
			return true;
		}

		@Override
		public boolean isActive(Plugin plugin)
		{
			return active;
		}
	}

	@Test
	public void itIsEnabledFirstAndThenStarted()
	{
		final FakePlugins plugins = new FakePlugins();
		assertTrue(PluginSwitch.turnOn(plugins, new StandIn()));
		// The plugin manager starts only an enabled plugin, as RuneLite's plugin list does it
		assertEquals(java.util.Arrays.asList("enable true", "start"), plugins.calls);
	}

	@Test
	public void aPluginThatFailsToStartIsReportedAndStaysEnabled()
	{
		final FakePlugins plugins = new FakePlugins();
		plugins.startFails = new PluginInstantiationException("startUp threw");
		assertFalse(PluginSwitch.turnOn(plugins, new StandIn()));
		assertTrue("left enabled, as the plugin list leaves it", plugins.enabled[0]);

		final FakePlugins broken = new FakePlugins();
		broken.startBreaks = new IllegalStateException("conflict");
		assertFalse(PluginSwitch.turnOn(broken, new StandIn()));
	}

	@Test
	public void aStartThatDoesNothingCountsOnlyIfThePluginRuns()
	{
		// Already running: starting returns false, but the plugin runs
		final FakePlugins running = new FakePlugins();
		running.active = true;
		assertTrue(PluginSwitch.turnOn(running, new StandIn()));
		// Started without an error but not running afterwards
		final FakePlugins stopped = new FakePlugins()
		{
			@Override
			public boolean start(Plugin plugin)
			{
				calls.add("start");
				return true;
			}
		};
		assertFalse(PluginSwitch.turnOn(stopped, new StandIn()));
	}

	@Test
	public void aPluginThatIsNotThereIsNotTouched()
	{
		final FakePlugins plugins = new FakePlugins();
		assertFalse(PluginSwitch.turnOn(plugins, null));
		assertTrue(plugins.calls.isEmpty());
	}
}
