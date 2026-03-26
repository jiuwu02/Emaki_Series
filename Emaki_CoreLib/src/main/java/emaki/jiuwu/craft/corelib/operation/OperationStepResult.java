package emaki.jiuwu.craft.corelib.operation;

public record OperationStepResult(int lineNumber,
                                  String rawLine,
                                  String operationId,
                                  OperationResult result) {
}
