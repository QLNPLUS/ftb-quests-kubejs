package dev.questjs.ftbquestskubejs;

import dev.questjs.ftbquestskubejs.attribute.AttributeTask;
import dev.questjs.ftbquestskubejs.composite.CompositeTask;
import dev.questjs.ftbquestskubejs.data.CustomDataNumberTask;
import net.minecraftforge.fml.common.Mod;

@Mod("ftbquestskubejs")
public final class FTBQuestsKubeJSMod {
    public FTBQuestsKubeJSMod() {
        // Force task-type registration before quest files are loaded.
        AttributeTask.TYPE.getTypeId();
        CustomDataNumberTask.TYPE.getTypeId();
        CompositeTask.TYPE.getTypeId();
    }
}
