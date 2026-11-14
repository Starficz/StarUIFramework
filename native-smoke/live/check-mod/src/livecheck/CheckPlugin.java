package livecheck;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

public class CheckPlugin extends BaseModPlugin {
    private static final Logger log = Global.getLogger(CheckPlugin.class);

    @Override
    public void onApplicationLoad() {
        log.info("LIVE_CHECK application loaded");
        // the screens group: a StarUIPlugin that labels every screen, registered before vanilla builds any
        String group = System.getProperty("live.check");
        if (group != null && java.util.Arrays.asList(group.split(",")).contains("screens")) {
            org.starficz.starui.livecheck.LiveScreens.register();
        }
    }

    @Override
    public void onGameLoad(boolean newGame) {
        String group = System.getProperty("live.check");
        if (group == null) return;
        log.info("LIVE_CHECK game loaded, group " + group);
        Global.getSector().addTransientScript(new Runner(group));
    }
}
