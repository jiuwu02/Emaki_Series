package emaki.jiuwu.craft.corelib.apiimpl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.action.pipeline.registry.RegisteredStage;
import emaki.jiuwu.craft.corelib.action.pipeline.registry.RegisteredTrigger;
import emaki.jiuwu.craft.corelib.action.pipeline.registry.StageRegistry;
import emaki.jiuwu.craft.corelib.api.action.CoreActionGate;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSource;
import emaki.jiuwu.craft.corelib.api.action.CoreActionStage;
import emaki.jiuwu.craft.corelib.api.action.CoreStageKind;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.api.action.descriptor.CoreActionStageDescriptor;
import emaki.jiuwu.craft.corelib.api.action.descriptor.CoreActionTriggerDescriptor;
import emaki.jiuwu.craft.corelib.api.text.Texts;

final class ActionDescriptorApiService {

    private final EmakiCoreLibPlugin plugin;

    ActionDescriptorApiService(EmakiCoreLibPlugin plugin) {
        this.plugin = plugin;
    }

    List<CoreActionStageDescriptor> stages() {
        StageRegistry registry = plugin.stageRegistry();
        if (registry == null) {
            return List.of();
        }
        List<CoreActionStageDescriptor> descriptors = new ArrayList<>();
        registry.sources().all().stream()
                .filter(ActionDescriptorApiService::live)
                .forEach(entry -> descriptors.add(stage(entry)));
        registry.gates().all().stream()
                .filter(ActionDescriptorApiService::live)
                .forEach(entry -> descriptors.add(stage(entry)));
        registry.actions().all().stream()
                .filter(ActionDescriptorApiService::live)
                .forEach(entry -> descriptors.add(stage(entry)));
        return List.copyOf(descriptors);
    }

    Optional<CoreActionStageDescriptor> stage(String id) {
        if (Texts.isBlank(id)) {
            return Optional.empty();
        }
        String key = Texts.lower(Texts.trim(id));
        return stages().stream().filter(descriptor -> descriptor.id().equals(key)).findFirst();
    }

    List<CoreActionTriggerDescriptor> triggers() {
        return plugin.triggerRegistry().all().stream()
                .filter(entry -> entry.owner() == null || entry.owner().isEnabled())
                .map(this::trigger)
                .toList();
    }

    Optional<CoreActionTriggerDescriptor> trigger(String id) {
        RegisteredTrigger entry = plugin.triggerRegistry().lookup(id);
        return entry == null ? Optional.empty() : Optional.of(trigger(entry));
    }

    private static boolean live(RegisteredStage entry) {
        return entry.owner() == null || entry.owner().isEnabled();
    }

    private CoreActionStageDescriptor stage(RegisteredStage entry) {
        try {
            return switch (entry.kind()) {
                case SOURCE -> source(entry, (CoreActionSource) entry.stage());
                case GATE -> gate(entry, (CoreActionGate) entry.stage());
                case ACTION -> action(entry, (CoreActionStage) entry.stage());
            };
        } catch (RuntimeException | LinkageError exception) {
            plugin.getLogger().warning("Action stage metadata failed for " + entry.id() + ": "
                    + Texts.toStringSafe(exception.getMessage()));
            return new CoreActionStageDescriptor(entry.id(), entry.kind(), entry.ownerName(), "", "", "",
                    List.of(), CoreTargetRequirement.NONE, Set.of(), Set.of(), Set.of());
        }
    }

    private CoreActionTriggerDescriptor trigger(RegisteredTrigger entry) {
        String description;
        try {
            description = entry.trigger().description();
        } catch (RuntimeException | LinkageError exception) {
            plugin.getLogger().warning("Action trigger metadata failed for " + entry.id() + ": "
                    + Texts.toStringSafe(exception.getMessage()));
            description = "";
        }
        return new CoreActionTriggerDescriptor(entry.id(), entry.ownerName(), description, entry.contract());
    }

    private static CoreActionStageDescriptor source(RegisteredStage entry, CoreActionSource source) {
        return new CoreActionStageDescriptor(entry.id(), CoreStageKind.SOURCE, entry.ownerName(),
                source.category(), source.description(), "", source.parameters(), CoreTargetRequirement.NONE,
                Set.of(), Set.of(), Set.of());
    }

    private static CoreActionStageDescriptor gate(RegisteredStage entry, CoreActionGate gate) {
        return new CoreActionStageDescriptor(entry.id(), CoreStageKind.GATE, entry.ownerName(),
                gate.category(), gate.description(), "", gate.parameters(), CoreTargetRequirement.NONE,
                Set.of(), gate.providedContext(), gate.providedVariables());
    }

    private static CoreActionStageDescriptor action(RegisteredStage entry, CoreActionStage action) {
        return new CoreActionStageDescriptor(entry.id(), CoreStageKind.ACTION, entry.ownerName(),
                action.category(), action.description(), action.version(), action.parameters(),
                action.targetRequirement(), action.requiredContext(), Set.of(), Set.of());
    }
}
