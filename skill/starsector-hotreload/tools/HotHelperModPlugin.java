package hotreloadhelper;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;

/**
 * The test-only helper mod's plugin (hot-reload games only, never shipped): in the campaign, a transient script hands
 * hot.sh console commands to Console Commands each frame (HotHelperConsole). Does nothing without Console Commands.
 */
public class HotHelperModPlugin extends BaseModPlugin {
    @Override
    public void onGameLoad(boolean newGame) {
        if (!HotHelperConsole.consoleEnabled()) return;
        Global.getSector().addTransientScript(new EveryFrameScript() {
            public boolean isDone() { return false; }
            public boolean runWhilePaused() { return true; }
            public void advance(float amount) { HotHelperConsole.poll("CAMPAIGN_MAP"); }
        });
    }
}
