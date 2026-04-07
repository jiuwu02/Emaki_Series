package emaki.jiuwu.craft.strengthen.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.assembly.EmakiItemLayerSnapshot;
import emaki.jiuwu.craft.corelib.assembly.EmakiPresentationEntry;
import emaki.jiuwu.craft.corelib.assembly.EmakiStatContribution;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.model.StrengthenProfile;
import emaki.jiuwu.craft.strengthen.model.StrengthenState;

public final class StrengthenSnapshotBuilder {

    private final EmakiStrengthenPlugin plugin;

    public StrengthenSnapshotBuilder(EmakiStrengthenPlugin plugin) {
        this.plugin = plugin;
    }

    public EmakiItemLayerSnapshot buildLayerSnapshot(StrengthenProfile profile,
            StrengthenState state,
            String materialsSignature) {
        if (profile == null || state == null) {
            return null;
        }
        Map<String, Double> stats = profile.cumulativeStats(state.currentStar());
        Map<String, Object> audit = buildAudit(profile, state, materialsSignature);
        return new EmakiItemLayerSnapshot(
                "strengthen",
                1,
                audit,
                buildStatContributions(stats),
                buildPresentation(profile, state, stats)
        );
    }

    private Map<String, Object> buildAudit(StrengthenProfile profile, StrengthenState state, String materialsSignature) {
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("profile_id", profile.id());
        audit.put("current_star", state.currentStar());
        audit.put("crack_level", state.crackLevel());
        audit.put("success_count", state.successCount());
        audit.put("failure_count", state.failureCount());
        audit.put("first_reach_flags", new ArrayList<>(state.milestoneFlags()));
        audit.put("last_attempt_at", state.lastAttemptAt());
        audit.put("materials_signature", Texts.toStringSafe(materialsSignature));
        audit.put("base_source_signature", state.baseSourceSignature());
        return audit;
    }

    private List<EmakiStatContribution> buildStatContributions(Map<String, Double> stats) {
        List<EmakiStatContribution> result = new ArrayList<>();
        int sequence = 0;
        for (Map.Entry<String, Double> entry : stats.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || Math.abs(entry.getValue()) <= 1.0E-9D) {
                continue;
            }
            result.add(new EmakiStatContribution(entry.getKey(), entry.getValue(), "strengthen:" + entry.getKey(), sequence++));
        }
        return result;
    }

    private List<EmakiPresentationEntry> buildPresentation(StrengthenProfile profile,
            StrengthenState state,
            Map<String, Double> stats) {
        List<String> desiredLines = new ArrayList<>();
        desiredLines.add("<gradient:#F2C46D:#C9703D>强化等级 +%d</gradient>".formatted(state.currentStar()));
        String crackColor = state.crackLevel() > 0 ? "<red>" : "<green>";
        desiredLines.add("<gray>裂痕层数: " + crackColor + state.crackLevel() + "/" + plugin.appConfig().maxCrack() + "</gray>");
        for (StrengthenProfile.Milestone milestone : profile.reachedMilestones(state.currentStar())) {
            desiredLines.add("<gold>星阶里程碑: " + milestone.name() + "</gold>");
            desiredLines.addAll(milestone.presentation());
        }
        if (!stats.isEmpty()) {
            desiredLines.add("<gray>强化加成:</gray>");
            for (Map.Entry<String, Double> entry : stats.entrySet()) {
                if (Math.abs(entry.getValue()) <= 1.0E-9D) {
                    continue;
                }
                desiredLines.add(renderStatLine(profile, entry.getKey(), entry.getValue()));
            }
        }
        List<EmakiPresentationEntry> entries = new ArrayList<>();
        int sequence = 0;
        for (int index = desiredLines.size() - 1; index >= 0; index--) {
            entries.add(new EmakiPresentationEntry("lore_prepend", "", desiredLines.get(index), sequence++, "strengthen"));
        }
        return entries;
    }

    private String renderStatLine(StrengthenProfile profile, String statId, double value) {
        String template = profile.statLineTemplates().get(Texts.lower(statId));
        String sign = value >= 0D ? "+" : "-";
        String formatted = Numbers.formatNumber(Math.abs(value), "0.##");
        if (Texts.isBlank(template)) {
            return "<gray>" + humanize(statId) + ": <gold>" + sign + formatted + "</gold></gray>";
        }
        return template
                .replace("{sign}", sign)
                .replace("{value}", formatted)
                .replace("{id}", statId);
    }

    private String humanize(String statId) {
        String text = Texts.toStringSafe(statId).replace('_', ' ');
        if (text.isBlank()) {
            return "属性";
        }
        return text;
    }
}
