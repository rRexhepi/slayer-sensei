package com.slayercompanion;

import static org.junit.Assert.assertNotNull;

import com.google.gson.Gson;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import com.google.inject.util.Providers;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import org.junit.Test;

/**
 * Builds the plugin's Guice graph the way RuneLite's PluginManager does: the plugin instance bound and installed as
 * a module. A class of the plugin that Guice cannot construct makes plugin creation throw at load time, and RuneLite
 * then silently leaves the plugin out of the plugin list while every logic test stays green. Tool stage resolves
 * every injection point without constructing anything, so the types RuneLite itself provides need only a binding.
 */
public class SlayerCompanionGraphTest
{
	@Test
	public void everyInjectionPointOfThePluginResolves()
	{
		final SlayerCompanionPlugin plugin = new SlayerCompanionPlugin();
		final Injector injector = Guice.createInjector(Stage.TOOL, new AbstractModule()
		{
			@Override
			protected void configure()
			{
				bind(Client.class).toProvider(Providers.<Client>of(null));
				bind(ConfigManager.class).toProvider(Providers.<ConfigManager>of(null));
				bind(OverlayManager.class).toProvider(Providers.<OverlayManager>of(null));
				bind(ItemManager.class).toProvider(Providers.<ItemManager>of(null));
				bind(WorldMapPointManager.class).toProvider(Providers.<WorldMapPointManager>of(null));
				bind(ClientToolbar.class).toProvider(Providers.<ClientToolbar>of(null));
				bind(ClientThread.class).toProvider(Providers.<ClientThread>of(null));
				bind(PluginManager.class).toProvider(Providers.<PluginManager>of(null));
				bind(EventBus.class).toProvider(Providers.<EventBus>of(null));
				bind(Gson.class).toInstance(new Gson());
				bind(SlayerCompanionPlugin.class).toInstance(plugin);
				install(plugin);
			}
		});
		for (Class<?> type : new Class<?>[]{SlayerCompanionPanel.class, SlayerCompanionOverlay.class, SlayerDataset.class,
				SlayerLocations.class, ShortestPathBridge.class, SlayerCompanionConfig.class})
		{
			assertNotNull(type.getSimpleName(), injector.getBinding(type));
		}
	}
}
