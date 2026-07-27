package emaki.jiuwu.craft.corelib.api.item;

import java.util.Objects;

/** Runtime/catalog capability information for one vanilla item component. */
public final class ItemComponentCapability {

    private final String componentId;
    private final String minimumMinecraftVersion;
    private final boolean supported;
    private final boolean genericBridgeSupported;
    private final String valueFormat;

    public ItemComponentCapability(String componentId,
            String minimumMinecraftVersion,
            boolean supported,
            boolean genericBridgeSupported,
            String valueFormat) {
        this.componentId = PlainItemData.componentId(componentId);
        this.minimumMinecraftVersion = minimumMinecraftVersion == null ? "" : minimumMinecraftVersion.trim();
        this.supported = supported;
        this.genericBridgeSupported = genericBridgeSupported;
        this.valueFormat = valueFormat == null ? "" : valueFormat;
    }

    public String componentId() {
        return componentId;
    }

    public String minimumMinecraftVersion() {
        return minimumMinecraftVersion;
    }

    public boolean supported() {
        return supported;
    }

    public boolean genericBridgeSupported() {
        return genericBridgeSupported;
    }

    public String valueFormat() {
        return valueFormat;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ItemComponentCapability capability)) {
            return false;
        }
        return supported == capability.supported
                && genericBridgeSupported == capability.genericBridgeSupported
                && componentId.equals(capability.componentId)
                && minimumMinecraftVersion.equals(capability.minimumMinecraftVersion)
                && valueFormat.equals(capability.valueFormat);
    }

    @Override
    public int hashCode() {
        return Objects.hash(componentId, minimumMinecraftVersion, supported, genericBridgeSupported, valueFormat);
    }

    @Override
    public String toString() {
        return "ItemComponentCapability[componentId=" + componentId
                + ", minimumMinecraftVersion=" + minimumMinecraftVersion
                + ", supported=" + supported
                + ", genericBridgeSupported=" + genericBridgeSupported
                + ", valueFormat=" + valueFormat + "]";
    }
}
