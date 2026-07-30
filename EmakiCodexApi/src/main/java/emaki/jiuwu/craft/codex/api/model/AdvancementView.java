package emaki.jiuwu.craft.codex.api.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Read-only view of one advancement defined in EmakiCodex.
 *
 * <p>Deliberately narrower than EmakiCodex's internal {@code AdvancementDefinition}: the parent localId
 * field, action scripts, trigger conditions, and GUI coordinates are not exposed. This view carries only
 * what is stable and useful to third parties.
 *
 * @param key         the fully qualified advancement key in the form {@code namespace:path}
 * @param nodeId      the local advancement id within its page
 * @param pageId      the id of the page this advancement belongs to
 * @param title       MiniMessage text of the advancement's title
 * @param description MiniMessage text of the advancement's description
 * @param frame       the display frame type
 * @param hidden      whether this advancement is hidden from the advancement screen until obtained
 * @param showToast   whether completing this advancement fires a toast notification
 * @param announce    whether completing this advancement is announced in chat
 * @param root        whether this advancement is the root of its page's tree
 * @param parentKey   the fully qualified key of this advancement's parent, or {@code null} for roots
 */
public record AdvancementView(@NotNull String key,
                              @NotNull String nodeId,
                              @NotNull String pageId,
                              @NotNull String title,
                              @NotNull String description,
                              @NotNull AdvancementFrameType frame,
                              boolean hidden,
                              boolean showToast,
                              boolean announce,
                              boolean root,
                              @Nullable String parentKey) {

    /**
     * Normalises every reference component so no accessor except {@code parentKey} can return
     * {@code null}.
     *
     * @param key         fully qualified advancement key
     * @param nodeId      local advancement id
     * @param pageId      owning page id
     * @param title       MiniMessage title
     * @param description MiniMessage description
     * @param frame       display frame type
     * @param hidden      whether hidden until obtained
     * @param showToast   whether to show a toast
     * @param announce    whether to announce in chat
     * @param root        whether this is a root node
     * @param parentKey   parent key, or {@code null}
     */
    public AdvancementView {
        key = key == null ? "" : key;
        nodeId = nodeId == null ? "" : nodeId;
        pageId = pageId == null ? "" : pageId;
        title = title == null ? nodeId : title;
        description = description == null ? "" : description;
        if (frame == null) {
            frame = AdvancementFrameType.TASK;
        }
        parentKey = parentKey == null || parentKey.isBlank() ? null : parentKey;
    }
}
