package testsupport;

import io.papermc.paper.enchantments.EnchantmentRarity;
import io.papermc.paper.registry.set.RegistryKeySet;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentTarget;
import org.bukkit.entity.EntityCategory;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemType;

import java.util.Set;

/** An enchantment that is only its key, for {@link FakeRegistryAccess}. Anything beyond identity throws. */
@SuppressWarnings({"deprecation", "removal"})
final class FakeEnchantment extends Enchantment {
	private final NamespacedKey key;

	FakeEnchantment(NamespacedKey key) {
		this.key = key;
	}

	private UnsupportedOperationException inert() {
		return new UnsupportedOperationException("FakeEnchantment " + key + " has no behaviour");
	}

	@Override public NamespacedKey getKey() { return key; }
	@Override public String translationKey() { return "enchantment." + key.getNamespace() + "." + key.getKey(); }
	@Override public String getTranslationKey() { return translationKey(); }
	@Override public String toString() { return "FakeEnchantment[" + key + "]"; }

	@Override public String getName() { throw inert(); }
	@Override public int getMaxLevel() { throw inert(); }
	@Override public int getStartLevel() { throw inert(); }
	@Override public EnchantmentTarget getItemTarget() { throw inert(); }
	@Override public boolean isTreasure() { throw inert(); }
	@Override public boolean isCursed() { throw inert(); }
	@Override public boolean conflictsWith(Enchantment other) { throw inert(); }
	@Override public boolean canEnchantItem(ItemStack item) { throw inert(); }
	@Override public Component displayName(int level) { throw inert(); }
	@Override public boolean isTradeable() { throw inert(); }
	@Override public boolean isDiscoverable() { throw inert(); }
	@Override public int getMinModifiedCost(int level) { throw inert(); }
	@Override public int getMaxModifiedCost(int level) { throw inert(); }
	@Override public int getAnvilCost() { throw inert(); }
	@Override public EnchantmentRarity getRarity() { throw inert(); }
	@Override public float getDamageIncrease(int level, EntityCategory category) { throw inert(); }
	@Override public float getDamageIncrease(int level, EntityType type) { throw inert(); }
	@Override public Set<EquipmentSlotGroup> getActiveSlotGroups() { throw inert(); }
	@Override public Component description() { throw inert(); }
	@Override public RegistryKeySet<ItemType> getSupportedItems() { throw inert(); }
	@Override public RegistryKeySet<ItemType> getPrimaryItems() { throw inert(); }
	@Override public int getWeight() { throw inert(); }
	@Override public RegistryKeySet<Enchantment> getExclusiveWith() { throw inert(); }
}
