package emaki.jiuwu.craft.structure.api;

import emaki.jiuwu.craft.structure.api.model.StructureTemplateView;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.Set;

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
