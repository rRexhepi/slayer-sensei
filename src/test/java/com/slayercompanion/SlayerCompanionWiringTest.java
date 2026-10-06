package com.slayercompanion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.lang.reflect.Field;
import net.runelite.client.plugins.PluginDependency;
import org.junit.Test;

/**
 * Guice wiring invariants that unit tests cannot catch by running plugin logic. Slayer Sensei reads the task from
 * the game itself, so it depends on no other plugin. A type from another plugin's package is bound only inside that
 * plugin's own injector: injecting one without a matching {@code @PluginDependency} makes plugin creation throw at load
 * time, and RuneLite then silently leaves the plugin out of the plugin list while every logic test stays green.
 */
public class SlayerCompanionWiringTest
{
	@Test
	public void thePluginDependsOnNoOtherPlugin()
	{
		assertEquals(0, SlayerCompanionPlugin.class.getAnnotationsByType(PluginDependency.class).length);
	}

	@Test
	public void nothingIsInjectedFromAnotherPlugin()
	{
		for (Field field : SlayerCompanionPlugin.class.getDeclaredFields())
		{
			if (!field.isAnnotationPresent(javax.inject.Inject.class))
			{
				continue;
			}
			final Package fieldPackage = field.getType().getPackage();
			// The plugin manager lives in net.runelite.client.plugins itself, which is the client's, not a plugin's
			assertFalse("field '" + field.getName() + "' injects a type from another plugin's package " + fieldPackage,
					fieldPackage != null && fieldPackage.getName().startsWith("net.runelite.client.plugins."));
		}
	}
}
