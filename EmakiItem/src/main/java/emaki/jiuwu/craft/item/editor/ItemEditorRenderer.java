package emaki.jiuwu.craft.item.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.gui.GuiItemBuilder;
import emaki.jiuwu.craft.corelib.gui.GuiSlot;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.item.EmakiItemPlugin;

public final class ItemEditorRenderer {

    private final EmakiItemPlugin plugin;

    private static final emaki.jiuwu.craft.corelib.item.MinecraftItemComponentCatalog CATALOG =
            new emaki.jiuwu.craft.corelib.item.MinecraftItemComponentCatalog();

    public static final String SKIN_FIELD_ID = "skin_edit";

    public ItemEditorRenderer(EmakiItemPlugin plugin) {
        this.plugin = plugin;
    }

    private List<ItemEditorField> adaptiveComponents(ItemEditorSession session) {
        String material = materialOf(session);
        List<ItemEditorField> fields = new ArrayList<>();
        for (emaki.jiuwu.craft.corelib.item.MinecraftItemComponentCatalog.Entry entry : CATALOG.specializedFor(material)) {
            if (emaki.jiuwu.craft.corelib.item.ProfileComponentSupport.PROFILE_COMPONENT_ID
                    .equals(entry.componentId())) {
                fields.add(new ItemEditorField(SKIN_FIELD_ID, ItemEditorField.Kind.COMMAND,
                        "editor.field.skin_edit", skinSummary(session), List.of(), -1, true));
                continue;
            }
            fields.add(new ItemEditorField("component_" + entry.componentId(), ItemEditorField.Kind.TEXT,
                    entry.componentId(), entry.valueFormat(), List.of(), -1, true));
        }
        return fields;
    }

    static String materialOf(ItemEditorSession session) {
        Object source = session.draft().value("item", "item_source");
        if (source == null) {
            source = session.draft().value("item", "source");
        }
        String raw = Texts.toStringSafe(source).trim();
        if (raw.isEmpty()) {
            return "";
        }
        return raw.startsWith("minecraft-") ? "minecraft:" + raw.substring("minecraft-".length()) : raw;
    }

    static String skinSummary(ItemEditorSession session) {
        Object profile = session.draft().value("item", "components",
                emaki.jiuwu.craft.corelib.item.ProfileComponentSupport.PROFILE_COMPONENT_ID);
        String url = emaki.jiuwu.craft.corelib.item.ProfileComponentSupport.textureUrlOf(profile);
        if (url != null) {
            return url;
        }
        String value = emaki.jiuwu.craft.corelib.item.ProfileComponentSupport.textureValueOf(profile);
        if (value != null) {
            return value.length() > 32 ? value.substring(0, 32) + "..." : value;
        }
        return profile instanceof Map<?, ?> map && map.get("name") != null
                ? Texts.toStringSafe(map.get("name"))
                : "-";
    }

    public List<ItemEditorField> fields(ItemEditorSession session, String menuId) {
        if (ItemEditorMenus.SET_LIST.equals(menuId)) {
            List<ItemEditorField> fields = new ArrayList<>();
            for (String setId : setIds()) {
                fields.add(new ItemEditorField("set_" + setId, ItemEditorField.Kind.COMMAND,
                        setId, setId, List.of(), -1, true));
            }
            return fields;
        }
        String listPath = session.context("list_path");
        if (listPath == null || listPath.isBlank()) {
            List<ItemEditorFieldSpec> specs = ItemEditorMenus.specs(menuId, session);
            List<ItemEditorField> fields = new ArrayList<>(specs.size());
            for (ItemEditorFieldSpec spec : specs) {
                fields.add(toField(session, menuId, spec));
            }
            if (ItemEditorMenus.COMPONENTS.equals(menuId)) {
                fields.addAll(adaptiveComponents(session));
            }
            return fields;
        }
        Object target = session.draftFor(menuId).value(listPath.split("\\."));
        if (target instanceof Map<?, ?> map) {
            List<ItemEditorField> fields = new ArrayList<>(map.size());
            int position = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                fields.add(new ItemEditorField(
                        "entry_" + position,
                        ItemEditorField.Kind.COMMAND,
                        "editor.field.entry",
                        Texts.toStringSafe(entry.getKey()) + " -> " + describe(entry.getValue()),
                        List.of(Texts.toStringSafe(entry.getKey())),
                        position,
                        true));
                position++;
            }
            return fields;
        }
        List<Object> items = new ArrayList<>();
        if (target instanceof List<?> list) {
            items.addAll(list);
        }
        List<ItemEditorField> fields = new ArrayList<>(items.size());
        for (int index = 0; index < items.size(); index++) {
            fields.add(new ItemEditorField(
                    "entry_" + index,
                    ItemEditorField.Kind.COMMAND,
                    "editor.field.entry",
                    describe(items.get(index)),
                    List.of(),
                    index,
                    true));
        }
        return fields;
    }

    public int entryCount(ItemEditorSession session, String menuId) {
        return fields(session, menuId).size();
    }

    public ItemStack render(ItemEditorSession session, String menuId, GuiTemplate.ResolvedSlot resolvedSlot) {
        if (resolvedSlot == null || resolvedSlot.definition() == null) {
            return null;
        }
        GuiSlot slot = resolvedSlot.definition();
        String type = Texts.lower(slot.type());
        if (ItemEditorGuiService.TYPE_FIELD_ENTRY.equals(type)) {
            return renderField(session, menuId, slot, resolvedSlot.slotIndex());
        }
        if (ItemEditorGuiService.TYPE_PREVIEW.equals(type)) {
            return renderPreview(session, slot);
        }
        if (ItemEditorGuiService.TYPE_FILE_PATH.equals(type)) {
            return GuiItemBuilder.build(slot.itemDefinition(),
                    Map.of("file_path", String.valueOf(session.document().path())),
                    plugin.coreLib().configuredItemService());
        }
        return buildStatic(slot);
    }

    private ItemStack renderField(ItemEditorSession session, String menuId, GuiSlot slot, int slotIndex) {
        List<ItemEditorField> fields = fields(session, menuId);
        int index = session.page() * pageSize() + slotIndex;
        if (index < 0 || index >= fields.size()) {
            return buildStatic(slot);
        }
        ItemEditorField field = fields.get(index);
        List<String> lore = new ArrayList<>();
        String description = plugin.messageService().messageOrFallback(field.labelKey() + "_desc", null);
        if (description != null && !description.isBlank()) {
            lore.add(description);
        }
        lore.add(plugin.messageService().message("editor.field.value", Map.of("value", field.valueKey())));
        lore.add(field.enabled()
                ? plugin.messageService().message("editor.field.hint." + field.kind().name().toLowerCase())
                : plugin.messageService().message("editor.field.disabled"));
        return GuiItemBuilder.build(
                slot,
                fieldIcon(field),
                plugin.messageService().message(field.labelKey()),
                lore,
                Map.of(),
                plugin.coreLib().configuredItemService());
    }

    private ItemStack renderPreview(ItemEditorSession session, GuiSlot slot) {
        try {
            return plugin.itemFactory().rebuildBase(
                    new emaki.jiuwu.craft.item.model.EmakiItemDefinitionParser(plugin.getLogger())
                            .parse(emaki.jiuwu.craft.corelib.api.yaml.YamlFiles.load(session.draft().text()),
                                    session.document().path().toString()),
                    1);
        } catch (RuntimeException failure) {
            return buildStatic(slot);
        }
    }

    private ItemStack buildStatic(GuiSlot slot) {
        return GuiItemBuilder.build(slot.itemDefinition(), Map.of(), plugin.coreLib().configuredItemService());
    }

    private int pageSize() {
        return 21;
    }

    private static String fieldIcon(ItemEditorField field) {
        String specific = specificIcon(field.id());
        if (specific != null) {
            return specific;
        }
        if (field.id().startsWith("component_")) {
            String componentIcon = componentIcon(field.id().substring("component_".length()));
            if (componentIcon != null) {
                return componentIcon;
            }
        }
        return switch (field.kind()) {
            case TOGGLE -> "minecraft-lever";
            case CYCLE -> "minecraft-comparator";
            case NUMBER -> "minecraft-clock";
            case TEXT -> "minecraft-book";
            case NAVIGATE -> "minecraft-oak_door";
            case LIST -> "minecraft-chest";
            case COMMAND -> "minecraft-paper";
        };
    }

    private static String specificIcon(String fieldId) {
        return switch (fieldId) {
            case "equip_slot" -> "minecraft-armor_stand";
            case "item_source" -> "minecraft-grass_block";
            case "update_enabled" -> "minecraft-redstone_torch";
            case "preserve_amount" -> "minecraft-bundle";
            case "preserve_damage" -> "minecraft-anvil";
            case "preserve_unknown" -> "minecraft-shulker_box";
            case "trigger_join" -> "minecraft-oak_door";
            case "trigger_held_change" -> "minecraft-iron_sword";
            case "trigger_inventory_click" -> "minecraft-chest";
            case "trigger_inventory_drag" -> "minecraft-rail";
            case "trigger_pickup" -> "minecraft-hopper";
            case "trigger_interact" -> "minecraft-lever";
            case "trigger_command" -> "minecraft-command_block";
            case "set_id", "set_display_name" -> "minecraft-name_tag";
            case "set_piece", "set_pieces", "set_lore_equipped" -> "minecraft-iron_chestplate";
            case "manage_sets" -> "minecraft-bookshelf";
            case "set_thresholds" -> "minecraft-ladder";
            case "set_lore_header" -> "minecraft-writable_book";
            case "set_lore_missing" -> "minecraft-gray_dye";
            case "set_lore_active" -> "minecraft-lime_dye";
            case "set_lore_inactive" -> "minecraft-light_gray_dye";
            case "set_lore_separator" -> "minecraft-stick";
            case "condition_type", "cond_type", "mat_matcher_type" -> "minecraft-comparator";
            case "condition_required_count", "cond_required_count", "mat_amount" -> "minecraft-clock";
            case "condition_invalid" -> "minecraft-redstone_torch";
            case "condition_fail_message" -> "minecraft-oak_sign";
            case "condition_block_output" -> "minecraft-redstone_block";
            case "condition_entries", "cond_nested", "mat_item_sources" -> "minecraft-chest";
            case "condition_pass_actions", "repair_on_repaired" -> "minecraft-lime_dye";
            case "condition_fail_actions", "repair_on_disabled" -> "minecraft-red_dye";
            case "cond_expression", "mat_matcher_value" -> "minecraft-book";
            case "repair_enabled" -> "minecraft-anvil";
            case "repair_name_prefix" -> "minecraft-name_tag";
            case "repair_materials" -> "minecraft-iron_ingot";
            case "repair_economy" -> "minecraft-gold_ingot";
            case "repair_lore_append" -> "minecraft-writable_book";
            case "repair_economy_enabled" -> "minecraft-redstone_torch";
            case "repair_economy_restore", "mat_restore" -> "minecraft-experience_bottle";
            case "repair_currencies" -> "minecraft-gold_nugget";
            case "mat_matcher_component" -> "minecraft-name_tag";
            case "mat_matcher_operator" -> "minecraft-redstone_torch";
            case "comp_custom_name" -> "minecraft-name_tag";
            case "comp_item_name" -> "minecraft-oak_sign";
            case "comp_lore" -> "minecraft-writable_book";
            case "comp_max_stack_size" -> "minecraft-bundle";
            case "comp_max_damage" -> "minecraft-anvil";
            case "comp_damage" -> "minecraft-stonecutter";
            case "comp_enchantable" -> "minecraft-enchanting_table";
            case "comp_unbreakable" -> "minecraft-netherite_ingot";
            case "comp_enchantment_glint" -> "minecraft-spectral_arrow";
            case "comp_rarity" -> "minecraft-emerald";
            case "comp_item_model" -> "minecraft-item_frame";
            case "comp_tooltip_style" -> "minecraft-oak_hanging_sign";
            case "comp_enchantments" -> "minecraft-enchanted_book";
            case "comp_attribute_modifiers" -> "minecraft-iron_axe";
            case "comp_custom_model_data" -> "minecraft-brush";
            case "comp_unset" -> "minecraft-structure_void";
            case "comp_reset" -> "minecraft-milk_bucket";
            case "effects_list", "effect_type" -> "minecraft-nether_star";
            case "effect_variables" -> "minecraft-paper";
            case "effect_attributes" -> "minecraft-iron_axe";
            case "effect_skills" -> "minecraft-blaze_rod";
            case "effect_skill_triggers" -> "minecraft-tripwire_hook";
            case "effect_accessory_slots" -> "minecraft-amethyst_shard";
            case "effect_name_actions" -> "minecraft-name_tag";
            case "effect_lore_actions" -> "minecraft-writable_book";
            case "actions_list" -> "minecraft-redstone";
            case ItemEditorRenderer.SKIN_FIELD_ID -> "minecraft-player_head";
            default -> null;
        };
    }

    private static String componentIcon(String componentId) {
        return switch (componentId) {
            case "minecraft:food" -> "minecraft-cooked_beef";
            case "minecraft:consumable" -> "minecraft-golden_apple";
            case "minecraft:potion_contents" -> "minecraft-potion";
            case "minecraft:suspicious_stew_contents" -> "minecraft-suspicious_stew";
            case "minecraft:dyed_color" -> "minecraft-cyan_dye";
            case "minecraft:trim" -> "minecraft-netherite_upgrade_smithing_template";
            case "minecraft:profile" -> "minecraft-player_head";
            case "minecraft:fireworks" -> "minecraft-firework_rocket";
            case "minecraft:fire_resistant" -> "minecraft-magma_cream";
            case "minecraft:equippable" -> "minecraft-iron_helmet";
            case "minecraft:tool", "minecraft:can_break" -> "minecraft-iron_pickaxe";
            case "minecraft:weapon" -> "minecraft-netherite_sword";
            case "minecraft:stored_enchantments", "minecraft:enchantable" -> "minecraft-enchanted_book";
            case "minecraft:repairable" -> "minecraft-iron_ingot";
            case "minecraft:glider" -> "minecraft-elytra";
            case "minecraft:blocks_attacks" -> "minecraft-shield";
            case "minecraft:death_protection" -> "minecraft-totem_of_undying";
            case "minecraft:use_remainder" -> "minecraft-bucket";
            case "minecraft:use_cooldown" -> "minecraft-clock";
            case "minecraft:charged_projectiles" -> "minecraft-crossbow";
            case "minecraft:bundle_contents" -> "minecraft-bundle";
            case "minecraft:entity_data" -> "minecraft-zombie_spawn_egg";
            case "minecraft:block_entity_data" -> "minecraft-chest";
            case "minecraft:block_state" -> "minecraft-piston";
            case "minecraft:can_place_on" -> "minecraft-oak_planks";
            case "minecraft:tooltip_display" -> "minecraft-tinted_glass";
            case "minecraft:break_sound" -> "minecraft-note_block";
            case "minecraft:max_stack_size" -> "minecraft-bundle";
            case "minecraft:repair_cost" -> "minecraft-experience_bottle";
            default -> null;
        };
    }

    private static String describe(Object value) {
        if (value == null) {
            return "-";
        }
        String text = Texts.toStringSafe(value);
        return text.length() > 40 ? text.substring(0, 40) + "..." : text;
    }

    private static ItemEditorField toField(ItemEditorSession session, String menuId, ItemEditorFieldSpec spec) {
        if (spec.kind() == ItemEditorField.Kind.NAVIGATE || spec.kind() == ItemEditorField.Kind.LIST) {
            return new ItemEditorField(spec.id(), spec.kind(), spec.labelKey(), "", List.of(), -1, true);
        }
        Object value = session.draftFor(menuId).value(spec.path());
        return new ItemEditorField(
                spec.id(),
                spec.kind(),
                spec.labelKey(),
                describe(value),
                spec.options(),
                -1,
                true);
    }

    private java.util.List<String> setIds() {
        java.util.List<String> ids = new ArrayList<>();
        java.io.File directory = new java.io.File(plugin.getDataFolder(), "sets");
        java.io.File[] files = directory.listFiles(
                (dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (files == null) {
            return ids;
        }
        java.util.Arrays.sort(files);
        for (java.io.File file : files) {
            try {
                Object rawId = emaki.jiuwu.craft.corelib.api.yaml.YamlFiles.load(file).asMap().get("id");
                String id = Texts.normalizeId(Texts.toStringSafe(rawId));
                if (!id.isEmpty()) {
                    ids.add(id);
                }
            } catch (RuntimeException ignored) {
            }
        }
        return ids;
    }
}
