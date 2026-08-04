package emaki.jiuwu.craft.level.service;

import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import emaki.jiuwu.craft.corelib.session.AbstractPlayerSessionCache;
import emaki.jiuwu.craft.level.model.PlayerLevelData;

/**
 * Session cache for loaded player level data.
 *
 * <p>All generation / seal / save-lane mechanics live in
 * {@link AbstractPlayerSessionCache}; this subclass only binds the key and payload types. Level
 * was the template the base class was extracted from, so it adds no module-specific behaviour and
 * keeps no data codec of its own — serialisation belongs to {@link PlayerLevelDataStore}.
 *
 * <p>The one exception is diagnostics: the base class records persistence anchors through a no-op
 * hook, and this subclass is where that hook gets an output. The gate and the sink arrive as plain
 * functions rather than a logger, so this class keeps no logging dependency of its own and the
 * composition root stays the only place that knows where anchors go.
 */
final class PlayerLevelDataCache extends AbstractPlayerSessionCache<UUID, PlayerLevelData> {

    private final BooleanSupplier anchorGate;
    private final Consumer<String> anchorSink;

    PlayerLevelDataCache() {
        this(null, null);
    }

    /**
     * @param anchorGate tells whether anchors are currently wanted; {@code null} disables them
     * @param anchorSink receives rendered anchor lines; {@code null} disables them
     */
    PlayerLevelDataCache(BooleanSupplier anchorGate, Consumer<String> anchorSink) {
        this.anchorGate = anchorGate;
        this.anchorSink = anchorSink;
    }

    @Override
    protected boolean anchorEnabled() {
        return anchorGate != null && anchorSink != null && anchorGate.getAsBoolean();
    }

    @Override
    protected void anchor(String fields) {
        Consumer<String> sink = anchorSink;
        if (sink != null) {
            sink.accept(fields);
        }
    }
}
