package data.scripts.campaign.intel.events;

import java.util.EnumSet;
import java.util.Set;

import java.awt.Color;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.campaign.listeners.CharacterStatsRefreshListener;
import com.fs.starfarer.api.campaign.listeners.CurrentLocationChangedListener;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseFactorTooltip;
import com.fs.starfarer.api.impl.campaign.intel.events.EventFactor;
import com.fs.starfarer.api.impl.campaign.intel.events.HAShipsDestroyedFactor;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.TimeoutTracker;

public class armaa_combatDataEventIntel extends BaseEventIntel implements FleetEventListener,
        CharacterStatsRefreshListener,
        CurrentLocationChangedListener {

    public static Color BAR_COLOR = Global.getSettings().getColor("progressBarFleetPointsColor");

    public static int PROGRESS_MAX = 1000;
    public static int PROGRESS_1 = 200;
    public static int PROGRESS_2 = 400;
    public static int PROGRESS_3 = 600;
    public static int PROGRESS_4 = 800;
    public static float FP_PER_POINT = Global.getSettings().getFloat("HA_fleetPointsPerPoint");
    public static float BASE_DETECTION_RANGE_LY = 3f;
    public static float RANGE_WITHIN_WHICH_SENSOR_ARRAYS_HELP_LY = 5f;
    public static float RANGE_PER_DOMAIN_SENSOR_ARRAY = 2f;
    public static float RANGE_PER_MAKESHIFT_SENSOR_ARRAY = 1f;
    public static int MAX_SENSOR_ARRAYS = 3;
    public static float WAYSTATION_BONUS = 2f;

    public static float SLIPSTREAM_FUEL_MULT = 0.25f;
    public static float HYPER_BURN_BONUS = 3f;

    public static String KEY = "$armaa_atac_ref";

    public static enum Stage {
        START,
        MINOR_INSIGHTS,
        ADVANCED_TELEMETRY,
        PROTOTYPE_BREAKTHROUGH,
        AUTOMATION,
        DATA_DELIVERY,
    }

    public static float RECENT_READINGS_TIMEOUT = 30f;
    public static float RECENT_READINGS_RANGE_LY = 10f;

    public static class RecentTopographyReadings {

        public Vector2f loc;

        public RecentTopographyReadings(Vector2f loc) {
            this.loc = loc;
        }
    }

    public static void addFactorCreateIfNecessary(EventFactor factor, InteractionDialogAPI dialog) {
        if (get() == null) {
            new armaa_combatDataEventIntel(null, false);
        }
        if (get() != null) {
            get().addFactor(factor, dialog);
        }
    }

    public static armaa_combatDataEventIntel get() {
        return (armaa_combatDataEventIntel) Global.getSector().getMemoryWithoutUpdate().get(KEY);
    }

    protected TimeoutTracker<RecentTopographyReadings> recent = new TimeoutTracker<RecentTopographyReadings>();

//	public static float CHECK_DAYS = 0.1f;
//	protected IntervalUtil interval = new IntervalUtil(CHECK_DAYS * 0.8f, CHECK_DAYS * 1.2f);
//	protected float burnBasedPoints = 0f;
    public armaa_combatDataEventIntel(TextPanelAPI text, boolean withIntelNotification) {
        super();

        Global.getSector().getMemoryWithoutUpdate().set(KEY, this);

        setup();

        // now that the event is fully constructed, add it and send notification
        Global.getSector().getIntelManager().addIntel(this, !withIntelNotification, text);
    }

    protected void setup() {
        factors.clear();
        stages.clear();
        int MAX_PROGRESS = PROGRESS_MAX;

        setMaxProgress(MAX_PROGRESS);

        addStage(Stage.START, 0);
        addStage(Stage.MINOR_INSIGHTS, PROGRESS_1);
        addStage(Stage.ADVANCED_TELEMETRY, PROGRESS_2);
        addStage(Stage.PROTOTYPE_BREAKTHROUGH, PROGRESS_3);
        addStage(Stage.AUTOMATION, PROGRESS_4);
        addStage(Stage.DATA_DELIVERY, MAX_PROGRESS);

        getDataFor(Stage.MINOR_INSIGHTS).keepIconBrightWhenLaterStageReached = true;
        getDataFor(Stage.ADVANCED_TELEMETRY).keepIconBrightWhenLaterStageReached = true;
        getDataFor(Stage.PROTOTYPE_BREAKTHROUGH).keepIconBrightWhenLaterStageReached = true;
        getDataFor(Stage.AUTOMATION).keepIconBrightWhenLaterStageReached = true;
        getDataFor(Stage.DATA_DELIVERY).keepIconBrightWhenLaterStageReached = true;

    }

    protected Object readResolve() {
        //if (getDataFor(Stage.GENERATE_SLIPSURGE) == null) {
        //	setup();
        //}
        return this;
    }

    @Override
    protected void notifyEnding() {
        super.notifyEnding();
    }

    @Override
    protected void notifyEnded() {
        super.notifyEnded();
        Global.getSector().getMemoryWithoutUpdate().unset(KEY);
    }

    protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate,
            Color tc, float initPad) {

        if (addEventFactorBulletPoints(info, mode, isUpdate, tc, initPad)) {
            return;
        }

        Color h = Misc.getHighlightColor();
        if (isUpdate && getListInfoParam() instanceof EventStageData) {
            EventStageData esd = (EventStageData) getListInfoParam();
            if (esd.id == Stage.MINOR_INSIGHTS) {
                info.addPara("New hullmods unlocked", tc, initPad);
            }
            if (esd.id == Stage.ADVANCED_TELEMETRY) {
//				info.addPara("Fuel use while traversing slipstreams multiplied by %s", initPad, tc,
//						h, "" + SLIPSTREAM_FUEL_MULT + Strings.X);
                info.addPara("Performance of ships with %s hullmod enhanced", initPad, tc,
                        h, "STRIKECRAFT");
            }
            if (esd.id == Stage.AUTOMATION) {
                info.addPara("New hullmods unlocked", initPad, tc, h, "MORGANA", "GAMLIN");
            }
            if (esd.id == Stage.PROTOTYPE_BREAKTHROUGH) {
                info.addPara("Prototype fighter %s delivered to %s for testing", initPad, tc, h, "MORGANA", "GAMLIN");
            }
            if (esd.id == Stage.DATA_DELIVERY) {
                info.addPara("New hullmods unlocked", tc, initPad);
            }
            return;
        }

//		EventStageData esd = getLastActiveStage(false);
//		if (esd != null && EnumSet.of(Stage.START, Stage.HA_1, Stage.HA_2, Stage.HA_3, Stage.HA_4).contains(esd.id)) {
//
//		}
    }

    public float getImageSizeForStageDesc(Object stageId) {
//		if (stageId == STAGE.PROTOTYPE_BREAKTHROUGH || stageId == Stage.GENERATE_SLIPSURGE) {
//			return 48f;
//		}
        if (stageId == Stage.START) {
            return 64f;
        }
        return 48f;
    }

    public float getImageIndentForStageDesc(Object stageId) {
//		if (stageId == STAGE.PROTOTYPE_BREAKTHROUGH || stageId == Stage.GENERATE_SLIPSURGE) {
//			return 16f;
//		}
        if (stageId == Stage.START) {
            return 0f;
        }
        return 16f;
    }

    @Override
    public void addStageDescriptionText(TooltipMakerAPI info, float width, Object stageId) {
        float opad = 10f;
        float small = 0f;
        Color h = Misc.getHighlightColor();

        //setProgress(0);
        //setProgress(199);
        //setProgress(600);
        //setProgress(899);
        //setProgress(1000);
        //setProgress(499);
        //setProgress(600);
        EventStageData stage = getDataFor(stageId);
        if (stage == null) {
            return;
        }

//		if (isStageActiveAndLast(stageId) &&  stageId == Stage.START) {
//			addStageDesc(info, stageId, small, false);
//		} else if (isStageActive(stageId) && stageId != Stage.START) {
//			addStageDesc(info, stageId, small, false);
//		}
        if (isStageActive(stageId)) {
            addStageDesc(info, stageId, small, false);
        }
    }

    public void addStageDesc(TooltipMakerAPI info, Object stageId, float initPad, boolean forTooltip) {
        float opad = 10f;
        Color h = Misc.getHighlightColor();
        if (stageId == Stage.START) {
            info.addPara("Participating with ATAC allows access to prototype technology and experimental units. "
                    + "Operatives earn evaluation points by surviving engagements while using Arma Armatura hardware."
                    + "Higher point totals grant access to more rewards.",
                    initPad);
        } else if (stageId == Stage.MINOR_INSIGHTS) {
            info.addPara("Data gleaned from your ops "
                    + "has prompted the design of several new technologies "
                    + "for %s. Design notes for several hullmods have been transferred to you:\n "
                    + "%s,%s,%s.", initPad,
                    Misc.getHighlightColor(),
                    "STRIKECRAFT",
                    "C-Stim Dispatcher",
                    "Thermoflux Drive",
                    "Targeting Disruptor"
            );
        } else if (stageId == Stage.ADVANCED_TELEMETRY) {
            info.addPara("Ships with the %s hullmod and piloted by officers have enhanced performance: "
                    + "%s and %s are improved by %s. Shield Efficiency increased by %s.",
                    initPad, Misc.getHighlightColor(),
                    "STRIKECRAFT",
                    "Flux Dissipation",
                    "Flux Capacity",
                    "15%", //dont hardcode it
                    "10%" //dont hardoced this either
            );
        } else if (stageId == Stage.PROTOTYPE_BREAKTHROUGH) {
            info.addPara("The reverse-engineered fighter %s has been left in storage "
                    + "at %s for use, at your discretion.", initPad, h,
                    "MORGANA",
                    "GAMLIN");
        } else if (stageId == Stage.AUTOMATION) {
            info.addPara("Data gleaned from your ops "
                    + "has prompted the design of several new technologies "
                    + "for %s. Design notes for several hullmods have been transferred to you:\n "
                    + "%s,%s,%s.", initPad,
                    Misc.getHighlightColor(),
                    "STRIKECRAFT",
                    "Automaton Control Shell",
                    "OVERLORD SUITE[GAMMA]",
                    "OVERLORD SUITE[BETA]"
            );
        } else if (stageId == Stage.DATA_DELIVERY) {
            info.addPara("Data gleaned from your ops "
                    + "has prompted the design of several new technologies "
                    + "for %s. Design notes for several hullmods have been transferred to you:\n "
                    + "%s,%s,%s.", initPad,
                    Misc.getHighlightColor(),
                    "STRIKECRAFT",
                    "Hi-Manuever System",
                    "Emergency Recall Device",
                    "OVERLORD SUITE[ALPHA]"
            );
        }
    }

    public TooltipCreator getStageTooltipImpl(Object stageId) {
        final EventStageData esd = getDataFor(stageId);

        if (esd != null && EnumSet.of(Stage.MINOR_INSIGHTS, Stage.ADVANCED_TELEMETRY,
                Stage.PROTOTYPE_BREAKTHROUGH, Stage.AUTOMATION, Stage.DATA_DELIVERY).contains(esd.id)) {
            return new BaseFactorTooltip() {
                @Override
                public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                    float opad = 10f;

                    if (esd.id == Stage.MINOR_INSIGHTS) {
                        tooltip.addTitle("Minor Insights");
                    } else if (esd.id == Stage.ADVANCED_TELEMETRY) {
                        tooltip.addTitle("Real-Time Tactical Yield Performance Enhancement");
                    } else if (esd.id == Stage.PROTOTYPE_BREAKTHROUGH) {
                        tooltip.addTitle("Prototype Breakthroughs");
                    } else if (esd.id == Stage.AUTOMATION) {
                        tooltip.addTitle("Automation");
                    } else if (esd.id == Stage.DATA_DELIVERY) {
                        tooltip.addTitle("Strategic Convergence");
                    }

                    addStageDesc(tooltip, esd.id, opad, true);

                    esd.addProgressReq(tooltip, opad);
                }
            };
        }

        return null;
    }

    @Override
    public String getIcon() {
        return Global.getSettings().getSpriteName("events", "armaa_analysis_start");
    }

    protected String getStageIconImpl(Object stageId) {
        EventStageData esd = getDataFor(stageId);
        if (esd == null) {
            return null;
        }

        if (EnumSet.of(Stage.MINOR_INSIGHTS, Stage.PROTOTYPE_BREAKTHROUGH, Stage.AUTOMATION, Stage.ADVANCED_TELEMETRY, Stage.DATA_DELIVERY, Stage.START).contains(esd.id)) {
            return Global.getSettings().getSpriteName("events", "armaa_analysis_" + ((Stage) esd.id).name().toLowerCase());
        }
        // should not happen - the above cases should handle all possibilities - but just in case
        return Global.getSettings().getSpriteName("events", "armaa_analysis_start");
    }

    @Override
    public Color getBarColor() {
        Color color = BAR_COLOR;
        //color = Misc.getBasePlayerColor();
        color = Misc.interpolateColor(color, Color.black, 0.25f);
        return color;
    }

    @Override
    public Color getBarProgressIndicatorColor() {
        return super.getBarProgressIndicatorColor();
    }

    @Override
    protected int getStageImportance(Object stageId) {
        return super.getStageImportance(stageId);
    }

    @Override
    protected String getName() {
        return "ATAC Combat Analysis";
    }

    public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, FleetDespawnReason reason, Object param) {

    }

    public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle) {
        if (isEnded() || isEnding()) {
            return;
        }

        if (!battle.isPlayerInvolved()) {
            return;
        }
        boolean hasValidCraft = false;
        for (FleetMemberAPI member : Global.getSector().getPlayerFleet().getMembersWithFightersCopy()) {
            if (member.getHullSpec().getManufacturer().equals("Arma Armatura") || member.getVariant().hasHullMod("strikeCraft")) {
                hasValidCraft = true;
                break;
            }
        }
        if (!hasValidCraft) {
            return;
        }
        float fpDestroyed = 0;
        CampaignFleetAPI first = null;
        for (CampaignFleetAPI otherFleet : battle.getNonPlayerSideSnapshot()) {
            //if (!Global.getSector().getPlayerFaction().isHostileTo(otherFleet.getFaction())) continue;
            for (FleetMemberAPI loss : Misc.getSnapshotMembersLost(otherFleet)) {
                fpDestroyed += loss.getFleetPointCost();
                if (first == null) {
                    first = otherFleet;
                }
            }
        }

        int points = computeProgressPoints(fpDestroyed);
        if (points > 0) {
            //points = 700;
            HAShipsDestroyedFactor factor = new HAShipsDestroyedFactor(points);
            //sendUpdateIfPlayerHasIntel(factor, false); // addFactor now sends update
            addFactor(factor);
        }
        //HAShipsDestroyedFactor factor = new HAShipsDestroyedFactor(-1 * 10);
        //sendUpdateIfPlayerHasIntel(factor, false);
        //addFactor(factor);
    }

    public static int computeProgressPoints(float fleetPointsDestroyed) {
        if (fleetPointsDestroyed <= 0) {
            return 0;
        }

        int points = Math.round(fleetPointsDestroyed / FP_PER_POINT);
        if (points < 1) {
            points = 1;
        }
        return points;
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add("armaarmatura");
        //tags.remove(Tags.INTEL_MAJOR_EVENT);
        return tags;
    }

    @Override
    protected void advanceImpl(float amount) {
        super.advanceImpl(amount);
        applyFleetEffects();

        float days = Global.getSector().getClock().convertToDays(amount);
        recent.advance(days);

        //setProgress(getProgress() + 10);
    }

    public void addAbility(String id) {
        if (Global.getSector().getPlayerFleet().hasAbility(id)) {
            return;
        }
    }

    @Override
    protected void notifyStageReached(EventStageData stage) {
        //applyFleetEffects();
        if (stage.id == Stage.MINOR_INSIGHTS) {
            if (!Global.getSector().getPlayerFaction().knowsHullMod("armaa_sykoStims")) {
                Global.getSector().getPlayerFaction().addKnownHullMod("armaa_sykoStims");
            }
            if (!Global.getSector().getPlayerFaction().knowsHullMod("armaa_targetingDisruptor")) {
                Global.getSector().getPlayerFaction().addKnownHullMod("armaa_targetingDisruptor");
            }
            if (!Global.getSector().getPlayerFaction().knowsHullMod("armaa_wingClipper2")) {
                Global.getSector().getPlayerFaction().addKnownHullMod("armaa_wingClipper2");
            }
        }

        if (stage.id == Stage.ADVANCED_TELEMETRY) {
            Global.getSector().getPlayerPerson().getStats().setSkillLevel("armaa_strikeCraftBuff", 1);
        }

        if (stage.id == Stage.PROTOTYPE_BREAKTHROUGH) // what if seraph doesn't exist tho??
        // I guess this is fine, since the only way to get this event is from seraph
        // Ergo, if she doesn't exist, we don't need to worry about it
        {
            if (Global.getSector().getImportantPeople().getPerson("armaa_seraph") != null && Global.getSector().getImportantPeople().getPerson("armaa_seraph").getMarket() != null) {
                if (!Global.getSector().getMemoryWithoutUpdate().contains("$armaa_hasMorgana")) {
                    Global.getSector().getImportantPeople().getPerson("armaa_seraph").getMarket().getSubmarket(
                            Submarkets.SUBMARKET_STORAGE).getCargo().addMothballedShip(FleetMemberType.SHIP, "armaa_morgana_standard", "Morgana");
                    Global.getSector().getMemoryWithoutUpdate().set("$armaa_hasMorgana", true);
                }
            }
        }

        if (stage.id == Stage.AUTOMATION) {
            if (!Global.getSector().getPlayerFaction().knowsHullMod("armaa_automatedCognitionShell")) {
                Global.getSector().getPlayerFaction().addKnownHullMod("armaa_automatedCognitionShell");
            }
            if (!Global.getSector().getPlayerFaction().knowsHullMod("armaa_skyMindGamma")) {
                Global.getSector().getPlayerFaction().addKnownHullMod("armaa_skyMindGamma");
            }
            if (!Global.getSector().getPlayerFaction().knowsHullMod("armaa_skyMindBeta")) {
                Global.getSector().getPlayerFaction().addKnownHullMod("armaa_skyMindBeta");
            }
        }
        if (stage.id == Stage.DATA_DELIVERY) {
            if (!Global.getSector().getPlayerFaction().knowsHullMod("armaa_emergencyRecallDevice")) {
                Global.getSector().getPlayerFaction().addKnownHullMod("armaa_emergencyRecallDevice");
            }
            if (!Global.getSector().getPlayerFaction().knowsHullMod("armaa_himac")) {
                Global.getSector().getPlayerFaction().addKnownHullMod("armaa_himac");
            }
            if (!Global.getSector().getPlayerFaction().knowsHullMod("armaa_skyMindAlpha")) {
                Global.getSector().getPlayerFaction().addKnownHullMod("armaa_skyMindAlpha");
            }
        }
    }

    public void reportAboutToRefreshCharacterStatEffects() {

    }

    public void reportCurrentLocationChanged(LocationAPI prev, LocationAPI curr) {
        //applyFleetEffects();
    }

    public void reportRefreshedCharacterStatEffects() {
        // called when opening colony screen, so the Spaceport tooltip gets the right values
        //updateMarketDetectionRanges();
        //applyFleetEffects();
    }

    public void applyFleetEffects() {

    }

    public boolean withMonthlyFactors() {
        return false;
    }

    protected String getSoundForStageReachedUpdate(Object stageId) {
        if (stageId == Stage.PROTOTYPE_BREAKTHROUGH) {
            return "ui_learned_ability";
        }
        return super.getSoundForStageReachedUpdate(stageId);
    }

    @Override
    protected String getSoundForOneTimeFactorUpdate(EventFactor factor) {
//		if (factor instanceof HTAbyssalLightFactor) {
//			return "sound_none";
//		}
        return null;
    }

}
