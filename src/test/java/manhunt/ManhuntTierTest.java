package manhunt;

import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.*;

// of(ItemStack) reads lore through Utils (Bukkit registry at static init), so it's not covered here.
@DisplayName("ManhuntTier: the Stick-to-Netherite Hyperion ladder")
class ManhuntTierTest {

	private static final ManhuntTier[] LADDER = ManhuntTier.values();

	private static void rising(String stat, ToDoubleFunction<ManhuntTier> f) {
		for(int i = 1; i < LADDER.length; i++) {
			assertTrue(f.applyAsDouble(LADDER[i]) >= f.applyAsDouble(LADDER[i - 1]), stat + " drops at " + LADDER[i]);
		}
	}

	private static void falling(String stat, ToDoubleFunction<ManhuntTier> f) {
		for(int i = 1; i < LADDER.length; i++) {
			assertTrue(f.applyAsDouble(LADDER[i]) <= f.applyAsDouble(LADDER[i - 1]), stat + " rises at " + LADDER[i]);
		}
	}

	@Test
	@DisplayName("next() walks all eight rungs in order and ends at Netherite")
	void chain() {
		assertEquals(8, LADDER.length);
		assertEquals(ManhuntTier.BASE, LADDER[0]);
		for(int i = 0; i < LADDER.length - 1; i++) {
			assertEquals(LADDER[i + 1], LADDER[i].next());
		}
		assertNull(ManhuntTier.NETHERITE.next());
	}

	@Test
	@DisplayName("every upgrade only helps: offence and pool up, costs and waits down")
	void monotonic() {
		rising("damage", ManhuntTier::damage);
		rising("implosion damage", ManhuntTier::implosionDamage);
		rising("radius", ManhuntTier::radius);
		rising("absorption", ManhuntTier::absorption);
		rising("damage reduction", ManhuntTier::damageReduction);
		rising("max intelligence", ManhuntTier::maxIntelligence);
		rising("enchantability", ManhuntTier::enchantability);
		rising("rarity", t -> t.rank().ordinal());
		falling("cooldown", ManhuntTier::cooldown);
		falling("ticks per mana", ManhuntTier::ticksPerMana);
		falling("mana cost", ManhuntTier::manaCost);
	}

	@Test
	@DisplayName("the top rung still sits below the real Hyperion")
	void belowFullHyperion() {
		assertTrue(ManhuntTier.NETHERITE.ticksPerMana() > ManhuntTier.FULL_TICKS_PER_MANA);
		assertTrue(ManhuntTier.NETHERITE.maxIntelligence() < ManhuntTier.FULL_MAX_INTELLIGENCE);
	}

	@Test
	@DisplayName("the Stick is the start: no swords to reach it, no enchanting table")
	void stick() {
		assertEquals(Material.STICK, ManhuntTier.BASE.material());
		assertEquals(0, ManhuntTier.BASE.swordsToUpgrade());
		assertEquals(0, ManhuntTier.BASE.enchantability());
		assertEquals("Manhunt Hyperion", ManhuntTier.BASE.displayName());
		assertNull(ManhuntTier.BASE.skyblockId());
	}

	@Test
	@DisplayName("every other rung is a sword, costs swords, and is named for its material")
	void swords() {
		for(int i = 1; i < LADDER.length; i++) {
			ManhuntTier t = LADDER[i];
			assertTrue(t.material().name().endsWith("_SWORD"), t + " is " + t.material());
			assertTrue(t.swordsToUpgrade() > 0, t.toString());
			assertEquals(t.prefix() + " Manhunt Hyperion", t.displayName());
		}
		assertEquals("Wooden Manhunt Hyperion", ManhuntTier.WOOD.displayName());
	}

	@Test
	@DisplayName("rungs share a lore id, so each needs its own texture id")
	void distinctSkyblockIds() {
		Set<String> seen = new HashSet<>();
		for(int i = 1; i < LADDER.length; i++) {
			String id = LADDER[i].skyblockId();
			assertNotNull(id, LADDER[i].toString());
			assertTrue(seen.add(id), "duplicate id " + id);
		}
	}
}
