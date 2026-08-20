package emaki.jiuwu.craft.item.service;

public final class ItemStateDerivationGuard {

    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    private ItemStateDerivationGuard() {
    }

    public static int depth() {
        return DEPTH.get()[0];
    }

    public static boolean enter(int maxDepth) {
        int[] holder = DEPTH.get();
        if (holder[0] >= Math.max(1, maxDepth)) {
            return false;
        }
        holder[0]++;
        return true;
    }

    public static void leave() {
        int[] holder = DEPTH.get();
        holder[0] = Math.max(0, holder[0] - 1);
        if (holder[0] == 0) {
            DEPTH.remove();
        }
    }
}
