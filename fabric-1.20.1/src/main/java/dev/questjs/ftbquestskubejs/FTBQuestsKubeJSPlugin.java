package dev.questjs.ftbquestskubejs;

import dev.latvian.mods.kubejs.KubeJSPlugin;

public final class FTBQuestsKubeJSPlugin extends KubeJSPlugin {
    @Override
    public void registerEvents() {
        FTBQuestsEvents.GROUP.register();
    }
}
