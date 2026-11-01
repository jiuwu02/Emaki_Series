package emaki.jiuwu.craft.corelib.script.bridge;

import org.graalvm.polyglot.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class ScriptPayloads {

    private ScriptPayloads() {
    }

    static @Nullable String text(@Nullable Value value) {
        if (value == null || value.isNull()) {
            return null;
        }
        try {
            return value.asString();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    static @Nullable String textMember(@NotNull Value payload, @NotNull String member) {
        if (!payload.hasMember(member)) {
            return null;
        }
        return text(payload.getMember(member));
    }

    static @Nullable Value functionMember(@NotNull Value payload, @NotNull String member) {
        if (!payload.hasMember(member)) {
            return null;
        }
        Value value = payload.getMember(member);
        if (value == null || value.isNull() || !value.canExecute()) {
            return null;
        }
        return value;
    }

    static @Nullable Long longMember(@NotNull Value payload, @NotNull String member) {
        if (!payload.hasMember(member)) {
            return null;
        }
        Value value = payload.getMember(member);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isNumber()) {
            return Long.MIN_VALUE;
        }
        try {
            return value.asLong();
        } catch (RuntimeException ignored) {
            return Long.MIN_VALUE;
        }
    }

    static @Nullable Integer intMember(@NotNull Value payload, @NotNull String member) {
        Long parsed = longMember(payload, member);
        if (parsed == null || parsed == Long.MIN_VALUE) {
            return null;
        }
        return parsed > Integer.MAX_VALUE || parsed < Integer.MIN_VALUE ? null : parsed.intValue();
    }
}
