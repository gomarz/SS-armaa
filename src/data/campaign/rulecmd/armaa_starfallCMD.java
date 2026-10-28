package data.campaign.rulecmd;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import java.util.Random;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.CustomRepImpact;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActionEnvelope;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions;
import com.fs.starfarer.api.impl.campaign.DModManager;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;

import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;

import data.scripts.campaign.armaa_starfallBattleListener;
import data.scripts.campaign.armaa_starfallRoyalties;
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
        if ("payout".equals(command)) {
            return payout(dialog);
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
        if ("mercenary".equals(command)) {
            return mercenary();
        }
        if ("chooseDeny".equals(command)) {
            return choosePath("DENY");
        }
        if ("chooseAccept".equals(command)) {
            return choosePath("ACCEPT");
        }
        if ("chooseSolo".equals(command)) {
            return choosePath("SOLO");
        }
        if ("spawnAmbush".equals(command)) {
            return spawnAmbush();
        }
        if ("ambushDefeated".equals(command)) {
            return ambushDefeated();
        }
        if ("summonIntake".equals(command)) {
            Object ref = Global.getSector().getMemoryWithoutUpdate().get("$armaa_sfo_ref");
            if (ref instanceof armaa_starfallMission) {
                ((armaa_starfallMission) ref).summonIntake();
            }
            return intakeNearby();
        }
        if ("intakeNearby".equals(command)) {
            return intakeNearby();
        }
        if ("destroyInstallation".equals(command)) {
            return destroyInstallation(dialog);
        }
        if ("cleanup".equals(command)) {
            return cleanup();
        }
        if ("startStealth".equals(command)) {
            return startStealth(dialog);
        }

        return false;
    }

    // ------------------------------------------------------------------
    // fame gate - "somebody in the King's office saw you in a segment"
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
            // Already banked this raid, but choices made since (the site
            // survey, for one) move the counters, and the rules read them
            // live. Refresh the bands so the report matches the text.
            m.set("$armaa_sfo_ratingsBand", band(m.getInt("$armaa_sfo_ratings")));
            m.set("$armaa_sfo_favorBand", band(m.getInt("$armaa_sfo_favor")));
            return true;
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
            if (!m.getBoolean("$armaa_sfo_battleSeen")) {
                // The player took the garrison before the Order was in the
                // battle. Nothing of theirs was at risk, so the "no losses"
                // bonus does not apply; the show gets little footage (-1
                // ratings) and the recruits are kept out of it (+1 favor).
                ratings -= 1;
                favor += 1;
            } else if(losses == 0)
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
        m.set("$armaa_sfo_scored", true);

        Global.getLogger(armaa_starfallCMD.class).info(
                "[starfall] score: ratings=" + ratings + " (" + band(ratings) + ")"
                + " favor=" + favor + " (" + band(favor) + ")"
                + " | share=" + m.getInt("$armaa_sfo_playerShareOfForce")
                + " hull=" + hull + " losses=" + losses);
        return true;
    }

    // ------------------------------------------------------------------
    // the installation, and clearing the quest's memory afterwards
    // ------------------------------------------------------------------
    public static final String INSTALLATION_FATE_KEY = "$armaa_sfo_installationFate";

    /**
     * The player has the site brought down. Removes the entity being talked
     * to (the installation, not the Pather base) and records the outcome for
     * whatever quest picks this up later.
     */
    private boolean destroyInstallation(InteractionDialogAPI dialog) {
        Global.getSector().getMemoryWithoutUpdate().set(INSTALLATION_FATE_KEY, "destroyed");
        SectorEntityToken site = dialog == null ? null : dialog.getInteractionTarget();
        if (site != null) {
            Misc.makeUnimportant(site, "$armaa_sfo_investigateSite");
            Misc.fadeAndExpire(site, 2f);
        }
        return true;
    }

    /**
     * $armaa_sfo_ keys that outlive the quest, either because rules still read
     * them (the stage gates the bar options, watchedReel/debriefed gate the
     * reel) or because a later quest may want to react to what the player did.
     * Everything else under the prefix is working state and is removed.
     */
    private static final List<String> KEEP = Arrays.asList(
            "$armaa_sfo_stage", "$armaa_sfo_debriefed", "$armaa_sfo_watchedReel", "$armaa_sfo_paid",
            "$armaa_sfo_royaltiesActive", "$armaa_sfo_path", "$armaa_sfo_wiped",
            "$armaa_sfo_ratings", "$armaa_sfo_favor", "$armaa_sfo_lesson", "$armaa_sfo_mercenaryAsked",
            "$armaa_sfo_installationFate", "$armaa_sfo_installationScanned",
            "$armaa_sfo_prisonerTrust", "$armaa_sfo_prisonerTaken", "$armaa_sfo_prisonerReleased",
            "$armaa_sfo_toldRoland", "$armaa_sfo_custodyReason", "$armaa_sfo_sabotage");
    private static final String KEEP_PREFIX = "$armaa_sfo_alive_";

    /**
     * Clears the quest's working state from sector memory. Safe to call from
     * several places: it does nothing until the quest is over, the reel has
     * been watched (its text reads the battle flags) and the prisoner, if the
     * player took him, has been released. Always returns true: a rule command
     * that returns false stops the rest of the row's script.
     */
    private boolean cleanup() {
        MemoryAPI m = Global.getSector().getMemoryWithoutUpdate();
        if (!m.getBoolean("$armaa_sfo_debriefed") || !m.getBoolean("$armaa_sfo_watchedReel")) {
            return true;
        }
        if (m.getBoolean("$armaa_sfo_transportingPather") && !m.getBoolean("$armaa_sfo_prisonerReleased")) {
            return true;
        }
        List<String> doomed = new ArrayList<String>();
        for (String key : m.getKeys()) {
            if (key.startsWith("$armaa_sfo_") && !KEEP.contains(key) && !key.startsWith(KEEP_PREFIX)) {
                doomed.add(key);
            }
        }
        for (String key : doomed) {
            m.unset(key);
        }
        Global.getLogger(armaa_starfallCMD.class).info(
                "[starfall] cleanup: cleared " + doomed.size() + " memory keys");
        return true;
    }

    // ------------------------------------------------------------------
    // the King's office pays out
    // ------------------------------------------------------------------
    public static final int BASE_CREDITS = 60000;
    public static final int MERCENARY_CREDITS = 90000;
    public static final int ROYALTY_PER_RATING = 2000;
    public static final String REWARD_VARIANT = "armaa_ps_damascus_standard";

    /**
     * Credits, royalties, and the Damascus. Called once from the ending.
     *
     * Ratings decide the royalties (amount and how long they run), favor decides
     * the condition of the ship and the League's regard. The wiped ending pays
     * half of everything money-shaped and gives no ship (Roland says so
     * himself), and no reputation. Guarded by $armaa_sfo_paid.
     */
    private boolean payout(InteractionDialogAPI dialog) {
        MemoryAPI m = Global.getSector().getMemoryWithoutUpdate();
        if (m.getBoolean("$armaa_sfo_paid")) {
            return true;
        }
        m.set("$armaa_sfo_paid", true);

        TextPanelAPI text = dialog.getTextPanel();
        boolean wiped = m.getBoolean("$armaa_sfo_wiped");
        float scale = wiped ? 0.5f : 1f;

        // ---- base credits; the mercenary rider only ever raises this ----
        int base = m.getBoolean("$armaa_sfo_mercenaryPayoutBoost") ? MERCENARY_CREDITS : BASE_CREDITS;
        base = (int) (base * scale);
        Global.getSector().getPlayerFleet().getCargo().getCredits().add(base);
        AddRemoveCommodity.addCreditsGainText(base, text);

        // ---- royalties: amount and length both follow ratings ----
        int ratings = m.getInt("$armaa_sfo_ratings");
        int royalty = (int) (ROYALTY_PER_RATING * ratings * scale);
        int months = ratings >= 7 ? 24 : (ratings >= 4 ? 12 : 6);
        if (royalty > 0) {
            new armaa_starfallRoyalties(royalty, months);
            text.addPara("You will receive " + Misc.getDGSCredits(royalty)
                    + " a month in royalties for " + months + " months.");
            text.highlightInLastPara(Misc.getHighlightColor(), Misc.getDGSCredits(royalty), "" + months);
        }

        if (wiped) {
            return true;
        }

        // ---- favor: the ship's condition, and the League's regard ----
        int favor = m.getInt("$armaa_sfo_favor");
        int dmods = favor >= 7 ? 0 : (favor > 2 ? 1 : 3);
        FleetMemberAPI ship = Global.getFactory().createFleetMember(FleetMemberType.SHIP, REWARD_VARIANT);
        ship.setShipName(Global.getSector().getPlayerFaction().pickRandomShipName());
        if (dmods > 0) {
            DModManager.addDMods(ship, false, dmods, new Random());
        }
        Global.getSector().getPlayerFleet().getFleetData().addFleetMember(ship);
        AddRemoveCommodity.addFleetMemberGainText(ship, text);

        float rep = favor >= 7 ? 5f : (favor > 2 ? 2f : 0f);
        if (rep > 0) {
            CustomRepImpact impact = new CustomRepImpact();
            impact.delta = rep * 0.01f;
            Global.getSector().adjustPlayerReputation(
                    new RepActionEnvelope(RepActions.CUSTOM, impact, null, text, true),
                    Factions.PERSEAN);
        }

        Global.getLogger(armaa_starfallCMD.class).info(
                "[starfall] payout: base=" + base + " royalty=" + royalty + "x" + months
                + " ratings=" + ratings + " favor=" + favor + " dmods=" + dmods + " rep=" + rep);
        return true;
    }

    private static String band(int value) {
        if (value >= 7) {
            return "High";
        }
        if (value >= 4) {
            return "Medium";
        }
        if (value < 0) {
            return "Abysmal";
        }
        return "Low";
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

    // ------------------------------------------------------------------
    // the debrief decision: Mercenary / Deny / Accept / Solo
    // ------------------------------------------------------------------
    /**
     * "Mercenary" is a rider on top of the real choice, not a fourth path -
     * it just banks the payout memkey, then lets armaa_starfallDebriefOpts
     * re-present Deny/Accept/Solo (rules.csv hides this option itself
     * afterward, via $armaa_sfo_mercenaryAsked). The ethos increment stays in
     * rules.csv as $player.ethosMercenary++, same as every other ethos hit in
     * this file - no need to resolve player memory from Java for it.
     */
    private boolean mercenary() {
        MemoryAPI global = Global.getSector().getMemoryWithoutUpdate();
        global.set("$armaa_sfo_mercenaryAsked", true);
        // read by whatever computes the King's office's payout at the very end -
        // this command only banks the intent, not the credits themselves.
        global.set("$armaa_sfo_mercenaryPayoutBoost", true);
        return true;
    }

    /**
     * Deny/Accept/Solo. armaa_starfallMission.routeAfterDecisionIfNeeded
     * still reads $armaa_sfo_path off global memory to issue the intake's
     * fleet orders (it needs findIntake() to succeed first, so that part
     * waits a tick) - but raising the Pather base happens right here,
     * synchronously, so the stage flips to INVESTIGATE and the intel panel
     * updates the same instant the option is picked, rather than lagging a
     * tick behind and briefly showing the old DEBRIEF text.
     *
     * STAGE_KEY is "RAID_DONE" the whole time from raidDone() through this
     * entire decision conversation - armaa_starfallDebrief1 does NOT set it
     * to "DEBRIEF" (deliberately; it used to, and that was the bug: the bar
     * option at Fikenhild would already be live the moment Roland hailed you,
     * long before any of Deny/Accept/Solo/the ambush had happened). DEBRIEF
     * is earned two ways only: choosing Deny here, or ambushDefeated() after
     * Accept/Solo. Nothing else may set STAGE_KEY to "DEBRIEF".
     *
     * $armaa_sfo_readyToDebrief is the same idea, one layer down: it drives
     * the MISSION's own internal Stage enum (Stage.DECISION -> Stage.DEBRIEF),
     * which had the identical bug independently of STAGE_KEY -
     * connectWithGlobalFlag(RAID_DONE, DEBRIEF, debriefOpened) used to put the
     * mission's currentStage at DEBRIEF the instant the hail opened, which is
     * what the intel panel/next-step text actually reads. Both flags are set
     * together here and in ambushDefeated() so the string (for rules.csv) and
     * the enum (for the mission's own tracker) never drift apart again.
     */
    private boolean choosePath(String path) {
        MemoryAPI global = Global.getSector().getMemoryWithoutUpdate();
        global.set(armaa_starfallMission.PATH_KEY, path);
        if ("DENY".equals(path)) {
            global.set(STAGE_KEY, "DEBRIEF");
            global.set("$armaa_sfo_readyToDebrief", true);
            return true;
        }

        global.set(STAGE_KEY, "INVESTIGATE");

        Object ref = global.get("$armaa_sfo_ref");
        if (ref instanceof armaa_starfallMission) {
            ((armaa_starfallMission) ref).spawnPatherBase();
        }
        return true;
    }

    /**
     * Fired from the placeholder investigate entity's dialog, regardless of
     * what is said there - see armaa_starfallMission.spawnAmbushFleet() for
     * why this is unconditional.
     */
    private boolean spawnAmbush() {
        MemoryAPI global = Global.getSector().getMemoryWithoutUpdate();
        Object ref = global.get("$armaa_sfo_ref");
        if (!(ref instanceof armaa_starfallMission)) {
            return false;
        }
        if (!armaa_starfallMission.AMBUSH_ENABLED) {
            // No ambush: finishing the site survey is what ends the
            // investigation. Same flags the ambush-defeated path sets, minus
            // the closing hail (the survey scenes carry Roland's lines).
            global.set("$armaa_sfo_ambushSpawned", true);
            global.set("$armaa_sfo_installationScanned", true);
            ((armaa_starfallMission) ref).ambushDefeated(false);
            return true;
        }
        ((armaa_starfallMission) ref).spawnAmbushFleet();
        return true;
    }

    /**
     * Writes $armaa_sfo_intakeNearby for rules.csv to gate the optional crew
     * dialogue at the Pather installation. Always returns true - "not nearby"
     * is a normal answer, not a failure, and returning false would halt the
     * rest of the script that called this.
     */
    private boolean intakeNearby() {
        boolean near = false;
        String status = "none";
        Object ref = Global.getSector().getMemoryWithoutUpdate().get("$armaa_sfo_ref");
        if (ref instanceof armaa_starfallMission) {
            near = ((armaa_starfallMission) ref).isIntakeNearPlayer();
            status = ((armaa_starfallMission) ref).getIntakeStatus();
        }
        Global.getSector().getMemoryWithoutUpdate().set("$armaa_sfo_intakeNearby", near);
        Global.getSector().getMemoryWithoutUpdate().set("$armaa_sfo_intakeStatus", status);
        return true;
    }

    /**
     * Starts the stealth section in the dialog's visual panel. Difficulty is
     * the alone escape's heat so far ($armaa_sfo_aloneHeat). The options are
     * swapped for a single Skip; the panel replaces them with Continue when the
     * run ends. Always returns true.
     */
    private boolean startStealth(InteractionDialogAPI dialog) {
        if (dialog == null) {
            return false;
        }
        MemoryAPI m = Global.getSector().getMemoryWithoutUpdate();
        int difficulty = m.contains("$armaa_sfo_aloneHeat") ? m.getInt("$armaa_sfo_aloneHeat") : 0;
        // who is in the cages of the hub: the three officers with the order's party; alone, the escort that came
        String occupants = "named";
        int occupantCount = 0;
        String escortKey = m.contains("$armaa_sfo_boardEscort") ? m.getString("$armaa_sfo_boardEscort") : "none";
        int lost = m.contains("$armaa_sfo_marinesLost") ? m.getInt("$armaa_sfo_marinesLost") : 0;
        if (!m.getBoolean("$armaa_sfo_boardAlone")) {
            // with the order's party: the officers, and the escort in the next cage
            if ("token".equals(escortKey)) {
                occupants = "named+bodyguards";
            } else if ("full".equals(escortKey)) {
                occupants = "named+marines";
                occupantCount = 5 - lost;
            }
        } else {
            String escort = m.contains("$armaa_sfo_boardEscort") ? m.getString("$armaa_sfo_boardEscort") : "none";
            if ("token".equals(escort)) {
                occupants = "bodyguards";
            } else if ("full".equals(escort)) {
                occupants = "marines";
                occupantCount = 5 - (m.contains("$armaa_sfo_marinesLost") ? m.getInt("$armaa_sfo_marinesLost") : 0);
            } else {
                occupants = "none";
            }
        }
        dialog.getOptionPanel().clearOptions();
        dialog.getOptionPanel().addOption("Skip this section", "armaa_sfo_boardAloneStealthSkip");
        dialog.getVisualPanel().showCustomPanel(
                data.scripts.campaign.stealth.armaa_stealthSim.VIEW_W * data.scripts.campaign.stealth.armaa_stealthPanel.fit(),
                data.scripts.campaign.stealth.armaa_stealthSim.VIEW_H * data.scripts.campaign.stealth.armaa_stealthPanel.fit(),
                new data.scripts.campaign.stealth.armaa_stealthPanel(dialog, difficulty, !m.getBoolean("$armaa_sfo_boardAlone"), occupants, occupantCount));
        return true;
    }

    /**
     * Fired off the ArmaaStarfallAmbushDefeated trigger. Delegates to
     * armaa_starfallMission.ambushDefeated(), which moves STAGE_KEY from
     * "INVESTIGATE" back to "DEBRIEF" - that alone is what lets
     * armaa_starfallDebriefOption fire again once the player is back at
     * Fikenhild. $armaa_sfo_debriefed is a separate flag, set later at the
     * end of the Fikenhild bench scene, same as the Deny branch.
     */
    private boolean ambushDefeated() {
        Object ref = Global.getSector().getMemoryWithoutUpdate().get("$armaa_sfo_ref");
        if (ref instanceof armaa_starfallMission) {
            ((armaa_starfallMission) ref).ambushDefeated();
        }
        return true;
    }
}
