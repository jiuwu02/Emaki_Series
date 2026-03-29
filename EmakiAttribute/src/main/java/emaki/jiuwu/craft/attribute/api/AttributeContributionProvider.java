package emaki.jiuwu.craft.attribute.api;

import java.util.Collection;
import org.bukkit.entity.LivingEntity;

public interface AttributeContributionProvider {

    String id();

    int priority();

    Collection<AttributeContribution> collect(LivingEntity entity);
}
