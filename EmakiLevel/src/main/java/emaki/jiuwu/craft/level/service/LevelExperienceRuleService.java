package emaki.jiuwu.craft.level.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.level.config.AppConfig;

public final class LevelExperienceRuleService {

    private final Map<String, Map<UUID, Map<String, Double>>> dailyGains = new ConcurrentHashMap<>();
    private AppConfig config = AppConfig.defaults();
    private ZoneId zoneId = ZoneId.systemDefault();

    public void config(AppConfig config) {
        if (config != null) {
            this.config = config;
        }
    }

    public void zoneId(ZoneId zoneId) {
        if (zoneId != null) {
            this.zoneId = zoneId;
        }
    }

    /** Computes multiplier and remaining daily quota without recording a gain. */
    public synchronized LevelExperienceAdjustment preview(UUID uuid,
            String typeId,
            double amount,
            String reason) {
        if (uuid == null || Texts.isBlank(typeId) || !Double.isFinite(amount) || amount <= 0D) {
            return LevelExperienceAdjustment.invalid(amount);
        }
        String normalizedType = Texts.normalizeId(typeId);
        String normalizedReason = Texts.normalizeId(reason);
        double multiplier = resolveMultiplier(normalizedType, normalizedReason);
        double multiplied = Math.max(0D, amount * multiplier);
        if (multiplied <= 0D) {
            return LevelExperienceAdjustment.reached(amount, multiplier, multiplied);
        }
        String today = LocalDate.now(zoneId).toString();
        clearExpired(today);
        double dailyLimit = resolveDailyLimit(normalizedType);
        double gained = currentGain(today, uuid, normalizedType);
        double actual = cap(multiplied, dailyLimit, gained);
        return actual <= 0D
                ? LevelExperienceAdjustment.limitReached(amount, multiplier, multiplied, dailyLimit, gained, 0D)
                : LevelExperienceAdjustment.applied(amount, multiplier, multiplied, dailyLimit, gained, actual);
    }

    /**
     * Applies the current daily quota to an event-approved amount and records only the amount that will
     * actually be written. Call this after {@code PlayerExpGainEvent} has completed.
     */
    public synchronized LevelExperienceAdjustment record(UUID uuid,
            String typeId,
            double approvedAmount,
            LevelExperienceAdjustment preview) {
        if (uuid == null
                || Texts.isBlank(typeId)
                || !Double.isFinite(approvedAmount)
                || approvedAmount <= 0D
                || preview == null) {
            return LevelExperienceAdjustment.invalid(preview == null ? approvedAmount : preview.originalAmount());
        }
        String normalizedType = Texts.normalizeId(typeId);
        String today = LocalDate.now(zoneId).toString();
        clearExpired(today);
        double dailyLimit = resolveDailyLimit(normalizedType);
        double gained = currentGain(today, uuid, normalizedType);
        double actual = cap(approvedAmount, dailyLimit, gained);
        if (actual <= 0D) {
            return LevelExperienceAdjustment.limitReached(
                    preview.originalAmount(),
                    preview.multiplier(),
                    preview.multipliedAmount(),
                    dailyLimit,
                    gained,
                    0D);
        }
        dailyGains.computeIfAbsent(today, ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(uuid, ignored -> new ConcurrentHashMap<>())
                .merge(normalizedType, actual, Double::sum);
        return LevelExperienceAdjustment.applied(
                preview.originalAmount(),
                preview.multiplier(),
                preview.multipliedAmount(),
                dailyLimit,
                gained,
                actual);
    }

    public synchronized void clearExpired() {
        clearExpired(LocalDate.now(zoneId).toString());
    }

    private void clearExpired(String today) {
        dailyGains.keySet().removeIf(day -> !day.equals(today));
    }

    private double resolveMultiplier(String typeId, String reason) {
        AppConfig.ExperienceMultiplierConfig multiplierConfig = config.experienceMultipliers();
        if (multiplierConfig == null || !multiplierConfig.enabled()) {
            return 1D;
        }
        double multiplier = multiplierConfig.global();
        Double typeMultiplier = multiplierConfig.types().get(typeId);
        if (typeMultiplier != null) {
            multiplier *= typeMultiplier;
        }
        Double reasonMultiplier = multiplierConfig.reasons().get(reason);
        if (reasonMultiplier != null) {
            multiplier *= reasonMultiplier;
        }
        return Math.max(0D, multiplier);
    }

    private double resolveDailyLimit(String typeId) {
        AppConfig.DailyLimitConfig dailyLimitConfig = config.dailyLimits();
        if (dailyLimitConfig == null || !dailyLimitConfig.enabled()) {
            return -1D;
        }
        Double value = dailyLimitConfig.types().get(typeId);
        return value == null ? dailyLimitConfig.defaultLimit() : value;
    }

    private double currentGain(String day, UUID uuid, String typeId) {
        Map<UUID, Map<String, Double>> players = dailyGains.get(day);
        if (players == null) {
            return 0D;
        }
        Map<String, Double> types = players.get(uuid);
        if (types == null) {
            return 0D;
        }
        return types.getOrDefault(typeId, 0D);
    }

    private static double cap(double amount, double dailyLimit, double gained) {
        if (dailyLimit < 0D) {
            return amount;
        }
        return Math.min(amount, Math.max(0D, dailyLimit - gained));
    }

    public record LevelExperienceAdjustment(double originalAmount,
            double multiplier,
            double multipliedAmount,
            double dailyLimit,
            double gainedToday,
            double actualAmount,
            String reason) {

        static LevelExperienceAdjustment invalid(double originalAmount) {
            return new LevelExperienceAdjustment(originalAmount, 1D, 0D, -1D, 0D, 0D, "invalid_amount");
        }

        static LevelExperienceAdjustment reached(double originalAmount,
                double multiplier,
                double multipliedAmount) {
            return new LevelExperienceAdjustment(
                    originalAmount, multiplier, multipliedAmount, -1D, 0D, 0D, "invalid_amount");
        }

        static LevelExperienceAdjustment applied(double originalAmount,
                double multiplier,
                double multipliedAmount,
                double dailyLimit,
                double gainedToday,
                double actualAmount) {
            return new LevelExperienceAdjustment(
                    originalAmount, multiplier, multipliedAmount, dailyLimit, gainedToday, actualAmount, "success");
        }

        static LevelExperienceAdjustment limitReached(double originalAmount,
                double multiplier,
                double multipliedAmount,
                double dailyLimit,
                double gainedToday,
                double actualAmount) {
            return new LevelExperienceAdjustment(
                    originalAmount,
                    multiplier,
                    multipliedAmount,
                    dailyLimit,
                    gainedToday,
                    actualAmount,
                    "daily_cap_reached");
        }

        public Map<String, Object> data() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("original_amount", originalAmount);
            data.put("multiplier", multiplier);
            data.put("multiplied_amount", multipliedAmount);
            data.put("daily_limit", dailyLimit);
            data.put("gained_today", gainedToday);
            data.put("actual_amount", actualAmount);
            data.put("reason", reason);
            return Map.copyOf(data);
        }
    }
}
