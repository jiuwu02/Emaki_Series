package emaki.jiuwu.craft.corelib.gui;

import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public record GuiOpenRequest(Plugin owner,
        Player viewer,
        GuiTemplate template,
        Map<String, ?> replacements,
        GuiRenderer renderer,
        GuiSessionHandler handler) {

}
