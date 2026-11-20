package items.weapons;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Terminator: arrow and Salvation damage by Power level, before Strength")
class TerminatorDamageTest {

	@Test
	@DisplayName("arrow: 2.5 base, +0.25 per Power level, VII rounds to +2")
	void arrow() {
		assertEquals(2.5, Terminator.arrowDamage(0));
		assertEquals(2.5, Terminator.arrowDamage(-1));
		assertEquals(3.75, Terminator.arrowDamage(5));
		assertEquals(4.0, Terminator.arrowDamage(6));
		assertEquals(4.5, Terminator.arrowDamage(7));
	}

	@Test
	@DisplayName("Salvation: 4 base, +0.5 per Power level, VII rounds to +4")
	void salvation() {
		assertEquals(4.0, Terminator.salvationDamage(0));
		assertEquals(6.5, Terminator.salvationDamage(5));
		assertEquals(7.0, Terminator.salvationDamage(6));
		assertEquals(8.0, Terminator.salvationDamage(7));
	}

	@Test
	@DisplayName("both rise with Power, and the beam always beats one arrow")
	void monotonic() {
		for(int power = 1; power <= 7; power++) {
			assertTrue(Terminator.arrowDamage(power) > Terminator.arrowDamage(power - 1), "arrow at " + power);
			assertTrue(Terminator.salvationDamage(power) > Terminator.salvationDamage(power - 1), "salvation at " + power);
			assertTrue(Terminator.salvationDamage(power) > Terminator.arrowDamage(power), "at " + power);
		}
	}
}
