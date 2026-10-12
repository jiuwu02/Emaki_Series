package emaki.jiuwu.craft.structure.config;

import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AppConfigParser {

    private AppConfigParser() {
    }

    public static AppConfig parse(YamlSection section) {
        if (section == null) {
            return AppConfig.defaults();
        }
        AppConfig defaults = AppConfig.defaults();
        List<ConfigIssue> issues = new ArrayList<>();
        String language = readString(section, "language", defaults.language());
        String version = readString(section, "version", defaults.version());
        List<AppConfig.ReplaceRule> replaceRules = parseReplaceRules(section, defaults.replaceRules(), issues);
        return new AppConfig(language, version,
                parseGenerate(section.getSection("generate"), defaults.generate(), issues),
                parseCache(section.getSection("cache"), defaults.cache(), issues),
                parseSelection(section.getSection("selection"), defaults.selection(), issues),
                parseTemplate(section.getSection("template"), defaults.template(), issues),
                parseSpawn(section.getSection("spawn"), defaults.spawn(), issues),
                parseDetection(section.getSection("detection"), defaults.detection(), issues),
                parseCooldown(section.getSection("cooldown"), defaults.cooldown(), issues),
                replaceRules,
                parseRollback(section.getSection("rollback"), defaults.rollback(), issues),
                parseFurniture(section.getSection("furniture"), defaults.furniture(), issues),
                List.copyOf(issues));
    }

    private static List<AppConfig.ReplaceRule> parseReplaceRules(YamlSection section,
            List<AppConfig.ReplaceRule> fallback, List<ConfigIssue> issues) {
        if (section == null) {
            return fallback;
        }
        Object raw = section.get("replace_rules");
        if (raw == null) {
            return fallback;
        }
        if (!(raw instanceof List<?> entries)) {
            issues.add(invalid(ConfigIssue.Kind.REPLACE_RULE, "replace_rules", raw, "list"));
            return List.of();
        }
        List<AppConfig.ReplaceRule> rules = new ArrayList<>();
        for (int index = 0; index < entries.size(); index++) {
            String path = "replace_rules[" + index + "]";
            Object entry = entries.get(index);
            if (!(entry instanceof Map<?, ?> map)) {
                issues.add(invalid(ConfigIssue.Kind.REPLACE_RULE, path, entry, "mapping"));
                continue;
            }
            AppConfig.ReplaceRule rule = parseReplaceRule(path, map, issues);
            if (rule != null) {
                rules.add(rule);
            }
        }
        return List.copyOf(rules);
    }

    private static AppConfig.ReplaceRule parseReplaceRule(String path, Map<?, ?> map,
            List<ConfigIssue> issues) {
        Object applyToExisting = map.get("apply_to_existing");
        if (Boolean.TRUE.equals(applyToExisting)) {
            issues.add(invalid(ConfigIssue.Kind.REPLACE_RULE, path + ".apply_to_existing",
                    applyToExisting, "false"));
            return null;
        }
        Object rawStructure = map.get("structure");
        if (!(rawStructure instanceof String structure) || structure.isBlank()
                || !structure.toLowerCase(Locale.ROOT).startsWith("minecraft:")) {
            issues.add(invalid(ConfigIssue.Kind.REPLACE_RULE, path + ".structure",
                    rawStructure, "minecraft:<structure_id>"));
            return null;
        }
        Object rawTemplate = map.get("template");
        if (!(rawTemplate instanceof String template) || !NameRules.isValid(template)) {
            issues.add(invalid(ConfigIssue.Kind.REPLACE_RULE, path + ".template",
                    rawTemplate, "name"));
            return null;
        }
        double chance = 1.0d;
        Object rawChance = map.get("chance");
        if (rawChance != null) {
            if (!(rawChance instanceof Number number)) {
                issues.add(invalid(ConfigIssue.Kind.REPLACE_RULE, path + ".chance",
                        rawChance, "[0.0, 1.0]"));
                return null;
            }
            chance = number.doubleValue();
            if (!(chance >= 0.0d) || !(chance <= 1.0d)) {
                issues.add(invalid(ConfigIssue.Kind.REPLACE_RULE, path + ".chance",
                        rawChance, "[0.0, 1.0]"));
                return null;
            }
        }
        Object rawWorlds = map.get("worlds");
        if (rawWorlds != null && !(rawWorlds instanceof List<?>)) {
            issues.add(invalid(ConfigIssue.Kind.REPLACE_RULE, path + ".worlds",
                    rawWorlds, "list"));
            return null;
        }
        Object rawBiomes = map.get("biomes");
        if (rawBiomes != null && !(rawBiomes instanceof List<?>)) {
            issues.add(invalid(ConfigIssue.Kind.REPLACE_RULE, path + ".biomes",
                    rawBiomes, "list"));
            return null;
        }
        Object rawEnabled = map.get("enabled");
        if (rawEnabled != null && !(rawEnabled instanceof Boolean)) {
            issues.add(invalid(ConfigIssue.Kind.REPLACE_RULE, path + ".enabled",
                    rawEnabled, "true|false"));
            return null;
        }
        return new AppConfig.ReplaceRule(
                structure.toLowerCase(Locale.ROOT), template, chance,
                asStringList(rawWorlds), asStringList(rawBiomes),
                !(rawEnabled instanceof Boolean flag) || flag);
    }

    private static List<String> asStringList(Object raw) {
        if (!(raw instanceof List<?> values)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object value : values) {
            if (value instanceof String text && !text.isBlank()) {
                result.add(text.trim());
            }
        }
        return List.copyOf(result);
    }

    private static AppConfig.RollbackSettings parseRollback(YamlSection section,
            AppConfig.RollbackSettings defaults, List<ConfigIssue> issues) {
        if (section == null) {
            return defaults;
        }
        boolean enabled = readBoolean(section, "enabled",
                ConfigIssue.Kind.ROLLBACK_ENABLED, defaults.enabled(), issues);
        long maxBytes = readBoundedLong(section, "journal_max_bytes",
                ConfigIssue.Kind.ROLLBACK_JOURNAL_MAX_BYTES, defaults.journalMaxBytes(),
                1048576L, Long.MAX_VALUE, issues);
        return new AppConfig.RollbackSettings(enabled, maxBytes);
    }

    private static AppConfig.FurnitureSettings parseFurniture(YamlSection section,
            AppConfig.FurnitureSettings defaults, List<ConfigIssue> issues) {
        if (section == null) {
            return defaults;
        }
        boolean enabled = readBoolean(section, "enabled",
                ConfigIssue.Kind.FURNITURE_ENABLED, defaults.enabled(), issues);
        return new AppConfig.FurnitureSettings(enabled);
    }

    private static AppConfig.DetectionSettings parseDetection(YamlSection section,
            AppConfig.DetectionSettings defaults,
            List<ConfigIssue> issues) {
        if (section == null) {
            return defaults;
        }
        Duration sweep = defaults.sweepInterval();
        Object rawSweep = section.get("sweep_interval");
        if (rawSweep != null) {
            String text = asText(rawSweep);
            if (Durations.isValid(text)) {
                Duration parsed = Durations.parse(text, sweep);
                if (!parsed.isZero() && parsed.compareTo(Duration.ofSeconds(30)) < 0) {
                    issues.add(invalid(ConfigIssue.Kind.SWEEP_INTERVAL,
                            "detection.sweep_interval", rawSweep, "0 or >= 30s"));
                } else {
                    sweep = parsed;
                }
            } else {
                issues.add(invalid(ConfigIssue.Kind.SWEEP_INTERVAL,
                        "detection.sweep_interval", rawSweep, "0 or >= 30s"));
            }
        }
        int maxColdChecks = readBoundedInt(section, "max_cold_checks",
                ConfigIssue.Kind.MAX_COLD_CHECKS, defaults.maxColdChecks(), 8, 4096, issues);
        int instanceLimit = readBoundedInt(section, "instance_limit",
                ConfigIssue.Kind.INSTANCE_LIMIT, defaults.instanceLimit(),
                1, Integer.MAX_VALUE, issues);
        Duration instanceTtl = readDuration(section, "instance_ttl",
                ConfigIssue.Kind.INSTANCE_TTL, defaults.instanceTtl(),
                Duration.ofMinutes(1), issues);
        Duration debounce = readDuration(section, "debounce",
                ConfigIssue.Kind.DEBOUNCE, defaults.debounce(),
                Duration.ZERO, issues);
        boolean retrigger = readBoolean(section, "retrigger_on_reform",
                ConfigIssue.Kind.RETRIGGER_ON_REFORM, defaults.retriggerOnReform(), issues);
        boolean sample = readBoolean(section, "sample_recheck",
                ConfigIssue.Kind.SAMPLE_RECHECK, defaults.sampleRecheck(), issues);
        return new AppConfig.DetectionSettings(sweep, maxColdChecks, instanceLimit,
                instanceTtl, debounce, retrigger, sample);
    }

    private static AppConfig.CooldownSettings parseCooldown(YamlSection section,
            AppConfig.CooldownSettings defaults,
            List<ConfigIssue> issues) {
        if (section == null) {
            return defaults;
        }
        Duration global = readDuration(section, "global",
                ConfigIssue.Kind.COOLDOWN_GLOBAL, defaults.global(),
                Duration.ZERO, issues);
        Duration perPlayer = readDuration(section, "per_player",
                ConfigIssue.Kind.COOLDOWN_PER_PLAYER, defaults.perPlayer(),
                Duration.ZERO, issues);
        return new AppConfig.CooldownSettings(global, perPlayer);
    }

    private static Duration readDuration(YamlSection section, String key, ConfigIssue.Kind kind,
            Duration fallback, Duration minimum, List<ConfigIssue> issues) {
        Object raw = section.get(key);
        if (raw == null) {
            return fallback;
        }
        String text = asText(raw);
        if (!Durations.isValid(text)) {
            issues.add(invalid(kind, key, raw, ">= " + minimum));
            return fallback;
        }
        Duration parsed = Durations.parse(text, fallback);
        if (parsed.compareTo(minimum) < 0) {
            issues.add(invalid(kind, key, raw, ">= " + minimum));
            return fallback;
        }
        return parsed;
    }

    private static String asText(Object raw) {
        return raw instanceof Number number
                ? String.valueOf(number.longValue())
                : String.valueOf(raw);
    }

    private static AppConfig.GenerateSettings parseGenerate(YamlSection section,
            AppConfig.GenerateSettings defaults,
            List<ConfigIssue> issues) {
        if (section == null) {
            return defaults;
        }
        int sizeX = defaults.sizeLimitX();
        int sizeY = defaults.sizeLimitY();
        int sizeZ = defaults.sizeLimitZ();
        Object rawSize = section.get("size_limit");
        if (rawSize instanceof List<?> values && values.size() >= 3
                && values.get(0) instanceof Number first
                && values.get(1) instanceof Number second
                && values.get(2) instanceof Number third) {
            sizeX = first.intValue();
            sizeY = second.intValue();
            sizeZ = third.intValue();
            if (!inRange(sizeX, 8, 512) || !inRange(sizeY, 8, 512) || !inRange(sizeZ, 8, 512)) {
                issues.add(invalid(ConfigIssue.Kind.SIZE_LIMIT,
                        "generate.size_limit", rawSize, "[8, 512]"));
                sizeX = defaults.sizeLimitX();
                sizeY = defaults.sizeLimitY();
                sizeZ = defaults.sizeLimitZ();
            }
        } else if (rawSize != null) {
            issues.add(invalid(ConfigIssue.Kind.SIZE_LIMIT,
                    "generate.size_limit", rawSize, "[8, 512]"));
        }
        int maxBlocks = readBoundedInt(section, "max_blocks_per_tick",
                ConfigIssue.Kind.MAX_BLOCKS_PER_TICK,
                defaults.maxBlocksPerTick(), 1, 200000, issues);
        AppConfig.EntityPolicy entityPolicy = defaults.entityPolicy();
        Object rawPolicy = section.get("entity_policy");
        if (rawPolicy != null) {
            if (rawPolicy instanceof String text && isEntityPolicy(text)) {
                entityPolicy = AppConfig.EntityPolicy.fromConfig(text, entityPolicy);
            } else {
                issues.add(invalid(ConfigIssue.Kind.ENTITY_POLICY,
                        "generate.entity_policy", rawPolicy, "all|passive|none"));
            }
        }
        AppConfig.GroundMode groundMode = defaults.groundMode();
        Object rawGround = section.get("ground_mode");
        if (rawGround != null) {
            if (rawGround instanceof String text && isGroundMode(text)) {
                groundMode = AppConfig.GroundMode.fromConfig(text, groundMode);
            } else {
                issues.add(invalid(ConfigIssue.Kind.GROUND_MODE,
                        "generate.ground_mode", rawGround, "keep|fill|skip"));
            }
        }
        boolean adaptive = readBoolean(section, "adaptive_throttling",
                ConfigIssue.Kind.ADAPTIVE_THROTTLING, defaults.adaptiveThrottling(), issues);
        Duration queueTimeout = defaults.queueTimeout();
        Object rawTimeout = section.get("queue_timeout");
        if (rawTimeout != null) {
            String text = rawTimeout instanceof Number number
                    ? String.valueOf(number.longValue())
                    : String.valueOf(rawTimeout);
            if (Durations.isValid(text)) {
                queueTimeout = Durations.parse(text, queueTimeout);
                if (queueTimeout.isZero() || queueTimeout.isNegative()) {
                    issues.add(invalid(ConfigIssue.Kind.QUEUE_TIMEOUT,
                            "generate.queue_timeout", rawTimeout, "> 0s"));
                    queueTimeout = defaults.queueTimeout();
                }
            } else {
                issues.add(invalid(ConfigIssue.Kind.QUEUE_TIMEOUT,
                        "generate.queue_timeout", rawTimeout, "> 0s"));
            }
        }
        return new AppConfig.GenerateSettings(sizeX, sizeY, sizeZ, maxBlocks,
                entityPolicy, groundMode, adaptive, queueTimeout);
    }

    private static AppConfig.CacheSettings parseCache(YamlSection section,
            AppConfig.CacheSettings defaults,
            List<ConfigIssue> issues) {
        if (section == null) {
            return defaults;
        }
        int maxEntries = readBoundedInt(section, "max_entries",
                ConfigIssue.Kind.CACHE_MAX_ENTRIES, defaults.maxEntries(),
                1, Integer.MAX_VALUE, issues);
        long maxBytes = readBoundedLong(section, "max_bytes",
                ConfigIssue.Kind.CACHE_MAX_BYTES, defaults.maxBytes(),
                1L, Long.MAX_VALUE, issues);
        return new AppConfig.CacheSettings(maxEntries, maxBytes);
    }

    private static AppConfig.SelectionSettings parseSelection(YamlSection section,
            AppConfig.SelectionSettings defaults,
            List<ConfigIssue> issues) {
        if (section == null) {
            return defaults;
        }
        Duration timeout = defaults.sessionTimeout();
        Object raw = section.get("session_timeout");
        if (raw != null) {
            String text = raw instanceof Number number
                    ? String.valueOf(number.longValue())
                    : String.valueOf(raw);
            if (Durations.isValid(text)) {
                Duration parsed = Durations.parse(text, timeout);
                if (parsed.compareTo(Duration.ofMinutes(1)) < 0
                        || parsed.compareTo(Duration.ofHours(24)) > 0) {
                    issues.add(invalid(ConfigIssue.Kind.SESSION_TIMEOUT,
                            "selection.session_timeout", raw, "1m-24h"));
                } else {
                    timeout = parsed;
                }
            } else {
                issues.add(invalid(ConfigIssue.Kind.SESSION_TIMEOUT,
                        "selection.session_timeout", raw, "1m-24h"));
            }
        }
        return new AppConfig.SelectionSettings(timeout);
    }

    private static AppConfig.TemplateSettings parseTemplate(YamlSection section,
            AppConfig.TemplateSettings defaults,
            List<ConfigIssue> issues) {
        if (section == null) {
            return defaults;
        }
        long importMax = readBoundedLong(section, "import_max_file_bytes",
                ConfigIssue.Kind.IMPORT_MAX_FILE_BYTES, defaults.importMaxFileBytes(),
                1L, Long.MAX_VALUE, issues);
        AppConfig.LegacyPolicy legacyPolicy = defaults.legacyPolicy();
        Object rawPolicy = section.get("legacy_policy");
        if (rawPolicy != null) {
            if (rawPolicy instanceof String text && isLegacyPolicy(text)) {
                legacyPolicy = AppConfig.LegacyPolicy.fromConfig(text, legacyPolicy);
            } else {
                issues.add(invalid(ConfigIssue.Kind.LEGACY_POLICY,
                        "template.legacy_policy", rawPolicy, "reject|skip"));
            }
        }
        int paletteLimit = readBoundedInt(section, "palette_limit",
                ConfigIssue.Kind.PALETTE_LIMIT, defaults.paletteLimit(),
                1, Integer.MAX_VALUE, issues);
        int blockEntityMax = readBoundedInt(section, "block_entity_max_bytes",
                ConfigIssue.Kind.BLOCK_ENTITY_MAX_BYTES, defaults.blockEntityMaxBytes(),
                1, Integer.MAX_VALUE, issues);
        return new AppConfig.TemplateSettings(importMax, legacyPolicy, paletteLimit, blockEntityMax);
    }

    private static AppConfig.SpawnSettings parseSpawn(YamlSection section,
            AppConfig.SpawnSettings defaults,
            List<ConfigIssue> issues) {
        if (section == null) {
            return defaults;
        }
        boolean enabled = readBoolean(section, "enabled",
                ConfigIssue.Kind.SPAWN_ENABLED, defaults.enabled(), issues);
        int maxPerGeneration = readBoundedInt(section, "max_per_generation",
                ConfigIssue.Kind.SPAWN_MAX_PER_GENERATION, defaults.maxPerGeneration(),
                1, 256, issues);
        int batchPerTick = readBoundedInt(section, "batch_per_tick",
                ConfigIssue.Kind.SPAWN_BATCH_PER_TICK, defaults.batchPerTick(),
                1, 64, issues);
        boolean respectPolicy = readBoolean(section, "respect_entity_policy",
                ConfigIssue.Kind.SPAWN_RESPECT_ENTITY_POLICY, defaults.respectEntityPolicy(), issues);
        return new AppConfig.SpawnSettings(enabled, maxPerGeneration, batchPerTick, respectPolicy);
    }

    private static int readBoundedInt(YamlSection section, String key, ConfigIssue.Kind kind,
            int fallback, int min, int max, List<ConfigIssue> issues) {
        Object raw = section.get(key);
        if (raw == null) {
            return fallback;
        }
        if (!(raw instanceof Number number)) {
            issues.add(invalid(kind, key, raw, min + "-" + max));
            return fallback;
        }
        int value = number.intValue();
        if (!inRange(value, min, max)) {
            issues.add(invalid(kind, key, raw, min + "-" + max));
            return fallback;
        }
        return value;
    }

    private static long readBoundedLong(YamlSection section, String key, ConfigIssue.Kind kind,
            long fallback, long min, long max, List<ConfigIssue> issues) {
        Object raw = section.get(key);
        if (raw == null) {
            return fallback;
        }
        if (!(raw instanceof Number number)) {
            issues.add(invalid(kind, key, raw, min + "-" + max));
            return fallback;
        }
        long value = number.longValue();
        if (value < min || value > max) {
            issues.add(invalid(kind, key, raw, min + "-" + max));
            return fallback;
        }
        return value;
    }

    private static boolean readBoolean(YamlSection section, String key, ConfigIssue.Kind kind,
            boolean fallback, List<ConfigIssue> issues) {
        Object raw = section.get(key);
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof Boolean flag) {
            return flag;
        }
        issues.add(invalid(kind, key, raw, "true|false"));
        return fallback;
    }

    private static String readString(YamlSection section, String key, String fallback) {
        Object raw = section.get(key);
        if (raw instanceof String text && !text.isBlank()) {
            return text;
        }
        return fallback;
    }

    private static ConfigIssue invalid(ConfigIssue.Kind kind, String path, Object value,
            String expected) {
        String shown = value == null ? "" : String.valueOf(value);
        if (shown.length() > 64) {
            shown = shown.substring(0, 64);
        }
        return new ConfigIssue(kind, "error.config.invalid",
                Map.of("path", path, "value", shown, "expected", expected));
    }

    private static boolean inRange(int value, int min, int max) {
        return value >= min && value <= max;
    }

    private static boolean isEntityPolicy(String raw) {
        return isOneOf(raw, "all", "passive", "none");
    }

    private static boolean isGroundMode(String raw) {
        return isOneOf(raw, "keep", "fill", "skip");
    }

    private static boolean isLegacyPolicy(String raw) {
        return isOneOf(raw, "reject", "skip");
    }

    private static boolean isOneOf(String raw, String... allowed) {
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (String candidate : allowed) {
            if (candidate.equals(normalized)) {
                return true;
            }
        }
        return false;
    }
}
