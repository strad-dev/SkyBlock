package listeners;

import items.ingredients.mining.Alloy;
import items.ingredients.misc.EnchantmentUpgrader;
import manhunt.Manhunt;
import misc.Random;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Chest;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

public class CustomChestLoot implements Listener {
	@EventHandler
	public void onLootGenerate(LootGenerateEvent e) {
		if(e.getInventoryHolder() instanceof Chest chest) {
			double roll = seeded(chest.getLocation(), 0).nextDouble();
			Inventory inventory = chest.getBlockInventory();
			if(roll < 0.005) { // 0.5%
				if(!Manhunt.limitsT6()) inventory.addItem(EnchantmentUpgrader.getItem());
			} else if(roll < 0.01) { // 0.5%
				addBook(inventory, Enchantment.LOOTING, 4);
			} else if(roll < 0.015) { // 0.5%
				addBook(inventory, Enchantment.FORTUNE, 4);
			} else if(roll < 0.025) { // 1%
				addBook(inventory, Enchantment.SHARPNESS, 6);
			} else if(roll < 0.035) { // 1%
				addBook(inventory, Enchantment.POWER, 6);
			} else if(roll < 0.05) { // 1.5%
				addBook(inventory, Enchantment.PROTECTION, 5);
			} else if(roll < 0.06) { // 1%
				addBook(inventory, Enchantment.SWEEPING_EDGE, 4);
			}
		} else if(e.getInventoryHolder() instanceof StorageMinecart minecart) {
			if(seeded(minecart.getLocation(), 1).nextDouble() < 0.04) {
				minecart.getInventory().addItem(Alloy.getItem());
			}
		}
	}

	private static void addBook(Inventory inventory, Enchantment enchant, int level) {
		if(!Manhunt.claimBook(enchant)) return;
		ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
		EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
		meta.addStoredEnchant(enchant, level, true);
		book.setItemMeta(meta);

		inventory.addItem(book);
	}

	private static Random seeded(Location location, long salt) {
		long h = location.getWorld().getSeed();
		h = Random.mix(h ^ location.getWorld().getEnvironment().ordinal());
		h = Random.mix(h ^ location.getBlockX());
		h = Random.mix(h ^ location.getBlockY());
		h = Random.mix(h ^ location.getBlockZ());
		return new Random(Random.mix(h ^ salt));
	}
}
