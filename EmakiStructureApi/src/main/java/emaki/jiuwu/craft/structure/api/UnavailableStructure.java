package emaki.jiuwu.craft.structure.api;

import emaki.jiuwu.craft.structure.api.model.StructureTemplateView;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.Set;

/**
 * No-op layer handed out by {@link EmakiStructureApi} while no bridge is installed.
 *
 * <p>{@code CATALOG} is the shared instance the facade's {@code catalog()} accessor returns when
 * EmakiStructure is absent or the bridge reports {@code null}: it answers with an empty id set
 * and empty optionals and never throws. Availability must be checked with
 * {@link EmakiStructureApi#status()} rather than inferred from an empty answer.
 */
final class UnavailableStructure {

    static final StructureCatalog CATALOG = new StructureCatalog() {

        @Override
        public @NotNull Set<String> registeredIds() {
            return Set.of();
        }

        @Override
        public @NotNull Optional<StructureTemplateView> template(@NotNull String templateId) {
            return Optional.empty();
        }
    };

    private UnavailableStructure() {
    }
}
