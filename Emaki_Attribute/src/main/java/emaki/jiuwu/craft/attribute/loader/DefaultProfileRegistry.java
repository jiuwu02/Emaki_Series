package emaki.jiuwu.craft.attribute.loader;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.model.DefaultProfile;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;

public final class DefaultProfileRegistry extends DirectoryLoader<DefaultProfile> {

    private final List<DefaultProfile> mergedProfiles = new ArrayList<>();

    public DefaultProfileRegistry(EmakiAttributePlugin plugin) {
        super(plugin);
    }

    @Override
    protected String directoryName() {
        return "defaults";
    }

    @Override
    protected String typeName() {
        return plugin.messageService() == null ? "默认组" : plugin.messageService().message("label.default_profile");
    }

    @Override
    protected DefaultProfile parse(File file, YamlConfiguration configuration) {
        return DefaultProfile.fromMap(configuration);
    }

    @Override
    protected String idOf(DefaultProfile value) {
        return value.id();
    }

    @Override
    protected void afterLoad() {
        mergedProfiles.clear();
        mergedProfiles.addAll(items.values());
        mergedProfiles.sort(Comparator.comparingInt(DefaultProfile::priority).reversed());
    }

    public List<DefaultProfile> mergedProfiles() {
        return List.copyOf(mergedProfiles);
    }
}
