package emaki.jiuwu.craft.corelib.script.bridge;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.action.CoreActionKey;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;

public final class ScriptStageContextExport {

    private final CoreStageContext context;

    public ScriptStageContextExport(@NotNull CoreStageContext context) {
        this.context = context;
    }

    @Nullable
    public String getSourcePlugin() {
        return context.sourcePlugin() == null ? null : context.sourcePlugin().getName();
    }

    @NotNull
    public ScriptSubjectExport getCaster() {
        return new ScriptSubjectExport(context.caster());
    }

    @NotNull
    public List<ScriptSubjectExport> getTargets() {
        List<ScriptSubjectExport> exports = new ArrayList<>();
        for (CoreActionSubject subject : context.targets()) {
            exports.add(new ScriptSubjectExport(subject));
        }
        return exports;
    }

    @NotNull
    public ScriptSubjectExport getCurrentTarget() {
        return new ScriptSubjectExport(context.currentTarget());
    }

    public int getCurrentTargetIndex() {
        return context.currentTargetIndex();
    }

    @NotNull
    public ScriptLocationExport getOrigin() {
        return new ScriptLocationExport(context.origin());
    }

    @NotNull
    public String getPhase() {
        return context.phase();
    }

    public boolean isSilent() {
        return context.silent();
    }

    @Nullable
    public String getVariable(@Nullable String name) {
        return context.variable(name).orElse(null);
    }

    public boolean hasVariable(@Nullable String name) {
        return context.variable(name).isPresent();
    }

    @NotNull
    public String render(@Nullable String template) {
        return context.render(template);
    }

    @NotNull
    public List<String> getPresentKeys() {
        List<String> names = new ArrayList<>();
        for (CoreActionKey<?> key : context.presentKeys()) {
            names.add(key.name());
        }
        return names;
    }

    @Override
    public String toString() {
        return "stageContext[phase=" + context.phase() + ", targets=" + context.targets().size() + "]";
    }
}
