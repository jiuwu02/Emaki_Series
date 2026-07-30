package emaki.jiuwu.craft.strengthen.api.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A node in a strengthen recipe's branching star tree.
 *
 * <p>Each node owns a set of {@link StrengthenRecipe.StarStage star stages} and
 * may fork into named child branches after {@code forkAfterStar}. Branch paths
 * are slash-separated child ids (e.g. {@code "fire/blaze"}). The navigation
 * helpers resolve a path to a node, collect the stages reachable along a path up
 * to a star, compute the maximum reachable star and determine whether a fork
 * selection is pending.
 *
 * @param branchId     this node's branch id ({@code "root"} when {@code null})
 * @param displayName  the display name; never {@code null}
 * @param stages       star level to stage mapping owned by this node
 * @param forkAfterStar the star after which children fork ({@code -1} when there
 *                     are no children)
 * @param children     child branch nodes keyed by branch id; never {@code null}
 */
public record StrengthenBranchNode(
        String branchId,
        String displayName,
        Map<Integer, StrengthenRecipe.StarStage> stages,
        int forkAfterStar,
        Map<String, StrengthenBranchNode> children
) {

    /** Canonical constructor; applies defaults and copies the maps. */
    public StrengthenBranchNode {
        branchId = branchId == null ? "root" : branchId;
        displayName = displayName == null ? "" : displayName;
        stages = stages == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(stages));
        forkAfterStar = children == null || children.isEmpty() ? -1 : forkAfterStar;
        children = children == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(children));
    }

    /**
     * Resolves the node reached by following a branch path from this node.
     *
     * @param branchPath the slash-separated branch path; blank returns this node
     * @return the resolved node, or the deepest reachable node if the path runs
     *         past a leaf
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
     * Collects all star stages reachable along a branch path up to a star level.
     *
     * @param branchPath the slash-separated branch path; blank uses the root
     *                   chain only
     * @param upToStar   the inclusive star ceiling
     * @return an immutable star-to-stage map of the reachable stages
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
     * {@return the highest star reachable along the given branch path}
     *
     * @param branchPath the slash-separated branch path
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
     * {@return the child branches available at the node resolved by the path}
     *
     * @param branchPath the slash-separated branch path
     */
    public Map<String, StrengthenBranchNode> childrenAt(String branchPath) {
        StrengthenBranchNode node = resolveNode(branchPath);
        return node.children();
    }

    /**
     * Determines whether the player must choose a fork to continue.
     *
     * @param branchPath  the current slash-separated branch path
     * @param currentStar the current star level
     * @return {@code true} when a fork selection is required to advance
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

        return !children.isEmpty() && forkAfterStar >= 0 && currentStar >= forkAfterStar;
    }

    /**
     * Appends a child branch id to a branch path.
     *
     * @param currentPath the existing branch path; blank starts a new path
     * @param childId     the child branch id to append
     * @return the combined branch path
     */
    public static String appendBranch(String currentPath, String childId) {
        if (currentPath == null || currentPath.isEmpty()) {
            return childId;
        }
        return currentPath + "/" + childId;
    }

    /**
     * Splits a branch path into its individual segment ids.
     *
     * @param branchPath the slash-separated branch path
     * @return the ordered segment list; empty when the path is blank
     */
    public static List<String> pathSegments(String branchPath) {
        if (branchPath == null || branchPath.isEmpty()) {
            return List.of();
        }
        return List.of(branchPath.split("/"));
    }
}
