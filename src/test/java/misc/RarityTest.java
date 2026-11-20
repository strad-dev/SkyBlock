package misc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Rarity: name colour and the bottom lore line")
class RarityTest {

	@Test
	@DisplayName("each rarity's colour is the vanilla named colour SkyBlock uses")
	void colours() {
		assertEquals("white", Rarity.COMMON.colour());
		assertEquals("green", Rarity.UNCOMMON.colour());
		assertEquals("blue", Rarity.RARE.colour());
		assertEquals("dark_purple", Rarity.EPIC.colour());
		assertEquals("gold", Rarity.LEGENDARY.colour());
		assertEquals("light_purple", Rarity.MYTHIC.colour());
	}

	@Test
	@DisplayName("no type reads as the rarity alone, with no double space")
	void bare() {
		assertEquals("<dark_purple><bold><obfuscated>a</obfuscated> EPIC <obfuscated>a</obfuscated>", Rarity.EPIC.lore(""));
	}

	@Test
	@DisplayName("a type follows the rarity")
	void typed() {
		assertEquals("<gold><bold><obfuscated>a</obfuscated> LEGENDARY SWORD <obfuscated>a</obfuscated>", Rarity.LEGENDARY.lore("SWORD"));
	}

	@Test
	@DisplayName("declared lowest to highest, which ladders compare by")
	void order() {
		assertTrue(Rarity.COMMON.compareTo(Rarity.UNCOMMON) < 0);
		assertTrue(Rarity.LEGENDARY.compareTo(Rarity.MYTHIC) < 0);
	}
}
