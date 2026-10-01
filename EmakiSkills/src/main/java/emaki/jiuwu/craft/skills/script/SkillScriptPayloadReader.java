package emaki.jiuwu.craft.skills.script;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.polyglot.Value;

final class SkillScriptPayloadReader {

    private SkillScriptPayloadReader() {
    }

    static SkillScriptPayload read(Value payload) {
        if (payload == null || !payload.hasMembers()) {
            return SkillScriptPayload.EMPTY;
        }
        return new SkillScriptPayload(
                stringOf(member(payload, "id")),
                stringOf(member(payload, "displayName")),
                listOf(member(payload, "description")),
                stringOf(member(payload, "iconMaterial")),
                stringOf(member(payload, "activationType")),
                listOf(member(payload, "passiveTriggers")),
                longOf(member(payload, "cooldownTicks")),
                longOf(member(payload, "globalCooldownTicks")),
                listOf(member(payload, "tags")),
                listOf(member(payload, "loreAliases")),
                stringOf(member(payload, "pdcSkillId")),
                stringOf(member(payload, "uiCategory")),
                (int) longOf(member(payload, "sortOrder")),
                booleanOf(member(payload, "showInSlots")),
                booleanOf(member(payload, "enabled")),
                phaseMap(member(payload, "scriptLines")),
                phaseMap(member(payload, "scriptConditions")),
                stringOf(member(payload, "mythicSkill")));
    }

    private static Value member(Value payload, String key) {
        try {
            return payload.getMember(key);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static Map<String, List<String>> phaseMap(Value value) {
        if (value == null || value.isNull() || !value.hasMembers()) {
            return Map.of();
        }
        Map<String, List<String>> result = new LinkedHashMap<>();
        try {
            for (String key : value.getMemberKeys()) {
                result.put(key, listOf(value.getMember(key)));
            }
        } catch (RuntimeException exception) {
            return result;
        }
        return result;
    }

    private static List<String> listOf(Value value) {
        if (value == null || value.isNull()) {
            return List.of();
        }
        if (value.isString()) {
            String single = value.asString();
            return single.isBlank() ? List.of() : List.of(single);
        }
        if (value.hasArrayElements()) {
            List<String> result = new ArrayList<>();
            long size = value.getArraySize();
            for (long index = 0; index < size; index++) {
                String element = stringOf(value.getArrayElement(index));
                if (!element.isBlank()) {
                    result.add(element);
                }
            }
            return List.copyOf(result);
        }
        return List.of();
    }

    private static String stringOf(Value value) {
        if (value == null || value.isNull()) {
            return "";
        }
        if (value.isString()) {
            return value.asString();
        }
        if (value.isHostObject()) {
            Object host = value.asHostObject();
            return host == null ? "" : String.valueOf(host);
        }
        return value.toString();
    }

    private static long longOf(Value value) {
        if (value == null || value.isNull()) {
            return 0L;
        }
        if (value.isNumber()) {
            return (long) value.asDouble();
        }
        if (value.isBoolean()) {
            return value.asBoolean() ? 1L : 0L;
        }
        return 0L;
    }

    private static Boolean booleanOf(Value value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isNumber()) {
            return value.asDouble() != 0.0D;
        }
        if (value.isString()) {
            return !value.asString().isBlank();
        }
        return Boolean.TRUE;
    }
}
