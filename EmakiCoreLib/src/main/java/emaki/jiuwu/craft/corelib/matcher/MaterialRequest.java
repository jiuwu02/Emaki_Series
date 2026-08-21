package emaki.jiuwu.craft.corelib.matcher;

import org.jetbrains.annotations.NotNull;

public record MaterialRequest(@NotNull Matcher matcher, int quantity) {

    public MaterialRequest {
        quantity = Math.max(0, quantity);
    }
}
