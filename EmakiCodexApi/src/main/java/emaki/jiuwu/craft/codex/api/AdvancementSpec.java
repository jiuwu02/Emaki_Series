package emaki.jiuwu.craft.codex.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.codex.api.model.AdvancementFrameType;

/** Narrow, configuration-independent definition for an externally registered advancement. */
public record AdvancementSpec(
        @NotNull String id,
        @NotNull String icon,
        @NotNull String title,
        @NotNull String description,
        @NotNull AdvancementFrameType frame,
        @Nullable String parentKey,
        boolean hidden,
        boolean showToast,
        boolean announce) {

    public AdvancementSpec {
        id = id == null ? "" : id.trim();
        icon = icon == null || icon.isBlank() ? "minecraft:book" : icon.trim();
        title = title == null ? "" : title;
        description = description == null ? "" : description;
        frame = frame == null ? AdvancementFrameType.TASK : frame;
        parentKey = parentKey == null || parentKey.isBlank() ? null : parentKey.trim();
    }
}
