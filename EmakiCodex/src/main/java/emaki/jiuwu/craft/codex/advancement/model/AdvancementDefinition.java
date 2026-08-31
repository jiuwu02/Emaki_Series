package emaki.jiuwu.craft.codex.advancement.model;

import java.util.List;

public record AdvancementDefinition(String id,
        String icon,
        String title,
        String description,
        AdvancementFrame frame,
        double x,
        double y,
        String parent,
        boolean hidden,
        boolean showToast,
        boolean announce,
        List<String> completeActions,
        List<AdvancementTrigger> triggers) {

    public static final String CRITERION = "codex";

    public AdvancementDefinition {
        completeActions = completeActions == null ? List.of() : List.copyOf(completeActions);
        triggers = triggers == null ? List.of() : List.copyOf(triggers);
    }

    public boolean isRoot() {
        return parent == null || parent.isBlank();
    }

    public boolean hasExplicitPosition() {
        return x != 0.0D || y != 0.0D;
    }
}
