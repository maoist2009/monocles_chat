package eu.siacs.conversations.utils;

import net.fellbaum.jemoji.EmojiManager;
import com.google.common.collect.ImmutableSet;
import java.util.Set;

public class Emoticons {

    private static final int VARIATION_16 = 0xFE0F;
    private static final int VARIATION_15 = 0xFE0E;
    private static final String VARIATION_16_STRING = new String(new char[] {VARIATION_16});
    private static final String VARIATION_15_STRING = new String(new char[] {VARIATION_15});

    private static final Set<String> TEXT_DEFAULT_TO_VS16 =
            ImmutableSet.of(
                    "❤",
                    "✔",
                    "✖",
                    "➕",
                    "➖",
                    "➗",
                    "⭐",
                    "⚡",
                    "\uD83C\uDF96",
                    "\uD83C\uDFC6",
                    "\uD83E\uDD47",
                    "\uD83E\uDD48",
                    "\uD83E\uDD49",
                    "\uD83D\uDC51",
                    "⚓",
                    "⛵",
                    "✈",
                    "⚖",
                    "⛑",
                    "⚒",
                    "⛏",
                    "☎",
                    "⛄",
                    "⛅",
                    "⚠",
                    "⚛",
                    "✡",
                    "☮",
                    "☯",
                    "☀",
                    "⬅",
                    "➡",
                    "⬆",
                    "⬇");

    public static String normalizeToVS16(final String input) {
        return TEXT_DEFAULT_TO_VS16.contains(input) && !input.endsWith(VARIATION_15_STRING)
                ? input + VARIATION_16_STRING
                : input;
    }

    public static String existingVariant(final String original, final Set<String> existing) {
        if (existing.contains(original) || original.endsWith(VARIATION_15_STRING)) {
            return original;
        }
        final var variant =
                original.endsWith(VARIATION_16_STRING)
                        ? original.substring(0, original.length() - 1)
                        : original + VARIATION_16_STRING;
        return existing.contains(variant) ? variant : original;
    }

    public static boolean isEmoji(String input) {
        return EmojiManager.isEmoji(input);
    }

    public static boolean isOnlyEmoji(String input) {
        if (input.trim().length() == 0) return false; // Vaccuously true but not useful

        return EmojiManager.removeAllEmojis(input).replaceAll("\uFE0F", "").trim().length() == 0;
    }
}
