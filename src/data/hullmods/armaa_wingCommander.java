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
    private IntervalUtil tracker = new IntervalUtil(0.5f, 1.0f);

    // --- PERF FIX (item 6): valid skill list is static final ---
    // Was: getValidSkillList() allocated a new ArrayList on every call.
    // Now: one allocation at class-load time, shared by all code paths.
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

    private final ArrayList<String> squadChatter_villain = new ArrayList<>();
    private final ArrayList<String> squadChatter_soldier = new ArrayList<>();
    private final WeightedRandomPicker<String> voices = new WeightedRandomPicker<>();
    private static final Set<String> BLOCKED_HULLMODS = new HashSet<>();
    public static float FIGHTER_OP_PER_DP = 5;
    public static int MIN_DP = 1;

    static {
        ENGAGEMENT_REDUCTION.put(HullSize.FIGHTER, 0.7f);
        ENGAGEMENT_REDUCTION.put(HullSize.FRIGATE, 0.7f);
        ENGAGEMENT_REDUCTION.put(HullSize.DESTROYER, 0.4f);
        ENGAGEMENT_REDUCTION.put(HullSize.CRUISER, 0.3f);
        ENGAGEMENT_REDUCTION.put(HullSize.CAPITAL_SHIP, 0.2f);
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
        if (1 == 1) {
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
        int extraCrew = 0;
        if (stats.getVariant().getHullSpec().getFighterBays() == 0) {
            for (int i = 0; i < stats.getVariant().getWings().size(); i++) {
                if (stats.getVariant().getWing(i) != null) {
                    extraCrew += stats.getVariant().getWing(i).getVariant().getHullSpec().getMinCrew()
                            * stats.getVariant().getWing(i).getNumFighters();
                }
            }
            stats.getMinCrewMod().modifyFlat(id, extraCrew);
            // BUG FIX: was applied twice (copy-paste leftover)
            stats.getDynamic().getMod(Stats.BOMBER_COST_MOD).modifyFlat(id, BOMBER_COST_MOD);
            if (stats.getNumFighterBays().isUnmodified()) {
                stats.getNumFighterBays().modifyFlat(id, 1f);
            }
            stats.getDynamic().getStat(Stats.REPLACEMENT_RATE_DECREASE_MULT).modifyMult(id, 1f + FIGHTER_REPLACEMENT_TIME_MULT);
        }
        stats.getFighterWingRange().modifyMult(id, 1f - ENGAGEMENT_REDUCTION.get(hullSize));
        stats.getDynamic().getStat(Stats.REPLACEMENT_RATE_DECREASE_MULT).modifyMult(id, 1f + FIGHTER_REPLACEMENT_TIME_MULT);
        stats.getFighterRefitTimeMult().modifyMult(id, FIGHTER_RATE);
        stats.getDynamic().getStat(Stats.FIGHTER_CREW_LOSS_MULT).modifyMult(id, CREW_LOSS_MULT);
    }

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, java.lang.String id) {
        for (String tmp : BLOCKED_HULLMODS) {
            if (ship.getVariant().getHullMods().contains(tmp)) {
                MagicIncompatibleHullmods.removeHullmodWithWarning(ship.getVariant(), tmp, "cataphract");
            }
        }
        if (ship.getCaptain() != null) {
            if (!ship.getCaptain().isDefault() && getWingSize(ship) > 0) {
                if (hasSquad(ship.getCaptain(), true)) {
                    createPilots(ship.getCaptain(), ship, true);
                }
            }
        }
    }

    @Override
    public boolean affectsOPCosts() {
        return true;
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        return (!ship.isStationModule() && ship.getVariant().hasHullMod("strikeCraft")
                && ship.getHullSpec().getFighterBays() == 0)
                || ship.getMutableStats().getNumFighterBays().isPositive()
                || ship.getHullSize() != HullSize.FRIGATE && ship.getHullSpec().getFighterBays() > 0;
    }

    @Override
    public String getUnapplicableReason(ShipAPI ship) {
        if (ship == null) {
            return "Can not be assigned";
        }
        return "Only installable on strikecraft or carriers larger than frigates";
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

        FighterWingSpecAPI wing = ship.getVariant().getWing(0);
        int wingSize = getWingSize(ship);

        if (ship.getVariant() != null
                || Global.getSector().getPlayerFleet().getCargo().getCrew() - 1 <= wingSize) {
            if (wing == null) {
                tooltip.addPara("No wing assigned.", 10, Misc.getHighlightColor());
            } else if (wingSize == 0) {
                tooltip.addPara("Wing is automated. No pilots assigned.", 10, Misc.getHighlightColor());
            } else if (Global.getCombatEngine().isInCampaign()
                    && Global.getSector().getPlayerFleet().getCargo().getCrew() - 1 <= wingSize) {
                tooltip.addPara("No crew can be spared to assign to this wing.", 10, Misc.getHighlightColor());
            } else if (ship.getCaptain().isDefault()) {
                tooltip.addPara(
                        "The wing lead by this unit is of no real note. Assign an officer to establish a squadron.",
                        10, Misc.getHighlightColor());
            } else {
                if (!ship.getCaptain().isDefault() && wingSize > 0) {
                    String squadName = "";
                    if (hasSquad(ship.getCaptain(), true)) {
                        squadName = (String) Global.getSector().getPersistentData()
                                .get("armaa_wingCommander_squadronName_" + ship.getCaptain().getId());
                    } else {
                        createSquad(ship.getCaptain());
                    }

                    tooltip.addPara(
                            "The " + squadName + " has been established under the command of "
                            + ship.getCaptain().getNameString()
                            + ". If this officer is assigned to another unit with WINGCOM, they will follow.",
                            10, HL, squadName, ship.getCaptain().getNameString());

                    float squadLevel = 0;
                    // --- PERF FIX (item 3): hoist captain ID out of loop ---
                    String captainId = ship.getCaptain().getId();
                    for (int i = 0; i < wingSize; i++) {
                        Object p = Global.getSector().getPersistentData()
                                .get("armaa_wingCommander_wingman_" + i + "_" + captainId);
                        if (p instanceof PersonAPI) {
                            squadLevel += ((PersonAPI) p).getRelToPlayer().getRel();
                        }
                    }
                    squadLevel /= wingSize;

                    tooltip.addPara("Unit solidarity is at %s, increasing fighter defensive capabilities "
                            + "by %s and offensive by %s. ", pad, F,
                            (int) (squadLevel * 100) + "%",
                            (int) (Math.min((squadLevel * 100) * 0.30, 0.30 * 100)) + "%",
                            (int) (Math.min(squadLevel * 100 * 0.30, 0.30 * 100)) + "%");

                    createPilots(ship.getCaptain(), ship, true);

                    }
                }
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
        if (ship.getLaunchBaysCopy().isEmpty()) {
            return;
        }
        if (ship.isStationModule() && ship.getStationSlot() != null) {
            return;
        }
        
        if(ship.getVariant().hasHullMod("converted_hangar"))
            return;
        if ((ship.getHullSpec().getFighterBays() > 0 && !ship.isFrigate() && !ship.isFighter())
                || ship.getHullSpec().hasTag("strikecraft_with_bays")) {
            return;
        }

        if (ship.isStationModule()) {
            if (ship.getCaptain() != null && !ship.getCaptain().isDefault() && getWingSize(ship) > 0) {
                if (hasSquad(ship.getCaptain(), true)) {
                    createPilots(ship.getCaptain(), ship, true);
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
            // --- PERF FIX (item 7): cache map bounds once per advanceInCombat call ---
            // Was: computed inside per-fighter loop on every frame a fighter is lifting off.
            // Half-extents only needed if we fall through to the "no carrier" fallback,
            // but computing them here is still cheaper than recomputing per fighter.
            final float mapHalfW = Global.getCombatEngine().getMapWidth() / 2f;
            final float mapHalfH = Global.getCombatEngine().getMapHeight() / 2f;
            final Vector2f rawLL = new Vector2f(-mapHalfW, -mapHalfH);
            final Vector2f rawUR = new Vector2f(mapHalfW, mapHalfH);

            // --- PERF FIX (item 1): hoist ship ID string — used in every key below ---
            final String shipId = ship.getId();

            List<ShipAPI> wingMembers = bay.getWing().getWingMembers();
            for (int i = 0; i < wingMembers.size(); i++) {
                ShipAPI fighter = wingMembers.get(i);
                if (fighter == null || fighter.isHulk()) {
                    continue;
                }

                // --- PERF FIX (item 1+2): build the "set" key once, do single map lookup ---
                final String setKey = "armaa_wingCommander_landingLocation_" + fighter.getId() + "_set";
                Object setFlag = Global.getCombatEngine().getCustomData().get(setKey);
                boolean done = setFlag instanceof Boolean && (Boolean) setFlag;

                if (fighter.isLiftingOff() && !done) {
                    Vector2f landingLoc = null;

                    // --- PERF FIX (item 1): build per-index key once ---
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

            tracker.advance(amount);
            if (tracker.intervalElapsed()) {
                if (!bay.getWing().getReturning().isEmpty()) {
                    doLanding(bay, ship);
                }
            }
        }
    }

    public void assignPilotToFighters(int count, ShipAPI fighter, ShipAPI ship, boolean persistent) {
        float squadLevel = 0;
        PersonAPI pilot = null;

        final String captainId = ship.getCaptain().getId();

        if (persistent) {
            for (int j = 0; j < count; j++) {
                final String pilotKey = "armaa_wingCommander_wingman_" + j + "_" + captainId;
                final String assignedKey = "armaa_wingCommander_wingman_" + j + "_wasAssigned_" + captainId;

                Object pilotObj = Global.getSector().getPersistentData().get(pilotKey);
                if (!(pilotObj instanceof PersonAPI)
                        || fighter.getWing().getSpec().getVariant().getHullSpec().getMinCrew() <= 0) {
                    continue;
                }

                Object assignedFlag = Global.getCombatEngine().getCustomData().get(assignedKey);
                boolean wasAssigned = assignedFlag instanceof Boolean && (Boolean) assignedFlag;

                pilot = (PersonAPI) pilotObj;
                squadLevel += pilot.getRelToPlayer().getRel();

                if (!wasAssigned) {
                    String callsign = (String) Global.getSector().getPersistentData()
                            .get("armaa_wingCommander_wingman_" + j + "_callsign_" + captainId);
                    fighter.setCaptain(pilot);
                    Global.getCombatEngine().getCustomData().put(assignedKey, true);
                    Global.getCombatEngine().addPlugin(new armaa_pilotTracker(fighter, callsign, j));
                    break;
                }
            }
        } else {
            for (int j = 0; j < count; j++) {
                final String pilotKey = "armaa_wingCommander_wingman_" + j + "_" + captainId;
                final String assignedKey = "armaa_wingCommander_wingman_" + j + "_wasAssigned_" + captainId;

                Object pilotObj = Global.getCombatEngine().getCustomData().get(pilotKey);
                if (!(pilotObj instanceof PersonAPI)
                        || fighter.getWing().getSpec().getVariant().getHullSpec().getMinCrew() <= 0) {
                    continue;
                }

                Object assignedFlag = Global.getCombatEngine().getCustomData().get(assignedKey);
                boolean wasAssigned = assignedFlag instanceof Boolean && (Boolean) assignedFlag;

                pilot = (PersonAPI) pilotObj;
                squadLevel += (pilot.getStats().getLevel() / ship.getCaptain().getStats().getLevel()) * .3f;

                if (!wasAssigned) {
                    String callsign = (String) Global.getCombatEngine().getCustomData()
                            .get("armaa_wingCommander_wingman_" + j + "_callsign_" + captainId);
                    fighter.setCaptain(pilot);
                    Global.getCombatEngine().getCustomData().put(assignedKey, true);
                    Global.getCombatEngine().addPlugin(new armaa_pilotTrackerNP(fighter, callsign, j));
                    break;
                }
            }
        }

        if (fighter.getCaptain() == null || fighter.getCaptain().isDefault()) {
            return;
        }

        squadLevel /= fighter.getWing().getSpec().getNumFighters();
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

    public int getWingSize(ShipAPI ship) {
        FighterWingSpecAPI wing = ship.getVariant().getWing(0);
        int wingSize = 0;
        boolean crewedWing = false;
        if (wing != null) {
            for (int i = 0; i < ship.getVariant().getWings().size(); i++) {
                FighterWingSpecAPI w = ship.getVariant().getWing(i);
                if (w == null) {
                    continue;
                }
                crewedWing = w.getVariant().getHullSpec().getMinCrew() > 0;
                if (crewedWing) {
                    wingSize += w.getNumFighters();
                }
            }
        }
        for (String slot : ship.getVariant().getModuleSlots()) {
            if (!ship.getVariant().getModuleVariant(slot).hasHullMod("armaa_wingCommander")) {
                continue;
            }
            for (int i = 0; i < ship.getVariant().getModuleVariant(slot).getWings().size(); i++) {
                FighterWingSpecAPI w = ship.getVariant().getModuleVariant(slot).getWing(i);
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
        int size = getWingSize(ship);
        int currentSize = size;
        // --- PERF FIX (item 3): hoist commander ID ---
        final String cmdId = commander.getId();
        final String sizeKey = "armaa_wingCommander_squadSize_" + cmdId;

        Object existingSize = Global.getSector().getPersistentData().get(sizeKey);
        if (existingSize instanceof Integer) {
            currentSize = (int) existingSize;
            if (size == currentSize || size == 0) {
                return;
            }
            if (currentSize > 100) {
                currentSize = 0; // drone-wing safety cap
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
            Global.getSector().getPersistentData().put(sizeKey, currentSize + Math.abs(currentSize - size));
        } else {
            Global.getCombatEngine().getCustomData().put(sizeKey, size);
        }
    }

    public void createSquad(PersonAPI commander) {
        String squadName = getSquadName();
        Global.getSector().getPersistentData()
                .put("armaa_wingCommander_squadronName_" + commander.getId(), squadName);
    }

    public boolean hasSquad(PersonAPI commander, boolean persistent) {
        if (persistent) {
            return Global.getSector().getPersistentData()
                    .get("armaa_wingCommander_squadronName_" + commander.getId()) instanceof String;
        }
        return Global.getCombatEngine().getCustomData()
                .get("armaa_wingCommander_squadSize_" + commander.getId()) instanceof Integer;
    }

    @Override
    public void applyEffectsToFighterSpawnedByShip(ShipAPI fighter, ShipAPI ship, java.lang.String id) {
        if (ship.getCaptain() == null || ship.getCaptain().isDefault()) {
            return;
        }
        if (fighter.getMutableStats().getMinCrewMod()
                .computeEffective(fighter.getHullSpec().getMinCrew()) <= 0) {
            return;
        }

        final String captainId = ship.getCaptain().getId();
        final String sizeKey = "armaa_wingCommander_squadSize_" + captainId;

        Object sizeObj = Global.getSector().getPersistentData().get(sizeKey);
        if (sizeObj instanceof Integer) {
            assignPilotToFighters((Integer) sizeObj, fighter, ship, true);
        } else {
            if (ship.isAlly() || ship.getOwner() == 1) {
                if (!hasSquad(ship.getCaptain(), false)) {
                    createPilots(ship.getCaptain(), ship, false);
                }
                Object combatSize = Global.getCombatEngine().getCustomData().get(sizeKey);
                if (combatSize instanceof Integer) {
                    assignPilotToFighters((Integer) combatSize, fighter, ship, false);
                }
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
                    // --- PERF FIX (item 2): single lookup, cast once ---
                    final String retreatKey = "armaa_wingCommander_fighterRetreat_" + fighter.getId();
                    Object retreatFlag = fighter.getCustomData().get(retreatKey);
                    boolean alreadyHasAI = retreatFlag instanceof Boolean && (Boolean) retreatFlag;
                    if (!alreadyHasAI) {
                        armaa_combat_retreat_AI_fighter RetreatAI
                                = new armaa_combat_retreat_AI_fighter(fighter);
                        // BUG FIX: was   if (fighter.getShipAI() != RetreatAI);
                        // Semicolon made block unconditional — removed.
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
