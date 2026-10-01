package emaki.jiuwu.craft.codex.script;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

final class TriggerScriptLogic {

    static final int DEFAULT_PRIORITY = 100;

    private TriggerScriptLogic() {
    }

    static String normalizeId(Object raw) {
        if (!(raw instanceof String text)) {
            return "";
        }
        return text.trim();
    }

    static Integer resolvePriority(Object raw) {
        if (raw == null) {
            return DEFAULT_PRIORITY;
        }
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw instanceof String text) {
            String trimmed = text.trim();
            if (trimmed.isEmpty()) {
                return DEFAULT_PRIORITY;
            }
            try {
                return Integer.valueOf(trimmed);
            } catch (NumberFormatException _) {
                return null;
            }
        }
        return null;
    }

    static String validatePayload(Object idRaw, Object priorityRaw, boolean advancementsCallable) {
        if (normalizeId(idRaw).isEmpty()) {
            return "id_blank";
        }
        if (!advancementsCallable) {
            return "advancements_missing";
        }
        if (resolvePriority(priorityRaw) == null) {
            return "priority_invalid";
        }
        return null;
    }

    static List<String> collectStringIds(Collection<?> elements) {
        if (elements == null || elements.isEmpty()) {
            return List.of();
        }
        List<String> ids = new ArrayList<>(elements.size());
        for (Object element : elements) {
            if (element instanceof String text) {
                ids.add(text);
            }
        }
        return ids;
    }

    static String describe(Throwable exception) {
        String message = exception.getMessage();
        return message != null && !message.isBlank() ? message : exception.toString();
    }
}
