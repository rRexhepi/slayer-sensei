import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Dumps RuneLite's world map location enums (the world map plugin's own data) to a TSV file: one line
 * per enum constant with every getter's value. build_slayer_areas.py reads entrance points from it.
 * Usage: java -cp <client jar>;<runelite-api jar>;<guava>;<slf4j> DumpRuneLiteMapData out.tsv
 */
public class DumpRuneLiteMapData
{
	public static void main(String[] args) throws Exception
	{
		final String[] classes = {
			"net.runelite.client.plugins.worldmap.DungeonLocation",
			"net.runelite.client.plugins.worldmap.TransportationPointLocation",
			"net.runelite.client.plugins.worldmap.MinigameLocation",
			"net.runelite.client.plugins.worldmap.FairyRingLocation",
			"net.runelite.client.plugins.worldmap.TeleportLocationData",
		};
		try (PrintWriter out = new PrintWriter(args[0], "UTF-8"))
		{
			for (String name : classes)
			{
				final Class<?> c = Class.forName(name);
				final Method values = c.getDeclaredMethod("values");
				values.setAccessible(true);
				final Method[] getters = Arrays.stream(c.getDeclaredMethods())
						.filter(m -> m.getParameterCount() == 0 && m.getName().startsWith("get")
								&& !m.getName().equals("getDeclaringClass"))
						.sorted(Comparator.comparing(Method::getName))
						.toArray(Method[]::new);
				for (Object constant : (Object[]) values.invoke(null))
				{
					final StringBuilder line = new StringBuilder();
					line.append(c.getSimpleName()).append('\t').append(((Enum<?>) constant).name());
					for (Method getter : getters)
					{
						getter.setAccessible(true);
						line.append('\t').append(getter.getName().substring(3)).append('=').append(getter.invoke(constant));
					}
					out.print(line.append('\n'));
				}
			}
		}
	}
}
