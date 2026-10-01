package emaki.jiuwu.craft.item.script;

import org.graalvm.polyglot.Value;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.item.model.EmakiItemDefinitionParser;

final class ItemScriptPayloads {

    private ItemScriptPayloads() {
    }

    static ItemScriptPayload from(Value payload) {
        if (payload == null || payload.isNull() || !payload.hasMembers()) {
            throw new IllegalArgumentException("payload must be an effect type object");
        }
        Value idValue = payload.getMember("id");
        if (isAbsent(idValue)) {
            throw new IllegalArgumentException("id is required");
        }
        if (!idValue.isString()) {
            throw new IllegalArgumentException("id must be a string");
        }
        String id = normalizeId(idValue.asString());
        if (id.isBlank()) {
            throw new IllegalArgumentException("id is blank");
        }
        if (isReservedId(id)) {
            throw new IllegalArgumentException("id '" + id + "' is a reserved built-in effect type");
        }
        Value parseFn = payload.getMember("parse");
        if (isAbsent(parseFn) || !parseFn.canExecute()) {
            throw new IllegalArgumentException("parse must be a function");
        }
        Value applyFn = payload.getMember("apply");
        if (isAbsent(applyFn) || !applyFn.canExecute()) {
            throw new IllegalArgumentException("apply must be a function");
        }
        Value clearFn = payload.getMember("clear");
        if (isAbsent(clearFn)) {
            clearFn = null;
        } else if (!clearFn.canExecute()) {
            throw new IllegalArgumentException("clear must be a function when provided");
        }
        return new ItemScriptPayload(id, parseFn, clearFn, applyFn);
    }

    static String tryExtractId(Value payload) {
        if (payload == null || !payload.hasMembers()) {
            return null;
        }
        Value idValue = payload.getMember("id");
        if (isAbsent(idValue) || !idValue.isString()) {
            return null;
        }
        return idValue.asString();
    }

    static String normalizeId(String raw) {
        return Texts.normalizeId(raw);
    }

    static boolean isReservedId(String normalizedId) {
        return EmakiItemDefinitionParser.BUILT_IN_EFFECT_TYPES.contains(normalizedId);
    }

    static boolean isAbsent(Value value) {
        return value == null || value.isNull();
    }
}
