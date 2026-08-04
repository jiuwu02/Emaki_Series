package emaki.jiuwu.craft.corelib.dialog;

import java.io.File;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlDirectoryLoader;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.api.dialog.DialogDefinition;

/**
 * 从对话框目录加载 {@link DialogDefinition}。
 *
 * <p>目录名由 CoreLib 配置 {@code dialog.directory} 决定，因此在构造时传入。
 */
public final class DialogLoader extends YamlDirectoryLoader<DialogDefinition> {

    private final String directoryName;

    public DialogLoader(JavaPlugin plugin, String directoryName) {
        super(plugin);
        this.directoryName = Texts.isBlank(directoryName) ? "dialogs" : directoryName.trim();
    }

    @Override
    protected String directoryName() {
        return directoryName;
    }

    @Override
    protected String typeName() {
        return "dialog";
    }

    @Override
    protected String idOf(DialogDefinition value) {
        return value.id();
    }

    @Override
    protected DialogDefinition parse(File file, YamlSection configuration) {
        if (configuration == null || configuration.isEmpty()) {
            return null;
        }
        String id = Texts.normalizeId(configuration.getString("id"));
        if (Texts.isBlank(id)) {
            plugin.getLogger().warning("[dialog] Skipping " + file.getName() + ": missing or invalid id.");
            return null;
        }
        return DialogDefinitions.parse(id, configuration,
                issue -> plugin.getLogger().warning("[dialog] Skipping " + id + ": " + issue));
    }
}
