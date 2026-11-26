package emaki.jiuwu.craft.dungeon.editor;

import java.util.Map;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;

final class GuiTemplateTitle {

    private GuiTemplateTitle() {
    }

    static String resolve(GuiTemplate template, Map<String, ?> replacements, String fallback) {
        String title = template.title();
        if (Texts.isBlank(title)) {
            return fallback;
        }
        return Texts.formatTemplate(title, replacements == null ? Map.of() : replacements);
    }
}
