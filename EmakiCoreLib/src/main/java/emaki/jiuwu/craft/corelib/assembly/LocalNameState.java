package emaki.jiuwu.craft.corelib.assembly;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.text.Texts;

public final class LocalNameState {

    private BaseNamePolicy baseNamePolicy = BaseNamePolicy.SOURCE_EFFECTIVE_NAME;
    private String baseNameTemplate = "";
    private final List<String> prefixes = new ArrayList<>();
    private final List<String> postfixes = new ArrayList<>();

    public BaseNamePolicy baseNamePolicy() {
        return baseNamePolicy;
    }

    public String baseNameTemplate() {
        return baseNameTemplate;
    }

    public List<String> prefixes() {
        return prefixes;
    }

    public List<String> postfixes() {
        return postfixes;
    }

    public void replaceBase(String value) {
        baseNamePolicy = BaseNamePolicy.EXPLICIT_TEMPLATE;
        baseNameTemplate = Texts.toStringSafe(value);
        prefixes.clear();
        postfixes.clear();
    }

    public void addPrefix(String value) {
        prefixes.add(0, Texts.toStringSafe(value));
    }

    public void addPostfix(String value) {
        postfixes.add(Texts.toStringSafe(value));
    }

    public void applyRegexReplace(String regex, String replacement, Map<String, Object> variables) {
        if (Texts.isBlank(regex) || baseNamePolicy != BaseNamePolicy.EXPLICIT_TEMPLATE) {
            return;
        }
        baseNameTemplate = OperationTemplateRenderer.replaceRegex(baseNameTemplate, regex, replacement, variables);
    }
}
