package emaki.jiuwu.craft.corelib.action.v2.compile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionKey;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageKind;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.runtime.ExecutionDomain;

/** In-memory {@link StageResolver} so validation can be tested without a server. */
final class FakeStageResolver implements StageResolver {

    private final Map<String, Resolution> entries = new LinkedHashMap<>();

    FakeStageResolver source(String id, CoreStageParameter... parameters) {
        entries.put(id, Resolution.found(CoreStageKind.SOURCE, List.of(parameters), Set.of(),
                CoreTargetRequirement.NONE, ExecutionDomain.SERVER_GLOBAL));
        return this;
    }

    FakeStageResolver gate(String id, CoreStageParameter... parameters) {
        entries.put(id, Resolution.found(CoreStageKind.GATE, List.of(parameters), Set.of(),
                CoreTargetRequirement.NONE, ExecutionDomain.SERVER_GLOBAL));
        return this;
    }

    FakeStageResolver action(String id, CoreStageParameter... parameters) {
        return action(id, CoreTargetRequirement.OPTIONAL, ExecutionDomain.ENTITY, Set.of(), parameters);
    }

    FakeStageResolver action(String id,
            CoreTargetRequirement requirement,
            ExecutionDomain domain,
            Set<CoreActionKey<?>> requiredContext,
            CoreStageParameter... parameters) {
        entries.put(id, Resolution.found(CoreStageKind.ACTION, List.of(parameters), requiredContext,
                requirement, domain));
        return this;
    }

    FakeStageResolver undeclaredDomain(String id) {
        entries.put(id, Resolution.found(CoreStageKind.ACTION, List.of(), Set.of(),
                CoreTargetRequirement.OPTIONAL, null));
        return this;
    }

    FakeStageResolver disabled(String id, CoreStageKind kind, String ownerName) {
        entries.put(id, Resolution.disabled(kind, ownerName));
        return this;
    }

    @Override
    public @NotNull Resolution resolve(@Nullable String id) {
        Resolution resolution = id == null ? null : entries.get(id);
        return resolution == null ? Resolution.unknown() : resolution;
    }

    @Override
    public @NotNull List<String> knownIds(@NotNull CoreStageKind kind) {
        List<String> ids = new ArrayList<>();
        entries.forEach((id, resolution) -> {
            if (resolution.kind() == kind) {
                ids.add(id);
            }
        });
        return List.copyOf(ids);
    }
}
