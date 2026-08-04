package emaki.jiuwu.craft.corelib.assembly;

import java.util.List;

import emaki.jiuwu.craft.corelib.api.text.Texts;

record ItemOperationBaseView(String customName, List<String> lore) {

    ItemOperationBaseView {
        customName = Texts.toStringSafe(customName);
        lore = lore == null || lore.isEmpty() ? List.of() : List.copyOf(lore);
    }
}
