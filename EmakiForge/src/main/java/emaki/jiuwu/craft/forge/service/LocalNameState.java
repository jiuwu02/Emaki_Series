package emaki.jiuwu.craft.forge.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.assembly.BaseNamePolicy;
import emaki.jiuwu.craft.corelib.text.Texts;

final class LocalNameState {

    private BaseNamePolicy baseNamePolicy = BaseNamePolicy.SOURCE_EFFECTIVE_NAME;
    private String baseNameTemplate = "";
    private final List<String> prefixes = new ArrayList<>();
    private final List<String> postfixes = new ArrayList<>();

    BaseNamePolicy baseNamePolicy() {
        return baseNamePolicy;
    }

    String baseNameTemplate() {
        return baseNameTemplate;
    }

    List<String> prefixes() {
        return prefixes;
    }

    List<String> postfixes() {
        return postfixes;
    }

    void replaceBase(String value) {
        baseNamePolicy = BaseNamePolicy.EXPLICIT_TEMPLATE;
        baseNameTemplate = Texts.toStringSafe(value);
        prefixes.clear();
        postfixes.clear();
    }

    void addPrefix(String value) {
        prefixes.add(0, Texts.toStringSafe(value));
    }

    void addPostfix(String value) {
        postfixes.add(Texts.toStringSafe(value));
    }

    void applyRegexReplace(String regex, String replacement, Map<String, Object> variables) {
        if (Texts.isBlank(regex) || baseNamePolicy != BaseNamePolicy.EXPLICIT_TEMPLATE) {
            return;
        }
        baseNameTemplate = TextTemplateRenderer.replaceRegex(baseNameTemplate, regex, replacement, variables);
    }
}
