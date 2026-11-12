package listeners;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonChargePlayerPhase;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonDeathPhase;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonHoldingPatternPhase;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonLandingApproachPhase;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonLandingPhase;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonPhaseInstance;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonStrafePlayerPhase;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonTakeoffPhase;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhaseManager;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.entity.CraftEnderDragon;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.function.Function;

// BACKPORT of MC-272431 (26.4 snapshot 8): EnderDragon.aiStep climbs by ydd * 0.01, fixed to 0.1. Delete on 26.4.
public final class DragonFlight implements Listener {
	private static final double MISSING_LIFT = 0.1 - 0.01;

	private static final Map<EnderDragonPhase<?>, Function<EnderDragon, DragonPhaseInstance>> FIXED = Map.of(
			EnderDragonPhase.HOLDING_PATTERN, HoldingPattern::new,
			EnderDragonPhase.STRAFE_PLAYER, StrafePlayer::new,
			EnderDragonPhase.LANDING_APPROACH, LandingApproach::new,
			EnderDragonPhase.LANDING, Landing::new,
			EnderDragonPhase.TAKEOFF, Takeoff::new,
			EnderDragonPhase.CHARGING_PLAYER, ChargePlayer::new,
			EnderDragonPhase.DYING, Death::new);

	private static Field phasesField, currentField;

	public DragonFlight() {
		try {
			phasesField = EnderDragonPhaseManager.class.getDeclaredField("phases");
			currentField = EnderDragonPhaseManager.class.getDeclaredField("currentPhase");
			phasesField.setAccessible(true);
			currentField.setAccessible(true);
		} catch(ReflectiveOperationException e) {
			Bukkit.getLogger().warning("DragonFlight: phase manager fields not found, dragon climb fix off.");
			phasesField = null;
			return;
		}
		for(World world : Bukkit.getWorlds()) {
			world.getEntitiesByClass(org.bukkit.entity.EnderDragon.class).forEach(DragonFlight::install);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onAdd(EntityAddToWorldEvent e) {
		if(e.getEntity() instanceof org.bukkit.entity.EnderDragon dragon) install(dragon);
	}

	private static void install(org.bukkit.entity.EnderDragon bukkitDragon) {
		if(phasesField == null || !(bukkitDragon instanceof CraftEnderDragon craft)) return;
		EnderDragon dragon = craft.getHandle();
		EnderDragonPhaseManager manager = dragon.getPhaseManager();
		try {
			DragonPhaseInstance[] phases = (DragonPhaseInstance[]) phasesField.get(manager);
			DragonPhaseInstance current = (DragonPhaseInstance) currentField.get(manager);
			for(Map.Entry<EnderDragonPhase<?>, Function<EnderDragon, DragonPhaseInstance>> fixed : FIXED.entrySet()) {
				int id = fixed.getKey().getId();
				if(phases[id] instanceof Lifted) continue;
				phases[id] = fixed.getValue().apply(dragon);
				// what setPhase would do; the replaced instance's state is lost, begin() starts it fresh
				if(current != null && current.getPhase() == fixed.getKey()) {
					currentField.set(manager, phases[id]);
					phases[id].begin();
				}
			}
		} catch(ReflectiveOperationException e) {
			e.printStackTrace();
		}
	}

	// getFlySpeed is read once per aiStep, after the climb is worked out and before ydd * 0.01 is added: add the rest here
	private static float lift(EnderDragon dragon, Vec3 target, float speed) {
		if(target == null) return speed;
		double dx = target.x - dragon.getX();
		double dy = target.y - dragon.getY();
		double dz = target.z - dragon.getZ();
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		if(horizontal > 0) dy = Mth.clamp(dy / horizontal, -speed, speed);
		dragon.setDeltaMovement(dragon.getDeltaMovement().add(0, dy * MISSING_LIFT, 0));
		return speed;
	}

	private interface Lifted {}

	private static final class HoldingPattern extends DragonHoldingPatternPhase implements Lifted {
		HoldingPattern(EnderDragon dragon) { super(dragon); }
		@Override public float getFlySpeed() { return lift(dragon, getFlyTargetLocation(), super.getFlySpeed()); }
	}

	private static final class StrafePlayer extends DragonStrafePlayerPhase implements Lifted {
		StrafePlayer(EnderDragon dragon) { super(dragon); }
		@Override public float getFlySpeed() { return lift(dragon, getFlyTargetLocation(), super.getFlySpeed()); }
	}

	private static final class LandingApproach extends DragonLandingApproachPhase implements Lifted {
		LandingApproach(EnderDragon dragon) { super(dragon); }
		@Override public float getFlySpeed() { return lift(dragon, getFlyTargetLocation(), super.getFlySpeed()); }
	}

	private static final class Landing extends DragonLandingPhase implements Lifted {
		Landing(EnderDragon dragon) { super(dragon); }
		@Override public float getFlySpeed() { return lift(dragon, getFlyTargetLocation(), super.getFlySpeed()); }
	}

	private static final class Takeoff extends DragonTakeoffPhase implements Lifted {
		Takeoff(EnderDragon dragon) { super(dragon); }
		@Override public float getFlySpeed() { return lift(dragon, getFlyTargetLocation(), super.getFlySpeed()); }
	}

	private static final class ChargePlayer extends DragonChargePlayerPhase implements Lifted {
		ChargePlayer(EnderDragon dragon) { super(dragon); }
		@Override public float getFlySpeed() { return lift(dragon, getFlyTargetLocation(), super.getFlySpeed()); }
	}

	private static final class Death extends DragonDeathPhase implements Lifted {
		Death(EnderDragon dragon) { super(dragon); }
		@Override public float getFlySpeed() { return lift(dragon, getFlyTargetLocation(), super.getFlySpeed()); }
	}
}
