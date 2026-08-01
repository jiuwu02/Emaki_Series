package emaki.jiuwu.craft.codex.action.v2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.codex.EmakiCodexPlugin;
import emaki.jiuwu.craft.codex.advancement.AdvancementRegistrar;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionTarget;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionStage;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStagePlanningContext;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.text.Texts;

/**
 * Grants, revokes, resyncs and resets EmakiCodex advancements on the target.
 *
 * <p>The v2 counterpart of the module's five advancement actions. One behavioural difference from v1: there is
 * no {@code target} argument. Naming the subject is the target flow's job in a pipeline, so
 * {@code player_by_name Steve | codex_grant_advancement advancement=x} replaces {@code target=Steve}, and the
 * v1 default of falling back to the context player is what an omitted source already does. That removes the
 * fourth copy of name-or-UUID resolution from this module.</p>
 *
 * <p>Domain {@code CONTEXT_ENTITY}: every operation reads and writes one player's advancement progress.</p>
 */
public final class CodexAdvancementStage implements CoreActionStage {

    /** Which advancement mutation a stage instance performs. */
    public enum Operation {

        /** Award one advancement by id. */
        GRANT("codex_grant_advancement", "Grants an EmakiCodex advancement to the target."),

        /** Take back one advancement by id. */
        REVOKE("codex_revoke_advancement", "Revokes an EmakiCodex advancement from the target."),

        /** Resend the advancement tree to the client. */
        RESYNC("codex_resync_advancement", "Resends the EmakiCodex advancement tree to the target."),

        /** Revoke every node belonging to one page. */
        RESET_PAGE("codex_reset_page", "Revokes every EmakiCodex advancement on one page for the target."),

        /** Revoke every registered node. */
        RESET_ALL("codex_reset_all", "Revokes every registered EmakiCodex advancement for the target.");

        private final String id;
        private final String description;

        Operation(String id, String description) {
            this.id = id;
            this.description = description;
        }

        /** {@return the pipeline stage id} */
        public String id() {
            return id;
        }
    }

    private final EmakiCodexPlugin plugin;
    private final Operation operation;

    /**
     * Creates a stage.
     *
     * @param plugin owning plugin, source of the advancement services
     * @param operation which mutation this instance performs
     */
    public CodexAdvancementStage(@NotNull EmakiCodexPlugin plugin, @NotNull Operation operation) {
        this.plugin = plugin;
        this.operation = operation;
    }

    @Override
    public @NotNull String id() {
        return operation.id;
    }

    @Override
    public @NotNull String description() {
        return operation.description;
    }

    @Override
    public @NotNull String category() {
        return "codex";
    }

    @Override
    public @NotNull List<CoreStageParameter> parameters() {
        return switch (operation) {
            case GRANT, REVOKE -> List.of(CoreStageParameter.required("advancement",
                    CoreStageParameterType.STRING, "Advancement id"));
            case RESET_PAGE -> List.of(CoreStageParameter.required("page",
                    CoreStageParameterType.STRING, "Codex page id"));
            case RESYNC, RESET_ALL -> List.of();
        };
    }

    @Override
    public @NotNull CoreTargetRequirement targetRequirement() {
        return CoreTargetRequirement.REQUIRED_ENTITY;
    }

    @Override
    public @NotNull CoreActionExecutionTarget executionTarget(@NotNull CoreStagePlanningContext context) {
        return CoreActionExecutionTarget.contextEntity();
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        Player target = player(context.currentTarget());
        if (target == null) {
            return CoreActionOutcome.skipped("action.v2.stage.common.not_player");
        }
        return switch (operation) {
            case GRANT -> grant(target, arguments);
            case REVOKE -> revoke(target, arguments);
            case RESYNC -> resync(target);
            case RESET_PAGE, RESET_ALL -> reset(target, arguments);
        };
    }

    private CoreActionOutcome grant(Player target, CoreResolvedArguments arguments) {
        if (plugin.advancementService() == null) {
            return unavailable();
        }
        String advancement = Texts.trim(arguments.getString("advancement"));
        if (advancement.isEmpty()) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.v2.stage.codex.advancement_required");
        }
        if (!plugin.advancementService().grant(target, advancement).isSuccess()) {
            // v1 reported the same two causes together: an unregistered id and an already-completed node are
            // indistinguishable from the service's result, so narrowing the diagnostic here would be a guess.
            return CoreActionOutcome.failure(CoreActionFailureKind.REJECTED,
                    "action.v2.stage.codex.grant_refused", Map.of("advancement", advancement));
        }
        return CoreActionOutcome.success(Map.of(
                "advancement", advancement,
                "target", target.getUniqueId().toString()));
    }

    private CoreActionOutcome revoke(Player target, CoreResolvedArguments arguments) {
        if (plugin.advancementService() == null) {
            return unavailable();
        }
        String advancement = Texts.trim(arguments.getString("advancement"));
        if (advancement.isEmpty()) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.v2.stage.codex.advancement_required");
        }
        if (!plugin.advancementService().revoke(target, advancement).isSuccess()) {
            return CoreActionOutcome.failure(CoreActionFailureKind.REJECTED,
                    "action.v2.stage.codex.revoke_refused", Map.of("advancement", advancement));
        }
        return CoreActionOutcome.success(Map.of(
                "advancement", advancement,
                "target", target.getUniqueId().toString()));
    }

    private CoreActionOutcome resync(Player target) {
        if (plugin.advancementPacketGateway() == null) {
            return unavailable();
        }
        if (!plugin.advancementPacketGateway().canResync()) {
            return CoreActionOutcome.failure(CoreActionFailureKind.MISSING_CONTEXT,
                    "action.v2.stage.codex.resync_unavailable");
        }
        if (!plugin.advancementPacketGateway().resync(target)) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INTERNAL_ERROR,
                    "action.v2.stage.codex.resync_failed", Map.of("player", target.getName()));
        }
        return CoreActionOutcome.success(Map.of(
                "target", target.getUniqueId().toString(),
                "player", target.getName()));
    }

    private CoreActionOutcome reset(Player target, CoreResolvedArguments arguments) {
        if (plugin.advancementRegistrar() == null || plugin.advancementService() == null) {
            return unavailable();
        }
        String page = operation == Operation.RESET_PAGE ? Texts.trim(arguments.getString("page")) : "";
        if (operation == Operation.RESET_PAGE && page.isEmpty()) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.v2.stage.codex.page_required");
        }
        List<AdvancementRegistrar.RegisteredNode> nodes = matchingNodes(page);
        if (nodes.isEmpty()) {
            return CoreActionOutcome.skipped("action.v2.stage.codex.no_nodes");
        }
        int revoked = 0;
        for (AdvancementRegistrar.RegisteredNode node : nodes) {
            if (plugin.advancementService().revoke(target, node.key().toString()).isSuccess()) {
                revoked++;
            }
        }
        return CoreActionOutcome.success(Map.of(
                "target", target.getUniqueId().toString(),
                "player", target.getName(),
                "mode", operation.name().toLowerCase(Locale.ROOT),
                "page", page,
                "nodes", nodes.size(),
                "revoked", revoked));
    }

    /**
     * Collects the nodes this reset covers, deepest first.
     *
     * <p>The reversal is load-bearing and comes from v1: revoking a parent before its children can leave the
     * client showing progress that no longer exists, so children are revoked first.</p>
     */
    private List<AdvancementRegistrar.RegisteredNode> matchingNodes(String page) {
        List<AdvancementRegistrar.RegisteredNode> result = new ArrayList<>();
        String normalizedPage = Texts.normalizeId(page);
        for (AdvancementRegistrar.RegisteredNode node : plugin.advancementRegistrar().registeredNodes()) {
            if (node == null) {
                continue;
            }
            if (operation == Operation.RESET_ALL
                    || (node.page() != null && node.page().pageId().equalsIgnoreCase(normalizedPage))) {
                result.add(node);
            }
        }
        Collections.reverse(result);
        return List.copyOf(result);
    }

    private static CoreActionOutcome unavailable() {
        return CoreActionOutcome.failure(CoreActionFailureKind.MISSING_CONTEXT,
                "action.v2.stage.codex.service_unavailable");
    }

    private static Player player(CoreActionSubject subject) {
        return subject != null && subject.entityOrNull() instanceof Player resolved ? resolved : null;
    }
}
