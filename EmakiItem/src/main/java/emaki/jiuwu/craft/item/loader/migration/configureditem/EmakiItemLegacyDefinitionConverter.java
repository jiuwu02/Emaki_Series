package emaki.jiuwu.craft.item.loader.migration.configureditem;

import java.util.Map;

import emaki.jiuwu.craft.corelib.item.migration.configureditem.ConfiguredItemMigration.Conversion;
import emaki.jiuwu.craft.corelib.item.migration.configureditem.ConfiguredItemNodeConverter;
import emaki.jiuwu.craft.corelib.item.migration.configureditem.ConfiguredItemNodeConverter.NodeMigration;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class EmakiItemLegacyDefinitionConverter {

    private EmakiItemLegacyDefinitionConverter() {
    }

    public static Conversion convert(Map<String, Object> root) {
        String itemId = Texts.normalizeId(Texts.toStringSafe(root.get("id")));
        NodeMigration migrated = ConfiguredItemNodeConverter.convertLegacyItemNode(root, itemId);
        if (Texts.isNotBlank(migrated.skipReason())) {
            return Conversion.skipped(root, migrated.skipReason());
        }
        return migrated.changed()
                ? Conversion.changed(migrated.values(), 1)
                : Conversion.unchanged(root);
    }
}
