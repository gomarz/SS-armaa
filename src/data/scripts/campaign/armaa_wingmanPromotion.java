package data.scripts.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.util.Misc;
import data.scripts.campaign.intel.armaa_promoteWingman;
import com.fs.starfarer.api.campaign.FleetEncounterContextPlugin.DataForEncounterSide;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent;
import java.util.*;
import com.fs.starfarer.api.characters.*;

import com.fs.starfarer.api.impl.campaign.ids.Stats;

import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.MissionCompletionRep;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.*;
import com.fs.starfarer.api.campaign.RepLevel;
import org.lazywizard.lazylib.MathUtils;

// Pull the static skill list from the hullmod so there's a single source of truth.
import data.hullmods.armaa_wingCommander;
import lunalib.lunaSettings.LunaSettings;

public class armaa_wingmanPromotion extends BaseCampaignEventListener implements EveryFrameScript {

    public armaa_wingmanPromotion() {
        super(true);
    }

    static long previousXP = Long.MAX_VALUE;
    private Float repRewardPerson = null;
    private Float repPenaltyPerson = null;
    private int numEngagements = 0;
    RepLevel rewardLimitPerson = null;
    RepLevel penaltyLimitPerson = null;
    transient boolean isWin = false;
    private transient Map<FleetMemberAPI, List<Integer>> promoteablePilots = new HashMap<>();
    private transient Map<String, Float> damageThisBattle = new HashMap<String, Float>();
    transient EngagementResultAPI batResult;
    // XP tuning: base XP needed for the first level; scales linearly with level.
    public static float XP_BASE = 250f;
    // How much a battle contributes: flat floor + scaled by the ship's hull damage output.
    public static float XP_FLAT_PER_BATTLE = 10f;
    public static float XP_PER_DAMAGE = 2.0f;
    public static float XP_PER_ENGAGEMENT = 5f;
    public static float XP_MAX_BATTLE_FRACTION = 0.5f;
    // Latent-talent Ace awakening: per-engagement chance, gated behind the latent
    // tag (rolled at pilot creation) and the fleet-wide Ace cap.
    public static float ACE_AWAKEN_CHANCE_PER_ENGAGEMENT = 0.03f;
    // Loyalty floor for the awakening roll. Low (vs the 0.50 at-cap elite gate) so
    // a promising rookie can surprise the player early rather than only at max level.
    public static float ACE_AWAKEN_MIN_REL = 0.25f;

    public static long getReAdjustedXp() {
        return Global.getSector().getPlayerStats().getXP()
                + Global.getSector().getPlayerStats().getBonusXp() - previousXP;
    }

    public TextPanelAPI textPanelForXPGain = null;

    public TextPanelAPI getTextPanelForXPGain() {
        return textPanelForXPGain;
    }

    public void setTextPanelForXPGain(TextPanelAPI t) {
        textPanelForXPGain = t;
    }

    private Random salvageRandom = null;

    public Random getSalvageRandom() {
        return salvageRandom;
    }

    public void setSalvageRandom(Random r) {
        salvageRandom = r;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public void advance(float amount) {
        long xp = Global.getSector().getPlayerStats().getXP();
        if (previousXP == Long.MAX_VALUE) {
            previousXP = xp;
        } else if (previousXP < xp) {
            long dXp = Math.min(10000, xp - previousXP);
            previousXP = xp;
        }
    }

    public DataForEncounterSide getDataFor(CampaignFleetAPI participantOrCombined, BattleAPI battle,
            List<DataForEncounterSide> sideData) {
        CampaignFleetAPI combined = battle.getCombinedFor(participantOrCombined);
        if (combined == null) {
            return new DataForEncounterSide(participantOrCombined);
        }
        for (DataForEncounterSide curr : sideData) {
            if (curr.getFleet() == combined) {
                return curr;
            }
        }
        DataForEncounterSide dfes = new DataForEncounterSide(combined);
        sideData.add(dfes);
        return dfes;
    }

    protected void clearNoSourceMembers(EngagementResultForFleetAPI result, BattleAPI battle) {
        clearNoSource(result.getDeployed(), battle);
        clearNoSource(result.getReserves(), battle);
        clearNoSource(result.getDestroyed(), battle);
        clearNoSource(result.getDisabled(), battle);
        clearNoSource(result.getRetreated(), battle);
    }

    // --- PERF FIX (item 5): extracted helper avoids copy-pasting the iterator pattern 5 times ---
    private void clearNoSource(Collection<FleetMemberAPI> col, BattleAPI battle) {
        Iterator<FleetMemberAPI> iter = col.iterator();
        while (iter.hasNext()) {
            if (battle.getSourceFleet(iter.next()) == null) {
                iter.remove();
            }
        }
    }

    @Override
    public void reportPlayerEngagement(EngagementResultAPI result) {
        batResult = result;
        numEngagements++;
        if (damageThisBattle == null) {
            damageThisBattle = new HashMap<String, Float>();
        }

        CombatDamageData dmgData = result.getLastCombatDamageData();
        if (dmgData == null) {
            return;
        }

        for (FleetMemberAPI member : Global.getSector().getPlayerFleet()
                .getFleetData().getMembersListCopy()) {
            if (dmgData.getDealtBy(member) == null
                    || dmgData.getDealtBy(member).getDamage() == null) {
                continue;
            }
            float rawHull = 0f;
            for (CombatDamageData.DamageToFleetMember f
                    : dmgData.getDealtBy(member).getDamage().values()) {
                rawHull += f.hullDamage;
            }
            String key = member.getId();
            float prev = damageThisBattle.containsKey(key) ? damageThisBattle.get(key) : 0f;
            damageThisBattle.put(key, prev + rawHull);
        }
    }

    @Override
    public void reportBattleOccurred(CampaignFleetAPI primaryWinner, BattleAPI battle) {
        if (!battle.isPlayerInvolved() || batResult == null) {
            clearBattleState();
            return;
        }

        isWin = battle == null || primaryWinner == null || battle.getPlayerSide() == null ? null
                : battle.getPlayerSide().contains(primaryWinner);

        List<DataForEncounterSide> sideData = new ArrayList<DataForEncounterSide>();
        long xpEarned = getReAdjustedXp();
        if (xpEarned <= 0) {
            clearBattleState();
            return;
        }

        EngagementResultForFleetAPI winnerResult = batResult.getWinnerResult();
        EngagementResultForFleetAPI loserResult = batResult.getLoserResult();
        clearNoSourceMembers(winnerResult, battle);
        clearNoSourceMembers(loserResult, battle);

        DataForEncounterSide winnerData = getDataFor(winnerResult.getFleet(), battle, sideData);
        DataForEncounterSide loserData = getDataFor(loserResult.getFleet(), battle, sideData);
        DataForEncounterSide sideOne = winnerData;
        DataForEncounterSide sideTwo = loserData;

        if (battle.isPlayerSide(battle.getSideFor(sideTwo.getFleet()))) {
        }

        EngagementResultForFleetAPI enemyFleet = loserResult.isPlayer() ? winnerResult : loserResult;
        EngagementResultForFleetAPI playerFleet = loserResult.isPlayer() ? loserResult : winnerResult;

        List<FleetMemberAPI> enemyCasualties = new ArrayList<FleetMemberAPI>();
        List<FleetMemberAPI> allyCasualties = new ArrayList<FleetMemberAPI>();
        enemyCasualties.addAll(enemyFleet.getDestroyed());
        enemyCasualties.addAll(enemyFleet.getDisabled());
        allyCasualties.addAll(playerFleet.getDestroyed());
        allyCasualties.addAll(playerFleet.getDisabled());
        allyCasualties.addAll(playerFleet.getRetreated());

        float fpDestroyed = 0f;
        for (FleetMemberAPI data : enemyCasualties) {
            float fp = data.getFleetPointCost();
            fp *= 1f + data.getCaptain().getStats().getLevel() / 5f;
            fpDestroyed += fp;
        }
        for (FleetMemberAPI data : allyCasualties) {
            if (data.isAlly()) {
                continue;
            }
            float fp = data.getFleetPointCost();
            fp *= 1f + data.getCaptain().getStats().getLevel() / 5f;
            fpDestroyed += fp;
        }

        List<FleetMemberAPI> playerMembers
                = Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy();

        for (FleetMemberAPI member : playerMembers) {
            float fp = member.getFleetPointCost();
            fp *= 1f + member.getCaptain().getStats().getLevel() / 5f;

            final String captainId = member.getCaptain().getId();
            final String sizeKey = "armaa_wingCommander_squadSize_" + captainId;

            // Only process WINGCOM ships that have an established named squad
            if (!(Global.getSector().getPersistentData()
                    .get("armaa_wingCommander_wingman_0_" + captainId) instanceof PersonAPI)) {
                continue;
            }
            if (!member.getVariant().hasHullMod("armaa_wingCommander")) {
                continue;
            }
            if (!playerFleet.getDeployed().contains(member) && !allyCasualties.contains(member)) {
                continue;
            }

            float damageDealt = 0f;
            if (damageThisBattle != null && damageThisBattle.containsKey(member.getId())) {
                damageDealt = damageThisBattle.get(member.getId());
            }
            damageDealt = damageDealt / member.getMemberStrength() / 5f;
            adjustRelations(member, allyCasualties, damageDealt);
        }

        int max = Misc.getMaxOfficers(Global.getSector().getPlayerFleet());
        int curr = Misc.getNumNonMercOfficers(Global.getSector().getPlayerFleet());

        ArrayList<String> alreadyConsidered = new ArrayList<String>();
        if (curr < max) {
            if (Global.getSector().getIntelManager().getIntel(armaa_promoteWingman.class) != null) {
                int size = Global.getSector().getIntelManager()
                        .getIntel(armaa_promoteWingman.class).size();
                for (int i = 0; i < size; i++) {
                    armaa_promoteWingman pastIntel = (armaa_promoteWingman) Global.getSector()
                            .getIntelManager().getIntel(armaa_promoteWingman.class).get(i);
                    alreadyConsidered.add(pastIntel.getCandidate().getId());
                }
            }
            if (promoteablePilots != null) {
                for (Map.Entry<FleetMemberAPI, List<Integer>> mapElement : promoteablePilots.entrySet()) {
                    FleetMemberAPI key = mapElement.getKey();
                    if (key == null) {
                        continue;
                    }
                    // --- PERF FIX (item 3): hoist captain ID for inner loop ---
                    final String captainId = key.getCaptain().getId();
                    for (Integer pilotIdx : mapElement.getValue()) {
                        boolean alreadyEligible = false;
                        PersonAPI candidate = null;
                        Object p = Global.getSector().getPersistentData()
                                .get("armaa_wingCommander_wingman_" + pilotIdx + "_" + captainId);
                        if (p instanceof PersonAPI) {
                            candidate = (PersonAPI) p;
                        }
                        if (candidate == null) {
                            continue;
                        }
                        if (candidate.hasTag("armaa_doNotAutoConsiderAgain")) {
                            continue;
                        }
                        if (alreadyConsidered.contains(candidate.getId())) {
                            alreadyEligible = true;
                        }

                        if (!alreadyEligible) {
                            armaa_promoteWingman intel = new armaa_promoteWingman(
                                    textPanelForXPGain, key, pilotIdx);
                            Global.getSector().getIntelManager()
                                    .addIntel(intel, false, textPanelForXPGain);
                        }
                    }
                }
                promoteablePilots.clear();
            }
        }
        enemyCasualties.clear();
        allyCasualties.clear();
        previousXP = Global.getSector().getPlayerStats().getXP();
        clearBattleState();
    }

    private void adjustRelations(FleetMemberAPI member, List<FleetMemberAPI> members, float damageDealt) {
        List<RepActions> bonus = new ArrayList<RepActions>();
        bonus.add(RepActions.COMBAT_HELP_MINOR);
        bonus.add(RepActions.COMBAT_HELP_MAJOR);
        bonus.add(RepActions.MISSION_SUCCESS);

        Random rand = new Random();
        if (members != null && members.contains(member)) {
            bonus.clear();
            bonus.add(RepActions.COMBAT_NORMAL_TOFF);
            bonus.add(RepActions.COMBAT_AGGRESSIVE_TOFF);
            bonus.add(RepActions.MISSION_FAILURE);
            if (isWin) {
                bonus.add(RepActions.MISSION_SUCCESS);
            }
        }

        // --- PERF FIX (item 3): hoist captain ID before the pilot loop ---
        final String captainId = member.getCaptain().getId();
        final String sizeKey = "armaa_wingCommander_squadSize_" + captainId;
        int count = 0;
        Object sizeObj = Global.getSector().getPersistentData().get(sizeKey);
        if (sizeObj instanceof Integer) {
            count = (Integer) sizeObj;
        }

        ArrayList<Integer> pilots = new ArrayList<Integer>();
        for (int j = 0; j < count; j++) {
            repRewardPerson = null;
            repPenaltyPerson = null;
            rewardLimitPerson = null;
            penaltyLimitPerson = null;

            // --- PERF FIX (item 2): single lookup, cast once ---
            Object pilotObj = Global.getSector().getPersistentData()
                    .get("armaa_wingCommander_wingman_" + j + "_" + captainId);
            if (!(pilotObj instanceof PersonAPI)) {
                continue;
            }

            PersonAPI pilot = (PersonAPI) pilotObj;
            String callsign = (String) Global.getSector().getPersistentData()
                    .get("armaa_wingCommander_wingman_" + j + "_callsign_" + captainId);

            MissionCompletionRep completionRepPerson = new MissionCompletionRep(
                    getRepRewardSuccessPerson(), getRewardLimitPerson(),
                    -getRepPenaltyFailurePerson(), getPenaltyLimitPerson());

            Global.getSector().adjustPlayerReputation(
                    new RepActionEnvelope(bonus.get(rand.nextInt(bonus.size())),
                            completionRepPerson, null, true, false),
                    pilot);

            levelUpIfApplicable(pilot, j, callsign, member, damageDealt);

            if (pilot.getRelToPlayer().getRel() >= .70f) {
                pilots.add(j);
            }
        }

        if (promoteablePilots == null) {
            promoteablePilots = new HashMap<>();
        }
        promoteablePilots.put(member, pilots);
    }

    public float getRepRewardSuccessPerson() {
        if (repRewardPerson != null) {
            return repRewardPerson;
        }
        return MathUtils.getRandomNumberInRange(RepRewards.SMALL, RepRewards.VERY_HIGH) * numEngagements;
    }

    public float getRepPenaltyFailurePerson() {
        if (repPenaltyPerson != null) {
            return repPenaltyPerson;
        }
        return MathUtils.getRandomNumberInRange(RepRewards.SMALL, RepRewards.VERY_HIGH);
    }

    public RepLevel getRewardLimitPerson() {
        return rewardLimitPerson != null ? rewardLimitPerson : RepLevel.COOPERATIVE;
    }

    public RepLevel getPenaltyLimitPerson() {
        return penaltyLimitPerson != null ? penaltyLimitPerson : RepLevel.VENGEFUL;
    }

    // -------------------------------------------------------------------------
    // levelUpIfApplicable fork:
    //   (always)   -> latent-talent pilots roll for Ace awakening at ANY level
    //   below cap  -> deterministic XP accumulation (loyalty scales the rate)
    //   at/above   -> RNG elite skill upgrades (requires loyalty)
    // -------------------------------------------------------------------------
    public void levelUpIfApplicable(PersonAPI pilot, int j, String callsign,
            FleetMemberAPI member, float damageDealt) {
        int levelCap = 2;
        if (Global.getSettings().getModManager().isModEnabled("lunalib")) {
            levelCap = LunaSettings.getInt("armaa", "armaa_wingcomMaxLevel");
        }
        final String captainId = member.getCaptain().getId();

        // Latent-talent Ace awakening can fire at ANY level. Hidden from the player:
        // no UI marker distinguishes these pilots, and the SP "Promote to Ace" button
        // is gated to max level for everyone, so it reveals nothing. The only tell is
        // that a latent pilot may unexpectedly become an Ace early in its career.
        if (tryLatentAwakening(pilot, j, callsign, captainId)) {
            return; // awakening is the event this battle; skip XP/elite this turn
        }

        if (pilot.getStats().getLevel() < levelCap) {
            accrueXp(pilot, j, callsign, captainId, damageDealt, levelCap);
        } else {
            tryEliteUpgrade(pilot, j, callsign, captainId);
        }
    }

    // --- LATENT AWAKENING: rare per-battle roll, any level, low loyalty floor so a
    //     promising rookie can surprise the player early. Returns true if it fired. ---
    private boolean tryLatentAwakening(PersonAPI pilot, int j, String callsign, String captainId) {
        if (!pilot.hasTag("armaa_latentTalent")
                || armaa_AceUtil.hasAce(pilot)
                || !armaa_AceUtil.canGrantAce()) {
            return false;
        }
        if (pilot.getRelToPlayer().getRel() < ACE_AWAKEN_MIN_REL) {
            return false;
        }
        if (Math.random() >= ACE_AWAKEN_CHANCE_PER_ENGAGEMENT * numEngagements) {
            return false;
        }
        if (!armaa_AceUtil.grantAce(pilot)) {
            return false; // cap raced out from under us between check and grant
        }
        pilot.removeTag("armaa_latentTalent");
        Global.getSector().getCampaignUI().addMessage(
                "Pilot, callsign \"" + callsign + "\" has awakened as an ACE PILOT!",
                Misc.getHighlightColor());
        Global.getSector().getPersistentData().put(
                "armaa_wingCommander_wingman_" + j + "_" + captainId, pilot);
        return true;
    }

    // --- BELOW CAP: deterministic XP. Loyalty scales the rate; it never blocks. ---
    private void accrueXp(PersonAPI pilot, int j, String callsign,
            String captainId, float damageDealt, int levelCap) {
        final String xpKey = "armaa_wingCommander_wingman_" + j + "_xp_" + captainId;
        float xp = 0f;
        Object xpObj = Global.getSector().getPersistentData().get(xpKey);
        if (xpObj instanceof Float) {
            xp = (Float) xpObj;
        }

        int level = pilot.getStats().getLevel();
        float threshold = XP_BASE * (level + 1);

        float loyaltyMult = Math.max(0.25f, 0.5f + pilot.getRelToPlayer().getRel());

        // Damage is the primary driver; engagements are a minor nudge.
        float battleKick = damageDealt * XP_PER_DAMAGE + numEngagements * XP_PER_ENGAGEMENT;
        // Ceiling scales with the level requirement, so big battles stay rewarding
        // at every level but can never one-shot a level.
        float maxPerBattle = threshold * XP_MAX_BATTLE_FRACTION;
        float gained = (XP_FLAT_PER_BATTLE + Math.min(battleKick, maxPerBattle)) * loyaltyMult;
        xp += gained;

        while (level < levelCap && xp >= threshold) {
            MutableCharacterStatsAPI stats = pilot.getStats();
            String newSkill = OfficerManagerEvent.pickSkill(
                    pilot, armaa_wingCommander.VALID_SKILLS,
                    OfficerManagerEvent.SkillPickPreference.YES_ENERGY_YES_BALLISTIC_YES_MISSILE_YES_DEFENSE,
                    0, new Random());

            if (newSkill == null || stats.hasSkill(newSkill)) {
                break; // no skill to grant; stop, leave xp banked at/above threshold
            }

            xp -= threshold;
            String skillName = Global.getSettings().getSkillSpec(newSkill).getName();
            Global.getSector().getCampaignUI().addMessage(
                    "Pilot, callsign \"" + callsign + "\" learned " + skillName + "!",
                    Misc.getHighlightColor());
            stats.increaseSkill(newSkill);
            stats.setLevel(stats.getLevel() + 1);

            level = stats.getLevel();
            threshold = XP_BASE * (level + 1);
        }

        // If we've reached the cap, dump any leftover overflow it can't be spent
        // (at-cap pilots progress via the RNG elite path, not XP).
        if (level >= levelCap) {
            xp = 0f;
        }

        Global.getSector().getPersistentData().put(xpKey, xp);
        Global.getSector().getPersistentData().put(
                "armaa_wingCommander_wingman_" + j + "_" + captainId, pilot);
    }

    private void tryEliteUpgrade(PersonAPI pilot, int j, String callsign, String captainId) {
        if (pilot.getRelToPlayer().getRel() < 0.50f) {
            return;
        }

        MutableCharacterStatsAPI playerStats = Global.getSector().getPlayerFleet()
                .getFleetData().getCommander().getStats();
        int maxElite = (int) Global.getSettings().getInt("officerMaxEliteSkills")
                + (int) playerStats.getDynamic().getMod(Stats.OFFICER_MAX_ELITE_SKILLS_MOD).computeEffective(0);
        if (Misc.getNumEliteSkills(pilot) >= maxElite) {
            return;
        }
        if (Math.random() > 0.05f * numEngagements) {
            return;
        }

        MutableCharacterStatsAPI stats = pilot.getStats();
        for (MutableCharacterStatsAPI.SkillLevelAPI skill : stats.getSkillsCopy()) {
            if (!armaa_wingCommander.VALID_SKILLS.contains(skill.getSkill().getId())) {
                continue;
            }
            if ((int) stats.getSkillLevel(skill.getSkill().getId()) == 1) {
                Global.getSector().getCampaignUI().addMessage(
                        "Pilot, callsign \"" + callsign + "\"'s aptitude in "
                        + skill.getSkill().getName() + " increased to Elite!",
                        Misc.getHighlightColor());
                stats.increaseSkill(skill.getSkill().getId());
                Global.getSector().getPersistentData().put(
                        "armaa_wingCommander_wingman_" + j + "_" + captainId, pilot);
                break;
            }
        }
    }

    private void clearBattleState() {
        batResult = null;
        numEngagements = 0;
        if (damageThisBattle != null) {
            damageThisBattle.clear();
        }
    }
}
