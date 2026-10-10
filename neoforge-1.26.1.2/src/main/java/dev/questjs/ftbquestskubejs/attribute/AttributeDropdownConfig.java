package dev.questjs.ftbquestskubejs.attribute;

import dev.ftb.mods.ftblibrary.client.config.editable.EditableEnum;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.util.NameMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.Comparator;
import java.util.List;

final class AttributeDropdownConfig extends EditableEnum<Identifier> {
    AttributeDropdownConfig(NameMap<Identifier> nameMap) {
        super(nameMap);
    }

    static NameMap<Identifier> createNameMap() {
        List<Identifier> ids = BuiltInRegistries.ATTRIBUTE.keySet().stream()
                .sorted(Comparator.comparing(Identifier::toString))
                .toList();
        Identifier defaultId = Attributes.MAX_HEALTH.unwrapKey().orElseThrow().identifier();
        if (ids.isEmpty()) {
            ids = List.of(defaultId);
        }

        return NameMap.of(defaultId, ids)
                .id(Identifier::toString)
                .name(AttributeDropdownConfig::displayName)
                .icon(id -> Icon.getIcon("minecraft:paper"))
                .create();
    }

    private static Component displayName(Identifier id) {
        Attribute attribute = BuiltInRegistries.ATTRIBUTE.get(id)
                .map(holder -> holder.value())
                .orElse(null);
        Component name = attribute == null
                ? Component.literal(id.toString())
                : Component.translatable(attribute.getDescriptionId());
        return name.copy().append(Component.literal(" [" + id + "]"));
    }
}
