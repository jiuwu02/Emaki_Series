package emaki.jiuwu.craft.corelib.progression;

import java.util.ArrayList;
import java.util.List;

public final class CompositeProgression<T> implements Progression<T> {

    private final List<Progression<T>> delegates;
    private final T fallback;

    public CompositeProgression(List<Progression<T>> delegates, T fallback) {
        this.delegates = delegates == null ? List.of() : List.copyOf(delegates);
        this.fallback = fallback;
    }

    @Override
    public T valueAt(int level) {
        for (Progression<T> delegate : delegates) {
            if (delegate == null) {
                continue;
            }
            T value = delegate.valueAt(level);
            if (value != null) {
                return value;
            }
        }
        return fallback;
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public static final class Builder<T> {
        private final List<Progression<T>> delegates = new ArrayList<>();
        private T fallback;

        public Builder<T> add(Progression<T> progression) {
            if (progression != null) {
                delegates.add(progression);
            }
            return this;
        }

        public Builder<T> fallback(T fallback) {
            this.fallback = fallback;
            return this;
        }

        public CompositeProgression<T> build() {
            return new CompositeProgression<>(delegates, fallback);
        }
    }
}
