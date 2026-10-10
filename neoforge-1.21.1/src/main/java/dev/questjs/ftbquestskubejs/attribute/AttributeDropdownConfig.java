package dev.questjs.ftbquestskubejs.attribute;

import dev.ftb.mods.ftblibrary.config.ConfigCallback;
import dev.ftb.mods.ftblibrary.config.EnumConfig;
import dev.ftb.mods.ftblibrary.config.NameMap;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.SimpleTextButton;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftblibrary.ui.misc.ButtonListBaseScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Comparator;
import java.util.List;

@OnlyIn(Dist.CLIENT)
final class AttributeDropdownConfig extends EnumConfig<ResourceLocation> {
    AttributeDropdownConfig(NameMap<ResourceLocation> nameMap) {
        super(nameMap);
    }

    static NameMap<ResourceLocation> createNameMap() {
        List<ResourceLocation> ids = BuiltInRegistries.ATTRIBUTE.keySet().stream()
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .toList();
        ResourceLocation defaultId = Attributes.MAX_HEALTH.unwrapKey().orElseThrow().location();
        if (defaultId == null && !ids.isEmpty()) {
            defaultId = ids.get(0);
        }
        if (defaultId == null) {
            defaultId = ResourceLocation.withDefaultNamespace("max_health");
            ids = List.of(defaultId);
        }

        return NameMap.of(defaultId, ids)
                .id(ResourceLocation::toString)
                .name(AttributeDropdownConfig::displayName)
                .icon(id -> Icon.getIcon("minecraft:paper"))
                .create();
    }

    private static Component displayName(ResourceLocation id) {
        Attribute attribute = BuiltInRegistries.ATTRIBUTE.get(id);
        Component displayName = attribute == null
                ? Component.literal(id.toString())
                : Component.translatable(attribute.getDescriptionId());
        return displayName.copy().append(Component.literal(" [" + id + "]"));
    }

    @Override
    public void onClicked(Widget clickedWidget, MouseButton button, ConfigCallback callback) {
        ButtonListBaseScreen screen = new ButtonListBaseScreen() {
            @Override
            public void addButtons(Panel panel) {
                for (ResourceLocation id : nameMap) {
                    panel.add(new SimpleTextButton(panel, nameMap.getDisplayName(id), nameMap.getIcon(id)) {
                        @Override
                        public void onClicked(MouseButton mouseButton) {
                            playClickSound();
                            callback.save(setCurrentValue(id));
                        }
                    });
                }
            }
        };
        screen.setHasSearchBox(true);
        screen.openGui();
    }
}
