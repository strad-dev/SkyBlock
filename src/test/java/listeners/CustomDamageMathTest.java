package listeners;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// CustomDamage's static init reads Enchantment constants: testsupport.FakeRegistryAccess lets it load.
@DisplayName("CustomDamage: armor, breach, protection and enchant multipliers")
class CustomDamageMathTest {
	private static final double EPS = 1e-9;

	@Test
	@DisplayName("armor: 4% per point to 20, then a curve that never reaches zero")
	void armor() {
		assertEquals(1.0, CustomDamage.armorMultiplier(0), EPS);
		assertEquals(0.6, CustomDamage.armorMultiplier(10), EPS);
		assertEquals(0.2, CustomDamage.armorMultiplier(20), EPS);
		assertEquals(1 / 15.0, CustomDamage.armorMultiplier(30), EPS);
		assertEquals(1.0, CustomDamage.armorMultiplier(-5), EPS);
	}

	@Test
	@DisplayName("armor: continuous at 20, always falling, always positive")
	void armorShape() {
		assertEquals(CustomDamage.armorMultiplier(20), CustomDamage.armorMultiplier(19.9999), 1e-5);
		double last = CustomDamage.armorMultiplier(0);
		for(double a = 0.5; a <= 200; a += 0.5) {
			double m = CustomDamage.armorMultiplier(a);
			assertTrue(m < last, "not falling at " + a);
			assertTrue(m > 0, "not positive at " + a);
			last = m;
		}
	}

	@Test
	@DisplayName("breach: none changes nothing, 15% of armor's effect per level, all of it gone past 6.67")
	void breach() {
		for(double m : new double[]{1.0, 0.6, 0.2, 1 / 15.0}) {
			assertEquals(m, CustomDamage.breachMultiplier(m, 0), EPS);
			assertEquals(1.0, CustomDamage.breachMultiplier(m, 7), EPS);
		}
		assertEquals(1 / 2.6, CustomDamage.breachMultiplier(0.2, 4), EPS);
	}

	@Test
	@DisplayName("Protection: 2.5% per level, floor at half")
	void protection() {
		assertEquals(1.0, CustomDamage.protectionMultiplier(0), EPS);
		assertEquals(0.8, CustomDamage.protectionMultiplier(8), EPS);
		assertEquals(0.5, CustomDamage.protectionMultiplier(20), EPS);
		assertEquals(0.5, CustomDamage.protectionMultiplier(100), EPS);
	}

	@Test
	@DisplayName("typed protection: 5% per level, a full IV set is 80%, never below zero")
	void typedProtection() {
		assertEquals(1.0, CustomDamage.typedProtectionMultiplier(0), EPS);
		assertEquals(0.2, CustomDamage.typedProtectionMultiplier(16), EPS);
		assertEquals(0.0, CustomDamage.typedProtectionMultiplier(20), EPS);
		assertEquals(0.0, CustomDamage.typedProtectionMultiplier(25), EPS);
		assertEquals(3, CustomDamage.TYPED_PROTECTIONS.size());
	}

	@Test
	@DisplayName("Sharpness: 0.7 a level, VII a round 5")
	void sharpness() {
		assertEquals(0, CustomDamage.sharpnessBonus(0), EPS);
		assertEquals(0.7, CustomDamage.sharpnessBonus(1), EPS);
		assertEquals(4.2, CustomDamage.sharpnessBonus(6), EPS);
		assertEquals(5, CustomDamage.sharpnessBonus(7), EPS);
	}

	@Test
	@DisplayName("Smite on top of Sharpness adds up to the old 1.25 a level, 9 at VII")
	void smite() {
		assertEquals(0, CustomDamage.smiteBonus(0), EPS);
		assertEquals(2.75, CustomDamage.smiteBonus(5), EPS);
		assertEquals(4, CustomDamage.smiteBonus(7), EPS);
		for(int level = 1; level <= 7; level++) {
			double total = level == 7 ? 9 : level * 1.25;
			assertEquals(total, CustomDamage.sharpnessBonus(level) + CustomDamage.smiteBonus(level), EPS, "level " + level);
		}
	}

	@Test
	@DisplayName("a crit is 1.5x the whole blow")
	void crit() {
		assertEquals(1.5, CustomDamage.CRIT_MULTIPLIER);
	}
}
