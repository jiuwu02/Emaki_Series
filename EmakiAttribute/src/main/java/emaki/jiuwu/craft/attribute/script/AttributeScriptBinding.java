package emaki.jiuwu.craft.attribute.script;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.polyglot.Value;

import emaki.jiuwu.craft.attribute.loader.AttributeRegistry;

public final class AttributeScriptBinding {

    private static final String SOURCE = "script";

    private final AttributeRegistry attributeRegistry;
    private final Object collectorLock = new Object();
    private final List<AttributePayloadParser.PayloadError> errors = new ArrayList<>();
    private final List<String> overrideIds = new ArrayList<>();

    public AttributeScriptBinding(AttributeRegistry attributeRegistry) {
        this.attributeRegistry = attributeRegistry;
    }

    public void register(Value payload) {
        AttributePayloadParser.ParseResult result = AttributePayloadParser.parse(payload);
        if (!result.errors().isEmpty()) {
            synchronized (collectorLock) {
                errors.addAll(result.errors());
            }
            return;
        }
        if (attributeRegistry == null || result.definition() == null) {
            return;
        }
        String id = result.definition().id();
        boolean override = attributeRegistry.get(id) != null;
        if (attributeRegistry.registerRuntime(result.definition(), SOURCE) && override) {
            synchronized (collectorLock) {
                overrideIds.add(id);
            }
        }
    }

    public void reset() {
        synchronized (collectorLock) {
            errors.clear();
            overrideIds.clear();
        }
    }

    public List<AttributePayloadParser.PayloadError> drainErrors() {
        synchronized (collectorLock) {
            if (errors.isEmpty()) {
                return List.of();
            }
            List<AttributePayloadParser.PayloadError> drained = List.copyOf(errors);
            errors.clear();
            return drained;
        }
    }

    public List<String> drainOverrides() {
        synchronized (collectorLock) {
            if (overrideIds.isEmpty()) {
                return List.of();
            }
            List<String> drained = List.copyOf(overrideIds);
            overrideIds.clear();
            return drained;
        }
    }
}
