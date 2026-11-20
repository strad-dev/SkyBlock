package listeners;

import io.papermc.paper.event.entity.TameableDeathMessageEvent;
import misc.Plugin;
import misc.Utils;
import mobs.enderDragons.CustomDragon;
import mobs.hardmode.enderDragons.PrimalDragon;
import mobs.hardmode.withers.Maxor;
import mobs.withers.CustomWither;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.TranslationArgument;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

public class CustomMobs implements Listener {
	private static boolean isWitherLordFightActive = false;

	public static void updateWitherLordFight(boolean isWitherLordFightActive) {
		CustomMobs.isWitherLordFightActive = isWitherLordFightActive;
	}

	// "Wolf ❤ 14/40 was slain by ...": the HP tag is still on the name when the owner's message is built.
	@EventHandler
	public void onPetDeathMessage(TameableDeathMessageEvent e) {
		if(!(e.deathMessage() instanceof TranslatableComponent msg)) return;
		List<Component> args = new ArrayList<>();
		for(TranslationArgument arg : msg.arguments()) {
			args.add(arg.value() instanceof Component c ? Utils.stripHealth(c) : arg.asComponent());
		}
		e.deathMessage(msg.arguments(args));
	}

	/**
	 * Summons lightning on every entity in a given radius
	 *
	 * @param entity the entity at the center
	 * @param radius the radius to spawn lightning on
	 */
	public static void spawnLightning(Entity entity, int radius) {
		spawnLightning(entity, radius, 0);
	}

	public static void spawnLightning(Entity entity, int radius, double boltDamage) {
		List<Entity> entities = (List<Entity>) entity.getWorld().getNearbyEntities(entity.getLocation(), radius, 320, radius);
		List<EntityType> doNotKill = CustomItems.createList();
		for(Entity temp : entities) {
			if(!doNotKill.contains(temp.getType()) && temp instanceof LivingEntity entity1) {
				strike(entity1.getLocation(), boltDamage);
			}
		}
	}

	private static NamespacedKey boltKey;

	private static NamespacedKey boltKey() {
		if(boltKey == null) boltKey = new NamespacedKey(Plugin.getInstance(), "bolt_damage");
		return boltKey;
	}

	public static void strike(Location l, double damage) {
		l.getWorld().spawn(l, LightningStrike.class, bolt -> {
			if(damage > 0) bolt.getPersistentDataContainer().set(boltKey(), PersistentDataType.DOUBLE, damage);
		});
	}

	// A bolt riding on a hit (Storm's bonus) -> what it has hit. A bolt strikes on several ticks and vanilla leans on
	// i-frames to make that one hit, but the hit that called it has just started the target's i-frames.
	private static final java.util.Map<Entity, java.util.Set<java.util.UUID>> bonusBolts = new java.util.WeakHashMap<>();

	/** Lands once per target and never meets i-frames, so the hit that called it can't block it. */
	public static void strikeBonus(Location l, double damage) {
		l.getWorld().spawn(l, LightningStrike.class, bolt -> {
			bolt.getPersistentDataContainer().set(boltKey(), PersistentDataType.DOUBLE, damage);
			bonusBolts.put(bolt, new java.util.HashSet<>());
		});
	}

	public static boolean isBonusBolt(Entity e) {
		return bonusBolts.containsKey(e);
	}

	/** True the first time this bonus bolt hits {@code target}, false after. */
	public static boolean firstBonusHit(Entity bolt, Entity target) {
		java.util.Set<java.util.UUID> hit = bonusBolts.get(bolt);
		return hit != null && hit.add(target.getUniqueId());
	}

	public static double boltDamage(Entity e) {
		if(!(e instanceof LightningStrike bolt)) return 0;
		Double damage = bolt.getPersistentDataContainer().get(boltKey(), PersistentDataType.DOUBLE);
		return damage == null ? 0 : damage;
	}

	private static final java.util.Set<org.bukkit.Material> DRAGON_PROOF = java.util.EnumSet.of(org.bukkit.Material.BEACON,
			org.bukkit.Material.IRON_BLOCK, org.bukkit.Material.GOLD_BLOCK, org.bukkit.Material.DIAMOND_BLOCK,
			org.bukkit.Material.EMERALD_BLOCK, org.bukkit.Material.NETHERITE_BLOCK);

	// EnderDragon.checkWalls reports the blocks it flies through as an explode event, and only breaks what's left in
	// the list. Beacons and their bases stay.
	@EventHandler(ignoreCancelled = true)
	public void onDragonGrief(org.bukkit.event.entity.EntityExplodeEvent e) {
		if(e.getEntity() instanceof EnderDragon) e.blockList().removeIf(b -> DRAGON_PROOF.contains(b.getType()));
	}

	@EventHandler(ignoreCancelled = true)
	public void onBoltIgnite(BlockIgniteEvent e) {
		if(boltDamage(e.getIgnitingEntity()) > 0) e.setCancelled(true);
	}

	@EventHandler(ignoreCancelled = true)
	public void onBoltCombust(EntityCombustByEntityEvent e) {
		if(boltDamage(e.getCombuster()) > 0) e.setCancelled(true);
	}

	/**
	 * Returns the player that triggers hard mode for this spawn, or null if not hard mode.
	 */
	private Player getHardModePlayer(Player nearest, EntitySpawnEvent e) {
		if(nearest == null) return null;
		if(nearest.hasPotionEffect(PotionEffectType.BAD_OMEN)) return nearest;
		for(Player p2 : e.getEntity().getWorld().getPlayers()) {
			if(p2.hasPotionEffect(PotionEffectType.BAD_OMEN)) {
				return p2;
			}
		}
		return null;
	}

	@EventHandler
	public void onEntitySpawn(EntitySpawnEvent e) {
		if(e.getEntity() instanceof LivingEntity entity) {
			// Depth Strider 3 on every boss + subentity (all tagged "SkyblockBoss") so they don't crawl through
			// water. A tick late: the tag is added during/after this event.
			Utils.scheduleTask(() -> {
				if(entity.isValid() && entity.getScoreboardTags().contains("SkyblockBoss")) {
					Utils.applyDepthStrider(entity);
				}
			}, 1);

			String name = "";

			// MAXOR, STORM, GOLDOR, NECRON
			try {
				switch(entity) {
					case Wither wither -> {
						Player p = Utils.getNearestPlayer(e.getEntity(), 64);
						Player hardModePlayer = getHardModePlayer(p, e);
						wither.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
						if(!isWitherLordFightActive) {
							if(hardModePlayer != null) {
								hardModePlayer.removePotionEffect(PotionEffectType.BAD_OMEN);
								Utils.scheduleTask(wither::remove, 1);
								Utils.scheduleTask(() -> {
									Wither wither2 = (Wither) wither.getWorld().spawnEntity(wither.getLocation(), EntityType.WITHER);
									new Maxor().onSpawn(p, wither2);
								}, 120);
								isWitherLordFightActive = true;
								Utils.scheduleTask(() -> {
									Utils.playGlobalSound(Sound.ENTITY_WITHER_AMBIENT);
									Bukkit.broadcast(Utils.msg("<gold><bold>﴾ <red><bold>Maxor<gold><bold> ﴿<red><bold>: WELL WELL WELL LOOK WHO'S BACK FOR A REMATCH!"));
								}, 20);
								Utils.scheduleTask(() -> {
									Utils.playGlobalSound(Sound.ENTITY_WITHER_AMBIENT);
									Bukkit.broadcast(Utils.msg("<gold><bold>﴾ <red><bold>Maxor<gold><bold> ﴿<red><bold>: MY TRICKS ARE MORE SOPHISTICATED THAN EVER; YOU WILL NEVER GET AROUND THEM!"));
								}, 60);
								Utils.scheduleTask(() -> {
									Utils.playGlobalSound(Sound.ENTITY_WITHER_AMBIENT);
									Bukkit.broadcast(Utils.msg("<gold><bold>﴾ <red><bold>Maxor<gold><bold> ﴿<red><bold>: NOW LET'S HAVE SOME FUN HERE!"));
								}, 100);
								Bukkit.getLogger().info("A player has initiated the Wither Lords fight!");
							} else {
								name = CustomWither.spawnRandom(wither.getWorld()).onSpawn(p, wither);
								Utils.setupBoss(wither, p);
							}
						} else {
							return;
						}
					}
					case EnderDragon dragon -> {
						Player p = Utils.getNearestPlayer(e.getEntity(), 128);
						Player hardModePlayer = getHardModePlayer(p, e);
						dragon.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
						if(hardModePlayer != null) {
							hardModePlayer.removePotionEffect(PotionEffectType.BAD_OMEN);
							name = new PrimalDragon().onSpawn(hardModePlayer, dragon);
						} else {
							name = CustomDragon.spawnRandom(dragon.getWorld()).onSpawn(p, dragon);
							Utils.setupBoss(dragon, p);
						}
					}
					default -> {
					}
				}
			} catch(Exception exception) {
				// do nothing
			}
			// add health to the entity name if it doesn't exist already
			if(name.isEmpty()) {
				// Already named (a CustomPig that copied its name across the swap, another plugin)? Leave it:
				// rebuilding from legacy getName() loses colours (red ❤ turns <aqua>) and MiniMessage can't parse
				// § codes. HP stays current via Utils.changeName(entity) on hits.
				if(entity.customName() != null) {
					// Exception: a splitting cube mob inherits its parent's name WHOLE (COMPONENTS_TO_COPY is
					// {CUSTOM_NAME, CUSTOM_DATA}) while setSize already shrank its max HP, so a size-1 slime off a
					// size-4 showed 16/16 on 1 HP. The one-arg changeName swaps only the trailing numbers, keeping
					// colours and any name-tagged base name.
					if(e instanceof CreatureSpawnEvent spawn
							&& spawn.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SLIME_SPLIT) {
						// CustomDrops' death case already reset the parent to a bare "Slime"/"Magma Cube".
						if(entity.getName().contains("❤")) Utils.changeName(entity);
						else Utils.changeName(entity, "<aqua>" + entity.getName());
					}
					entity.setCustomNameVisible(true);
					return;
				}
				name = "<aqua>" + entity.getName();
			}
			if(!name.contains("❤")) {
				Utils.changeName(entity, name);
			} else {
				entity.customName(Utils.msg(name));
			}
			entity.setCustomNameVisible(true);
		}
	}
}