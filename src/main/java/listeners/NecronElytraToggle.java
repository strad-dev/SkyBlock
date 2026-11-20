package listeners;

import items.armor.NecronElytra;
import misc.Utils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class NecronElytraToggle implements Listener {
	@EventHandler(ignoreCancelled = true)
	public void onRightClick(InventoryClickEvent e) {
		if(e.getClick() != ClickType.RIGHT) return;
		if(!(e.getWhoClicked() instanceof Player p)) return;
		if(!(e.getClickedInventory() instanceof PlayerInventory)) return;
		if(!e.getCursor().getType().isAir()) return;
		ItemStack item = e.getCurrentItem();
		if(item == null || !Utils.firstLorePlain(item).equals(NecronElytra.ID)) return;
		e.setCancelled(true);
		ItemStack swapped = item.withType(NecronElytra.isChestplate(item) ? NecronElytra.ELYTRA_FORM : NecronElytra.CHESTPLATE_FORM);
		ItemStack rebuilt = ItemReloader.refreshItem(swapped, p);
		e.setCurrentItem(rebuilt != null ? rebuilt : swapped);
	}
}
