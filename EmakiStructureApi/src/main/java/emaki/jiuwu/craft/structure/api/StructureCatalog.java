package emaki.jiuwu.craft.structure.api;

import emaki.jiuwu.craft.structure.api.model.StructureTemplateView;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.Set;

/**
 * Read-only query layer over the structure templates loaded by EmakiStructure.
 *
 * <p>All methods return snapshots and are safe to call from any thread. An empty answer does
 * not necessarily mean EmakiStructure is absent; check {@link EmakiStructureApi#status()} to
 * distinguish "not installed" from "installed but no template loaded".
 */
public interface StructureCatalog {

    /**
     * {@return an unmodifiable snapshot of every currently loaded template id}
     */
    @NotNull
    Set<String> registeredIds();

    /**
     * {@return the template view for the given id, or empty when not loaded}
     *
     * @param templateId template id to resolve
     */
    @NotNull
    Optional<StructureTemplateView> template(@NotNull String templateId);
}
