package listeners;

import mobs.CustomMob;
import mobs.enderDragons.CustomDragon;
import mobs.withers.CustomWither;
import org.bukkit.entity.DragonFireball;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Item;
import org.bukkit.entity.Wither;
import org.bukkit.entity.WitherSkull;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;

public class EditSkull implements Listener {
	@EventHandler
	public void onProjectileLaunch(ProjectileLaunchEvent e) {
		if(e.getEntity() instanceof WitherSkull skull && skull.getShooter() != null) {
			CustomWither wither = (CustomWither) CustomMob.getMob((Wither) skull.getShooter());
			wither.whenShootingSkull(skull);
		} else if(e.getEntity() instanceof DragonFireball fireball) {
			try {
				CustomDragon dragon = (CustomDragon) CustomMob.getMob((EnderDragon) fireball.getShooter());
				dragon.whenShootingFireball(fireball);
			} catch(Exception exception) {
				// continue
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onItemDamage(EntityDamageByEntityEvent e) {
		if(e.getEntity() instanceof Item && e.getDamager() instanceof WitherSkull) {
			e.setCancelled(true);
		}
	}
}
