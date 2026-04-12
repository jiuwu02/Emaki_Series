package emaki.jiuwu.craft.corelib.text;

import org.bukkit.plugin.java.JavaPlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;

public final class ConsoleOutputs {

    private static final int ASCII_START_COLOR = 0xFF80FF;
    private static final int ASCII_END_COLOR = 0x00FFFF;

    private ConsoleOutputs() {
    }

    public static void sendGradientAscii(JavaPlugin plugin, String asciiArt) {
        if (plugin == null || Texts.isBlank(asciiArt)) {
            return;
        }
        String normalized = Texts.toStringSafe(asciiArt)
                .stripTrailing()
                .replace("\r\n", "\n")
                .replace("\r", "\n");
        for (String line : normalized.split("\n", -1)) {
            AdventureSupport.sendMessage(plugin, plugin.getServer().getConsoleSender(), buildGradientLine(line));
        }
    }

    private static Component buildGradientLine(String line) {
        if (line == null || line.isEmpty()) {
            return Component.empty();
        }
        TextComponent.Builder builder = Component.text();
        int length = line.length();
        for (int index = 0; index < length; index++) {
            float progress = length == 1 ? 0F : (float) index / (length - 1);
            builder.append(Component.text(line.charAt(index), interpolateColor(progress)));
        }
        return builder.build();
    }

    private static TextColor interpolateColor(float progress) {
        int startRed = (ASCII_START_COLOR >> 16) & 0xFF;
        int startGreen = (ASCII_START_COLOR >> 8) & 0xFF;
        int startBlue = ASCII_START_COLOR & 0xFF;

        int endRed = (ASCII_END_COLOR >> 16) & 0xFF;
        int endGreen = (ASCII_END_COLOR >> 8) & 0xFF;
        int endBlue = ASCII_END_COLOR & 0xFF;

        int red = Math.round(startRed + (endRed - startRed) * progress);
        int green = Math.round(startGreen + (endGreen - startGreen) * progress);
        int blue = Math.round(startBlue + (endBlue - startBlue) * progress);
        return TextColor.color(red, green, blue);
    }
}
