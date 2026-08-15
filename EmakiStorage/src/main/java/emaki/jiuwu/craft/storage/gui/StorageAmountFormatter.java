package emaki.jiuwu.craft.storage.gui;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

import emaki.jiuwu.craft.storage.config.AppConfig;

public final class StorageAmountFormatter {

    private static final long[] UNIT_THRESHOLDS = {
        1_000L,
        1_000_000L,
        1_000_000_000L,
        1_000_000_000_000L,
        1_000_000_000_000_000L,
        1_000_000_000_000_000_000L
    };

    private volatile AppConfig.DisplayConfig config;

    public StorageAmountFormatter(AppConfig.DisplayConfig config) {
        this.config = config == null ? AppConfig.DisplayConfig.defaults() : config;
    }

    public void reconfigure(AppConfig.DisplayConfig config) {
        if (config != null) {
            this.config = config;
        }
    }

    public int displayAmount(long amount, long stackLimit) {
        AppConfig.DisplayConfig active = config;
        int scale = clampScale(active.percentScale());
        if (active.amountMode() == AppConfig.AmountMode.ONE) {
            return 1;
        }

        if (stackLimit >= Long.MAX_VALUE || stackLimit <= 0L) {
            return 1;
        }
        if (amount <= 0L) {
            return 1;
        }
        if (amount >= stackLimit) {
            return scale;
        }
        long scaled = Math.round((double) amount / (double) stackLimit * scale);
        int clamped = (int) Math.max(1L, Math.min(scale - 1L, scaled));
        return clamped;
    }

    public double occupancyPercent(long amount, long stackLimit) {
        if (stackLimit >= Long.MAX_VALUE || stackLimit <= 0L || amount <= 0L) {
            return 0.0D;
        }
        return Math.min(100.0D, (double) amount / (double) stackLimit * 100.0D);
    }

    public String compact(long amount) {
        AppConfig.DisplayConfig active = config;
        List<String> units = active.compactUnits();
        if (units.isEmpty() || amount < UNIT_THRESHOLDS[0]) {
            return String.valueOf(amount);
        }
        int index = -1;
        for (int candidate = 0; candidate < UNIT_THRESHOLDS.length && candidate < units.size(); candidate++) {
            if (amount >= UNIT_THRESHOLDS[candidate]) {
                index = candidate;
            }
        }
        if (index < 0) {
            return String.valueOf(amount);
        }
        BigDecimal value = BigDecimal.valueOf(amount)
                .divide(BigDecimal.valueOf(UNIT_THRESHOLDS[index]), Math.max(0, active.compactDecimals()),
                        RoundingMode.DOWN)
                .stripTrailingZeros();
        return value.toPlainString() + units.get(index);
    }

    public String exact(long amount) {
        DecimalFormat format = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.ROOT));
        return format.format(amount);
    }

    public String compactLimit(long stackLimit) {
        if (stackLimit >= Long.MAX_VALUE || stackLimit <= 0L) {
            return "";
        }
        return compact(stackLimit);
    }

    public String percentText(long amount, long stackLimit) {
        double percent = occupancyPercent(amount, stackLimit);
        if (percent <= 0.0D) {
            return "0";
        }
        BigDecimal value = BigDecimal.valueOf(percent)
                .setScale(1, RoundingMode.DOWN)
                .stripTrailingZeros();
        return value.toPlainString();
    }

    public boolean percentMeaningful(long stackLimit) {
        return config.amountMode() == AppConfig.AmountMode.PERCENT
                && stackLimit > 0L
                && stackLimit < Long.MAX_VALUE;
    }

    public boolean showExactAmount() {
        return config.showExactAmount();
    }

    public AppConfig.LorePosition lorePosition() {
        return config.lorePosition();
    }

    public int clampScale(int configured) {
        return Math.max(1, Math.min(99, configured));
    }
}
