package emaki.jiuwu.craft.attribute.api;

import java.util.Collection;
import org.bukkit.entity.Player;

public interface AttributeContributionProvider {

    String id();

    int priority();

    Collection<AttributeContribution> collect(Player player);
}
