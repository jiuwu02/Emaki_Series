package emaki.jiuwu.craft.codex.api.model;

import java.util.List;

import org.jetbrains.annotations.NotNull;

/**
 * Read-only view of one advancement page — the tab a group of advancements appears under.
 *
 * <p>The background texture path is not exposed: it is a client-side resource reference that carries no
 * meaning for server-side callers.
 *
 * @param pageId       canonical lowercase page id
 * @param title        MiniMessage text of the page's title
 * @param rootKey      the fully qualified key of this page's root advancement; empty when the page
 *                     declares no root
 * @param advancements every advancement on this page, in load order
 */
public record CodexPageView(@NotNull String pageId,
                            @NotNull String title,
                            @NotNull String rootKey,
                            @NotNull List<AdvancementView> advancements) {

    /**
     * Normalises every reference component so no accessor can return {@code null}.
     *
     * @param pageId       canonical lowercase page id
     * @param title        MiniMessage title
     * @param rootKey      root advancement key
     * @param advancements page members
     */
    public CodexPageView {
        pageId = pageId == null ? "" : pageId;
        title = title == null || title.isBlank() ? pageId : title;
        rootKey = rootKey == null ? "" : rootKey;
        advancements = advancements == null ? List.of() : List.copyOf(advancements);
    }

    /** {@return how many advancements this page contains} */
    public int size() {
        return advancements.size();
    }
}
