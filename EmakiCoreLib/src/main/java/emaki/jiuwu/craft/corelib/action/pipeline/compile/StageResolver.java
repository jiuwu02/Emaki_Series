package emaki.jiuwu.craft.corelib.action.pipeline.compile;

import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.action.CoreActionKey;
import emaki.jiuwu.craft.corelib.api.action.CoreStageKind;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.runtime.ExecutionDomain;

public interface StageResolver {

    @NotNull
    Resolution resolve(@Nullable String id);

    @NotNull
    List<String> knownIds(@NotNull CoreStageKind kind);

    record Resolution(@Nullable CoreStageKind kind,
            @NotNull List<CoreStageParameter> parameters,
            @NotNull Set<CoreActionKey<?>> requiredContext,
            @NotNull Set<CoreActionKey<?>> providedContext,
            @NotNull Set<String> providedVariables,
            @NotNull String ownerName,
            boolean ownerDisabled,
            @NotNull CoreTargetRequirement targetRequirement,
            @Nullable ExecutionDomain probeDomain) {

        public Resolution {
            parameters = parameters == null ? List.of() : List.copyOf(parameters);
            requiredContext = requiredContext == null ? Set.of() : Set.copyOf(requiredContext);
            providedContext = providedContext == null ? Set.of() : Set.copyOf(providedContext);
            providedVariables = providedVariables == null ? Set.of() : Set.copyOf(providedVariables);
            ownerName = ownerName == null ? "" : ownerName;
            targetRequirement = targetRequirement == null ? CoreTargetRequirement.NONE : targetRequirement;
        }

        public static @NotNull Resolution unknown() {
            return new Resolution(null, List.of(), Set.of(), Set.of(), Set.of(), "", false,
                    CoreTargetRequirement.NONE, null);
        }

        public static @NotNull Resolution disabled(@NotNull CoreStageKind kind, @NotNull String ownerName) {
            return new Resolution(kind, List.of(), Set.of(), Set.of(), Set.of(), ownerName, true,
                    CoreTargetRequirement.NONE, null);
        }

        public static @NotNull Resolution found(@NotNull CoreStageKind kind,
                @Nullable List<CoreStageParameter> parameters,
                @Nullable Set<CoreActionKey<?>> requiredContext,
                @Nullable Set<CoreActionKey<?>> providedContext,
                @Nullable Set<String> providedVariables,
                @Nullable CoreTargetRequirement targetRequirement,
                @Nullable ExecutionDomain probeDomain) {
            return new Resolution(kind, parameters, requiredContext, providedContext, providedVariables,
                    "", false, targetRequirement, probeDomain);
        }

        public boolean known() {
            return kind != null;
        }

        public boolean usable() {
            return kind != null && !ownerDisabled;
        }
    }

    static @NotNull StageResolver empty() {
        return new StageResolver() {

            @Override
            public @NotNull Resolution resolve(@Nullable String id) {
                return Resolution.unknown();
            }

            @Override
            public @NotNull List<String> knownIds(@NotNull CoreStageKind kind) {
                return List.of();
            }
        };
    }
}
