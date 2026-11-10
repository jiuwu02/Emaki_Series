package emaki.jiuwu.craft.item.editor;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.descriptor.CoreActionStageDescriptor;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.editor.EditorBrowserRoot;
import emaki.jiuwu.craft.corelib.editor.EditorField;
import emaki.jiuwu.craft.corelib.editor.EditorFieldSpec;
import emaki.jiuwu.craft.corelib.editor.EditorFeedback;
import emaki.jiuwu.craft.corelib.editor.EditorMenuMeta;
import emaki.jiuwu.craft.corelib.editor.EditorModule;
import emaki.jiuwu.craft.corelib.editor.EditorRenderer;
import emaki.jiuwu.craft.corelib.editor.EditorService;
import emaki.jiuwu.craft.corelib.editor.EditorSession;
import emaki.jiuwu.craft.corelib.editor.EditorValidator;
import emaki.jiuwu.craft.corelib.editor.YamlTextDocument;
import emaki.jiuwu.craft.corelib.gui.GuiClickContext;
import emaki.jiuwu.craft.corelib.gui.GuiItemBuilder;
import emaki.jiuwu.craft.corelib.gui.GuiSlot;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.item.MinecraftItemComponentCatalog;
import emaki.jiuwu.craft.corelib.item.MinecraftServerVersions;
import emaki.jiuwu.craft.corelib.item.ProfileComponentSupport;
import emaki.jiuwu.craft.item.EmakiItemPlugin;
import emaki.jiuwu.craft.item.model.EmakiItemDefinition;
import emaki.jiuwu.craft.item.model.EmakiItemDefinitionParser;

final class ItemEditorModuleDelegate {

    private final ItemEditorModule ownerRef;

    ItemEditorModuleDelegate(ItemEditorModule ownerRef) {
        this.ownerRef = ownerRef;
    }

    void bind(EditorService service) {
        ownerRef.service = service;
    }

    EditorService service() {
        return ownerRef.service;
    }

    String rootCommand() {
        return "emakiitem";
    }

    List<EditorBrowserRoot> roots() {
        return List.of();
    }

    EditorMenuMeta meta(String menuId) {
        return ItemEditorMenus.meta(menuId);
    }

    List<EditorFieldSpec> fields(EditorSession session, String menuId) {
        return ItemEditorMenus.specs(menuId, session);
    }

    String homeMenuId() {
        return ItemEditorMenus.HOME;
    }

    String titleKey(String menuId) {
        return ItemEditorMenus.titleKey(menuId);
    }

    String templateId(String menuId) {
        return ItemEditorMenus.template(menuId);
    }

    EditorValidator documentValidator() {
        return (candidate, docId, source) -> {
            try {
                YamlSection section = YamlFiles.load(candidate.text());
                if (section == null) {
                    return "unparseable_yaml";
                }
                EmakiItemDefinition definition = new EmakiItemDefinitionParser(ownerRef.plugin.getLogger())
                        .parse(section, source.toString());
                if (definition == null) {
                    return "definition_parse_failed";
                }
                if (!docId.equals(definition.id())) {
                    return "id_changed";
                }
            } catch (RuntimeException failure) {
                return "unparseable_yaml";
            }
            return null;
        };
    }

    static String skeleton(String itemId) {
        return """
                id: "%s"

                item:
                  source: "minecraft-stone"
                  components:
                    custom_name: "<white>%s</white>"

                equip_slot: "all"

                update:
                  enabled: false
                  version: 1
                  triggers:
                    join: true
                    held_change: true
                    inventory_click: true
                    inventory_drag: true
                    pickup: true
                    interact: true
                    command: true

                effects: []

                set:
                  id: ""
                  piece: ""

                condition:
                  type: "all_of"
                  required_count: 0
                  invalid_as_failure: true
                  entries: []
                  on_pass:
                    actions: []
                  on_fail:
                    message: ""
                    block_output: false
                    actions: []

                repair:
                  enabled: false
                  materials: []
                  economy:
                    enabled: false
                    restore: "100%%"
                    currencies: []
                  disabled_display:
                    name_prefix: ""
                    lore_append: []
                  on_disabled: []
                  on_repaired: []

                actions: {}
                """.formatted(itemId, itemId);
    }

    Material fieldIcon(EditorField field) {
        String specific = ItemEditorModule.specificIcon(field.id());
        if (specific == null) {
            return null;
        }
        String name = specific.startsWith("minecraft-")
                ? specific.substring("minecraft-".length())
                : specific;
        return Material.matchMaterial(name.toUpperCase(Locale.ROOT));
    }

    String componentDescription(MinecraftItemComponentCatalog.Entry entry) {
        String description = entry.descriptionText();
        String requirement = entry.versionRequirement();
        if (requirement.isBlank()) {
            return description;
        }
        String note = ownerRef.plugin.messageService().message("editor.value.version", Map.of("version", requirement));
        return description.isBlank() ? note : description + "<newline>" + note;
    }

    static boolean matchesFilter(MinecraftItemComponentCatalog.Entry entry, String filter) {
        return Texts.lower(entry.componentId()).contains(filter)
                || Texts.lower(entry.displayNameOrId()).contains(filter);
    }

    static String componentDraftKey(String componentId) {
        return componentId != null && componentId.startsWith("minecraft:")
                ? componentId.substring("minecraft:".length())
                : componentId;
    }

    static EditorField.Kind editorKind(MinecraftItemComponentCatalog.Entry entry) {
        if (entry.nonValued()) {
            return EditorField.Kind.TOGGLE;
        }
        if ("minecraft:rarity".equals(entry.componentId())) {
            return EditorField.Kind.CYCLE;
        }
        String format = Texts.lower(entry.valueFormat());
        if (format.startsWith("boolean")) {
            return EditorField.Kind.TOGGLE;
        }
        if (format.contains("integer") || format.contains("number")) {
            return EditorField.Kind.NUMBER;
        }
        if (ItemEditorModule.LIST_COMPONENTS.contains(entry.componentId())) {
            return EditorField.Kind.LIST;
        }
        return EditorField.Kind.TEXT;
    }

    static String materialOf(EditorSession session) {
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

    static String skinSummary(EditorSession session) {
        Object profile = session.draft().value("item", "components",
                ProfileComponentSupport.PROFILE_COMPONENT_ID);
        String url = ProfileComponentSupport.textureUrlOf(profile);
        if (url != null) {
            return url;
        }
        String value = ProfileComponentSupport.textureValueOf(profile);
        if (value != null) {
            return value.length() > 32 ? value.substring(0, 32) + "..." : value;
        }
        return profile instanceof Map<?, ?> map && map.get("name") != null
                ? Texts.toStringSafe(map.get("name"))
                : "-";
    }

    static Map<String, Object> buildProfile(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return ProfileComponentSupport.profileWithTextureUrl(trimmed);
        }
        if (trimmed.length() > 24 && trimmed.matches("[A-Za-z0-9+/=]+")) {
            return ProfileComponentSupport.profileWithTextureValue(trimmed);
        }
        return ProfileComponentSupport.profileWithPlayerName(trimmed);
    }

    String componentLabel(EditorField field, String componentId) {
        return field.displayName() != null ? field.displayName() : componentId;
    }

    YamlTextDocument draft(EditorSession session) {
        return session.draft(session.current().scope());
    }

    static boolean isTruthy(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = Texts.toStringSafe(value).trim();
        return "true".equalsIgnoreCase(text) || "yes".equalsIgnoreCase(text);
    }

    static Object parseYamlValue(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        try {
            Object parsed = YamlFiles.load("value: " + trimmed).asMap().get("value");
            return parsed == null ? trimmed : parsed;
        } catch (RuntimeException failure) {
            return trimmed;
        }
    }

    static boolean isActionList(String listPath) {
        return listPath != null && listPath.contains("actions");
    }

    static String composeHint(String formatHint, String currentValue) {
        if (formatHint == null || formatHint.isBlank()) {
            return currentValue;
        }
        return formatHint + "\n" + currentValue;
    }

    static boolean isEditableScalar(Object value) {
        return !(value instanceof Map<?, ?>) && !(value instanceof List<?>);
    }

    static Object parseNumber(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException ignored) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignoredAgain) {
                return text;
            }
        }
    }

}
