package emaki.jiuwu.craft.corelib.script.js.registration;

import java.util.Map;

import emaki.jiuwu.craft.corelib.text.Texts;

public record JavaScriptRegistrationSnapshot(String owner,
        String scriptPath,
        String id,
        JavaScriptRegistrationType type,
        long registeredAtMillis,
        long registrationDurationMillis,
        String lastError,
        Map<String, Object> metadata) {

    public JavaScriptRegistrationSnapshot {
        owner = Texts.toStringSafe(owner);
        scriptPath = Texts.toStringSafe(scriptPath);
        id = Texts.normalizeId(id);
        registeredAtMillis = Math.max(0L, registeredAtMillis);
        registrationDurationMillis = Math.max(0L, registrationDurationMillis);
        lastError = Texts.toStringSafe(lastError);
        metadata = metadata == null || metadata.isEmpty() ? Map.of() : Map.copyOf(metadata);
    }
}
