package emaki.jiuwu.craft.corelib.assembly;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

final class SpigotItemComponentNameWriter {

    private static final Gson GSON = new Gson();
    private static final String CUSTOM_NAME_COMPONENT = "minecraft:custom_name";
    private static final Map<TextDecoration, String> DECORATION_KEYS = Map.of(
            TextDecoration.BOLD, "bold",
            TextDecoration.ITALIC, "italic",
            TextDecoration.UNDERLINED, "underlined",
            TextDecoration.STRIKETHROUGH, "strikethrough",
            TextDecoration.OBFUSCATED, "obfuscated"
    );

    private SpigotItemComponentNameWriter() {
    }

    static boolean writeCustomName(ItemStack itemStack, Component customName) {
        if (itemStack == null || itemStack.getType().isAir() || customName == null) {
            return false;
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return false;
        }
        try {
            String components = itemMeta.getAsComponentString();
            String patchedComponents = putCustomNameComponent(components, toJson(customName));
            ItemStack parsed = Bukkit.getItemFactory().createItemStack(itemTypeKey(itemStack) + patchedComponents);
            if (parsed == null || parsed.getType() != itemStack.getType()) {
                return false;
            }
            ItemMeta parsedMeta = parsed.getItemMeta();
            if (parsedMeta == null) {
                return false;
            }
            itemStack.setItemMeta(parsedMeta);
            return true;
        } catch (RuntimeException | LinkageError _) {
            return false;
        }
    }

    static String putCustomNameComponent(String components, String customNameJson) {
        String normalizedComponents = Texts.toStringSafe(components).trim();
        if (normalizedComponents.isEmpty() || "[]".equals(normalizedComponents)) {
            return "[" + CUSTOM_NAME_COMPONENT + "=" + customNameJson + "]";
        }
        if (!normalizedComponents.startsWith("[") || !normalizedComponents.endsWith("]")) {
            return "[" + CUSTOM_NAME_COMPONENT + "=" + customNameJson + "]";
        }

        String body = normalizedComponents.substring(1, normalizedComponents.length() - 1).trim();
        if (body.isEmpty()) {
            return "[" + CUSTOM_NAME_COMPONENT + "=" + customNameJson + "]";
        }

        List<String> entries = splitTopLevelEntries(body);
        boolean replaced = false;
        List<String> patched = new ArrayList<>(entries.size() + 1);
        for (String entry : entries) {
            if (isCustomNameEntry(entry)) {
                patched.add(CUSTOM_NAME_COMPONENT + "=" + customNameJson);
                replaced = true;
            } else {
                patched.add(entry);
            }
        }
        if (!replaced) {
            patched.add(CUSTOM_NAME_COMPONENT + "=" + customNameJson);
        }
        return "[" + String.join(",", patched) + "]";
    }

    private static List<String> splitTopLevelEntries(String body) {
        List<String> entries = new ArrayList<>();
        int start = 0;
        int depth = 0;
        char quote = 0;
        boolean escaped = false;
        for (int index = 0; index < body.length(); index++) {
            char current = body.charAt(index);
            if (quote != 0) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == quote) {
                    quote = 0;
                }
                continue;
            }
            if (current == '\'' || current == '"') {
                quote = current;
                continue;
            }
            if (current == '[' || current == '{' || current == '(') {
                depth++;
                continue;
            }
            if (current == ']' || current == '}' || current == ')') {
                depth = Math.max(0, depth - 1);
                continue;
            }
            if (current == ',' && depth == 0) {
                addEntry(entries, body.substring(start, index));
                start = index + 1;
            }
        }
        addEntry(entries, body.substring(start));
        return entries;
    }

    private static void addEntry(List<String> entries, String raw) {
        String entry = Texts.toStringSafe(raw).trim();
        if (!entry.isEmpty()) {
            entries.add(entry);
        }
    }

    private static boolean isCustomNameEntry(String entry) {
        int assignmentIndex = findTopLevelAssignment(entry);
        if (assignmentIndex <= 0) {
            return false;
        }
        String key = entry.substring(0, assignmentIndex).trim();
        return "custom_name".equals(key) || CUSTOM_NAME_COMPONENT.equals(key);
    }

    private static int findTopLevelAssignment(String entry) {
        int depth = 0;
        char quote = 0;
        boolean escaped = false;
        for (int index = 0; index < entry.length(); index++) {
            char current = entry.charAt(index);
            if (quote != 0) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == quote) {
                    quote = 0;
                }
                continue;
            }
            if (current == '\'' || current == '"') {
                quote = current;
                continue;
            }
            if (current == '[' || current == '{' || current == '(') {
                depth++;
                continue;
            }
            if (current == ']' || current == '}' || current == ')') {
                depth = Math.max(0, depth - 1);
                continue;
            }
            if (current == '=' && depth == 0) {
                return index;
            }
        }
        return -1;
    }

    private static String itemTypeKey(ItemStack itemStack) {
        NamespacedKey key = itemStack.getType().getKey();
        return key.getNamespace() + ":" + key.getKey();
    }

    private static String toJson(Component component) {
        return GSON.toJson(toJsonObject(component == null ? Component.empty() : component));
    }

    private static JsonObject toJsonObject(Component component) {
        JsonObject object = new JsonObject();
        boolean includeChildren = true;
        if (component instanceof TextComponent textComponent) {
            object.addProperty("text", textComponent.content());
        } else if (component instanceof TranslatableComponent translatableComponent) {
            object.addProperty("translate", translatableComponent.key());
            if (Texts.isNotBlank(translatableComponent.fallback())) {
                object.addProperty("fallback", translatableComponent.fallback());
            }
            if (!translatableComponent.args().isEmpty()) {
                JsonArray with = new JsonArray();
                for (Component argument : translatableComponent.args()) {
                    with.add(toJsonObject(argument));
                }
                object.add("with", with);
            }
        } else {
            object.addProperty("text", MiniMessages.plain(component));
            includeChildren = false;
        }
        appendStyle(object, component.style());
        if (includeChildren && !component.children().isEmpty()) {
            JsonArray extra = new JsonArray();
            for (Component child : component.children()) {
                extra.add(toJsonObject(child));
            }
            object.add("extra", extra);
        }
        return object;
    }

    private static void appendStyle(JsonObject object, Style style) {
        if (style == null || style.isEmpty()) {
            return;
        }
        TextColor color = style.color();
        if (color != null) {
            object.addProperty("color", colorName(color));
        }
        Key font = style.font();
        if (font != null) {
            object.addProperty("font", font.asString());
        }
        for (Map.Entry<TextDecoration, String> entry : DECORATION_KEYS.entrySet()) {
            TextDecoration.State state = style.decoration(entry.getKey());
            if (state == TextDecoration.State.TRUE) {
                object.addProperty(entry.getValue(), true);
            } else if (state == TextDecoration.State.FALSE) {
                object.addProperty(entry.getValue(), false);
            }
        }
    }

    private static String colorName(TextColor color) {
        if (color instanceof NamedTextColor namedColor) {
            String named = NamedTextColor.NAMES.key(namedColor);
            if (Texts.isNotBlank(named)) {
                return named;
            }
        }
        return color.asHexString();
    }
}
