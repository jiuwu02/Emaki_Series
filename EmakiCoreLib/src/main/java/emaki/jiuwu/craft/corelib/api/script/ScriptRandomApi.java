package emaki.jiuwu.craft.corelib.api.script;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class ScriptRandomApi {

    public int integer(int min, int max) {
        if (max < min) {
            int swap = min;
            min = max;
            max = swap;
        }
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    public double decimal() {
        return ThreadLocalRandom.current().nextDouble();
    }

    public boolean chance(double percent) {
        return ThreadLocalRandom.current().nextDouble(100D) < percent;
    }

    public Object pick(List<?> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.get(ThreadLocalRandom.current().nextInt(values.size()));
    }
}
