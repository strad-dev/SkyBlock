package listeners;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CustomDamage rules: i-frames, knockback, perch takeoff")
class DamageRulesTest {
	private static final double EPS = 1e-9;

	@Nested
	@DisplayName("i-frames")
	class IFrames {

		@Test
		@DisplayName("outside i-frames every blow lands in full")
		void outside() {
			CustomDamage.IFrameHit hit = CustomDamage.iFrameHit(false, DamageType.MELEE, true, 5, 3);
			assertFalse(hit.blocked());
			assertFalse(hit.upgrade());
			assertEquals(3, hit.damage(), EPS);
		}

		@Test
		@DisplayName("ranged, magic and absolute ignore i-frames, untouched by the bigger-hit rule")
		void bypass() {
			for(DamageType type : DamageType.values()) {
				if(!CustomDamage.bypassesIFrames(type)) continue;
				CustomDamage.IFrameHit hit = CustomDamage.iFrameHit(true, type, true, 50, 3);
				assertFalse(hit.blocked(), type.name());
				assertFalse(hit.upgrade(), type.name());
				assertEquals(3, hit.damage(), EPS, type.name());
			}
			assertTrue(CustomDamage.bypassesIFrames(DamageType.RANGED));
			assertTrue(CustomDamage.bypassesIFrames(DamageType.LETHAL_ABSOLUTE));
			assertFalse(CustomDamage.bypassesIFrames(DamageType.MELEE));
			assertFalse(CustomDamage.bypassesIFrames(DamageType.ENVIRONMENTAL));
		}

		@Test
		@DisplayName("a player hit harder inside i-frames takes only the difference")
		void upgrade() {
			CustomDamage.IFrameHit hit = CustomDamage.iFrameHit(true, DamageType.MELEE, true, 5, 8);
			assertFalse(hit.blocked());
			assertTrue(hit.upgrade());
			assertEquals(3, hit.damage(), EPS);
		}

		@Test
		@DisplayName("an equal or weaker blow inside a player's i-frames does nothing")
		void notBigger() {
			assertTrue(CustomDamage.iFrameHit(true, DamageType.MELEE, true, 5, 5).blocked());
			assertTrue(CustomDamage.iFrameHit(true, DamageType.MELEE, true, 5, 4).blocked());
		}

		@Test
		@DisplayName("a mob, or a dead player, never takes the difference")
		void mobsIgnore() {
			CustomDamage.IFrameHit hit = CustomDamage.iFrameHit(true, DamageType.MELEE, false, 5, 50);
			assertTrue(hit.blocked());
			assertFalse(hit.upgrade());
		}

		@Test
		@DisplayName("only melee, sweep and i-frame environmental start i-frames, and none of them bypass")
		void starts() {
			for(DamageType type : DamageType.values()) {
				boolean expected = type == DamageType.MELEE || type == DamageType.MELEE_SWEEP || type == DamageType.IFRAME_ENVIRONMENTAL;
				assertEquals(expected, CustomDamage.startsIFrames(type), type.name());
				assertFalse(CustomDamage.startsIFrames(type) && CustomDamage.bypassesIFrames(type), type.name());
			}
		}

		@Test
		@DisplayName("a timer set at tick 100 covers 100-108, gone at 109")
		void timer() {
			Map<UUID, Integer> timers = new HashMap<>();
			UUID a = UUID.randomUUID();
			CustomDamage.startTimer(timers, a, 100);
			assertTrue(CustomDamage.timerActive(timers, a, 100));
			assertTrue(CustomDamage.timerActive(timers, a, 108));
			assertFalse(CustomDamage.timerActive(timers, a, 109));
		}

		@Test
		@DisplayName("each attacker key has its own timer; a restart extends it")
		void keys() {
			Map<UUID, Integer> timers = new HashMap<>();
			UUID a = UUID.randomUUID(), b = UUID.randomUUID();
			CustomDamage.startTimer(timers, a, 100);
			assertFalse(CustomDamage.timerActive(timers, b, 100));
			CustomDamage.startTimer(timers, a, 105);
			assertTrue(CustomDamage.timerActive(timers, a, 113));
		}

		@Test
		@DisplayName("starting a timer drops expired ones")
		void prunes() {
			Map<UUID, Integer> timers = new HashMap<>();
			UUID a = UUID.randomUUID(), b = UUID.randomUUID();
			CustomDamage.startTimer(timers, a, 100);
			CustomDamage.startTimer(timers, b, 120);
			assertFalse(timers.containsKey(a));
			assertTrue(timers.containsKey(b));
		}
	}

	@Nested
	@DisplayName("knockback")
	class Knockback {

		private static double mods(DamageType type, int kb, int punch, boolean crit, boolean sprint, boolean term, boolean block) {
			return CustomDamage.knockbackModifiers(type, kb, punch, crit, sprint, term, block);
		}

		@Test
		@DisplayName("modifiers: Knockback on melee, Punch otherwise, +25% a level")
		void enchants() {
			assertEquals(1.0, mods(DamageType.MELEE, 0, 0, false, false, false, false), EPS);
			assertEquals(1.5, mods(DamageType.MELEE, 2, 0, false, false, false, false), EPS);
			assertEquals(1.5, mods(DamageType.MELEE_SWEEP, 2, 0, false, false, false, false), EPS);
			assertEquals(1.0, mods(DamageType.RANGED, 2, 0, false, false, false, false), EPS);
			assertEquals(1.5, mods(DamageType.RANGED, 2, 2, false, false, false, false), EPS);
		}

		@Test
		@DisplayName("modifiers: crit x1.2 and sprint x1.5 on a direct melee hit only")
		void critSprint() {
			assertEquals(1.8, mods(DamageType.MELEE, 0, 0, true, true, false, false), EPS);
			assertEquals(1.0, mods(DamageType.MELEE_SWEEP, 0, 0, true, true, false, false), EPS);
			assertEquals(1.0, mods(DamageType.RANGED, 0, 0, true, true, false, false), EPS);
		}

		@Test
		@DisplayName("modifiers: a Terminator arrow is a quarter, a shield block halves")
		void termBlock() {
			assertEquals(0.25, mods(DamageType.RANGED, 0, 0, false, false, true, false), EPS);
			assertEquals(0.125, mods(DamageType.RANGED, 0, 0, false, false, true, true), EPS);
			assertEquals(0.5, mods(DamageType.MELEE, 0, 0, false, false, false, true), EPS);
		}

		@Test
		@DisplayName("strength: 0.4 out and 0.4 up at no resistance")
		void base() {
			CustomDamage.Knockback kb = CustomDamage.knockback(1, 1);
			assertEquals(0.4, kb.horizontal(), EPS);
			assertEquals(0.4, kb.vertical(), EPS);
		}

		@Test
		@DisplayName("strength: resistance scales the pop by its sqrt and the push by its 0.75 power")
		void resistance() {
			CustomDamage.Knockback kb = CustomDamage.knockback(0.5, 1);
			assertEquals(0.4 * Math.pow(0.5, 0.75), kb.horizontal(), EPS);
			assertEquals(0.4 * Math.sqrt(0.5), kb.vertical(), EPS);
		}

		@Test
		@DisplayName("strength: modifiers scale the push linearly and never the pop")
		void linear() {
			CustomDamage.Knockback one = CustomDamage.knockback(0.7, 1), two = CustomDamage.knockback(0.7, 2);
			assertEquals(2 * one.horizontal(), two.horizontal(), EPS);
			assertEquals(one.vertical(), two.vertical(), EPS);
		}

		@Test
		@DisplayName("strength: negative resistance pushes harder but never pops above 0.4")
		void negativeResistance() {
			CustomDamage.Knockback kb = CustomDamage.knockback(1.5, 1);
			assertEquals(0.6, kb.horizontal(), EPS);
			assertEquals(0.4, kb.vertical(), EPS);
		}

		@Test
		@DisplayName("velocity: keeps half the target's momentum, pops only on the ground")
		void velocity() {
			Vector dir = new Vector(1, 0, 0);
			Vector grounded = CustomDamage.knockbackVelocity(new Vector(1, 0, 1), dir, 0.4, 0.4, true, false);
			assertEquals(0.9, grounded.getX(), EPS);
			assertEquals(0.4, grounded.getY(), EPS);
			assertEquals(0.5, grounded.getZ(), EPS);
			Vector airborne = CustomDamage.knockbackVelocity(new Vector(0, -0.3, 0), dir, 0.4, 0.4, false, false);
			assertEquals(-0.3, airborne.getY(), EPS);
		}

		@Test
		@DisplayName("a same-tick Terminator volley adds up to 75% of a melee hit's push, with one hit's pop")
		void terminatorVolley() {
			Vector dir = new Vector(1, 0, 0), still = new Vector(0, 0, 0);
			double arrow = CustomDamage.knockback(1, mods(DamageType.RANGED, 0, 0, false, false, true, false)).horizontal();
			Vector v = CustomDamage.knockbackVelocity(still, dir, arrow, 0.4, true, false);
			v = CustomDamage.knockbackVelocity(v, dir, arrow, 0.4, true, true);
			v = CustomDamage.knockbackVelocity(v, dir, arrow, 0.4, true, true);
			Vector melee = CustomDamage.knockbackVelocity(still, dir, CustomDamage.knockback(1, 1).horizontal(), 0.4, true, false);
			assertEquals(0.75 * melee.getX(), v.getX(), EPS);
			assertEquals(melee.getY(), v.getY(), EPS);
		}
	}

	@Nested
	@DisplayName("dragon perch")
	class Perch {

		@Test
		@DisplayName("takes off once a perch has taken MORE than 50, then counts from zero")
		void takeoff() {
			Map<String, Double> perch = new HashMap<>();
			assertFalse(CustomDamage.addPerchDamage(perch, "dragon", 30));
			assertFalse(CustomDamage.addPerchDamage(perch, "dragon", 20));
			assertTrue(CustomDamage.addPerchDamage(perch, "dragon", 1));
			assertFalse(perch.containsKey("dragon"));
			assertFalse(CustomDamage.addPerchDamage(perch, "dragon", 50));
		}

		@Test
		@DisplayName("each dragon counts its own perch")
		void perDragon() {
			Map<String, Double> perch = new HashMap<>();
			CustomDamage.addPerchDamage(perch, "a", 40);
			assertFalse(CustomDamage.addPerchDamage(perch, "b", 40));
			assertTrue(CustomDamage.addPerchDamage(perch, "a", 40));
		}
	}
}
