package emaki.jiuwu.craft.corelib.api.action.v2;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * One element flowing through an action pipeline: an entity, a location, or nothing.
 *
 * <p>This replaces the v1 model where {@code CoreActionContext.player()} had to serve as both the
 * caster and the target. A pipeline carries a list of subjects; each stage transforms that list.</p>
 */
public sealed interface CoreActionSubject {

    /** An entity subject. The entity may become invalid before the pipeline reaches it. */
    record OfEntity(@NotNull Entity entity) implements CoreActionSubject {

        public OfEntity {
            if (entity == null) {
                throw new IllegalArgumentException("entity must not be null");
            }
        }

        @Override
        public @Nullable Location location() {
            return entity.getLocation();
        }

        @Override
        public boolean valid() {
            return entity.isValid();
        }
    }

    /** A location subject, used by block and particle style stages. */
    record OfLocation(@NotNull Location location) implements CoreActionSubject {

        public OfLocation {
            if (location == null) {
                throw new IllegalArgumentException("location must not be null");
            }
            location = location.clone();
        }

        @Override
        public boolean valid() {
            return location.getWorld() != null;
        }
    }

    /** No subject. Produced when a trigger has no caster, for example a console invocation. */
    record Absent() implements CoreActionSubject {

        private static final Absent INSTANCE = new Absent();

        @Override
        public @Nullable Location location() {
            return null;
        }

        @Override
        public boolean valid() {
            return false;
        }
    }

    /** {@return the spatial position of this subject, or {@code null} when it has none} */
    @Nullable
    Location location();

    /** {@return whether this subject is still usable at this moment} */
    boolean valid();

    /** {@return the entity when this subject is an entity, otherwise {@code null}} */
    default @Nullable Entity entityOrNull() {
        return this instanceof OfEntity of ? of.entity() : null;
    }

    /** {@return a subject wrapping {@code entity}, or {@link Absent} when it is {@code null}} */
    static @NotNull CoreActionSubject of(@Nullable Entity entity) {
        return entity == null ? absent() : new OfEntity(entity);
    }

    /** {@return a subject wrapping {@code location}, or {@link Absent} when it is {@code null}} */
    static @NotNull CoreActionSubject of(@Nullable Location location) {
        return location == null ? absent() : new OfLocation(location);
    }

    /** {@return the shared absent subject} */
    static @NotNull CoreActionSubject absent() {
        return Absent.INSTANCE;
    }
}
