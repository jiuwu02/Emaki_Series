package emaki.jiuwu.craft.attribute.loader;

import java.io.File;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

public final class EditorValidationSupport {

    private EditorValidationSupport() {
    }

    public static boolean attributeOk(EmakiAttributePlugin plugin, File file, YamlSection section) {
        if (file == null || section == null) {
            return false;
        }
        try {
            AttributeRegistry registry = new AttributeRegistry(plugin);
            return registry.validateSchema(file, section) && registry.parse(file, section) != null;
        } catch (RuntimeException failure) {
            return false;
        }
    }

    public static boolean conditionOk(EmakiAttributePlugin plugin, File file, YamlSection section) {
        if (file == null || section == null) {
            return false;
        }
        try {
            return new PdcReadRuleLoader(plugin).validateSchema(file, section);
        } catch (RuntimeException failure) {
            return false;
        }
    }

    public static boolean damageTypeOk(EmakiAttributePlugin plugin, File file, YamlSection section) {
        if (file == null || section == null) {
            return false;
        }
        try {
            AttributeRegistry attributes = plugin == null ? null : plugin.attributeRegistry();
            DamageTypeRegistry registry = new DamageTypeRegistry(plugin, attributes);
            return registry.validateSchema(file, section) && registry.parse(file, section) != null;
        } catch (RuntimeException failure) {
            return false;
        }
    }

    public static boolean loreFormatOk(EmakiAttributePlugin plugin, File file, YamlSection section) {
        if (file == null || section == null) {
            return false;
        }
        try {
            LoreFormatRegistry registry = new LoreFormatRegistry(plugin);
            return registry.validateSchema(file, section) && registry.parse(file, section) != null;
        } catch (RuntimeException failure) {
            return false;
        }
    }

    public static boolean presetOk(EmakiAttributePlugin plugin, File file, YamlSection section) {
        if (file == null || section == null) {
            return false;
        }
        try {
            AttributePresetRegistry registry = new AttributePresetRegistry(plugin);
            return registry.validateSchema(file, section) && registry.parse(file, section) != null;
        } catch (RuntimeException failure) {
            return false;
        }
    }

    public static boolean balanceOk(EmakiAttributePlugin plugin, File file, YamlSection section) {
        if (file == null || section == null) {
            return false;
        }
        try {
            AttributeRegistry attributes = plugin == null ? null : plugin.attributeRegistry();
            return new AttributeBalanceRegistry(plugin, attributes).validateCandidate(section, file);
        } catch (RuntimeException failure) {
            return false;
        }
    }
}
