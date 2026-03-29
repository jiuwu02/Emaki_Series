package emaki.jiuwu.craft.corelib.action;

public record ActionStepResult(int lineNumber,
                                  String rawLine,
                                  String actionId,
                                  ActionResult result) {
}
