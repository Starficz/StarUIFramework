package hotreloadhelper;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import java.util.List;

/** The helper's combat plugin (registered in its settings.json): hot.sh console commands in a battle. */
public class HotHelperCombat extends BaseEveryFrameCombatPlugin {
    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (!HotHelperConsole.consoleEnabled()) return;
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null) return;
        String context = engine.isSimulation() ? "COMBAT_SIMULATION"
                : engine.isInCampaign() ? "COMBAT_CAMPAIGN" : "COMBAT_MISSION";
        HotHelperConsole.poll(context);
    }
}
