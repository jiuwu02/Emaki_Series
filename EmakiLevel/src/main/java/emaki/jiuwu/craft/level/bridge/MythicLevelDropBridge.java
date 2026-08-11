package emaki.jiuwu.craft.level.bridge;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.level.EmakiLevelPlugin;
import io.lumine.mythic.api.adapters.AbstractPlayer;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.drops.DropMetadata;
import io.lumine.mythic.api.drops.IIntangibleDrop;
import io.lumine.mythic.bukkit.events.MythicDropLoadEvent;

public final class MythicLevelDropBridge implements Listener {

    private final EmakiLevelPlugin plugin;

    public MythicLevelDropBridge(EmakiLevelPlugin plugin) {
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
        String type = value(config, argument, "type", plugin.appConfig().primaryType());
        String amountText = value(config, argument, "amount", "1");
        String formula = value(config, argument, "amount_formula", "%amount% * %drop_amount%");
        String reason = value(config, argument, "reason", "mythic_drop");
        boolean autoUpgrade = booleanValue(config, argument, "auto_upgrade", true);
        boolean silent = booleanValue(config, argument, "silent", false);
        event.register(new EmakiLevelExperienceDrop(plugin, dropName, type, amountText, formula, reason, autoUpgrade, silent));
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

    private static boolean booleanValue(MythicLineConfig config, String argument, String key, boolean fallback) {
        String value = value(config, argument, key, String.valueOf(fallback));
        return Boolean.parseBoolean(value);
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

    private static final class EmakiLevelExperienceDrop implements IIntangibleDrop {

        private final EmakiLevelPlugin plugin;
        private final String dropName;
        private final String type;
        private final String amountText;
        private final String formula;
        private final String reason;
        private final boolean autoUpgrade;
        private final boolean silent;

        private EmakiLevelExperienceDrop(EmakiLevelPlugin plugin,
                String dropName,
                String type,
                String amountText,
                String formula,
                String reason,
                boolean autoUpgrade,
                boolean silent) {
            this.plugin = plugin;
            this.dropName = dropName;
            this.type = Texts.normalizeId(type);
            this.amountText = amountText;
            this.formula = formula;
            this.reason = reason;
            this.autoUpgrade = autoUpgrade;
            this.silent = silent;
        }

        @Override
        public void giveDrop(AbstractPlayer target, DropMetadata metadata, double amount) {
            Player player = resolvePlayer(target);
            if (player == null) {
                return;
            }
            double baseAmount = parseAmount(amountText, metadata, amount);
            Map<String, Object> variables = new LinkedHashMap<>();
            variables.put("amount", baseAmount);
            variables.put("drop_amount", amount);
            variables.put("metadata_amount", metadata == null ? 1F : metadata.getAmount());
            variables.put("generations", metadata == null ? 0 : metadata.getGenerations());
            variables.put("tick", metadata == null ? 0 : metadata.tick());
            variables.put("player", player.getName());
            variables.put("type", type);
            variables.put("drop", dropName);
            double finalAmount = Math.max(0D, ExpressionEngine.evaluate(formula, variables));
            if (finalAmount <= 0D) {
                return;
            }
            plugin.levelService().addExp(player.getUniqueId(), type, finalAmount, reason, autoUpgrade, silent);
        }

        private Player resolvePlayer(AbstractPlayer target) {
            if (target == null || target.getBukkitEntity() == null) {
                return null;
            }
            return target.getBukkitEntity() instanceof Player player ? player : null;
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
