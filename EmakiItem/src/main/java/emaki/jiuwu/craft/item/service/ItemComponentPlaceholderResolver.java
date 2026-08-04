package emaki.jiuwu.craft.item.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.placeholder.PlaceholderResolver;
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class ItemComponentPlaceholderResolver implements PlaceholderResolver {

    private static final Pattern PARAMETERIZED = Pattern.compile("%(held_component_has|item_component_has|held_component_value|item_component_value|held_component_json|item_component_json):([^%]+)%");
    private static final Pattern SIMPLE = Pattern.compile("%(held_components_raw|item_components_raw|held_components|item_components)%");

    private final ItemComponentInspector inspector;

    public ItemComponentPlaceholderResolver(ItemComponentInspector inspector) {
        this.inspector = inspector;
    }

    @Override
    public String resolve(ActionContext context, String text) {
        if (context == null || Texts.isBlank(text)) {
            return text;
        }
        ItemStack itemStack = itemStack(context);
        String resolved = replaceParameterized(itemStack, text);
        return replaceSimple(itemStack, resolved);
    }

    private String replaceParameterized(ItemStack itemStack, String text) {
        Matcher matcher = PARAMETERIZED.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String type = matcher.group(1);
            String componentId = matcher.group(2);
            String replacement = switch (type) {
                case "held_component_has", "item_component_has" -> inspector.contains(itemStack, componentId) ? "1" : "0";
                case "held_component_value", "item_component_value" -> inspector.value(itemStack, componentId);
                case "held_component_json", "item_component_json" -> inspector.prettyJson(itemStack, componentId);
                default -> matcher.group(0);
            };
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String replaceSimple(ItemStack itemStack, String text) {
        Matcher matcher = SIMPLE.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String type = matcher.group(1);
            String replacement = switch (type) {
                case "held_components", "item_components" -> inspector.idList(itemStack);
                case "held_components_raw", "item_components_raw" -> inspector.raw(itemStack);
                default -> matcher.group(0);
            };
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private ItemStack itemStack(ActionContext context) {
        Object attribute = context.attribute("item_stack");
        if (attribute instanceof ItemStack stack) {
            return stack;
        }
        Player player = context.player();
        return player == null ? null : player.getInventory().getItemInMainHand();
    }
}
