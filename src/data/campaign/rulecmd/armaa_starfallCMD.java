package data.campaign.rulecmd;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;

import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;

import data.scripts.campaign.armaa_starfallBattleListener;
import data.scripts.campaign.missions.armaa_starfallMission;

public class armaa_starfallCMD extends BaseCommandPlugin {

    public static final String STAGE_KEY = "$armaa_sfo_stage";
    public static final String RAID_DONE_KEY = "$armaa_sfo_raidDone";
    public static final int MIN_LEVEL = 8;

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
            List<Token> params, Map<String, MemoryAPI> memoryMap) {
        if (params.isEmpty()) {
            return false;
        }
        String command = params.get(0).getString(memoryMap);
        if (command == null) {
            return false;
        }

        if ("isScouted".equals(command)) {
            return isScouted();
        }
        if ("proposeTarget".equals(command)) {
            return proposeTarget(memoryMap, false);
        }
        if ("rerollTarget".equals(command)) {
            return proposeTarget(memoryMap, true);
        }
        if ("startMission".equals(command)) {
            return startMission(dialog, memoryMap);
        }
        //if ("atTarget".equals(command)) return atTarget();
        if ("briefingDone".equals(command)) {
            return briefingDone(false);
        }
        if ("briefingDoneLead".equals(command)) {
            return briefingDoneLead();
        }
        if ("raidDone".equals(command)) {
            return raidDone();
        }
        if ("tallyLosses".equals(command)) {
            return tallyLosses();
        }
        if ("score".equals(command)) {
            return score();
        }
        if ("watchBattle".equals(command)) {
            return watchBattle(true);
        }
        if ("stopWatchingBattle".equals(command)) {
            return watchBattle(false);
        }
        if ("targetName".equals(command)) {
            return targetName(memoryMap);
        }

        return false;
    }

    // ------------------------------------------------------------------
    // fame gate - "somebody in the Household saw you in a segment"
    // ------------------------------------------------------------------
    private boolean isScouted() {
        MemoryAPI m = Global.getSector().getMemoryWithoutUpdate();

        // any one of these is enough to have been noticed
        if (m.getBoolean("$player.defeatedHegemony")) {
            return true;
        }
        if (m.getBoolean("$player.counterRaidedTriTach")) {
            return true;
        }
        if (m.getBoolean("$global.gaATG_missionCompleted")) {
            return true;
        }
        if (m.getBoolean("$global.foundOneslaught")) {
            return true;
        }

        // otherwise fall back on raw renown
        return Global.getSector().getPlayerStats().getLevel() >= MIN_LEVEL;
    }

    // ------------------------------------------------------------------
    // create + accept in one step. There is no bar event or contact here,
    // so this does what BeginMission/AcceptMission do together.
    // ------------------------------------------------------------------
    public static final String PROPOSED_KEY = "$armaa_sfo_proposedTarget";

    /**
     * Picks a target and parks its id in global memory so the dialogue can name
     * it before anything is committed. Nothing is created here.
     *
     * @param reroll if true, steer away from whatever is currently proposed -
     * so "find me something else" actually finds something else.
     */
    private boolean proposeTarget(Map<String, MemoryAPI> memoryMap, boolean reroll) {
        MemoryAPI global = Global.getSector().getMemoryWithoutUpdate();

        MarketAPI avoid = null;
        if (reroll) {
            avoid = getProposed();
        }

        MarketAPI picked = armaa_starfallMission.proposeTarget(avoid);
        if (picked == null) {
            // nothing else in the sector qualifies - keep what we had rather
            // than dropping the player into a dead end
            return getProposed() != null;
        }

        global.set(PROPOSED_KEY, picked.getId());
        global.set(PROPOSED_KEY + "_name", picked.getName());
        writeTargetTokens(memoryMap, picked);
        return true;
    }

    private static MarketAPI getProposed() {
        String id = Global.getSector().getMemoryWithoutUpdate().getString(PROPOSED_KEY);
        if (id == null) {
            return null;
        }
        return Global.getSector().getEconomy().getMarket(id);
    }

    private void writeTargetTokens(Map<String, MemoryAPI> memoryMap, MarketAPI market) {
        MemoryAPI local = memoryMap.get("local");
        if (local == null) {
            return;
        }
        local.set("$armaa_sfo_targetName", market.getName(), 0);
        local.set("$armaa_sfo_targetSystem",
                market.getStarSystem().getNameWithLowercaseType(), 0);
        local.set("$armaa_sfo_targetSize", market.getSize(), 0);
        local.set("$armaa_sfo_targetFaction", market.getFaction().getDisplayName(), 0);
    }

    /**
     * Commits to whatever proposeTarget last picked. create() skips its own
     * search because the target is preset.
     */
    private boolean startMission(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) {
            return false;
        }

        MarketAPI createdAt = null;
        if (dialog.getInteractionTarget() != null) {
            createdAt = dialog.getInteractionTarget().getMarket();
        }
        if (createdAt == null) {
            return false;
        }

        PersonAPI giver = dialog.getInteractionTarget().getActivePerson();
        if (giver == null) {
            return false;
        }

        armaa_starfallMission mission = new armaa_starfallMission();
        mission.setGenRandom(Misc.random);
        mission.setGiver(giver);
        mission.setPresetTarget(getProposed());   // null is fine - it searches

        if (!mission.create(createdAt, false)) {
            return false;
        }

        mission.accept(dialog, memoryMap);
        Global.getSector().getMemoryWithoutUpdate().unset(PROPOSED_KEY);
        return true;
    }

    // ------------------------------------------------------------------
    // called at the end of the intake's briefing at the target. Advances the
    // mission to RAID, which is what spawns the garrison - so the player has
    // always spoken to the intake before anything hostile exists.
    // ------------------------------------------------------------------
    private boolean briefingDoneLead() {
        return briefingDone(true);
    }

    /**
     * @param lead true if the player told the intake to make the run itself.
     * Sets $armaa_sfo_leadAttack, which escortPlayerIfNeeded() reads to choose
     * between orbiting the player and going at the garrison. Also banks the
     * matching counter.
     */
    private boolean briefingDone(boolean lead) {
        MemoryAPI m = Global.getSector().getMemoryWithoutUpdate();
        m.set("$armaa_sfo_briefed", true);
        m.set(armaa_starfallMission.LEAD_ATTACK_KEY, lead);
        // no counter maths here - every decision just records itself, and
        // score() reads the lot in one pass. Keeps the weighting in one place
        // and lets it be recomputed.
        m.set(STAGE_KEY, "RAID_ACTIVE");
        return true;
    }

    // ------------------------------------------------------------------
    // called from the post-battle dialogue
    // ------------------------------------------------------------------
    private boolean raidDone() {
        MemoryAPI m = Global.getSector().getMemoryWithoutUpdate();
        m.set(RAID_DONE_KEY, true);       // connectWithGlobalFlag watches this
        m.set(STAGE_KEY, "RAID_DONE");    // rules.csv gates on this
        return true;
    }

    // ------------------------------------------------------------------
    // battle recording
    // ------------------------------------------------------------------
    /**
     * Adds or removes the engagement listener. Transient, so it is never
     * serialised - call watchBattle when the raid begins, and the listener
     * removes itself once it has captured a result. stopWatchingBattle is the
     * manual escape hatch for aborts.
     */
    private boolean watchBattle(boolean on) {
        ListenerManagerAPI lm = Global.getSector().getListenerManager();

        // never stack them - a duplicate would double-write the flags
        for (armaa_starfallBattleListener existing
                : lm.getListeners(armaa_starfallBattleListener.class)) {
            lm.removeListener(existing);
        }
        if (on) {
            lm.addListener(new armaa_starfallBattleListener(), true);
        }
        return true;
    }

    // ------------------------------------------------------------------
    // scoring
    // ------------------------------------------------------------------
    /**
     * Reads every recorded decision and outcome and produces the two counters.
     *
     * Run it at the debrief, after tallyLosses. Nothing else should touch
     * $armaa_sfo_ratings or $armaa_sfo_favor - one pass means the weighting is
     * all in this table and can be retuned without hunting through rules.csv.
     *
     * The axis: damage and losses to the INTAKE raise ratings and cost favor,
     * because that is what Alard said and what Roland fears. Damage to the
     * player's own ships scores nothing either way, which is what stops the
     * player farming ratings by flying badly.
     *
     * Also sets, for the debrief text: $armaa_sfo_ratingsBand /
     * $armaa_sfo_favorBand none / low / high
     */
    private boolean score() {
        MemoryAPI m = Global.getSector().getMemoryWithoutUpdate();
        if (m.getBoolean("$armaa_sfo_scored")) {
            return true;                      // already banked this raid
        }
        int ratings = 0;
        int favor = 0;

        // ---- who made the run ----
        if (m.getBoolean(armaa_starfallMission.LEAD_ATTACK_KEY)) {
            ratings += 1;
        } else {
            favor += 1;
        }

        // ---- how much of the line was yours ----
        if (m.getBoolean("$armaa_sfo_battleSeen")) {
            int share = m.getInt("$armaa_sfo_playerShareOfForce");
            if (share < 25) {
                ratings += 3;
            } else if (share < 35) {
                ratings += 2;
            } else if (share < 50) {
                ratings += 1;
            }
        }

        int hull = m.contains("$armaa_sfo_intakeHullPct")
                ? m.getInt("$armaa_sfo_intakeHullPct") : 0;
                    int losses = m.getInt("$armaa_sfo_losses");
        if (m.getBoolean("$armaa_sfo_battleSeen") && m.contains("$armaa_sfo_intakeHullPct")) {
            if (hull < 30) {
                ratings += 2;
            } else if (hull < 60) {
                ratings += 1;
            } else if (hull < 80) {
                favor += 1;
            } else {
                favor += 2;
            }
}
            // ---- losses ----
            //int losses = m.getInt("$armaa_sfo_losses");
            if(losses == 0)
                favor +=2;
            else if (losses == 1) {
                ratings += 1;
            } else {
                ratings += 2;
            }
        
        int currRatings = m.contains("$armaa_sfo_ratings") ? m.getInt("$armaa_sfo_ratings") : 0;
        int currFavor = m.contains("$armaa_sfo_favor") ? m.getInt("$armaa_sfo_favor") : 0;
        int totalRatings = (int) (currRatings + ratings);
        int totalFavor = (int) (currFavor + favor);

        m.set("$armaa_sfo_ratings", totalRatings);
        m.set("$armaa_sfo_favor", totalFavor);
        m.set("$armaa_sfo_ratingsBand", band(totalRatings));
        m.set("$armaa_sfo_favorBand", band(totalFavor));

        Global.getLogger(armaa_starfallCMD.class).info(
                "[starfall] score: ratings=" + ratings + " (" + band(ratings) + ")"
                + " favor=" + favor + " (" + band(favor) + ")"
                + " | share=" + m.getInt("$armaa_sfo_playerShareOfForce")
                + " hull=" + hull + " losses=" + losses);
        return true;
    }

    private static String band(int value) {
        if (value >= 7) {
            return "High";
        }
        if (value >= 4) {
            return "Medium";
        }
        if (value <= 3) {
            return "Low";
        }
        return "Abysmal";
    }

    // ------------------------------------------------------------------
    // who came home
    // ------------------------------------------------------------------
    /**
     * Snapshots the intake's state into globals so the debrief can branch on
     * it.
     *
     * Must run on the defeat trigger, NOT on the debrief node: it reads the
     * live fleet, and findIntake() only scans Fikenhild's and the target's
     * systems. Once the player leaves, the answer is gone.
     *
     * Sets, for each of voit / ehrenmark / alard: $armaa_sfo_alive_<id> their
     * hull came home plus: $armaa_sfo_losses how many did not $armaa_sfo_wiped
     * the whole intake is gone
     */
    private boolean tallyLosses() {
        MemoryAPI m = Global.getSector().getMemoryWithoutUpdate();

        Object ref = m.get("$armaa_sfo_ref");
        if (!(ref instanceof armaa_starfallMission)) {
            // without this the alive flags stay unset, which the resolution
            // nodes read as three casualties - so say so rather than fail quiet
            Global.getLogger(armaa_starfallCMD.class).warn(
                    "[starfall] tallyLosses: no mission at $armaa_sfo_ref");
            return false;
        }
        armaa_starfallMission mission = (armaa_starfallMission) ref;

        boolean wiped = mission.findIntake() == null;
        m.set("$armaa_sfo_wiped", wiped);

        int losses = 0;
        for (String personId : armaa_starfallMission.RECRUITS) {
            boolean alive = !wiped && mission.isAlive(personId);
            // personId is armaa_sfo_voit -> flag is $armaa_sfo_alive_voit
            String shortId = personId.substring(personId.lastIndexOf('_') + 1);
            m.set("$armaa_sfo_alive_" + shortId, alive);
            if (!alive) {
                losses++;
            }
        }
        m.set("$armaa_sfo_losses", losses);
        m.set("$armaa_sfo_anyLosses", losses > 0);

        Global.getLogger(armaa_starfallCMD.class).info(
                "[starfall] tallyLosses: wiped=" + wiped + " losses=" + losses);
        return true;
    }

    // ------------------------------------------------------------------
    // expose the target to dialogue text
    // ------------------------------------------------------------------
    private boolean targetName(Map<String, MemoryAPI> memoryMap) {
        Object ref = Global.getSector().getMemoryWithoutUpdate().get("$armaa_sfo_ref");
        if (!(ref instanceof armaa_starfallMission)) {
            return false;
        }

        armaa_starfallMission mission = (armaa_starfallMission) ref;
        if (mission.getTarget() == null) {
            return false;
        }

        MemoryAPI local = memoryMap.get("local");
        if (local == null) {
            return false;
        }

        local.set("$armaa_sfo_targetName", mission.getTarget().getName(), 0);
        local.set("$armaa_sfo_targetSystem",
                mission.getTarget().getStarSystem().getNameWithLowercaseType(), 0);
        return true;
    }
}
