package emaki.jiuwu.craft.codex.advancement;

import org.bukkit.inventory.ItemStack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import emaki.jiuwu.craft.codex.advancement.model.AdvancementDefinition;
import emaki.jiuwu.craft.codex.advancement.model.AdvancementPage;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

/**
 * Builds the vanilla advancement JSON string consumed by
 * {@code Bukkit.getUnsafe().loadAdvancement(key, json)}.
 *
 * <p>Uses the 1.20.5+/1.21 schema: {@code display.icon.id} (not {@code item}),
 * a {@code minecraft:impossible} manual criterion, and text components serialized
 * from MiniMessage via {@link GsonComponentSerializer}. Root nodes carry a
 * background and omit {@code parent}; children reference their parent's full key.
 */
@SuppressWarnings("deprecation") // Material#getKey is soft-deprecated but the stable id source on Spigot
public final class AdvancementJsonBuilder {

    private final ItemSourceService itemSourceService;

    public AdvancementJsonBuilder(ItemSourceService itemSourceService) {
        this.itemSourceService = itemSourceService;
    }

    /**
     * Builds the JSON for one advancement node.
     *
     * @param page       the owning page (supplies background and key namespace)
     * @param definition the node definition
     * @param parentKey  the full parent advancement key, or {@code null} for the root
     * @return the advancement JSON string
     */
    public String build(AdvancementPage page, AdvancementDefinition definition, String parentKey) {
        JsonObject root = new JsonObject();

        JsonObject display = new JsonObject();
        display.add("icon", buildIcon(definition.icon()));
        display.add("title", textComponent(definition.title()));
        display.add("description", textComponent(definition.description()));        display.addProperty("frame", definition.frame().token());
        display.addProperty("show_toast", definition.showToast());
        display.addProperty("announce_to_chat", definition.announce());
        display.addProperty("hidden", definition.hidden());
        if (definition.isRoot() && Texts.isNotBlank(page.background())) {
            display.addProperty("background", page.background());
        }
        root.add("display", display);

        if (!definition.isRoot() && Texts.isNotBlank(parentKey)) {
            root.addProperty("parent", parentKey);
        }

        JsonObject criteria = new JsonObject();
        JsonObject manual = new JsonObject();
        manual.addProperty("trigger", "minecraft:impossible");
        criteria.add(AdvancementDefinition.CRITERION, manual);
        root.add("criteria", criteria);

        JsonArray requirements = new JsonArray();
        JsonArray requirementGroup = new JsonArray();
        requirementGroup.add(AdvancementDefinition.CRITERION);
        requirements.add(requirementGroup);
        root.add("requirements", requirements);

        return root.toString();
    }

    private JsonObject buildIcon(String iconShorthand) {
        JsonObject icon = new JsonObject();
        String materialId = resolveIconId(iconShorthand);
        icon.addProperty("id", materialId);
        return icon;
    }

    private String resolveIconId(String iconShorthand) {
        if (Texts.isBlank(iconShorthand)) {
            return "minecraft:book";
        }
        // Try to resolve the shorthand to a real ItemStack so custom items still map to
        // a vanilla material id for the advancement icon (custom textures come from resource packs).
        ItemSource source = ItemSourceUtil.parse(iconShorthand);
        if (source != null && itemSourceService != null) {
            ItemStack stack = itemSourceService.createItem(source, 1);
            if (stack != null && !stack.getType().isAir()) {
                return stack.getType().getKey().toString();
            }
        }
        // Fall back to interpreting the shorthand's identifier as a vanilla material name.
        String identifier = source == null ? iconShorthand : source.getIdentifier();
        String normalized = identifier.contains(":") ? identifier : "minecraft:" + identifier;
        return normalized;
    }

    private JsonElement textComponent(String miniMessage) {
        Component component = MiniMessages.parse(miniMessage == null ? "" : miniMessage);
        String json = GsonComponentSerializer.gson().serialize(component);
        return JsonParser.parseString(json);
    }
}
