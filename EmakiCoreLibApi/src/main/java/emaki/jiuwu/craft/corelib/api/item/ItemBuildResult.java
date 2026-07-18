package emaki.jiuwu.craft.corelib.api.item;

import java.util.List;

import org.bukkit.inventory.ItemStack;

/** Result of creating or patching an item, including non-fatal diagnostics. */
public final class ItemBuildResult {

    private final ItemStack itemStack;
    private final List<ItemBuildIssue> issues;

    public ItemBuildResult(ItemStack itemStack, List<ItemBuildIssue> issues) {
        this.itemStack = itemStack == null ? null : itemStack.clone();
        this.issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public static ItemBuildResult unavailable(String message) {
        return new ItemBuildResult(null, List.of(ItemBuildIssue.error(null, message)));
    }

    public ItemStack itemStack() {
        return itemStack == null ? null : itemStack.clone();
    }

    public List<ItemBuildIssue> issues() {
        return issues;
    }

    public boolean success() {
        return itemStack != null && issues.stream().noneMatch(issue -> issue.severity() == ItemBuildIssueSeverity.ERROR);
    }

    public boolean hasWarnings() {
        return issues.stream().anyMatch(issue -> issue.severity() == ItemBuildIssueSeverity.WARNING);
    }

    public boolean hasErrors() {
        return issues.stream().anyMatch(issue -> issue.severity() == ItemBuildIssueSeverity.ERROR);
    }

    @Override
    public String toString() {
        return "ItemBuildResult[itemStack=" + itemStack + ", issues=" + issues + "]";
    }
}
