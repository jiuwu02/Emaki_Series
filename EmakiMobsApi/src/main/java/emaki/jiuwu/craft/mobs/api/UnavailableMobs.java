package emaki.jiuwu.craft.mobs.api;

import emaki.jiuwu.craft.mobs.api.model.MobDefinition;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;
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

        @Override
        public @NotNull Optional<String> identify(LivingEntity entity) {
            return Optional.empty();
        }
    };

    static final MobOperations OPERATIONS = new MobOperations() {
        @Override
        public @NotNull Optional<LivingEntity> spawn(@NotNull Location location, @NotNull String mobId) {
            return Optional.empty();
        }

        @Override
        public boolean refresh(LivingEntity entity) {
            return false;
        }
    };

    static final MobExtensions EXTENSIONS = new MobExtensions() {
        @Override
        public @NotNull MobSpawnerRegistration registerCustomSpawner(Plugin owner, String id, CustomSpawner spawner) {
            return MobSpawnerRegistration.noop();
        }

        @Override
        public void unregisterCustomSpawners(Plugin owner) {
        }
    };

    private UnavailableMobs() {
    }
}
