package emaki.jiuwu.craft.strengthen.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.model.StrengthenBranchNode;
import emaki.jiuwu.craft.strengthen.model.StrengthenRecipe;

public final class StrengthenRoutePreviewService {

    private final EmakiStrengthenPlugin plugin;

    public StrengthenRoutePreviewService(EmakiStrengthenPlugin plugin) {
        this.plugin = plugin;
    }

    public Map<String, ?> preview(String recipeId) {
        StrengthenRecipe recipe = plugin.recipeLoader().get(recipeId);
        if (recipe == null) {
            return Map.of(
                    "recipeId", Texts.toStringSafe(recipeId),
                    "displayName", "",
                    "branching", false,
                    "nodes", List.of(),
                    "edges", List.of(),
                    "branches", List.of(),
                    "warnings", List.of(warning("recipe_not_found", "Recipe not found."))
            );
        }
        RouteBuilder builder = new RouteBuilder(recipe);
        if (recipe.branchTree() == null) {
            builder.linear();
        } else {
            builder.branchTree(recipe.branchTree(), "", null, 0);
        }
        return Map.of(
                "recipeId", recipe.id(),
                "displayName", recipe.displayName(),
                "branching", recipe.branchTree() != null,
                "maxStar", recipe.limits().maxStar(),
                "nodes", builder.nodes,
                "edges", builder.edges,
                "branches", builder.branches,
                "warnings", builder.warnings
        );
    }

    private final class RouteBuilder {
        private final StrengthenRecipe recipe;
        private final List<Map<String, ?>> nodes = new ArrayList<>();
        private final List<Map<String, ?>> edges = new ArrayList<>();
        private final List<Map<String, ?>> branches = new ArrayList<>();
        private final List<Map<String, ?>> warnings = new ArrayList<>();

        private RouteBuilder(StrengthenRecipe recipe) {
            this.recipe = recipe;
        }

        private void linear() {
            List<Integer> stars = sortedStars(recipe.stars());
            String previousId = null;
            int previousStar = 0;
            for (Integer star : stars) {
                StrengthenRecipe.StarStage stage = recipe.stars().get(star);
                String nodeId = nodeId("root", star);
                nodes.add(node(nodeId, "", "root", recipe.displayName(), stage, previousStar));
                if (previousId != null) {
                    edges.add(edge(previousId, nodeId, "next", ""));
                }
                previousId = nodeId;
                previousStar = star;
            }
            if (nodes.isEmpty()) {
                warnings.add(warning("empty_stars", "Recipe has no star stages."));
            }
        }

        private void branchTree(StrengthenBranchNode branch, String path, String parentNodeId, int parentStar) {
            String branchId = Texts.isBlank(path) ? "root" : path;
            branches.add(Map.of(
                    "path", path,
                    "id", branch.branchId(),
                    "displayName", branch.displayName(),
                    "forkAfterStar", branch.forkAfterStar(),
                    "children", branch.children().keySet().stream().toList()
            ));
            List<Integer> stars = sortedStars(branch.stages());
            String previousId = parentNodeId;
            int previousStar = parentStar;
            String firstNodeId = null;
            for (Integer star : stars) {
                StrengthenRecipe.StarStage stage = branch.stages().get(star);
                String currentId = nodeId(branchId, star);
                if (firstNodeId == null) {
                    firstNodeId = currentId;
                    if (parentNodeId != null) {
                        edges.add(edge(parentNodeId, currentId, "branch", branch.displayName()));
                    }
                } else if (previousId != null) {
                    edges.add(edge(previousId, currentId, "next", ""));
                }
                nodes.add(node(currentId, path, branch.branchId(), branch.displayName(), stage, previousStar));
                previousId = currentId;
                previousStar = star;
            }
            String forkNodeId = branch.forkAfterStar() > 0 ? nodeId(branchId, branch.forkAfterStar()) : previousId;
            int forkStar = branch.forkAfterStar() > 0 ? branch.forkAfterStar() : previousStar;
            for (Map.Entry<String, StrengthenBranchNode> child : branch.children().entrySet()) {
                String childPath = StrengthenBranchNode.appendBranch(path, child.getKey());
                branchTree(child.getValue(), childPath, forkNodeId, forkStar);
            }
        }

        private Map<String, ?> node(String id, String branchPath, String branchId, String branchName, StrengthenRecipe.StarStage stage, int previousStar) {
            int star = stage.targetStar();
            Map<String, Double> previousStats = recipe.cumulativeVariables(Math.max(0, previousStar), branchPath);
            Map<String, Double> currentStats = recipe.cumulativeVariables(star, branchPath);
            Map<String, Double> delta = delta(previousStats, currentStats);
            return Map.ofEntries(
                    Map.entry("id", id),
                    Map.entry("star", star),
                    Map.entry("branchPath", branchPath),
                    Map.entry("branchId", branchId),
                    Map.entry("branchName", branchName),
                    Map.entry("stageName", stage.name()),
                    Map.entry("successRate", recipe.successRateForTargetStar(plugin.appConfig().successRates(), star)),
                    Map.entry("materials", materials(stage.materials())),
                    Map.entry("statsDelta", delta),
                    Map.entry("cumulativeStats", currentStats),
                    Map.entry("cumulativeAttributes", recipe.cumulativeAttributes(star, branchPath)),
                    Map.entry("skillIds", recipe.cumulativeSkillIds(star, branchPath)),
                    Map.entry("hasSuccessActions", !stage.successActions().isEmpty()),
                    Map.entry("hasFailureActions", !stage.failureActions().isEmpty())
            );
        }
    }

    private List<Integer> sortedStars(Map<Integer, ?> stages) {
        return stages.keySet().stream().sorted(Comparator.naturalOrder()).toList();
    }

    private List<Map<String, ?>> materials(List<StrengthenRecipe.StarStageMaterial> materials) {
        List<Map<String, ?>> result = new ArrayList<>();
        for (StrengthenRecipe.StarStageMaterial material : materials) {
            result.add(Map.of(
                    "item", material.item(),
                    "amount", material.amount(),
                    "optional", material.optional(),
                    "protection", material.protection(),
                    "temperBoost", material.temperBoost()
            ));
        }
        return result;
    }

    private Map<String, Double> delta(Map<String, Double> previous, Map<String, Double> current) {
        Map<String, Double> result = new LinkedHashMap<>();
        for (String key : current.keySet()) {
            double value = current.getOrDefault(key, 0D) - previous.getOrDefault(key, 0D);
            if (Math.abs(value) > 1.0E-9D) {
                result.put(key, value);
            }
        }
        return result;
    }

    private Map<String, String> warning(String type, String message) {
        return Map.of("type", type, "message", message);
    }

    private Map<String, String> edge(String from, String to, String type, String label) {
        return Map.of("from", Texts.toStringSafe(from), "to", Texts.toStringSafe(to), "type", type, "label", Texts.toStringSafe(label));
    }

    private String nodeId(String branchPath, int star) {
        return (Texts.isBlank(branchPath) ? "root" : branchPath) + ":+" + star;
    }
}
