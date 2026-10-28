package data.scripts.campaign.missions;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.ai.CampaignFleetAIAPI;
import com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.bases.LuddicPathBaseIntel;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithSearch;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithSearch.MarketRequirement;
import com.fs.starfarer.api.impl.campaign.missions.hub.MissionFleetInterceptPlayerIfNearby;
import com.fs.starfarer.api.impl.campaign.missions.hub.MissionFleetAutoDespawn;
import com.fs.starfarer.api.impl.campaign.missions.hub.ReqMode;
import com.fs.starfarer.api.impl.campaign.missions.hub.TriggerFleetAssignmentAI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import data.campaign.rulecmd.armaa_starfallPeopleCMD;
import data.scripts.campaign.armaa_starfallIntakeListener;

/**
 * Starfall Order graduation exercise.
 *
 * Entry is from rules.csv rather than a bar event or contact, so this extends
 * HubMissionWithSearch directly instead of HubMissionWithBarEvent. Started by
 * armaa_starfallCMD:
 *
 * armaa_starfallMission m = new armaa_starfallMission(); if
 * (m.create(dialog.getInteractionTarget().getMarket(), false)) {
 * m.accept(dialog, memoryMap); }
 *
 * accept() is a plain public method on BaseHubMission - it sets the starting
 * stage, files the intel, adds the mission as a sector script and calls
 * runTriggers(). No hub source required.
 *
 * The target is a pirate market chosen with the search DSL. In fiction the
 * studios picked it because it films well, which is why the garrison estimate
 * is stale - see the raid dialogue.
 */
public class armaa_starfallMission extends HubMissionWithSearch {

    public static float MISSION_DAYS = 120f;
    public static int MAX_TARGET_SIZE = 4;
    public static boolean DEBUG = true;

    /**
     * Escort behaviour, lifted from Nexerelin's FollowMeAbility. ORBIT_PASSIVE
     * rather than FOLLOW - FOLLOW only asks the AI to keep loose station, which
     * is why they drifted off; this holds them on the target. Both flags carry
     * the same duration and are refreshed by re-issuing, so there is nothing to
     * clean up.
     */
    public static final String ESCORT_REASON = "armaa_sfo_escort";
    public static final float ESCORT_DAYS = 7f;
    public static final FleetAssignment ESCORT_ASSIGNMENT = FleetAssignment.ORBIT_PASSIVE;

    /**
     * Set by the briefing. False - "fly with us" - and the intake sticks to the
     * player and joins the fight the player starts. True - "lead the attack" -
     * and it goes at the garrison itself, leaving the player to join as
     * reinforcement: riskier for the recruits, much better footage.
     */
    public static final String LEAD_ATTACK_KEY = "$armaa_sfo_leadAttack";

    /**
     * Once the garrison is dead the intake breaks off the exercise and closes
     * on the player so Roland can report in. This has to be its own reason
     * rather than reusing ESCORT_REASON: the escort flags carry ESCORT_DAYS and
     * are still live at this point, and FLEET_BUSY in particular has to come
     * off or the hail never opens.
     */
    public static final String RENDEZVOUS_REASON = "armaa_sfo_rendezvous";
    public static final float RENDEZVOUS_DAYS = 30f;
    /**
     * Duration of the intake's cross-system travel order to the Pather base.
     * Deliberately far longer than any real trip.
     */
    public static final float TRAVEL_DAYS = 90f;

    /**
     * Set by armaa_starfallCMD debriefOpened, from the hail rule. Advances
     * RAID_DONE -> DEBRIEF, which is what moves the map marker home.
     */
    public static final String DEBRIEF_OPENED_KEY = "$armaa_sfo_debriefOpened";

    /**
     * The recruits, in the order they get put on hulls. Which one ends up on
     * which Damascus does not matter, so there is nothing to match.
     */
    public static final String[] RECRUITS = new String[]{
        armaa_starfallPeopleCMD.VOIT,
        armaa_starfallPeopleCMD.EHREN,
        armaa_starfallPeopleCMD.ALARD,};

    /**
     * Base hull id from the .ship file - NOT a variant id.
     */
    public static final String RECRUIT_HULL = "armaa_ps_damascus";

    /**
     * Never picked, regardless of tags. Add mod bases here as needed.
     */
    public static final String[] PROTECTED_MARKETS = new String[]{
        "kantas_den", "new_maxios", "prism",};

    public static enum Stage {
        GO_TO_TARGET, // travel; intake intercepts and briefs the player
        RAID, // briefing done; garrison spawns and the exercise begins
        RAID_DONE, // garrison dead; intake breaks off to come alongside
        // Roland has hailed and the interrogation/Mercenary-Deny-Accept-Solo
        // conversation is under way - NOT yet resolved. Renamed off "DEBRIEF"
        // deliberately: that name used to be entered here, which made the
        // Fikenhild "meet Roland" text live the instant the hail opened,
        // before any decision had been made or any ambush fought. See
        // DEBRIEF below for the stage that name is reserved for now.
        DECISION,
        INVESTIGATE, // Accept/Solo: a Path base has been raised, the ambush is still out there
        // Resolved: either Deny was chosen, or Accept/Solo's ambush is dead.
        // Only entered via $armaa_sfo_readyToDebrief - see chooseDeny() and
        // armaa_starfallMission.ambushDefeated().
        DEBRIEF,
        COMPLETED,
        FAILED,
    }

    protected MarketAPI target;
    protected MarketAPI fikenhild;
    protected PersonAPI giver;
    protected MarketAPI presetTarget;
    protected MarketAPI avoidTarget;

    /**
     * Marines lost when a full-escort boarding party is captured. Rolled once
     * and exposed to the rules as $armaa_sfo_marineLosses (Call
     * $global.armaa_sfo_ref updateData), then taken with vanilla's
     * RemoveCommodity, same as TheUsurpers does with $sdtu_marineLosses.
     */
    protected int marineLosses;
    public static final int MARINE_LOSSES_MIN = 1;
    public static final int MARINE_LOSSES_MAX = 3;

    @Override
    protected void updateInteractionDataImpl() {
        if (marineLosses <= 0) {
            // missions created before this existed carry no roll
            marineLosses = MARINE_LOSSES_MIN
                    + new java.util.Random().nextInt(MARINE_LOSSES_MAX - MARINE_LOSSES_MIN + 1);
        }
        set("$armaa_sfo_marineLosses", marineLosses);
    }

    /**
     * Chosen at create() alongside the pirate target, same as
     * BornanewFilesFalseIdols does it for its own Pather station - a system is
     * picked once, up front, and only actually turned into a base if the
     * Accept/Solo branch needs it. Never touched by the Deny branch.
     */
    protected StarSystemAPI patherBaseSystem;
    protected LuddicPathBaseIntel patherStation;
    /** A Path base that already exists near the raid target, chosen at create(). Null if there was none. */
    protected LuddicPathBaseIntel existingPatherBase;
    protected SectorEntityToken investigateSite;

    /**
     * Must be called before create(). There is no bar event or contact here, so
     * hub is null and getPerson() would otherwise return null - which the intel
     * UI dereferences for a portrait.
     */
    public void setGiver(PersonAPI person) {
        this.giver = person;
    }

    @Override
    public boolean create(MarketAPI createdAt, boolean barEvent) {
        // one at a time, and survives a save/load via the global ref
        if (!setGlobalReference("$armaa_sfo_ref")) {
            return false;
        }

        // no hub, so the mission has no person of its own unless we give it one
        if (giver == null) {
            if (DEBUG) {
                Global.getLogger(armaa_starfallMission.class).info("[starfall] abort: no giver - BeginConversation must run before startMission");
            }
            return false;
        }
        setPersonOverride(giver);

        fikenhild = Global.getSector().getEconomy().getMarket("fikenhild");
        if (fikenhild == null) {
            if (DEBUG) {
                Global.getLogger(armaa_starfallMission.class).info("[starfall] abort: market id fikenhild not found");
            }
            return false;
        }

        // ---- pick the pirate base ----
        // The studios picked it because it films well, but we still keep off
        // anything a story is using or anything sitting in the core. Tried as
        // hard requirements first, then again as preferences if the sector has
        // nothing that qualifies.
        // Use the target the dialogue already showed the player, if there is
        // one. Without this create() runs its own search and commits to a
        // different market than the map marker pointed at.
        target = presetTarget;
        if (target == null) {
            target = findTarget(true);
        }
        if (target == null) {
            target = findTarget(false);
        }
        if (target == null) {
            if (DEBUG) {
                Global.getLogger(armaa_starfallMission.class).info(
                        "[starfall] abort: no pirate market matched on either pass");
            }
            return false;
        }

        if (!setMarketMissionRef(target, "$armaa_sfo_ref")) {
            return false;
        }

        // ---- pick a system to hide the Pather base in, same search
        // BornanewFilesFalseIdols uses for its own station. Picked now so a
        // dead sector doesn't strand the Accept/Solo branch later - if this
        // comes back null, spawnPatherBase() falls back to the target's
        // system rather than leaving the player with nowhere to go.
        existingPatherBase = findExistingPatherBase(target.getStarSystem());
        if (existingPatherBase != null) {
            patherBaseSystem = existingPatherBase.getSystem();
        } else {
            patherBaseSystem = pickPatherSystem(target.getStarSystem());
        }

        marineLosses = MARINE_LOSSES_MIN
                + genRandom.nextInt(MARINE_LOSSES_MAX - MARINE_LOSSES_MIN + 1);

        // ---- stages ----
        setStartingStage(Stage.GO_TO_TARGET);
        addSuccessStages(Stage.COMPLETED);
        addFailureStages(Stage.FAILED);

        makeImportant(target, "$armaa_sfo_target",
                Stage.GO_TO_TARGET, Stage.RAID, Stage.RAID_DONE);
        // the marker moves home once the decision is actually resolved -
        // DEBRIEF only, not DECISION, so it does not appear while Roland is
        // still crossing the system to reach you OR while the player is mid
        // interrogation/decision/ambush and has nothing to report yet
        makeImportant(fikenhild, "$armaa_sfo_home", Stage.DEBRIEF);

        // No $global. prefix - connectWithGlobalFlag resolves against global
        // memory itself, and vanilla writes these bare ("$rsom_raidedOutpost").
        // Two hops. The garrison must not exist until the intake has briefed the
        // player, or the two can collide first and the briefing never happens.
        connectWithGlobalFlag(Stage.GO_TO_TARGET, Stage.RAID, "$armaa_sfo_briefed");
        // RAID_DONE exists to separate "the garrison is dead" from "Roland has
        // said his piece". The defeat trigger fires inside vanilla's engagement
        // dialogue, which owns the option list and clears anything a rule adds
        // there - so it only does bookkeeping, and the scene itself runs off the
        // hail once the intake has closed. That is also the only point at which
        // the battle listener has written: it reports on reportBattleOccurred,
        // after the combat dialogue closes, so tallyLosses and score belong to
        // the hail node and not to the defeat trigger.
        connectWithGlobalFlag(Stage.RAID, Stage.RAID_DONE, "$armaa_sfo_raidDone");
        // Hailed and talking, decision not yet made - this used to jump
        // straight to DEBRIEF, which is exactly the bug the string stage key
        // ($armaa_sfo_stage) had too: "meet Roland at Fikenhild" text (and
        // the makeImportant marker above) would go live the instant the hail
        // opened, before Deny/Accept/Solo or the ambush had happened.
        connectWithGlobalFlag(Stage.RAID_DONE, Stage.DECISION, DEBRIEF_OPENED_KEY);
        // Deny jumps straight from DECISION to DEBRIEF; Accept/Solo detour
        // through INVESTIGATE first and only reach DEBRIEF once the ambush is
        // dead. $armaa_sfo_readyToDebrief is the one flag both routes set -
        // see armaa_starfallCMD.choosePath (Deny) and
        // armaa_starfallMission.ambushDefeated (Accept/Solo). setStageOnGlobalFlag
        // (from-any-stage) is used rather than connectWithGlobalFlag, which
        // would only fire out of one specific predecessor stage.
        setStageOnGlobalFlag(Stage.INVESTIGATE, "$armaa_sfo_investigating");
        setStageOnGlobalFlag(Stage.DEBRIEF, "$armaa_sfo_readyToDebrief");
        setStageOnGlobalFlag(Stage.COMPLETED, "$armaa_sfo_debriefed");

        setNoAbandon();
        setTimeLimit(Stage.FAILED, MISSION_DAYS, null);
        setRepFactionChangesNone();   // the King's office pays; nobody's reputation moves

        // ---- the intake's fleet ----
        // Spawns at Fikenhild when the job is accepted and flies out under its
        // own power, rather than materialising at the target. beginStageTrigger
        // fires as the stage begins, which is at accept - correct here.
        beginStageTrigger(Stage.GO_TO_TARGET);

        triggerCreateFleet(FleetSize.LARGER, FleetQuality.SMOD_2, Factions.PERSEAN,
                FleetTypes.TASK_FORCE, fikenhild.getStarSystem());
        triggerFleetSetCommander(armaa_starfallPeopleCMD.get(armaa_starfallPeopleCMD.ROLAND));
        triggerFleetSetFlagship("armaa_ps_tripoli_Standard");
        triggerAddShips(
                "armaa_ps_edessa_standard",
                "armaa_ps_damascus_standard",
                "armaa_ps_damascus_standard",
                "armaa_ps_damascus_standard",
                "armaa_harper_frig_standard",
                "armaa_harper_frig_standard",
                "armaa_harper_frig_standard");
        triggerSetFleetOfficers(OfficerNum.MORE, OfficerQuality.HIGHER);
        triggerFleetSetName("Starfall Order Live Exercise Fleet");
        triggerFleetSetNoFactionInName();
        triggerFleetSetTravelActionText("moving to the exercise area");
        triggerFleetSetPatrolActionText("running the exercise");
        triggerFleetNoAutoDespawn();
        triggerMakeNonHostile();
        triggerMakeFleetIgnoreOtherFleetsExceptPlayer();

        triggerPickLocationAroundEntity(fikenhild.getPrimaryEntity(), 0f, 150f, 400f);
        triggerSpawnFleetAtPickedLocation("$armaa_sfo_intakeSpawned", "$armaa_sfo_intakeRef");
        triggerSetFleetMissionRef("$armaa_sfo_ref");
        triggerOrderFleetAttackLocation(target.getPrimaryEntity());
        triggerFleetMakeImportant("$armaa_sfo_intake",
                Stage.GO_TO_TARGET, Stage.RAID, Stage.RAID_DONE);


        endTrigger();


        beginStageTrigger(Stage.RAID);

        triggerCreateFleet(FleetSize.LARGER, FleetQuality.DEFAULT, Factions.PIRATES,
                FleetTypes.TASK_FORCE, target.getStarSystem());
        //triggerAutoAdjustFleetStrengthModerate();
        triggerFleetSetName("Garrison Force");
        triggerMakeHostileAndAggressive();
        triggerFleetNoAutoDespawn();
        // so the intake piles in when the player engages it
        triggerMakeEveryoneJoinBattleAgainst();
        triggerSetStandardAggroPirateFlags();
        triggerPickLocationAroundEntity(target.getPrimaryEntity(), 800f);
        triggerSpawnFleetAtPickedLocation("$armaa_sfo_garrisonSpawned", null);
        triggerFleetAddDefeatTrigger("ArmaaStarfallGarrisonDefeated");
        triggerFleetMakeImportant("$armaa_sfo_garrison", Stage.RAID);

        endTrigger();

        return true;
    }

    @Override
    public void acceptImpl(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        // rules.csv drives the dialogue side off this string enum; the mission
        // owns the real state. Keep the two in step here and nowhere else.
        Global.getSector().getMemoryWithoutUpdate().set("$armaa_sfo_stage", "RAID_ACTIVE");
    }

    protected transient boolean interceptArmed = false;
    protected transient boolean recruitsAssigned = false;
    /**
     * Days the intake has been unfindable. It goes missing legitimately while
     * in hyperspace between Fikenhild and the target, so a single miss is not
     * evidence of anything - only a sustained absence is.
     */
    protected float intakeMissingDays = 0f;

    @Override
    protected void advanceImpl(float amount) {
        super.advanceImpl(amount);
        armInterceptIfNeeded();
        escortPlayerIfNeeded();
        rendezvousIfNeeded();
        routeAfterDecisionIfNeeded();
        escortToInvestigateIfNeeded();
        aftermathIfNeeded();
        assignRecruitsIfNeeded();
        checkIntakeLostIfNeeded(amount);
    }

    protected void escortPlayerIfNeeded() {
        if (currentStage != Stage.RAID) {
            return;
        }
        CampaignFleetAPI intake = findIntake();
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (intake == null || player == null) {
            return;
        }
        if (intake.getBattle() != null) {
            return;
        }
        CampaignFleetAIAPI ai = intake.getAI();
        if (ai == null) {
            return;
        }

        boolean lead = Global.getSector().getMemoryWithoutUpdate().getBoolean(LEAD_ATTACK_KEY);

        FleetAssignment assignment = lead
                ? FleetAssignment.ORBIT_AGGRESSIVE
                : ESCORT_ASSIGNMENT;
        SectorEntityToken orderTarget = lead ? findGarrison() : player;
        String actionText = lead ? "making their run" : "flying with your fleet";

        if (orderTarget == null) {
            return;   // garrison not spawned yet - retried next tick
        }
        if (!lead && intake.getContainingLocation() != player.getContainingLocation()) {
            return;
        }

        // already on station - let the assignment run rather than churning it
        FleetAssignmentDataAPI curr = ai.getCurrentAssignment();
        if (curr != null && curr.getAssignment() == assignment
                && curr.getTarget() == orderTarget) {
            return;
        }

        ai.removeFirstAssignmentIfItIs(assignment);
        ai.addAssignmentAtStart(assignment, orderTarget, ESCORT_DAYS, actionText, null);

        MemoryAPI mem = intake.getMemoryWithoutUpdate();
        Misc.setFlagWithReason(mem, MemFlags.FLEET_BUSY, ESCORT_REASON, true, ESCORT_DAYS);
        // On the lead branch this flag must come OFF, or they will ignore the
        // very fleet they were just told to attack.
        Misc.setFlagWithReason(mem, MemFlags.FLEET_IGNORES_OTHER_FLEETS, ESCORT_REASON,
                !lead, ESCORT_DAYS);

        if (DEBUG) {
            Global.getLogger(armaa_starfallMission.class).info(
                    "[starfall] intake assignment: " + (lead ? "leading the attack" : "escorting the player"));
        }
    }

    /**
     * Garrison is dead: the intake breaks off and closes on the player so
     * Roland can report in. This only gets them into comm range - the scene
     * itself is the BeginFleetEncounter/OpenCommLink pair in rules.csv, gated
     * on $armaa_sfo_raidDone and !$armaa_sfo_debriefOpened.
     *
     * Deliberately does nothing if the intake is gone. A wiped intake is a real
     * outcome here rather than a failure, and the Fikenhild menu option is the
     * backstop for it - so that option must accept RAID_DONE as well as DEBRIEF.
     */
    protected void rendezvousIfNeeded() {
        if (currentStage != Stage.RAID_DONE) {
            return;
        }
        if (Global.getSector().getMemoryWithoutUpdate().getBoolean(DEBRIEF_OPENED_KEY)) {
            return;
        }

        CampaignFleetAPI intake = findIntake();
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (intake == null || player == null) {
            return;
        }
        if (intake.getBattle() != null) {
            return;
        }
        if (intake.getContainingLocation() != player.getContainingLocation()) {
            return;   // player left the system - Fikenhild picks it up instead
        }
        CampaignFleetAIAPI ai = intake.getAI();
        if (ai == null) {
            return;
        }

        // already closing - let it run rather than re-issuing every frame
        FleetAssignmentDataAPI curr = ai.getCurrentAssignment();
        if (curr != null && curr.getAssignment() == FleetAssignment.INTERCEPT
                && curr.getTarget() == player) {
            return;
        }

        MemoryAPI mem = intake.getMemoryWithoutUpdate();
        // the escort flags are still live on ESCORT_DAYS. FLEET_BUSY has to come
        // off or the hail never opens; the ignore flag goes back on under its
        // own reason so they do not stop to fight their way across the system.
        Misc.setFlagWithReason(mem, MemFlags.FLEET_BUSY, ESCORT_REASON, false, 0f);
        Misc.setFlagWithReason(mem, MemFlags.FLEET_IGNORES_OTHER_FLEETS, ESCORT_REASON,
                false, 0f);
        Misc.setFlagWithReason(mem, MemFlags.FLEET_IGNORES_OTHER_FLEETS, RENDEZVOUS_REASON,
                true, RENDEZVOUS_DAYS);

        // clear whichever order the exercise left in front - on the lead branch
        // that is ORBIT_AGGRESSIVE on a garrison that no longer exists
        ai.removeFirstAssignmentIfItIs(ESCORT_ASSIGNMENT);
        ai.removeFirstAssignmentIfItIs(FleetAssignment.ORBIT_AGGRESSIVE);
        ai.removeFirstAssignmentIfItIs(FleetAssignment.INTERCEPT);
        ai.addAssignmentAtStart(FleetAssignment.INTERCEPT, player, RENDEZVOUS_DAYS,
                "moving to rendezvous", null);

        if (DEBUG) {
            Global.getLogger(armaa_starfallMission.class).info(
                    "[starfall] intake breaking off to rendezvous");
        }
    }

    public static final String PATH_KEY = "$armaa_sfo_path";

    /**
     * Set by armaa_starfallCMD once the player picks Deny/Accept/Solo at the
     * debrief. Runs once - after this the intake either heads home
     * (Deny/Solo) or stays on the player (Accept), and Accept/Solo raise the
     * Pather base so the map has somewhere to point.
     */
    protected transient boolean pathRouted = false;

    protected void routeAfterDecisionIfNeeded() {
        if (pathRouted) {
            return;
        }
        // No stage guard on purpose: choosePath() sets $armaa_sfo_investigating /
        // $armaa_sfo_readyToDebrief synchronously, so by the time this runs
        // the stage has usually already left DECISION (INVESTIGATE for
        // Accept/Solo, DEBRIEF for Deny). A DECISION-only guard meant this
        // never ran, no order was ever issued, and the log never showed
        // "routed decision". PATH_KEY is only ever written at the decision,
        // and pathRouted stops a repeat.
        String path = Global.getSector().getMemoryWithoutUpdate().getString(PATH_KEY);
        if (path == null) {
            return;
        }

        CampaignFleetAPI intake = findIntake();
        if (intake == null) {
            return;   // retried next tick - the fleet is always findable by now
        }
        CampaignFleetAIAPI ai = intake.getAI();
        if (ai == null) {
            return;
        }

        MemoryAPI mem = intake.getMemoryWithoutUpdate();
        // clear whatever rendezvousIfNeeded left running
        Misc.setFlagWithReason(mem, MemFlags.FLEET_IGNORES_OTHER_FLEETS, RENDEZVOUS_REASON, false, 0f);
        // removeFirstAssignmentIfItIs only removes a match for the exact
        // assignment named - useless here since the whole point is switching
        // AWAY from whatever is currently running (rendezvous's INTERCEPT,
        // most likely, but not guaranteed). clearAssignments() wipes the
        // queue outright so addAssignmentAtStart below actually takes over.
        ai.clearAssignments();
        // The intake was spawned via triggerOrderFleetAttackLocation(), which
        // attaches a TriggerFleetAssignmentAI script. That script's advance()
        // re-adds PATROL_SYSTEM around the raid target any time the fleet has
        // no current assignment - so clearing the queue alone just gets the
        // old order put straight back, and it resurfaces whenever ours ends
        // (e.g. when the player jumps and the orbit target vanishes). It has
        // to come off for anything below to stick.
        intake.removeScriptsOfClass(TriggerFleetAssignmentAI.class);
        // Two more leftovers from that same spawn. MissionFleetAutoDespawn is
        // re-attached by TriggerFleetAssignmentAI's constructor (so the
        // triggerFleetNoAutoDespawn() call at spawn never actually stuck), and
        // it sends the fleet back to its source market whenever it has no
        // assignment while sharing the player's location - which is what
        // "flies off the opposite way" mid-trip looks like once a travel
        // order lapses. And the action text the assignment script set
        // ("moving to the exercise area") is never cleared once the script is
        // gone, so it goes on describing an order that no longer exists.
        intake.removeScriptsOfClass(MissionFleetAutoDespawn.class);
        ai.setActionTextOverride(null);

        if ("ACCEPT".equals(path)) {
            // escortToInvestigateIfNeeded() takes over keeping them on the
            // player from here - this just gets the first order in immediately
            // rather than waiting a tick. Same mechanism as ESCORT_ASSIGNMENT
            // during RAID - see that constant's comment for why ORBIT_PASSIVE
            // and not FOLLOW/INTERCEPT.
            CampaignFleetAPI player = Global.getSector().getPlayerFleet();
            Misc.setFlagWithReason(mem, MemFlags.FLEET_BUSY, ESCORT_REASON, true, ESCORT_DAYS);
            Misc.setFlagWithReason(mem, MemFlags.FLEET_IGNORES_OTHER_FLEETS, ESCORT_REASON, true, ESCORT_DAYS);
            ai.addAssignmentAtStart(ESCORT_ASSIGNMENT, player, ESCORT_DAYS, "flying with your fleet", null);
        } else {
            // DENY and SOLO both send the intake home - the only difference
            // is whether the player is going with them
            ai.addAssignmentAtStart(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN,
                    fikenhild.getPrimaryEntity(), RENDEZVOUS_DAYS, "returning to Fikenhild", null);
        }

        pathRouted = true;
        if (DEBUG) {
            Global.getLogger(armaa_starfallMission.class).info("[starfall] routed decision: " + path);
        }
    }

    /**
     * Keeps the intake on the player for the whole trip out to the Pather
     * base on the Accept branch - same idea as escortPlayerIfNeeded() during
     * RAID, just gated on INVESTIGATE instead. Reuses ESCORT_ASSIGNMENT
     * rather than inventing a separate travel assignment: this codebase's own
     * comment on ESCORT_ASSIGNMENT already correctly cites Nexerelin's
     * FollowMeAbility as the source, and that class - exerelin.campaign.abilities.
     * FollowMeAbility, decompiled straight out of Nexerelin's own shipped jar -
     * confirms it: ORBIT_PASSIVE, re-issued periodically, with FLEET_BUSY and
     * FLEET_IGNORES_OTHER_FLEETS. No INTERCEPT (which makes the AI want to
     * close in and interact/hail, not just tag along), no FOLLOW, no pursuit
     * flags, no location hints - none of that is in Histidine's actual
     * implementation, and ORBIT_PASSIVE re-issued every tick is what already
     * carries Nexerelin's escort fleets across the sector in real play.
     */
    /**
     * ORBIT_PASSIVE (Nexerelin's FollowMeAbility, ESCORT_ASSIGNMENT above)
     * only knows how to hold station near a target that is already in the
     * same system - it has no notion of crossing a jump to reach one that
     * isn't, so once the player jumps, the intake is left behind and its
     * orbit target is simply gone; it falls back to idling/wandering. GO_TO_LOCATION
     * targeting the player directly turned out no better - same symptom,
     * follows to the jump point and no further.
     *
     * Both of those attempts targeted the PLAYER FLEET - a moving object -
     * for the cross-system leg. This mission's own intake spawn already
     * proves the actual fix: triggerOrderFleetAttackLocation() at GO_TO_TARGET
     * got this exact fleet from Fikenhild to a completely different target
     * system, and all it does under the hood (HubMissionWithTriggers ->
     * OrderFleetPatrolPointsAction -> TriggerFleetAssignmentAI.pickNext()) is
     * fleet.addAssignment(FleetAssignment.PATROL_SYSTEM, aStationaryEntity, ...).
     * The target there is a planet/station - never a moving fleet. Whatever
     * about jump-point routing breaks for a moving target, PATROL_SYSTEM
     * against a fixed anchor (the destination system's star) is the same
     * mechanism already proven to work for this fleet, so that is what is
     * used here for the "not in the same system yet" leg - then it hands off
     * to ORBIT_PASSIVE once sameSystem flips true and player itself is a
     * sensible, close-range target again.
     */
    protected void escortToInvestigateIfNeeded() {
        if (currentStage != Stage.INVESTIGATE || ambushOver) {
            return;
        }
        if (!"ACCEPT".equals(Global.getSector().getMemoryWithoutUpdate().getString(PATH_KEY))) {
            return;
        }
        CampaignFleetAPI intake = findIntake();
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (intake == null || player == null) {
            return;
        }
        if (intake.getBattle() != null) {
            return;
        }

        CampaignFleetAIAPI ai = intake.getAI();
        if (ai == null) {
            return;
        }

        // Protection while in transit. FLEET_IGNORES_OTHER_FLEETS only stops
        // the intake picking fights; hostile fleets can still decide to
        // engage IT, which is how it got killed en route. IGNORED_BY_OTHER_FLEETS
        // covers that side. Refreshed every tick (before the early-return
        // below), since these carry a duration and were previously only set
        // when an order was re-issued - they could lapse mid-trip. Lifted once
        // the intake is inside the Pather system, where fighting alongside the
        // player against the ambush is wanted.
        MemoryAPI protectMem = intake.getMemoryWithoutUpdate();
        boolean inDestination = patherStation != null
                && intake.getContainingLocation() == patherStation.getSystem();
        Misc.setFlagWithReason(protectMem, MemFlags.FLEET_IGNORES_OTHER_FLEETS,
                ESCORT_REASON, !inDestination, ESCORT_DAYS);
        Misc.setFlagWithReason(protectMem, MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS,
                ESCORT_REASON, !inDestination, ESCORT_DAYS);

        boolean sameSystem = intake.getContainingLocation() == player.getContainingLocation();
        FleetAssignment desired;
        SectorEntityToken target;
        String actionText;
        if (sameSystem) {
            desired = ESCORT_ASSIGNMENT;
            target = player;
            actionText = "flying with your fleet";
        } else {
            desired = FleetAssignment.PATROL_SYSTEM;
            // Aim at the destination itself - the Pather station, a fixed
            // anchor - rather than wherever the player currently is. The
            // player spends the trip in hyperspace, which is not a star
            // system, so "the player's system" has no anchor there and the
            // old fallback degenerated to targeting the moving player token.
            // The destination is always known and never moves.
            if (patherStation != null) {
                target = patherStation.getEntity();
            } else if (player.getContainingLocation() instanceof StarSystemAPI) {
                target = ((StarSystemAPI) player.getContainingLocation()).getCenter();
            } else {
                return;   // nothing stable to aim at yet - retried next tick
            }
            actionText = "following you to the Pather base";
        }

        FleetAssignmentDataAPI curr = ai.getCurrentAssignment();
        if (curr != null && curr.getAssignment() == desired && curr.getTarget() == target) {
            return;
        }

        // same reasoning as routeAfterDecisionIfNeeded - this only runs when
        // curr is NOT already the desired assignment (guarded above), so
        // whatever is queued needs to be cleared outright, not conditionally
        // removed. This also fires the instant sameSystem flips either way,
        // which is what actually swaps ORBIT_PASSIVE <-> PATROL_SYSTEM as the
        // player crosses a jump in either direction.
        ai.clearAssignments();
        // The cross-system leg gets a long duration. It used to share
        // ESCORT_DAYS (7), which is shorter than a real trip across the sector;
        // the order lapsed partway and the fleet was left with nothing to do.
        ai.addAssignmentAtStart(desired, target,
                sameSystem ? ESCORT_DAYS : TRAVEL_DAYS, actionText, null);

        // ignore/ignored-by flags are handled above, every tick
        Misc.setFlagWithReason(protectMem, MemFlags.FLEET_BUSY, ESCORT_REASON, true, ESCORT_DAYS);
    }

    /**
     * How far from the raid target the Pather base may be, in light years.
     * Tried in order; the player is at the target when the trail starts, so
     * that is the anchor rather than Fikenhild.
     */
    public static final float[] PATHER_RANGES_LY = {12f, 20f, 30f};

    /** Farthest an existing Path base may be from the raid target and still be used. */
    public static final float EXISTING_BASE_RANGE_LY = 30f;

    private static boolean isUsableBase(LuddicPathBaseIntel base) {
        return base != null && !base.isEnded() && !base.isEnding()
                && base.getEntity() != null && base.getSystem() != null && base.getMarket() != null;
    }

    /**
     * The nearest Path base that already exists within EXISTING_BASE_RANGE_LY of
     * the raid target, other than one in the target's own system. Using it
     * means the trail leads somewhere real that the player can already see on
     * the map; a new base is only raised when there is none.
     */
    protected LuddicPathBaseIntel findExistingPatherBase(StarSystemAPI near) {
        if (near == null) {
            return null;
        }
        LuddicPathBaseIntel best = null;
        float bestDist = EXISTING_BASE_RANGE_LY;
        for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel(LuddicPathBaseIntel.class)) {
            LuddicPathBaseIntel base = (LuddicPathBaseIntel) intel;
            if (!isUsableBase(base) || base.getSystem() == near) {
                continue;
            }
            float dist = Misc.getDistanceLY(near.getLocation(), base.getSystem().getLocation());
            if (dist <= bestDist) {
                bestDist = dist;
                best = base;
            }
        }
        if (DEBUG) {
            Global.getLogger(armaa_starfallMission.class).info("[starfall] existing Pather base near "
                    + near.getName() + ": " + (best == null ? "none within " + EXISTING_BASE_RANGE_LY + " LY"
                            : best.getSystem().getName() + " (" + Math.round(bestDist) + " LY)"));
        }
        return best;
    }

    /**
     * Picks the system for the Pather base, nearest first. For each range the
     * search is tried with the full filters (a quiet misc/ruins system, not
     * unsafe, not core, not a story's), then with the theme requirement
     * dropped, then with only "not core, not a story's, not the target's own
     * system". Closer beats stricter: an 11 LY system that fails the theme
     * filter is chosen over a matching one at 30 LY.
     *
     * Every attempt is logged, including the fallback, so a far pick can be
     * traced to what ruled the nearer systems out.
     */
    protected StarSystemAPI pickPatherSystem(StarSystemAPI near) {
        if (near != null) {
            for (float range : PATHER_RANGES_LY) {
                for (int level = 0; level <= 2; level++) {
                    resetSearch();
                    addPatherFilters(near, level);
                    requireSystemWithinRangeOf(near.getLocation(), range);
                    StarSystemAPI picked = pickSystem();
                    logPatherPick(near, range, level, picked);
                    if (picked != null) {
                        return picked;
                    }
                }
            }
        }
        resetSearch();
        addPatherFilters(near, 2);
        if (near != null) {
            preferSystemWithinRangeOf(near.getLocation(), PATHER_RANGES_LY[0]);
        }
        preferSystemOnFringeOfSector();
        StarSystemAPI picked = pickSystem();
        logPatherPick(near, -1f, -1, picked);
        return picked;
    }

    private void logPatherPick(StarSystemAPI near, float range, int level, StarSystemAPI picked) {
        if (!DEBUG) {
            return;
        }
        String result = picked == null ? "nothing"
                : picked.getName() + " (" + Math.round(Misc.getDistanceLY(near.getLocation(), picked.getLocation())) + " LY)";
        Global.getLogger(armaa_starfallMission.class).info("[starfall] Pather base search from "
                + (near == null ? "?" : near.getName()) + ": "
                + (range < 0 ? "fallback, unbounded" : "within " + range + " LY, filter level " + level)
                + " -> " + result);
    }

    /**
     * level 0: misc/ruins theme, not unsafe or core. 1: any theme, not unsafe or
     * core. 2: only not core. Always: not a story's system, not the target's own.
     */
    private void addPatherFilters(StarSystemAPI near, int level) {
        if (level <= 0) {
            requireSystemTags(ReqMode.ANY, Tags.THEME_MISC, Tags.THEME_MISC_SKIP, Tags.THEME_RUINS);
        }
        if (level <= 1) {
            requireSystemTags(ReqMode.NOT_ANY, Tags.THEME_UNSAFE, Tags.THEME_CORE);
        } else {
            requireSystemTags(ReqMode.NOT_ANY, Tags.THEME_CORE);
        }
        requireSystemNotAlreadyUsedForStory();
        preferSystemUnexplored();
        if (near != null) {
            requireSystemNot(near);   // the raid target's own system would make the base pointless to travel to
        }
    }

    /**
     * Raises the Pather base for Accept/Solo, same approach as
     * BornanewFilesFalseIdols.callAction("spawnPatherBase"): a real
     * LuddicPathBaseIntel rather than a hand-rolled market, so it behaves like
     * any other hidden Path base (docking, defenses, the works).
     *
     * Also drops a placeholder investigate marker alongside it - the actual
     * find belongs to whoever writes the scene, this just gives rules.csv
     * something to hang a dialog on. Falls back to the raid target's system
     * if the sector didn't have anything matching patherBaseSystem's search.
     */
    public void spawnPatherBase() {
        if (patherStation != null) {
            return;   // already raised - do not do this twice on a reload/re-entry
        }
        // create() picks the base, but a mission created before that logic existed
        // (or before an earlier base was destroyed) carries a stale choice. The
        // decision is when it matters, so look again: an existing base if there
        // is one, and a fresh search if the stored system is out of range.
        StarSystemAPI near = target == null ? null : target.getStarSystem();
        if (!isUsableBase(existingPatherBase)) {
            existingPatherBase = findExistingPatherBase(near);
        }
        if (existingPatherBase == null && near != null && patherBaseSystem != null
                && Misc.getDistanceLY(near.getLocation(), patherBaseSystem.getLocation())
                > PATHER_RANGES_LY[PATHER_RANGES_LY.length - 1]) {
            patherBaseSystem = pickPatherSystem(near);
        }

        StarSystemAPI system = patherBaseSystem;
        if (system == null) {
            system = target == null ? null : target.getStarSystem();
        }
        if (system == null) {
            if (DEBUG) {
                Global.getLogger(armaa_starfallMission.class).info(
                        "[starfall] spawnPatherBase: no system available");
            }
            return;
        }

        // The base chosen at create() may have been destroyed since. If so, raise
        // a new one in the fallback system rather than leaving the trail empty.
        if (isUsableBase(existingPatherBase)) {
            patherStation = existingPatherBase;
            system = patherStation.getSystem();
        } else {
            patherStation = new LuddicPathBaseIntel(system, Factions.LUDDIC_PATH);
            Global.getSector().addScript(patherStation);
        }
        SectorEntityToken stationEntity = patherStation.getEntity();
        stationEntity.getMemoryWithoutUpdate().set("$armaa_sfo_patherStationTarget", true);
        Misc.makeImportant(stationEntity, "$armaa_sfo_investigatePatherStation");

        // placeholder - a plain marker beside the station for the dialog rule
        // in rules.csv (OpenInteractionDialog, gated on the armaa_sfo_pathersite
        // tag). Swap the entity type/name/faction for real content later; the
        // tag and the rule are the part that has to stay stable. Faction
        // neutral, same as armaa_research_station/exsedol_station elsewhere in
        // this mod - an investigate-style marker should not need friendly
        // Path relations to approach.
        investigateSite = system.addCustomEntity("armaa_sfo_investigateSite",
                "Unidentified Installation", "station_side07", Factions.NEUTRAL);
        investigateSite.setInteractionImage("illustrations", "abandoned_station");
        investigateSite.setCircularOrbit(stationEntity, 0f, 200f, 60f);
        investigateSite.addTag("armaa_sfo_pathersite");
        Misc.makeImportant(investigateSite, "$armaa_sfo_investigateSite");

        Global.getSector().getMemoryWithoutUpdate().set("$armaa_sfo_investigating", true);
        Global.getSector().getMemoryWithoutUpdate().set("$armaa_sfo_patherSystemName", system.getName());

        if (DEBUG) {
            Global.getLogger(armaa_starfallMission.class).info(
                    "[starfall] Pather base raised in " + system.getName());
        }
    }

    /**
     * Called from armaa_starfallCMD once the placeholder investigate dialog
     * resolves. Fires exactly once (guarded by $armaa_sfo_ambushSpawned) -
     * whichever branch of that dialog the player picks, this runs the same
     * way, per the design: the ambush isn't a consequence of what's said,
     * it's a consequence of having come looking at all.
     *
     * Built the same way armaa_JeniusMission spawns its defenders -
     * FleetParamsV3 + FleetFactoryV3, rather than the trigger DSL, because
     * this fires on demand from a rule and not from a stage transition.
     */
    public void spawnAmbushFleet() {
        MemoryAPI global = Global.getSector().getMemoryWithoutUpdate();
        if (global.getBoolean("$armaa_sfo_ambushSpawned")) {
            return;
        }
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null || player.getContainingLocation() == null) {
            return;
        }

        float points = Math.max(120f, player.getFleetPoints() * 0.9f);
        FleetParamsV3 fparams = new FleetParamsV3(
                player.getLocationInHyperspace(),
                Factions.LUDDIC_PATH,
                null,
                FleetTypes.PATROL_LARGE,
                points, // combatPts
                0f, // freighterPts
                0f, // tankerPts
                0f, // transportPts
                0f, // linerPts
                0f, // utilityPts
                0.3f // qualityMod
        );
        CampaignFleetAPI ambush = FleetFactoryV3.createFleet(fparams);
        if (ambush == null || ambush.isEmpty()) {
            return;
        }
        FleetFactoryV3.addCommanderAndOfficersV2(ambush, fparams, new java.util.Random());
        ambush.setName("Order of Fire Interdiction Element");

        // spawnFleet takes a fixed (x, y) offset from the anchor, not a
        // random radius - a diagonal offset is enough to not spawn on top of
        // the player, and INTERCEPT below closes the rest of the distance.
        player.getContainingLocation().spawnFleet(player, 1200f, 900f, ambush);
        ambush.getFleetData().sort();
        ambush.setFaction(Factions.LUDDIC_PATH, false);

        // Misc.makeHostile() alone isn't what triggerMakeHostileAndAggressive()
        // does for the (working) garrison spawn - that sets MEMORY_KEY_MAKE_HOSTILE
        // and MEMORY_KEY_MAKE_AGGRESSIVE_ONE_BATTLE_ONLY too, which the fleet
        // AI actually reads to decide whether to press the attack rather than
        // disengage. makeHostileAndAggressive() is inherited straight from
        // HubMissionWithTriggers - same method the DSL helper calls.
        makeHostileAndAggressive(ambush, true);
        MemoryAPI ambushMem = ambush.getMemoryWithoutUpdate();
        // this is an ambush, not a patrol - it does not get to run away once
        // it starts losing, or the fleet can survive combat as a remnant that
        // retreats instead of being "defeated," and ArmaaStarfallAmbushDefeated
        // never fires.
        ambushMem.set(MemFlags.MEMORY_KEY_MAKE_PREVENT_DISENGAGE, true);
        ambushMem.set(MemFlags.MEMORY_KEY_MAKE_ALWAYS_PURSUE, true);
        ambushMem.set(MemFlags.MEMORY_KEY_ALLOW_LONG_PURSUIT, true);
        // read by armaa_EinhanderHaxPlugin to attach armaa_sfoAmbushBattlePlugin
        ambushMem.set("$inSfoAmbushBattle", true);

        ambush.addAssignment(FleetAssignment.INTERCEPT, player, 60f);
        Misc.addDefeatTrigger(ambush, "ArmaaStarfallAmbushDefeated");
        Misc.makeImportant(ambush, "$armaa_sfo_ambush");

        global.set("$armaa_sfo_ambushSpawned", true);

        if (DEBUG) {
            Global.getLogger(armaa_starfallMission.class).info(
                    "[starfall] ambush fleet spawned, fp=" + points);
        }
    }

    /**
     * Called off ArmaaStarfallAmbushDefeated. Sets both stage mechanisms this
     * mission tracks: $armaa_sfo_stage (the string armaa_starfallDebriefOption
     * gates on at Fikenhild) and $armaa_sfo_readyToDebrief (which drives this
     * mission's own Stage.INVESTIGATE -> Stage.DEBRIEF, read by the intel
     * panel/next-step text). See armaa_starfallCMD.choosePath for why both
     * exist and why neither may be set anywhere else.
     */
    public void ambushDefeated() {
        ambushDefeated(true);
    }

    /**
     * Switch for the Order of Fire interdiction ambush at the site. Off: the
     * site survey in rules.csv ends the investigation instead (see
     * armaa_starfallCMD.spawnAmbush). The ambush code is left intact; flip
     * this to bring it back.
     */
    public static final boolean AMBUSH_ENABLED = true;

    /**
     * @param hail whether the intake closes in for the post-ambush hail. False
     * when the investigation ends inside the site dialog, where Roland has
     * already said his piece on the channel.
     */
    public void ambushDefeated(boolean hail) {
        MemoryAPI global = Global.getSector().getMemoryWithoutUpdate();
        global.set("$armaa_sfo_stage", "DEBRIEF");
        global.set("$armaa_sfo_readyToDebrief", true);
        Misc.makeUnimportant(investigateSite, "$armaa_sfo_investigateSite");
        if (patherStation != null && patherStation.getEntity() != null) {
            Misc.makeUnimportant(patherStation.getEntity(), "$armaa_sfo_investigatePatherStation");
        }
        ambushOver = true;

        // An intake still in the player's system gets a closing hail before it
        // goes home. If it is dead or in another system there is no scene.
        CampaignFleetAPI intake = findIntake();
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (hail && intake != null && player != null
                && intake.getContainingLocation() == player.getContainingLocation()) {
            global.set(AFTERMATH_READY_KEY, true);
        } else {
            sendIntakeHome();
        }
    }

    public static final String AFTERMATH_READY_KEY = "$armaa_sfo_aftermathReady";
    public static final String AFTERMATH_DONE_KEY = "$armaa_sfo_aftermathDone";

    /**
     * Set once the ambush is beaten. The stage only leaves INVESTIGATE on the
     * next advance, so this stops escortToInvestigateIfNeeded() re-issuing
     * the escort order in the meantime.
     */
    protected boolean ambushOver = false;

    protected boolean intakeSentHome = false;

    /**
     * Post-ambush hail. Same mechanism as rendezvousIfNeeded(): INTERCEPT makes
     * the fleet hail on arrival, and the scene itself is the
     * BeginFleetEncounter/OpenCommLink pair in rules.csv, gated on
     * $armaa_sfo_aftermathReady and !$armaa_sfo_aftermathDone. The comm row sets
     * the done flag, and this then sends the intake home.
     *
     * If the player leaves the system before it catches up, the scene is
     * skipped and the intake just goes home.
     */
    protected void aftermathIfNeeded() {
        MemoryAPI global = Global.getSector().getMemoryWithoutUpdate();
        if (!global.getBoolean(AFTERMATH_READY_KEY) || intakeSentHome) {
            return;
        }
        if (global.getBoolean(AFTERMATH_DONE_KEY)) {
            sendIntakeHome();
            return;
        }

        CampaignFleetAPI intake = findIntake();
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (intake == null || player == null || intake.getAI() == null) {
            return;   // destroyed - nothing to do, and nothing to send home
        }
        if (intake.getBattle() != null) {
            return;
        }
        if (intake.getContainingLocation() != player.getContainingLocation()) {
            global.set(AFTERMATH_DONE_KEY, true);   // skipped
            sendIntakeHome();
            return;
        }

        CampaignFleetAIAPI ai = intake.getAI();
        FleetAssignmentDataAPI curr = ai.getCurrentAssignment();
        if (curr != null && curr.getAssignment() == FleetAssignment.INTERCEPT
                && curr.getTarget() == player) {
            return;
        }

        MemoryAPI mem = intake.getMemoryWithoutUpdate();
        // The hail needs the fleet un-busy and willing to talk. The debrief
        // comm row set ignorePlayerCommRequests, and it may not have expired.
        Misc.setFlagWithReason(mem, MemFlags.FLEET_BUSY, ESCORT_REASON, false, 0f);
        Misc.setFlagWithReason(mem, MemFlags.FLEET_IGNORES_OTHER_FLEETS, ESCORT_REASON, false, 0f);
        Misc.setFlagWithReason(mem, MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS, ESCORT_REASON, false, 0f);
        Misc.setFlagWithReason(mem, MemFlags.FLEET_IGNORES_OTHER_FLEETS, RENDEZVOUS_REASON,
                true, RENDEZVOUS_DAYS);
        mem.unset("$ignorePlayerCommRequests");
        ai.clearAssignments();
        ai.addAssignmentAtStart(FleetAssignment.INTERCEPT, player, RENDEZVOUS_DAYS,
                "moving to speak with you", null);
        if (DEBUG) {
            Global.getLogger(armaa_starfallMission.class).info(
                    "[starfall] intake closing for aftermath hail");
        }
    }

    /**
     * Ends the escort and sends the intake back to Fikenhild, using the same
     * order as the Deny/Solo paths. The escort's ORBIT_PASSIVE on the player
     * lasts ESCORT_DAYS unless it is replaced, so it has to be cleared here.
     * The escort protection flags come off too. No-op if the intake is dead.
     */
    protected void sendIntakeHome() {
        intakeSentHome = true;
        CampaignFleetAPI intake = findIntake();
        if (intake == null || intake.getAI() == null) {
            return;
        }
        MemoryAPI mem = intake.getMemoryWithoutUpdate();
        Misc.setFlagWithReason(mem, MemFlags.FLEET_BUSY, ESCORT_REASON, false, 0f);
        Misc.setFlagWithReason(mem, MemFlags.FLEET_IGNORES_OTHER_FLEETS, ESCORT_REASON, false, 0f);
        Misc.setFlagWithReason(mem, MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS, ESCORT_REASON, false, 0f);

        CampaignFleetAIAPI ai = intake.getAI();
        ai.clearAssignments();
        ai.setActionTextOverride(null);
        // Fikenhild is usually a different system, so this gets the long
        // duration rather than RENDEZVOUS_DAYS.
        ai.addAssignmentAtStart(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN,
                fikenhild.getPrimaryEntity(), TRAVEL_DAYS, "returning to Fikenhild", null);
        if (DEBUG) {
            Global.getLogger(armaa_starfallMission.class).info("[starfall] intake sent home after ambush");
        }
    }

    /**
     * The garrison carries $armaa_sfo_garrisonSpawned from its spawn trigger.
     */
    protected CampaignFleetAPI findGarrison() {
        if (target == null || target.getStarSystem() == null) {
            return null;
        }
        for (CampaignFleetAPI fleet : target.getStarSystem().getFleets()) {
            if (fleet.getMemoryWithoutUpdate().getBoolean("$armaa_sfo_garrisonSpawned")) {
                return fleet;
            }
        }
        return null;
    }

    /**
     * Attaches vanilla's intercept script to the intake, but only once the
     * player is actually in the target system. Doing it from the trigger block
     * would arm it at Fikenhild, where the player is inside range on spawn.
     */
    protected void armInterceptIfNeeded() {
        if (interceptArmed) {
            return;
        }
        if (currentStage != Stage.GO_TO_TARGET) {
            return;
        }
        if (target == null) {
            return;
        }
        if (Global.getSector().getCurrentLocation() != target.getStarSystem()) {
            return;
        }

        for (CampaignFleetAPI fleet : target.getStarSystem().getFleets()) {
            if (!fleet.getMemoryWithoutUpdate().getBoolean("$armaa_sfo_intakeSpawned")) {
                continue;
            }
            // no expiry - the hail gate must stay open until the briefing runs
            Global.getSector().getMemoryWithoutUpdate().set("$armaa_sfo_atTarget", true);

            if (fleet.hasScriptOfClass(MissionFleetInterceptPlayerIfNearby.class)) {
                interceptArmed = true;
                return;
            }
            List<Object> stages = Arrays.asList((Object) Stage.GO_TO_TARGET);
            //fleet.addScript(new MissionFleetInterceptPlayerIfNearby(
            //        fleet, this, false, 2000f, true, 30f, stages));
            //fleet.addScript(new MissionFleetStopPursuingPlayer(fleet, this, stages));
            interceptArmed = true;
            if (DEBUG) {
                Global.getLogger(armaa_starfallMission.class).info(
                        "[starfall] intercept armed on intake at " + target.getStarSystem().getName());
            }
            return;
        }
    }

    private static String shipNameFor(String personId) {
        if (armaa_starfallPeopleCMD.ROLAND.equals(personId)) {
            return "Oliphant";
        }
        if (armaa_starfallPeopleCMD.VOIT.equals(personId)) {
            return "Gareth";
        }
        if (armaa_starfallPeopleCMD.EHREN.equals(personId)) {
            return "Lyonesse";
        }
        if (armaa_starfallPeopleCMD.ALARD.equals(personId)) {
            return "Astolat";
        }
        return null;
    }

    /**
     * triggerSetFleetOfficers only does bulk generation, so the named recruits
     * are attached after the fleet exists. Walks the Damascus hulls and drops
     * them in - no per-recruit variant needed.
     */
    protected void assignRecruitsIfNeeded() {
        if (recruitsAssigned) {
            return;
        }
        CampaignFleetAPI intake = findIntake();
        if (intake == null) {
            return;
        }

        // Attached here because this is the first point the fleet is known to
        // exist, and it runs once. Fleet listeners persist with the fleet.
        Global.getSector().getMemoryWithoutUpdate()
                .set(armaa_starfallIntakeListener.DESTROYED_KEY, false);
        Global.getSector().getMemoryWithoutUpdate().set(AFTERMATH_READY_KEY, false);
        Global.getSector().getMemoryWithoutUpdate().set(AFTERMATH_DONE_KEY, false);
        intake.addEventListener(new armaa_starfallIntakeListener());

        int next = 0;
        for (FleetMemberAPI member : intake.getFleetData().getMembersListCopy()) {
            if (next >= RECRUITS.length) {
                break;
            }
            if (!RECRUIT_HULL.equals(member.getHullId())) {
                continue;
            }
            if (isRecruit(member.getCaptain())) {
                continue;
            }
            String id = RECRUITS[next];
            PersonAPI person = armaa_starfallPeopleCMD.get(id);
            if (person == null) {
                next++;
                continue;
            }
            member.setCaptain(person);
            String name = shipNameFor(id);
            if (name != null) {
                member.setShipName("SFO " + name);
            }
            next++;
        }

        recruitsAssigned = true;
        if (DEBUG) {
            Global.getLogger(armaa_starfallMission.class).info(
                    "[starfall] recruits assigned: " + next);
        }
    }

    /**
     * How long the intake can be unfindable before it is presumed destroyed.
     */
    public static float INTAKE_MISSING_LIMIT = 30f;

    /**
     * Fails the mission if the intake is destroyed on the way out or during the
     * exercise. Without this nothing advances and the player waits out the
     * 120-day time limit with no idea why.
     *
     * findIntake() returns null while the fleet is in hyperspace between
     * Fikenhild and the target, so a single miss means nothing - this only
     * fires after a sustained absence.
     */
    protected void checkIntakeLostIfNeeded(float amount) {
        if (currentStage != Stage.GO_TO_TARGET && currentStage != Stage.RAID) {
            intakeMissingDays = 0f;
            return;
        }
        // The listener's flag is definitive, so no waiting period. The timer
        // below stays as a fallback for a despawn the listener never saw.
        boolean destroyed = Global.getSector().getMemoryWithoutUpdate()
                .getBoolean(armaa_starfallIntakeListener.DESTROYED_KEY);
        if (!destroyed && findIntake() != null) {
            intakeMissingDays = 0f;
            return;
        }

        if (!destroyed) {
            intakeMissingDays += Global.getSector().getClock().convertToDays(amount);
            if (intakeMissingDays < INTAKE_MISSING_LIMIT) {
                return;
            }
        }

        Global.getSector().getMemoryWithoutUpdate().set("$armaa_sfo_intakeLost", true);
        applyIntakeLostConsequences();
        if (DEBUG) {
            Global.getLogger(armaa_starfallMission.class).info(
                    "[starfall] intake presumed destroyed - failing mission");
        }
        setCurrentStage(Stage.FAILED, null, null);
    }

    /**
     * Player-caused loss costs standing with the League; the King's office
     * pays nothing either way (payout only runs at the ending, which a failed
     * mission never reaches). Runs once, from checkIntakeLostIfNeeded.
     */
    protected void applyIntakeLostConsequences() {
        MemoryAPI m = Global.getSector().getMemoryWithoutUpdate();
        boolean byPlayer = m.getBoolean(armaa_starfallIntakeListener.KILLED_BY_PLAYER_KEY);
        if (byPlayer) {
            com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.CustomRepImpact impact =
                    new com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.CustomRepImpact();
            impact.delta = -0.15f;
            Global.getSector().adjustPlayerReputation(
                    new com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActionEnvelope(
                            com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions.CUSTOM,
                            impact, null, null, true),
                    Factions.PERSEAN);
            Global.getSector().getCampaignUI().addMessage(
                    "The Starfall Order's exercise fleet was destroyed in an engagement with your fleet. "
                    + "The King's office has cancelled your contract and the Persean League has taken note.",
                    Misc.getNegativeHighlightColor());
        } else {
            Global.getSector().getCampaignUI().addMessage(
                    "The Starfall Order's exercise fleet has been lost. The King's office has cancelled the exercise.",
                    Misc.getNegativeHighlightColor());
        }
    }

    /**
     * True if this officer is one of the three recruits.
     */
    public static boolean isRecruit(PersonAPI person) {
        if (person == null) {
            return false;
        }
        for (String id : RECRUITS) {
            if (id.equals(person.getId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a recruit is still flying. Call before the debrief.
     */
    public boolean isAlive(String personId) {
        CampaignFleetAPI intake = findIntake();
        if (intake == null) {
            return false;
        }
        for (FleetMemberAPI member : intake.getFleetData().getMembersListCopy()) {
            PersonAPI captain = member.getCaptain();
            if (captain != null && personId.equals(captain.getId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * The intake carries $armaa_sfo_intakeSpawned from the spawn trigger.
     */
    public CampaignFleetAPI findIntake() {
        CampaignFleetAPI found = scanFor(fikenhild);
        if (found == null) {
            found = scanFor(target);
        }
        // Accept sends the intake on to a third system it was never briefed
        // to be in - without this, escortToInvestigateIfNeeded() loses the
        // fleet for good the moment it arrives, and the escort order (good
        // for ESCORT_DAYS only) is never refreshed for the rest of the trip.
        if (found == null && patherStation != null) {
            found = scanFor(patherStation.getSystem());
        }
        // Hyperspace is not a star system, so none of the scans above ever
        // see a fleet that is mid-trip. That left escortToInvestigateIfNeeded()
        // blind for the whole crossing - it could not notice or repair the
        // order if anything went wrong, exactly when it mattered most.
        if (found == null) {
            found = scanFor(Global.getSector().getHyperspace());
        }
        return found;
    }

    private CampaignFleetAPI scanFor(MarketAPI market) {
        if (market == null || market.getStarSystem() == null) {
            return null;
        }
        return scanFor(market.getStarSystem());
    }

    private CampaignFleetAPI scanFor(LocationAPI system) {
        if (system == null) {
            return null;
        }
        for (CampaignFleetAPI fleet : system.getFleets()) {
            if (fleet.getMemoryWithoutUpdate().getBoolean("$armaa_sfo_intakeSpawned")) {
                return fleet;
            }
        }
        return null;
    }

    public MarketAPI getTarget() {
        return target;
    }

    /**
     * How close the intake has to be for its crew to weigh in on the
     * installation - roughly comm range, well inside sensor range.
     */
    public static final float NEARBY_RANGE = 2500f;

    /**
     * True only on the Accept branch (Solo sends the intake home) and only
     * while the intake is in the player's own location and close by. Read by
     * armaa_starfallCMD intakeNearby to gate the optional crew dialogue at the
     * Pather installation.
     */
    /**
     * Where the Order's fleet stands relative to the player, for the site
     * dialogue. "none": nobody is expected (Deny or Solo, the fleet went home).
     * "near": close enough to board with. "following": alive on the Accept
     * path but not yet in range, so the player can wait for it. "absent":
     * expected but gone (destroyed, or it turned back).
     */
    public String getIntakeStatus() {
        if (!"ACCEPT".equals(Global.getSector().getMemoryWithoutUpdate().getString(PATH_KEY))) {
            return "none";
        }
        if (isIntakeNearPlayer()) {
            return "near";
        }
        return findIntake() != null ? "following" : "absent";
    }

    /**
     * Skips the wait: moves the Order's fleet next to the player, into the
     * player's location if it is elsewhere, and clears its orders so the normal
     * escort logic takes over again. False if there is no fleet to bring.
     */
    public boolean summonIntake() {
        CampaignFleetAPI intake = findIntake();
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (intake == null || player == null || player.getContainingLocation() == null) {
            return false;
        }
        LocationAPI to = player.getContainingLocation();
        if (intake.getContainingLocation() != to) {
            if (intake.getContainingLocation() != null) {
                intake.getContainingLocation().removeEntity(intake);
            }
            to.addEntity(intake);
        }
        double angle = Math.random() * Math.PI * 2.0;
        intake.setLocation(player.getLocation().x + (float) Math.cos(angle) * 350f,
                player.getLocation().y + (float) Math.sin(angle) * 350f);
        intake.setVelocity(0f, 0f);
        intake.clearAssignments();
        return true;
    }

    public boolean isIntakeNearPlayer() {
        if (!"ACCEPT".equals(Global.getSector().getMemoryWithoutUpdate().getString(PATH_KEY))) {
            return false;
        }
        CampaignFleetAPI intake = findIntake();
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (intake == null || player == null) {
            return false;
        }
        if (intake.getContainingLocation() != player.getContainingLocation()) {
            return false;
        }
        return Misc.getDistance(intake, player) <= NEARBY_RANGE;
    }

    public SectorEntityToken getTargetEntity() {
        return target == null ? null : target.getPrimaryEntity();
    }

    /**
     * @param strict hard-require the story/core exclusions. Called again with
     * false if nothing in the sector passes, so the quest can still run on a
     * heavily-colonised or heavily-storied save.
     */
    protected MarketAPI findTarget(boolean strict) {
        resetSearch();

        requireMarketFaction(Factions.PIRATES);
        requireMarketNotInHyperspace();
        // requireMarketSizeAtMost(MAX_TARGET_SIZE);
        // NOT requireMarketNotHidden(): most vanilla pirate holdings are hidden
        // markets, and requiring visibility narrows the pool to roughly Kanta's
        // Den alone - which is excluded below. Preference only.

        // System-level tags do not catch a story-critical market sitting in an
        // otherwise ordinary system - Kanta's Den being the obvious case. There
        // is no requireMarketTags(), but search.marketReqs is public and
        // MarketRequirement is an interface, so the check goes in directly.
        search.marketReqs.add(new MarketRequirement() {
            public boolean marketMatchesRequirement(MarketAPI market) {
                //if (market.hasTag(Tags.STORY_CRITICAL)) {
                //    return false;
                //}
                FactionAPI owner = Misc.getClaimingFaction(market.getPrimaryEntity().getStarSystem().getCenter());
                //if (owner != null && !owner.getId().equals("luddic_path") && !owner.getId().equals("pirates") && owner.isHostileTo("persean_league")) {
                //    return false;
                //}
                if (market.hasTag(Tags.NOT_RANDOM_MISSION_TARGET)) {
                    return false;
                }
                if (market.getMemoryWithoutUpdate().getBoolean(MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE)) {
                    return false;
                }
                // belt and braces: never send them at the big named holdings
                for (String id : PROTECTED_MARKETS) {
                    if (id.equals(market.getId())) {
                        return false;
                    }
                }
                return true;
            }
        });

       // if (strict) {
       //     requireSystemNotAlreadyUsedForStory();
       //     requireSystemTags(ReqMode.NOT_ANY, Tags.THEME_CORE, Tags.THEME_UNSAFE,
       //             Tags.THEME_SPECIAL, Tags.NOT_RANDOM_MISSION_TARGET);
        // else {
            preferSystemTags(ReqMode.NOT_ANY, Tags.THEME_CORE, Tags.THEME_UNSAFE,
                    Tags.THEME_SPECIAL, Tags.NOT_RANDOM_MISSION_TARGET);
        //}

        preferMarketSizeAtLeast(3);
        preferMarketNotHidden();
        preferMarketInDirectionOfOtherMissions();

        // the intake flies out from Fikenhild, so don't send them across the sector
        if (fikenhild != null) {
            preferSystemWithinRangeOf(fikenhild.getLocationInHyperspace(), 10f);
        }

        MarketAPI picked = pickMarket();
        if (DEBUG) {
            if (picked == null) {
                Global.getLogger(armaa_starfallMission.class).info(
                        "[starfall] no target found, strict=" + strict);
            } else {
                Global.getLogger(armaa_starfallMission.class).info(
                        "[starfall] target=" + picked.getId() + " size=" + picked.getSize()
                        + " hidden=" + picked.isHidden()
                        + " system=" + picked.getStarSystem().getName() + " strict=" + strict);
            }
        }
        return picked;
    }

    // ------------------------------------------------------------------
    // intel
    // ------------------------------------------------------------------
    @Override
    public void addDescriptionForNonEndStage(TooltipMakerAPI info, float width, float height) {
        float opad = 10f;
        Stage stage = (Stage) currentStage;
        if (stage == Stage.GO_TO_TARGET) {
            info.addPara("The Starfall Order's intake is running a live exercise against a pirate "
                    + "holding at %s, in the %s. Ser Roland has asked you to be present.",
                    opad, Misc.getHighlightColor(),
                    target.getName(), target.getStarSystem().getNameWithLowercaseType());
            info.addPara("The target was selected by the King's office rather than by the order.",
                    opad);
        }
        if (stage == Stage.RAID_DONE) {
            info.addPara("The garrison is destroyed. The Starfall Order's fleet is breaking "
                    + "off the exercise and closing on your position.", opad);
        }
        if (stage == Stage.DECISION) {
            info.addPara("Roland's fleet has closed the distance and hailed you. Something "
                    + "about the raid does not sit right with him, and he wants your call "
                    + "on how to proceed.", opad);
        }
        if (stage == Stage.DEBRIEF) {
            info.addPara("The exercise is over. Ser Roland has asked you to meet him at %s "
                    + "once the segment has gone out.", opad, Misc.getHighlightColor(),
                    fikenhild.getName());
        }
        if (stage == Stage.INVESTIGATE) {
            String systemName = patherStation != null
                    ? patherStation.getSystem().getName()
                    : Global.getSector().getMemoryWithoutUpdate().getString("$armaa_sfo_patherSystemName");
            info.addPara("A lead on the Order of Fire points to %s. Whatever it is, someone "
                    + "is prepared to kill to keep it hidden.", opad, Misc.getHighlightColor(),
                    systemName);
        }
    }

    @Override
    public boolean addNextStepText(TooltipMakerAPI info, java.awt.Color tc, float pad) {
        Stage stage = (Stage) currentStage;
        if (stage == Stage.GO_TO_TARGET) {
            info.addPara("Join the intake at %s", pad, tc,
                    Misc.getHighlightColor(), target.getName());
            return true;
        }
        if (stage == Stage.RAID_DONE) {
            info.addPara("Wait for the Starfall Order's fleet", pad, tc,
                    Misc.getHighlightColor());
            return true;
        }
        if (stage == Stage.DECISION) {
            info.addPara("Hear Roland out and decide how to proceed", pad, tc,
                    Misc.getHighlightColor());
            return true;
        }
        if (stage == Stage.DEBRIEF) {
            info.addPara("Return to %s", pad, tc,
                    Misc.getHighlightColor(), fikenhild.getName());
            return true;
        }
        if (stage == Stage.INVESTIGATE) {
            String systemName = patherStation != null
                    ? patherStation.getSystem().getName()
                    : Global.getSector().getMemoryWithoutUpdate().getString("$armaa_sfo_patherSystemName");
            info.addPara("Investigate the Order of Fire's lead in %s", pad, tc,
                    Misc.getHighlightColor(), systemName);
            return true;
        }
        return false;
    }

    @Override
    public String getBaseName() {
        return "Starfall Exercise";
    }

    /**
     * Same idea as BornanewFilesFalseIdols.getMapLocation() - once the Pather
     * base exists, that is what "next step" should point at rather than
     * wherever makeImportant() last pinned (which does not know about an
     * entity created after create() ran).
     */
    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        if (currentStage == Stage.INVESTIGATE && patherStation != null) {
            return patherStation.getEntity();
        }
        return super.getMapLocation(map);
    }

    /**
     * Set before create() to use an already-chosen target instead of searching.
     */
    public void setPresetTarget(MarketAPI market) {
        this.presetTarget = market;
    }

    /**
     * Picks a target without creating anything. Lets the dialogue show Roland's
     * proposal and re-roll it before the player commits.
     *
     * @param avoid the market to steer away from, or null
     */
    public static MarketAPI proposeTarget(MarketAPI avoid) {
        armaa_starfallMission tmp = new armaa_starfallMission();
        tmp.setGenRandom(Misc.random);
        tmp.fikenhild = Global.getSector().getEconomy().getMarket("fikenhild");
        if (tmp.fikenhild == null) {
            return null;
        }
        tmp.avoidTarget = avoid;
        MarketAPI m = tmp.findTarget(true);
        if (m == null) {
            m = tmp.findTarget(false);
        }
        return m;
    }
}