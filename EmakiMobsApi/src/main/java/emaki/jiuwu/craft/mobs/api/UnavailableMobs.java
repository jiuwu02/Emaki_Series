package emaki.jiuwu.craft.mobs.api;

import emaki.jiuwu.craft.mobs.api.model.MobDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.Set;

final class UnavailableMobs {

    static final MobCatalog CATALOG = new MobCatalog() {

        @Override
        public Optional<MobDefinition> definition(@NotNull String mobId) {
            return Optional.empty();
        }

        @Override
        public @NotNull Set<String> registeredIds() {
            return Set.of();
        }
    };

    private UnavailableMobs() {
    }
}
