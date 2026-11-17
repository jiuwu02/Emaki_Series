package emaki.jiuwu.craft.strengthen.enhancement.mastery;

import java.util.UUID;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.corelib.log.LogTags;
import emaki.jiuwu.craft.strengthen.log.StrengthenLogTag;
import java.util.Map;
import java.util.logging.Level;

public final class MasteryProgressService {

    private final EmakiStrengthenPlugin plugin;
    private final MasteryLayerCodec codec;

    public MasteryProgressService(@NotNull EmakiStrengthenPlugin plugin, @NotNull MasteryLayerCodec codec) {
        this.plugin = plugin;
        this.codec = codec;
    }

    public boolean recordAttempt(@Nullable ItemStack target, boolean success) {
        double gain = resolveGain(success);
        if (gain <= 0D || target == null || target.getType().isAir()) {
            return false;
        }
        try {
            MasteryLayer existing = codec.read(target);
            MasteryLayer base = existing == null
                    ? MasteryLayer.empty(UUID.randomUUID().toString(), resolveSoftCap())
                    : existing;
            if (Texts.isBlank(base.instanceId())) {
                base = base.withInstanceId(UUID.randomUUID().toString());
            }
            int configuredCap = resolveSoftCap();
            if (base.softCap() != configuredCap) {
                base = base.withSoftCap(configuredCap);
            }
            return codec.write(target, base.withGainedExp(gain));
        } catch (RuntimeException | LinkageError exception) {
            warn(LogTags.text(plugin, "console.mastery_write_failed", Map.of()), exception);
            return false;
        }
    }

    public @Nullable MasteryLayer read(@Nullable ItemStack itemStack) {
        try {
            return codec.read(itemStack);
        } catch (RuntimeException | LinkageError exception) {
            warn(LogTags.text(plugin, "console.mastery_read_failed", Map.of()), exception);
            return null;
        }
    }

    public boolean overwriteTotalExp(@Nullable ItemStack itemStack, double totalExp) {
        if (itemStack == null || itemStack.getType().isAir() || !Double.isFinite(totalExp) || totalExp < 0D) {
            return false;
        }
        try {
            MasteryLayer existing = codec.read(itemStack);
            String instanceId = existing == null || Texts.isBlank(existing.instanceId())
                    ? UUID.randomUUID().toString()
                    : existing.instanceId();
            MasteryLayer replaced = MasteryLayer.empty(instanceId, resolveSoftCap()).withGainedExp(totalExp);
            return codec.write(itemStack, replaced);
        } catch (RuntimeException | LinkageError exception) {
            warn(LogTags.text(plugin, "console.mastery_overwrite_failed", Map.of()), exception);
            return false;
        }
    }

    public boolean clear(@Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return false;
        }
        try {
            codec.clear(itemStack);
            return true;
        } catch (RuntimeException | LinkageError exception) {
            warn(LogTags.text(plugin, "console.mastery_clear_failed", Map.of()), exception);
            return false;
        }
    }

    private double resolveGain(boolean success) {
        if (plugin.appConfig() == null) {
            return 0D;
        }
        double perAttempt = plugin.appConfig().enhancementMasteryExpPerAttempt();
        double perSuccess = success ? plugin.appConfig().enhancementMasteryExpPerSuccess() : 0D;
        double total = perAttempt + perSuccess;
        return Double.isFinite(total) ? Math.max(0D, total) : 0D;
    }

    private int resolveSoftCap() {
        return plugin.appConfig() == null ? 0 : plugin.appConfig().enhancementMasterySoftCap();
    }

    private void warn(String message, Throwable throwable) {
        if (plugin.getLogger() != null) {
            LogTags.sendRaw(plugin, StrengthenLogTag.MASTERY, Level.WARNING, message + ": " + String.valueOf(throwable.getMessage()));
        }
    }
}
