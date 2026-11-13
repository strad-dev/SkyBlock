package misc;

import org.bukkit.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class Random extends java.util.Random {
	private record Tick(long time, Random random) {}

	private static final Map<UUID, Tick> TICKS = new HashMap<>();

	public Random(long seed) {
		super(seed);
	}

	public static Random of(World world) {
		long time = world.getGameTime();
		Tick tick = TICKS.get(world.getUID());
		if(tick == null || tick.time() != time) {
			tick = new Tick(time, new Random(mix(world.getSeed() ^ mix(time))));
			TICKS.put(world.getUID(), tick);
		}
		return tick.random();
	}

	public static long mix(long z) {
		z += 0x9E3779B97F4A7C15L;
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}
}
