package emaki.jiuwu.craft.gem.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.item.ConfiguredItemDefinition;
import emaki.jiuwu.craft.corelib.api.item.ItemComponentPatch;
import emaki.jiuwu.craft.corelib.gui.GuiSlot;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.gem.model.GemItemDefinition;

final class GemGuiTemplates {

    static final String DEFAULT_GEM_TEMPLATE = "gem/default";
    static final String DEFAULT_OPEN_TEMPLATE = "open/default";

    private GemGuiTemplates() {
    }

    static GuiTemplate resolveGemTemplate(GuiTemplateLoader loader, GemItemDefinition itemDefinition) {
        return resolve(loader,
                itemDefinition == null ? "" : itemDefinition.guiSettings().gemTemplate(),
                DEFAULT_GEM_TEMPLATE);
    }

    static GuiTemplate resolveOpenTemplate(GuiTemplateLoader loader, GemItemDefinition itemDefinition) {
        return resolve(loader,
                itemDefinition == null ? "" : itemDefinition.guiSettings().openTemplate(),
                DEFAULT_OPEN_TEMPLATE);
    }

    static GuiTemplate resolve(GuiTemplateLoader loader, String configuredId, String defaultId) {
        if (loader == null) {
            return null;
        }
        if (Texts.isNotBlank(configuredId)) {
            GuiTemplate configured = loader.get(configuredId.trim());
            if (configured != null) {
                return configured;
            }
        }
        return loader.get(defaultId);
    }

    static ConfiguredItemDefinition configuredDefinition(GuiSlot slot, String item, String name, List<String> lore) {
        Map<String, ItemComponentPatch> patches = new LinkedHashMap<>(
                slot == null ? Map.of() : slot.itemDefinition().components());
        if (Texts.isNotBlank(name)) {
            patches.putIfAbsent("minecraft:custom_name", ItemComponentPatch.set(name));
        }
        if (lore != null) {
            patches.putIfAbsent("minecraft:lore", ItemComponentPatch.set(List.copyOf(lore)));
        }
        return new ConfiguredItemDefinition(item, 1, patches);
    }
}
