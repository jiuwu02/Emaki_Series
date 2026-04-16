package emaki.jiuwu.craft.attribute.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.model.AttributeDefinition;
import emaki.jiuwu.craft.attribute.model.AttributeSnapshot;
import emaki.jiuwu.craft.attribute.model.AttributeValueKind;
import emaki.jiuwu.craft.corelib.text.Texts;
final class VanillaAttributeSynchronizer {

    private static final double DEFAULT_WALK_SPEED_BLOCKS_PER_SECOND = 4.317D;
    private static final int DEFAULT_ATTACK_COOLDOWN_TICKS = 20;

    record VanillaAttributeBinding(AttributeDefinition definition,
            Attribute attribute,
            AttributeModifier.Operation operation,
            NamespacedKey modifierKey,
            String targetAttributeId) {

    }

    private final EmakiAttributePlugin plugin;

    VanillaAttributeSynchronizer(EmakiAttributePlugin plugin) {
        this.plugin = plugin;
    }

    VanillaAttributeBinding resolveBinding(AttributeDefinition definition) {
        if (definition == null || !definition.isVanillaMappedValue()) {
            return null;
        }
        Attribute attribute = resolveVanillaAttribute(definition.targetId());
        if (attribute == null) {
            if (plugin.messageService() != null) {
                plugin.messageService().warning("console.vanilla_attribute_resolve_failed", Map.of(
                        "attribute", definition.id(),
                        "target", definition.targetId()
                ));
            }
            return null;
        }
        AttributeModifier.Operation operation = switch (definition.valueKind()) {
            case PERCENT, CHANCE ->
                AttributeModifier.Operation.ADD_SCALAR;
            default ->
                AttributeModifier.Operation.ADD_NUMBER;
        };
        NamespacedKey modifierKey = new NamespacedKey(plugin, "vanilla/" + definition.id());
        return new VanillaAttributeBinding(definition, attribute, operation, modifierKey, attribute.getKey().toString());
    }

    void syncMovementSpeed(Player player, AttributeSnapshot snapshot, List<AttributeDefinition> speedDefinitions) {
        if (player == null) {
            return;
        }
        double flatSpeed = 0D;
        double percentSpeed = 0D;
        for (AttributeDefinition definition : speedDefinitions) {
            Double value = snapshot == null ? null : snapshot.values().get(definition.id());
            if (value == null) {
                continue;
            }
            if (definition.valueKind() == AttributeValueKind.PERCENT) {
                percentSpeed += value;
            } else if (definition.valueKind() != AttributeValueKind.CHANCE
                    && definition.valueKind() != AttributeValueKind.REGEN
                    && definition.valueKind() != AttributeValueKind.RESOURCE
                    && definition.valueKind() != AttributeValueKind.DERIVED) {
                flatSpeed += value;
            }
        }
        double factor = AttributeFusionMath.percentFactor(percentSpeed, true);
        double blocksPerSecond = AttributeFusionMath.usesFusedCombatValues(snapshot)
                ? Math.max(0D, (DEFAULT_WALK_SPEED_BLOCKS_PER_SECOND * factor) + flatSpeed)
                : Math.max(0D, DEFAULT_WALK_SPEED_BLOCKS_PER_SECOND + flatSpeed) * factor;
        float walkSpeed = (float) Math.max(0D, Math.min(1D, (blocksPerSecond / DEFAULT_WALK_SPEED_BLOCKS_PER_SECOND) * 0.2D));
        player.setWalkSpeed(walkSpeed);
    }

    void syncVanillaMappedAttributes(LivingEntity entity,
            AttributeSnapshot snapshot,
            List<VanillaAttributeBinding> bindings,
            Set<Attribute> managedAttributes) {
        if (entity == null || !entity.isValid() || entity.isDead()) {
            return;
        }
        clearManagedVanillaAttributeModifiers(entity, managedAttributes);
        if (snapshot == null || bindings.isEmpty()) {
            return;
        }
        for (VanillaAttributeBinding binding : bindings) {
            applyVanillaMappedAttribute(entity, snapshot, binding);
        }
    }

    int resolveAttackCooldownTicks(AttributeSnapshot snapshot, List<AttributeDefinition> attackSpeedDefinitions) {
        double flatAttackRate = 0D;
        double percentModifier = 0D;
        for (AttributeDefinition definition : attackSpeedDefinitions) {
            Double value = snapshot == null ? null : snapshot.values().get(definition.id());
            if (value == null) {
                continue;
            }
            if (definition.valueKind() == AttributeValueKind.PERCENT) {
                percentModifier += value;
            } else if (definition.valueKind() != AttributeValueKind.CHANCE
                    && definition.valueKind() != AttributeValueKind.REGEN
                    && definition.valueKind() != AttributeValueKind.RESOURCE
                    && definition.valueKind() != AttributeValueKind.DERIVED) {
                flatAttackRate += value;
            }
        }
        double effectiveAttackRate = AttributeFusionMath.usesFusedCombatValues(snapshot)
                ? Math.max(0D, flatAttackRate)
                : Math.max(0D, flatAttackRate) * AttributeFusionMath.percentFactor(percentModifier, true);
        if (effectiveAttackRate <= 0D) {
            return DEFAULT_ATTACK_COOLDOWN_TICKS;
        }
        double cooldownTicks = 20D / effectiveAttackRate;
        return Math.max(1, (int) Math.round(cooldownTicks));
    }

    Set<Attribute> collectManagedAttributes(List<VanillaAttributeBinding> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<Attribute> attributes = new LinkedHashSet<>();
        for (VanillaAttributeBinding binding : bindings) {
            if (binding != null && binding.attribute() != null) {
                attributes.add(binding.attribute());
            }
        }
        return attributes.isEmpty() ? Set.of() : Set.copyOf(attributes);
    }

    private void clearManagedVanillaAttributeModifiers(LivingEntity entity, Set<Attribute> managedAttributes) {
        if (entity == null || managedAttributes == null || managedAttributes.isEmpty()) {
            return;
        }
        for (Attribute attribute : managedAttributes) {
            if (attribute == null) {
                continue;
            }
            AttributeInstance instance = entity.getAttribute(attribute);
            if (instance == null) {
                continue;
            }
            for (AttributeModifier modifier : new ArrayList<>(instance.getModifiers())) {
                if (isManagedVanillaModifier(modifier)) {
                    instance.removeModifier(modifier);
                }
            }
        }
    }

    private void applyVanillaMappedAttribute(LivingEntity entity, AttributeSnapshot snapshot, VanillaAttributeBinding binding) {
        if (entity == null || snapshot == null || binding == null || binding.definition() == null || binding.attribute() == null) {
            return;
        }
        Double rawValue = snapshot.values().get(binding.definition().id());
        if (rawValue == null) {
            return;
        }
        double amount = resolveVanillaModifierAmount(binding.definition(), rawValue);
        if (Double.compare(amount, 0D) == 0) {
            return;
        }
        AttributeInstance instance = entity.getAttribute(binding.attribute());
        if (instance == null) {
            return;
        }
        instance.addModifier(new AttributeModifier(binding.modifierKey(), amount, binding.operation(), EquipmentSlotGroup.ANY));
    }

    private boolean isManagedVanillaModifier(AttributeModifier modifier) {
        NamespacedKey key = modifier == null ? null : modifier.getKey();
        return key != null && key.toString().contains(":vanilla/");
    }

    private double resolveVanillaModifierAmount(AttributeDefinition definition, double value) {
        if (definition == null) {
            return value;
        }
        return switch (definition.valueKind()) {
            case PERCENT, CHANCE ->
                value / 100D;
            default ->
                value;
        };
    }

    private Attribute resolveVanillaAttribute(String targetId) {
        String normalized = normalizeId(targetId);
        if (Texts.isBlank(normalized)) {
            return null;
        }
        for (String candidate : vanillaAttributeCandidates(normalized)) {
            NamespacedKey key = NamespacedKey.fromString(candidate);
            if (key == null) {
                continue;
            }
            Attribute attribute = Registry.ATTRIBUTE.get(key);
            if (attribute != null) {
                return attribute;
            }
        }
        return null;
    }

    private List<String> vanillaAttributeCandidates(String normalized) {
        if (Texts.isBlank(normalized)) {
            return List.of();
        }
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        candidates.add(normalized);
        int namespaceSeparator = normalized.indexOf(':');
        String key = namespaceSeparator >= 0 && namespaceSeparator < normalized.length() - 1
                ? normalized.substring(namespaceSeparator + 1)
                : normalized;
        candidates.add(key);
        String normalizedKey = key.replace('.', '_');
        candidates.add(normalizedKey);
        addLegacyVanillaCandidates(candidates, key, "generic_");
        addLegacyVanillaCandidates(candidates, key, "player_");
        addLegacyVanillaCandidates(candidates, normalizedKey, "generic_");
        addLegacyVanillaCandidates(candidates, normalizedKey, "player_");
        return List.copyOf(candidates);
    }

    private void addLegacyVanillaCandidates(Set<String> candidates, String key, String legacyPrefix) {
        if (candidates == null || Texts.isBlank(key) || Texts.isBlank(legacyPrefix)) {
            return;
        }
        if (key.startsWith(legacyPrefix) && key.length() > legacyPrefix.length()) {
            candidates.add(key.substring(legacyPrefix.length()));
        }
    }

    private String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }
}
