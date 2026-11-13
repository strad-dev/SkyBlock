package misc;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.*;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;

public final class DamageNumbers {
	private static final int LIFETIME_TICKS = 20;
	private static final int MAX_PER_PLAYER = 100;
	private static final double SPAWN_RADIUS = 1.0;
	private static final int SPAWN_ATTEMPTS = 12;
	private static final double PART_RAY_RANGE = 64;
	private static final double PART_RAY_MARGIN = 0.3;
	private static final String[] CRIT_CADENCE = {"<green>", "<gold>", "<red>", "<red>", "<gold>", "<green>",
			"<green>", "<gold>", "<red>"};

	private static final Map<UUID, Deque<TextDisplay>> LIVE = new HashMap<>();

	private static boolean enabled = true;

	private DamageNumbers() {
	}

	public static void setEnabled(boolean value) {
		enabled = value;
	}

	public static void removeAll() {
		for(Deque<TextDisplay> q : LIVE.values()) {
			for(TextDisplay d : q) {
				if(d.isValid()) d.remove();
			}
		}
		LIVE.clear();
	}

	public static void show(LivingEntity target, double damage, boolean crit, Player attacker, @javax.annotation.Nullable Entity hitBy) {
		show(target, damage, crit ? null : "<gray>", attacker, hitBy);
	}

	public static void show(LivingEntity target, double damage, @javax.annotation.Nullable String colour, Player attacker, @javax.annotation.Nullable Entity hitBy) {
		if(!enabled || target == null || damage <= 0 || attacker == null) return;
		Location at = spawnPoint(target, hitBy);

		Deque<TextDisplay> q = LIVE.computeIfAbsent(attacker.getUniqueId(), k -> new ArrayDeque<>());
		while(q.size() >= MAX_PER_PLAYER) {
			TextDisplay oldest = q.pollFirst();
			if(oldest != null && oldest.isValid()) oldest.remove();
		}

		TextDisplay display = target.getWorld().spawn(at, TextDisplay.class, d -> {
			d.text(Utils.msg(format(damage, colour)));
			d.setBillboard(Display.Billboard.CENTER);
			d.setSeeThrough(true);
			d.setShadowed(true);
			d.setViewRange(0.4f);
			d.setPersistent(false);
		});
		q.addLast(display);

		Bukkit.getScheduler().runTaskLater(Plugin.getInstance(), () -> {
			if(display.isValid()) display.remove();
			Deque<TextDisplay> live = LIVE.get(attacker.getUniqueId());
			if(live != null) live.remove(display);
		}, LIFETIME_TICKS);
	}

	private static Location spawnPoint(LivingEntity target, @javax.annotation.Nullable Entity hitBy) {
		Location eyes = centre(target, hitBy);
		Random random = Random.of(target.getWorld());
		for(int i = 0; i < SPAWN_ATTEMPTS; i++) {
			double theta = random.nextDouble() * Math.PI * 2;
			double y = random.nextDouble() * 2 - 1;
			double ring = Math.sqrt(1 - y * y);
			double r = SPAWN_RADIUS * Math.cbrt(random.nextDouble());
			Location at = eyes.clone().add(r * ring * Math.cos(theta), r * y, r * ring * Math.sin(theta));
			if(at.getBlock().isPassable()) return at;
		}
		return eyes;
	}

	private static Location centre(LivingEntity target, @javax.annotation.Nullable Entity hitBy) {
		if(!(target instanceof ComplexLivingEntity complex) || hitBy == null) return target.getEyeLocation();
		Vector origin;
		Vector direction;
		if(hitBy instanceof LivingEntity living) {
			origin = living.getEyeLocation().toVector();
			direction = living.getEyeLocation().getDirection();
		} else {
			origin = hitBy.getLocation().toVector();
			direction = hitBy.getVelocity();
		}
		BoundingBox best = null;
		double bestDistance = Double.MAX_VALUE;
		if(direction.lengthSquared() > 1.0E-6) {
			for(ComplexEntityPart part : complex.getParts()) {
				BoundingBox box = part.getBoundingBox().expand(PART_RAY_MARGIN);
				RayTraceResult hit = box.rayTrace(origin, direction, PART_RAY_RANGE);
				if(hit == null) continue;
				double d = hit.getHitPosition().distanceSquared(origin);
				if(d < bestDistance) {
					bestDistance = d;
					best = part.getBoundingBox();
				}
			}
		}
		if(best == null) {
			for(ComplexEntityPart part : complex.getParts()) {
				BoundingBox box = part.getBoundingBox();
				Vector closest = new Vector(Math.clamp(origin.getX(), box.getMinX(), box.getMaxX()),
						Math.clamp(origin.getY(), box.getMinY(), box.getMaxY()),
						Math.clamp(origin.getZ(), box.getMinZ(), box.getMaxZ()));
				double d = closest.distanceSquared(origin);
				if(d < bestDistance) {
					bestDistance = d;
					best = box;
				}
			}
		}
		return best == null ? target.getEyeLocation() : best.getCenter().toLocation(target.getWorld());
	}

	private static String format(double damage, @javax.annotation.Nullable String colour) {
		String digits = Utils.damageNumber(damage);
		if(colour != null) return colour + digits;
		StringBuilder sb = new StringBuilder("<white>✧");
		int step = 0;
		for(int i = 0; i < digits.length(); i++) {
			char c = digits.charAt(i);
			if(Character.isDigit(c)) {
				sb.append(CRIT_CADENCE[step % CRIT_CADENCE.length]);
				step++;
			}
			sb.append(c);
		}
		sb.append("<white>✧");
		return sb.toString();
	}
}
