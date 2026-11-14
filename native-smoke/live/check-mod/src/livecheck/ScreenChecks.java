package livecheck;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import org.starficz.starui.StarUIManager;
import org.starficz.starui.livecheck.LiveScreens;

import java.util.ArrayList;
import java.util.List;

// StarUIPlugin screen panels in a real campaign: the campaign core UI's (injected when vanilla makes it), each core
// tab's (the API's CoreUITabListener, when vanilla opens it), a tab opened again (a new panel, a new injection), and
// an interaction dialog's own core UI. LiveScreens (native-smoke/live/kotlin) is registered at onApplicationLoad by
// CheckPlugin when this group runs, and labels every screen "starui-screen <name>".
public final class ScreenChecks {
    private ScreenChecks() {
    }

    private static final CoreUITabId[] TABS = {
        CoreUITabId.CHARACTER, CoreUITabId.FLEET, CoreUITabId.REFIT, CoreUITabId.CARGO,
        CoreUITabId.MAP, CoreUITabId.INTEL, CoreUITabId.OUTPOSTS
    };

    private static UIPanelAPI campaignCore;

    private static PlanetAPI somePlanet() {
        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (system.isProcgen()) continue;
            for (PlanetAPI p : system.getPlanets()) {
                if (!p.isStar() && !p.isGasGiant() && p.getMarket() != null && !p.getMarket().isPlanetConditionMarketOnly()) return p;
            }
        }
        return null;
    }

    /** The open tab carries exactly one screen panel, on top, labelled for [tab], built [expectedBuilds] times. */
    private static void checkTab(Ctx c, CoreUITabId tab, int expectedBuilds, String when) {
        c.eq(StarUIManager.INSTANCE.getCurrentCoreUITabID(), tab, when + ": the open tab is " + tab);
        UIPanelAPI panel = StarUIManager.INSTANCE.getCurrentCoreUITabPanel();
        c.expect(panel != null, when + ": " + tab + " has a tab panel");
        c.eq(LiveScreens.screenNames(panel, false), "tab-" + tab, when + ": " + tab + "'s screen panel holds its own builder's UI");
        c.eq(LiveScreens.screenPanelCount(panel), 1, when + ": " + tab + " holds one screen panel");
        c.expect(LiveScreens.screenPanelOnTop(panel), when + ": " + tab + "'s screen panel is its last child (on top)");
        c.eq(LiveScreens.builds("tab-" + tab), expectedBuilds, when + ": " + tab + "'s builder ran " + expectedBuilds + " time(s)");
    }

    /** A real Esc closes the open core tab (the core UI's own key handling). */
    private static void closeTab(Ctx c) {
        c.key("ESC");
        c.frames(20);
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();

        steps.add(c -> {
            c.eq(Global.getCurrentState(), GameState.CAMPAIGN, "the game is in the campaign");
            campaignCore = StarUIManager.INSTANCE.getCoreUI();
            c.expect(campaignCore != null, "StarUIManager.coreUI is the campaign core UI");
            c.eq(LiveScreens.screenNames(campaignCore, false), "campaign", "the campaign core UI got addPanelToCampaignUI when it was made");
            c.eq(LiveScreens.screenPanelCount(campaignCore), 1, "the campaign core UI holds one screen panel");
            c.note("campaign root built " + LiveScreens.builds("campaign") + " time(s) since load");
            c.shot("screens-campaign");
        });

        // each tab from a closed core UI (switching by script while a tab is open can be put off by vanilla until a
        // dialog closes; then it only highlights the button and announces nothing)
        boolean first = true;
        for (final CoreUITabId tab : TABS) {
            if (!first) steps.add(ScreenChecks::closeTab);
            first = false;
            steps.add(c -> {
                Global.getSector().getCampaignUI().showCoreUITab(tab);
                c.frames(20);
            });
            steps.add(c -> {
                checkTab(c, tab, 1, "first open");
                c.shot("screens-tab-" + tab.name().toLowerCase());
            });
        }

        // the cargo tab again: vanilla makes a new panel, which gets its own (one) screen panel
        steps.add(ScreenChecks::closeTab);
        steps.add(c -> {
            Global.getSector().getCampaignUI().showCoreUITab(CoreUITabId.CARGO);
            c.frames(20);
        });
        steps.add(c -> checkTab(c, CoreUITabId.CARGO, 2, "opened again"));

        steps.add(c -> {
            c.key("ESC");
            c.frames(20);
        });

        // an interaction dialog's own core UI (the trade screen of a market)
        steps.add(c -> {
            c.keepDialog(true);
            boolean shown = Global.getSector().getCampaignUI().showInteractionDialog(new Ui.Bare(), Global.getSector().getPlayerFleet());
            c.expect(shown, "an interaction dialog opened");
            c.frames(30);
        });
        steps.add(c -> {
            InteractionDialogAPI dialog = Global.getSector().getCampaignUI().getCurrentInteractionDialog();
            PlanetAPI planet = somePlanet();
            c.expect(dialog != null && planet != null, "a dialog and a market to trade with");
            if (dialog == null || planet == null) return;
            dialog.getVisualPanel().showCore(CoreUITabId.CARGO, planet, () -> { });
            c.frames(30);
        });
        steps.add(c -> {
            UIPanelAPI core = StarUIManager.INSTANCE.getCoreUI();
            c.expect(core != null && core != campaignCore, "StarUIManager.coreUI is now the dialog's own core UI");
            c.eq(LiveScreens.screenNames(core, false), "campaign", "the dialog's core UI got addPanelToCampaignUI too");
            checkTab(c, CoreUITabId.CARGO, 3, "in a dialog");
            c.shot("screens-dialog-cargo");
        });

        return steps;
    }
}
