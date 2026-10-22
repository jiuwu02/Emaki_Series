package emaki.jiuwu.craft.corelib.api.text;

import org.bukkit.plugin.java.JavaPlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;

/**
 * Sends decorative multi-line ASCII art to the server console with a per-character color
 * gradient.
 *
 * <p>Both helpers are no-ops for a {@code null} plugin or blank art, normalize Windows line
 * endings before splitting, and color each line independently from the start color on the left
 * to the end color on the right.
 */
public final class ConsoleOutputs {

    private static final int ASCII_START_COLOR = 0xFF80FF;
    private static final int ASCII_END_COLOR = 0x00FFFF;

    private ConsoleOutputs() {
    }

    /**
     * Sends the art with the default pink-to-cyan gradient.
     *
     * @param plugin plugin whose console receives the art; {@code null} makes this a no-op
     * @param asciiArt multi-line art; blank makes this a no-op
     */
    public static void sendGradientAscii(JavaPlugin plugin, String asciiArt) {
        sendGradientAscii(plugin, asciiArt, ASCII_START_COLOR, ASCII_END_COLOR);
    }

    /**
     * Sends the art line by line with an explicit gradient.
     *
     * <p>Trailing whitespace of the whole text is stripped and line endings are normalized to
     * {@code \n} first; every line is then sent to the console sender as one component whose
     * characters interpolate evenly from {@code startColor} to {@code endColor} (a
     * one-character line keeps the start color). Empty lines are sent as empty components.
     *
     * @param plugin plugin whose console receives the art; {@code null} makes this a no-op
     * @param asciiArt multi-line art; blank makes this a no-op
     * @param startColor {@code 0xRRGGBB} color of the leftmost character
     * @param endColor {@code 0xRRGGBB} color of the rightmost character
     */
    public static void sendGradientAscii(JavaPlugin plugin, String asciiArt, int startColor, int endColor) {
        if (plugin == null || Texts.isBlank(asciiArt)) {
            return;
        }
        String normalized = Texts.toStringSafe(asciiArt)
                .stripTrailing()
                .replace("\r\n", "\n")
                .replace("\r", "\n");
        for (String line : normalized.split("\n", -1)) {
            plugin.getServer().getConsoleSender().sendMessage(buildGradientLine(line, startColor, endColor));
        }
    }

    private static Component buildGradientLine(String line, int startColor, int endColor) {
        if (line == null || line.isEmpty()) {
            return Component.empty();
        }
        TextComponent.Builder builder = Component.text();
        int length = line.length();
        for (int index = 0; index < length; index++) {
            float progress = length == 1 ? 0F : (float) index / (length - 1);
            builder.append(Component.text(line.charAt(index), interpolateColor(progress, startColor, endColor)));
        }
        return builder.build();
    }

    private static TextColor interpolateColor(float progress, int startColor, int endColor) {
        int startRed = (startColor >> 16) & 0xFF;
        int startGreen = (startColor >> 8) & 0xFF;
        int startBlue = startColor & 0xFF;

        int endRed = (endColor >> 16) & 0xFF;
        int endGreen = (endColor >> 8) & 0xFF;
        int endBlue = endColor & 0xFF;

        int red = Math.round(startRed + (endRed - startRed) * progress);
        int green = Math.round(startGreen + (endGreen - startGreen) * progress);
        int blue = Math.round(startBlue + (endBlue - startBlue) * progress);
        return TextColor.color(red, green, blue);
    }
}
