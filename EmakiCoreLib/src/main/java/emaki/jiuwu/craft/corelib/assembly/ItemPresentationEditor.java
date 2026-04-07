package emaki.jiuwu.craft.corelib.assembly;

import java.util.List;
import java.util.Map;

import org.bukkit.inventory.ItemStack;

public final class ItemPresentationEditor {

    private final ItemPresentationCompiler compiler;
    private final ItemRenderService renderService = new ItemRenderService();

    public ItemPresentationEditor(ItemPresentationCompiler compiler) {
        this.compiler = compiler == null ? new ItemPresentationCompiler() : compiler;
    }

    public EditResult applyNameOperation(ItemStack itemStack, Map<String, Object> operation) {
        return apply(itemStack, List.of(operation), List.of());
    }

    public EditResult applyLoreOperation(ItemStack itemStack, Map<String, Object> operation) {
        return apply(itemStack, List.of(), List.of(operation));
    }

    public EditResult apply(ItemStack itemStack, Object nameOperations, Object loreOperations) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return new EditResult(null, List.of());
        }
        PresentationCompileResult compileResult = compiler.compile(nameOperations, loreOperations, 0, "action_item_edit");
        ItemStack edited = itemStack.clone();
        if (!compileResult.entries().isEmpty()) {
            EmakiItemLayerSnapshot snapshot = new EmakiItemLayerSnapshot(
                    "action_item_edit",
                    1,
                    Map.of(),
                    List.of(),
                    compileResult.entries()
            );
            renderService.renderItem(edited, List.of(snapshot));
        }
        return new EditResult(edited, compileResult.issues());
    }

    public record EditResult(ItemStack itemStack, List<PresentationCompileIssue> issues) {

        public EditResult {
            issues = issues == null || issues.isEmpty() ? List.of() : List.copyOf(issues);
        }

        public boolean success() {
            return itemStack != null && issues.isEmpty();
        }
    }
}
