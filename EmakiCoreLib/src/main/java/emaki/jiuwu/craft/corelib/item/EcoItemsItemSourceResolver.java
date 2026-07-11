package emaki.jiuwu.craft.corelib.item;

import org.bukkit.inventory.ItemStack;

import com.willfp.ecoitems.items.EcoItem;
import com.willfp.ecoitems.items.EcoItems;
import com.willfp.ecoitems.items.ItemUtilsKt;

import emaki.jiuwu.craft.corelib.text.Texts;

/**
 * Item source resolver bridging Auxilor's EcoItems plugin.
 *
 * <p>EcoItems is built on the eco / libreforge framework and registers its
 * items when the plugin enables, so there is no dedicated "items loaded" event
 * to hook. The managed resolver relies on {@link DirectAccessor#detectLoaded()}
 * probing the registry and leaves {@code registerLoadEventListener} as the
 * default no-op.</p>
 *
 * <p>EcoItems is accessed through its public API just like the other item
 * sources (Nexo / Oraxen / MMOItems). The API types {@link EcoItem} and
 * {@link EcoItems} extend deep eco / libreforge framework super-types
 * ({@code EcoItem} implements {@code com.willfp.libreforge.Holder} and
 * {@code com.willfp.eco.core.registry.Registrable}; {@code EcoItems} extends
 * {@code ConfigCategory}), so both {@code eco} and {@code libreforge} must be on
 * the compile classpath as {@code provided} dependencies for javac to resolve the
 * inheritance hierarchy; none of them are shaded into the jar. Coordinates are
 * pinned to Auxilor's latest public CalVer generation (eco {@code 2026.28},
 * libreforge / EcoItems / libreforge-loader {@code 2026.27}), and the EcoItems
 * classifier choice is critical (verified by bytecode inspection + a real javac
 * compile): EcoItems must use the <b>plain</b> jar (no classifier), whose {@code
 * EcoItems} super-type is the standard {@code com.willfp.libreforge.loader.configs.
 * ConfigCategory} supplied by the public {@code libreforge-loader} artifact. The
 * {@code classifier=all} fat jar instead relocates that super-type to {@code
 * com.willfp.ecoitems.libreforge.loader.configs.ConfigCategory}, a class shipped
 * in no public artifact, which makes javac fail with "cannot find ConfigCategory
 * class file". {@code libreforge-loader} provides {@code ConfigCategory} /
 * {@code LegacyLocation} / {@code LibreforgePlugin}; {@code libreforge} (the
 * {@code classifier=all} fat jar) provides {@code Holder}; {@code eco} provides
 * {@code Registrable} / {@code FastItemStack}. libreforge's pom drags in {@code
 * libreforge.core:common}, which lives only in Auxilor's private repo, so that
 * group is excluded (the all jar is self-contained). Every call is guarded by
 * {@code RuntimeException | LinkageError} so a missing or incompatible EcoItems
 * never breaks CoreLib at runtime.</p>
 */
final class EcoItemsItemSourceResolver
        extends AbstractManagedItemSourceResolver<EcoItemsItemSourceResolver.DirectAccessor> {

    private static final String PLUGIN_NAME = "EcoItems";

    EcoItemsItemSourceResolver() {
        this(PluginAvailability.BUKKIT, new DirectAccessor());
    }

    EcoItemsItemSourceResolver(PluginAvailability pluginAvailability, DirectAccessor accessor) {
        super(pluginAvailability, accessor == null ? new DirectAccessor() : accessor);
    }

    @Override
    public String id() {
        return "corelib_ecoitems";
    }

    @Override
    public int priority() {
        return 95;
    }

    @Override
    public String pluginName() {
        return PLUGIN_NAME;
    }

    @Override
    protected ItemSourceType sourceType() {
        return ItemSourceType.ECOITEMS;
    }

    @Override
    protected String waitingDetail() {
        return "EcoItems items are not loaded yet.";
    }

    static final class DirectAccessor implements AbstractManagedItemSourceResolver.Accessor {

        private String failureReason = "";

        @Override
        public boolean ensureAvailable() {
            try {
                EcoItems.INSTANCE.values();
                failureReason = "";
                return true;
            } catch (RuntimeException | LinkageError exception) {
                failureReason = describe(exception);
                return false;
            }
        }

        @Override
        public String failureReason() {
            return failureReason;
        }

        @Override
        public boolean detectLoaded() {
            try {
                return EcoItems.INSTANCE.values() != null;
            } catch (RuntimeException | LinkageError exception) {
                failureReason = describe(exception);
                return false;
            }
        }

        @Override
        public String identifyIdentifier(ItemStack itemStack) {
            if (itemStack == null) {
                return "";
            }
            try {
                EcoItem ecoItem = ItemUtilsKt.getEcoItem(itemStack);
                return ecoItem == null ? "" : Texts.trim(ecoItem.getID());
            } catch (RuntimeException | LinkageError exception) {
                return "";
            }
        }

        @Override
        public ItemStack createItem(String identifier, int amount) {
            if (Texts.isBlank(identifier)) {
                return null;
            }
            try {
                EcoItem ecoItem = EcoItems.INSTANCE.getByID(identifier);
                return ecoItem == null ? null : ecoItem.getItemStack();
            } catch (RuntimeException | LinkageError exception) {
                return null;
            }
        }

        @Override
        public String displayName(String identifier) {
            if (Texts.isBlank(identifier)) {
                return null;
            }
            try {
                EcoItem ecoItem = EcoItems.INSTANCE.getByID(identifier);
                if (ecoItem == null) {
                    return null;
                }
                String displayName = ecoItem.getDisplayName();
                return Texts.isBlank(displayName) ? null : displayName;
            } catch (RuntimeException | LinkageError exception) {
                return null;
            }
        }

        @Override
        public void reset() {
            failureReason = "";
        }

        private String describe(Throwable throwable) {
            return throwable.getMessage() == null
                    ? throwable.getClass().getSimpleName()
                    : throwable.getMessage();
        }
    }
}
