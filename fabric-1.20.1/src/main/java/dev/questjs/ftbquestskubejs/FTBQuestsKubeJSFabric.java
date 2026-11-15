package dev.questjs.ftbquestskubejs;

import dev.questjs.ftbquestskubejs.attribute.AttributeTask;
import dev.questjs.ftbquestskubejs.composite.ExternalTaskTask;
import dev.questjs.ftbquestskubejs.data.CustomDataNumberTask;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;

public final class FTBQuestsKubeJSFabric implements ModInitializer, ClientModInitializer {
    @Override
    public void onInitialize() {
        registerTaskTypes();
    }

    @Override
    public void onInitializeClient() {
        QuestJSConfig.load();
        registerTaskTypes();
    }

    /**
     * Forces the task types to be registered before quest files are loaded.
     * Referencing the static TYPE fields triggers class init, which is where
     * {@code TaskTypes.register} runs.
     */
    private static void registerTaskTypes() {
        AttributeTask.TYPE.getTypeId();
        CustomDataNumberTask.TYPE.getTypeId();
        ExternalTaskTask.TYPE.getTypeId();
    }
}
