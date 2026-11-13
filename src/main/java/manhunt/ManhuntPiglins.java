package manhunt;

import misc.Random;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Piglin;
import org.bukkit.entity.PiglinBrute;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.List;
import java.util.function.Function;

/** Manhunt nether rules: 1.16.1 barters, no brutes. Listener gates both on {@link Manhunt#active()}. */
public final class ManhuntPiglins {
	private ManhuntPiglins() {}

	private record Barter(int weight, Function<Random, ItemStack> item) {}

	private static final List<Barter> TABLE = List.of(
			new Barter(5, r -> soulSpeed(r, new ItemStack(Material.ENCHANTED_BOOK))),
			new Barter(8, r -> soulSpeed(r, new ItemStack(Material.IRON_BOOTS))),
			new Barter(10, r -> potion(Material.POTION, PotionType.FIRE_RESISTANCE)),
			new Barter(10, r -> potion(Material.SPLASH_POTION, PotionType.FIRE_RESISTANCE)),
			new Barter(10, r -> potion(Material.POTION, PotionType.WATER)),
			new Barter(10, r -> stack(r, Material.IRON_NUGGET, 9, 36)),
			new Barter(20, r -> stack(r, Material.QUARTZ, 8, 16)),
			new Barter(20, r -> stack(r, Material.GLOWSTONE_DUST, 5, 12)),
			new Barter(20, r -> stack(r, Material.MAGMA_CREAM, 2, 6)),
			new Barter(25, r -> stack(r, Material.ENDER_PEARL, 4, 8)),
			new Barter(20, r -> stack(r, Material.STRING, 8, 24)),
			new Barter(10, r -> new ItemStack(Material.DRIED_GHAST)),
			new Barter(40, r -> stack(r, Material.FIRE_CHARGE, 1, 5)),
			new Barter(40, r -> stack(r, Material.GRAVEL, 8, 16)),
			new Barter(40, r -> stack(r, Material.LEATHER, 4, 10)),
			new Barter(40, r -> stack(r, Material.NETHER_BRICK, 4, 16)),
			new Barter(40, r -> new ItemStack(Material.OBSIDIAN)),
			new Barter(40, r -> stack(r, Material.CRYING_OBSIDIAN, 1, 3)),
			new Barter(40, r -> stack(r, Material.SOUL_SAND, 4, 16)),
			new Barter(40, r -> stack(r, Material.SPECTRAL_ARROW, 6, 12)),
			new Barter(40, r -> stack(r, Material.BLACKSTONE, 8, 16)));

	private static final int TOTAL_WEIGHT = TABLE.stream().mapToInt(Barter::weight).sum();

	public static void reroll(Piglin piglin, List<ItemStack> outcome) {
		Random random = Random.of(piglin.getWorld());
		int roll = random.nextInt(TOTAL_WEIGHT);
		for(Barter barter : TABLE) {
			roll -= barter.weight();
			if(roll < 0) {
				outcome.clear();
				outcome.add(barter.item().apply(random));
				return;
			}
		}
	}

	private static ItemStack stack(Random random, Material material, int min, int max) {
		return new ItemStack(material, random.nextInt(min, max + 1));
	}

	private static ItemStack soulSpeed(Random random, ItemStack item) {
		int level = random.nextInt(Enchantment.SOUL_SPEED.getStartLevel(), Enchantment.SOUL_SPEED.getMaxLevel() + 1);
		if(item.getItemMeta() instanceof EnchantmentStorageMeta) {
			item.editMeta(EnchantmentStorageMeta.class, meta -> meta.addStoredEnchant(Enchantment.SOUL_SPEED, level, false));
		} else {
			item.addEnchantment(Enchantment.SOUL_SPEED, level);
		}
		return item;
	}

	private static ItemStack potion(Material material, PotionType type) {
		ItemStack item = new ItemStack(material);
		item.editMeta(PotionMeta.class, meta -> meta.setBasePotionType(type));
		return item;
	}

	/** Permanent: reset doesn't restore brutes, nothing records where they were. */
	public static void demote(PiglinBrute brute) {
		if(!brute.isValid()) return;
		Location where = brute.getLocation();
		boolean immune = brute.isImmuneToZombification();
		boolean staysPut = !brute.getRemoveWhenFarAway();
		brute.remove();
		replace(where, immune, staysPut);
	}

	/** Keeps the brute's zombification immunity and despawn flag; otherwise a default piglin, gear included. */
	public static void replace(Location where, boolean immune, boolean staysPut) {
		World world = where.getWorld();
		if(world == null) return;
		world.spawn(where, Piglin.class, CreatureSpawnEvent.SpawnReason.CUSTOM, piglin -> {
			piglin.setImmuneToZombification(immune);
			piglin.setRemoveWhenFarAway(!staysPut);
		});
	}

	/** Run on start: already-loaded brutes are past both the spawn event and the chunk load. */
	public static void demoteLoaded() {
		for(World world : Bukkit.getWorlds()) {
			for(PiglinBrute brute : world.getEntitiesByClass(PiglinBrute.class)) {
				demote(brute);
			}
		}
	}
}
