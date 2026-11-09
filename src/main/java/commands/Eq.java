package commands;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import items.misc.HolyIce;
import items.misc.IceSpray;
import items.weapons.Scylla;
import items.weapons.SwordOfBadHealth;
import listeners.CustomDamage;
import misc.Menus;
import misc.Plugin;
import misc.Utils;
import net.kyori.adventure.text.Component;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

/*
 * /eq, ported from M7. 1-row GUI: worn armor in slots 0-3, offensive/defensive stats in 6-7, sugar cane in
 * slot 8 whose count encodes speed (100 = vanilla, exact figure on the tooltip). Clicking armor in your own
 * inventory equips it.
 * Both the executor and a listener; the GUI is identified by EqHolder.
 */
public class Eq implements CommandExecutor, Listener {

	private static final Component TITLE = Utils.msg("<dark_gray>Equipment");
	private static final int OFFENSE_SLOT = 6;
	private static final int DEFENSE_SLOT = 7;
	private static final int SPEED_SLOT = 8;
	/** Modifiers currentSpeed skips: vanilla mechanics, not the speed stat. Speed/Soul Speed deliberately count. */
	private static final Set<NamespacedKey> IGNORED_SPEED_MODIFIERS = Set.of(NamespacedKey.minecraft("sprinting"));
	/** Last swap tick per player; collapses a double-click burst into one swap. */
	private static final Map<UUID, Integer> lastSwapTick = new HashMap<>();

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if(!(sender instanceof Player p)) {
			sender.sendMessage(Utils.msg("Only players can run this"));
			return true;
		}
		open(p);
		return true;
	}

	private static void open(Player p) {
		EqHolder holder = new EqHolder();
		Inventory gui = Bukkit.createInventory(holder, 9, TITLE);
		holder.setInventory(gui);
		refresh(p, gui);
		p.openInventory(gui);
		applySpeedCane(p); // after the menu exists: writes via NMS
	}

	/** Armor into slots 0-3, stats into 6-7. The cane is set separately via NMS. */
	private static void refresh(Player p, Inventory gui) {
		for(int i = 0; i < 4; i++) {
			ItemStack worn = getArmor(p, i);
			gui.setItem(i, worn == null ? null : worn.clone());
		}
		refreshStats(p, gui);
	}

	private static void refreshStats(Player p, Inventory gui) {
		gui.setItem(OFFENSE_SLOT, offenseItem(p));
		gui.setItem(DEFENSE_SLOT, defenseItem(p));
	}

	private static ItemStack offenseItem(Player p) {
		ItemStack held = p.getInventory().getItemInMainHand();
		var attr = p.getAttribute(Attribute.ATTACK_DAMAGE);
		double damage = attr == null ? 1 : attr.getValue();
		damage = Math.max(0, damage + CustomDamage.sharpnessBonus(held.getEnchantmentLevel(Enchantment.SHARPNESS))
				- CustomDamage.strengthPenalty(p));

		double multiplier = 1;
		List<Component> modifiers = new ArrayList<>();
		if(p.getScoreboardTags().contains("IceSprayed")) {
			multiplier *= IceSpray.DAMAGE_DEALT_PENALTY;
			modifiers.add(modifierLine("Ice Sprayed", IceSpray.DAMAGE_DEALT_PENALTY, true));
		}
		if(p.getScoreboardTags().contains("BadHealthBuffed")) {
			multiplier *= SwordOfBadHealth.DAMAGE_BONUS;
			modifiers.add(modifierLine("Sword of Bad Health", SwordOfBadHealth.DAMAGE_BONUS, true));
		}

		boolean breakdown = showBreakdown.contains(p.getUniqueId());
		boolean crit = showCrit.contains(p.getUniqueId());
		double critFactor = crit ? CustomDamage.CRIT_MULTIPLIER : 1;
		List<Component> lore = new ArrayList<>();
		lore.add(Utils.mm("<gray>Hit: " + (crit ? "<yellow>Critical" : "<white>Normal")));
		lore.add(Utils.mm("<gray>Damage: <red>" + Utils.damageNumber(damage * critFactor * multiplier)));
		if(breakdown) {
			List<Component> parts = attributeBreakdown(p, Attribute.ATTACK_DAMAGE, "red");
			int sharpness = held.getEnchantmentLevel(Enchantment.SHARPNESS);
			if(sharpness > 0) {
				parts.add(breakdownLine(Enchantment.SHARPNESS.displayName(sharpness), "+", CustomDamage.sharpnessBonus(sharpness), "red"));
			}
			if(crit) {
				parts.add(breakdownLine(Component.text("Critical Hit"), "x", CustomDamage.CRIT_MULTIPLIER, "red"));
			}
			addSection(lore, parts);
		}
		addTypedDamage(lore, held, Enchantment.SMITE, "Undead", damage, critFactor, multiplier, breakdown);
		addTypedDamage(lore, held, Enchantment.BANE_OF_ARTHROPODS, "Arthropods", damage, critFactor, multiplier, breakdown);
		if(Scylla.ID.equals(Utils.firstLorePlain(held))) {
			int smite = held.getEnchantmentLevel(Enchantment.SMITE);
			double smiteBonus = CustomDamage.smiteBonus(smite);
			if(breakdown) lore.add(Component.empty());
			lore.add(Utils.mm("<gray>Damage VS Withers: <red>" + Utils.damageNumber(((damage + smiteBonus) * critFactor + Scylla.WITHER_BONUS) * multiplier)));
			if(breakdown) {
				List<Component> parts = new ArrayList<>();
				if(smite > 0) parts.add(breakdownLine(Enchantment.SMITE.displayName(smite), "+", smiteBonus, "red"));
				parts.add(breakdownLine(held.effectiveName(), "+", Scylla.WITHER_BONUS, "red"));
				addSection(lore, parts);
			}
		}
		Double ability = abilityDamage(p, held);
		if(ability != null) {
			if(breakdown) lore.add(Component.empty());
			lore.add(Utils.mm("<gray>Ability Damage: <red>" + Utils.tenthNumber(ability * multiplier)));
		}
		if(!modifiers.isEmpty()) {
			lore.add(Component.empty());
			lore.addAll(modifiers);
		}
		lore.add(Component.empty());
		lore.add(Utils.mm("<dark_gray>Left-click to " + (breakdown ? "hide" : "show") + " breakdown"));
		lore.add(Utils.mm("<dark_gray>Right-click to show " + (crit ? "normal" : "critical") + " hits"));
		return statsItem(Material.DIAMOND_SWORD, "<red>Offensive Stats", lore);
	}

	private static void addTypedDamage(List<Component> lore, ItemStack held, Enchantment enchant, String against,
			double damage, double critFactor, double multiplier, boolean breakdown) {
		int level = held.getEnchantmentLevel(enchant);
		if(level <= 0) return;
		double bonus = CustomDamage.smiteBonus(level);
		if(breakdown) lore.add(Component.empty());
		lore.add(Utils.mm("<gray>Damage VS " + against + ": <red>" + Utils.damageNumber((damage + bonus) * critFactor * multiplier)));
		if(breakdown) addSection(lore, new ArrayList<>(List.of(breakdownLine(enchant.displayName(level), "+", bonus, "red"))));
	}

	// =================== Breakdown ===================

	private static final Set<UUID> showBreakdown = new HashSet<>();
	private static final Set<UUID> showCrit = new HashSet<>();
	private static final NamespacedKey STRENGTH_KEY = NamespacedKey.minecraft("effect.strength");

	private static void addSection(List<Component> lore, List<Component> parts) {
		if(parts.isEmpty()) return;
		lore.add(Component.empty());
		lore.addAll(parts);
	}

	private static void addToggleHint(List<Component> lore, boolean breakdown) {
		lore.add(Component.empty());
		lore.add(Utils.mm("<dark_gray>Click to " + (breakdown ? "hide" : "show") + " breakdown"));
	}

	private static Map<NamespacedKey, Component> itemSources(Player p, Attribute attribute) {
		Map<NamespacedKey, Component> sources = new HashMap<>();
		EntityEquipment equipment = p.getEquipment();
		for(EquipmentSlot slot : EquipmentSlot.values()) {
			if(!p.canUseEquipmentSlot(slot)) continue;
			ItemStack item = equipment.getItem(slot);
			if(item.isEmpty()) continue;
			ItemAttributeModifiers mods = item.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
			if(mods == null) continue;
			for(ItemAttributeModifiers.Entry entry : mods.modifiers()) {
				if(entry.attribute().equals(attribute) && entry.getGroup().test(slot)) {
					sources.putIfAbsent(entry.modifier().getKey(), item.effectiveName());
				}
			}
		}
		return sources;
	}

	private static Component modifierLabel(Player p, NamespacedKey key, Map<NamespacedKey, Component> sources) {
		Component label = sources.get(key);
		if(label == null) label = effectLabel(p, key);
		if(label == null) label = Component.text(key.asString());
		return label;
	}

	private static List<Component> attributeBreakdown(Player p, Attribute attribute, String colour) {
		List<Component> out = new ArrayList<>();
		AttributeInstance instance = p.getAttribute(attribute);
		if(instance == null) return out;
		Map<NamespacedKey, Component> sources = itemSources(p, attribute);

		if(instance.getBaseValue() != 0) {
			out.add(breakdownLine(Component.text("Base"), "+", instance.getBaseValue(), colour));
		}
		for(AttributeModifier mod : instance.getModifiers()) {
			NamespacedKey key = mod.getKey();
			double amount = mod.getAmount();
			if(key.equals(STRENGTH_KEY)) amount -= CustomDamage.strengthPenalty(p);
			Component label = modifierLabel(p, key, sources);
			out.add(switch(mod.getOperation()) {
				case ADD_NUMBER -> breakdownLine(label, amount < 0 ? "-" : "+", Math.abs(amount), colour);
				case ADD_SCALAR -> breakdownLine(label, amount < 0 ? "-" : "+", Math.abs(amount) * 100, colour)
						.append(Utils.mm("<" + colour + ">% of base"));
				case MULTIPLY_SCALAR_1 -> breakdownLine(label, "x", 1 + amount, colour);
			});
		}
		return out;
	}

	@javax.annotation.Nullable
	private static Component effectLabel(Player p, NamespacedKey key) {
		if(!key.getNamespace().equals(NamespacedKey.MINECRAFT) || !key.getKey().startsWith("effect.")) return null;
		String id = key.getKey().substring("effect.".length());
		for(PotionEffect effect : p.getActivePotionEffects()) {
			if(!effect.getType().getKey().getKey().equals(id)) continue;
			int level = effect.getAmplifier() + 1;
			Component numeral = level <= 10 ? Component.translatable("enchantment.level." + level) : Component.text(level);
			return Component.translatable("effect.minecraft." + id).append(Component.space()).append(numeral);
		}
		return null;
	}

	private static Component breakdownLine(Component label, String sign, double amount, String colour) {
		return Component.empty().color(net.kyori.adventure.text.format.NamedTextColor.GRAY)
				.decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
				.append(label).append(Component.text(": "))
				.append(Utils.mm("<" + colour + ">" + sign + Utils.damageNumber(amount)));
	}

	/** Per-target damage of the held item's ability, from the same figure the ability pays; null if it deals none. */
	@javax.annotation.Nullable
	private static Double abilityDamage(Player p, ItemStack held) {
		manhunt.ManhuntTier tier = manhunt.ManhuntTier.of(held);
		if(tier != null) return tier.implosionDamage();
		return switch(Utils.firstLorePlain(held)) {
			case Scylla.ID -> Scylla.implosionDamage(p, held);
			case "skyblock/combat/terminator" ->
					items.weapons.Terminator.salvationDamage(held.getEnchantmentLevel(Enchantment.POWER));
			case "skyblock/combat/ice_spray_wand" -> IceSpray.DAMAGE;
			default -> null;
		};
	}

	private static ItemStack defenseItem(Player p) {
		double armor = CustomDamage.armorPoints(p);
		List<Component> modifiers = new ArrayList<>();
		double taken = CustomDamage.armorMultiplier(armor) * takenModifiers(p, modifiers);

		boolean breakdown = showBreakdown.contains(p.getUniqueId());
		List<Component> lore = new ArrayList<>();
		lore.add(Utils.mm("<gray>Armor: <green>" + Utils.damageNumber(armor)));
		if(breakdown) {
			addSection(lore, attributeBreakdown(p, Attribute.ARMOR, "green"));
			lore.add(Component.empty());
		}
		lore.add(Utils.mm("<gray>Armor Reduction: <green>" + Utils.percent(1 - CustomDamage.armorMultiplier(armor))));
		if(!modifiers.isEmpty()) {
			lore.add(Component.empty());
			lore.addAll(modifiers);
		}
		var maxHealth = p.getAttribute(Attribute.MAX_HEALTH);
		double health = maxHealth == null ? 20 : maxHealth.getValue();
		lore.add(Component.empty());
		lore.add(Utils.mm("<gray>Damage Taken: <white>" + Utils.percent(taken)));
		lore.add(effectiveHp("Effective HP", health, taken));

		// Only the hit types they cover, so off the main figure: Projectile Protection does nothing to a sword.
		for(Enchantment protection : CustomDamage.TYPED_PROTECTIONS) {
			int levels = CustomDamage.typedProtectionLevels(p, protection);
			if(levels <= 0) continue;
			double m = CustomDamage.typedProtectionMultiplier(levels);
			String against = protection.equals(Enchantment.FIRE_PROTECTION) ? "Fire"
					: protection.equals(Enchantment.BLAST_PROTECTION) ? "Explosions" : "Projectiles";
			lore.add(Component.empty());
			String name = protection.equals(Enchantment.FIRE_PROTECTION) ? "Fire Protection"
					: protection.equals(Enchantment.BLAST_PROTECTION) ? "Blast Protection" : "Projectile Protection";
			lore.add(modifierLine(name + " (" + levels + ")", m, false));
			lore.add(Utils.mm("<gray>Damage Taken VS " + against + ": <white>" + Utils.percent(taken * m)));
			lore.add(effectiveHp("Effective HP VS " + against, health, taken * m));
		}
		addToggleHint(lore, breakdown);
		return statsItem(Material.DIAMOND_CHESTPLATE, "<green>Defensive Stats", lore);
	}

	private static double takenModifiers(Player p, List<Component> lines) {
		double taken = 1;
		int prot = CustomDamage.protectionLevels(p);
		if(prot > 0) {
			double m = CustomDamage.protectionMultiplier(prot);
			taken *= m;
			lines.add(modifierLine("Protection (" + prot + ")", m, false));
		}
		PotionEffect resistance = p.getPotionEffect(PotionEffectType.RESISTANCE);
		if(resistance != null) {
			double m = CustomDamage.resistanceMultiplier(p);
			taken *= m;
			lines.add(modifierLine("Resistance " + (resistance.getAmplifier() + 1), m, false));
		}
		if(p.getScoreboardTags().contains("WitherShield")) {
			double m = 1 - Scylla.witherShieldReduction(p);
			taken *= m;
			lines.add(modifierLine("Wither Shield", m, false));
		}
		if(p.getScoreboardTags().contains("HolyIce")) {
			taken *= HolyIce.DAMAGE_TAKEN;
			lines.add(modifierLine("Holy Ice", HolyIce.DAMAGE_TAKEN, false));
		}
		if(p.getScoreboardTags().contains("IceSprayed")) {
			taken *= IceSpray.DAMAGE_TAKEN_BONUS;
			lines.add(modifierLine("Ice Sprayed", IceSpray.DAMAGE_TAKEN_BONUS, false));
		}
		return taken;
	}

	private static Component effectiveHp(String label, double health, double taken) {
		if(taken <= 0) return Utils.mm("<gray>" + label + ": <white>Immune");
		return Utils.mm("<gray>" + label + ": <white>" + Utils.tenthNumber(health / taken)
				+ " <dark_gray>(" + Utils.tenthNumber(1 / taken) + "x)");
	}

	private static Component modifierLine(String label, double multiplier, boolean higherIsGood) {
		boolean up = multiplier > 1;
		String colour = up == higherIsGood ? "<green>" : "<red>";
		return Utils.mm("<gray>" + label + ": " + colour + (up ? "+" : "-") + Utils.percent(Math.abs(multiplier - 1)));
	}

	private static ItemStack statsItem(Material material, String title, List<Component> lore) {
		ItemStack item = new ItemStack(material);
		ItemMeta meta = item.getItemMeta();
		if(meta == null) return item;
		meta.displayName(Utils.mm(title));
		meta.lore(lore);
		item.setItemMeta(meta);
		item.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay()
				.addHiddenComponents(DataComponentTypes.ATTRIBUTE_MODIFIERS));
		return item;
	}

	/**
	 * Count is speed/10: the client clamps count to max_stack_size, hard-capped at 99. MAX_STACK_SIZE is raised
	 * so 65-99 render.
	 */
	private static void applySpeedCane(Player p) {
		ServerPlayer sp = ((CraftPlayer) p).getHandle();
		AbstractContainerMenu menu = sp.containerMenu;
		if(SPEED_SLOT >= menu.slots.size()) return;
		int amount = Math.clamp(currentSpeed(p) / 10, 1, 99);
		net.minecraft.world.item.ItemStack nms = CraftItemStack.asNMSCopy(speedItem(p));
		nms.set(DataComponents.MAX_STACK_SIZE, amount);
		nms.setCount(amount);
		menu.getSlot(SPEED_SLOT).set(nms);
		sp.connection.send(new ClientboundContainerSetSlotPacket(menu.containerId, menu.incrementStateId(), SPEED_SLOT, nms));
	}

	private static ItemStack speedItem(Player p) {
		int speed = currentSpeed(p);
		boolean breakdown = showBreakdown.contains(p.getUniqueId());
		List<Component> lore = new ArrayList<>();
		lore.add(Utils.mm("<gray>Speed: <aqua>" + speed));
		if(breakdown) {
			addSection(lore, speedBreakdown(p));
		}
		addFallStats(lore, p);
		addToggleHint(lore, breakdown);
		return statsItem(Material.SUGAR_CANE, "<white>Misc Stats", lore);
	}

	private static List<Component> speedBreakdown(Player p) {
		List<Component> out = new ArrayList<>();
		AttributeInstance attr = p.getAttribute(Attribute.MOVEMENT_SPEED);
		if(attr == null || attr.getBaseValue() == 0) return out;
		double base = attr.getBaseValue();
		double withFlat = base;
		for(AttributeModifier mod : attr.getModifiers()) {
			if(counts(mod, AttributeModifier.Operation.ADD_NUMBER)) withFlat += mod.getAmount();
		}
		Map<NamespacedKey, Component> sources = itemSources(p, Attribute.MOVEMENT_SPEED);
		out.add(breakdownLine(Component.text("Base"), "+", 100, "white"));
		for(AttributeModifier mod : attr.getModifiers()) {
			if(IGNORED_SPEED_MODIFIERS.contains(mod.getKey())) continue;
			Component label = modifierLabel(p, mod.getKey(), sources);
			double amount = mod.getAmount();
			String sign = amount < 0 ? "-" : "+";
			out.add(switch(mod.getOperation()) {
				case ADD_NUMBER -> breakdownLine(label, sign, Math.abs(amount) / base * 100, "white");
				case ADD_SCALAR -> breakdownLine(label, sign, Math.abs(amount) * withFlat / base * 100, "white");
				case MULTIPLY_SCALAR_1 -> breakdownLine(label, "x", 1 + amount, "white");
			});
		}
		return out;
	}

	private static void addFallStats(List<Component> lore, Player p) {
		AttributeInstance safeAttr = p.getAttribute(Attribute.SAFE_FALL_DISTANCE);
		double safe = safeAttr == null ? 3 : safeAttr.getValue();
		AttributeInstance fallAttr = p.getAttribute(Attribute.FALL_DAMAGE_MULTIPLIER);
		double fallMultiplier = fallAttr == null ? 1 : Math.max(0, fallAttr.getValue());

		List<Component> sources = new ArrayList<>();
		if(safeAttr != null) {
			Map<NamespacedKey, Component> items = itemSources(p, Attribute.SAFE_FALL_DISTANCE);
			for(AttributeModifier mod : safeAttr.getModifiers()) {
				double amount = mod.getOperation() == AttributeModifier.Operation.ADD_NUMBER ? mod.getAmount()
						: safeAttr.getBaseValue() * mod.getAmount();
				sources.add(Component.empty().color(net.kyori.adventure.text.format.NamedTextColor.GRAY)
						.decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
						.append(modifierLabel(p, mod.getKey(), items)).append(Component.text(": "))
						.append(Utils.mm((amount >= 0 ? "<green>+" : "<red>-") + Utils.damageNumber(Math.abs(amount)) + " safe blocks")));
			}
		}
		if(fallAttr != null) {
			Map<NamespacedKey, Component> items = itemSources(p, Attribute.FALL_DAMAGE_MULTIPLIER);
			for(AttributeModifier mod : fallAttr.getModifiers()) {
				double m = mod.getOperation() == AttributeModifier.Operation.MULTIPLY_SCALAR_1 ? 1 + mod.getAmount()
						: 1 + fallAttr.getBaseValue() * mod.getAmount();
				sources.add(Component.empty().color(net.kyori.adventure.text.format.NamedTextColor.GRAY)
						.decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
						.append(modifierLabel(p, mod.getKey(), items)).append(Component.text(": "))
						.append(Utils.mm((m > 1 ? "<red>+" : "<green>-") + Utils.percent(Math.abs(m - 1)))));
			}
		}
		int featherFalling = CustomDamage.featherFallingLevel(p);
		if(featherFalling > 0) {
			sources.add(modifierLine("Feather Falling (" + featherFalling + ")",
					1 - featherFalling * CustomDamage.FEATHER_FALLING_PER_LEVEL, false));
		}
		double taken = CustomDamage.featherFallingMultiplier(p) * takenModifiers(p, sources);

		boolean immune = fallMultiplier <= 0 || taken <= 0;
		double total = fallMultiplier * taken;
		lore.add(Component.empty());
		lore.add(Utils.mm("<gray>Fall Damage Reduction: " + (immune ? "<green>Immune"
				: total <= 1 ? "<green>-" + Utils.percent(1 - total) : "<red>+" + Utils.percent(total - 1))));
		lore.addAll(sources);
		if(immune) {
			lore.add(Utils.mm("<gray>Max Safe Fall: <green>Unlimited"));
			return;
		}
		AttributeInstance maxHealth = p.getAttribute(Attribute.MAX_HEALTH);
		double health = maxHealth == null ? 20 : maxHealth.getValue();
		long survivable = (long) Math.ceil(health / taken + CustomDamage.FALL_FLAT_REDUCTION - 1);
		while(survivable > 0 && (survivable - CustomDamage.FALL_FLAT_REDUCTION) * taken >= health) survivable--;
		while((survivable + 1 - CustomDamage.FALL_FLAT_REDUCTION) * taken < health) survivable++;
		double limit = safe + (survivable + 1) / fallMultiplier;
		long blocks = (long) Math.ceil(limit) - 1;
		lore.add(Utils.mm("<gray>Max Safe Fall: <green>" + blocks + " blocks"));
	}

	/** 100 = vanilla, sprinting excluded. */
	private static int currentSpeed(Player p) {
		var attr = p.getAttribute(Attribute.MOVEMENT_SPEED);
		if(attr == null || attr.getBaseValue() == 0) return 100;
		// Not getValue(): sprinting is a +30% MULTIPLY_SCALAR_1 modifier and read 30% high. The three passes
		// mirror AttributeInstance.calculateValue; floor 0 like sanitizeValue.
		double vanillaBase = attr.getBaseValue(); // the 100 scale is relative to this
		Collection<AttributeModifier> mods = attr.getModifiers();

		double base = vanillaBase;
		for(AttributeModifier mod : mods) {
			if(counts(mod, AttributeModifier.Operation.ADD_NUMBER)) base += mod.getAmount();
		}
		double value = base;
		for(AttributeModifier mod : mods) {
			if(counts(mod, AttributeModifier.Operation.ADD_SCALAR)) value += base * mod.getAmount();
		}
		for(AttributeModifier mod : mods) {
			if(counts(mod, AttributeModifier.Operation.MULTIPLY_SCALAR_1)) value *= 1 + mod.getAmount();
		}
		return (int) Math.round(Math.max(0, value) / vanillaBase * 100);
	}

	private static boolean counts(AttributeModifier mod, AttributeModifier.Operation op) {
		return mod.getOperation() == op && !IGNORED_SPEED_MODIFIERS.contains(mod.getKey());
	}

	// =================== Click handling ===================

	@EventHandler
	public void onInventoryClick(InventoryClickEvent e) {
		if(!(e.getView().getTopInventory().getHolder() instanceof EqHolder)) return;
		if(Menus.ignoreDoubleClick(e)) return;
		e.setCancelled(true); // only the armor swap below changes anything
		if(!(e.getWhoClicked() instanceof Player p)) return;
		Inventory clicked = e.getClickedInventory();
		Inventory top = e.getView().getTopInventory();
		if(clicked != null && clicked.equals(top) && (e.getSlot() == OFFENSE_SLOT || e.getSlot() == DEFENSE_SLOT || e.getSlot() == SPEED_SLOT)) {
			Set<UUID> toggle = e.getSlot() == OFFENSE_SLOT && e.isRightClick() ? showCrit : showBreakdown;
			if(!toggle.remove(p.getUniqueId())) toggle.add(p.getUniqueId());
			refreshStats(p, top);
			applySpeedCane(p);
			return;
		}
		if(clicked == null || !clicked.equals(p.getInventory())) return; // only bottom-inventory clicks act

		ItemStack item = e.getCurrentItem();
		if(item == null || item.getType() == Material.AIR) return;
		int idx = armorSlotIndex(item.getType());
		if(idx < 0) return;

		// One swap per player per tick.
		int now = MinecraftServer.currentTick;
		if(lastSwapTick.getOrDefault(p.getUniqueId(), -1) == now) return;
		lastSwapTick.put(p.getUniqueId(), now);

		ItemStack worn = getArmor(p, idx);
		setArmor(p, idx, item.clone());
		e.setCurrentItem(worn); // null clears the slot if nothing was worn
		Inventory gui = e.getView().getTopInventory();
		gui.setItem(idx, item.clone());
		Bukkit.getScheduler().runTaskLater(Plugin.getInstance(), () -> {
			if(p.getOpenInventory().getTopInventory().getHolder() instanceof EqHolder) {
				refreshStats(p, gui);
				applySpeedCane(p);
			}
		}, 2L);
	}

	@EventHandler
	public void onInventoryDrag(InventoryDragEvent e) {
		if(!(e.getView().getTopInventory().getHolder() instanceof EqHolder)) return;
		int topSize = e.getView().getTopInventory().getSize();
		for(int slot : e.getRawSlots()) {
			if(slot < topSize) {
				e.setCancelled(true);
				return;
			}
		}
	}

	// =================== Armor helpers ===================

	/** 0 helmet, 1 chestplate, 2 leggings, 3 boots, else -1. */
	private static int armorSlotIndex(Material m) {
		String n = m.name();
		if(n.endsWith("_HELMET") || m == Material.PLAYER_HEAD || m == Material.CARVED_PUMPKIN) return 0;
		if(n.endsWith("_CHESTPLATE") || m == Material.ELYTRA) return 1;
		if(n.endsWith("_LEGGINGS")) return 2;
		if(n.endsWith("_BOOTS")) return 3;
		return -1;
	}

	private static ItemStack getArmor(Player p, int idx) {
		PlayerInventory inv = p.getInventory();
		return switch(idx) {
			case 0 -> inv.getHelmet();
			case 1 -> inv.getChestplate();
			case 2 -> inv.getLeggings();
			case 3 -> inv.getBoots();
			default -> null;
		};
	}

	private static void setArmor(Player p, int idx, ItemStack item) {
		PlayerInventory inv = p.getInventory();
		switch(idx) {
			case 0 -> inv.setHelmet(item);
			case 1 -> inv.setChestplate(item);
			case 2 -> inv.setLeggings(item);
			case 3 -> inv.setBoots(item);
		}
	}

	/** Identifies the /eq GUI. */
	public static final class EqHolder implements InventoryHolder {
		private Inventory inv;
		void setInventory(Inventory inv) { this.inv = inv; }
		@Override public Inventory getInventory() { return inv; }
	}
}
