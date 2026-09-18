package data.scripts.campaign.missions;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.ai.CampaignFleetAIAPI;
import com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithSearch;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithSearch.MarketRequirement;
import com.fs.starfarer.api.impl.campaign.missions.hub.MissionFleetInterceptPlayerIfNearby;
import com.fs.starfarer.api.impl.campaign.missions.hub.ReqMode;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import data.campaign.rulecmd.armaa_starfallPeopleCMD;

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
        DEBRIEF, // Roland has reported in; report back to Fikenhild once it has aired
        COMPLETED,
        FAILED,
    }

    protected MarketAPI target;
    protected MarketAPI fikenhild;
    protected PersonAPI giver;
    protected MarketAPI presetTarget;
    protected MarketAPI avoidTarget;

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

        // ---- stages ----
        setStartingStage(Stage.GO_TO_TARGET);
        addSuccessStages(Stage.COMPLETED);
        addFailureStages(Stage.FAILED);

        makeImportant(target, "$armaa_sfo_target",
                Stage.GO_TO_TARGET, Stage.RAID, Stage.RAID_DONE);
        // the marker moves home for the after-action - DEBRIEF only, so it does
        // not appear while Roland is still crossing the system to reach you
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
        connectWithGlobalFlag(Stage.RAID_DONE, Stage.DEBRIEF, DEBRIEF_OPENED_KEY);
        connectWithGlobalFlag(Stage.DEBRIEF, Stage.COMPLETED, "$armaa_sfo_debriefed");

        setNoAbandon();
        setTimeLimit(Stage.FAILED, MISSION_DAYS, null);
        setRepFactionChangesNone();   // the Household pays; nobody's reputation moves

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
                "armaa_ps_damascus_standard");
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

        triggerCreateFleet(FleetSize.LARGER, FleetQuality.DEFAULT, Factions.LUDDIC_PATH,
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
        if (findIntake() != null) {
            intakeMissingDays = 0f;
            return;
        }

        intakeMissingDays += Global.getSector().getClock().convertToDays(amount);
        if (intakeMissingDays < INTAKE_MISSING_LIMIT) {
            return;
        }

        Global.getSector().getMemoryWithoutUpdate().set("$armaa_sfo_intakeLost", true);
        if (DEBUG) {
            Global.getLogger(armaa_starfallMission.class).info(
                    "[starfall] intake presumed destroyed - failing mission");
        }
        setCurrentStage(Stage.FAILED, null, null);
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
        return found;
    }

    private CampaignFleetAPI scanFor(MarketAPI market) {
        if (market == null || market.getStarSystem() == null) {
            return null;
        }
        for (CampaignFleetAPI fleet : market.getStarSystem().getFleets()) {
            if (fleet.getMemoryWithoutUpdate().getBoolean("$armaa_sfo_intakeSpawned")) {
                return fleet;
            }
        }
        return null;
    }

    public MarketAPI getTarget() {
        return target;
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
            info.addPara("The target was selected by the Household rather than by the order.",
                    opad);
        }
        if (stage == Stage.RAID_DONE) {
            info.addPara("The garrison is destroyed. The Starfall Order's fleet is breaking "
                    + "off the exercise and closing on your position.", opad);
        }
        if (stage == Stage.DEBRIEF) {
            info.addPara("The exercise is over. Ser Roland has asked you to meet him at %s "
                    + "once the segment has gone out.", opad, Misc.getHighlightColor(),
                    fikenhild.getName());
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
        if (stage == Stage.DEBRIEF) {
            info.addPara("Return to %s", pad, tc,
                    Misc.getHighlightColor(), fikenhild.getName());
            return true;
        }
        return false;
    }

    @Override
    public String getBaseName() {
        return "Starfall Exercise";
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