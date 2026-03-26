package emaki.jiuwu.craft.corelib.operation;

import java.util.Map;

public record ParsedOperationLine(int lineNumber,
                                  String rawLine,
                                  String operationId,
                                  Map<String, String> arguments,
                                  OperationLineControl control) {
}
