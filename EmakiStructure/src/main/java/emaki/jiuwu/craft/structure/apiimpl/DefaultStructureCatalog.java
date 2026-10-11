package emaki.jiuwu.craft.structure.apiimpl;

import emaki.jiuwu.craft.structure.EmakiStructurePlugin;
import emaki.jiuwu.craft.structure.api.StructureCatalog;
import emaki.jiuwu.craft.structure.api.model.StructureTemplateView;
import emaki.jiuwu.craft.structure.template.StructureTemplate;
import emaki.jiuwu.craft.structure.template.TemplateRepository;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.Set;

public final class DefaultStructureCatalog implements StructureCatalog {

    private final EmakiStructurePlugin plugin;

    public DefaultStructureCatalog(EmakiStructurePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull Set<String> registeredIds() {
        TemplateRepository repository = plugin.templateRepository();
        if (repository == null) {
            return Set.of();
        }
        return Set.copyOf(repository.templateIds());
    }

    @Override
    public @NotNull Optional<StructureTemplateView> template(@NotNull String templateId) {
        TemplateRepository repository = plugin.templateRepository();
        if (repository == null) {
            return Optional.empty();
        }
        Optional<StructureTemplate> template = repository.template(templateId);
        if (template.isEmpty()) {
            return Optional.empty();
        }
        StructureTemplate loaded = template.get();
        return Optional.of(new StructureTemplateView(loaded.id(), loaded.sizeX(), loaded.sizeY(),
                loaded.sizeZ(), (int) Math.min(Integer.MAX_VALUE, loaded.blockCount())));
    }
}
