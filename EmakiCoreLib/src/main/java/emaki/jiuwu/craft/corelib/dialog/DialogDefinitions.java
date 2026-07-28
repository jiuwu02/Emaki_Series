package emaki.jiuwu.craft.corelib.dialog;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;

/**
 * 把一段 YAML 解析为 {@link DialogDefinition}。
 *
 * <p>与 {@link DialogLoader} 共用同一套键，因此写在业务插件配置里的内联对话框块
 * 和放在对话框目录中的独立文件语义完全一致。
 *
 * <p>校验失败通过 {@code issues} 上报文本，由调用方决定日志前缀与降级方式；
 * 解析器本身不持有插件实例，也不写日志。
 */
public final class DialogDefinitions {

    private DialogDefinitions() {
    }

    /**
     * 解析一段内联对话框配置。
     *
     * <p>与目录加载不同，内联块的 id 由调用方给出：它只用于日志与提交回调标识，
     * 配置里不需要重复声明。
     *
     * @param id      对话框 id，由调用方提供
     * @param section 配置段；为空返回 {@code null}
     * @param issues  校验问题接收者，可为 {@code null}
     * @return 解析结果；配置为空或校验失败时返回 {@code null}
     */
    public static DialogDefinition parse(String id, YamlSection section, Consumer<String> issues) {
        if (section == null || section.isEmpty()) {
            return null;
        }
        return parse(id, (Object) section, issues);
    }

    /**
     * 解析一段内联对话框配置。
     *
     * @param id      对话框 id，由调用方提供
     * @param mapping 配置映射；为空返回 {@code null}
     * @param issues  校验问题接收者，可为 {@code null}
     * @return 解析结果；配置为空或校验失败时返回 {@code null}
     */
    public static DialogDefinition parse(String id, Object mapping, Consumer<String> issues) {
        if (mapping == null) {
            return null;
        }
        String normalizedId = Texts.normalizeId(id);
        if (Texts.isBlank(normalizedId)) {
            report(issues, "missing or invalid id.");
            return null;
        }
        String title = ConfigNodes.string(mapping, "title", "");
        if (Texts.isBlank(title)) {
            report(issues, "title is required.");
            return null;
        }
        DialogDefinition.Type type = DialogDefinition.Type.parse(
                ConfigNodes.string(mapping, "type", null), DialogDefinition.Type.NOTICE);
        List<DialogDefinition.Button> buttons = parseButtons(ConfigNodes.get(mapping, "buttons"));
        if (type == DialogDefinition.Type.CONFIRMATION && buttons.size() < 2) {
            report(issues, "confirmation type requires two buttons.");
            return null;
        }
        return new DialogDefinition(
                normalizedId,
                type,
                title,
                ConfigNodes.string(mapping, "external_title", null),
                ConfigNodes.bool(mapping, "can_close_with_escape", true),
                ConfigNodes.bool(mapping, "pause", false),
                DialogDefinition.AfterAction.parse(
                        ConfigNodes.string(mapping, "after_action", null),
                        DialogDefinition.AfterAction.CLOSE),
                parseBody(ConfigNodes.get(mapping, "body")),
                parseInputs(ConfigNodes.get(mapping, "inputs")),
                buttons,
                parseButton(ConfigNodes.get(mapping, "exit_button")),
                Numbers.tryParseInt(ConfigNodes.get(mapping, "columns"), 2)
        );
    }

    /** 解析正文条目。 */
    public static List<DialogDefinition.Body> parseBody(Object raw) {
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

    /** 解析输入控件。 */
    public static List<DialogDefinition.Input> parseInputs(Object raw) {
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

    private static List<DialogDefinition.Input.Option> parseOptions(Object raw) {
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

    /** 解析按钮列表。 */
    public static List<DialogDefinition.Button> parseButtons(Object raw) {
        List<DialogDefinition.Button> result = new ArrayList<>();
        for (Object entry : ConfigNodes.asObjectList(raw)) {
            DialogDefinition.Button button = parseButton(entry);
            if (button != null) {
                result.add(button);
            }
        }
        return result;
    }

    /** 解析单个按钮；{@code raw} 为空或缺少 label 时返回 {@code null}。 */
    public static DialogDefinition.Button parseButton(Object raw) {
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

    private static DialogDefinition.Action parseAction(Object raw) {
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

    private static void report(Consumer<String> issues, String message) {
        if (issues != null) {
            issues.accept(message);
        }
    }

    private static int intOf(Object raw, String key) {
        return Numbers.tryParseInt(ConfigNodes.get(raw, key), 0);
    }

    private static boolean boolOf(Object raw, String key, boolean fallback) {
        return ConfigNodes.bool(raw, key, fallback);
    }

    private static float floatOf(Object raw, String key, float fallback) {
        Double value = Numbers.tryParseDouble(ConfigNodes.get(raw, key), null);
        return value == null ? fallback : value.floatValue();
    }
}
