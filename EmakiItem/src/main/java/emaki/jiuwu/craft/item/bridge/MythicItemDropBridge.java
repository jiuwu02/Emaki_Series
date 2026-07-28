package emaki.jiuwu.craft.item.bridge;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.bukkit.inventory.ItemStack;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.item.EmakiItemPlugin;
import io.lumine.mythic.api.adapters.AbstractItemStack;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.drops.DropMetadata;
import io.lumine.mythic.api.drops.IItemDrop;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.events.MythicDropLoadEvent;

public final class MythicItemDropBridge implements Listener {

    private final EmakiItemPlugin plugin;

    public MythicItemDropBridge(EmakiItemPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDropLoad(MythicDropLoadEvent event) {
        if (!plugin.appConfig().mythicEnabled() || !plugin.appConfig().mythicDropsEnabled()) {
            return;
        }
        Set<String> names = plugin.appConfig().mythicDropNames().stream()
                .map(Texts::normalizeId)
                .collect(Collectors.toUnmodifiableSet());
        String dropName = Texts.normalizeId(event.getDropName());
        if (!names.contains(dropName)) {
            return;
        }
        MythicLineConfig config = event.getConfig();
        String argument = event.getArgument();
        String id = value(config, argument, "id", "");
        if (Texts.isBlank(id)) {
            plugin.getLogger().warning("MythicMobs drop '" + dropName + "' is missing the required 'id' argument.");
            return;
        }
        String amountText = value(config, argument, "amount", "1");
        String formula = value(config, argument, "amount_formula", "%amount% * %drop_amount%");
        event.register(new EmakiItemDrop(plugin, dropName, id, amountText, formula));
    }

    private static String value(MythicLineConfig config, String argument, String key, String fallback) {
        if (config != null) {
            String value = config.getString(key, null);
            if (Texts.isNotBlank(value)) {
                return value;
            }
        }
        Map<String, String> arguments = parseArgument(argument);
        return arguments.getOrDefault(key, fallback);
    }

    private static Map<String, String> parseArgument(String argument) {
        Map<String, String> result = new LinkedHashMap<>();
        if (Texts.isBlank(argument)) {
            return result;
        }
        String[] parts = argument.split("[;,]");
        for (String part : parts) {
            int index = part.indexOf('=');
            if (index < 0) {
                continue;
            }
            String key = Texts.normalizeId(part.substring(0, index));
            String value = part.substring(index + 1).trim();
            if (Texts.isNotBlank(key)) {
                result.put(key, stripQuotes(value));
            }
        }
        return result;
    }

    private static String stripQuotes(String value) {
        String text = Texts.toStringSafe(value).trim();
        if (text.length() >= 2 && ((text.startsWith("\"") && text.endsWith("\"")) || (text.startsWith("'") && text.endsWith("'")))) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }

    private static final class EmakiItemDrop implements IItemDrop {

        private final EmakiItemPlugin plugin;
        private final String dropName;
        private final String id;
        private final String amountText;
        private final String formula;

        private EmakiItemDrop(EmakiItemPlugin plugin,
                String dropName,
                String id,
                String amountText,
                String formula) {
            this.plugin = plugin;
            this.dropName = dropName;
            this.id = Texts.normalizeId(id);
            this.amountText = amountText;
            this.formula = formula;
        }

        @Override
        public AbstractItemStack getDrop(DropMetadata metadata, double amount) {
            double baseAmount = parseAmount(amountText, metadata, amount);
            Map<String, Object> variables = new LinkedHashMap<>();
            variables.put("amount", baseAmount);
            variables.put("drop_amount", amount);
            variables.put("metadata_amount", metadata == null ? 1F : metadata.getAmount());
            variables.put("generations", metadata == null ? 0 : metadata.getGenerations());
            variables.put("tick", metadata == null ? 0 : metadata.tick());
            variables.put("id", id);
            variables.put("drop", dropName);
            int finalAmount = (int) Math.floor(ExpressionEngine.evaluate(formula, variables));
            if (finalAmount <= 0) {
                return null;
            }
            ItemStack itemStack = plugin.itemFactory() == null
                    ? null
                    : plugin.itemFactory().create(id, finalAmount);
            if (itemStack == null) {
                plugin.getLogger().warning("MythicMobs drop '" + dropName
                        + "' could not resolve EmakiItem id '" + id + "'.");
                return null;
            }
            itemStack.setAmount(Math.max(1, Math.min(finalAmount, itemStack.getMaxStackSize())));
            return BukkitAdapter.adapt(itemStack);
        }

        private double parseAmount(String amountText, DropMetadata metadata, double dropAmount) {
            try {
                return Double.parseDouble(Texts.toStringSafe(amountText));
            } catch (NumberFormatException ignored) {
                Map<String, Object> variables = new LinkedHashMap<>();
                variables.put("drop_amount", dropAmount);
                variables.put("metadata_amount", metadata == null ? 1F : metadata.getAmount());
                variables.put("generations", metadata == null ? 0 : metadata.getGenerations());
                variables.put("tick", metadata == null ? 0 : metadata.tick());
                return ExpressionEngine.evaluate(amountText, variables);
            }
        }
    }
}
