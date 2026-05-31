package emaki.jiuwu.craft.item.model;

import java.util.List;

import emaki.jiuwu.craft.corelib.text.Texts;

public record DisabledDisplay(String namePrefix,
        List<String> loreAppend) {

    public DisabledDisplay {
        namePrefix = Texts.toStringSafe(namePrefix);
        loreAppend = loreAppend == null ? List.of() : List.copyOf(loreAppend);
    }

    public static DisabledDisplay empty() {
        return new DisabledDisplay("", List.of());
    }

    public boolean hasNamePrefix() {
        return Texts.isNotBlank(namePrefix);
    }

    public boolean hasLoreAppend() {
        return !loreAppend.isEmpty();
    }
}
