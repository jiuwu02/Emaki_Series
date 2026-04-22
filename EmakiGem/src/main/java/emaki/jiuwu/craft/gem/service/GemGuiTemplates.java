package emaki.jiuwu.craft.gem.service;

import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.gem.model.GemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemDefinition;

final class GemGuiTemplates {

    static final String DEFAULT_GEM_TEMPLATE = "gem/default";
    static final String DEFAULT_UPGRADE_TEMPLATE = "upgrade/default";
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

    static GuiTemplate resolveUpgradeTemplate(GuiTemplateLoader loader, GemDefinition gemDefinition) {
        return resolve(loader,
                gemDefinition == null ? "" : gemDefinition.upgrade().guiTemplate(),
                DEFAULT_UPGRADE_TEMPLATE);
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
}
