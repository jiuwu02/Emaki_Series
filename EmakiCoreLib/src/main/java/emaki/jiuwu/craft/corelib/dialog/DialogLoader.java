package emaki.jiuwu.craft.corelib.dialog;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlDirectoryLoader;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;

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
        String title = configuration.getString("title", "");
        if (Texts.isBlank(title)) {
            plugin.getLogger().warning("[dialog] Skipping " + id + ": title is required.");
            return null;
        }
        DialogDefinition.Type type = DialogDefinition.Type.parse(
                configuration.getString("type"), DialogDefinition.Type.NOTICE);
        List<DialogDefinition.Button> buttons = parseButtons(configuration.get("buttons"));
        if (type == DialogDefinition.Type.CONFIRMATION && buttons.size() < 2) {
            plugin.getLogger().warning("[dialog] Skipping " + id + ": confirmation type requires two buttons.");
            return null;
        }
        return new DialogDefinition(
                id,
                type,
                title,
                configuration.getString("external_title", null),
                configuration.getBoolean("can_close_with_escape", true),
                configuration.getBoolean("pause", false),
                DialogDefinition.AfterAction.parse(
                        configuration.getString("after_action"), DialogDefinition.AfterAction.CLOSE),
                parseBody(configuration.get("body")),
                parseInputs(configuration.get("inputs")),
                buttons,
                parseButton(configuration.get("exit_button")),
                configuration.getInt("columns", 2)
        );
    }

    private List<DialogDefinition.Body> parseBody(Object raw) {
        List<DialogDefinition.Body> result = new ArrayList<>();
        for (Object entry : ConfigNodes.asObjectList(raw)) {
            if (entry instanceof String text) {
                if (Texts.isNotBlank(text)) {
                    result.add(new DialogDefinition.Body(text, null, 0));
                }
                continue;
            }
            String item = ConfigNodes.string(entry, "item", null);
            String text = ConfigNodes.string(entry, "text", null);
            if (Texts.isBlank(item) && Texts.isBlank(text)) {
                continue;
            }
            result.add(new DialogDefinition.Body(text, item, intOf(entry, "width")));
        }
        return result;
    }

    private List<DialogDefinition.Input> parseInputs(Object raw) {
        List<DialogDefinition.Input> result = new ArrayList<>();
        for (Object entry : ConfigNodes.asObjectList(raw)) {
            String key = Texts.normalizeId(ConfigNodes.string(entry, "key", null));
            if (Texts.isBlank(key)) {
                continue;
            }
            DialogDefinition.InputType inputType = DialogDefinition.InputType.parse(
                    ConfigNodes.string(entry, "type", null), DialogDefinition.InputType.TEXT);
            result.add(new DialogDefinition.Input(
                    inputType,
                    key,
                    ConfigNodes.string(entry, "label", key),
                    boolOf(entry, "label_visible", true),
                    ConfigNodes.string(entry, "initial", ""),
                    intOf(entry, "max_length"),
                    intOf(entry, "width"),
                    floatOf(entry, "start", 0F),
                    floatOf(entry, "end", 100F),
                    floatOf(entry, "step", 1F),
                    boolOf(entry, "initial_boolean", false),
                    ConfigNodes.string(entry, "on_true", "true"),
                    ConfigNodes.string(entry, "on_false", "false"),
                    parseOptions(ConfigNodes.get(entry, "options"))
            ));
        }
        return result;
    }

    private List<DialogDefinition.Input.Option> parseOptions(Object raw) {
        List<DialogDefinition.Input.Option> result = new ArrayList<>();
        for (Object entry : ConfigNodes.asObjectList(raw)) {
            if (entry instanceof String text) {
                if (Texts.isNotBlank(text)) {
                    result.add(new DialogDefinition.Input.Option(text, text, false));
                }
                continue;
            }
            String optionId = ConfigNodes.string(entry, "id", null);
            if (Texts.isBlank(optionId)) {
                continue;
            }
            result.add(new DialogDefinition.Input.Option(
                    optionId,
                    ConfigNodes.string(entry, "display", optionId),
                    boolOf(entry, "initial", false)
            ));
        }
        return result;
    }

    private List<DialogDefinition.Button> parseButtons(Object raw) {
        List<DialogDefinition.Button> result = new ArrayList<>();
        for (Object entry : ConfigNodes.asObjectList(raw)) {
            DialogDefinition.Button button = parseButton(entry);
            if (button != null) {
                result.add(button);
            }
        }
        return result;
    }

    private DialogDefinition.Button parseButton(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof String text) {
            return Texts.isBlank(text) ? null : new DialogDefinition.Button(text, null, 0, null);
        }
        String label = ConfigNodes.string(raw, "label", null);
        if (Texts.isBlank(label)) {
            return null;
        }
        return new DialogDefinition.Button(
                label,
                ConfigNodes.string(raw, "tooltip", null),
                intOf(raw, "width"),
                parseAction(ConfigNodes.get(raw, "action"))
        );
    }

    private DialogDefinition.Action parseAction(Object raw) {
        if (raw == null) {
            return null;
        }
        DialogDefinition.ActionType type = DialogDefinition.ActionType.parse(
                ConfigNodes.string(raw, "type", null), DialogDefinition.ActionType.NONE);
        String value = ConfigNodes.string(raw, "template", null);
        if (Texts.isBlank(value)) {
            value = ConfigNodes.string(raw, "value", null);
        }
        if (type != DialogDefinition.ActionType.NONE && Texts.isBlank(value)) {
            return null;
        }
        return new DialogDefinition.Action(type, value);
    }

    private int intOf(Object raw, String key) {
        return Numbers.tryParseInt(ConfigNodes.get(raw, key), 0);
    }

    private boolean boolOf(Object raw, String key, boolean fallback) {
        return ConfigNodes.bool(raw, key, fallback);
    }

    private float floatOf(Object raw, String key, float fallback) {
        Double value = Numbers.tryParseDouble(ConfigNodes.get(raw, key), null);
        return value == null ? fallback : value.floatValue();
    }
}
