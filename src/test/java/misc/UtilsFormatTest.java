package misc;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Utils' static init reads Enchantment and Attribute constants: testsupport.FakeRegistryAccess lets it load.
@DisplayName("Utils: lore number formatting, and wrapLore through MiniMessage")
class UtilsFormatTest {

	@Test
	@DisplayName("damageNumber: no trailing .0, at most two decimals")
	void damageNumber() {
		assertEquals("5", Utils.damageNumber(5.0));
		assertEquals("1.25", Utils.damageNumber(1.25));
		assertEquals("0.3", Utils.damageNumber(0.1 + 0.2));
		assertEquals("-1.5", Utils.damageNumber(-1.5));
	}

	@Test
	@DisplayName("damageNumber rounds the double's exact binary value: 2.675 is stored just under")
	void damageNumberBinary() {
		assertEquals("2.67", Utils.damageNumber(2.675));
	}

	@Test
	@DisplayName("tenthNumber: nearest 0.1, half up")
	void tenth() {
		assertEquals("8.7", Utils.tenthNumber(8.74));
		assertEquals("8.8", Utils.tenthNumber(8.75));
		assertEquals("3", Utils.tenthNumber(3.04));
	}

	@Test
	@DisplayName("percent takes a 0-1 share, to 0.1%")
	void percent() {
		assertEquals("15%", Utils.percent(0.15));
		assertEquals("33.5%", Utils.percent(0.335));
		assertEquals("10%", Utils.percent(0.1));
	}

	@Test
	@DisplayName("wrapLore parses MiniMessage, wraps at LORE_WIDTH and keeps colour")
	void wrapLore() {
		String words = String.join(" ", java.util.Collections.nCopies(20, "aaaaa"));
		List<Component> lines = MinecraftFont.wrapLore("<red>" + words);
		assertTrue(lines.size() > 1);
		for(Component line : lines) {
			assertTrue(MinecraftFont.width(line) <= MinecraftFont.LORE_WIDTH);
			assertEquals(TextDecoration.State.FALSE, line.decoration(TextDecoration.ITALIC));
			assertEquals(NamedTextColor.RED, line.children().getFirst().color());
		}
		assertEquals(words, String.join(" ", lines.stream().map(PlainTextComponentSerializer.plainText()::serialize).toList()));
	}
}
