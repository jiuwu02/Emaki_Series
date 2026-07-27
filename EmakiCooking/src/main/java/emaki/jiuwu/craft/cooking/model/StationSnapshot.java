package emaki.jiuwu.craft.cooking.model;


































public record StationSnapshot(
        StationType stationType,
        String worldName,
        int x,
        int y,
        int z,
        String stationBlockId,
        String heatBlockId,
        boolean burning,
        long burningRemainingSeconds,
        int heat,
        int moisture,
        int steam,
        String inputItemName,
        String inputItemSource,
        int inputAmount,
        int ingredientCount,
        String recipeId,
        String recipeName,
        int progressCurrent,
        int progressTarget,
        double progressPercent,
        boolean completed,
        String fluidName,
        int fluidAmountMl,
        String playerName) {

    public StationSnapshot {
        worldName = safe(worldName);
        stationBlockId = safe(stationBlockId);
        heatBlockId = safe(heatBlockId);
        inputItemName = safe(inputItemName);
        inputItemSource = safe(inputItemSource);
        recipeId = safe(recipeId);
        recipeName = safe(recipeName);
        fluidName = safe(fluidName);
        playerName = safe(playerName);
        burningRemainingSeconds = Math.max(0L, burningRemainingSeconds);
        if (Double.isNaN(progressPercent) || Double.isInfinite(progressPercent)) {
            progressPercent = 0.0D;
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
