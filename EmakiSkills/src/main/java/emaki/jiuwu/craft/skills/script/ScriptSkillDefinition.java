package emaki.jiuwu.craft.skills.script;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.skills.api.ExternalSkillDefinition;

public final class ScriptSkillDefinition implements ExternalSkillDefinition {

    private final String id;
    private final String displayName;
    private final List<String> description;
    private final String iconMaterial;
    private final String activationType;
    private final List<String> passiveTriggers;
    private final long cooldownTicks;
    private final long globalCooldownTicks;
    private final Map<String, List<String>> scriptLines;
    private final Map<String, List<String>> scriptConditions;
    private final List<String> tags;
    private final List<String> loreAliases;
    private final String pdcSkillId;
    private final String uiCategory;
    private final int sortOrder;
    private final boolean showInSlots;
    private final boolean enabled;
    private final String mythicSkill;

    private ScriptSkillDefinition(String id,
            String displayName,
            List<String> description,
            String iconMaterial,
            String activationType,
            List<String> passiveTriggers,
            long cooldownTicks,
            long globalCooldownTicks,
            Map<String, List<String>> scriptLines,
            Map<String, List<String>> scriptConditions,
            List<String> tags,
            List<String> loreAliases,
            String pdcSkillId,
            String uiCategory,
            int sortOrder,
            boolean showInSlots,
            boolean enabled,
            String mythicSkill) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.iconMaterial = iconMaterial;
        this.activationType = activationType;
        this.passiveTriggers = passiveTriggers;
        this.cooldownTicks = cooldownTicks;
        this.globalCooldownTicks = globalCooldownTicks;
        this.scriptLines = scriptLines;
        this.scriptConditions = scriptConditions;
        this.tags = tags;
        this.loreAliases = loreAliases;
        this.pdcSkillId = pdcSkillId;
        this.uiCategory = uiCategory;
        this.sortOrder = sortOrder;
        this.showInSlots = showInSlots;
        this.enabled = enabled;
        this.mythicSkill = mythicSkill;
    }

    public static @Nullable ScriptSkillDefinition create(@Nullable SkillScriptPayload payload) {
        if (payload == null) {
            return null;
        }
        String id = Texts.normalizeId(payload.id());
        if (id.isBlank()) {
            return null;
        }
        String displayName = Texts.isBlank(payload.displayName()) ? id : payload.displayName().trim();
        List<String> description = cleanedStrings(payload.description());
        String iconMaterial = payload.iconMaterial().trim();
        String activationType = "passive".equals(
                Texts.lower(payload.activationType()).replace('-', '_').trim())
                        ? "PASSIVE"
                        : "ACTIVE";
        List<String> passiveTriggers = triggerIds(payload.passiveTriggers());
        long cooldownTicks = Math.max(0L, payload.cooldownTicks());
        long globalCooldownTicks = Math.max(0L, payload.globalCooldownTicks());
        Map<String, List<String>> scriptLines = phaseLines(payload.scriptLines());
        Map<String, List<String>> scriptConditions = phaseLines(payload.scriptConditions());
        List<String> tags = normalizedIds(payload.tags());
        List<String> loreAliases = cleanedStrings(payload.loreAliases());
        String pdcSkillId = payload.pdcSkillId().trim();
        String uiCategory = Texts.isBlank(payload.uiCategory()) ? "default" : payload.uiCategory().trim();
        int sortOrder = payload.sortOrder();
        boolean showInSlots = payload.showInSlots() == null || payload.showInSlots();
        boolean enabled = payload.enabled() == null || payload.enabled();
        String mythicSkill = payload.mythicSkill().trim();
        return new ScriptSkillDefinition(
                id,
                displayName,
                description,
                iconMaterial,
                activationType,
                passiveTriggers,
                cooldownTicks,
                globalCooldownTicks,
                scriptLines,
                scriptConditions,
                tags,
                loreAliases,
                pdcSkillId,
                uiCategory,
                sortOrder,
                showInSlots,
                enabled,
                mythicSkill);
    }

    @Override
    public @NotNull String id() {
        return id;
    }

    @Override
    public @NotNull String displayName() {
        return displayName;
    }

    @Override
    public @NotNull List<String> description() {
        return description;
    }

    @Override
    public @NotNull String iconMaterial() {
        return iconMaterial;
    }

    @Override
    public @NotNull String activationType() {
        return activationType;
    }

    @Override
    public @NotNull List<String> passiveTriggers() {
        return passiveTriggers;
    }

    @Override
    public long cooldownTicks() {
        return cooldownTicks;
    }

    @Override
    public long globalCooldownTicks() {
        return globalCooldownTicks;
    }

    @Override
    public @NotNull Map<String, List<String>> scriptLines() {
        return scriptLines;
    }

    @Override
    public @NotNull Map<String, List<String>> scriptConditions() {
        return scriptConditions;
    }

    @Override
    public @NotNull List<String> tags() {
        return tags;
    }

    @Override
    public @NotNull List<String> loreAliases() {
        return loreAliases;
    }

    @Override
    public @NotNull String pdcSkillId() {
        return pdcSkillId;
    }

    @Override
    public @NotNull String uiCategory() {
        return uiCategory;
    }

    @Override
    public int sortOrder() {
        return sortOrder;
    }

    @Override
    public boolean showInSlots() {
        return showInSlots;
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public @NotNull String mythicSkill() {
        return mythicSkill;
    }

    private static Map<String, List<String>> phaseLines(Map<String, List<String>> raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : raw.entrySet()) {
            SkillScriptPhase phase = SkillScriptPhase.fromString(entry.getKey());
            List<String> source = entry.getValue();
            if (phase == null || source == null || source.isEmpty()) {
                continue;
            }
            List<String> lines = new ArrayList<>(source.size());
            for (String line : source) {
                if (Texts.isNotBlank(line)) {
                    lines.add(line.trim());
                }
            }
            if (!lines.isEmpty()) {
                result.put(phase.configKey(), List.copyOf(lines));
            }
        }
        return result.isEmpty() ? Map.of() : Map.copyOf(result);
    }

    private static List<String> cleanedStrings(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>(raw.size());
        for (String value : raw) {
            String text = Texts.toStringSafe(value).trim();
            if (!text.isEmpty()) {
                result.add(text);
            }
        }
        return result.isEmpty() ? List.of() : List.copyOf(result);
    }

    private static List<String> triggerIds(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (String value : raw) {
            String id = Texts.lower(value).replace('-', '_').trim();
            if (!id.isBlank()) {
                ids.add(id);
            }
        }
        return ids.isEmpty() ? List.of() : List.copyOf(ids);
    }

    private static List<String> normalizedIds(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (String value : raw) {
            String id = Texts.normalizeId(value).replace('-', '_');
            if (!id.isBlank()) {
                ids.add(id);
            }
        }
        return ids.isEmpty() ? List.of() : List.copyOf(ids);
    }
}
