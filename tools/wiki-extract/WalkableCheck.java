import java.io.BufferedReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Checks candidate kill spots against the collision map that ships inside the Shortest Path plugin
 * jar, so every spot the plugin sends is a tile Shortest Path can actually stand on. Read-only: the
 * jar is only put on the classpath, never modified or bundled.
 *
 * Input: lines of "x<TAB>y<TAB>plane". Output: the same lines with a fourth column, 1 when the tile
 * has at least one walkable edge in Shortest Path's map, 0 otherwise, and a fifth column listing every
 * plane (0 to 3) on which that x and y is walkable, for example "2" or "0,1", or "-" for none.
 */
public class WalkableCheck
{
	public static void main(String[] args) throws Exception
	{
		final Class<?> splitFlagMap = Class.forName("shortestpath.pathfinder.SplitFlagMap");
		final Object flags = splitFlagMap.getMethod("fromResources").invoke(null);
		final Class<?> collisionMap = Class.forName("shortestpath.pathfinder.CollisionMap");
		final Object map = collisionMap.getConstructor(splitFlagMap).newInstance(flags);
		final java.lang.reflect.Method isBlocked = collisionMap.getMethod("isBlocked", int.class, int.class, int.class);
		int checked = 0;
		int walkable = 0;
		try (BufferedReader in = Files.newBufferedReader(Paths.get(args[0]), StandardCharsets.UTF_8);
			PrintWriter out = new PrintWriter(Files.newBufferedWriter(Paths.get(args[1]), StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = in.readLine()) != null)
			{
				if (line.trim().isEmpty()) continue;
				final String[] p = line.split("\t");
				final int x = Integer.parseInt(p[0]);
				final int y = Integer.parseInt(p[1]);
				final int plane = Integer.parseInt(p[2]);
				final boolean ok = !(Boolean) isBlocked.invoke(map, x, y, plane);
				final StringBuilder planes = new StringBuilder();
				for (int z = 0; z <= 3; z++)
				{
					if (!(Boolean) isBlocked.invoke(map, x, y, z))
					{
						if (planes.length() > 0) planes.append(',');
						planes.append(z);
					}
				}
				out.print(x + "\t" + y + "\t" + plane + "\t" + (ok ? 1 : 0) + "\t" + (planes.length() == 0 ? "-" : planes) + "\n");
				checked++;
				if (ok) walkable++;
			}
		}
		System.out.println(walkable + " of " + checked + " points are walkable in Shortest Path's collision map");
	}
}
