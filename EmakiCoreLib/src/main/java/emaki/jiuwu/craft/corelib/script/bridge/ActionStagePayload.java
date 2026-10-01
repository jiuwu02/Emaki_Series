package emaki.jiuwu.craft.corelib.script.bridge;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.graalvm.polyglot.Value;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.text.Texts;

record ActionStagePayload(String id,
        String description,
        String category,
        List<CoreStageParameter> parameters,
        long timeoutMillis,
        Value execute) {

    private static final String DEFAULT_CATEGORY = "script";
    private static final long INVALID_NUMBER = Long.MIN_VALUE;

    static PayloadResult<ActionStagePayload> parse(@Nullable Value payload, long defaultTimeoutMillis) {
        if (payload == null || payload.isNull() || !payload.hasMembers()) {
            return PayloadResult.fail("actions: payload must be an object");
        }
        String rawId = ScriptPayloads.textMember(payload, "id");
        String id = Texts.normalizeId(Texts.toStringSafe(rawId));
        if (Texts.isBlank(id)) {
            return PayloadResult.fail("actions: field 'id' is required");
        }
        String description = Texts.toStringSafe(ScriptPayloads.textMember(payload, "description"));
        String category = Texts.toStringSafe(ScriptPayloads.textMember(payload, "category"));
        if (Texts.isBlank(category)) {
            category = DEFAULT_CATEGORY;
        }
        PayloadResult<List<CoreStageParameter>> parameters = parseParameters(payload);
        if (parameters.failed()) {
            return PayloadResult.fail(parameters.error());
        }
        long timeout = parseTimeout(payload, defaultTimeoutMillis);
        if (timeout <= 0L) {
            return PayloadResult.fail("actions: field 'timeout' of '" + id + "' must be a positive number");
        }
        Value execute = ScriptPayloads.functionMember(payload, "execute");
        if (execute == null) {
            return PayloadResult.fail("actions: field 'execute' of '" + id + "' must be a callable function");
        }
        return PayloadResult.ok(new ActionStagePayload(id, description, category,
                parameters.value(), timeout, execute));
    }

    private static PayloadResult<List<CoreStageParameter>> parseParameters(Value payload) {
        if (!payload.hasMember("parameters") || payload.getMember("parameters").isNull()) {
            return PayloadResult.ok(List.of());
        }
        Value parameters = payload.getMember("parameters");
        if (!parameters.hasArrayElements()) {
            return PayloadResult.fail("actions: field 'parameters' must be an array");
        }
        List<CoreStageParameter> parsed = new ArrayList<>();
        for (long index = 0; index < parameters.getArraySize(); index++) {
            Value entry = parameters.getArrayElement(index);
            if (entry == null || entry.isNull() || !entry.hasMembers()) {
                return PayloadResult.fail("actions: parameter at index " + index + " must be an object");
            }
            String name = Texts.toStringSafe(ScriptPayloads.textMember(entry, "name")).trim();
            if (Texts.isBlank(name)) {
                return PayloadResult.fail("actions: parameter at index " + index + " is missing 'name'");
            }
            CoreStageParameterType type = parseType(entry);
            if (type == null) {
                String rawType = Texts.toStringSafe(ScriptPayloads.textMember(entry, "type"));
                return PayloadResult.fail("actions: parameter '" + name + "' declares invalid type '"
                        + rawType + "'");
            }
            boolean required = parseBoolean(entry, "required");
            String defaultValue = Texts.toStringSafe(ScriptPayloads.textMember(entry, "default"));
            String description = Texts.toStringSafe(ScriptPayloads.textMember(entry, "description"));
            parsed.add(new CoreStageParameter(name.toLowerCase(Locale.ROOT), type, required, defaultValue,
                    false, description));
        }
        return PayloadResult.ok(List.copyOf(parsed));
    }

    private static CoreStageParameterType parseType(Value entry) {
        String rawType = ScriptPayloads.textMember(entry, "type");
        if (Texts.isBlank(rawType)) {
            return CoreStageParameterType.STRING;
        }
        String normalized = rawType.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        try {
            return CoreStageParameterType.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static boolean parseBoolean(Value entry, String member) {
        if (!entry.hasMember(member)) {
            return false;
        }
        Value value = entry.getMember(member);
        if (value == null || value.isNull()) {
            return false;
        }
        try {
            return value.asBoolean();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static long parseTimeout(Value payload, long defaultTimeoutMillis) {
        Long timeout = ScriptPayloads.longMember(payload, "timeout");
        if (timeout == null) {
            return defaultTimeoutMillis;
        }
        if (timeout == INVALID_NUMBER) {
            return 0L;
        }
        return timeout <= 0L ? defaultTimeoutMillis : timeout;
    }
}
