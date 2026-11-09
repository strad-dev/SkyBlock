package mobs.hardmode.withers;

import listeners.CustomMobs;
import listeners.DamageType;
import misc.DamageData;
import misc.Utils;
import mobs.CustomMob;
import mobs.withers.CustomWither;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.entity.CraftWither;
import org.bukkit.entity.*;

import java.util.ArrayList;
import java.util.List;

public class Goldor implements CustomWither {
	private static final String name = "<gold><bold>﴾ <red><bold>Goldor<gold><bold> ﴿";

	@Override
	public String onSpawn(Player p, Mob e) {
		e.setCanPickupItems(false);
		Wither wither;
		if(e instanceof Wither) {
			wither = (Wither) e;
		} else {
			throw new IllegalStateException("Uh oh!  Wrong mob type!");
		}

		//noinspection DuplicatedCode
		List<EntityType> immune = new ArrayList<>();
		immune.add(EntityType.WITHER_SKELETON);
		Utils.spawnTNT(wither, wither.getLocation(), 0, 32, 50, immune);
		Utils.playGlobalSound(Sound.ENTITY_WITHER_SPAWN);

		wither.getAttribute(Attribute.MAX_HEALTH).setBaseValue(1200.0);
		wither.setHealth(1200.0);
		wither.addScoreboardTag("Goldor");
		wither.addScoreboardTag("HardMode");
		wither.addScoreboardTag("SkyblockBoss");
		wither.setPersistent(true);
		wither.setRemoveWhenFarAway(false);
		Utils.changeName(wither, name);

		damageAll(wither);

		return name;
	}

	private void damageAll(Wither wither) {
		if(!wither.isDead() && !wither.getScoreboardTags().contains("Dead")) {
			Utils.spawnTNT(wither, wither.getLocation(), 0, 6, Math.min(30, 15 + (int) Math.floor((1200.0 - wither.getHealth()) / 60.0)), new ArrayList<>());
			Utils.scheduleTask(() -> damageAll(wither), 20);
		}
	}

	@Override
	public boolean whenDamaged(LivingEntity damagee, Entity damager, double originalDamage, DamageType type, DamageData data) {
		if(((Wither) damagee).getInvulnerableTicks() != 0 && type != DamageType.LETHAL_ABSOLUTE || type == DamageType.IFRAME_ENVIRONMENTAL) {
			return false;
		}

		CustomMobs.updateWitherLordFight(true);

		if(damagee.getScoreboardTags().contains("Invulnerable")) {
			Utils.changeName(damagee);
			return false;
		} else if(damagee.getHealth() - originalDamage < 1) {
			data.feedbackOnly = true;
			damagee.addScoreboardTag("Invulnerable");
			damagee.addScoreboardTag("Dead");
			damagee.setHealth(1.0);
			damagee.setSilent(true);
			damagee.setAI(false);
			Utils.changeName(damagee);
			WitherBoss nmsWither = ((CraftWither) damagee).getHandle();
			nmsWither.bossEvent.setProgress(nmsWither.getHealth() / 1200);
			Bukkit.broadcast(Utils.msg(name + "<red><bold>: How did you break through my shield???"));
			Utils.playGlobalSound(Sound.ENTITY_WITHER_AMBIENT);
			damagee.remove();
			Utils.playGlobalSound(Sound.ENTITY_WITHER_DEATH);
			Utils.spawnTNT(damagee, damagee.getLocation(), 0, 32, 50, new ArrayList<>());
			Utils.scheduleTask(() -> {
				Utils.playGlobalSound(Sound.ENTITY_WITHER_AMBIENT);
				Bukkit.broadcast(Utils.msg(name + "<red><bold>: Either way I don't think you're getting past Necron."));
			}, 40);
			Utils.scheduleTask(() -> {
				Utils.playGlobalSound(Sound.ENTITY_WITHER_AMBIENT);
				Bukkit.broadcast(Utils.msg("<gold><bold>﴾ <red><bold>Necron<gold><bold> ﴿<red><bold>: I have heard a lot about you."));
			}, 100);
			Utils.scheduleTask(() -> {
				Utils.playGlobalSound(Sound.ENTITY_WITHER_AMBIENT);
				Bukkit.broadcast(Utils.msg("<gold><bold>﴾ <red><bold>Necron<gold><bold> ﴿<red><bold>: Your perseverence is immeasurable, but your journey ends here.  Goodbye."));
			}, 140);
			Utils.scheduleTask(() -> {
				Wither wither = (Wither) damagee.getWorld().spawnEntity(damagee.getLocation(), EntityType.WITHER);
				CustomMob.getMob("Necron", true).onSpawn((damager instanceof Player p ? p : Utils.getNearestPlayer(damagee)), wither);
			}, 160);
			return false;
		}
		return true;
	}

	@Override
	public boolean whenDamaging(LivingEntity damagee, Entity damager, double originalDamage, DamageType type, DamageData data) {
		return true;
	}

	@Override
	public void whenShootingSkull(WitherSkull skull) {

	}
}
