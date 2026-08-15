package emaki.jiuwu.craft.mobs.apiimpl;

import emaki.jiuwu.craft.mobs.EmakiMobsPlugin;
import emaki.jiuwu.craft.mobs.api.MobCatalog;
import emaki.jiuwu.craft.mobs.api.model.MobDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.Set;

final class DefaultMobCatalog implements MobCatalog {

    private final EmakiMobsPlugin plugin;

    DefaultMobCatalog(EmakiMobsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Optional<MobDefinition> definition(@NotNull String mobId) {
        if (!plugin.contentReady()) {
            return Optional.empty();
        }
        var spec = plugin.mobRegistry().get().get(mobId);
        return spec == null ? Optional.empty() : Optional.of(spec.toApiModel());
    }

    @Override
    public @NotNull Set<String> registeredIds() {
        if (!plugin.contentReady()) {
            return Set.of();
        }
        return Set.copyOf(plugin.mobRegistry().get().keySet());
    }
}
