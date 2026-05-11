package emaki.jiuwu.craft.strengthen.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A node in the strengthen branch tree.
 * <p>
 * Each node has:
 * <ul>
 *   <li>branchId: identifier for this branch (root node uses "root")</li>
 *   <li>displayName: human-readable name</li>
 *   <li>stages: the linear star stages within this branch (Map&lt;Integer, StarStage&gt;)</li>
 *   <li>forkAfterStar: after which star this node forks into children (-1 = no fork)</li>
 *   <li>children: sub-branches (empty if no fork)</li>
 * </ul>
 * <p>
 * The tree is traversed using a "branch path" string like "sharp/lethal".
 */
public record StrengthenBranchNode(
        String branchId,
        String displayName,
        Map<Integer, StrengthenRecipe.StarStage> stages,
        int forkAfterStar,
        Map<String, StrengthenBranchNode> children
) {

    public StrengthenBranchNode {
        branchId = branchId == null ? "root" : branchId;
        displayName = displayName == null ? "" : displayName;
        stages = stages == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(stages));
        forkAfterStar = children == null || children.isEmpty() ? -1 : forkAfterStar;
        children = children == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(children));
    }

    /**
     * Traverse the tree following the given branch path and return the deepest reachable node.
     *
     * @param branchPath slash-separated path (e.g. "sharp/lethal"), empty or null for root
     * @return the resolved node, or this node if path is empty
     */
    public StrengthenBranchNode resolveNode(String branchPath) {
        if (branchPath == null || branchPath.isEmpty()) {
            return this;
        }
        String[] segments = branchPath.split("/");
        return resolveNode(segments, 0);
    }

    private StrengthenBranchNode resolveNode(String[] segments, int index) {
        if (index >= segments.length) {
            return this;
        }
        String segment = segments[index];
        StrengthenBranchNode child = children.get(segment);
        if (child == null) {
            return this;
        }
        return child.resolveNode(segments, index + 1);
    }

    /**
     * Collect all stages from root through the branch path, up to the given star level.
     * <p>
     * Stages are collected in order:
     * <ol>
     *   <li>This node's stages with targetStar &lt;= upToStar (and &lt;= forkAfterStar if a fork exists)</li>
     *   <li>If the path continues into a child, recurse into that child</li>
     * </ol>
     *
     * @param branchPath slash-separated path
     * @param upToStar   maximum star level to collect
     * @return combined map of all collected stages in order
     */
    public Map<Integer, StrengthenRecipe.StarStage> collectStages(String branchPath, int upToStar) {
        String[] segments = (branchPath == null || branchPath.isEmpty())
                ? new String[0]
                : branchPath.split("/");
        Map<Integer, StrengthenRecipe.StarStage> result = new LinkedHashMap<>();
        collectStages(segments, 0, upToStar, result);
        return Map.copyOf(result);
    }

    private void collectStages(String[] segments, int index, int upToStar,
                               Map<Integer, StrengthenRecipe.StarStage> result) {
        boolean hasFork = forkAfterStar >= 0 && !children.isEmpty();
        int ceiling = hasFork ? Math.min(upToStar, forkAfterStar) : upToStar;

        for (Map.Entry<Integer, StrengthenRecipe.StarStage> entry : stages.entrySet()) {
            if (entry.getKey() <= ceiling && entry.getValue() != null) {
                result.put(entry.getKey(), entry.getValue());
            }
        }

        if (index < segments.length) {
            String segment = segments[index];
            StrengthenBranchNode child = children.get(segment);
            if (child != null) {
                child.collectStages(segments, index + 1, upToStar, result);
            }
        }
    }

    /**
     * Get the maximum star reachable on the given branch path.
     *
     * @param branchPath slash-separated path
     * @return the highest star level reachable, or 0 if no stages exist
     */
    public int maxReachableStar(String branchPath) {
        String[] segments = (branchPath == null || branchPath.isEmpty())
                ? new String[0]
                : branchPath.split("/");
        return maxReachableStar(segments, 0);
    }

    private int maxReachableStar(String[] segments, int index) {
        if (index < segments.length) {
            String segment = segments[index];
            StrengthenBranchNode child = children.get(segment);
            if (child != null) {
                return child.maxReachableStar(segments, index + 1);
            }
        }

        if (!children.isEmpty() && index >= segments.length) {
            // At a fork point without a chosen branch — max is forkAfterStar
            return forkAfterStar >= 0 ? forkAfterStar : maxStageKey();
        }

        return maxStageKey();
    }

    private int maxStageKey() {
        int max = 0;
        for (int key : stages.keySet()) {
            if (key > max) {
                max = key;
            }
        }
        return max;
    }

    /**
     * Get available children at the fork point reached by the given branch path.
     *
     * @param branchPath slash-separated path
     * @return the children map of the deepest resolved node, or empty map if no fork
     */
    public Map<String, StrengthenBranchNode> childrenAt(String branchPath) {
        StrengthenBranchNode node = resolveNode(branchPath);
        return node.children();
    }

    /**
     * Determine whether the player needs to choose a branch at the current position.
     * <p>
     * This is true when:
     * <ul>
     *   <li>The resolved node has children (a fork exists)</li>
     *   <li>currentStar &gt;= forkAfterStar</li>
     *   <li>The path does not already continue into a child</li>
     * </ul>
     *
     * @param branchPath  current branch path
     * @param currentStar the player's current star level
     * @return true if a fork selection is needed
     */
    public boolean needsForkSelection(String branchPath, int currentStar) {
        String[] segments = (branchPath == null || branchPath.isEmpty())
                ? new String[0]
                : branchPath.split("/");
        return needsForkSelection(segments, 0, currentStar);
    }

    private boolean needsForkSelection(String[] segments, int index, int currentStar) {
        if (index < segments.length) {
            String segment = segments[index];
            StrengthenBranchNode child = children.get(segment);
            if (child != null) {
                return child.needsForkSelection(segments, index + 1, currentStar);
            }
        }

        // We are at the deepest resolved node
        return !children.isEmpty() && forkAfterStar >= 0 && currentStar >= forkAfterStar;
    }

    /**
     * Append a child branch id to the current path.
     *
     * @param currentPath current branch path (may be null or empty)
     * @param childId     the child branch id to append
     * @return the new path string
     */
    public static String appendBranch(String currentPath, String childId) {
        if (currentPath == null || currentPath.isEmpty()) {
            return childId;
        }
        return currentPath + "/" + childId;
    }

    /**
     * Get the list of branch ids that form the given path.
     *
     * @param branchPath slash-separated path
     * @return list of segment ids, empty list if path is null or empty
     */
    public static List<String> pathSegments(String branchPath) {
        if (branchPath == null || branchPath.isEmpty()) {
            return List.of();
        }
        return List.of(branchPath.split("/"));
    }
}
