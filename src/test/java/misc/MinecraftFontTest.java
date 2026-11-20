package misc;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// wrapLore goes through Utils.mm, and Utils' static init needs the Bukkit registry: tests use wrap() directly.
@DisplayName("MinecraftFont: glyph widths and style-keeping word wrap")
class MinecraftFontTest {

	private static List<String> plain(List<Component> lines) {
		return lines.stream().map(PlainTextComponentSerializer.plainText()::serialize).toList();
	}

	@Test
	@DisplayName("glyph widths include the 1px spacing")
	void charWidths() {
		assertEquals(2, MinecraftFont.charWidth('i'));
		assertEquals(3, MinecraftFont.charWidth('l'));
		assertEquals(4, MinecraftFont.charWidth(' '));
		assertEquals(4, MinecraftFont.charWidth('I'));
		assertEquals(5, MinecraftFont.charWidth('f'));
		assertEquals(7, MinecraftFont.charWidth('@'));
		assertEquals(6, MinecraftFont.charWidth('a'));
	}

	@Test
	@DisplayName("bold adds one pixel per glyph, spaces included")
	void boldWidth() {
		assertEquals(12, MinecraftFont.width("ab"));
		assertEquals(14, MinecraftFont.width("ab", true));
		assertEquals(5, MinecraftFont.width(" ", true));
	}

	@Test
	@DisplayName("LORE_WIDTH is the Hyperion ability header, 'RIGHT CLICK' bold")
	void loreWidth() {
		assertEquals(177, MinecraftFont.LORE_WIDTH);
	}

	@Test
	@DisplayName("a component's width counts bold runs, inherited bold included")
	void componentWidth() {
		assertEquals(26, MinecraftFont.width(Component.text("ab").append(Component.text("cd").decorate(TextDecoration.BOLD))));
		assertEquals(14, MinecraftFont.width(Component.text("").decorate(TextDecoration.BOLD).append(Component.text("ab"))));
	}

	@Test
	@DisplayName("text that fits stays on one line")
	void fits() {
		assertEquals(List.of("aaa bbb"), plain(MinecraftFont.wrap(Component.text("aaa bbb"), 1000)));
	}

	@Test
	@DisplayName("wraps at a word boundary, exactly-full line kept, no leading space on the next")
	void wraps() {
		// "aaa bbb" = 18 + 4 + 18 = 40
		assertEquals(List.of("aaa bbb", "ccc"), plain(MinecraftFont.wrap(Component.text("aaa bbb ccc"), 40)));
		assertEquals(List.of("aaa", "bbb", "ccc"), plain(MinecraftFont.wrap(Component.text("aaa bbb ccc"), 39)));
	}

	@Test
	@DisplayName("a word wider than the line overruns on its own line, never split")
	void longWord() {
		assertEquals(List.of("aa", "aaaaaaaaaa", "aa"), plain(MinecraftFont.wrap(Component.text("aa aaaaaaaaaa aa"), 30)));
	}

	@Test
	@DisplayName("a newline always breaks")
	void newline() {
		assertEquals(List.of("aa", "bb"), plain(MinecraftFont.wrap(Component.text("aa\nbb"), 1000)));
	}

	@Test
	@DisplayName("empty input is one empty line")
	void empty() {
		assertEquals(List.of(""), plain(MinecraftFont.wrap(Component.empty(), 100)));
	}

	@Test
	@DisplayName("bold text wraps earlier than the same text plain")
	void boldWrapsEarlier() {
		assertEquals(1, MinecraftFont.wrap(Component.text("aaa bbb"), 40).size());
		assertEquals(2, MinecraftFont.wrap(Component.text("aaa bbb").decorate(TextDecoration.BOLD), 40).size());
	}

	@Test
	@DisplayName("a line starting mid-run keeps the run's colour")
	void styleSurvivesWrap() {
		List<Component> lines = MinecraftFont.wrap(Component.text("aaa ").append(Component.text("bbb", NamedTextColor.RED)), 20);
		assertEquals(List.of("aaa", "bbb"), plain(lines));
		assertEquals(NamedTextColor.RED, lines.get(1).children().getFirst().color());
	}

	@Test
	@DisplayName("every line is explicitly non-italic, lore's default is italic")
	void notItalic() {
		for(Component line : MinecraftFont.wrap(Component.text("aaa bbb ccc"), 40)) {
			assertEquals(TextDecoration.State.FALSE, line.decoration(TextDecoration.ITALIC));
		}
	}
}
