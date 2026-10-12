package emaki.jiuwu.craft.structure.config;

import java.util.Map;

public record ConfigIssue(Kind kind, String reasonKey, Map<String, Object> placeholders) {

    public enum Kind {
        SIZE_LIMIT,
        MAX_BLOCKS_PER_TICK,
        ENTITY_POLICY,
        GROUND_MODE,
        ADAPTIVE_THROTTLING,
        QUEUE_TIMEOUT,
        CACHE_MAX_ENTRIES,
        CACHE_MAX_BYTES,
        SESSION_TIMEOUT,
        IMPORT_MAX_FILE_BYTES,
        LEGACY_POLICY,
        PALETTE_LIMIT,
        BLOCK_ENTITY_MAX_BYTES,
        SPAWN_ENABLED,
        SPAWN_MAX_PER_GENERATION,
        SPAWN_BATCH_PER_TICK,
        SPAWN_RESPECT_ENTITY_POLICY,
        SWEEP_INTERVAL,
        MAX_COLD_CHECKS,
        INSTANCE_LIMIT,
        INSTANCE_TTL,
        DEBOUNCE,
        RETRIGGER_ON_REFORM,
        SAMPLE_RECHECK,
        COOLDOWN_GLOBAL,
        COOLDOWN_PER_PLAYER,
        REPLACE_RULE,
        ROLLBACK_ENABLED,
        ROLLBACK_JOURNAL_MAX_BYTES,
        FURNITURE_ENABLED
    }

    public ConfigIssue {
        if (kind == null) {
            throw new NullPointerException("kind");
        }
        if (reasonKey == null) {
            throw new NullPointerException("reasonKey");
        }
        placeholders = placeholders == null ? Map.of() : Map.copyOf(placeholders);
    }
}
