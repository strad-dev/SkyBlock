package listeners;

import items.ingredients.mining.*;
import misc.Plugin;
import misc.Random;
import misc.Utils;
import mobs.hardmode.generic.VoidcrazedSeraph;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static misc.Utils.sendRareDropMessage;

public class CustomMining implements Listener {
	// Players defusing a Voidgloom yang-glyph beacon. crackId is ours, not the player's entity id, so vanilla's own
	// (slower) crack broadcast for that player never overwrites it; the client draws the higher of the two.
	private record Defuse(Block block, BukkitTask task, int crackId) {}
	private final Map<UUID, Defuse> defusing = new HashMap<>();

	/**
	 * Voidgloom yang-glyph beacon: defuse it on a fixed timer (3s fist, 2s Divan's Pickaxe) instead of the slow
	 * vanilla beacon dig, which tool tier doesn't speed up. Defusing removes it so the boss never detonates it.
	 */
	@EventHandler(ignoreCancelled = true)
	public void onBeaconDamage(BlockDamageEvent e) {
		startDefuse(e.getPlayer(), e.getBlock(), false);
	}

	// Adventure: with no can_break item the client never digs a beacon, it only swings, which Paper reports as
	// LEFT_CLICK_BLOCK. Holding the button doesn't swing again, so one click starts it and looking away aborts.
	@EventHandler
	public void onBeaconClick(PlayerInteractEvent e) {
		if(e.getAction() != Action.LEFT_CLICK_BLOCK || e.getClickedBlock() == null || e.getPlayer().getGameMode() != GameMode.ADVENTURE) return;
		startDefuse(e.getPlayer(), e.getClickedBlock(), true);
	}

	@EventHandler
	public void onBeaconAbort(BlockDamageAbortEvent e) {
		cancelBeacon(e.getPlayer().getUniqueId());
	}

	private void startDefuse(Player p, Block b, boolean mustLook) {
		if(!VoidcrazedSeraph.isVoidgloomBeacon(b) || b.getType() != Material.BEACON) return;
		UUID id = p.getUniqueId();
		Defuse current = defusing.get(id);
		if(current != null && b.equals(current.block())) return; // already breaking this beacon
		cancelBeacon(id);
		int total = isDivanPickaxe(p.getInventory().getItemInMainHand()) ? 40 : 60; // 2s Divan's (33% faster), 3s otherwise
		int crackId = -1 - p.getEntityId();
		BukkitTask task = new BukkitRunnable() {
			int ticks = 0;

			@Override
			public void run() {
				if(!p.isOnline() || !VoidcrazedSeraph.isVoidgloomBeacon(b) || b.getType() != Material.BEACON || mustLook && !lookingAt(p, b)) {
					cancelBeacon(id);
					return;
				}
				if(++ticks >= total) {
					cancelBeacon(id);
					b.setType(Material.AIR); // defused before the boss can detonate it
					b.getWorld().playSound(b.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.0f);
					return;
				}
				sendCrack(b, crackId, (float) ticks / total);
			}
		}.runTaskTimer(Plugin.getInstance(), 0L, 1L);
		defusing.put(id, new Defuse(b, task, crackId));
	}

	private void cancelBeacon(UUID id) {
		Defuse d = defusing.remove(id);
		if(d == null) return;
		d.task().cancel();
		sendCrack(d.block(), d.crackId(), 0); // 0 clears it
	}

	private static boolean lookingAt(Player p, Block b) {
		RayTraceResult hit = p.rayTraceBlocks(p.getAttribute(Attribute.BLOCK_INTERACTION_RANGE).getValue());
		return hit != null && b.equals(hit.getHitBlock());
	}

	// Vanilla's broadcast range for block cracks.
	private static void sendCrack(Block b, int crackId, float progress) {
		Location l = b.getLocation();
		for(Player viewer : b.getWorld().getPlayers()) {
			if(viewer.getLocation().distanceSquared(l) < 32 * 32) viewer.sendBlockDamage(l, progress, crackId);
		}
	}

	private static boolean isDivanPickaxe(ItemStack item) {
		return Utils.firstLorePlain(item).equals("skyblock/combat/divan_pickaxe");
	}

	@EventHandler
	public void onBlockBreak(BlockBreakEvent e) {
		Player p = e.getPlayer();
		Block b = e.getBlock();
		if(VoidcrazedSeraph.isVoidgloomBeacon(b)) {
			e.setDropItems(false);
			return;
		}
		Location l = b.getLocation();
		World world = l.getWorld();
		ItemStack itemInHand = p.getInventory().getItemInMainHand();
		Random random = Random.of(world);
		if(itemInHand.hasItemMeta() && itemInHand.getItemMeta().hasEnchant(Enchantment.FORTUNE)) {
			boolean dropDouble = Utils.firstLorePlain(itemInHand).contains("skyblock/combat/divan_pickaxe");
			int fortune = itemInHand.getItemMeta().getEnchantLevel(Enchantment.FORTUNE);

			double fortuneMulti = 1 + 0.25 * fortune;
			ItemStack item;
			Location dropLocation = e.getBlock().getLocation().add(0.5, 0.5, 0.5);
			switch(b.getType()) {
				case STONE, DEEPSLATE -> {
					if(dropDouble) {
						e.setCancelled(true);
						if(b.getType().equals(Material.STONE)) {
							item = new ItemStack(Material.COBBLESTONE);
						} else {
							item = new ItemStack(Material.COBBLED_DEEPSLATE);
						}
						item.setAmount(2);
						world.dropItemNaturally(dropLocation, item);
						e.getBlock().getWorld().getBlockAt(dropLocation).setType(Material.AIR);
						Utils.damageItem(p, itemInHand, 1); // *technically* this isnt needed becuase only divan pick can drop double
					}
					if(random.nextDouble() < 0.001 * fortuneMulti) {
						ItemStack rare = ConcentratedStone.getItem();
						world.dropItemNaturally(l, rare);
						sendRareDropMessage(p, rare, "Concentrated Stone");
					}
				}
				case COAL_ORE, DEEPSLATE_COAL_ORE -> {
					if(dropDouble) {
						item = new ItemStack(Material.COAL);
						item.setAmount(random.nextInt(fortune + 1) + 1);
						world.dropItemNaturally(dropLocation, item);
					}
				}
				case COPPER_ORE, DEEPSLATE_COPPER_ORE -> {
					if(dropDouble) {
						item = new ItemStack(Material.RAW_COPPER);
						item.setAmount(random.nextInt((fortune + 1) * 4) + 2);
						world.dropItemNaturally(dropLocation, item);
					}
				}
				case LAPIS_ORE, DEEPSLATE_LAPIS_ORE -> {
					if(dropDouble) {
						item = new ItemStack(Material.LAPIS_LAZULI);
						item.setAmount(random.nextInt((fortune + 1) * 6) + 4);
						world.dropItemNaturally(dropLocation, item);
					}
					if(random.nextDouble() < 0.0025 * fortuneMulti) {
						ItemStack rare = RefinedLapis.getItem();
						world.dropItemNaturally(l, rare);
						sendRareDropMessage(p, rare, "Refined Lapis");
					}
				}
				case IRON_ORE, DEEPSLATE_IRON_ORE -> {
					if(dropDouble) {
						item = new ItemStack(Material.RAW_IRON);
						item.setAmount(random.nextInt(fortune + 1) + 1);
						world.dropItemNaturally(dropLocation, item);
					}
					if(random.nextDouble() < 0.0025 * fortuneMulti) {
						ItemStack rare = RefinedIron.getItem();
						world.dropItemNaturally(l, rare);
						sendRareDropMessage(p, rare, "Refined Iron");
					}
				}
				case GOLD_ORE, DEEPSLATE_GOLD_ORE -> {
					if(dropDouble) {
						item = new ItemStack(Material.RAW_GOLD);
						item.setAmount(random.nextInt(fortune + 1) + 1);
						world.dropItemNaturally(dropLocation, item);
					}
					if(random.nextDouble() < 0.0025 * fortuneMulti) {
						ItemStack rare = RefinedGold.getItem();
						world.dropItemNaturally(l, rare);
						sendRareDropMessage(p, rare, "Refined Gold");
					}
				}
				case REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE -> {
					if(dropDouble) {
						item = new ItemStack(Material.REDSTONE);
						item.setAmount(random.nextInt(fortune + 2) + 4);
						world.dropItemNaturally(dropLocation, item);
					}
					if(random.nextDouble() < 0.0025 * fortuneMulti) {
						ItemStack rare = RefinedRedstone.getItem();
						world.dropItemNaturally(l, rare);
						sendRareDropMessage(p, rare, "Refined Redstone");
					}
				}
				case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE -> {
					if(dropDouble) {
						item = new ItemStack(Material.DIAMOND);
						item.setAmount(random.nextInt(fortune + 1) + 1);
						world.dropItemNaturally(dropLocation, item);
					}
					if(random.nextDouble() < 0.005 * fortuneMulti) {
						ItemStack rare = RefinedDiamond.getItem();
						world.dropItemNaturally(l, rare);
						sendRareDropMessage(p, rare, "Refined Diamond");
					}
				}
				case ANCIENT_DEBRIS -> {
					if(autoSmeltDebris(e, itemInHand) && random.nextDouble() < 0.01 * fortuneMulti) {
						ItemStack rare = RefinedNetherite.getItem();
						world.dropItemNaturally(l, rare);
						sendRareDropMessage(p, rare, "Refined Netherite");
					}
				}
				case EMERALD_ORE, DEEPSLATE_EMERALD_ORE -> {
					if(dropDouble) {
						item = new ItemStack(Material.EMERALD);
						item.setAmount(random.nextInt(fortune + 1) + 1);
						world.dropItemNaturally(dropLocation, item);
					}
					if(random.nextDouble() < 0.02 * fortuneMulti) {
						ItemStack rare = RefinedEmerald.getItem();
						world.dropItemNaturally(l, rare);
						sendRareDropMessage(p, rare, "Refined Emerald");
					}
				}
			}
		} else if(b.getType() == Material.ANCIENT_DEBRIS) {
			autoSmeltDebris(e, itemInHand);
		}
	}

	// auto-smelt so debris can't be re-placed to farm the refined roll
	private static boolean autoSmeltDebris(BlockBreakEvent e, ItemStack tool) {
		Player p = e.getPlayer();
		Block b = e.getBlock();
		if(e.isCancelled() || p.getGameMode() == GameMode.CREATIVE || b.getDrops(tool, p).isEmpty()
				|| tool.containsEnchantment(Enchantment.SILK_TOUCH)) return false;
		e.setDropItems(false);
		b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), new ItemStack(Material.NETHERITE_SCRAP, isDivanPickaxe(tool) ? 2 : 1));
		return true;
	}
}