package data.hullmods;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.*;
import com.fs.starfarer.api.combat.FighterWingAPI.ReturningFighter;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import org.lazywizard.lazylib.MathUtils;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import data.scripts.ai.armaa_combat_docking_AI_fighter;
import data.scripts.ai.armaa_combat_retreat_AI_fighter;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;
import java.util.*;
import java.awt.Color;
import java.util.Random;
import com.fs.starfarer.api.ui.Alignment;
import data.scripts.util.armaa_utils;
import data.scripts.util.armaa_pilotTracker;
import data.scripts.util.armaa_pilotTrackerNP;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent;
import data.scripts.MechaModPlugin;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import com.fs.starfarer.api.impl.campaign.ids.Voices;
import com.fs.starfarer.api.impl.campaign.ids.Skills;

import org.magiclib.util.MagicIncompatibleHullmods;

import com.fs.starfarer.api.util.IntervalUtil;

public class armaa_wingCommander extends BaseHullMod {

    private static final float RETREAT_AREA_SIZE = 2000f;
    private static final Map<HullSize, Float> ENGAGEMENT_REDUCTION = new HashMap<>();
    private static final int BOMBER_COST_MOD = 10000;
    private static final float FIGHTER_REPLACEMENT_TIME_MULT = .70f;
    private static final float FIGHTER_RATE = 1.25f;
    private static final float CREW_LOSS_MULT = 0.25f;
    private static final String MOD_ID = "armaa_wingCommander";
    private static final String SQUAD_TICKER_KEY = "armaa_wingCommander_squadTicker";
    private static final String FLIGHT_TICKER_KEY = "armaa_wingCommander_flightTicker";
    private static final String INITIAL_FILL_KEY = "armaa_wingCommander_initialFillDone";
    private static final int MODULE_DEPTH_LIMIT = 4;

    public static final List<String> VALID_SKILLS;

    static {
        List<String> s = new ArrayList<String>();
        s.add(Skills.COMBAT_ENDURANCE);
        s.add(Skills.HELMSMANSHIP);
        s.add(Skills.ENERGY_WEAPON_MASTERY);
        s.add(Skills.BALLISTIC_MASTERY);
        s.add(Skills.FIELD_MODULATION);
        s.add(Skills.TARGET_ANALYSIS);
        s.add(Skills.IMPACT_MITIGATION);
        s.add(Skills.DAMAGE_CONTROL);
        s.add(Skills.POLARIZED_ARMOR);
        s.add(Skills.POINT_DEFENSE);
        s.add(Skills.MISSILE_SPECIALIZATION);
        s.add(Skills.SYSTEMS_EXPERTISE);
        VALID_SKILLS = Collections.unmodifiableList(s);
    }

    private static String st = "st";
    private static String nd = "nd";
    private static String rd = "rd";
    private static String th = "th";

    private final WeightedRandomPicker<String> voices = new WeightedRandomPicker<>();
    private static final Set<String> BLOCKED_HULLMODS = new HashSet<>();
    public static float FIGHTER_OP_PER_DP = 5;
    public static int MIN_DP = 1;

    static {
        ENGAGEMENT_REDUCTION.put(HullSize.FIGHTER, 0.5f);
        ENGAGEMENT_REDUCTION.put(HullSize.FRIGATE, 0.5f);
        ENGAGEMENT_REDUCTION.put(HullSize.DESTROYER, 0.15f);
        ENGAGEMENT_REDUCTION.put(HullSize.CRUISER, 0.15f);
        ENGAGEMENT_REDUCTION.put(HullSize.CAPITAL_SHIP, 0.15f);
    }

    {
        voices.add(Voices.SOLDIER, 5);
        voices.add(Voices.SPACER, 10);
        voices.add(Voices.FAITHFUL, 3);
        voices.add(Voices.VILLAIN, 4);
    }

    private final Map<String, List<String>> VOICE_DIALG = new HashMap<>();

    {
        VOICE_DIALG.put(Voices.SOLDIER, MechaModPlugin.squadChatter_soldier);
        VOICE_DIALG.put(Voices.VILLAIN, MechaModPlugin.squadChatter_villain);
    }

    // =====================================================================
    // Module-group helpers
    // =====================================================================
    /**
     * Walks up to the top-level hull of a module group. Returns the ship itself
     * if it is not attached to anything. Guarded against malformed cycles.
     */
    public static ShipAPI getRoot(ShipAPI ship) {
        if (ship == null) {
            return null;
        }
        ShipAPI cur = ship;
        for (int guard = 0; cur.getParentStation() != null && guard < 8; guard++) {
            cur = cur.getParentStation();
        }
        return cur;
    }

    /**
     * Modules normally inherit the parent's captain. This is the safety net for
     * when they report null or default instead.
     */
    public static PersonAPI resolveCaptain(ShipAPI ship) {
        if (ship == null) {
            return null;
        }
        PersonAPI cap = ship.getCaptain();
        if (cap != null && !cap.isDefault()) {
            return cap;
        }
        ShipAPI root = getRoot(ship);
        if (root != null && root != ship) {
            PersonAPI rootCap = root.getCaptain();
            if (rootCap != null && !rootCap.isDefault()) {
                return rootCap;
            }
        }
        return cap;
    }

    /**
     * Built-in mods do not reliably show up in variant.hasHullMod(), so check
     * all three sources.
     */
    public static boolean hasWingCom(ShipVariantAPI v) {
        if (v == null) {
            return false;
        }
        if (v.hasHullMod(MOD_ID) || v.getPermaMods().contains(MOD_ID)) {
            return true;
        }
        return v.getHullSpec() != null && v.getHullSpec().getBuiltInMods().contains(MOD_ID);
    }

    /**
     * True if this variant or any module beneath it carries WINGCOM. Use this
     * for campaign-side filtering (intel lists, fleet member scans) where the
     * fleet member's own variant may be clean but a module hosts the wings.
     */
    public static boolean hasWingComInGroup(ShipVariantAPI v) {
        return hasWingComInGroup(v, 0);
    }

    private static boolean hasWingComInGroup(ShipVariantAPI v, int depth) {
        if (v == null || depth > MODULE_DEPTH_LIMIT) {
            return false;
        }
        if (hasWingCom(v)) {
            return true;
        }
        for (String slot : v.getModuleSlots()) {
            if (hasWingComInGroup(v.getModuleVariant(slot), depth + 1)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Flat, ordered list of every crewed wing spec on WINGCOM hulls in the
     * group. Order is: this variant's own wings first, then module slots in
     * declaration order, recursively. Callers that map a pilot index onto a
     * wing should use this rather than variant.getWings() directly.
     */
    public static List<FighterWingSpecAPI> getCrewedWingsInGroup(ShipVariantAPI v) {
        List<FighterWingSpecAPI> out = new ArrayList<FighterWingSpecAPI>();
        collectCrewedWings(v, out, 0);
        return out;
    }

    private static void collectCrewedWings(ShipVariantAPI v, List<FighterWingSpecAPI> out, int depth) {
        if (v == null || depth > MODULE_DEPTH_LIMIT) {
            return;
        }
        if (hasWingCom(v)) {
            for (int i = 0; i < v.getWings().size(); i++) {
                FighterWingSpecAPI w = v.getWing(i);
                if (w == null) {
                    continue;
                }
                if (w.getVariant().getHullSpec().getMinCrew() <= 0) {
                    continue;
                }
                out.add(w);
            }
        }
        for (String slot : v.getModuleSlots()) {
            collectCrewedWings(v.getModuleVariant(slot), out, depth + 1);
        }
    }

    /**
     * True only for genuine battlestations. Mobile hulls that happen to use the
     * module system (Bakraid and friends) return false, so their modules run
     * the full strikecraft flight path.
     */
    private static boolean isTrueStation(ShipAPI root) {
        if (root == null) {
            return false;
        }
        if (root.isStation()) {
            return true;
        }
        return root.getHullSpec() != null && root.getHullSpec().hasTag("station");
    }

    /**
     * Only player-owned, non-ally hulls in an actual campaign battle write to
     * sector persistent data.
     */
    private boolean canPersist(ShipAPI ship) {
        if (Global.getCombatEngine() == null || !Global.getCombatEngine().isInCampaign()) {
            return false;
        }
        if (Global.getSector() == null) {
            return false;
        }
        return ship.getOwner() == 0 && !ship.isAlly();
    }

    /**
     * Per-ship interval, stored in ship custom data. Deliberately not an
     * instance field: one hullmod object is shared by every hull carrying the
     * mod, so instance state leaks across ships.
     */
    private static IntervalUtil getSquadTicker(ShipAPI ship) {
        Object o = ship.getCustomData().get(SQUAD_TICKER_KEY);
        if (o instanceof IntervalUtil) {
            return (IntervalUtil) o;
        }
        IntervalUtil iv = new IntervalUtil(0.4f, 0.8f);
        ship.setCustomData(SQUAD_TICKER_KEY, iv);
        return iv;
    }

    /**
     * Per-ship interval for the strikecraft flight / spare-chassis block.
     *
     * This MUST NOT be an instance field. One hullmod object is shared by every
     * hull carrying the mod, so a shared IntervalUtil gets advanced once per
     * participating ship per frame and its elapsed flag is read by all of them.
     * That makes setFastReplacements fire at a multiple of the intended rate,
     * which reads in-game as fighters respawning instantly.
     */
    private static IntervalUtil getFlightTicker(ShipAPI ship) {
        Object o = ship.getCustomData().get(FLIGHT_TICKER_KEY);
        if (o instanceof IntervalUtil) {
            return (IntervalUtil) o;
        }
        IntervalUtil iv = new IntervalUtil(0.5f, 1.0f);
        ship.setCustomData(FLIGHT_TICKER_KEY, iv);
        return iv;
    }

    // =====================================================================
    public static int computeDPModifier(float fighterOPCost) {
        int mod = (int) Math.ceil(fighterOPCost / FIGHTER_OP_PER_DP);
        if (mod < MIN_DP) {
            mod = MIN_DP;
        }
        return mod;
    }

    public static float getFighterOPCost(MutableShipStatsAPI stats) {
        float cost = 0;
        for (String wingId : getFighterWings(stats)) {
            FighterWingSpecAPI spec = Global.getSettings().getFighterWingSpec(wingId);
            cost += spec.getOpCost(stats);
        }
        return cost;
    }

    public static List<String> getFighterWings(MutableShipStatsAPI stats) {
        if (stats.getVariant() != null) {
            int baseBays = (int) Math.round(stats.getNumFighterBays().getBaseValue());
            if (baseBays <= 0) {
                return stats.getVariant().getFittedWings();
            } else {
                List<String> result = new ArrayList<>();
                for (String wingId : stats.getVariant().getFittedWings()) {
                    if (baseBays > 0) {
                        baseBays--;
                        continue;
                    }
                    result.add(wingId);
                }
                return result;
            }
        }
        return new ArrayList<String>();
    }

    public float computeCRMult(float suppliesPerDep, float dpMod) {
        return 1f + dpMod / suppliesPerDep;
    }

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        boolean builtIn = stats.getVariant().getPermaMods().contains("armaa_wingCommander");
        if (builtIn) {
            float dpMod = computeDPModifier(getFighterOPCost(stats));
            if (dpMod > 0) {
                stats.getDynamic().getMod(Stats.DEPLOYMENT_POINTS_MOD).modifyFlat(id, dpMod);
                if (stats.getFleetMember() != null) {
                    float perDep = stats.getFleetMember().getHullSpec().getSuppliesToRecover();
                    float mult = computeCRMult(perDep, dpMod);
                    stats.getCRPerDeploymentPercent().modifyMult(id, mult);
                }
                stats.getSuppliesToRecover().modifyFlat(id, dpMod);
            }
        }
        if (stats.getVariant().getHullSpec().getFighterBays() == 0 && stats.getVariant().getHullSpec().getHullSize() == HullSize.FRIGATE) {

            if (stats.getNumFighterBays().isUnmodified()) {
                stats.getNumFighterBays().modifyFlat(id, 1f);
            }
        }
        stats.getFighterWingRange().modifyMult(id, 1f - ENGAGEMENT_REDUCTION.get(hullSize));
        stats.getDynamic().getStat(Stats.REPLACEMENT_RATE_DECREASE_MULT).modifyMult(id, 1f + FIGHTER_REPLACEMENT_TIME_MULT);
        stats.getFighterRefitTimeMult().modifyMult(id, FIGHTER_RATE);
        stats.getDynamic().getStat(Stats.FIGHTER_CREW_LOSS_MULT).modifyMult(id, CREW_LOSS_MULT);
        stats.getFighterRefitTimeMult().unmodify("wingcombonus");

    }

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, java.lang.String id) {
        for (String tmp : BLOCKED_HULLMODS) {
            if (ship.getVariant().getHullMods().contains(tmp)) {
                MagicIncompatibleHullmods.removeHullmodWithWarning(ship.getVariant(), tmp, "cataphract");
            }
        }
        PersonAPI cap = resolveCaptain(ship);
        if (cap != null && !cap.isDefault() && getWingSize(ship) > 0) {
            if (hasSquad(cap, true)) {
                createPilots(cap, ship, true);
            }
        }
    }

    @Override
    public boolean affectsOPCosts() {
        return true;
    }

    private static boolean hasBays(ShipAPI ship) {
        return ship.getHullSpec().getFighterBays() > 0
                || ship.getMutableStats().getNumFighterBays().isPositive();
    }

@Override
public boolean isApplicableToShip(ShipAPI ship) {
    if (ship == null || ship.getVariant() == null) {
        return false;
    }
    if (!ship.isFrigate()) {
        return hasBays(ship);
    }
    // Strikecraft frigates: WINGCOM grants the bay itself, so don't demand one.
    if (ship.getVariant().hasHullMod("strikeCraft")) {
        return true;
    }
    return hasBays(ship) && ship.getHullSpec() != null
            && ship.getHullSpec().getHints().contains(ShipHullSpecAPI.ShipTypeHints.MODULE);
}

    @Override
    public String getUnapplicableReason(ShipAPI ship) {
        if (ship == null) {
            return "Can not be assigned";
        }
        if (!hasBays(ship)) {
            return "Requires fighter bays";
        }
        // Only remaining failure is a bayed frigate that is neither strikecraft nor a module.
        return "Frigate-sized hulls must be strikecraft, or a module of a larger hull";
    }

    private final Color HL = Global.getSettings().getColor("hColor");
    private final Color TT = Global.getSettings().getColor("buttonBgDark");
    private final Color F = Global.getSettings().getColor("textFriendColor");
    private final Color E = Global.getSettings().getColor("textEnemyColor");
    private final Color def = Global.getSettings().getColor("standardTextColor");

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize,
            ShipAPI ship, float width, boolean isForModSpec) {
        float pad = 10f;
        float padS = 2f;
        Color[] arr = {Misc.getHighlightColor(), F};
        Color[] arrB = {Misc.getHighlightColor(), F, F};
        Color[] arr2 = {Misc.getHighlightColor(), E};

        tooltip.addSectionHeading("Details", Alignment.MID, 10);
        tooltip.addPara("%s " + "Assigning an officer to this vessel %s.", pad, arr,
                "\u2022", "establishes a squadron that gains skills over time");
        tooltip.addPara("%s " + "Fighter crew losses are reduced by %s.", pad, arrB,
                "\u2022", (int) ((1f - CREW_LOSS_MULT) * 100f) + "%");
        tooltip.addPara("%s " + "Only applicable with %s fighters.", pad, arrB, "\u2022", "crewed");
        tooltip.addPara("%s " + "On hulls with the %s hullmod, increases wing size by %s.", pad, arrB, "\u2022", "Spare chassis", "1");
        if (ship == null) {
            tooltip.addPara("%s " + "Fighter engagement range decreased by %s.", pad, arr2,
                    "\u2022", "70/60/50/40" + " percent");
        } else {
            tooltip.addPara("%s " + "Fighter engagement range decreased by %s.", pad, arr2,
                    "\u2022", (int) (ENGAGEMENT_REDUCTION.get(ship.getVariant().getHullSize()) * 100f) + "%");
            if (ship.getVariant().getHullSpec().getFighterBays() == 0) {
                tooltip.addPara("%s " + "If frigate, or no built-in bays: Replacement rate consumption increased by %s.",
                        padS, arr2, "\u2022", (int) Math.round(FIGHTER_REPLACEMENT_TIME_MULT * 100f) + "%");
                tooltip.addPara("%s " + "Refit time increased by %s.", padS, arr2,
                        "\u2022", (FIGHTER_RATE - 1f) * 100 + "%");
                tooltip.addPara("%s " + "Strikecraft %s.", padS, Misc.getHighlightColor(),
                        "\u2022", "enter combat from deployment zone, or carrier landed at for refit");
                tooltip.addPara("%s " + "If no carriers are present, %s.", padS, Misc.getHighlightColor(),
                        "\u2022", "fighters in need of refit will attempt to exit the combat zone");
            }
        }
        if (ship == null) {
            return;
        }

        tooltip.addSectionHeading("=== S Q U A D R O N   I N F O ===", Alignment.MID, 10);

        // Resolve once. A module can report a null captain here and the old
        // code dereferenced it unguarded.
        PersonAPI tipCaptain = resolveCaptain(ship);

        FighterWingSpecAPI wing = ship.getVariant().getWing(0);
        int wingSize = getWingSize(ship);

        boolean inCampaign = Global.getCombatEngine() != null
                && Global.getCombatEngine().isInCampaign()
                && Global.getSector() != null
                && Global.getSector().getPlayerFleet() != null;
        boolean crewStarved = inCampaign
                && Global.getSector().getPlayerFleet().getCargo().getCrew() - 1 <= wingSize;

        if (wing == null && wingSize == 0) {
            tooltip.addPara("No wing assigned.", 10, Misc.getHighlightColor());
        } else if (wingSize == 0) {
            tooltip.addPara("Wing is automated. No pilots assigned.", 10, Misc.getHighlightColor());
        } else if (crewStarved) {
            tooltip.addPara("No crew can be spared to assign to this wing.", 10, Misc.getHighlightColor());
        } else if (tipCaptain == null || tipCaptain.isDefault()) {
            tooltip.addPara(
                    "The wing lead by this unit is of no real note. Assign an officer to establish a squadron.",
                    10, Misc.getHighlightColor());
        } else {
            String squadName = "";
            if (hasSquad(tipCaptain, true)) {
                squadName = (String) Global.getSector().getPersistentData()
                        .get("armaa_wingCommander_squadronName_" + tipCaptain.getId());
            } else {
                createSquad(tipCaptain);
                squadName = (String) Global.getSector().getPersistentData()
                        .get("armaa_wingCommander_squadronName_" + tipCaptain.getId());
            }

            tooltip.addPara(
                    "The " + squadName + " has been established under the command of "
                    + tipCaptain.getNameString()
                    + ". If this officer is assigned to another unit with WINGCOM, they will follow.",
                    10, HL, squadName, tipCaptain.getNameString());

            int solidaritySize = armaa_utils.getBaseWingSize(ship);
            float squadLevel = 0;
            String captainId = tipCaptain.getId();
            for (int i = 0; i < solidaritySize; i++) {
                Object p = Global.getSector().getPersistentData()
                        .get("armaa_wingCommander_wingman_" + i + "_" + captainId);
                if (p instanceof PersonAPI) {
                    squadLevel += ((PersonAPI) p).getRelToPlayer().getRel();
                }
            }
            if (solidaritySize > 0) {
                squadLevel /= solidaritySize;
            }

            tooltip.addPara("Unit solidarity is at %s, increasing fighter defensive capabilities "
                    + "by %s and offensive by %s. ", pad, F,
                    (int) (squadLevel * 100) + "%",
                    (int) (Math.min((squadLevel * 100) * 0.30, 0.30 * 100)) + "%",
                    (int) (Math.min(squadLevel * 100 * 0.30, 0.30 * 100)) + "%");

            createPilots(tipCaptain, ship, true);
        }
    }

    @Override
    public void advanceInCampaign(FleetMemberAPI member, float amount) {
        if (member.getFleetData() == null || member.getFleetData().getFleet() == null) {
            return;
        }
        if (!member.getFleetData().getFleet().isPlayerFleet()) {
            return;
        }
        if (member.getCaptain() != null) {
            if (!member.getCaptain().isDefault() && getWingSize(member.getVariant()) > 0) {
                if (!hasSquad(member.getCaptain(), true)) {
                    createSquad(member.getCaptain());
                }
            }
        }
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {

        boolean firstPass = !(ship.getCustomData().get(SQUAD_TICKER_KEY) instanceof IntervalUtil);
        IntervalUtil squadTicker = getSquadTicker(ship);
        squadTicker.advance(amount);
        if (firstPass || squadTicker.intervalElapsed()) {
            PersonAPI cap = resolveCaptain(ship);
            if (cap != null && !cap.isDefault() && canPersist(ship) && getWingSize(ship) > 0) {
                if (!hasSquad(cap, true)) {
                    createSquad(cap);
                }
                createPilots(cap, ship, true);
            }
        }


        if (ship.getLaunchBaysCopy().isEmpty()) {
            return;
        }
        // Genuine battlestation modules opt out. Modules of a mobile hull
        // (ejectable cores etc.) run the full path.
        if (ship.isStationModule() && ship.getStationSlot() != null
                && isTrueStation(getRoot(ship))) {
            return;
        }
        if (ship.getVariant().hasHullMod("converted_hangar")) {
            return;
        }
        if ((ship.getHullSpec().getFighterBays() > 0 && !ship.isFrigate() && !ship.isFighter())
                || ship.getHullSpec().hasTag("strikecraft_with_bays")) {
            return;
        }

        // Advance once, at the top, before anything reads it. The original
        // advanced it further down and only inside the "wing has members"
        // branch, so the spare-chassis check below could read an interval that
        // had not moved in many frames.
        IntervalUtil flightTicker = getFlightTicker(ship);
        flightTicker.advance(amount);

        if (flightTicker.intervalElapsed()) {
            if (ship.getHullSpec().getBuiltInMods().contains("armaa_spare_chassis")) {
                // The extra chassis is meant to appear immediately on
                // deployment, once. The old guard
                // (getPercentStatMod("wingcombonus") == null) was permanently
                // true: applyEffectsBeforeShipCreation unmodifies that stat and
                // the block meant to set it was unreachable, since allDeployed
                // and ranOnce were per-frame locals. So setFastReplacements
                // fired every interval, which reads in-game as fighters being
                // replaced instantly.
                boolean alreadyFilled = Boolean.TRUE.equals(ship.getCustomData().get(INITIAL_FILL_KEY));
                boolean sawWing = false;

                for (FighterLaunchBayAPI bay : ship.getLaunchBaysCopy()) {
                    FighterWingAPI wing = bay.getWing();
                    if (wing == null) {
                        continue;
                    }
                    sawWing = true;

                    FighterWingSpecAPI wingSpec = wing.getSpec();
                    int deployed = wing.getWingMembers().size();
                    int maxTotal = wingSpec.getNumFighters() + 1;
                    int actualAdd = maxTotal - deployed;

                    if (actualAdd > 0) {
                        bay.setExtraDeployments(actualAdd);
                        bay.setExtraDeploymentLimit(maxTotal);
                        bay.setExtraDuration(9999999);
                    } else {
                        bay.setExtraDeployments(0);
                        bay.setExtraDeploymentLimit(0);
                        bay.setFastReplacements(0);
                    }

                    // actualAdd > 0 rather than != 0, so an over-capacity bay
                    // never gets a negative written into fastReplacements.
                    if (!alreadyFilled && actualAdd > 0) {
                        bay.setFastReplacements(actualAdd);
                    }
                }

                // Only latch once a wing actually existed to fill; on the first
                // tick of combat the bay can still be empty.
                if (!alreadyFilled && sawWing) {
                    ship.setCustomData(INITIAL_FILL_KEY, true);
                }
            }
        }
        FighterLaunchBayAPI bay = ship.getLaunchBaysCopy().get(0);

        if (ship.isLanding()) {
            ShipAPI defaultCarrier = getCarrier(ship);
            if (defaultCarrier != null) {
                Global.getCombatEngine().getCustomData()
                        .put("armaa_wingCommander_landingLocation_default" + ship.getId(), defaultCarrier);
            }
        }

        if (bay.getWing() != null && !bay.getWing().getWingMembers().isEmpty()) {
            final float mapHalfW = Global.getCombatEngine().getMapWidth() / 2f;
            final float mapHalfH = Global.getCombatEngine().getMapHeight() / 2f;
            final Vector2f rawLL = new Vector2f(-mapHalfW, -mapHalfH);
            final Vector2f rawUR = new Vector2f(mapHalfW, mapHalfH);

            final String shipId = ship.getId();

            List<ShipAPI> wingMembers = bay.getWing().getWingMembers();
            for (int i = 0; i < wingMembers.size(); i++) {
                ShipAPI fighter = wingMembers.get(i);
                if (fighter == null || fighter.isHulk()) {
                    continue;
                }

                //build the "set" key once, do single map lookup ---
                final String setKey = "armaa_wingCommander_landingLocation_" + fighter.getId() + "_set";
                Object setFlag = Global.getCombatEngine().getCustomData().get(setKey);
                boolean done = setFlag instanceof Boolean && (Boolean) setFlag;

                if (fighter.isLiftingOff() && !done) {
                    Vector2f landingLoc = null;

                    // build per-index key once ---
                    final String indexKey = "armaa_wingCommander_landingLocation_" + shipId + "_" + i;

                    ShipAPI potentialLaunchPoint = (ShipAPI) Global.getCombatEngine()
                            .getCustomData().get("armaa_wingCommander_landingLocation_default" + shipId);

                    Object indexedCarrier = Global.getCombatEngine().getCustomData().get(indexKey);
                    if (indexedCarrier instanceof ShipAPI) {
                        potentialLaunchPoint = (ShipAPI) indexedCarrier;
                    }

                    if (potentialLaunchPoint != null
                            && (!potentialLaunchPoint.isAlive() || potentialLaunchPoint.isHulk())) {
                        potentialLaunchPoint = null;
                    }
                    if (potentialLaunchPoint == null) {
                        potentialLaunchPoint = getCarrier(fighter);
                    }

                    if (potentialLaunchPoint != null
                            && Global.getCombatEngine().isEntityInPlay(potentialLaunchPoint)
                            && potentialLaunchPoint.isAlive()) {
                        for (FighterLaunchBayAPI wep : potentialLaunchPoint.getLaunchBaysCopy()) {
                            if (wep.getWeaponSlot() != null) {
                                WeaponSlotAPI w = wep.getWeaponSlot();
                                landingLoc = new Vector2f(
                                        potentialLaunchPoint.getLocation().x + w.getLocation().y,
                                        potentialLaunchPoint.getLocation().y + w.getLocation().x);
                                if (Math.random() <= .50f) {
                                    break;
                                }
                            }
                        }
                    }

                    if (landingLoc == null) {
                        if (potentialLaunchPoint != null
                                && potentialLaunchPoint.getLocation() != null
                                && potentialLaunchPoint.isAlive()) {
                            landingLoc = potentialLaunchPoint.getLocation();
                        } else {
                            if (fighter.getOwner() == 0) {
                                if (ship.getLocation().getY() > rawLL.getY() - RETREAT_AREA_SIZE) {
                                    landingLoc = new Vector2f(ship.getLocation().getX(),
                                            ship.getLocation().getY() - 2000);
                                } else {
                                    landingLoc = new Vector2f((rawLL.x + rawUR.x) / 2, rawLL.y);
                                }
                            } else {
                                landingLoc = new Vector2f((rawLL.x + rawUR.x) / 2, rawUR.y);
                            }
                        }
                    }

                    armaa_utils.setLocation(fighter, landingLoc);
                    Global.getCombatEngine().getCustomData().put(setKey, true);
                }
            }

            if (flightTicker.intervalElapsed()) {
                if (!bay.getWing().getReturning().isEmpty()) {
                    doLanding(bay, ship);
                }
            }
        }
    }

    public void assignPilotToFighters(int count, ShipAPI fighter, ShipAPI ship, boolean persistent) {
        // Crew check is a property of the fighter, not the loop index. hoist it.
        if (fighter.getWing() == null
                || fighter.getWing().getSpec().getVariant().getHullSpec().getMinCrew() <= 0) {
            return;
        }
        // Orphaned wing: a combo unit's frame has its native wing's source
        // deliberately nulled so it stops rendering in the bay HUD. Its bay
        // keeps launching, so replacements land here. They must not consume a
        // roster slot or get a tracker, both of which key off the source ship.
        if (fighter.getWing().getSourceShip() == null) {
            return;
        }

        final PersonAPI commander = resolveCaptain(ship);
        if (commander == null || commander.isDefault()) {
            return;
        }
        final String captainId = commander.getId();
        // Group-wide, not per-wing: parent- and module-launched fighters must
        // share one denominator or the roster past the first wing's size never
        // counts toward anyone's solidarity.
        final int baseSize = getWingSize(ship);

        // --- PART 1: Solidarity. core roster only (indices 0..baseSize-1).
        // RD spares on the bench don't factor into the average.
        float squadLevel = 0;
        int solidarityCount = Math.min(count, baseSize);
        for (int j = 0; j < solidarityCount; j++) {
            final String pilotKey = "armaa_wingCommander_wingman_" + j + "_" + captainId;
            Object pilotObj = persistent
                    ? Global.getSector().getPersistentData().get(pilotKey)
                    : Global.getCombatEngine().getCustomData().get(pilotKey);
            if (!(pilotObj instanceof PersonAPI)) {
                continue;
            }
            PersonAPI p = (PersonAPI) pilotObj;
            if (persistent) {
                squadLevel += p.getRelToPlayer().getRel();
            } else {
                squadLevel += (p.getStats().getLevel()
                        / commander.getStats().getLevel()) * .3f;
            }
        }
        if (baseSize > 0) {
            squadLevel /= baseSize;
        }

        //Assignment
        for (int j = 0; j < count; j++) {
            final String pilotKey = "armaa_wingCommander_wingman_" + j + "_" + captainId;
            final String assignedKey = "armaa_wingCommander_wingman_" + j + "_wasAssigned_" + captainId;
            final String callsignKey = "armaa_wingCommander_wingman_" + j + "_callsign_" + captainId;

            Object pilotObj = persistent
                    ? Global.getSector().getPersistentData().get(pilotKey)
                    : Global.getCombatEngine().getCustomData().get(pilotKey);
            if (!(pilotObj instanceof PersonAPI)) {
                continue;
            }

            Object assignedFlag = Global.getCombatEngine().getCustomData().get(assignedKey);
            boolean wasAssigned = assignedFlag instanceof Boolean && (Boolean) assignedFlag;
            if (wasAssigned) {
                continue;
            }

            PersonAPI pilot = (PersonAPI) pilotObj;
            String callsign;
            fighter.setCaptain(pilot);
            Global.getCombatEngine().getCustomData().put(assignedKey, true);
            if (persistent) {
                callsign = (String) Global.getSector().getPersistentData().get(callsignKey);
                Global.getCombatEngine().addPlugin(new armaa_pilotTracker(fighter, callsign, j));
            } else {
                callsign = (String) Global.getCombatEngine().getCustomData().get(callsignKey);
                Global.getCombatEngine().addPlugin(new armaa_pilotTrackerNP(fighter, callsign, j));
            }
            break;
        }

        // Buffs. every fighter in the wing gets the same core-derived
        // solidarity. Spares benefit from it without contributing to it.
        if (fighter.getCaptain() == null || fighter.getCaptain().isDefault()) {
            return;
        }

        float mult = Math.min(squadLevel, 0.15f);
        MutableShipStatsAPI stats = fighter.getMutableStats();

        stats.getHullDamageTakenMult().modifyMult("armaa_wingCommander", (1f - mult));
        stats.getArmorDamageTakenMult().modifyMult("armaa_wingCommander", (1f - mult));
        stats.getEmpDamageTakenMult().modifyMult("armaa_wingCommander", (1f - mult));
        stats.getShieldDamageTakenMult().modifyMult("armaa_wingCommander", (1f - mult));

        float speedMult = Math.min(squadLevel * 0.67f, 0.10f);
        stats.getMaxSpeed().modifyPercent("armaa_wingCommander", speedMult * 100);

        if (squadLevel >= 0.50f) {
            stats.getBallisticRoFMult().modifyMult("armaa_wingCommander", 1f + mult);
            stats.getEnergyRoFMult().modifyMult("armaa_wingCommander", 1f + mult);
        }
    }

    public ShipAPI getCarrier(ShipAPI ship) {
        boolean isFakeFighter = ship.getVariant().getHullSize() == HullSize.FRIGATE;

        if (!isFakeFighter && ship.getWing() != null && ship.getWing().getSourceShip() != null) {
            // Normal fighter: look close to the source ship first.
            ShipAPI source = ship.getWing().getSourceShip();
            Vector2f sourceLoc = source.getLocation();
            ShipAPI best = null;
            float bestDist = Float.MAX_VALUE;

            // Tight pass: 3000 units around source.  Covers the vast majority of cases.
            for (ShipAPI carrier : CombatUtils.getShipsWithinRange(sourceLoc, 3000f)) {
                if (!isValidCarrier(carrier, ship)) {
                    continue;
                }
                float d = MathUtils.getDistance(carrier, source);
                if (d < bestDist) {
                    bestDist = d;
                    best = carrier;
                }
            }
            if (best != null) {
                return best;
            }

            // Wide fallback: only reached if no carrier was near the source.
            for (ShipAPI carrier : CombatUtils.getShipsWithinRange(sourceLoc, 8000f)) {
                if (!isValidCarrier(carrier, ship)) {
                    continue;
                }
                float d = MathUtils.getDistance(carrier, source);
                if (d < bestDist) {
                    bestDist = d;
                    best = carrier;
                }
            }
            return best;
        }

        // Fake-fighter / station path: return the first valid carrier found.
        for (ShipAPI carrier : CombatUtils.getShipsWithinRange(ship.getLocation(), 8000f)) {
            if (!isValidCarrier(carrier, ship)) {
                continue;
            }
            if (!carrier.isFrigate() || carrier.isStationModule()) {
                if (carrier.hasLaunchBays()) {
                    return carrier;
                }
            }
        }
        return null;
    }

    // Extracted predicate so getCarrier stays readable.
    private boolean isValidCarrier(ShipAPI carrier, ShipAPI ship) {
        if (carrier.getOwner() != ship.getOwner()) {
            return false;
        }
        if (!carrier.isAlive() || carrier.isHulk()) {
            return false;
        }
        if (carrier.isFrigate() && carrier.getVariant().getNonBuiltInWings().size() <= 1) {
            return false;
        }
        if ((carrier.isFighter() && !carrier.getHullSpec().hasTag("strikecraft_with_bays"))
                || carrier.getHullSpec().getFighterBays() < 1) {
            return false;
        }
        return true;
    }

    /**
     * Group-wide wing size. Resolves to the root of the module group and walks
     * down, so every hull in the group reports the same number regardless of
     * which one is asking. That is what lets parent and module share one
     * captain-keyed roster without fighting over its size.
     */
    public int getWingSize(ShipAPI ship) {
        return countWings(getRoot(ship), 0);
    }

    private int countWings(ShipAPI hull, int depth) {
        if (hull == null || depth > MODULE_DEPTH_LIMIT) {
            return 0;
        }
        int size = 0;

        // Counting is gated on WINGCOM. The descent below deliberately is not:
        // a WINGCOM core sitting under a plain structural module must still be
        // reached.
        if (hasWingCom(hull.getVariant())) {
            boolean countedFromBays = false;
            for (FighterLaunchBayAPI bay : hull.getLaunchBaysCopy()) {
                FighterWingAPI w = bay.getWing();
                if (w == null) {
                    continue;
                }
                if (w.getSpec().getVariant().getHullSpec().getMinCrew() <= 0) {
                    continue;
                }
                size += Math.max(w.getSpec().getNumFighters(), bay.getExtraDeploymentLimit());
                countedFromBays = true;
            }
            if (!countedFromBays) {
                // Refit / pre-deployment: no live bays, read variant specs.
                size += getWingSize(hull.getVariant());
            }
        }

        List<ShipAPI> children = hull.getChildModulesCopy();
        if (children != null && !children.isEmpty()) {
            for (ShipAPI child : children) {
                size += countWings(child, depth + 1);
            }
        } else {
            // Refit screen: child modules are not instantiated. Walk the
            // variant's module slots instead. Never both, or we double count.
            size += countVariantModules(hull.getVariant(), depth);
        }
        return size;
    }

    private int countVariantModules(ShipVariantAPI variant, int depth) {
        if (variant == null || depth > MODULE_DEPTH_LIMIT) {
            return 0;
        }
        int size = 0;
        for (String slot : variant.getModuleSlots()) {
            ShipVariantAPI mv = variant.getModuleVariant(slot);
            if (mv == null) {
                continue;
            }
            if (hasWingCom(mv)) {
                size += getWingSize(mv);
            }
            size += countVariantModules(mv, depth + 1);
        }
        return size;
    }

    /**
     * Single-variant wing size. Intentionally does NOT walk module slots:
     * countVariantModules relies on that to avoid double counting, and
     * advanceInCampaign only ever sees the fleet member's own variant.
     */
    public int getWingSize(ShipVariantAPI ship) {
        FighterWingSpecAPI wing = ship.getWing(0);
        int wingSize = 0;
        boolean crewedWing = false;
        if (wing != null) {
            for (int i = 0; i < ship.getWings().size(); i++) {
                FighterWingSpecAPI w = ship.getWing(i);
                if (w == null) {
                    continue;
                }
                crewedWing = w.getVariant().getHullSpec().getMinCrew() > 0;
                if (crewedWing) {
                    wingSize += w.getNumFighters();
                }
            }
        }
        return wingSize;
    }

    public void createPilots(PersonAPI commander, ShipAPI ship, boolean persistent) {
        if (commander == null || commander.isDefault()) {
            return;
        }
        int size = getWingSize(ship);
        // hoist commander ID ---
        final String cmdId = commander.getId();
        final String sizeKey = "armaa_wingCommander_squadSize_" + cmdId;
        Object existingSize;
        if (persistent) {
            existingSize = Global.getSector().getPersistentData().get(sizeKey);
        } else {
            existingSize = Global.getCombatEngine().getCustomData().get(sizeKey);
        }
        if (existingSize instanceof Integer) {
            int currentSize = (Integer) existingSize;
            if (size <= currentSize || size == 0) {
                return;
            }
        }

        for (int i = 0; i < size; i++) {
            PersonAPI pilot = null;
            String callsign;
            final String pilotKey = "armaa_wingCommander_wingman_" + i + "_" + cmdId;
            final String callsignKey = "armaa_wingCommander_wingman_" + i + "_callsign_" + cmdId;

            if (persistent) {
                Object p = Global.getSector().getPersistentData().get(pilotKey);
                if (p instanceof PersonAPI) {
                    pilot = (PersonAPI) p;
                    callsign = (String) Global.getSector().getPersistentData().get(callsignKey);
                }
            } else {
                Object p = Global.getCombatEngine().getCustomData().get(pilotKey);
                if (p instanceof PersonAPI) {
                    pilot = (PersonAPI) p;
                    callsign = (String) Global.getCombatEngine().getCustomData().get(callsignKey);
                }
            }

            if (pilot == null) {
                int level = MathUtils.getRandomNumberInRange(0, 1);
                if (!persistent) {
                    level = MathUtils.getRandomNumberInRange(0, 2);
                }

                pilot = OfficerManagerEvent.createOfficer(commander.getFaction(), level, true);
                callsign = OfficerManagerEvent.createOfficer(commander.getFaction(), 1, true)
                        .getName().getLast();
                pilot.setVoice(voices.pick());
                if ((float) Math.random() < .20f) {
                    pilot.addTag("armaa_latentTalent");
                }

                if (persistent) {
                    Global.getSector().getPersistentData().put(pilotKey, pilot);
                    Global.getSector().getPersistentData().put(callsignKey, callsign);
                } else {
                    Global.getCombatEngine().getCustomData().put(pilotKey, pilot);
                    Global.getCombatEngine().getCustomData().put(callsignKey, callsign);
                }
            }
        }

        if (persistent) {
            Global.getSector().getPersistentData().put(sizeKey, size);
        } else {
            Global.getCombatEngine().getCustomData().put(sizeKey, size);
        }
    }

    public void createSquad(PersonAPI commander) {
        if (commander == null || commander.isDefault()) {
            return;
        }
        String squadName = getSquadName();
        Global.getSector().getPersistentData()
                .put("armaa_wingCommander_squadronName_" + commander.getId(), squadName);
    }

    public boolean hasSquad(PersonAPI commander, boolean persistent) {
        if (commander == null) {
            return false;
        }
        if (persistent) {
            if (Global.getSector() == null) {
                return false;
            }
            return Global.getSector().getPersistentData()
                    .get("armaa_wingCommander_squadronName_" + commander.getId()) instanceof String;
        }
        return Global.getCombatEngine().getCustomData()
                .get("armaa_wingCommander_squadSize_" + commander.getId()) instanceof Integer;
    }

    @Override
    public void applyEffectsToFighterSpawnedByShip(ShipAPI fighter, ShipAPI ship, java.lang.String id) {
        PersonAPI cap = resolveCaptain(ship);
        if (cap == null || cap.isDefault()) {
            return;
        }
        if (fighter.getMutableStats().getMinCrewMod()
                .computeEffective(fighter.getHullSpec().getMinCrew()) <= 0) {
            return;
        }

        final String sizeKey = "armaa_wingCommander_squadSize_" + cap.getId();

        if (hasSquad(cap, true)) {
            // Grow roster if extra deployments raised the wing size, then assign.
            createPilots(cap, ship, true);
            Object sizeObj = Global.getSector().getPersistentData().get(sizeKey);
            if (sizeObj instanceof Integer) {
                assignPilotToFighters((Integer) sizeObj, fighter, ship, true);
            }
        } else if (ship.isAlly() || ship.getOwner() == 1) {
            if (!hasSquad(cap, false)) {
                createPilots(cap, ship, false);
            }
            Object combatSize = Global.getCombatEngine().getCustomData().get(sizeKey);
            if (combatSize instanceof Integer) {
                assignPilotToFighters((Integer) combatSize, fighter, ship, false);
            }
        }
    }

    public void assignTempFighters() {
    }

    @Override
    public String getDescriptionParam(int index, HullSize hullSize) {
        return null;
    }

    public static String getSuffix(int num) {
        if (num == 11 || num == 12 || num == 13) {
            return th;
        }
        switch (num % 10) {
            case 1:
                return st;
            case 2:
                return nd;
            case 3:
                return rd;
            default:
                return th;
        }
    }

    public String getSquadName() {
        int num = MathUtils.getRandomNumberInRange(1, 100);
        String suffix = getSuffix(num);
        Random rand = new Random();
        int size = rand.nextInt(MechaModPlugin.squadNames.size());
        return num + suffix + " " + MechaModPlugin.squadNames.get(size);
    }

    public void doLanding(FighterLaunchBayAPI bay, ShipAPI ship) {
        List<ReturningFighter> returning = bay.getWing().getReturning();
        // --- PERF FIX (item 1): hoist ship ID ---
        final String shipId = ship.getId();

        for (int i = 0; i < returning.size(); i++) {
            ShipAPI fighter = returning.get(i).fighter;
            if (fighter == null) {
                continue;
            }

            if (bay.getWing().isReturning(fighter)) {
                ShipAPI posCarrier = getCarrier(fighter);
                if (posCarrier != null) {
                    armaa_combat_docking_AI_fighter DockingAI
                            = new armaa_combat_docking_AI_fighter(fighter);
                    if (fighter.getShipAI() != DockingAI) {
                        fighter.setShipAI(DockingAI);
                        DockingAI.init();
                    }
                } else {
                    final String retreatKey = "armaa_wingCommander_fighterRetreat_" + fighter.getId();
                    Object retreatFlag = fighter.getCustomData().get(retreatKey);
                    boolean alreadyHasAI = retreatFlag instanceof Boolean && (Boolean) retreatFlag;
                    if (!alreadyHasAI) {
                        armaa_combat_retreat_AI_fighter RetreatAI
                                = new armaa_combat_retreat_AI_fighter(fighter);

                        if (fighter.getShipAI() != RetreatAI) {
                            fighter.setShipAI(RetreatAI);
                            fighter.getCustomData().put(retreatKey, true);
                        }
                    }
                }
            }

            if (fighter.isLanding()) {
                for (ShipAPI carrier : CombatUtils.getShipsWithinRange(fighter.getLocation(), 100f)) {
                    if (carrier.getOwner() != fighter.getOwner()) {
                        continue;
                    }
                    if (carrier.isFighter()) {
                        continue;
                    }
                    if (!carrier.isFrigate() || carrier.isStationModule()) {
                        if (carrier.hasLaunchBays()) {
                            // --- PERF FIX (item 1): use hoisted shipId ---
                            Global.getCombatEngine().getCustomData()
                                    .put("armaa_wingCommander_landingLocation_" + shipId + "_" + i, carrier);
                            break;
                        }
                    }
                }
            }

            if (fighter.isFinishedLanding()) {
                bay.land(fighter);
                Global.getCombatEngine().removeEntity(fighter);
            }
        }
    }
}