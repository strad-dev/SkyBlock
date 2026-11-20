package items.armor;

import misc.Plugin;
import misc.SkyblockId;
import misc.Utils;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class NecronElytra implements Armor {
	public static final String ID = "skyblock/combat/necron_elytra";
	public static final Material ELYTRA_FORM = Material.ELYTRA;
	public static final Material CHESTPLATE_FORM = Material.NETHERITE_CHESTPLATE;
	public static final String CHESTPLATE_SKYBLOCK_ID = "POWER_WITHER_CHESTPLATE";

	public static ItemStack getItem() {
		return getItem(false);
	}

	public static ItemStack getItem(boolean chestplate) {
		ItemStack necronElytra = new ItemStack(chestplate ? CHESTPLATE_FORM : ELYTRA_FORM);

		ItemMeta data = necronElytra.getItemMeta();
		data.setUnbreakable(true);
		data.displayName(Utils.mm(chestplate ? "<light_purple>Necron's Chestplate" : "<light_purple>Necron's Elytra"));
		AttributeModifier damage = new AttributeModifier(new NamespacedKey(Plugin.getInstance(), "necronElytraDamage"), 1, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.CHEST);
		AttributeModifier armor = new AttributeModifier(new NamespacedKey(Plugin.getInstance(), "necronElytraArmor"), 8, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.CHEST);
		AttributeModifier antiKB = new AttributeModifier(new NamespacedKey(Plugin.getInstance(), "necronElytraAntiKB"), 0.1, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.CHEST);
		data.addAttributeModifier(Attribute.ATTACK_DAMAGE, damage);
		data.addAttributeModifier(Attribute.ARMOR, armor);
		data.addAttributeModifier(Attribute.KNOCKBACK_RESISTANCE, antiKB);
		data.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);

		List<Component> lore = new ArrayList<>();
		lore.add(Utils.mm(ID));
		lore.add(Utils.mm(""));
		lore.addAll(Utils.statLore(data));
		lore.add(Utils.mm(""));
		lore.add(Utils.mm("<gray><italic>This Elytra has the stats of a Netherite"));
		lore.add(Utils.mm("<gray><italic>Chestplate, while still allowing you to fly!"));
		lore.add(Utils.mm(""));
		lore.add(Utils.mm(chestplate ? "<yellow>Right-click in your inventory to turn into an Elytra!" : "<yellow>Right-click in your inventory to turn into a Chestplate!"));
		lore.add(Utils.mm(""));
		lore.add(Utils.mm("<light_purple><bold><obfuscated>a</obfuscated> MYTHIC CHESTPLATE <obfuscated>a</obfuscated>"));

		data.lore(lore);
		necronElytra.setItemMeta(data);
		Utils.setEnchantability(necronElytra, Utils.SKYBLOCK_ENCHANTABILITY);

		return chestplate ? SkyblockId.stamp(necronElytra, CHESTPLATE_SKYBLOCK_ID) : SkyblockId.stamp(necronElytra);
	}

	public static boolean isChestplate(ItemStack item) {
		return item.getType() == CHESTPLATE_FORM;
	}
}
