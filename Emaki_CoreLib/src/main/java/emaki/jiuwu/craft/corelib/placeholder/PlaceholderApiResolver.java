package emaki.jiuwu.craft.corelib.placeholder;

import emaki.jiuwu.craft.corelib.operation.OperationContext;
import emaki.jiuwu.craft.corelib.text.Texts;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;

public final class PlaceholderApiResolver implements PlaceholderResolver {

    @Override
    public String resolve(OperationContext context, String text) {
        if (context == null || context.player() == null || Texts.isBlank(text)) {
            return text;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return text;
        }
        try {
            return PlaceholderAPI.setPlaceholders(context.player(), text);
        } catch (Exception ignored) {
            return text;
        }
    }
}
