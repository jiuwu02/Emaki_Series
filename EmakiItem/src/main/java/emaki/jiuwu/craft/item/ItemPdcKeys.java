package emaki.jiuwu.craft.item;

import org.bukkit.NamespacedKey;

public final class ItemPdcKeys {

    public static final NamespacedKey DISABLED = new NamespacedKey("emakiitem", "disabled");

    public static final NamespacedKey PROJECTILE_SOURCE_ITEM =
            new NamespacedKey("emakiitem", "projectile_source_item");

    public static final String ASSEMBLY_PARTITION = "item";

    public static final String ASSEMBLY_FIELD_SCHEMA_VERSION = "schema_version";
    public static final String ASSEMBLY_FIELD_BASE_SOURCE = "base_source";
    public static final String ASSEMBLY_FIELD_BASE_CUSTOM_NAME = "base_custom_name";
    public static final String ASSEMBLY_FIELD_BASE_LORE = "base_lore";

    private ItemPdcKeys() {}
}
