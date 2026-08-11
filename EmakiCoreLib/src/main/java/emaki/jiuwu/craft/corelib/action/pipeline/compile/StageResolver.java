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

/**
 * What the validator needs to know about a registered stage.
 *
 * <p>A seam over the registry so validation can be exercised without a live server, and so the
 * validator does not depend on the registry's internals.</p>
 */
public interface StageResolver {

    /**
     * Resolves a stage id.
     *
     * @param id stage name as written
     * @return the resolution
     */
    @NotNull
    Resolution resolve(@Nullable String id);

    /**
     * Lists known ids of one kind, for "did you mean" diagnostics.
     *
     * @param kind stage role
     * @return known ids
     */
    @NotNull
    List<String> knownIds(@NotNull CoreStageKind kind);

    /**
     * What resolving one stage id produced.
     *
     * @param kind which table held it, or {@code null} when unresolved
     * @param parameters its declared parameters
     * @param requiredContext context keys it reads
     * @param providedContext context keys it can publish after it runs
     * @param providedVariables pipeline variables it can publish after it runs
     * @param ownerName owning plugin name, used when the owner is disabled
     * @param ownerDisabled whether the id is known but its owner is currently disabled
     * @param targetRequirement target contract for action stages
     * @param probeDomain domain returned for a neutral planning context; {@code null} only when the
     *        stage is unresolved, disabled, or its declaration failed
     */
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

        /** {@return an unresolved result} */
        public static @NotNull Resolution unknown() {
            return new Resolution(null, List.of(), Set.of(), Set.of(), Set.of(), "", false,
                    CoreTargetRequirement.NONE, null);
        }

        /**
         * Creates a result for a known id whose owner is disabled.
         *
         * @param kind which table held it
         * @param ownerName the owning plugin name
         * @return the resolution
         */
        public static @NotNull Resolution disabled(@NotNull CoreStageKind kind, @NotNull String ownerName) {
            return new Resolution(kind, List.of(), Set.of(), Set.of(), Set.of(), ownerName, true,
                    CoreTargetRequirement.NONE, null);
        }

        /**
         * Creates a result for a usable stage.
         *
         * @param kind which table held it
         * @param parameters declared parameters
         * @param requiredContext context keys it reads
         * @param providedContext context keys it can publish
         * @param providedVariables pipeline variables it can publish
         * @return the resolution
         */
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

        /** {@return whether the stage exists in some table} */
        public boolean known() {
            return kind != null;
        }

        /** {@return whether the stage is usable right now} */
        public boolean usable() {
            return kind != null && !ownerDisabled;
        }
    }

    /** {@return a resolver that knows no stages} */
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
