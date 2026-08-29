package data.scripts.campaign;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.EngagementResultForFleetAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

import data.scripts.campaign.missions.armaa_starfallMission;

/**
 * Records what the exercise battle actually cost.
 *
 * Two-callback pattern, same as armaa_drugsAreBad: reportPlayerEngagement can
 * fire more than once per battle and the lists are not settled there, so it
 * only stashes the result. The work happens in reportBattleOccurred, which runs
 * once, at the end.
 *
 * Member attribution goes through battle.getSourceFleet(member). In a
 * multi-fleet battle the result's getFleet() is the COMBINED fleet, not any one
 * participant - so checking the intake's memory flag on it would never match,
 * and members from every allied fleet turn up in the same lists.
 *
 * Writes (bare - rules adds the $global. prefix): $armaa_sfo_battleSeen a
 * qualifying engagement was captured $armaa_sfo_playerDeployedFP fleet points
 * the player committed $armaa_sfo_playerTotalFP fleet points the player had
 * available $armaa_sfo_playerCommitPct 0-100, share of YOUR OWN fleet you
 * deployed $armaa_sfo_intakeDeployedFP fleet points the intake put on the field
 * $armaa_sfo_playerShareOfForce 0-100, your share of the allied line
 * $armaa_sfo_heldBack your share of the line was under the threshold
 * $armaa_sfo_intakeLostShips intake hulls destroyed or disabled
 * $armaa_sfo_hurt_<recruit> that recruit's hull was destroyed or disabled
 */
public class armaa_starfallBattleListener extends BaseCampaignEventListener {

    /**
     * Under this share of the ALLIED LINE counts as letting the recruits carry
     * it. This is deliberately your contribution relative to theirs, not
     * relative to your own fleet size - a small fleet deploying everything
     * still may have done less of the fighting than a large one deploying a
     * fraction, and it is the second thing the segment is about.
     */
    public static final float HELD_BACK_THRESHOLD = 0.34f;

    protected transient EngagementResultAPI batResult;
    /**
     * Every hull on the PLAYER SIDE deployed at any point, across all rounds -
     * yours and the intake's both. They are separated later by source fleet.
     */
    protected transient Set<FleetMemberAPI> everDeployed;

    public armaa_starfallBattleListener() {
        super(true);
    }

    @Override
    public void reportPlayerEngagement(EngagementResultAPI result) {
        // do NOT do the work here - the state is not final yet. But this DOES
        // fire once per engagement round, and getDeployed() is per-round, so
        // the rounds have to be accumulated or a long battle reports only
        // whatever happened to be on the field at the end.
        batResult = result;
        if (result == null) {
            return;
        }

        if (everDeployed == null) {
            everDeployed = new HashSet<FleetMemberAPI>();
        }
        EngagementResultForFleetAPI winner = result.getWinnerResult();
        EngagementResultForFleetAPI loser = result.getLoserResult();
        if (winner != null && winner.isPlayer() && winner.getDeployed() != null) {
            everDeployed.addAll(winner.getDeployed());
        }
        if (loser != null && loser.isPlayer() && loser.getDeployed() != null) {
            everDeployed.addAll(loser.getDeployed());
        }
    }

    @Override
    public void reportBattleOccurred(CampaignFleetAPI primaryWinner, BattleAPI battle) {
        if (battle == null || !battle.isPlayerInvolved()) {
            return;
        }
        if (batResult == null) {
            return;
        }

        EngagementResultAPI result = batResult;
        batResult = null;
        Set<FleetMemberAPI> deployedThisBattle = everDeployed;
        everDeployed = null;

        EngagementResultForFleetAPI winner = result.getWinnerResult();
        EngagementResultForFleetAPI loser = result.getLoserResult();
        if (winner == null || loser == null) {
            return;
        }
        EngagementResultForFleetAPI playerSide = loser.isPlayer() ? loser : winner;

        CampaignFleetAPI intake = findIntake(battle);
        if (intake == null) {
            return;   // not the exercise
        }

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        MemoryAPI m = Global.getSector().getMemoryWithoutUpdate();

        // ---- how much of your own fleet you committed ----
        // Numerator: everything of yours that touched the field at any point,
        // accumulated across engagement rounds. Denominator: the fleet you
        // actually had, read straight off the player fleet rather than from the
        // result lists, which are per-round and lose ships as they are killed.
        int deployedFP = sumFPFrom(deployedThisBattle, battle, player);
        int totalFP = sumFP(player);
        if (totalFP < deployedFP) {
            totalFP = deployedFP;   // losses can push the live fleet below it
        }

        m.set("$armaa_sfo_playerDeployedFP", deployedFP);
        m.set("$armaa_sfo_playerTotalFP", totalFP);

        float pct = totalFP > 0 ? (float) deployedFP / (float) totalFP : 1f;
        // kept as a secondary read - how much of your OWN fleet you risked
        m.set("$armaa_sfo_playerCommitPct", (int) (pct * 100f));

        // ---- your share of the line ----
        // The intake's deployment is the comparison that matters. Their hulls
        // are also in the player-side lists, separated by source fleet.
        int intakeDeployedFP = sumFPFrom(deployedThisBattle, battle, intake);
        if (intakeDeployedFP == 0) {
            // they may have deployed in a round the accumulator did not see;
            // fall back to what they had on the field per the final result
            intakeDeployedFP = sumFPFrom(playerSide.getDeployed(), battle, intake);
        }
        m.set("$armaa_sfo_intakeDeployedFP", intakeDeployedFP);

        int lineFP = deployedFP + intakeDeployedFP;
        float share = lineFP > 0 ? (float) deployedFP / (float) lineFP : 1f;
        m.set("$armaa_sfo_playerShareOfForce", (int) (share * 100f));
        m.set("$armaa_sfo_heldBack", share < HELD_BACK_THRESHOLD);

        // ---- what it cost the intake ----
        int lost = 0;
        lost += tally(playerSide.getDestroyed(), battle, intake, m);
        lost += tally(playerSide.getDisabled(), battle, intake, m);
        m.set("$armaa_sfo_intakeLostShips", lost);

        m.set("$armaa_sfo_battleSeen", true);

        if (armaa_starfallMission.DEBUG) {
            logRoster("PLAYER", deployedThisBattle, battle, player);
            logRoster("INTAKE", deployedThisBattle, battle, intake);
            Global.getLogger(armaa_starfallBattleListener.class).info(
                    "[starfall] line: player " + deployedFP + " FP + intake "
                    + intakeDeployedFP + " FP = " + lineFP + " FP total"
                    + " -> player is " + m.getInt("$armaa_sfo_playerShareOfForce")
                    + "% of the line"
                    + " | player risked " + deployedFP + "/" + totalFP
                    + " of own fleet (" + (int) (pct * 100f) + "%)"
                    + " | intake lost " + lost + " hulls");
        }
        float sum = 0f;
        int n = 0;
        for (FleetMemberAPI member : intake.getFleetData().getMembersListCopy()) {
            sum += member.getStatus().getHullFraction();
            n++;
        }
        pct = n > 0 ? (int) ((sum / n) * 100f) : 100;
        m.set("$armaa_sfo_intakeHullPct", pct);
        m.set("$armaa_sfo_intakeMauled", pct < 50);
    }

    /**
     * Lists every hull one fleet put on the field, with its FP.
     */
    private void logRoster(String label, java.util.Collection<FleetMemberAPI> members,
            BattleAPI battle, CampaignFleetAPI from) {
        if (members == null || from == null) {
            return;
        }
        int running = 0;
        for (FleetMemberAPI member : members) {
            if (battle.getSourceFleet(member) != from) {
                continue;
            }
            running += member.getFleetPointCost();
            PersonAPI captain = member.getCaptain();
            String who = captain == null ? "-" : captain.getNameString();
            Global.getLogger(armaa_starfallBattleListener.class).info(
                    "[starfall]   " + label + " " + member.getHullId()
                    + " \"" + member.getShipName() + "\""
                    + " fp=" + member.getFleetPointCost()
                    + " dp=" + (int) member.getDeploymentPointsCost()
                    + " captain=" + who
                    + " (running " + running + ")");
        }
        Global.getLogger(armaa_starfallBattleListener.class).info(
                "[starfall]   " + label + " TOTAL " + running + " FP");
    }

    /**
     * The intake fleet if it took part in this battle, otherwise null.
     */
    private CampaignFleetAPI findIntake(BattleAPI battle) {
        List<CampaignFleetAPI> all = battle.getBothSides();
        if (all == null) {
            return null;
        }
        for (CampaignFleetAPI fleet : all) {
            if (fleet.getMemoryWithoutUpdate().getBoolean("$armaa_sfo_intakeSpawned")) {
                return fleet;
            }
        }
        return null;
    }

    /**
     * Fleet points of the whole fleet as it stands.
     */
    private int sumFP(CampaignFleetAPI fleet) {
        if (fleet == null) {
            return 0;
        }
        int total = 0;
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            total += member.getFleetPointCost();
        }
        return total;
    }

    /**
     * Fleet points of members that came from this specific fleet.
     */
    private int sumFPFrom(java.util.Collection<FleetMemberAPI> members, BattleAPI battle, CampaignFleetAPI from) {
        if (members == null || from == null) {
            return 0;
        }
        int total = 0;
        for (FleetMemberAPI member : members) {
            if (battle.getSourceFleet(member) != from) {
                continue;
            }
            total += member.getFleetPointCost();
        }
        return total;
    }

    /**
     * Counts intake hulls in the list and flags any recruits among them.
     */
    private int tally(List<FleetMemberAPI> members, BattleAPI battle,
            CampaignFleetAPI intake, MemoryAPI m) {
        if (members == null) {
            return 0;
        }
        int count = 0;
        for (FleetMemberAPI member : members) {
            if (battle.getSourceFleet(member) != intake) {
                continue;
            }
            count++;

            PersonAPI captain = member.getCaptain();
            if (!armaa_starfallMission.isRecruit(captain)) {
                continue;
            }
            String id = captain.getId();
            m.set("$armaa_sfo_hurt_" + id.substring(id.lastIndexOf('_') + 1), true);
        }
        return count;
    }
}
