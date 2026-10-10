package dev.questjs.ftbquestskubejs;

import dev.questjs.ftbquestskubejs.attribute.AttributeTask;
import net.neoforged.fml.common.Mod;

@Mod("ftbquestskubejs")
public final class FTBQuestsKubeJSMod {
    public FTBQuestsKubeJSMod() {
        // Force attribute task-type registration before quest files are loaded.
        AttributeTask.TYPE.getTypeId();
    }
}
