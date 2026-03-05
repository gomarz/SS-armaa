package data.scripts.campaign.missions;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.PersonImportance;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.FullName.Gender;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Skills;
import com.fs.starfarer.api.impl.campaign.ids.Voices;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithSearch;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import java.util.Random;


public class armaa_RedEarthIronCages extends HubMissionWithSearch {

    public static enum Stage {
        GO_TO_CRUOUR,
        RETRIEVE_TARGET,
        RETURN_TO_CONTACT,
        COMPLETED
    }
    protected MarketAPI market;
    protected MarketAPI createdAt;
    final boolean haveAnime = Global.getSettings().getModManager().isModEnabled("zzarmaa_anime");
    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        // if already accepted by the player, abort
        if (!setGlobalReference("$armaa_reic_ref", "$armaa_reic_inProgress")) {
            return false;
        }
        setStartingStage(Stage.GO_TO_CRUOUR);
        addSuccessStages(Stage.COMPLETED);        
        this.createdAt = createdAt;
        String facOwner = Global.getSector().getEconomy().getMarket("cruor").getFactionId();
        MarketAPI altMarket = null;
        if(market == null)
        {
            resetSearch();
            requireMarketIsNot(createdAt);
            preferMarketFaction("sindrian_diktat");
            requireMarketNotHidden();
            requireMarketNotInHyperspace();
            preferMarketSizeAtLeast(3);
            preferMarketSizeAtMost(7);
            preferMarketInDirectionOfOtherMissions();
            altMarket = pickMarket();        
        }
        market = facOwner.equals("sindrian_diktat") ?  Global.getSector().getEconomy().getMarket("cruor") : altMarket;
        if(market == null )
            return false;
        setStoryMission();
        if(Global.getSector().getImportantPeople().getPerson("armaa_lingfeng") == null)
            createMissionCharacters();
        makeImportant(market, "$armaa_reic_tookTheJob", Stage.GO_TO_CRUOUR);
        setStageOnGlobalFlag(Stage.RETRIEVE_TARGET, "$armaa_gotReicLoc");
        makeImportant(market, "$armaa_gotReicLoc", Stage.RETRIEVE_TARGET);
        //
        beginEnteredLocationTrigger(market.getStarSystem(), false, Stage.RETRIEVE_TARGET);
        triggerCreateFleet(FleetSize.MEDIUM, FleetQuality.DEFAULT, Factions.LIONS_GUARD, FleetTypes.PATROL_MEDIUM, market.getStarSystem());
        triggerAutoAdjustFleetStrengthMajor();
        triggerMakeHostileAndAggressive();
        triggerFleetAllowLongPursuit();
        triggerSetFleetAlwaysPursue();
        triggerMakeLowRepImpact();
        triggerPickLocationTowardsPlayer(market.getPlanetEntity() != null ? market.getPlanetEntity() : Global.getSector().getPlayerFleet(), 90f, getUnits(0.15f));
        triggerSpawnFleetAtPickedLocation("$armaa_reic_SDTPatrol", null);
        triggerSetFleetMissionRef("$armaa_reic_ref");
        triggerOrderFleetInterceptPlayer();
        triggerFleetMakeImportant(null, Stage.RETRIEVE_TARGET);
        endTrigger();
        //
        // Diktat patrol that gives intel + heat.
        beginStageTrigger(Stage.GO_TO_CRUOUR);
        triggerCreateFleet(FleetSize.SMALL, FleetQuality.DEFAULT, Factions.DIKTAT, FleetTypes.PATROL_MEDIUM, market.getStarSystem());
        triggerMakeNonHostile();
        triggerMakeNoRepImpact();
        triggerMakeFleetIgnoredByOtherFleets();
        triggerMakeFleetIgnoreOtherFleetsExceptPlayer();
        triggerMakeFleetIgnoreOtherFleetsExceptPlayer(); // don't go chasing others, please.
        triggerPickLocationAroundEntity(market.getPlanetEntity(), 800f);
        triggerSetFleetMissionRef("$armaa_reic_ref"); // so they can be made unimportant
        triggerFleetMakeImportant(null, Stage.GO_TO_CRUOUR);
        triggerFleetAddDefeatTrigger("armaa_reicPatrolDefeated");
        triggerFleetSetName("Cruor Logistics Escort");
        triggerSaveGlobalFleetRef("$armaa_reic_smugglerPatrol"); 
        triggerSetPatrol();
        triggerOrderFleetPatrol(market.getStarSystem());
        triggerFleetSetPatrolLeashRange(1000f);
        triggerSpawnFleetAtPickedLocation("$armaa_reic_interceptFleet", null);  
        triggerFleetSetTravelActionText("Moving sensitive cargo");
        endTrigger();
        //
        setStageOnGlobalFlag(Stage.RETURN_TO_CONTACT, "$armaa_reicGotPrisoner");
        makeImportant(createdAt, "$armaa_reicGotPrisoner", Stage.RETURN_TO_CONTACT);
        setStageOnGlobalFlag(Stage.COMPLETED, "$armaa_reic_Completed");

        setRepFactionChangesNone();
        setRepPersonChangesNone();

        beginStageTrigger(Stage.COMPLETED);
        triggerSetGlobalMemoryValue("$armaa_reic_missionCompleted", true);
        endTrigger();

        return true;
    }

    protected void updateInteractionDataImpl() {
        set("$armaa_reic_stage", getCurrentStage());
        set("$armaa_reic_marketName", market.getName());
        set("$armaa_reic_marketId", market.getId());
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
        if (currentStage == Stage.GO_TO_CRUOUR) {
            info.addPara("Retrieve the prisoner from the camp", opad);
        }
        if (currentStage == Stage.RETURN_TO_CONTACT) {
            info.addPara("Return the prisoner to " + createdAt.getName() + ".", opad);
        }
    }

    @Override
    public boolean addNextStepText(TooltipMakerAPI info, Color tc, float pad) {
        Color h = Misc.getHighlightColor();
        if (currentStage == Stage.GO_TO_CRUOUR) {
            info.addPara("Retrieve the prisoner from the camp", pad);
            return true;
        }
        if (currentStage == Stage.RETURN_TO_CONTACT) {
            info.addPara("Return the prisoner to " + createdAt.getName() + ".", pad);
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
        return "Red Earth, Iron Cages";
    }

    @Override
    public String getPostfixForState() {
        if (startingStage != null) {
            return "";
        }
        return super.getPostfixForState();
    }
    
    private void createMissionCharacters() {
        //lingfeng
        PersonAPI lingfeng = Global.getSector().getFaction("armaarmatura_pirates").createRandomPerson();
        lingfeng.setRankId(Ranks.AGENT);
        lingfeng.setPostId(Ranks.POST_AGENT);
        if (haveAnime) {
            lingfeng.setPortraitSprite("graphics/armaa/portraits/armaa_lingfeng_anim.png");
        } else {
            lingfeng.setPortraitSprite("graphics/armaa/portraits/armaa_lingfeng.png");
        }
        lingfeng.setPortraitSprite("graphics/armaa/portraits/armaa_lingfeng.png");

        //lingfeng.setGender(FullName.Gender.FEMALE);
        //lingfeng.addTag(Tags.CONTACT_MILITARY);	
        //use callsign, maybe have her name change when rep is high enough?
        lingfeng.getName().setFirst("Lingfeng");
        lingfeng.getName().setLast("Gu");
        lingfeng.setId("armaa_lingfeng");
        lingfeng.setGender(Gender.MALE);
        lingfeng.setImportanceAndVoice(PersonImportance.LOW, new Random());
        lingfeng.setVoice(Voices.SPACER);
        lingfeng.setPersonality("cautious");
        Global.getSector().getImportantPeople().addPerson(lingfeng);

        //skills
        lingfeng.getStats().increaseSkill(Skills.TARGET_ANALYSIS);
        lingfeng.getStats().increaseSkill(Skills.IMPACT_MITIGATION);
        lingfeng.getStats().increaseSkill(Skills.DAMAGE_CONTROL);
        lingfeng.getStats().increaseSkill(Skills.POLARIZED_ARMOR);
        lingfeng.getStats().increaseSkill(Skills.POINT_DEFENSE);
        lingfeng.getStats().increaseSkill(Skills.MISSILE_SPECIALIZATION);
        lingfeng.getStats().increaseSkill(Skills.SYSTEMS_EXPERTISE);
        lingfeng.getStats().setLevel(7);
        
        PersonAPI felix = Global.getSector().getFaction("independent").createRandomPerson();
        felix.setRankId(Ranks.PILOT);
        felix.setPostId(Ranks.POST_SPACER);
        felix.getName().setFirst("Broad Man");
        felix.getName().setLast("");
        felix.setId("armaa_felix");
        felix.setImportanceAndVoice(PersonImportance.LOW, new Random());
        felix.setVoice(Voices.SPACER);
        felix.setGender(Gender.MALE);
        felix.setPersonality("steady");
        Global.getSector().getImportantPeople().addPerson(felix);
        if (haveAnime) {
            felix.setPortraitSprite("graphics/armaa/portraits/armaa_felix_anim.png");
        } else {
            felix.setPortraitSprite("graphics/armaa/portraits/armaa_felix.png");
        }
        
        PersonAPI gira = Global.getSector().getFaction("independent").createRandomPerson();
        gira.setRankId(Ranks.PILOT);
        gira.setPostId(Ranks.POST_SPACER);
        gira.getName().setFirst("Thin Woman");
        gira.getName().setLast("");
        gira.setId("armaa_gira");
        gira.setImportanceAndVoice(PersonImportance.LOW, new Random());
        gira.setVoice(Voices.SPACER);
        gira.setPersonality("steady");
        gira.setGender(Gender.FEMALE);
        Global.getSector().getImportantPeople().addPerson(gira);
        if (haveAnime) {
            gira.setPortraitSprite("graphics/armaa/portraits/armaa_gira_anim.png");
        } else {
            gira.setPortraitSprite("graphics/armaa/portraits/armaa_gira.png");
        }

    }
}
