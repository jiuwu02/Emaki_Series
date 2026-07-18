package emaki.jiuwu.craft.corelib.api.item;

import java.util.Objects;

/** One warning or error produced while creating or patching an item. */
public final class ItemBuildIssue {

    private final ItemBuildIssueSeverity severity;
    private final String componentId;
    private final String message;

    public ItemBuildIssue(ItemBuildIssueSeverity severity, String componentId, String message) {
        this.severity = Objects.requireNonNull(severity, "severity");
        this.componentId = componentId == null || componentId.isBlank() ? null : PlainItemData.componentId(componentId);
        this.message = message == null ? "" : message;
    }

    public static ItemBuildIssue warning(String componentId, String message) {
        return new ItemBuildIssue(ItemBuildIssueSeverity.WARNING, componentId, message);
    }

    public static ItemBuildIssue error(String componentId, String message) {
        return new ItemBuildIssue(ItemBuildIssueSeverity.ERROR, componentId, message);
    }

    public ItemBuildIssueSeverity severity() {
        return severity;
    }

    public String componentId() {
        return componentId;
    }

    public String message() {
        return message;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ItemBuildIssue issue)) {
            return false;
        }
        return severity == issue.severity
                && Objects.equals(componentId, issue.componentId)
                && message.equals(issue.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(severity, componentId, message);
    }

    @Override
    public String toString() {
        return "ItemBuildIssue[severity=" + severity + ", componentId=" + componentId + ", message=" + message + "]";
    }
}
