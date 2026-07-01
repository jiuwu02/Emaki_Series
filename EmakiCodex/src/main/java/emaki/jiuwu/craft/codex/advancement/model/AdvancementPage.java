package emaki.jiuwu.craft.codex.advancement.model;

import java.util.List;
import java.util.Map;

/**
 * A configured advancement page (a vanilla advancement tab).
 *
 * <p>Every page owns exactly one root advancement plus any number of child nodes.
 * The {@code pageId} becomes the namespace path prefix for the underlying vanilla
 * advancement keys registered by EmakiCodex.
 *
 * @param pageId       the page id (also used as the key path prefix)
 * @param title        MiniMessage tab title text
 * @param background   the background texture path (e.g. {@code minecraft:textures/block/stone.png})
 * @param rootId       the local id of the root advancement in {@code advancements}
 * @param advancements the advancement nodes keyed by their local id, in file order
 */
public record AdvancementPage(String pageId,
        String title,
        String background,
        String rootId,
        Map<String, AdvancementDefinition> advancements) {

    public AdvancementPage {
        advancements = advancements == null ? Map.of() : Map.copyOf(advancements);
    }

    /** {@return the root advancement definition, or {@code null} when misconfigured} */
    public AdvancementDefinition root() {
        return rootId == null ? null : advancements.get(rootId);
    }

    /** {@return every advancement definition in insertion order} */
    public List<AdvancementDefinition> definitions() {
        return List.copyOf(advancements.values());
    }
}
