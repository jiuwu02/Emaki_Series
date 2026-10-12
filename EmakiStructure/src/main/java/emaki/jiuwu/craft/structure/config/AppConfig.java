package emaki.jiuwu.craft.structure.config;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public record AppConfig(String language,
                        String version,
                        GenerateSettings generate,
                        CacheSettings cache,
                        SelectionSettings selection,
                        TemplateSettings template,
                        SpawnSettings spawn,
                        DetectionSettings detection,
                        CooldownSettings cooldown,
                        List<ReplaceRule> replaceRules,
                        RollbackSettings rollback,
                        FurnitureSettings furniture,
                        List<ConfigIssue> issues) {

    public static final String CURRENT_VERSION = "1.0.0";

    public enum EntityPolicy {
        ALL, PASSIVE, NONE;

        public static EntityPolicy fromConfig(String raw, EntityPolicy fallback) {
            if (raw == null) {
                return fallback;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "all" -> ALL;
                case "passive" -> PASSIVE;
                case "none" -> NONE;
                default -> fallback;
            };
        }
    }

    public enum GroundMode {
        KEEP, FILL, SKIP;

        public static GroundMode fromConfig(String raw, GroundMode fallback) {
            if (raw == null) {
                return fallback;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "keep" -> KEEP;
                case "fill" -> FILL;
                case "skip" -> SKIP;
                default -> fallback;
            };
        }
    }

    public enum LegacyPolicy {
        REJECT, SKIP;

        public static LegacyPolicy fromConfig(String raw, LegacyPolicy fallback) {
            if (raw == null) {
                return fallback;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "reject" -> REJECT;
                case "skip" -> SKIP;
                default -> fallback;
            };
        }
    }

    public record GenerateSettings(int sizeLimitX,
                                   int sizeLimitY,
                                   int sizeLimitZ,
                                   int maxBlocksPerTick,
                                   EntityPolicy entityPolicy,
                                   GroundMode groundMode,
                                   boolean adaptiveThrottling,
                                   Duration queueTimeout) {

        public GenerateSettings withSizeLimit(GenerateSettings fallback) {
            return new GenerateSettings(fallback.sizeLimitX(), fallback.sizeLimitY(), fallback.sizeLimitZ(),
                    maxBlocksPerTick, entityPolicy, groundMode, adaptiveThrottling, queueTimeout);
        }

        public GenerateSettings withMaxBlocksPerTick(GenerateSettings fallback) {
            return new GenerateSettings(sizeLimitX, sizeLimitY, sizeLimitZ,
                    fallback.maxBlocksPerTick(), entityPolicy, groundMode, adaptiveThrottling, queueTimeout);
        }

        public GenerateSettings withEntityPolicy(GenerateSettings fallback) {
            return new GenerateSettings(sizeLimitX, sizeLimitY, sizeLimitZ,
                    maxBlocksPerTick, fallback.entityPolicy(), groundMode, adaptiveThrottling, queueTimeout);
        }

        public GenerateSettings withGroundMode(GenerateSettings fallback) {
            return new GenerateSettings(sizeLimitX, sizeLimitY, sizeLimitZ,
                    maxBlocksPerTick, entityPolicy, fallback.groundMode(), adaptiveThrottling, queueTimeout);
        }

        public GenerateSettings withAdaptiveThrottling(GenerateSettings fallback) {
            return new GenerateSettings(sizeLimitX, sizeLimitY, sizeLimitZ,
                    maxBlocksPerTick, entityPolicy, groundMode, fallback.adaptiveThrottling(), queueTimeout);
        }

        public GenerateSettings withQueueTimeout(GenerateSettings fallback) {
            return new GenerateSettings(sizeLimitX, sizeLimitY, sizeLimitZ,
                    maxBlocksPerTick, entityPolicy, groundMode, adaptiveThrottling, fallback.queueTimeout());
        }
    }

    public record CacheSettings(int maxEntries, long maxBytes) {

        public CacheSettings withMaxEntries(CacheSettings fallback) {
            return new CacheSettings(fallback.maxEntries(), maxBytes);
        }

        public CacheSettings withMaxBytes(CacheSettings fallback) {
            return new CacheSettings(maxEntries, fallback.maxBytes());
        }
    }

    public record SelectionSettings(Duration sessionTimeout) {

        public SelectionSettings withSessionTimeout(SelectionSettings fallback) {
            return new SelectionSettings(fallback.sessionTimeout());
        }
    }

    public record TemplateSettings(long importMaxFileBytes,
                                   LegacyPolicy legacyPolicy,
                                   int paletteLimit,
                                   int blockEntityMaxBytes) {

        public TemplateSettings withImportMaxFileBytes(TemplateSettings fallback) {
            return new TemplateSettings(fallback.importMaxFileBytes(), legacyPolicy,
                    paletteLimit, blockEntityMaxBytes);
        }

        public TemplateSettings withLegacyPolicy(TemplateSettings fallback) {
            return new TemplateSettings(importMaxFileBytes, fallback.legacyPolicy(),
                    paletteLimit, blockEntityMaxBytes);
        }

        public TemplateSettings withPaletteLimit(TemplateSettings fallback) {
            return new TemplateSettings(importMaxFileBytes, legacyPolicy,
                    fallback.paletteLimit(), blockEntityMaxBytes);
        }

        public TemplateSettings withBlockEntityMaxBytes(TemplateSettings fallback) {
            return new TemplateSettings(importMaxFileBytes, legacyPolicy,
                    paletteLimit, fallback.blockEntityMaxBytes());
        }
    }

    public record SpawnSettings(boolean enabled,
                                int maxPerGeneration,
                                int batchPerTick,
                                boolean respectEntityPolicy) {

        public SpawnSettings withEnabled(SpawnSettings fallback) {
            return new SpawnSettings(fallback.enabled(), maxPerGeneration, batchPerTick, respectEntityPolicy);
        }

        public SpawnSettings withMaxPerGeneration(SpawnSettings fallback) {
            return new SpawnSettings(enabled, fallback.maxPerGeneration(), batchPerTick, respectEntityPolicy);
        }

        public SpawnSettings withBatchPerTick(SpawnSettings fallback) {
            return new SpawnSettings(enabled, maxPerGeneration, fallback.batchPerTick(), respectEntityPolicy);
        }

        public SpawnSettings withRespectEntityPolicy(SpawnSettings fallback) {
            return new SpawnSettings(enabled, maxPerGeneration, batchPerTick, fallback.respectEntityPolicy());
        }
    }

    public record DetectionSettings(Duration sweepInterval,
                                    int maxColdChecks,
                                    int instanceLimit,
                                    Duration instanceTtl,
                                    Duration debounce,
                                    boolean retriggerOnReform,
                                    boolean sampleRecheck) {

        public DetectionSettings withSweepInterval(DetectionSettings fallback) {
            return new DetectionSettings(fallback.sweepInterval(), maxColdChecks, instanceLimit,
                    instanceTtl, debounce, retriggerOnReform, sampleRecheck);
        }

        public DetectionSettings withMaxColdChecks(DetectionSettings fallback) {
            return new DetectionSettings(sweepInterval, fallback.maxColdChecks(), instanceLimit,
                    instanceTtl, debounce, retriggerOnReform, sampleRecheck);
        }

        public DetectionSettings withInstanceLimit(DetectionSettings fallback) {
            return new DetectionSettings(sweepInterval, maxColdChecks, fallback.instanceLimit(),
                    instanceTtl, debounce, retriggerOnReform, sampleRecheck);
        }

        public DetectionSettings withInstanceTtl(DetectionSettings fallback) {
            return new DetectionSettings(sweepInterval, maxColdChecks, instanceLimit,
                    fallback.instanceTtl(), debounce, retriggerOnReform, sampleRecheck);
        }

        public DetectionSettings withDebounce(DetectionSettings fallback) {
            return new DetectionSettings(sweepInterval, maxColdChecks, instanceLimit,
                    instanceTtl, fallback.debounce(), retriggerOnReform, sampleRecheck);
        }

        public DetectionSettings withRetriggerOnReform(DetectionSettings fallback) {
            return new DetectionSettings(sweepInterval, maxColdChecks, instanceLimit,
                    instanceTtl, debounce, fallback.retriggerOnReform(), sampleRecheck);
        }

        public DetectionSettings withSampleRecheck(DetectionSettings fallback) {
            return new DetectionSettings(sweepInterval, maxColdChecks, instanceLimit,
                    instanceTtl, debounce, retriggerOnReform, fallback.sampleRecheck());
        }
    }

    public record CooldownSettings(Duration global, Duration perPlayer) {

        public CooldownSettings withGlobal(CooldownSettings fallback) {
            return new CooldownSettings(fallback.global(), perPlayer);
        }

        public CooldownSettings withPerPlayer(CooldownSettings fallback) {
            return new CooldownSettings(global, fallback.perPlayer());
        }
    }

    public record ReplaceRule(String structure,
                              String template,
                              double chance,
                              List<String> worlds,
                              List<String> biomes,
                              boolean enabled) {

        public ReplaceRule {
            worlds = worlds == null ? List.of() : List.copyOf(worlds);
            biomes = biomes == null ? List.of() : List.copyOf(biomes);
        }

        public boolean matchesWorld(String worldName) {
            return worlds.isEmpty() || worlds.contains(worldName);
        }

        public boolean matchesBiome(String biomeName) {
            return biomes.isEmpty() || biomes.contains(biomeName);
        }
    }

    public record RollbackSettings(boolean enabled, long journalMaxBytes) {

        public RollbackSettings withEnabled(RollbackSettings fallback) {
            return new RollbackSettings(fallback.enabled(), journalMaxBytes);
        }

        public RollbackSettings withJournalMaxBytes(RollbackSettings fallback) {
            return new RollbackSettings(enabled, fallback.journalMaxBytes());
        }
    }

    public record FurnitureSettings(boolean enabled) {

        public FurnitureSettings withEnabled(FurnitureSettings fallback) {
            return new FurnitureSettings(fallback.enabled());
        }
    }

    public static AppConfig defaults() {
        return new AppConfig("zh_CN", CURRENT_VERSION,
                new GenerateSettings(128, 128, 128, 3000, EntityPolicy.ALL, GroundMode.SKIP,
                        true, Duration.ofSeconds(60)),
                new CacheSettings(128, 268435456L),
                new SelectionSettings(Duration.ofMinutes(30)),
                new TemplateSettings(33554432L, LegacyPolicy.REJECT, 4096, 65536),
                new SpawnSettings(true, 32, 8, false),
                new DetectionSettings(Duration.ZERO, 256, 8, Duration.ofMinutes(10),
                        Duration.ofMillis(500), false, false),
                new CooldownSettings(Duration.ZERO, Duration.ZERO),
                List.of(),
                new RollbackSettings(true, 67108864L),
                new FurnitureSettings(true),
                List.of());
    }

    public AppConfig withIssues(List<ConfigIssue> recorded) {
        return new AppConfig(language, version, generate, cache, selection, template, spawn,
                detection, cooldown, replaceRules, rollback, furniture,
                recorded == null ? List.of() : List.copyOf(recorded));
    }

    public AppConfig withoutIssues() {
        return issues.isEmpty() ? this : withIssues(List.of());
    }

    public AppConfig repair(AppConfig fallback) {
        if (issues.isEmpty()) {
            return this;
        }
        Set<ConfigIssue.Kind> kinds = Set.copyOf(issues.stream().map(ConfigIssue::kind).toList());
        GenerateSettings nextGenerate = generate;
        if (kinds.contains(ConfigIssue.Kind.SIZE_LIMIT)) {
            nextGenerate = nextGenerate.withSizeLimit(fallback.generate());
        }
        if (kinds.contains(ConfigIssue.Kind.MAX_BLOCKS_PER_TICK)) {
            nextGenerate = nextGenerate.withMaxBlocksPerTick(fallback.generate());
        }
        if (kinds.contains(ConfigIssue.Kind.ENTITY_POLICY)) {
            nextGenerate = nextGenerate.withEntityPolicy(fallback.generate());
        }
        if (kinds.contains(ConfigIssue.Kind.GROUND_MODE)) {
            nextGenerate = nextGenerate.withGroundMode(fallback.generate());
        }
        if (kinds.contains(ConfigIssue.Kind.ADAPTIVE_THROTTLING)) {
            nextGenerate = nextGenerate.withAdaptiveThrottling(fallback.generate());
        }
        if (kinds.contains(ConfigIssue.Kind.QUEUE_TIMEOUT)) {
            nextGenerate = nextGenerate.withQueueTimeout(fallback.generate());
        }
        CacheSettings nextCache = cache;
        if (kinds.contains(ConfigIssue.Kind.CACHE_MAX_ENTRIES)) {
            nextCache = nextCache.withMaxEntries(fallback.cache());
        }
        if (kinds.contains(ConfigIssue.Kind.CACHE_MAX_BYTES)) {
            nextCache = nextCache.withMaxBytes(fallback.cache());
        }
        SelectionSettings nextSelection = selection;
        if (kinds.contains(ConfigIssue.Kind.SESSION_TIMEOUT)) {
            nextSelection = nextSelection.withSessionTimeout(fallback.selection());
        }
        TemplateSettings nextTemplate = template;
        if (kinds.contains(ConfigIssue.Kind.IMPORT_MAX_FILE_BYTES)) {
            nextTemplate = nextTemplate.withImportMaxFileBytes(fallback.template());
        }
        if (kinds.contains(ConfigIssue.Kind.LEGACY_POLICY)) {
            nextTemplate = nextTemplate.withLegacyPolicy(fallback.template());
        }
        if (kinds.contains(ConfigIssue.Kind.PALETTE_LIMIT)) {
            nextTemplate = nextTemplate.withPaletteLimit(fallback.template());
        }
        if (kinds.contains(ConfigIssue.Kind.BLOCK_ENTITY_MAX_BYTES)) {
            nextTemplate = nextTemplate.withBlockEntityMaxBytes(fallback.template());
        }
        SpawnSettings nextSpawn = spawn;
        if (kinds.contains(ConfigIssue.Kind.SPAWN_ENABLED)) {
            nextSpawn = nextSpawn.withEnabled(fallback.spawn());
        }
        if (kinds.contains(ConfigIssue.Kind.SPAWN_MAX_PER_GENERATION)) {
            nextSpawn = nextSpawn.withMaxPerGeneration(fallback.spawn());
        }
        if (kinds.contains(ConfigIssue.Kind.SPAWN_BATCH_PER_TICK)) {
            nextSpawn = nextSpawn.withBatchPerTick(fallback.spawn());
        }
        if (kinds.contains(ConfigIssue.Kind.SPAWN_RESPECT_ENTITY_POLICY)) {
            nextSpawn = nextSpawn.withRespectEntityPolicy(fallback.spawn());
        }
        DetectionSettings nextDetection = detection;
        if (kinds.contains(ConfigIssue.Kind.SWEEP_INTERVAL)) {
            nextDetection = nextDetection.withSweepInterval(fallback.detection());
        }
        if (kinds.contains(ConfigIssue.Kind.MAX_COLD_CHECKS)) {
            nextDetection = nextDetection.withMaxColdChecks(fallback.detection());
        }
        if (kinds.contains(ConfigIssue.Kind.INSTANCE_LIMIT)) {
            nextDetection = nextDetection.withInstanceLimit(fallback.detection());
        }
        if (kinds.contains(ConfigIssue.Kind.INSTANCE_TTL)) {
            nextDetection = nextDetection.withInstanceTtl(fallback.detection());
        }
        if (kinds.contains(ConfigIssue.Kind.DEBOUNCE)) {
            nextDetection = nextDetection.withDebounce(fallback.detection());
        }
        if (kinds.contains(ConfigIssue.Kind.RETRIGGER_ON_REFORM)) {
            nextDetection = nextDetection.withRetriggerOnReform(fallback.detection());
        }
        if (kinds.contains(ConfigIssue.Kind.SAMPLE_RECHECK)) {
            nextDetection = nextDetection.withSampleRecheck(fallback.detection());
        }
        CooldownSettings nextCooldown = cooldown;
        if (kinds.contains(ConfigIssue.Kind.COOLDOWN_GLOBAL)) {
            nextCooldown = nextCooldown.withGlobal(fallback.cooldown());
        }
        if (kinds.contains(ConfigIssue.Kind.COOLDOWN_PER_PLAYER)) {
            nextCooldown = nextCooldown.withPerPlayer(fallback.cooldown());
        }
        RollbackSettings nextRollback = rollback;
        if (kinds.contains(ConfigIssue.Kind.ROLLBACK_ENABLED)) {
            nextRollback = nextRollback.withEnabled(fallback.rollback());
        }
        if (kinds.contains(ConfigIssue.Kind.ROLLBACK_JOURNAL_MAX_BYTES)) {
            nextRollback = nextRollback.withJournalMaxBytes(fallback.rollback());
        }
        FurnitureSettings nextFurniture = furniture;
        if (kinds.contains(ConfigIssue.Kind.FURNITURE_ENABLED)) {
            nextFurniture = nextFurniture.withEnabled(fallback.furniture());
        }
        return new AppConfig(language, version, nextGenerate, nextCache, nextSelection,
                nextTemplate, nextSpawn, nextDetection, nextCooldown,
                replaceRules, nextRollback, nextFurniture, issues);
    }
}
