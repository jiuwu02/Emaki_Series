package emaki.jiuwu.craft.corelib.placeholder;

import emaki.jiuwu.craft.corelib.operation.OperationContext;

public interface PlaceholderResolver {

    String resolve(OperationContext context, String text);
}
