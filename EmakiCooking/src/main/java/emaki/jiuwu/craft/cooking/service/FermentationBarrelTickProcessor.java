package emaki.jiuwu.craft.cooking.service;

final class FermentationBarrelTickProcessor {

    boolean process(FermentationBarrelState state, long now, boolean pause) {
        if (state == null || pause || !state.fermenting()) {
            return false;
        }
        if (state.finishAtMs() > 0L && now >= state.finishAtMs()) {
            state.setFermenting(false);
            state.setCompleted(true);
            return true;
        }
        return false;
    }

    boolean shouldRemainActive(FermentationBarrelState state) {
        return state != null && (state.fermenting() || state.completed());
    }
}
