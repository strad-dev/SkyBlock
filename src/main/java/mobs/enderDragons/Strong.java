package mobs.enderDragons;

import listeners.DamageType;
import misc.DamageData;
import misc.Utils;
import org.bukkit.Bukkit;
import org.bukkit.entity.*;

import listeners.CustomDamage;

public class Strong implements CustomDragon {
	@Override
	public void whenShootingFireball(DragonFireball fireball) {

	}

	@Override
	public String onSpawn(Player p, Mob e) {
		e.setCanPickupItems(false);
		EnderDragon dragon;
		if(e instanceof EnderDragon) {
			dragon = (EnderDragon) e;
		} else {
			throw new IllegalStateException("Uh oh!  Wrong mob type!");
		}

		String name = "<gold><bold>﴾ <red><bold>Strong Dragon<gold><bold> ﴿";
		dragon.addScoreboardTag("StrongDragon");
		Bukkit.broadcast(Utils.msg("<red><bold>The STRONG DRAGON has arrived to pulverize you!"));
		Bukkit.getLogger().info("The Strong Dragon has been summoned!");
		return name;
	}

	@Override
	public boolean whenDamaged(LivingEntity damagee, Entity damager, double originalDamage, DamageType type, DamageData data) {
		return true;
	}

	@Override
	public boolean whenDamaging(LivingEntity damagee, Entity damager, double originalDamage, DamageType type, DamageData data) {
		data.onLanded = () -> CustomDamage.dealBonus(damagee, damager, 4);
		return true;
	}
}
