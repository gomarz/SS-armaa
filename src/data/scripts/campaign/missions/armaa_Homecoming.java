package data.scripts.campaign.missions;

import java.awt.Color;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithSearch;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;


public class armaa_Homecoming extends HubMissionWithSearch {

    public static enum Stage {
        GO_TO_PLANET,
        MEET_SERAPH,
        DEBRIEF,
        COMPLETED
    }
    protected MarketAPI market;

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        // if already accepted by the player, abort
        if (!setGlobalReference("$armaa_hc_ref", "$armaa_hc_inProgress")) {
            return false;
        }

        market = Global.getSector().getImportantPeople().getPerson("armaa_seraph").getMarket();
        setStartingStage(Stage.GO_TO_PLANET);
        addSuccessStages(Stage.COMPLETED);
        setStoryMission();
        makeImportant(market, "$armaa_hc_tookTheJob", Stage.GO_TO_PLANET);
        setStageOnGlobalFlag(Stage.MEET_SERAPH, "$armaa_hcArrived");
        makeImportant(Global.getSector().getImportantPeople().getPerson("armaa_seraph"), "$armaa_hcArrived", Stage.MEET_SERAPH);
        setStageOnGlobalFlag(Stage.DEBRIEF, "$armaa_hcMetSeraph");
        makeImportant(Global.getSector().getImportantPeople().getPerson("armaa_seraph"), "$armaa_hcMetSeraph", Stage.DEBRIEF);
        setStageOnGlobalFlag(Stage.COMPLETED, "$armaa_hc_Completed");

        setRepFactionChangesNone();
        setRepPersonChangesNone();

        beginStageTrigger(Stage.COMPLETED);
        triggerSetGlobalMemoryValue("$armaa_hc_missionCompleted", true);
        endTrigger();

        return true;
    }

    protected void updateInteractionDataImpl() {
        set("$armaa_hc_stage", getCurrentStage());
        set("$armaa_hc_marketName", market.getName());
        set("$armaa_hc_marketId", market.getId());
    }

    @Override
    protected boolean callAction(String action, String ruleId, final InteractionDialogAPI dialog,
            List<Token> params, final Map<String, MemoryAPI> memoryMap) {

        return super.callAction(action, ruleId, dialog, params, memoryMap);
    }

    @Override
    public void addDescriptionForNonEndStage(TooltipMakerAPI info, float width, float height) {
        float opad = 10f;
        Color h = Misc.getHighlightColor();
        if (currentStage == Stage.GO_TO_PLANET) {
            info.addPara("Transport Cassian to " + market.getName() + ".", opad);
        }
        if (currentStage == Stage.MEET_SERAPH) {
            info.addPara("Travel to chalet to speak with Sera, on " + market.getName() + ".", opad);
        }

        if (currentStage == Stage.DEBRIEF) {
            info.addPara("Return for debrief", opad);
        }
    }

    @Override
    public boolean addNextStepText(TooltipMakerAPI info, Color tc, float pad) {
        Color h = Misc.getHighlightColor();
        if (currentStage == Stage.GO_TO_PLANET) {
            info.addPara("Transport Cassian to " + market.getName() + ".", pad);
            return true;
        }
        if (currentStage == Stage.MEET_SERAPH) {
            info.addPara("Travel to chalet to speak with Sera, on " + market.getName() + ".", pad);
            return true;
        }
        if (currentStage == Stage.DEBRIEF) {
            info.addPara("Return for debrief", pad);
            return true;
        }
        if (currentStage == Stage.COMPLETED) {
            info.addPara("Completed", tc, pad);
            return true;
        }
        return false;
    }

    @Override
    public String getBaseName() {
        return "Homecoming";
    }

    @Override
    public String getPostfixForState() {
        if (startingStage != null) {
            return "";
        }
        return super.getPostfixForState();
    }
}
