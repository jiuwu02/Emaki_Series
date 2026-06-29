package emaki.jiuwu.craft.corelib.api.script;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.graalvm.polyglot.HostAccess;

public final class ScriptRandomApi {

    @HostAccess.Export
    public int integer(int min, int max) {
        if (max < min) {
            int swap = min;
            min = max;
            max = swap;
        }
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    @HostAccess.Export
    public double decimal() {
        return ThreadLocalRandom.current().nextDouble();
    }

    @HostAccess.Export
    public boolean chance(double percent) {
        return ThreadLocalRandom.current().nextDouble(100D) < percent;
    }

    @HostAccess.Export
    public Object pick(List<?> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.get(ThreadLocalRandom.current().nextInt(values.size()));
    }
}
