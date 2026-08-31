package emaki.jiuwu.craft.corelib.pdc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import emaki.jiuwu.craft.corelib.api.pdc.PdcKeyMigration;

@DisplayName("PDC 键名扁平化约束")
class PdcKeyFlatteningTest {

    private static final List<String> FLAT_PARTITION_ROOTS = List.of(
            "item_attributes",
            "item_skills",
            "gem_item",
            "gem_opener",
            "forge",
            "strengthen_affix",
            "strengthen_mastery",
            "item_state",
            "item_operations",
            "combat",
            "combat_resource",
            "projectile",
            "player",
            "emakiitem"
    );

    @Test
    @DisplayName("分隔符是下划线，不是点")
    void separatorIsUnderscore() {
        assertEquals("_", PdcPartition.SEPARATOR);
    }

    @Test
    @DisplayName("qualifiedPath 用下划线连接分区与字段")
    void qualifiedPathJoinsWithUnderscore() {
        PdcPartition partition = new PdcPartition("emaki_attribute", "item_attributes");
        assertEquals("item_attributes_source_index", partition.qualifiedPath("source_index"));
        assertFalse(partition.qualifiedPath("source_index").contains("."));
    }

    @Test
    @DisplayName("child 用下划线连接，不引入点")
    void childJoinsWithUnderscore() {
        PdcPartition root = new PdcPartition("emaki", "item_state");
        assertEquals("item_state_meta", root.child("meta").path());
        assertEquals("item_state_meta_revision", root.child("meta").qualifiedPath("revision"));
        assertFalse(root.child("meta").qualifiedPath("revision").contains("."));
    }

    @Test
    @DisplayName("所有实际使用的分区根不含点")
    void knownPartitionRootsAreFlat() {
        for (String root : FLAT_PARTITION_ROOTS) {
            PdcPartition partition = new PdcPartition("emaki", root);
            assertFalse(partition.path().contains("."),
                    "分区根含点: " + root);
            assertFalse(partition.qualifiedPath("field").contains("."),
                    "分区根拼字段后含点: " + root);
        }
    }

    @Test
    @DisplayName("根分区不产生前导分隔符")
    void rootPartitionHasNoLeadingSeparator() {
        PdcPartition root = new PdcPartition("emaki", "");
        assertEquals("", root.path());
        assertEquals("emakiattribute_snapshot", root.qualifiedPath("emakiattribute_snapshot"));
        assertEquals("layer", root.child("layer").path());
    }

    @Test
    @DisplayName("不压缩连续下划线，避免键名碰撞")
    void consecutiveUnderscoresAreNotCollapsed() {
        PdcPartition partition = new PdcPartition("emaki", "a");
        assertNotEquals(partition.child("b").path(), partition.child("_b").path());
        assertEquals("a__b", partition.child("_b").path());
    }

    @Test
    @DisplayName("迁移规则把历史带点键转成扁平键")
    void migrationFlattensLegacyKeys() {
        assertEquals("item_attributes_source_index",
                PdcKeyMigration.newKeyPath("emaki_attribute", "item.attributes.source_index"));
        assertEquals("item_skills_ids",
                PdcKeyMigration.newKeyPath("emaki_skills", "item.skills.ids"));
        assertEquals("forge_quality_id",
                PdcKeyMigration.newKeyPath("emakiforge", "forge.quality_id"));
        assertEquals("strengthen_affix_layer",
                PdcKeyMigration.newKeyPath("emaki_strengthen", "strengthen.affix.layer"));
        assertEquals("item_operations",
                PdcKeyMigration.newKeyPath("emaki", "item.operations"));
        assertEquals("emakiitem_set_signature",
                PdcKeyMigration.newKeyPath("emaki", "emakiitem.set_signature"));
        assertEquals("gem_item_id",
                PdcKeyMigration.newKeyPath("emaki", "gem.item.id"));
        assertEquals("combat_resource_mana_current_value",
                PdcKeyMigration.newKeyPath("emaki_attribute", "combat.resource.mana.current_value"));
        assertEquals("player_mining_level",
                PdcKeyMigration.newKeyPath("emakilevel", "player.mining_level"));
    }

    @Test
    @DisplayName("迁移结果永不含点")
    void migrationResultsAreFlat() {
        List<String[]> legacyKeys = List.of(
                new String[] { "emaki_attribute", "item.attributes.source.my_plugin.payload" },
                new String[] { "emaki_attribute", "projectile.arrow.launched_at" },
                new String[] { "emaki_skills", "item.skills.active_slot" },
                new String[] { "emakiforge", "forge.quality_multiplier" },
                new String[] { "emaki_strengthen", "strengthen.mastery.layer" },
                new String[] { "emaki", "item.presentation_snapshot" },
                new String[] { "emaki", "item.schema_version" },
                new String[] { "emakilevel", "player.combat_total_exp" }
        );
        for (String[] legacyKey : legacyKeys) {
            String migrated = PdcKeyMigration.newKeyPath(legacyKey[0], legacyKey[1]);
            assertFalse(migrated == null || migrated.contains("."),
                    "未迁移或结果仍含点: " + legacyKey[0] + ":" + legacyKey[1] + " -> " + migrated);
        }
    }

    @Test
    @DisplayName("装配层快照键按后缀迁移，只替换连接符")
    void layerSnapshotSuffixMigration() {
        assertEquals("emakiattribute_snapshot",
                PdcKeyMigration.newKeyPath("emaki", "emakiattribute.snapshot"));
        assertEquals("my.ns_snapshot",
                PdcKeyMigration.newKeyPath("emaki", "my.ns.snapshot"));
    }

    @Test
    @DisplayName("item_state 的服主自定义字段名保留原点号，避免键名碰撞")
    void itemStateCustomFieldKeepsInnerDot() {
        assertEquals("item_state_meta_revision",
                PdcKeyMigration.newKeyPath("emaki", "item_state.meta.revision"));
        assertEquals("item_state_my.field",
                PdcKeyMigration.newKeyPath("emaki", "item_state.my.field"));
    }

    @Test
    @DisplayName("已扁平的键不再迁移，规则无自映射")
    void flatKeysAreNotMigratedAgain() {
        assertNull(PdcKeyMigration.newKeyPath("emaki_attribute", "item_attributes_source_index"));
        assertNull(PdcKeyMigration.newKeyPath("emakiforge", "forge_quality_id"));
        assertNull(PdcKeyMigration.newKeyPath("emaki", "item_operations"));
        assertNull(PdcKeyMigration.newKeyPath("emaki", "emakiattribute_snapshot"));
        assertNull(PdcKeyMigration.newKeyPath("emakilevel", "player_mining_level"));
    }

    @Test
    @DisplayName("规则表外的命名空间与键不被改动")
    void unknownKeysAreLeftAlone() {
        assertNull(PdcKeyMigration.newKeyPath("thirdparty", "some.key"));
        assertNull(PdcKeyMigration.newKeyPath("emakicooking", "station.state"));
        assertNull(PdcKeyMigration.newKeyPath(null, "item.operations"));
        assertNull(PdcKeyMigration.newKeyPath("emaki", null));
    }

    @Test
    @DisplayName("规则覆盖的命名空间与实现一致")
    void coveredNamespacesMatchImplementation() {
        assertTrue(PdcKeyMigration.coveredNamespaces().containsAll(List.of(
                "emaki",
                "emaki_attribute",
                "emaki_skills",
                "emakiforge",
                "emaki_strengthen",
                "emakilevel")));
    }
}
