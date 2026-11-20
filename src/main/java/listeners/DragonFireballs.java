package listeners;

import misc.Plugin;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.phys.AABB;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.entity.DragonFireball;
import org.bukkit.entity.EnderDragon;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.WeakHashMap;

// BACKPORT of 26.4's Projectile fix: a dragon fireball hit its own dragon (head part) on spawn. 26.2's canHitEntity
// only spares the owner's vehicle, not its parts, and isOutsideOwnerCollisionRange skips the unpickable dragon body,
// so the fireball "left" on tick 1. 26.4 spares the owner's whole entity graph until the fireball clears the owner's
// box. Delete on 26.4.
public final class DragonFireballs implements Listener {
	// Fireballs still inside their dragon, until 26.4's leftOwner would latch; the task re-checks each tick.
	private static final Map<DragonFireball, BukkitTask> notLeft = new WeakHashMap<>();

	@EventHandler(ignoreCancelled = true)
	public void onLaunch(ProjectileLaunchEvent e) {
		if(!(e.getEntity() instanceof DragonFireball fireball) || !(fireball.getShooter() instanceof EnderDragon)) return;
		notLeft.put(fireball, Bukkit.getScheduler().runTaskTimer(Plugin.getInstance(), () -> {
			if(!fireball.isValid() || hasLeft(fireball)) {
				BukkitTask task = notLeft.remove(fireball);
				if(task != null) task.cancel();
			}
		}, 1L, 1L));
	}

	@EventHandler(ignoreCancelled = true)
	public void onHit(ProjectileHitEvent e) {
		if(!(e.getEntity() instanceof DragonFireball fireball) || e.getHitEntity() == null || !notLeft.containsKey(fireball)) return;
		// vanilla checks leftOwner in the same tick, before the hit, from the same position and motion
		if(hasLeft(fireball)) {
			notLeft.remove(fireball).cancel();
			return;
		}
		Entity owner = ((CraftEntity) fireball.getShooter()).getHandle();
		if(graphRoot(((CraftEntity) e.getHitEntity()).getHandle()) == graphRoot(owner)) e.setCancelled(true);
	}

	// 26.4 isOutsideOwnerCollisionRange: no CAN_BE_PICKED filter, so the dragon's own box counts.
	private static boolean hasLeft(DragonFireball fireball) {
		if(!(fireball.getShooter() instanceof EnderDragon dragon)) return true;
		net.minecraft.world.entity.projectile.Projectile nms = (net.minecraft.world.entity.projectile.Projectile) ((CraftEntity) fireball).getHandle();
		AABB box = nms.getBoundingBox().expandTowards(nms.getDeltaMovement()).inflate(1.0);
		return ((CraftEntity) dragon).getHandle().getRootVehicle().getSelfAndPassengers().noneMatch(e -> box.intersects(e.getBoundingBox()));
	}

	// 26.4 Entity.getGraphRoot: a part answers for its dragon.
	private static Entity graphRoot(Entity e) {
		return e instanceof EnderDragonPart part ? part.parentMob.getRootVehicle() : e.getRootVehicle();
	}
}
