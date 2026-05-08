package emaki.jiuwu.craft.forge.service;

import java.util.Map;

import emaki.jiuwu.craft.corelib.assembly.LocalNameState;
import emaki.jiuwu.craft.corelib.assembly.NameOperationRegistry;
import emaki.jiuwu.craft.corelib.assembly.OperationTemplateRenderer;

/**
 * @deprecated Use {@link NameOperationRegistry} from CoreLib directly.
 * This class is kept as a thin delegate for backward compatibility during migration.
 */
final class NameModificationRegistry {

    private final NameOperationRegistry delegate;

    NameModificationRegistry(TextTemplateRenderer templateRenderer) {
        this.delegate = new NameOperationRegistry(new OperationTemplateRenderer());
    }

    NameModificationRegistry(NameOperationRegistry delegate) {
        this.delegate = delegate;
    }

    void apply(LocalNameState state, Object operations, Map<String, Object> variables) {
        delegate.apply(state, operations, variables);
    }

    NameOperationRegistry delegate() {
        return delegate;
    }
}
