package emaki.jiuwu.craft.structure.template;

import emaki.jiuwu.craft.structure.template.nbt.NbtValue;

public record TemplateEntity(
        String type,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        NbtValue.CompoundTag nbt) {
}
