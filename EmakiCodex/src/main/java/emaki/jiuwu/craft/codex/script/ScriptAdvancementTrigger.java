package emaki.jiuwu.craft.codex.script;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.polyglot.Value;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.codex.api.AdvancementTrigger;
import emaki.jiuwu.craft.codex.api.AdvancementTriggerContext;
import emaki.jiuwu.craft.corelib.script.host.ScriptCallbackException;
import emaki.jiuwu.craft.corelib.script.host.ScriptCallbackRunner;
import emaki.jiuwu.craft.corelib.service.MessageService;

final class ScriptAdvancementTrigger implements AdvancementTrigger {

    private final String id;
    private final int priority;
    private final MessageService messages;
    private final ScriptCallbackRunner callbackRunner;
    private final Value advancementsFunction;

    ScriptAdvancementTrigger(String id, int priority, MessageService messages,
            ScriptCallbackRunner callbackRunner, Value advancementsFunction) {
        this.id = id;
        this.priority = priority;
        this.messages = messages;
        this.callbackRunner = callbackRunner;
        this.advancementsFunction = advancementsFunction;
    }

    @Override
    public @NotNull String id() {
        return id;
    }

    @Override
    public int priority() {
        return priority;
    }

    @Override
    public @NotNull Collection<String> advancements(@NotNull AdvancementTriggerContext context) {
        try {
            Value result = callbackRunner.run(advancementsFunction, buildContextExport(context));
            return normalizeResult(result);
        } catch (ScriptCallbackException exception) {
            warnFailure(TriggerScriptLogic.describe(exception));
            return List.of();
        } catch (RuntimeException | LinkageError exception) {
            warnFailure(TriggerScriptLogic.describe(exception));
            return List.of();
        }
    }

    private Map<String, Object> buildContextExport(AdvancementTriggerContext context) {
        Map<String, Object> export = new LinkedHashMap<>();
        export.put("player", context.player());
        export.put("triggerId", context.triggerId());
        export.put("variables", context.variables());
        return export;
    }

    private Collection<String> normalizeResult(Value result) {
        List<Object> elements = flatten(result);
        if (elements == null) {
            warnFailure("unsupported return type");
            return List.of();
        }
        List<String> ids = TriggerScriptLogic.collectStringIds(elements);
        int skipped = elements.size() - ids.size();
        if (skipped > 0) {
            messages.warning("console.script_callback_invalid_element",
                    Map.of("id", id, "count", skipped));
        }
        return ids;
    }

    private List<Object> flatten(Value result) {
        if (result == null || result.isNull()) {
            return List.of();
        }
        if (result.isString()) {
            return List.of(result.asString());
        }
        if (result.hasArrayElements()) {
            long size = result.getArraySize();
            List<Object> elements = new ArrayList<>();
            for (long index = 0; index < size; index++) {
                elements.add(plain(result.getArrayElement(index)));
            }
            return elements;
        }
        if (result.hasIterator()) {
            List<Object> elements = new ArrayList<>();
            for (Object element : result.as(Iterable.class)) {
                elements.add(element instanceof Value value ? plain(value) : element);
            }
            return elements;
        }
        Object host = result.asHostObject();
        if (host instanceof Collection<?> collection) {
            return new ArrayList<>(collection);
        }
        return null;
    }

    private Object plain(Value value) {
        if (value == null || value.isNull()) {
            return null;
        }
        return value.isString() ? value.asString() : value;
    }

    private void warnFailure(String reason) {
        messages.warning("console.script_callback_failed", Map.of("id", id, "reason", reason));
    }
}
