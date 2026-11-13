package livecheck;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomVisualDialogDelegate;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Skills;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.ht.HyperspaceTopographyEventIntel;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.MapParams;
import org.lwjgl.util.vector.Vector2f;
import org.starficz.starui.core.BoxedSectorMap;
import org.starficz.starui.livecheck.LivePorts;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// The StarUI ports that need a running campaign: SkillPanel, EventProgressBar, SurveyRequirements, SectorMap and
// PlaythroughData, built in a real interaction dialog's custom visual panel. The first step proves the game really is
// in a campaign (state, player fleet, a generated sector) before anything else runs.
public final class PortChecks {
    private PortChecks() {
    }

    private static final float WIDTH = 1150f;
    private static final float HEIGHT = 820f;

    private static Map<String, CustomPanelAPI> made;
    private static PlanetAPI planet;
    private static BoxedSectorMap firstMap;
    private static Vector2f centreBefore;

    private static PlanetAPI somePlanet() {
        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (system.isProcgen()) continue;
            for (PlanetAPI p : system.getPlanets()) {
                if (!p.isStar() && !p.isGasGiant() && p.getMarket() != null) return p;
            }
        }
        return null;
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();

        steps.add(c -> {
            c.eq(Global.getCurrentState(), GameState.CAMPAIGN, "the game is in the campaign");
            CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
            c.expect(fleet != null && fleet.getContainingLocation() != null, "the player fleet exists and is somewhere");
            c.expect(fleet != null && fleet.getFleetData().getNumMembers() > 0, "the player fleet has ships");
            int systems = Global.getSector().getStarSystems().size();
            c.expect(systems > 20, "the sector is generated (" + systems + " star systems)");
            c.note("campaign date " + Global.getSector().getClock().getDateString() + ", fleet in "
                + (fleet == null ? "nothing" : fleet.getContainingLocation().getName()));
            c.shot("campaign");
        });

        steps.add(c -> {
            planet = somePlanet();
            c.expect(planet != null, "found a core-world planet for the survey and the map");
            c.keepDialog(true);
            boolean shown = Global.getSector().getCampaignUI().showInteractionDialog(new Ui.Bare(), Global.getSector().getPlayerFleet());
            c.expect(shown, "an interaction dialog opened");
            c.frames(30);
        });

        steps.add(c -> {
            InteractionDialogAPI dialog = Global.getSector().getCampaignUI().getCurrentInteractionDialog();
            c.expect(dialog != null, "the interaction dialog is current");
            if (dialog == null || planet == null) return;
            HyperspaceTopographyEventIntel intel = HyperspaceTopographyEventIntel.get();
            if (intel == null) intel = new HyperspaceTopographyEventIntel(null, false);
            intel.setProgress(250);
            final BaseEventIntel event = intel;
            // a skip-tutorial character has no skills, and vanilla's panel then only says "None"
            Global.getSector().getPlayerPerson().getStats().setSkillLevel(Skills.HELMSMANSHIP, 2);
            Global.getSector().getPlayerPerson().getStats().setSkillLevel(Skills.TARGET_ANALYSIS, 1);
            Global.getSector().getPlayerPerson().getStats().setSkillLevel(Skills.NAVIGATION, 1);
            Global.getSector().getPlayerPerson().getStats().setSkillLevel(Skills.INDUSTRIAL_PLANNING, 1);
            final MapParams params = new MapParams();
            params.showSystem(planet.getStarSystem());
            params.positionToShowAllMarkersAndSystems(true, 300f);
            dialog.showCustomVisualDialog(WIDTH, HEIGHT, new CustomVisualDialogDelegate() {
                @Override public void init(CustomPanelAPI panel, DialogCallbacks callbacks) {
                    made = LivePorts.build(panel, WIDTH, Global.getSector().getPlayerPerson(), event, planet, params);
                }
                @Override public CustomUIPanelPlugin getCustomPanelPlugin() { return null; }
                @Override public float getNoiseAlpha() { return 0f; }
                @Override public void advance(float amount) { }
                @Override public void reportDismissed(int option) { }
            });
            c.frames(60);
        });

        steps.add(c -> {
            c.expect(made != null, "the ports were built in the dialog's custom panel");
            if (made == null) return;
            for (Map.Entry<String, CustomPanelAPI> e : made.entrySet()) {
                CustomPanelAPI p = e.getValue();
                float w = p.getPosition().getWidth(), h = p.getPosition().getHeight();
                c.expect(w > 50f && h > 15f, e.getKey() + " has a size (" + w + " x " + h + ")");
                c.expect(!p.getChildrenCopy2().isEmpty(), e.getKey() + " holds vanilla's component");
            }
            String skills = Ui.text(made.get("SkillPanel"));
            c.expect(skills.contains("Helmsmanship") && skills.contains("Target Analysis"),
                "SkillPanel shows the player's skills: " + skills.replace('\n', '|'));
            c.expect(!Ui.text(made.get("SkillPanel")).contains("None"), "and not vanilla's \"None\"");
            BaseEventIntel event = HyperspaceTopographyEventIntel.get();
            float bar = made.get("EventProgressBar").getPosition().getWidth();
            c.expect(event != null && bar >= event.getBarWidth() && bar <= event.getBarWidth() + 60f,
                "EventProgressBar is as wide as the event's own bar (" + bar + " for a " + (event == null ? 0 : event.getBarWidth()) + " bar)");
            String survey = Ui.text(made.get("SurveyRequirements"));
            c.expect(survey.contains("Supplies") || survey.contains("Heavy machinery") || survey.contains("Crew"),
                "SurveyRequirements lists the resources a survey needs: " + survey.replace('\n', '|'));
            c.expect(survey.contains("survey"), "and vanilla's line above them");
            firstMap = LivePorts.map(made.get("SectorMap"));
            c.expect(firstMap != null, "SectorMap's .sectorMap gives vanilla's map");
            if (firstMap != null) {
                c.near(firstMap.getPosition().getWidth(), made.get("SectorMap").getPosition().getWidth(), 1f,
                    "the boxed map is the panel vanilla lays out, as wide as its holder");
                firstMap.centerOnEntity(planet);
            }
            c.shot("ports");
            c.frames(20);
        });

        steps.add(c -> {
            if (firstMap == null) return;
            centreBefore = new Vector2f(firstMap.getWorldCenter());
            c.note("map centred on " + planet.getName() + " at " + planet.getLocation() + ": view centre " + centreBefore);
            LivePorts.mapHeight.setValue(220f);
            c.frames(30);
        });

        steps.add(c -> {
            if (firstMap == null) return;
            CustomPanelAPI holder = made.get("SectorMap");
            c.near(holder.getPosition().getHeight(), 220f, 1f, "the SectorMap panel took its new height");
            BoxedSectorMap again = LivePorts.map(holder);
            c.expect(again != null && again.getMap() != firstMap.getMap(), "a new size made vanilla's map again");
            if (again != null) {
                Vector2f now = again.getWorldCenter();
                // vanilla's getWorldCenter truncates to whole units and measures from the whole map panel, while
                // centerOn centres its scroller: the same view reads up to ~100 units apart (a few screen pixels)
                c.near(Vector2f.sub(now, centreBefore, null).length(), 0f, 150f,
                    "the new map keeps the old one's centre (" + centreBefore + " -> " + now + ")");
                c.near(again.getPosition().getHeight(), 220f, 1f, "and is laid out at the new height");
            }
            c.shot("ports-map-resized");
            c.keepDialog(false);
        });
        return steps;
    }
}
