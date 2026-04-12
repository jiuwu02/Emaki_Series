package emaki.jiuwu.craft.attribute.service;

import java.util.Locale;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import emaki.jiuwu.craft.attribute.model.AttributeSnapshot;
import emaki.jiuwu.craft.attribute.model.AttributeSnapshotCodecs;
import emaki.jiuwu.craft.attribute.model.ProjectileDamageSnapshot;
import emaki.jiuwu.craft.attribute.model.ResourceState;
import emaki.jiuwu.craft.corelib.pdc.PdcPartition;
import emaki.jiuwu.craft.corelib.pdc.PdcService;

final class AttributeStateRepository {

    private final PdcService pdcService;
    private final PdcPartition itemPartition;
    private final PdcPartition combatPartition;
    private final PdcPartition projectilePartition;

    AttributeStateRepository(PdcService pdcService) {
        this.pdcService = pdcService;
        this.itemPartition = pdcService.partition("item");
        this.combatPartition = pdcService.partition("combat");
        this.projectilePartition = pdcService.partition("projectile");
    }

    String readItemSourceSignature(ItemStack itemStack) {
        return pdcService.get(itemStack, itemPartition, "source_signature", PersistentDataType.STRING);
    }

    AttributeSnapshot readItemSnapshot(ItemStack itemStack) {
        return pdcService.readBlob(itemStack, itemPartition, "snapshot", AttributeSnapshotCodecs.ATTRIBUTE_SNAPSHOT);
    }

    void writeItemSnapshot(ItemStack itemStack, AttributeSnapshot snapshot) {
        if (itemStack == null || snapshot == null) {
            return;
        }
        pdcService.batchMutate(itemStack, container -> {
            container.set(itemPartition.key("schema_version"), PersistentDataType.INTEGER, snapshot.schemaVersion());
            container.set(itemPartition.key("source_signature"), PersistentDataType.STRING, snapshot.sourceSignature());
            container.set(itemPartition.key("snapshot"), PersistentDataType.STRING, AttributeSnapshotCodecs.ATTRIBUTE_SNAPSHOT.encode(snapshot));
        });
    }

    void clearItemSnapshot(ItemStack itemStack) {
        pdcService.batchMutate(itemStack, container -> {
            container.remove(itemPartition.key("schema_version"));
            container.remove(itemPartition.key("source_signature"));
            container.remove(itemPartition.key("snapshot"));
        });
    }

    String readCombatSourceSignature(LivingEntity entity) {
        return pdcService.get(entity, combatPartition, "source_signature", PersistentDataType.STRING);
    }

    AttributeSnapshot readCombatSnapshot(LivingEntity entity) {
        return pdcService.readBlob(entity, combatPartition, "snapshot", AttributeSnapshotCodecs.ATTRIBUTE_SNAPSHOT);
    }

    void writeCombatSnapshot(LivingEntity entity, AttributeSnapshot snapshot) {
        if (entity == null || snapshot == null) {
            return;
        }
        pdcService.set(entity, combatPartition, "schema_version", PersistentDataType.INTEGER, snapshot.schemaVersion());
        pdcService.set(entity, combatPartition, "source_signature", PersistentDataType.STRING, snapshot.sourceSignature());
        pdcService.writeBlob(entity, combatPartition, "snapshot", AttributeSnapshotCodecs.ATTRIBUTE_SNAPSHOT, snapshot);
    }

    void setDamageTypeOverride(LivingEntity entity, String damageTypeId) {
        if (entity == null) {
            return;
        }
        if (damageTypeId == null || damageTypeId.isBlank()) {
            pdcService.remove(entity, combatPartition, "damage_type_override");
            return;
        }
        pdcService.set(entity, combatPartition, "damage_type_override", PersistentDataType.STRING, normalizeId(damageTypeId));
    }

    String peekDamageTypeOverride(LivingEntity entity) {
        if (entity == null) {
            return null;
        }
        return pdcService.get(entity, combatPartition, "damage_type_override", PersistentDataType.STRING);
    }

    String consumeDamageTypeOverride(LivingEntity entity) {
        String override = peekDamageTypeOverride(entity);
        if (override != null && !override.isBlank()) {
            pdcService.remove(entity, combatPartition, "damage_type_override");
        }
        return override;
    }

    void markSyntheticDamage(LivingEntity entity, boolean value) {
        if (entity == null) {
            return;
        }
        if (value) {
            pdcService.set(entity, combatPartition, "synthetic_damage", PersistentDataType.BYTE, (byte) 1);
        } else {
            pdcService.remove(entity, combatPartition, "synthetic_damage");
        }
    }

    boolean isSyntheticDamage(LivingEntity entity) {
        return entity != null && pdcService.has(entity, combatPartition, "synthetic_damage", PersistentDataType.BYTE);
    }

    Long readAttackCooldownUntil(Player player) {
        return player == null ? null : pdcService.get(player, combatPartition, "attack_cooldown_until", PersistentDataType.LONG);
    }

    void writeAttackCooldownUntil(Player player, long until) {
        if (player == null) {
            return;
        }
        if (until <= 0L) {
            pdcService.remove(player, combatPartition, "attack_cooldown_until");
            return;
        }
        pdcService.set(player, combatPartition, "attack_cooldown_until", PersistentDataType.LONG, until);
    }

    void clearAttackCooldown(Player player) {
        if (player != null) {
            pdcService.remove(player, combatPartition, "attack_cooldown_until");
        }
    }

    ResourceState readResourceState(Player player, String resourceId) {
        if (player == null || resourceId == null || resourceId.isBlank()) {
            return null;
        }
        PdcPartition resourcePartition = resourcePartition(resourceId);
        Double defaultMax = pdcService.get(player, resourcePartition, "default_max", PersistentDataType.DOUBLE);
        Double bonusMax = pdcService.get(player, resourcePartition, "bonus_max", PersistentDataType.DOUBLE);
        Double currentMax = pdcService.get(player, resourcePartition, "current_max", PersistentDataType.DOUBLE);
        Double currentValue = pdcService.get(player, resourcePartition, "current_value", PersistentDataType.DOUBLE);
        String sourceSignature = pdcService.get(player, resourcePartition, "source_signature", PersistentDataType.STRING);
        Integer schemaVersion = pdcService.get(player, resourcePartition, "schema_version", PersistentDataType.INTEGER);
        if (defaultMax == null && bonusMax == null && currentMax == null && currentValue == null
                && (sourceSignature == null || sourceSignature.isBlank()) && schemaVersion == null) {
            return null;
        }
        return new ResourceState(
                normalizeId(resourceId),
                defaultMax == null ? 0D : defaultMax,
                bonusMax == null ? 0D : bonusMax,
                currentMax == null ? 0D : currentMax,
                currentValue == null ? 0D : currentValue,
                sourceSignature,
                schemaVersion == null ? ResourceState.CURRENT_SCHEMA_VERSION : schemaVersion
        );
    }

    void writeResourceState(Player player, ResourceState state) {
        if (player == null || state == null) {
            return;
        }
        PdcPartition resourcePartition = resourcePartition(state.resourceId());
        pdcService.set(player, resourcePartition, "schema_version", PersistentDataType.INTEGER, state.schemaVersion());
        pdcService.set(player, resourcePartition, "default_max", PersistentDataType.DOUBLE, state.defaultMax());
        pdcService.set(player, resourcePartition, "bonus_max", PersistentDataType.DOUBLE, state.bonusMax());
        pdcService.set(player, resourcePartition, "current_max", PersistentDataType.DOUBLE, state.currentMax());
        pdcService.set(player, resourcePartition, "current_value", PersistentDataType.DOUBLE, state.currentValue());
        pdcService.set(player, resourcePartition, "source_signature", PersistentDataType.STRING, state.sourceSignature());
    }

    ProjectileDamageSnapshot readProjectileSnapshot(Projectile projectile) {
        if (projectile == null) {
            return null;
        }
        ProjectileDamageSnapshot snapshot = pdcService.readBlob(projectile, projectilePartition, "snapshot", AttributeSnapshotCodecs.PROJECTILE_DAMAGE_SNAPSHOT);
        if (snapshot == null) {
            return null;
        }
        if (snapshot.expiresAt() > 0L && System.currentTimeMillis() > snapshot.expiresAt()) {
            clearProjectileSnapshot(projectile);
            return null;
        }
        return snapshot;
    }

    void writeProjectileSnapshot(Projectile projectile, ProjectileDamageSnapshot snapshot) {
        if (projectile == null || snapshot == null) {
            return;
        }
        pdcService.set(projectile, projectilePartition, "schema_version", PersistentDataType.INTEGER, snapshot.schemaVersion());
        pdcService.set(projectile, projectilePartition, "source_signature", PersistentDataType.STRING, snapshot.sourceSignature());
        pdcService.set(projectile, projectilePartition, "damage_type_id", PersistentDataType.STRING, snapshot.damageTypeId());
        pdcService.set(projectile, projectilePartition, "shooter_uuid", PersistentDataType.STRING, snapshot.shooterUuid() == null ? "" : snapshot.shooterUuid().toString());
        pdcService.set(projectile, projectilePartition, "launched_at", PersistentDataType.LONG, snapshot.launchedAt());
        pdcService.set(projectile, projectilePartition, "expires_at", PersistentDataType.LONG, snapshot.expiresAt());
        pdcService.writeBlob(projectile, projectilePartition, "attack_snapshot", AttributeSnapshotCodecs.ATTRIBUTE_SNAPSHOT, snapshot.attackSnapshot());
        pdcService.writeBlob(projectile, projectilePartition, "snapshot", AttributeSnapshotCodecs.PROJECTILE_DAMAGE_SNAPSHOT, snapshot);
    }

    void clearProjectileSnapshot(Projectile projectile) {
        if (projectile == null) {
            return;
        }
        pdcService.remove(projectile, projectilePartition, "schema_version");
        pdcService.remove(projectile, projectilePartition, "source_signature");
        pdcService.remove(projectile, projectilePartition, "damage_type_id");
        pdcService.remove(projectile, projectilePartition, "shooter_uuid");
        pdcService.remove(projectile, projectilePartition, "launched_at");
        pdcService.remove(projectile, projectilePartition, "expires_at");
        pdcService.remove(projectile, projectilePartition, "attack_snapshot");
        pdcService.remove(projectile, projectilePartition, "snapshot");
    }

    private PdcPartition resourcePartition(String resourceId) {
        return combatPartition.child("resource").child(resourceId);
    }

    private String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }
}
