package dev.questjs.ftbquestskubejs.attribute;

import dev.ftb.mods.ftblibrary.config.ConfigCallback;
import dev.ftb.mods.ftblibrary.config.EnumConfig;
import dev.ftb.mods.ftblibrary.config.NameMap;
import dev.ftb.mods.ftblibrary.icon.ItemIcon;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.SimpleTextButton;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftblibrary.ui.misc.ButtonListBaseScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Comparator;
import java.util.List;

@Environment(EnvType.CLIENT)
final class AttributeDropdownConfig extends EnumConfig<Identifier> {
    AttributeDropdownConfig(NameMap<Identifier> nameMap) {
        super(nameMap);
    }

    static NameMap<Identifier> createNameMap() {
        List<Identifier> ids = Registries.ATTRIBUTE.getIds().stream()
                .sorted(Comparator.comparing(Identifier::toString))
                .toList();
        Identifier defaultId = Registries.ATTRIBUTE.getId(EntityAttributes.GENERIC_MAX_HEALTH);
        if (defaultId == null && !ids.isEmpty()) {
            defaultId = ids.get(0);
        }
        if (defaultId == null) {
            defaultId = new Identifier("minecraft", "generic.max_health");
            ids = List.of(defaultId);
        }

        return NameMap.of(defaultId, ids)
                .id(Identifier::toString)
                .name(AttributeDropdownConfig::displayName)
                .icon(id -> ItemIcon.getItemIcon(Items.IRON_CHESTPLATE))
                .create();
    }

    private static Text displayName(Identifier id) {
        EntityAttribute attribute = Registries.ATTRIBUTE.get(id);
        Text displayName = attribute == null
                ? Text.literal(id.toString())
                : Text.translatable(attribute.getTranslationKey());
        return displayName.copy().append(Text.literal(" [" + id + "]"));
    }

    @Override
    public void onClicked(Widget clickedWidget, MouseButton button, ConfigCallback callback) {
        ButtonListBaseScreen screen = new ButtonListBaseScreen() {
            @Override
            public void addButtons(Panel panel) {
                for (Identifier id : nameMap) {
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
