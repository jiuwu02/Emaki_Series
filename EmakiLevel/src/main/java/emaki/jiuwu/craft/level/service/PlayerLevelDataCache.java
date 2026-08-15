package emaki.jiuwu.craft.level.service;

import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import emaki.jiuwu.craft.corelib.session.AbstractPlayerSessionCache;
import emaki.jiuwu.craft.level.model.PlayerLevelData;

final class PlayerLevelDataCache extends AbstractPlayerSessionCache<UUID, PlayerLevelData> {

    private final BooleanSupplier anchorGate;
    private final Consumer<String> anchorSink;

    PlayerLevelDataCache() {
        this(null, null);
    }

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
