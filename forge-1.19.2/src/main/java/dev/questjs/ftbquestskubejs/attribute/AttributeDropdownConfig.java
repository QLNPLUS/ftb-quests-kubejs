package dev.questjs.ftbquestskubejs.attribute;

import dev.ftb.mods.ftblibrary.config.ConfigCallback;
import dev.ftb.mods.ftblibrary.config.EnumConfig;
import dev.ftb.mods.ftblibrary.config.NameMap;
import dev.ftb.mods.ftblibrary.icon.ItemIcon;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.SimpleTextButton;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftblibrary.ui.misc.ButtonListBaseScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@OnlyIn(Dist.CLIENT)
final class AttributeDropdownConfig extends EnumConfig<ResourceLocation> {
    AttributeDropdownConfig(NameMap<ResourceLocation> nameMap) {
        super(nameMap);
    }

    static NameMap<ResourceLocation> createNameMap() {
        List<ResourceLocation> ids = ForgeRegistries.ATTRIBUTES.getValues().stream()
                .map(ForgeRegistries.ATTRIBUTES::getKey)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .toList();
        ResourceLocation defaultId = ForgeRegistries.ATTRIBUTES.getKey(Attributes.MAX_HEALTH);
        if (defaultId == null && !ids.isEmpty()) {
            defaultId = ids.get(0);
        }
        if (defaultId == null) {
            defaultId = new ResourceLocation("minecraft", "generic.max_health");
            ids = List.of(defaultId);
        }

        return NameMap.of(defaultId, ids)
                .id(ResourceLocation::toString)
                .name(AttributeDropdownConfig::displayName)
                .icon(id -> ItemIcon.getItemIcon(Items.IRON_CHESTPLATE))
                .create();
    }

    private static Component displayName(ResourceLocation id) {
        Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(id);
        Component displayName = attribute == null
                ? Component.literal(id.toString())
                : Component.translatable(attribute.getDescriptionId());
        return displayName.copy().append(Component.literal(" [" + id + "]"));
    }

    /**
     * FTB Library 1902 passes only the button and the callback; the widget itself
     * is not part of the signature, which is the only difference from 2001.
     */
    @Override
    public void onClicked(MouseButton button, ConfigCallback callback) {
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
