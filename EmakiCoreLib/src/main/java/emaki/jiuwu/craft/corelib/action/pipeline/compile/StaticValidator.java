package emaki.jiuwu.craft.corelib.action.pipeline.compile;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.action.CoreActionKey;
import emaki.jiuwu.craft.corelib.api.action.CoreStageKind;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.runtime.ExecutionDomain;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.action.pipeline.compile.PhaseContract;

public final class StaticValidator {

    public static final String SELF_SOURCE = "self";

    public static final String EVERY_STAGE = "every";

    public static final String AFTER_STAGE = "after";

    public static final String KEEP_GATE = "keep";

    public static final String SET_STAGE = "set";

    public static final String INHERITED_SOURCE = "inherited";

    private static final Pattern PERCENT_PLACEHOLDER = Pattern.compile("%([^%\\s]+)%");

    private final StageResolver stages;
    private final SequenceCatalog sequences;
    private final PipelineLimits limits;

    public StaticValidator(@Nullable StageResolver stages,
            @Nullable SequenceCatalog sequences,
            @Nullable PipelineLimits limits) {
        this.stages = stages == null ? StageResolver.empty() : stages;
        this.sequences = sequences == null ? SequenceCatalog.empty() : sequences;
        this.limits = limits == null ? PipelineLimits.defaults() : limits;
    }

    public @NotNull Result validate(@Nullable String source,
            @Nullable List<ActionAst> parsed,
            @Nullable PhaseContract phase) {
        List<ActionAst> nodes = parsed == null ? List.of() : parsed;
        PhaseContract contract = phase == null ? PhaseContract.permissive("default") : phase;
        if (nodes.isEmpty()) {
            return Result.failed(CompileDiagnostic.at("action.validate.empty_pipeline", null));
        }

        boolean implicitSelf = needsImplicitSelf(nodes);
        List<CompileDiagnostic> diagnostics = new ArrayList<>();
        if (implicitSelf) {
            StageResolver.Resolution self = stages.resolve(SELF_SOURCE);
            if (!self.usable() || self.kind() != CoreStageKind.SOURCE) {
                diagnostics.add(CompileDiagnostic.suggesting("action.validate.missing_self_source",
                        token(SELF_SOURCE, nodes.get(0).column()), stages.knownIds(CoreStageKind.SOURCE)));
            }
        }

        ValidationState state = new ValidationState(contract, diagnostics);
        List<ActionAst> resolved = validateNodes(nodes, state, false, 0);
        if (!diagnostics.isEmpty()) {
            return Result.failed(diagnostics);
        }
        return Result.ok(new CompiledPipeline(source == null ? "" : source, resolved, implicitSelf));
    }

    private List<ActionAst> validateNodes(List<ActionAst> nodes,
            ValidationState state,
            boolean inheritsInboundFlow,
            int branchDepth) {
        List<ActionAst> resolved = new ArrayList<>(nodes.size());
        boolean explicitSourceSeen = false;
        boolean actionSeen = false;
        boolean flowAvailable = inheritsInboundFlow;

        for (ActionAst node : nodes) {
            if (node instanceof ActionAst.Branch branch) {
                validateVariableReferences(branch.condition(), token("if", branch.column()), state, state.diagnostics);
                if (branchDepth >= limits.maxBranchDepth()) {
                    state.diagnostics.add(CompileDiagnostic.at("action.validate.branch_depth_exceeded",
                            token("if", branch.column()),
                            Map.of("maximum", limits.maxBranchDepth(), "depth", branchDepth + 1)));
                    continue;
                }
                List<ActionAst> thenBranch = validateNodes(branch.thenBranch(), state.fork(), true, branchDepth + 1);
                List<ActionAst> elseBranch = validateNodes(branch.elseBranch(), state.fork(), true, branchDepth + 1);
                resolved.add(new ActionAst.Branch(branch.condition(), thenBranch, elseBranch, branch.column()));
                continue;
            }
            if (node instanceof ActionAst.SequenceCall call) {
                for (Map.Entry<String, String> parameter : call.parameters().entrySet()) {
                    validateVariableReferences(parameter.getValue(), token(parameter.getKey(), call.column()),
                            state, state.diagnostics);
                }
                validateSequenceCall(call, state);
                resolved.add(call);
                actionSeen = true;
                flowAvailable = true;
                continue;
            }

            ActionAst.Stage stage = (ActionAst.Stage) node;
            StageResolver.Resolution resolution = stages.resolve(stage.id());
            PipelineToken stageToken = token(stage.id(), stage.column());
            if (!resolution.known()) {
                state.diagnostics.add(unknownStageDiagnostic(stage, stageToken));
                continue;
            }
            if (resolution.ownerDisabled()) {
                state.diagnostics.add(CompileDiagnostic.at("action.validate.stage_owner_disabled", stageToken,
                        Map.of("owner", resolution.ownerName(), "stage", stage.id())));
                continue;
            }

            CoreStageKind kind = resolution.kind();
            validateThreadDeclaration(stage, resolution, state.diagnostics);
            if (kind == CoreStageKind.SOURCE) {
                if (explicitSourceSeen) {
                    state.diagnostics.add(CompileDiagnostic.at("action.validate.multiple_sources", stageToken,
                            Map.of("stage", stage.id())));
                }
                if (actionSeen) {
                    state.diagnostics.add(CompileDiagnostic.at("action.validate.source_after_action", stageToken,
                            Map.of("stage", stage.id())));
                }
                validateInheritedTargets(stage, state, state.diagnostics);
                explicitSourceSeen = true;
                flowAvailable = true;
            } else if (kind == CoreStageKind.GATE) {

                if (actionSeen && !timingStage(stage.id())) {
                    state.diagnostics.add(CompileDiagnostic.at("action.validate.gate_after_action",
                            stageToken, Map.of("stage", stage.id())));
                }
                if (!flowAvailable) {
                    flowAvailable = true;
                }
            } else if (!flowAvailable) {

                flowAvailable = true;
            }
            if (kind == CoreStageKind.ACTION) {
                actionSeen = true;
            }

            validateVariableReferences(stage.arguments().values(), stageToken, state, state.diagnostics);
            validateVariableReferences(stage.positional(), stageToken, state, state.diagnostics);
            ActionAst.Stage normalized = normalizeArguments(stage, resolution.parameters(), state.diagnostics);
            validateRequiredContext(normalized, resolution.requiredContext(), state, state.diagnostics);
            validateParameterValues(normalized, resolution.parameters(), state.diagnostics);
            validateEvery(normalized, state.diagnostics);
            resolved.add(normalized.withKind(kind));
            if (kind == CoreStageKind.GATE) {
                state.provide(resolution.providedContext(), resolution.providedVariables());
                if (SET_STAGE.equals(normalized.id())) {
                    state.provideVariables(normalized.arguments().keySet());
                }
            }
        }
        return List.copyOf(resolved);
    }

    private CompileDiagnostic unknownStageDiagnostic(ActionAst.Stage stage, PipelineToken token) {
        List<String> candidates = new ArrayList<>();
        candidates.addAll(stages.knownIds(CoreStageKind.SOURCE));
        candidates.addAll(stages.knownIds(CoreStageKind.GATE));
        candidates.addAll(stages.knownIds(CoreStageKind.ACTION));
        return CompileDiagnostic.suggesting("action.validate.unknown_stage", token, candidates);
    }

    private ActionAst.Stage normalizeArguments(ActionAst.Stage stage,
            List<CoreStageParameter> declared,
            List<CompileDiagnostic> diagnostics) {
        Map<String, CoreStageParameter> byName = new LinkedHashMap<>();
        List<CoreStageParameter> positional = new ArrayList<>();
        for (CoreStageParameter parameter : declared) {
            if (parameter == null || Texts.isBlank(parameter.name())) {
                continue;
            }
            byName.put(Texts.lower(parameter.name()), parameter);
            if (parameter.positional()) {
                positional.add(parameter);
            }
        }

        Map<String, String> arguments = new LinkedHashMap<>(stage.arguments());
        if (EVERY_STAGE.equals(stage.id())) {
            normalizeEveryPositional(stage, arguments, diagnostics);
        } else if (!stage.positional().isEmpty()) {
            if (positional.isEmpty()) {

                diagnostics.add(new CompileDiagnostic("action.validate.positional_not_allowed",
                        "", "", 0, Math.max(1, stage.column()), stage.positional().get(0),
                        Map.of("stage", stage.id(), "value", stage.positional().get(0)),
                        List.copyOf(byName.keySet())));
            } else if (positional.size() == 1) {
                CoreStageParameter parameter = positional.get(0);
                if (arguments.containsKey(parameter.name())) {
                    diagnostics.add(CompileDiagnostic.at("action.validate.argument_both_named_and_positional",
                            token(parameter.name(), stage.column())));
                } else {
                    arguments.put(parameter.name(), String.join(" ", stage.positional()));
                }
            } else if (stage.positional().size() > positional.size()) {
                diagnostics.add(CompileDiagnostic.at("action.validate.too_many_positional_arguments",
                        token(stage.positional().get(positional.size()), stage.column()),
                        Map.of("maximum", positional.size(), "actual", stage.positional().size())));
            } else {
                for (int index = 0; index < stage.positional().size(); index++) {
                    CoreStageParameter parameter = positional.get(index);
                    if (arguments.containsKey(parameter.name())) {
                        diagnostics.add(CompileDiagnostic.at("action.validate.argument_both_named_and_positional",
                                token(parameter.name(), stage.column())));
                    } else {
                        arguments.put(parameter.name(), stage.positional().get(index));
                    }
                }
            }
        }

        if (!SET_STAGE.equals(stage.id())) {
            for (String supplied : arguments.keySet()) {
                if (!byName.containsKey(supplied)) {
                    diagnostics.add(CompileDiagnostic.suggesting("action.validate.unknown_argument",
                            token(supplied, stage.column()), List.copyOf(byName.keySet())));
                }
            }
        }
        for (CoreStageParameter parameter : declared) {
            String value = arguments.get(parameter.name());
            if (parameter.required() && Texts.isBlank(value) && Texts.isBlank(parameter.defaultValue())) {
                diagnostics.add(CompileDiagnostic.at("action.validate.missing_required_argument",
                        token(parameter.name(), stage.column()), Map.of("argument", parameter.name())));
            }
        }
        return new ActionAst.Stage(stage.id(), stage.kind(), arguments, List.of(), stage.column());
    }

    private void normalizeEveryPositional(ActionAst.Stage stage,
            Map<String, String> arguments,
            List<CompileDiagnostic> diagnostics) {
        List<String> positional = stage.positional();
        if (positional.isEmpty()) {
            return;
        }
        if (!arguments.containsKey("interval")) {
            arguments.put("interval", positional.get(0));
        } else {
            diagnostics.add(CompileDiagnostic.at("action.validate.argument_both_named_and_positional",
                    token("interval", stage.column())));
        }
        if (positional.size() == 1) {
            return;
        }
        if (positional.size() != 3 || !"times".equalsIgnoreCase(positional.get(1))) {
            diagnostics.add(CompileDiagnostic.at("action.validate.invalid_every_syntax",
                    token(String.join(" ", positional), stage.column()),
                    Map.of("expected", "every <interval> times <count>")));
            return;
        }
        if (arguments.containsKey("times")) {
            diagnostics.add(CompileDiagnostic.at("action.validate.argument_both_named_and_positional",
                    token("times", stage.column())));
        } else {
            arguments.put("times", positional.get(2));
        }
    }

    private void validateThreadDeclaration(ActionAst.Stage stage,
            StageResolver.Resolution resolution,
            List<CompileDiagnostic> diagnostics) {
        if (resolution.probeDomain() == null) {
            diagnostics.add(CompileDiagnostic.at("action.validate.thread_domain_undeclared",
                    token(stage.id(), stage.column()), Map.of("stage", stage.id())));
            return;
        }
        if (resolution.probeDomain() == ExecutionDomain.ASYNC_COMPUTE
                && resolution.kind() == CoreStageKind.ACTION
                && resolution.targetRequirement() != CoreTargetRequirement.NONE) {
            diagnostics.add(CompileDiagnostic.at("action.validate.async_stage_requires_target",
                    token(stage.id(), stage.column()),
                    Map.of("stage", stage.id(), "requirement", resolution.targetRequirement().name())));
        }
    }

    private void validateInheritedTargets(ActionAst.Stage stage,
            ValidationState state,
            List<CompileDiagnostic> diagnostics) {
        if (!INHERITED_SOURCE.equals(stage.id()) || state.contract.providesInheritedTargets()) {
            return;
        }
        diagnostics.add(CompileDiagnostic.at("action.validate.missing_inherited_targets",
                token(stage.id(), stage.column()), Map.of("phase", state.contract.phaseId())));
    }

    private void validateRequiredContext(ActionAst.Stage stage,
            Set<CoreActionKey<?>> required,
            ValidationState state,
            List<CompileDiagnostic> diagnostics) {
        for (CoreActionKey<?> key : required) {
            if (!state.provides(key)) {
                diagnostics.add(CompileDiagnostic.at("action.validate.missing_context_key",
                        token(stage.id(), stage.column()),
                        Map.of("stage", stage.id(), "key", key.name(), "type", key.type().getSimpleName(),
                                "phase", state.contract.phaseId())));
            }
        }
    }

    private void validateVariableReferences(Iterable<String> values,
            PipelineToken token,
            ValidationState state,
            List<CompileDiagnostic> diagnostics) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            validateVariableReferences(value, token, state, diagnostics);
        }
    }

    private void validateVariableReferences(String value,
            PipelineToken token,
            ValidationState state,
            List<CompileDiagnostic> diagnostics) {
        if (Texts.isBlank(value) || value.indexOf('%') < 0) {
            return;
        }
        Matcher matcher = PERCENT_PLACEHOLDER.matcher(value);
        while (matcher.find()) {
            String raw = matcher.group(1);
            if (raw.regionMatches(true, 0, "var.", 0, 4)) {
                String variable = PhaseContract.normalizeVariableName(raw);
                if (!state.providesVariable(variable)) {
                    diagnostics.add(CompileDiagnostic.at("action.validate.missing_variable",
                            token, Map.of("variable", variable, "phase", state.contract.phaseId())));
                }
                continue;
            }
            String normalized = PhaseContract.normalizeVariableName(raw);
            if (state.hasDeclaredVariable(normalized)) {
                diagnostics.add(CompileDiagnostic.at("action.validate.bare_variable_reference",
                        token, Map.of("variable", normalized, "expected", "%var." + normalized + "%")));
            }
        }
    }

    private void validateParameterValues(ActionAst.Stage stage,
            List<CoreStageParameter> declared,
            List<CompileDiagnostic> diagnostics) {
        for (CoreStageParameter parameter : declared) {
            String value = stage.arguments().get(parameter.name());
            if (Texts.isBlank(value)) {
                value = parameter.defaultValue();
            }
            if (Texts.isBlank(value) || containsPlaceholder(value)) {
                continue;
            }
            if (!validLiteral(value, parameter.type())) {
                diagnostics.add(CompileDiagnostic.at("action.validate.invalid_argument_type",
                        token(parameter.name(), stage.column()),
                        Map.of("argument", parameter.name(), "value", value,
                                "expected", parameter.type().name())));
            }
        }
    }

    private void validateEvery(ActionAst.Stage stage, List<CompileDiagnostic> diagnostics) {
        if (!EVERY_STAGE.equals(stage.id())) {
            return;
        }
        String rawTimes = stage.arguments().getOrDefault("times", "0");
        if (containsPlaceholder(rawTimes)) {
            diagnostics.add(CompileDiagnostic.at("action.validate.repeat_must_be_literal",
                    token("times", stage.column()), Map.of("value", rawTimes)));
            return;
        }
        Integer times = ValueParsers.parseIntNullable(rawTimes);
        if (times == null || times < 0) {
            diagnostics.add(CompileDiagnostic.at("action.validate.invalid_repeat_times",
                    token("times", stage.column()), Map.of("value", rawTimes)));
            return;
        }
        if (times > limits.maxRepeatTimes()) {
            diagnostics.add(CompileDiagnostic.at("action.validate.repeat_limit_exceeded",
                    token("times", stage.column()),
                    Map.of("value", times, "maximum", limits.maxRepeatTimes())));
        }
    }

    private void validateSequenceCall(ActionAst.SequenceCall call, ValidationState state) {
        PipelineToken token = token(call.sequence(), call.column());
        if (!sequences.contains(call.sequence())) {
            state.diagnostics.add(CompileDiagnostic.suggesting("action.validate.unknown_sequence",
                    token, sequences.names()));
            return;
        }
        Set<String> required = sequences.requiredParameters(call.sequence());
        for (String parameter : required) {
            if (Texts.isBlank(call.parameters().get(parameter))) {
                state.diagnostics.add(CompileDiagnostic.at("action.validate.missing_sequence_parameter",
                        token, Map.of("sequence", call.sequence(), "parameter", parameter)));
            }
        }
        for (String supplied : call.parameters().keySet()) {
            if (!required.contains(supplied)) {

                continue;
            }
        }
        detectSequenceCycle(call.sequence(), token, state.diagnostics);
    }

    private void detectSequenceCycle(String root,
            PipelineToken token,
            List<CompileDiagnostic> diagnostics) {
        Deque<String> path = new ArrayDeque<>();
        Set<String> visiting = new LinkedHashSet<>();
        if (cycleFrom(root, path, visiting, 0)) {
            diagnostics.add(CompileDiagnostic.at("action.validate.sequence_cycle", token,
                    Map.of("path", List.copyOf(path))));
        }
    }

    private boolean cycleFrom(String current,
            Deque<String> path,
            Set<String> visiting,
            int depth) {
        if (depth > limits.maxSequenceDepth()) {
            path.addLast(current);
            return true;
        }
        if (!visiting.add(current)) {
            path.addLast(current);
            return true;
        }
        path.addLast(current);
        for (String called : sequences.calls(current)) {
            if (cycleFrom(called, path, visiting, depth + 1)) {
                return true;
            }
        }
        path.removeLast();
        visiting.remove(current);
        return false;
    }

    private boolean needsImplicitSelf(List<ActionAst> nodes) {
        if (nodes.isEmpty()) {
            return false;
        }
        ActionAst first = nodes.get(0);
        if (first instanceof ActionAst.Branch || first instanceof ActionAst.SequenceCall) {
            return true;
        }
        ActionAst.Stage stage = (ActionAst.Stage) first;
        StageResolver.Resolution resolution = stages.resolve(stage.id());
        if (!resolution.known() || resolution.kind() == CoreStageKind.SOURCE) {

            return false;
        }
        return resolution.kind() != CoreStageKind.ACTION
                || resolution.targetRequirement() != CoreTargetRequirement.NONE;
    }

    private static boolean validLiteral(String value, CoreStageParameterType type) {
        CoreStageParameterType expected = type == null ? CoreStageParameterType.STRING : type;
        return switch (expected) {
            case STRING, SOUND -> true;
            case INTEGER -> ValueParsers.parseIntNullable(value) != null;
            case DOUBLE -> ValueParsers.parseDoubleNullable(value) != null;
            case BOOLEAN -> "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
            case DURATION -> ValueParsers.parseTicks(value) >= 0L;
            case ENTITY_TYPE -> enumExists(EntityType.class, value);
            case MATERIAL -> Material.matchMaterial(value) != null;
            case PERCENTAGE -> validChance(value);
            case EXPRESSION -> ExpressionEngine.evaluateNumericDetailed(value).success();
        };
    }

    private static boolean validChance(String value) {
        int slash = value.indexOf('/');
        if (slash > 0 && slash < value.length() - 1) {
            Double numerator = ValueParsers.parseDoubleNullable(value.substring(0, slash).trim());
            Double denominator = ValueParsers.parseDoubleNullable(value.substring(slash + 1).trim());
            return numerator != null && denominator != null && denominator != 0D
                    && numerator / denominator >= 0D && numerator / denominator <= 1D;
        }
        double chance = ValueParsers.parseChance(value);
        return chance >= 0D && chance <= 1D;
    }

    private static <E extends Enum<E>> boolean enumExists(Class<E> type, String value) {
        String normalized = Texts.trim(value).replace("minecraft:", "")
                .replace('.', '_').toUpperCase(Locale.ROOT);
        try {
            Enum.valueOf(type, normalized);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public static boolean timingStage(@Nullable String id) {
        return AFTER_STAGE.equals(id) || EVERY_STAGE.equals(id);
    }

    private static boolean containsPlaceholder(String value) {
        int first = value.indexOf('%');
        return first >= 0 && value.indexOf('%', first + 1) > first;
    }

    private static PipelineToken token(String text, int column) {
        return new PipelineToken(PipelineToken.Kind.WORD, text, Math.max(1, column), false);
    }

    private static final class ValidationState {

        private final PhaseContract contract;
        private final List<CompileDiagnostic> diagnostics;
        private final Set<CoreActionKey<?>> availableKeys;
        private final Set<String> availableVariables;

        private ValidationState(PhaseContract contract, List<CompileDiagnostic> diagnostics) {
            this(contract, diagnostics, contract.providedKeys(), contract.providedVariables());
        }

        private ValidationState(PhaseContract contract,
                List<CompileDiagnostic> diagnostics,
                Set<CoreActionKey<?>> availableKeys,
                Set<String> availableVariables) {
            this.contract = contract;
            this.diagnostics = diagnostics;
            this.availableKeys = availableKeys == null ? new LinkedHashSet<>() : new LinkedHashSet<>(availableKeys);
            this.availableVariables = availableVariables == null
                    ? new LinkedHashSet<>()
                    : new LinkedHashSet<>(availableVariables);
        }

        private ValidationState fork() {
            return new ValidationState(contract, diagnostics, availableKeys, availableVariables);
        }

        private boolean provides(CoreActionKey<?> key) {
            return contract.permissive() || (key != null && availableKeys.contains(key));
        }

        private boolean providesVariable(String variable) {
            String normalized = PhaseContract.normalizeVariableName(variable);
            return contract.permissive() || (!normalized.isEmpty() && availableVariables.contains(normalized));
        }

        private boolean hasDeclaredVariable(String variable) {
            String normalized = PhaseContract.normalizeVariableName(variable);
            return !normalized.isEmpty() && availableVariables.contains(normalized);
        }

        private void provide(Set<CoreActionKey<?>> keys, Set<String> variables) {
            if (keys != null) {
                for (CoreActionKey<?> key : keys) {
                    if (key != null) {
                        availableKeys.add(key);
                    }
                }
            }
            provideVariables(variables);
        }

        private void provideVariables(Iterable<String> variables) {
            if (variables == null) {
                return;
            }
            for (String variable : variables) {
                String normalized = PhaseContract.normalizeVariableName(variable);
                if (!normalized.isEmpty()) {
                    availableVariables.add(normalized);
                }
            }
        }
    }

    public record Result(@Nullable CompiledPipeline pipeline, @NotNull List<CompileDiagnostic> diagnostics) {

        public Result {
            diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        }

        static Result ok(CompiledPipeline pipeline) {
            return new Result(pipeline, List.of());
        }

        static Result failed(CompileDiagnostic diagnostic) {
            return new Result(null, List.of(diagnostic));
        }

        static Result failed(List<CompileDiagnostic> diagnostics) {
            return new Result(null, diagnostics);
        }

        public boolean successful() {
            return pipeline != null && diagnostics.isEmpty();
        }
    }
}
