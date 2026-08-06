package emaki.jiuwu.craft.station.config;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.station.recipe.RecipeCost;

/**
 * Reads {@code queue_costs.yml} into a {@link QueueCostConfig}.
 *
 * <p>Every problem degrades to "this tier does not exist" rather than to a cheaper price. A tier with an
 * unparseable range, no price at all, or an unrecognised currency is skipped with a warning, and a fallback
 * without its mandatory {@code max_amount} guard rail is dropped entirely. The result is that a broken file
 * refuses sales instead of holding a clearance event.
 */
public final class QueueCostLoader {

    private QueueCostLoader() {
    }

    /**
     * Loads the price table.
     *
     * @param file              the file to read
     * @param logger            where to report problems
     * @param reportMissingFile whether an absent or empty file should be logged; {@code false} during initial
     *                          startup, when the bundled default has not been released yet and an absent file
     *                          is expected
     * @return the parsed table, or {@link QueueCostConfig#empty()} when nothing usable was found
     */
    public static QueueCostConfig load(File file, Logger logger, boolean reportMissingFile) {
        YamlSection section = file == null ? null : YamlFiles.load(file);
        if (section == null || section.isEmpty()) {
            if (reportMissingFile && logger != null) {
                logger.warning("[station] " + (file == null ? "queue_costs.yml" : file.getName())
                        + " is missing or empty; paid queue slots are disabled until it is provided.");
            }
            return QueueCostConfig.empty();
        }
        List<QueueCostConfig.Tier> tiers = new ArrayList<>();
        for (Map<?, ?> raw : section.getMapList("tiers")) {
            QueueCostConfig.Tier tier = parseTier(raw, logger);
            if (tier != null) {
                tiers.add(tier);
            }
        }
        QueueCostConfig.Fallback fallback = parseFallback(section.getSection("fallback"), logger);
        QueueCostConfig.Batch batch = parseBatch(section.getSection("batch"));
        return new QueueCostConfig(tiers, fallback, batch);
    }

    private static QueueCostConfig.Tier parseTier(Map<?, ?> raw, Logger logger) {
        if (raw == null) {
            return null;
        }
        String range = ConfigNodes.string(raw, "count_range", null);
        int[] bounds = parseRange(range);
        if (bounds == null) {
            warn(logger, "Skipping queue cost tier with invalid count_range: " + range);
            return null;
        }
        QueueCostConfig.CurrencyCost currency = parseCurrency(ConfigNodes.get(raw, "currency"), logger);
        QueueCostConfig.ItemCost item = parseItem(ConfigNodes.get(raw, "item"));
        if (currency == null && item == null) {
            warn(logger, "Skipping queue cost tier " + range + " with no usable price.");
            return null;
        }
        return new QueueCostConfig.Tier(bounds[0], bounds[1], currency, item);
    }

    private static QueueCostConfig.Fallback parseFallback(YamlSection section, Logger logger) {
        if (section == null) {
            return null;
        }
        QueueCostConfig.CurrencyCost currency = parseCurrency(section.get("currency"), logger);
        QueueCostConfig.ItemCost item = parseItem(section.get("item"));
        if (currency == null && item == null) {
            return null;
        }
        Double maxAmount = section.getDouble("max_amount", null);
        if (maxAmount == null || maxAmount <= 0.0D) {
            // The guard rail is mandatory: an exponential formula loses double precision and can reach
            // Infinity at high counts, so an uncapped fallback is refused rather than trusted.
            warn(logger, "queue_costs.yml fallback requires a positive max_amount guard rail;"
                    + " paid queue slots beyond the defined tiers are disabled.");
            return null;
        }
        return new QueueCostConfig.Fallback(currency, item, maxAmount);
    }

    private static QueueCostConfig.Batch parseBatch(YamlSection section) {
        if (section == null) {
            return QueueCostConfig.Batch.defaults();
        }
        List<Integer> options = new ArrayList<>();
        for (Object option : section.getList("options", List.of())) {
            if (option instanceof Number number && number.intValue() > 0) {
                options.add(number.intValue());
            }
        }
        Boolean enabled = section.getBoolean("enabled", Boolean.TRUE);
        return new QueueCostConfig.Batch(enabled == null || enabled,
                options.isEmpty() ? QueueCostConfig.Batch.defaults().options() : options);
    }

    /**
     * Reads a currency price, resolving the configured token to a provider id.
     *
     * <p>An unrecognised token yields no price rather than silently falling back to Vault: charging a wallet
     * the administrator did not name is worse than refusing the sale.
     *
     * @param raw    the raw {@code currency} node
     * @param logger where to report an unrecognised token
     * @return the parsed price, or {@code null}
     */
    private static QueueCostConfig.CurrencyCost parseCurrency(Object raw, Logger logger) {
        if (raw == null) {
            return null;
        }
        String token = ConfigNodes.string(raw, "type", "vault");
        RecipeCost resolved = RecipeCost.fromToken(token, 1L);
        if (resolved == null) {
            warn(logger, "Unrecognised queue cost currency type: " + token);
            return null;
        }
        String providerId = resolved.providerId();
        Object amount = ConfigNodes.get(raw, "amount");
        if (amount instanceof Number number) {
            return number.doubleValue() <= 0.0D
                    ? null
                    : new QueueCostConfig.CurrencyCost(providerId, number.doubleValue(), null);
        }
        if (amount instanceof String expression && !expression.isBlank()) {
            return new QueueCostConfig.CurrencyCost(providerId, 0.0D,
                    expression.replace("%count%", "count"));
        }
        return null;
    }

    private static QueueCostConfig.ItemCost parseItem(Object raw) {
        if (raw == null) {
            return null;
        }
        String source = ConfigNodes.string(raw, "source", null);
        if (source == null || source.isBlank()) {
            return null;
        }
        Object amount = ConfigNodes.get(raw, "amount");
        int count = amount instanceof Number number ? number.intValue() : 1;
        return count <= 0 ? null : new QueueCostConfig.ItemCost(source.trim(), count);
    }

    /**
     * Parses {@code "10-19"} or a bare {@code "10"} into inclusive bounds.
     *
     * @param range the configured range
     * @return the bounds as {@code {min, max}}, or {@code null} when unusable
     */
    private static int[] parseRange(String range) {
        if (range == null || range.isBlank()) {
            return null;
        }
        String normalized = range.trim().toLowerCase(Locale.ROOT);
        int dash = normalized.indexOf('-');
        try {
            if (dash < 0) {
                int single = Integer.parseInt(normalized);
                return single <= 0 ? null : new int[] {single, single};
            }
            int min = Integer.parseInt(normalized.substring(0, dash).trim());
            int max = Integer.parseInt(normalized.substring(dash + 1).trim());
            if (min <= 0 || max < min) {
                return null;
            }
            return new int[] {min, max};
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static void warn(Logger logger, String message) {
        if (logger != null) {
            logger.warning("[station] " + message);
        }
    }
}
