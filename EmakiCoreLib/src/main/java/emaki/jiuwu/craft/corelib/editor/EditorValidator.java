package emaki.jiuwu.craft.corelib.editor;

import java.nio.file.Path;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;

@FunctionalInterface
public interface EditorValidator {

    String validate(YamlTextDocument candidate, String docId, Path source);

    static EditorValidator yamlOnly() {
        return (candidate, docId, source) -> {
            try {
                return YamlFiles.load(candidate.text()) == null ? "unparseable_yaml" : null;
            } catch (RuntimeException failure) {
                return "unparseable_yaml";
            }
        };
    }

    static EditorValidator idStable(String expectedId) {
        return (candidate, docId, source) -> {
            Object raw = candidate.value("id");
            String actual = Texts.normalizeId(raw == null ? "" : Texts.toStringSafe(raw));
            if (actual.isEmpty()) {
                return "definition_parse_failed";
            }
            return expectedId.equals(actual) ? null : "id_changed";
        };
    }

    static EditorValidator allOf(EditorValidator... validators) {
        return (candidate, docId, source) -> {
            for (EditorValidator validator : validators) {
                String problem = validator.validate(candidate, docId, source);
                if (problem != null) {
                    return problem;
                }
            }
            return null;
        };
    }
}
