package livecheck;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import org.starficz.starui.StarUIManager;
import org.starficz.starui.livecheck.LiveLayers;
import org.starficz.starui.window.StarUILayer;

import java.util.ArrayList;
import java.util.List;

// Layers (showLayer, Overlays.kt) in a real campaign, with real keys: a modal layer pauses and takes Space, C and
// Escape from the campaign; Escape closes only the top layer; the pause from before comes back; a layer opens above
// an interaction dialog and Escape leaves the dialog; a core UI made while a layer is off the screen panel (what the
// campaign's core rebuild does) puts it back on top.
public final class LayerChecks {
    private LayerChecks() {
    }

    private static StarUILayer one;
    private static StarUILayer two;
    private static StarUILayer overDialog;
    private static StarUILayer readded;
    private static InteractionDialogAPI dialog;

    private static CampaignUIAPI ui() {
        return Global.getSector().getCampaignUI();
    }

    private static PlanetAPI somePlanet() {
        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (system.isProcgen()) continue;
            for (PlanetAPI p : system.getPlanets()) {
                if (!p.isStar() && !p.isGasGiant() && p.getMarket() != null && !p.getMarket().isPlanetConditionMarketOnly()) return p;
            }
        }
        return null;
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();

        // a modal layer over the campaign map
        steps.add(c -> {
            c.eq(Global.getCurrentState(), GameState.CAMPAIGN, "the game is in the campaign");
            Global.getSector().setPaused(false);
            one = LiveLayers.open("one", true);
            c.expect(Global.getSector().isPaused(), "a modal layer pauses the campaign");
            c.expect(one.isOpen(), "the layer is open");
            c.expect(LiveLayers.index(one) > LiveLayers.index(StarUIManager.INSTANCE.getCoreUI()), "it is above the campaign's core UI");
            c.frames(20);
        });
        steps.add(c -> {
            c.shot("layers-modal");
            c.key("SPACE");
            c.frames(10);
        });
        steps.add(c -> {
            c.expect(Global.getSector().isPaused(), "Space under a modal layer does not unpause");
            c.key("C");
            c.frames(20);
        });
        steps.add(c -> {
            c.expect(ui().getCurrentCoreTab() == null, "C under a modal layer opens no tab (" + ui().getCurrentCoreTab() + ")");
            two = LiveLayers.open("two", true);
            c.frames(10);
        });
        steps.add(c -> {
            c.expect(LiveLayers.index(two) > LiveLayers.index(one), "a second layer opens above the first");
            c.key("ESC");
            c.frames(20);
        });
        steps.add(c -> {
            c.expect(!two.isOpen() && one.isOpen(), "Escape closes only the top layer");
            c.key("ESC");
            c.frames(20);
        });
        steps.add(c -> {
            c.expect(!one.isOpen(), "a second Escape closes the one below");
            c.expect(!ui().isShowingMenu(), "the Escapes opened no game menu");
            c.expect(!Global.getSector().isPaused(), "the game is unpaused again, as before the first layer");
            c.expect(LiveLayers.index(one) < 0 && LiveLayers.index(two) < 0, "both panels are off the screen panel");
            Global.getSector().setPaused(true);
            one = LiveLayers.open("paused", true);
            c.frames(5);
        });
        steps.add(c -> {
            one.close();
            c.frames(20);
        });
        steps.add(c -> c.expect(Global.getSector().isPaused(), "a layer opened while paused leaves the game paused"));

        // over an interaction dialog
        steps.add(c -> {
            c.keepDialog(true);
            boolean shown = ui().showInteractionDialog(new Ui.Bare(), Global.getSector().getPlayerFleet());
            c.expect(shown, "an interaction dialog opened");
            c.frames(30);
        });
        steps.add(c -> {
            dialog = ui().getCurrentInteractionDialog();
            c.expect(dialog != null, "the dialog is open");
            overDialog = LiveLayers.open("over-dialog", true);
            c.frames(20);
        });
        steps.add(c -> {
            c.expect(LiveLayers.index(overDialog) > LiveLayers.index(dialog),
                "the layer is above the dialog (" + LiveLayers.index(overDialog) + " > " + LiveLayers.index(dialog) + ")");
            c.shot("layers-over-dialog");
            c.key("ESC");
            c.frames(20);
        });
        steps.add(c -> {
            c.expect(!overDialog.isOpen(), "Escape closes the layer");
            c.expect(ui().getCurrentInteractionDialog() == dialog, "and leaves the dialog under it open");
        });

        // the campaign's core rebuild takes every child off the screen panel and makes a new core UI. Here: a layer
        // taken off by hand, then the dialog's own core UI made
        steps.add(c -> {
            readded = LiveLayers.open("readded", false);
            c.frames(10);
        });
        steps.add(c -> {
            LiveLayers.detach(readded);
            c.expect(LiveLayers.index(readded) < 0, "test setup: the layer is off the screen panel");
            PlanetAPI planet = somePlanet();
            c.expect(planet != null, "a market for the dialog's core UI");
            if (planet != null) dialog.getVisualPanel().showCore(CoreUITabId.CARGO, planet, () -> { });
            c.frames(30);
        });
        steps.add(c -> {
            c.expect(LiveLayers.index(readded) >= 0 && readded.isOpen(), "a core UI made while a layer is off the screen panel puts it back");
            c.expect(LiveLayers.index(readded) > LiveLayers.index(dialog), "on top, above the dialog");
            c.shot("layers-readded");
        });
        steps.add(c -> {
            readded.close();
            c.keepDialog(false);
            c.frames(20);
        });
        return steps;
    }
}
