package emaki.jiuwu.craft.skills.script;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.api.script.modules.ScriptServiceApiSupport;
import emaki.jiuwu.craft.corelib.script.ScriptEntitySnapshot;
import emaki.jiuwu.craft.corelib.script.ScriptExecutionResult;
import emaki.jiuwu.craft.corelib.script.ScriptSnapshots;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.skills.EmakiSkillsPlugin;
import emaki.jiuwu.craft.skills.api.SkillScriptAction;
import emaki.jiuwu.craft.skills.model.ResolvedSkillParameters;
import emaki.jiuwu.craft.skills.trigger.TriggerInvocation;





public final class SkillScriptIntentExecutor {

    private final EmakiSkillsPlugin plugin;
    private final SkillScriptContext context;
    private final Entity caster;
    private final Map<String, Entity> knownEntities;
    private final WorkerContext workerContext;
    private final SkillScriptAction.CancellationToken cancellationToken;

    public SkillScriptIntentExecutor(SkillScriptContext context) {
        this(context, new SkillScriptAction.CancellationToken());
    }

    public SkillScriptIntentExecutor(SkillScriptContext context,
            SkillScriptAction.CancellationToken cancellationToken) {
        this.context = context;
        this.cancellationToken = cancellationToken == null
                ? new SkillScriptAction.CancellationToken()
                : cancellationToken;
        this.plugin = context == null ? null : context.plugin();
        this.caster = context == null ? null : context.caster();
        Entity target = context == null ? null : context.targetEntity();
        Map<String, Entity> entities = new LinkedHashMap<>();
        addKnownEntity(entities, caster);
        addKnownEntity(entities, target);
        this.knownEntities = Map.copyOf(entities);
        this.workerContext = new WorkerContext(
                context == null ? "" : context.skillId(),
                context == null ? "" : context.triggerId(),
                context == null ? Map.of() : context.variables(),
                context == null ? Map.of() : ScriptSnapshots.immutableMap(context.sharedState()),
                ScriptEntitySnapshot.capture(caster),
                ScriptEntitySnapshot.capture(target),
                target == null || context == null ? Map.of() : locationSnapshot(context.targetLocation()),
                this.cancellationToken);
    }

    public WorkerContext workerContext() {
        return workerContext;
    }

    public CompletableFuture<ScriptExecutionResult> applyAsync(ScriptExecutionResult scriptResult) {
        if (cancellationToken.isCancelled()) {
            return CompletableFuture.completedFuture(ScriptExecutionResult.failure(
                    "JavaScript skill action was cancelled before intents were committed."));
        }
        List<Intent> intents = workerContext.seal();
        if (scriptResult == null) {
            return CompletableFuture.completedFuture(ScriptExecutionResult.failure(
                    "JavaScript skill action returned no result."));
        }
        if (!scriptResult.success() || scriptResult.skipped() || intents.isEmpty()) {
            return CompletableFuture.completedFuture(scriptResult);
        }
        return applyNext(intents, 0).thenApply(result -> result.success()
                ? scriptResult
                : ScriptExecutionResult.failure(result.message()));
    }

    private CompletableFuture<IntentResult> applyNext(List<Intent> intents, int index) {
        if (cancellationToken.isCancelled()) {
            return CompletableFuture.completedFuture(IntentResult.failure(
                    "JavaScript skill action was cancelled before all intents were committed."));
        }
        if (index >= intents.size()) {
            return CompletableFuture.completedFuture(IntentResult.ok());
        }
        return applyIntent(intents.get(index)).thenCompose(result -> result.success()
                ? applyNext(intents, index + 1)
                : CompletableFuture.completedFuture(result));
    }

    private CompletableFuture<IntentResult> applyIntent(Intent intent) {
        if (cancellationToken.isCancelled()) {
            return CompletableFuture.completedFuture(IntentResult.failure(
                    "JavaScript skill action was cancelled before intent commit."));
        }
        if (intent instanceof VariableIntent variable) {
            return onCaster(() -> {
                context.putVariable(variable.key(), variable.value());
                return CompletableFuture.completedFuture(IntentResult.ok());
            });
        }
        if (intent instanceof SharedStateIntent shared) {
            return onCaster(() -> {
                context.putSharedValue(shared.key(), shared.value());
                return CompletableFuture.completedFuture(IntentResult.ok());
            });
        }
        if (intent instanceof SetTargetIntent setTarget) {
            Entity target = resolveEntity(setTarget.entityUuid());
            if (target == null) {
                return onCaster(() -> {
                    context.setTarget(null);
                    return CompletableFuture.completedFuture(IntentResult.ok());
                });
            }
            return onEntity(target, () -> {
                context.setTarget(target);
                return CompletableFuture.completedFuture(IntentResult.ok());
            });
        }
        if (intent instanceof RunActionIntent action) {
            return onCaster(() -> runAction(action));
        }
        if (intent instanceof RunActionLineIntent actionLine) {
            return onCaster(() -> runActionLine(actionLine));
        }
        if (intent instanceof CastMythicIntent mythic) {
            return onCaster(() -> CompletableFuture.completedFuture(castMythic(mythic)));
        }
        if (intent instanceof ApplyDamageIntent damage) {
            Entity target = resolveEntity(damage.targetUuid());
            if (target == null) {
                return CompletableFuture.completedFuture(IntentResult.failure("Skill script damage target is unavailable."));
            }
            return onEntity(target, () -> CompletableFuture.completedFuture(applyDamage(target, damage)));
        }
        return CompletableFuture.completedFuture(IntentResult.failure("Unknown skill script intent."));
    }

    private CompletionStage<IntentResult> runAction(RunActionIntent intent) {
        if (plugin == null || plugin.coreLib().actionExecutor() == null || caster == null) {
            return CompletableFuture.completedFuture(IntentResult.failure("Core Action executor is unavailable."));
        }
        ActionContext actionContext = actionContext();
        return plugin.coreLib().actionExecutor().execute(actionContext, intent.actionId(), intent.arguments())
                .thenApply(this::fromActionResult);
    }

    private CompletionStage<IntentResult> runActionLine(RunActionLineIntent intent) {
        if (plugin == null || plugin.coreLib().actionExecutor() == null || caster == null) {
            return CompletableFuture.completedFuture(IntentResult.failure("Core Action executor is unavailable."));
        }
        return plugin.coreLib().actionExecutor().executeAll(actionContext(), List.of(intent.line()), true)
                .thenApply(result -> result != null && result.success()
                        ? IntentResult.ok()
                        : IntentResult.failure("Deferred skill Action line failed."));
    }

    private IntentResult castMythic(CastMythicIntent intent) {
        if (plugin == null || plugin.mythicSkillCastService() == null || caster == null) {
            return IntentResult.failure("Mythic skill casting is unavailable.");
        }
        TriggerInvocation invocation = context.invocation();
        boolean success = plugin.mythicSkillCastService().cast(
                context.caster(), intent.skillId(), invocation, new ResolvedSkillParameters(intent.parameters()));
        return success ? IntentResult.ok() : IntentResult.failure("Mythic skill cast failed: " + intent.skillId());
    }

    private IntentResult applyDamage(Entity target, ApplyDamageIntent intent) {
        if (!(target instanceof LivingEntity livingTarget)) {
            return IntentResult.failure("Skill script damage target is not living.");
        }
        LivingEntity attacker = caster instanceof LivingEntity livingCaster ? livingCaster : null;
        Map<String, Object> damageContext = new LinkedHashMap<>();
        damageContext.put("source", "skills_js");
        damageContext.put("skill_id", context.skillId());
        damageContext.put("trigger_id", context.triggerId());
        if (plugin != null) {
            damageContext.put("source_plugin", plugin.getName());
        }
        damageContext.putAll(intent.damageContext());
        boolean success = ScriptServiceApiSupport.service("emaki.jiuwu.craft.attribute.service.AttributeServiceFacade")
                .map(service -> ScriptServiceApiSupport.invokeBoolean(service,
                        "applyDamage",
                        new Class<?>[] { LivingEntity.class, LivingEntity.class, String.class, double.class, Map.class },
                        attacker,
                        livingTarget,
                        intent.damageTypeId(),
                        intent.baseDamage(),
                        Map.copyOf(damageContext)))
                .orElse(false);
        return success ? IntentResult.ok() : IntentResult.failure("Skill script damage application failed.");
    }

    private ActionContext actionContext() {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("skill_id", context.skillId());
        attributes.put("trigger_id", context.triggerId());
        attributes.put("target", context.targetEntity());
        attributes.put("target_location", context.targetLocation());
        return ActionContext.create(plugin, context.caster(), "skill_js", false)
                .withPlaceholders(context.variables())
                .withAttributes(attributes);
    }

    private IntentResult fromActionResult(ActionResult result) {
        if (result == null) {
            return IntentResult.failure("Deferred skill Action returned no result.");
        }
        return result.success() && !result.skipped()
                ? IntentResult.ok()
                : IntentResult.failure(Texts.isBlank(result.errorMessage())
                        ? "Deferred skill Action failed."
                        : result.errorMessage());
    }

    private CompletableFuture<IntentResult> onCaster(Supplier<? extends CompletionStage<IntentResult>> task) {
        if (caster == null) {
            return CompletableFuture.completedFuture(IntentResult.failure("Skill caster is unavailable."));
        }
        return onEntity(caster, task);
    }

    private CompletableFuture<IntentResult> onEntity(Entity entity,
            Supplier<? extends CompletionStage<IntentResult>> task) {
        CompletableFuture<IntentResult> future = new CompletableFuture<>();
        AtomicBoolean started = new AtomicBoolean();
        if (cancellationToken.isCancelled()) {
            future.complete(IntentResult.failure("Skill intent was cancelled before scheduling."));
            return future;
        }
        if (plugin == null || entity == null) {
            future.complete(IntentResult.failure("Skill intent entity domain is unavailable."));
            return future;
        }
        try {
            Runnable operation = () -> {
                started.set(true);
                if (cancellationToken.isCancelled()) {
                    future.complete(IntentResult.failure("Skill intent was cancelled before execution."));
                    return;
                }
                flatten(task, future);
            };
            if (plugin.threadOwnership() != null && plugin.threadOwnership().isEntityOwned(entity)) {
                operation.run();
            } else {
                var scheduled = plugin.executionDispatcher().runEntity(plugin, entity, operation,
                        () -> future.complete(IntentResult.failure(
                                "Skill intent entity-domain task retired before execution.")));
                if (scheduled == null) {
                    future.complete(IntentResult.failure("Skill intent entity-domain scheduling was rejected."));
                }
            }
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
        CompletableFuture.delayedExecutor(30L, TimeUnit.SECONDS).execute(() -> {
            if (!started.get()) {
                future.complete(IntentResult.failure(
                        "Skill intent entity-domain task did not execute before its scheduling deadline."));
            }
        });
        return future.exceptionally(throwable -> IntentResult.failure(message(throwable)));
    }

    private void flatten(Supplier<? extends CompletionStage<IntentResult>> task,
            CompletableFuture<IntentResult> future) {
        try {
            CompletionStage<IntentResult> stage = task.get();
            if (stage == null) {
                future.complete(IntentResult.failure("Skill intent returned no completion stage."));
                return;
            }
            stage.whenComplete((result, throwable) -> {
                if (throwable != null) {
                    future.completeExceptionally(unwrap(throwable));
                } else {
                    future.complete(result == null ? IntentResult.failure("Skill intent returned no result.") : result);
                }
            });
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
    }

    private Entity resolveEntity(String uuid) {
        return Texts.isBlank(uuid) ? null : knownEntities.get(uuid);
    }

    private static void addKnownEntity(Map<String, Entity> entities, Entity entity) {
        if (entity != null) {
            entities.put(entity.getUniqueId().toString(), entity);
        }
    }

    private static Map<String, Object> locationSnapshot(org.bukkit.Location location) {
        if (location == null) {
            return Map.of();
        }
        return Map.of(
                "world", location.getWorld() == null ? "" : location.getWorld().getName(),
                "x", location.getX(),
                "y", location.getY(),
                "z", location.getZ(),
                "yaw", location.getYaw(),
                "pitch", location.getPitch());
    }

    private static String message(Throwable throwable) {
        Throwable cause = unwrap(throwable);
        return cause == null || Texts.isBlank(cause.getMessage())
                ? "Skill script intent failed."
                : cause.getMessage();
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null
                && (current instanceof CompletionException
                || current instanceof java.util.concurrent.ExecutionException)) {
            current = current.getCause();
        }
        return current;
    }

    public static final class WorkerContext {

        private final String skillId;
        private final String triggerId;
        private final Map<String, String> variables;
        private final Map<String, Object> sharedState;
        private final ScriptEntitySnapshot.EntityView caster;
        private ScriptEntitySnapshot.EntityView target;
        private Map<String, Object> targetLocation;
        private final List<Intent> intents = new ArrayList<>();
        private final SkillScriptAction.CancellationToken cancellationToken;
        private boolean sealed;

        private WorkerContext(String skillId,
                String triggerId,
                Map<String, String> variables,
                Map<String, Object> sharedState,
                ScriptEntitySnapshot.EntityView caster,
                ScriptEntitySnapshot.EntityView target,
                Map<String, Object> targetLocation,
                SkillScriptAction.CancellationToken cancellationToken) {
            this.skillId = Texts.toStringSafe(skillId);
            this.triggerId = Texts.toStringSafe(triggerId);
            this.variables = new LinkedHashMap<>(variables == null ? Map.of() : variables);
            this.sharedState = new LinkedHashMap<>(sharedState == null ? Map.of() : sharedState);
            this.caster = caster == null ? ScriptEntitySnapshot.EntityView.empty() : caster;
            this.target = target == null ? ScriptEntitySnapshot.EntityView.empty() : target;
            this.targetLocation = targetLocation == null ? Map.of() : ScriptSnapshots.immutableMap(targetLocation);
            this.cancellationToken = cancellationToken;
        }

        public String skillId() {
            return skillId;
        }

        public String triggerId() {
            return triggerId;
        }

        public String variable(String key) {
            return variables.get(key);
        }

        public Map<String, String> variables() {
            return Map.copyOf(variables);
        }

        public boolean setVariable(String key, Object value) {
            if (!active() || Texts.isBlank(key)) {
                return false;
            }
            String normalized = Texts.lower(key);
            String safeValue = Texts.toStringSafe(value);
            variables.put(normalized, safeValue);
            intents.add(new VariableIntent(normalized, safeValue));
            return true;
        }

        public Object sharedValue(String key) {
            return sharedState.get(key);
        }

        public Map<String, Object> sharedState() {
            return Map.copyOf(sharedState);
        }

        public boolean setSharedValue(String key, Object value) {
            if (!active() || Texts.isBlank(key)) {
                return false;
            }
            Object safeValue = ScriptSnapshots.immutableValue(value);
            if (safeValue == null) {
                sharedState.remove(key);
            } else {
                sharedState.put(key, safeValue);
            }
            intents.add(new SharedStateIntent(key, safeValue));
            return true;
        }

        public ScriptEntitySnapshot.EntityView caster() {
            return caster;
        }

        public ScriptEntitySnapshot.EntityView target() {
            return target;
        }

        public boolean hasTarget() {
            return target.exists();
        }

        public Map<String, Object> targetLocation() {
            return targetLocation;
        }

        public boolean setTarget(String entityUuid) {
            if (!active()) {
                return false;
            }
            String safeUuid = validUuid(entityUuid) ? entityUuid : "";
            target = safeUuid.equals(caster.uuid()) ? caster : target.uuid().equals(safeUuid)
                    ? target
                    : ScriptEntitySnapshot.EntityView.empty();
            targetLocation = target.location();
            variables.put("has_target", target.exists() ? "1" : "0");
            intents.add(new SetTargetIntent(safeUuid));
            return true;
        }

        public boolean runAction(String actionId, Map<String, String> arguments) {
            if (!active() || Texts.isBlank(actionId)) {
                return false;
            }
            intents.add(new RunActionIntent(Texts.normalizeId(actionId),
                    arguments == null ? Map.of() : Map.copyOf(arguments)));
            return true;
        }

        public boolean runActionLine(String line) {
            if (!active() || Texts.isBlank(line)) {
                return false;
            }
            intents.add(new RunActionLineIntent(Texts.toStringSafe(line)));
            return true;
        }

        public boolean castMythic(String skillId, Map<String, String> parameters) {
            if (!active() || Texts.isBlank(skillId)) {
                return false;
            }
            intents.add(new CastMythicIntent(skillId, parameters == null ? Map.of() : Map.copyOf(parameters)));
            return true;
        }

        public boolean applyDamage(String targetUuid,
                String damageTypeId,
                double baseDamage,
                Map<String, Object> damageContext) {
            if (!active() || !validUuid(targetUuid)) {
                return false;
            }
            intents.add(new ApplyDamageIntent(
                    targetUuid,
                    Texts.toStringSafe(damageTypeId),
                    baseDamage,
                    damageContext == null ? Map.of() : ScriptSnapshots.immutableMap(damageContext)));
            return true;
        }

        private synchronized List<Intent> seal() {
            sealed = true;
            return cancellationToken.isCancelled() ? List.of() : List.copyOf(intents);
        }

        private boolean active() {
            return !sealed && !cancellationToken.isCancelled();
        }

        private static boolean validUuid(String value) {
            if (Texts.isBlank(value)) {
                return false;
            }
            try {
                UUID.fromString(value);
                return true;
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }
    }

    private sealed interface Intent permits VariableIntent, SharedStateIntent, SetTargetIntent,
            RunActionIntent, RunActionLineIntent, CastMythicIntent, ApplyDamageIntent {
    }

    private record VariableIntent(String key, String value) implements Intent {
    }

    private record SharedStateIntent(String key, Object value) implements Intent {
    }

    private record SetTargetIntent(String entityUuid) implements Intent {
    }

    private record RunActionIntent(String actionId, Map<String, String> arguments) implements Intent {
    }

    private record RunActionLineIntent(String line) implements Intent {
    }

    private record CastMythicIntent(String skillId, Map<String, String> parameters) implements Intent {
    }

    private record ApplyDamageIntent(String targetUuid,
            String damageTypeId,
            double baseDamage,
            Map<String, Object> damageContext) implements Intent {
    }

    private record IntentResult(boolean success, String message) {

        private static IntentResult ok() {
            return new IntentResult(true, "");
        }

        private static IntentResult failure(String message) {
            return new IntentResult(false, Texts.toStringSafe(message));
        }
    }
}
