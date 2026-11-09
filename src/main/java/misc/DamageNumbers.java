package misc;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

import java.util.*;

public final class DamageNumbers {
	private static final int LIFETIME_TICKS = 20;
	private static final int MAX_PER_PLAYER = 100;
	private static final double SPAWN_RADIUS = 1.0;
	private static final int SPAWN_ATTEMPTS = 12;
	private static final String[] CRIT_CADENCE = {"<green>", "<gold>", "<red>", "<red>", "<gold>", "<green>",
			"<green>", "<gold>", "<red>"};

	private static final Random RANDOM = new Random();
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

	public static void show(LivingEntity target, double damage, boolean crit, Player attacker) {
		show(target, damage, crit ? null : "<gray>", attacker);
	}

	public static void show(LivingEntity target, double damage, @javax.annotation.Nullable String colour, Player attacker) {
		if(!enabled || target == null || damage <= 0 || attacker == null) return;
		Location at = spawnPoint(target);

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

	private static Location spawnPoint(LivingEntity target) {
		Location eyes = target.getEyeLocation();
		for(int i = 0; i < SPAWN_ATTEMPTS; i++) {
			double theta = RANDOM.nextDouble() * Math.PI * 2;
			double y = RANDOM.nextDouble() * 2 - 1;
			double ring = Math.sqrt(1 - y * y);
			double r = SPAWN_RADIUS * Math.cbrt(RANDOM.nextDouble());
			Location at = eyes.clone().add(r * ring * Math.cos(theta), r * y, r * ring * Math.sin(theta));
			if(at.getBlock().isPassable()) return at;
		}
		return eyes;
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
