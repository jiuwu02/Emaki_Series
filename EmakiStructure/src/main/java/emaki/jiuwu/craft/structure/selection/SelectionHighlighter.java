package emaki.jiuwu.craft.structure.selection;

import org.bukkit.entity.Player;

public interface SelectionHighlighter {

    void refresh(Player player, SelectionSession session);

    void destroy(Player player);
}
