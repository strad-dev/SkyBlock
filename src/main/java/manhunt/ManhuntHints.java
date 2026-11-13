package manhunt;

import manhunt.structures.LiveWorld;
import manhunt.structures.LootRule;
import manhunt.structures.StructureFinder;
import misc.Plugin;
import misc.Utils;
import net.minecraft.core.BlockPos;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/** {@code manhunt.runner-hints}: on start, each online Speedrunner is told about the nearest structure of interest. */
public final class ManhuntHints {
	private ManhuntHints() {}

	public enum Level { OFF, DIRECTION, TYPE, COORDS }

	private static Level level = Level.TYPE;

	private static final String[] DIRECTIONS = {"North", "North-northeast", "Northeast", "East-northeast", "East",
			"East-southeast", "Southeast", "South-southeast", "South", "South-southwest", "Southwest", "West-southwest",
			"West", "West-northwest", "Northwest", "North-northwest"};

	public static void setLevel(String value) {
		level = switch(value.toLowerCase(Locale.ROOT)) {
			case "false", "off", "none" -> Level.OFF;
			case "direction" -> Level.DIRECTION;
			case "coords" -> Level.COORDS;
			default -> Level.TYPE;
		};
	}

	public static void give() {
		if(level == Level.OFF) return;
		for(Player p : Manhunt.onlineSpeedrunners()) {
			Location at = p.getLocation();
			LiveWorld world = new LiveWorld(((CraftWorld) at.getWorld()).getHandle());
			search(p, at, world, new StructureFinder(world, at.getX(), at.getZ(), Double.MAX_VALUE));
		}
	}

	private static void search(Player p, Location at, LiveWorld live, StructureFinder finder) {
		if(!p.isOnline()) return;
		StructureFinder.Found found = finder.next();
		if(found == null) return;
		LootRule rule = LootRule.of(found.type());
		if(rule == null) {
			tell(p, at, found);
			return;
		}
		World world = at.getWorld();
		CompletableFuture<?>[] loads = rule.chunks(found.start()).stream()
				.map(chunk -> world.getChunkAtAsync(chunk.x(), chunk.z()))
				.toArray(CompletableFuture[]::new);
		CompletableFuture.allOf(loads).whenComplete((done, error) -> Bukkit.getScheduler().runTask(Plugin.getInstance(), () -> {
			if(live.match(rule, found.start()) != null) {
				tell(p, at, found);
			} else {
				search(p, at, live, finder);
			}
		}));
	}

	private static void tell(Player p, Location at, StructureFinder.Found found) {
		BlockPos pos = found.pos();
		String direction = direction(pos.getX() - at.getX(), pos.getZ() - at.getZ());
		p.sendMessage(Utils.msg(switch(level) {
			case TYPE -> "<gray>" + found.type() + " " + direction;
			case COORDS -> "<gray>" + found.type() + " at " + pos.getX() + ", " + pos.getZ();
			default -> "<gray>" + direction;
		}));
	}

	/** North is -Z. */
	private static String direction(double dx, double dz) {
		double degrees = Math.toDegrees(Math.atan2(dx, -dz));
		return DIRECTIONS[Math.floorMod((int) Math.round(degrees / 22.5), DIRECTIONS.length)];
	}
}
