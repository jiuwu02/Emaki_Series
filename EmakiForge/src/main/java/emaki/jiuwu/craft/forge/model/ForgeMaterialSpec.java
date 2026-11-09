package emaki.jiuwu.craft.forge.model;

import java.util.List;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.matcher.Matcher;

public record ForgeMaterialSpec(
        String item,
        int amount,
        boolean optional,
        int capacityCost,
        List<ForgeMaterial.MaterialEffect> effects,
        List<ItemSourceRef> itemSources,
        Matcher matcher,
        String matcherKey,
        String materialId,
        String countKey,
        String auditId,
        boolean materialIdDeclared,
        boolean countKeyDeclared,
        boolean auditIdDeclared) {
}
