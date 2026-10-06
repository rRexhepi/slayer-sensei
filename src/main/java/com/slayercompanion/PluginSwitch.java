package com.slayercompanion;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginInstantiationException;
import net.runelite.client.plugins.PluginManager;

/**
 * Turns another plugin on when the player presses a sidebar button, the way RuneLite's own plugin list does: enabled in
 * the config, then started. Nothing else switches a plugin on. The steps go through {@link Plugins}, so a failure can be
 * tested without a client.
 */
@Slf4j
final class PluginSwitch
{
	/** The plugin manager's three calls this needs. */
	interface Plugins
	{
		void setEnabled(Plugin plugin, boolean enabled);

		boolean start(Plugin plugin) throws PluginInstantiationException;

		boolean isActive(Plugin plugin);
	}

	private PluginSwitch()
	{
	}

	/** The plugin manager's own calls. Starting asserts the Swing thread, as the plugin list starts plugins there. */
	static Plugins of(PluginManager pluginManager)
	{
		return new Plugins()
		{
			@Override
			public void setEnabled(Plugin plugin, boolean enabled)
			{
				pluginManager.setPluginEnabled(plugin, enabled);
			}

			@Override
			public boolean start(Plugin plugin) throws PluginInstantiationException
			{
				return pluginManager.startPlugin(plugin);
			}

			@Override
			public boolean isActive(Plugin plugin)
			{
				return pluginManager.isPluginActive(plugin);
			}
		};
	}

	/**
	 * Swing thread. Whether the plugin runs afterwards. A plugin that fails to start stays enabled, as the plugin list
	 * leaves it, so it tries again at the next launch.
	 */
	static boolean turnOn(Plugins plugins, Plugin plugin)
	{
		if (plugin == null) return false;
		try
		{
			plugins.setEnabled(plugin, true);
			// Starting does nothing for a plugin that already runs, so the check below decides
			plugins.start(plugin);
		}
		catch (PluginInstantiationException | RuntimeException e)
		{
			log.warn("Could not turn on {}", plugin.getName(), e);
			return false;
		}
		return plugins.isActive(plugin);
	}
}
