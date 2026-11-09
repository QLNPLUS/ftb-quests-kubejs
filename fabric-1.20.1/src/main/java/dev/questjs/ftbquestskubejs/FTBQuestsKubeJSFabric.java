package dev.questjs.ftbquestskubejs;

import net.fabricmc.api.ClientModInitializer;

public final class FTBQuestsKubeJSFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        QuestJSConfig.load();
    }
}
