package emaki.jiuwu.craft.codex.apiimpl;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.codex.EmakiCodexPlugin;
import emaki.jiuwu.craft.codex.advancement.AdvancementRegistrar;
import emaki.jiuwu.craft.codex.advancement.model.AdvancementDefinition;
import emaki.jiuwu.craft.codex.advancement.model.AdvancementFrame;
import emaki.jiuwu.craft.codex.advancement.model.AdvancementPage;
import emaki.jiuwu.craft.codex.api.CodexCatalog;
import emaki.jiuwu.craft.codex.api.model.AdvancementFrameType;
import emaki.jiuwu.craft.codex.api.model.AdvancementView;
import emaki.jiuwu.craft.codex.api.model.CodexPageView;
import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.text.Texts;

/**
 * {@link CodexCatalog} 的运行时实现。
 *
 * <p>完成状态直接读玩家的原版 advancement 进度，而不是 EmakiCodex 自己的记录——因为 EmakiCodex
 * 注册的就是真实成就，任何来源的授予都应被同等看见。
 */
public final class DefaultCodexCatalog implements CodexCatalog {

    private final EmakiCodexPlugin plugin;

    public DefaultCodexCatalog(EmakiCodexPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull List<AdvancementView> advancements() {
        AdvancementRegistrar registrar = plugin.isEnabled() ? plugin.advancementRegistrar() : null;
        if (registrar == null) {
            return List.of();
        }
        return registrar.registeredNodes().stream().map(DefaultCodexCatalog::toView).toList();
    }

    @Override
    public @NotNull Optional<AdvancementView> advancement(@Nullable String advancementId) {
        AdvancementRegistrar registrar = plugin.isEnabled() ? plugin.advancementRegistrar() : null;
        if (registrar == null || Texts.isBlank(advancementId)) {
            return Optional.empty();
        }
        NamespacedKey key = registrar.resolveKey(advancementId);
        if (key == null) {
            return Optional.empty();
        }
        String keyString = key.toString();
        return registrar.registeredNodes().stream()
                .filter(node -> keyString.equals(node.key().toString()))
                .findFirst()
                .map(DefaultCodexCatalog::toView);
    }

    @Override
    public @NotNull List<String> pageIds() {
        AdvancementRegistrar registrar = plugin.isEnabled() ? plugin.advancementRegistrar() : null;
        if (registrar == null) {
            return List.of();
        }
        return registrar.registeredNodes().stream()
                .map(node -> node.page() == null ? "" : Texts.lower(node.page().pageId()))
                .filter(id -> !id.isEmpty())
                .distinct()
                .sorted()
                .toList();
    }

    @Override
    public @NotNull Optional<CodexPageView> page(@Nullable String pageId) {
        AdvancementRegistrar registrar = plugin.isEnabled() ? plugin.advancementRegistrar() : null;
        if (registrar == null || Texts.isBlank(pageId)) {
            return Optional.empty();
        }
        String normalized = Texts.lower(pageId);
        List<AdvancementRegistrar.RegisteredNode> members = registrar.registeredNodes().stream()
                .filter(node -> node.page() != null && normalized.equals(Texts.lower(node.page().pageId())))
                .toList();
        if (members.isEmpty()) {
            return Optional.empty();
        }
        AdvancementPage page = members.getFirst().page();
        String rootKey = members.stream()
                .filter(node -> node.definition() != null && node.definition().isRoot())
                .map(node -> node.key().toString())
                .findFirst()
                .orElse("");
        return Optional.of(new CodexPageView(normalized,
                Texts.toStringSafe(page.title()),
                rootKey,
                members.stream().map(DefaultCodexCatalog::toView).toList()));
    }

    @Override
    public int count() {
        AdvancementRegistrar registrar = plugin.isEnabled() ? plugin.advancementRegistrar() : null;
        return registrar == null ? 0 : registrar.size();
    }

    @Override
    public @NotNull EmakiResult<Boolean> completed(@Nullable UUID playerId, @Nullable String advancementId) {
        if (playerId == null) {
            return EmakiResult.invalidInput("codex.error.no_player");
        }
        if (Texts.isBlank(advancementId)) {
            return EmakiResult.invalidInput("codex.error.no_advancement_id");
        }
        AdvancementRegistrar registrar = plugin.isEnabled() ? plugin.advancementRegistrar() : null;
        // Ready, not just non-null: "unknown_advancement" below is judged against the registrar's
        // contents, so answering it mid-reload would report a config error that is not one.
        if (registrar == null || !plugin.contentReady()) {
            return EmakiResult.unavailable();
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return EmakiResult.targetOffline();
        }
        if (plugin.threadOwnership() == null || !plugin.threadOwnership().isEntityOwned(player)) {
            return EmakiResult.wrongThread();
        }
        NamespacedKey key = registrar.resolveKey(advancementId);
        if (key == null) {
            return EmakiResult.notFound("codex.error.unknown_advancement");
        }
        Advancement advancement = Bukkit.getAdvancement(key);
        if (advancement == null) {
            return EmakiResult.notFound("codex.error.advancement_missing_on_server");
        }
        return EmakiResult.success(player.getAdvancementProgress(advancement).isDone());
    }

    /**
     * 把已注册节点映射为只读视图。
     *
     * @param node 已注册节点
     * @return 只读视图
     */
    static AdvancementView toView(AdvancementRegistrar.RegisteredNode node) {
        AdvancementDefinition definition = node.definition();
        String pageId = node.page() == null ? "" : Texts.lower(node.page().pageId());
        if (definition == null) {
            return new AdvancementView(node.key().toString(), "", pageId, "", "",
                    AdvancementFrameType.TASK, false, false, false, false, node.parentKey());
        }
        return new AdvancementView(node.key().toString(),
                Texts.lower(definition.id()),
                pageId,
                Texts.toStringSafe(definition.title()),
                Texts.toStringSafe(definition.description()),
                toFrameType(definition.frame()),
                definition.hidden(),
                definition.showToast(),
                definition.announce(),
                definition.isRoot(),
                node.parentKey());
    }

    /**
     * 把 runtime 帧类型映射为 API 枚举。
     *
     * @param frame runtime 帧类型
     * @return API 枚举；入参为 null 时回落 TASK
     */
    static AdvancementFrameType toFrameType(AdvancementFrame frame) {
        if (frame == null) {
            return AdvancementFrameType.TASK;
        }
        return switch (frame) {
            case GOAL -> AdvancementFrameType.GOAL;
            case CHALLENGE -> AdvancementFrameType.CHALLENGE;
            default -> AdvancementFrameType.TASK;
        };
    }
}
