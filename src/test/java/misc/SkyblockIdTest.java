package misc;

import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Loading the class at all proves both tables build: Map.ofEntries throws on a duplicate key.
@DisplayName("SkyblockId: lore id and vanilla material -> real Hypixel id")
class SkyblockIdTest {

	@Test
	@DisplayName("custom items map to their Hypixel ids")
	void custom() {
		assertEquals("HYPERION", SkyblockId.of("skyblock/combat/scylla"));
		assertEquals("TERMINATOR", SkyblockId.of("skyblock/combat/terminator"));
		assertEquals("STARRED_BONZO_STAFF", SkyblockId.of("skyblock/combat/bonzo_staff"));
	}

	@Test
	@DisplayName("no counterpart is null, never a guess")
	void absent() {
		assertNull(SkyblockId.of("skyblock/manhunt/hyperion"));
		assertNull(SkyblockId.of("not/a/lore/id"));
	}

	@Test
	@DisplayName("vanilla gear is dressed per material; unlisted materials are null")
	void vanilla() {
		assertEquals("NECRON_BLADE", SkyblockId.vanillaFor(Material.NETHERITE_SWORD));
		assertEquals("ASPECT_OF_THE_DRAGON", SkyblockId.vanillaFor(Material.IRON_SWORD));
		assertEquals("TREECAPITATOR_AXE", SkyblockId.vanillaFor(Material.COPPER_AXE));
		assertNull(SkyblockId.vanillaFor(Material.NETHERITE_AXE));
		assertNull(SkyblockId.vanillaFor(Material.DIRT));
	}
}
