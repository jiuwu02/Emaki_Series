package emaki.jiuwu.craft.item.editor;

import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.descriptor.CoreActionStageDescriptor;

public final class ActionLineValidator {

    public static final String EMPTY = "editor.action.empty";
    public static final String EMPTY_SEGMENT = "editor.action.empty_segment";
    public static final String UNKNOWN_STAGE = "editor.action.unknown_stage";
    public static final String UNKNOWN_ARGUMENT = "editor.action.unknown_argument";
    public static final String MISSING_ARGUMENT = "editor.action.missing_argument";

    private static final Set<String> CONTROL_KEYWORDS = Set.of(
            "if", "else", "run", "weight", "every", "after", "limit", "keep", "sort_by", "stop", "chance",
            "set", "create_item", "filter", "where", "select");

    private final Function<String, Optional<CoreActionStageDescriptor>> resolver;

    public ActionLineValidator(Function<String, Optional<CoreActionStageDescriptor>> resolver) {
        this.resolver = resolver;
    }

    public static ActionLineValidator coreLib() {
        return new ActionLineValidator(EmakiCoreLibApi::actionStage);
    }

    public String validate(String line) {
        String trimmed = line == null ? "" : line.trim();
        if (trimmed.isEmpty()) {
            return EMPTY;
        }
        for (String segment : trimmed.split("\\|", -1)) {
            String[] tokens = segment.trim().split("\\s+", -1);
            String stageId = tokens[0].toLowerCase(Locale.ROOT);
            if (stageId.isEmpty()) {
                return EMPTY_SEGMENT;
            }
            if (CONTROL_KEYWORDS.contains(stageId)) {
                continue;
            }
            Optional<CoreActionStageDescriptor> descriptor = resolver.apply(stageId);
            if (descriptor.isEmpty()) {
                return UNKNOWN_STAGE;
            }
            String problem = checkParameters(descriptor.get(), tokens);
            if (problem != null) {
                return problem;
            }
        }
        return null;
    }

    public static String checkParameters(CoreActionStageDescriptor descriptor, String[] tokens) {
        Set<String> provided = new HashSet<>();
        Set<String> declared = new HashSet<>();
        for (CoreStageParameter parameter : descriptor.parameters()) {
            declared.add(parameter.name().toLowerCase(Locale.ROOT));
        }
        for (int index = 1; index < tokens.length; index++) {
            int separator = tokens[index].indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String name = tokens[index].substring(0, separator).trim().toLowerCase(Locale.ROOT);
            if (!declared.contains(name)) {
                return UNKNOWN_ARGUMENT;
            }
            provided.add(name);
        }
        for (CoreStageParameter parameter : descriptor.parameters()) {
            String name = parameter.name().toLowerCase(Locale.ROOT);
            if (parameter.required() && !parameter.positional() && !provided.contains(name)) {
                return MISSING_ARGUMENT;
            }
        }
        return null;
    }
}
