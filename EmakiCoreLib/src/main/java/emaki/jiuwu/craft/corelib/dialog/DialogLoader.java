package emaki.jiuwu.craft.corelib.dialog;

import java.io.File;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlDirectoryLoader;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.api.dialog.DialogDefinition;
import emaki.jiuwu.craft.corelib.log.LogTags;
import emaki.jiuwu.craft.corelib.log.LogTag;
import java.util.Map;
import java.util.logging.Level;

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
            LogTags.send(plugin, LogTag.DIALOG, Level.WARNING, "dialog.skip_file_missing_or_invalid_id", Map.of("file", file.getName()));
            return null;
        }
        return DialogDefinitions.parse(id, configuration,
                issue -> LogTags.send(plugin, LogTag.DIALOG, Level.WARNING, "dialog.skip_invalid_definition", Map.of("id", id, "issue", issue)));
    }
}
