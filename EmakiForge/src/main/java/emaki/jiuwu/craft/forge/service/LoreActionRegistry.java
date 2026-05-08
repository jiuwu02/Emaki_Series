package emaki.jiuwu.craft.forge.service;

import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.assembly.LoreOperationRegistry;
import emaki.jiuwu.craft.corelib.assembly.OperationTemplateRenderer;

/**
 * @deprecated Use {@link LoreOperationRegistry} from CoreLib directly.
 * This class is kept as a thin delegate for backward compatibility during migration.
 */
final class LoreActionRegistry {

    private final LoreOperationRegistry delegate;

    LoreActionRegistry(TextTemplateRenderer templateRenderer) {
        this.delegate = new LoreOperationRegistry(new OperationTemplateRenderer());
    }

    LoreActionRegistry(LoreOperationRegistry delegate) {
        this.delegate = delegate;
    }

    void apply(List<String> lines, Object operations, Map<String, Object> variables) {
        delegate.apply(lines, operations, variables);
    }

    LoreOperationRegistry delegate() {
        return delegate;
    }
}
