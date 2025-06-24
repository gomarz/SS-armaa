package data.hullmods;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.characters.*;
import com.fs.starfarer.api.ui.UIPanelAPI;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.combat.EmpArcEntityAPI;
import com.fs.starfarer.api.combat.listeners.*;

import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;

import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.entities.AnchoredEntity;
import org.magiclib.util.MagicUI;
import data.scripts.ai.armaa_combat_docking_AI;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;
import java.util.*;
import java.awt.Color;
import data.scripts.util.armaa_utils;
import data.scripts.MechaModPlugin;
import org.magiclib.util.MagicRender;
import org.magiclib.util.MagicIncompatibleHullmods;
import lunalib.lunaSettings.LunaSettings;

public class armaa_strikeCraft extends BaseHullMod {

    private Color COLOR = new Color(0, 106, 0, 50);
    private float DEGRADE_INCREASE_PERCENT = 50f;
    public float CORONA_EFFECT_REDUCTION = 0.00001f;

    public MagicUI ui;
    private static final float RETREAT_AREA_SIZE = 2100f;
    private IntervalUtil interval = new IntervalUtil(25f, 25f);
    private IntervalUtil warningInterval = new IntervalUtil(1f, 1f);
    private IntervalUtil textInterval = new IntervalUtil(.5f, .5f);
    private IntervalUtil repairInterval = new IntervalUtil(.5f, .5f);
    private Color TEXT_COLOR = new Color(55, 155, 255, 255);
    private final Color CORE_COLOR = new Color(200, 200, 200);
    private final Color FRINGE_COLOR = new Color(255, 10, 10);
    private final float Repair = 100, CR = 20; //Hullmod in game description.
    private float HPPercent = 0.80f;
    private float BaseTimer = .30f;
    private float CRmarker = 0.4f;
    private float repairLevel = 0.5f;

    private float MISSILE_DAMAGE_THRESHOLD = 750f;
    public final ArrayList<String> landingLines_Good = new ArrayList<>();
    public final ArrayList<String> landingLines_Fair = new ArrayList<>();
    public final ArrayList<String> landingLines_Critical = new ArrayList<>();
    public final ArrayList<String> landingLines_notPossible = new ArrayList<>();
    private static final Set<String> BLOCKED_HULLMODS = new HashSet<>();

    // private Logger logger = Logger.getLogger(armaa_strikeCraft.class);
    enum RefitMode {
        REFIT_WHEN_SAFE("armaa_strikecraft_refit_outCombat"),
        REFIT_ASAP("armaa_strikecraft_refit_standard"),
        REFIT_NEVER("armaa_strikecraft_refit_never");
        public final String value;

        private RefitMode(String value) {
            this.value = value;
        }
    }

    static {
        BLOCKED_HULLMODS.add("converted_fighterbay");
        BLOCKED_HULLMODS.add("roider_fighterClamps");
    }

    {
        //If CR is low, but otherwise no major damage
        landingLines_Good.add("\"Welcome aboard, $hisorher.\"");
        landingLines_Good.add("\"Looks like you got away pretty clean!\"");
        landingLines_Good.add("\"Hopefully, you bag a few more kills today.\"");
        landingLines_Good.add("\"All conditions good. Fuselage solid.\"");

        //If taken heavy damage, but CR isn't too bad
        landingLines_Critical.add("\"Glad you made it back alive, $hisorher.\"");
        landingLines_Critical.add("\"Looks like this may be a tough battle, $hisorher.\"");
        landingLines_Critical.add("\"Be careful flying while severely damaged. Even anti-air fire could bring you down.\"");

        //Tried to find a carrier but could not
        landingLines_notPossible.add("\"Dammit, nowhere to land!\"");
        landingLines_notPossible.add("\"Could really use somewhere to land right now..\"");
        landingLines_notPossible.add("\"Zilch on available carriers. Aborting refit!\"");
        landingLines_notPossible.add("\"There's nowhere for me to resupply.\"");
    }

    public static Map DAMAGE_MAP = new HashMap();

    static {
        DAMAGE_MAP.put(HullSize.FIGHTER, 1f);
        DAMAGE_MAP.put(HullSize.FRIGATE, 1.20f);
        DAMAGE_MAP.put(HullSize.DESTROYER, 1.10f);
        DAMAGE_MAP.put(HullSize.CRUISER, 0.90f);
        DAMAGE_MAP.put(HullSize.CAPITAL_SHIP, 0.80f);
    }

    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        armaa_utils.applyFlatMod(stats.getZeroFluxMinimumFluxLevel(), id, -114514f); // -1 means boost never takes effect; 2 means always on
        armaa_utils.applyMultMod(stats.getAllowZeroFluxAtAnyLevel(), id, -114514f);
        stats.getCRLossPerSecondPercent().modifyPercent(id, DEGRADE_INCREASE_PERCENT);
        stats.getSensorProfile().modifyMult(id, 0.5f);
        stats.getSensorStrength().modifyMult(id, 0.5f);
        FleetMemberAPI member = stats.getFleetMember();

        if (stats.getFleetMember() != null && stats.getFleetMember().getFleetData() != null) {
            FleetDataAPI fleet = stats.getFleetMember().getFleetData();
            boolean carrierBonus = false;
            boolean independent = stats.getVariant().getHullSpec().getTags().contains("independent_of_carrier") ? true : false;
            boolean hasSupportingShip = false;
            PersonAPI defaultCap = null;
            fleet.syncIfNeeded();
            for (FleetMemberAPI ship : fleet.getMembersListCopy()) {
                if (ship == stats.getFleetMember()) {
                    continue;
                }

                if (independent && carrierBonus) {
                    break;
                }

                if (ship.getVariant().hasHullMod("armaa_strikeCraft")) {
                    continue;
                }

                hasSupportingShip = true;

                if (ship.isFrigate()) {
                    continue;
                }

                if (ship.getNumFlightDecks() < 1) {
                    continue;
                }
                carrierBonus = true;
            }

            if (!hasSupportingShip) {
                stats.getMaxBurnLevel().modifyMult("armaa_carrierStorage", 0f);
            } else {
                stats.getMaxBurnLevel().unmodify("armaa_carrierStorage");
            }

            if (carrierBonus && (!Global.getSector().getPersistentData().containsKey("armaa_hyperSpaceBuff"))) {
                stats.getDynamic().getStat(Stats.CORONA_EFFECT_MULT).modifyMult("armaa_carrierStorageHyper", CORONA_EFFECT_REDUCTION);
            }
        }

        if (Global.getCombatEngine() != null || (Global.getSector() != null && Global.getSector().getCampaignUI() != null && Global.getSector().getCampaignUI().isShowingDialog())) {
            stats.getDynamic().getStat(Stats.CORONA_EFFECT_MULT).unmodify("armaa_carrierStorageHyper");
        }
    }

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, java.lang.String id) {
        for (String tmp : BLOCKED_HULLMODS) {
            if (ship.getVariant().getHullMods().contains(tmp)) {
                MagicIncompatibleHullmods.removeHullmodWithWarning(ship.getVariant(), tmp, "cataphract");
            }
        }

        if (!ship.hasListenerOfClass(armaa_strikeCraft.StrikeCraftDeathMod.class)) {
            ship.addListener(new StrikeCraftDeathMod(ship));
        }
    }

    public boolean canRetreat(ShipAPI ship) {
        float mapWidth = Global.getCombatEngine().getMapWidth() / 2f, mapHeight = Global.getCombatEngine().getMapHeight() / 2f;
        Vector2f rawLL = new Vector2f(-mapWidth, -mapHeight), rawUR = new Vector2f(mapWidth, mapHeight);
        CombatEngineAPI engine = Global.getCombatEngine();
        BattleCreationContext context = engine.getContext();
        FleetGoal objective = context.getPlayerGoal();
        boolean check = false;
        Vector2f location = ship.getLocation();
        // if player is escaping or I am an enemy
        if (objective == FleetGoal.ESCAPE || ship.getOwner() == 1) {
            //I can retreat at the top of the map
            if (location.getY() > rawUR.getY() - RETREAT_AREA_SIZE) {
                check = true;
            }
        }
        //if player is escaping and I am an enemy, or if I am player
        if (objective == FleetGoal.ESCAPE && ship.getOwner() == 1 || ship.getOwner() == 0) {
            //I can retreat at the bottom of the map
            if (location.getY() < RETREAT_AREA_SIZE + rawLL.getY()) {
                check = true;
            }
        }
        return check;
    }

    //determines when AI pilots will return to carrier
    public void getPilotPersonality(ShipAPI ship) {
        float dangerLevel = .50f;
        if (Global.getSettings().getModManager().isModEnabled("lunalib")) {
            double d = LunaSettings.getDouble("armaa", "armaa_repairLevel");
            dangerLevel = (float) d;
        }
        Global.getCombatEngine().getCustomData().put("armaa_strikecraftPilot" + ship.getId(), dangerLevel);

    }

    public String getDescriptionParam(int index, HullSize hullSize) {

        if (index == 0) {
            return "" + "activating autopilot. You can also target a specific carrier to land on it.";
        }

        return null;
    }

    private final Color HL = Global.getSettings().getColor("hColor");
    private final Color F = Global.getSettings().getColor("textFriendColor");
    private final Color E = Global.getSettings().getColor("textEnemyColor");

    public String getRefitMode(ShipAPI ship) {
        Collection<String> tags = ship.getHullSpec().getTags();
        //switch (refitMode) {
        if (tags.contains(RefitMode.REFIT_WHEN_SAFE.value)) {
            return "refit when no enemies are nearby, or completely out of ammo";
        } else if (tags.contains(RefitMode.REFIT_ASAP.value)) {
            return "refit as soon as a weapon runs out of ammo.";
        } else {
            return "only refit when CR or Hull is low.";
        }
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        float pad = 10f;
        float padS = 2f;
        Color[] arr = {Misc.getHighlightColor(), F};
        Color[] arrB = {Misc.getHighlightColor(), F, F};
        Color[] arr2 = {Misc.getHighlightColor(), E};
        Color[] arr3 = {Misc.getHighlightColor(), Misc.getHighlightColor(), E};
        String size = "";
        boolean canCapturePoints = false;
        if (ship.getHullSpec().hasTag("strikecraft_medium")) {
            size = "can only land on ships larger than destroyers";
        } else if (ship.getHullSpec().hasTag("strikecraft_large")) {
            size = "can only land on capital ships";
        }

        if (ship.getHullSpec().getBuiltInMods().contains("armaa_strikeCraftFrig")) {
            canCapturePoints = true;
        }

        tooltip.addSectionHeading("Details", Alignment.MID, 10);

        tooltip.addPara("%s " + "Can %s.", pad, Misc.getHighlightColor(), "\u2022", "maneuver over asteroids and other vessels, unless flamed out");
        tooltip.addPara("%s " + "Most weapons %s.", padS, Misc.getHighlightColor(), "\u2022", "fire over allied ships");
        tooltip.addPara("%s " + "No %s.", padS, Misc.getHighlightColor(), "\u2022", "zero-flux speed bonus");
        tooltip.addPara("%s " + "Combat Readiness decreases %s faster.", padS, arr2, "\u2022", (int) DEGRADE_INCREASE_PERCENT + "%");
        tooltip.addPara("%s " + "Docking replenishes PPT, armor, hull, and ammo.", padS, Misc.getHighlightColor(), "\u2022");
        tooltip.addPara("%s " + "Benefits from all bonuses that affect frigates.", padS, Misc.getHighlightColor(), "\u2022", "frigates");
        tooltip.addSectionHeading("Point Defense Vulnerability", Alignment.MID, 10);
        TooltipMakerAPI pdWarning = tooltip.beginImageWithText("graphics/armaa/icons/hullsys/armaa_pdWarning.png", 64);
        pdWarning.addPara("%s " + "Receives extra damage ships/weapons would deal to fighters, up %s. The visual indicator to the left will appear over such vessels.", padS, Misc.getHighlightColor(), "\u2022", "25%", "visual indicator");
        UIPanelAPI temp = tooltip.addImageWithText(10f);

        tooltip.addSectionHeading("Refit Mode", Alignment.MID, 10);
        tooltip.addPara("Ship will " + getRefitMode(ship), pad, arr, "\u2022");
        tooltip.addSectionHeading("Carrier Bonuses", Alignment.MID, 10);
        if (ship.getMutableStats().getDynamic().getStat(Stats.CORONA_EFFECT_MULT).getMultMods().containsKey("armaa_carrierStorageHyper")) {
            tooltip.addPara("%s " + "Friendly carriers present for storage during travel: Receives %s from hyperspace storms and coronas outside of combat.", pad, arr, "\u2022", "no damage");
        }

        //title
        tooltip.addSectionHeading("Refit Penalties", Alignment.MID, 10);

        if (ship != null && ship.getVariant() != null) {
            if (size.length() > 1) {
                tooltip.addPara("%s " + "Large Strikecraft: %s", pad, arr2, "\u2022", size);
            }
            float adjustedRate = 0f;
            String weaponName = "";
            getRefitRate(ship);
            Map<String, Float> MALUSES = (Map) Global.getCombatEngine().getCustomData().get("armaa_strikecraftMalus_" + ship.getId());

            if (MALUSES == null || MALUSES.isEmpty()) {
                tooltip.addPara(
                        "No refit penalty.",
                         10,
                         F
                );

            } else {
                float total = (float) Global.getCombatEngine().getCustomData().get("armaa_strikecraftTotalMalus" + ship.getId());
                tooltip.addPara("%s " + "Refitting at carriers is %s slower.", pad, arr2, "\u2022", (int) (total * 100) + "%");
                tooltip.addSectionHeading("Modifiers", Alignment.MID, 10);
                tooltip.addPara("", 0f);
                for (Map.Entry<String, Float> entry : MALUSES.entrySet()) {
                    //effect applied
                    float value = entry.getValue();;
                    String weapon = entry.getKey();
                    tooltip.addPara("%s " + "%s: +%s.", padS, arr3, "\u2022", weapon, (int) (value * 100) + "%");

                }
            }
        }
    }

    private void getRefitRate(ShipAPI target) {
        float totalRate = 0f;
        String wepName = "";
        List<WeaponAPI> weapons = target.getAllWeapons();
        Map<String, Float> MALUSES = new HashMap<>();
        for (WeaponAPI w : weapons) {
            float adjustedRate = 0f;
            if (MechaModPlugin.MISSILE_REFIT_MALUS.get(w.getId()) != null) {
                //If not null, there is a custom refit value for this weapon
                adjustedRate = MechaModPlugin.MISSILE_REFIT_MALUS.get(w.getId());
                totalRate += adjustedRate;
                if (MALUSES.containsKey(w.getDisplayName())) {
                    float prev = MALUSES.get(w.getDisplayName());
                    MALUSES.put(w.getDisplayName(), prev + adjustedRate);
                } else {
                    MALUSES.put(w.getDisplayName(), adjustedRate);
                }
            } else if (w.getType() == WeaponType.MISSILE) {
                float damage = w.getDerivedStats().getDamagePerShot();
                if (damage > MISSILE_DAMAGE_THRESHOLD) {
                    float penalty = damage / MISSILE_DAMAGE_THRESHOLD;

                    if (penalty < 1) {
                        continue;
                    }

                    penalty = penalty / 10f;
                    float newRate = (float) Math.min(.5f, penalty);
                    wepName = w.getDisplayName();
                    adjustedRate = newRate;
                    totalRate += adjustedRate;

                    if (MALUSES.containsKey(wepName)) {
                        float prev = MALUSES.get(wepName);
                        MALUSES.put(wepName, prev + adjustedRate);
                    } else {
                        MALUSES.put(wepName, adjustedRate);
                    }
                }
            }
        }

        for (HullModSpecAPI spec : Global.getSettings().getAllHullModSpecs()) {
            float adjustedRate = 0f;
            //dont bother if its not in the map
            if (MechaModPlugin.HULLMOD_REFIT_MALUS.get(spec.getId()) == null) {
                continue;
            }

            //dont bother if its not on the target ship
            if (!target.getVariant().getHullMods().contains(spec.getId())) {
                continue;
            }

            String hullmod = spec.getId();
            String name = spec.getDisplayName();
            //If not null, there is a custom refit value for this weapon
            adjustedRate = MechaModPlugin.HULLMOD_REFIT_MALUS.get(hullmod);
            totalRate += adjustedRate;
            if (MALUSES.containsKey(name)) {
                float prev = MALUSES.get(name);
                MALUSES.put(name, prev + adjustedRate);
            } else {
                MALUSES.put(name, adjustedRate);
            }
        }
        Global.getCombatEngine().getCustomData().put("armaa_strikecraftTotalMalus" + target.getId(), totalRate);
        Global.getCombatEngine().getCustomData().put("armaa_strikecraftMalus" + "_" + target.getId(), MALUSES);
    }

    //Check if any of our weapons are out of ammo.
    private boolean needsReload(ShipAPI target) {
        if (target.getHullLevel() <= repairLevel) {
            return true;
        }
        getRefitRate(target);
        List<WeaponAPI> weapons = target.getAllWeapons();
        int loadedWeps = 0;
        int dryWeps = 0;

        for (ShipAPI module : target.getChildModulesCopy()) {
            String key = "moduleRepair_isDestroyed" + "_" + module.getId();
            if (Global.getCombatEngine().getCustomData().containsKey(key)) {
                return true;
            }

        }

        boolean check = false;
        int side = target.getOwner() == 1 ? 0 : 1;
        ShipHullSpecAPI spec = target.getVariant().getHullSpec();
        if (target.getVariant().getHullSpec().isDHull() && target.getHullSpec().getDParentHull() != null) {
            spec = target.getHullSpec().getDParentHull();
        }
        if (spec.getTags().contains(RefitMode.REFIT_WHEN_SAFE.value)) {
            if (target.areAnyEnemiesInRange() || CombatUtils.isVisibleToSide(target, side)) {
                return check;
            }
        } else if (spec.getTags().contains("armaa_strikecraft_refit_never")) {
            return check;
        }

        for (WeaponAPI w : weapons) {
            if (w.hasAIHint(WeaponAPI.AIHints.PD) || w.hasAIHint(WeaponAPI.AIHints.PD_ALSO) || w.getMaxAmmo() <= 0) {
                continue;
            }
            //So long as we have at least one weapon with some ammo left, don't return yet
            if (w.getId().equals("armaa_leynosBoostKnuckle") && Global.getCombatEngine().getPlayerShip() != target) {
                return false;
            }

            //code to make sure AI strikecraft don't return if they fire their landing beacon
            if (w.getId().equals("armaa_landingBeacon")) {
                if (w.getAmmo() < 1 && target == Global.getCombatEngine().getPlayerShip() && Global.getCombatEngine().isUIAutopilotOn()) {
                    check = true;
                    return check;
                } else {
                    continue;
                }
            }

            if (!w.getSlot().isDecorative() && !w.getId().equals("armaa_landingBeacon")) {
                if (w.usesAmmo() && (w.getAmmo() < 1 && w.getAmmoPerSecond() == 0)) {
                    dryWeps++;
                } else if ((w.usesAmmo() && w.getAmmo() >= 1 && w.getAmmoPerSecond() >= 0) || !w.usesAmmo()) {
                    loadedWeps++;
                }

            }
        }
        if (dryWeps > 0) {
            if (dryWeps >= loadedWeps) {
                check = true;
                return check;
            } else if ((Global.getCombatEngine().getPlayerShip() != target && (target.getShipTarget() == null || target.getShipTarget().isHulk())) || (Global.getCombatEngine().getPlayerShip() == target && !Global.getCombatEngine().isUIAutopilotOn() && target.getShipTarget() == null) || Global.getCombatEngine().getPlayerShip() == target) {
                check = true;
                return check;
            }
        }
        check = false;
        return check;
    }

    //Check if there are any ships that we can land at
    private boolean canRefit(ShipAPI ship) {
        for (ShipAPI module : ship.getChildModulesCopy()) {
            if (module.getVariant().hasTag("no_wingcom_docking")) {
                return false;
            }
        }
        boolean canRefit = false;

        for (ShipAPI carrier : CombatUtils.getShipsWithinRange(ship.getLocation(), 10000.0F)) {
            if (carrier.getOwner() != ship.getOwner() || carrier.isFighter() || carrier.isFrigate()) {
                continue;
            }
            if (carrier.isHulk()) {
                continue;
            }
            if (ship.getHullSpec().hasTag("strikecraft_medium")) {
                if (carrier.isDestroyer()) {
                    continue;
                }
            } else if (ship.getHullSpec().hasTag("strikecraft_large")) {
                if (carrier.isCruiser() || carrier.isDestroyer()) {
                    continue;
                }
            }
            if (carrier.getNumFighterBays() > 0) {
                canRefit = true;
                break;
            }
        }

        return canRefit;
    }

    private ShipAPI getNearestCarrier(ShipAPI ship) {
        ShipAPI potCarrier = null;
        float distance = 99999f;
        for (ShipAPI carrier : CombatUtils.getShipsWithinRange(ship.getLocation(), 10000.0F)) {
            if (carrier == ship) {
                continue;
            }
            if (carrier.getVariant().hasHullMod("strikeCraft")) {
                continue;
            }
            if (carrier.getHullSpec().hasTag("no_wingcom_docking")) {
                continue;
            }
            if (carrier.getOwner() != ship.getOwner() || !carrier.getHullSpec().hasTag("strikecraft_with_bays") && (carrier.isFighter() || carrier.isFrigate()) && !carrier.isStationModule() || carrier == ship) {
                continue;
            }
            if (carrier.isHulk()) {
                continue;
            }

            if (carrier.getNumFighterBays() > 0) {
                if (MathUtils.getDistance(ship, carrier) < distance) {
                    distance = MathUtils.getDistance(ship, carrier);
                    potCarrier = carrier;
                }
            }
        }

        return potCarrier;
    }

    private void retreatToRefit(ShipAPI ship) {
        if (ship.isRetreating()) {
            return;
        }
        if (ship == null || ship.getLocation() == null) {
            return;
        }
        boolean ally = false;
        ShipwideAIFlags flags = ship.getAIFlags();
        ShipAPI carrier = getNearestCarrier(ship);
        if (carrier == null) {
            return;
        }
        Global.getCombatEngine().headInDirectionWithoutTurning(ship, VectorUtils.getAngle(ship.getLocation(), carrier.getLocation()), ship.getMaxSpeed());
        flags.setFlag(ShipwideAIFlags.AIFlags.BACK_OFF, 1f);
    }

    public void adjustHullSize(ShipAPI ship, HullSize size) {
        if (ship.isRetreating() && ship.getHullSize() != HullSize.FRIGATE && !ship.isFinishedLanding()) {
            size = HullSize.FRIGATE;
        }
        if (ship.getHullSize().equals(size)) {
            return;
        }

        ship.setHullSize(size);
        if (size.equals(HullSize.FRIGATE)) {
            ship.resetDefaultAI();
        }
    }

    public boolean checkRefitStatus(ShipAPI ship, float amount) {
        boolean player = ship == Global.getCombatEngine().getPlayerShip();
        boolean needsrefit = false;
        boolean ally = ship.isAlly();
        CombatFleetManagerAPI cfm = Global.getCombatEngine().getFleetManager(ship.getOwner());
        CombatTaskManagerAPI ctm = cfm.getTaskManager(ally);
        repairInterval.advance(amount);
        if (repairInterval.intervalElapsed()) {
            if (player && !ship.isFinishedLanding()) {
                Global.getCombatEngine().maintainStatusForPlayerShip("AceSystem1", "graphics/ui/icons/icon_repair_refit.png", "/ - WARNING - /", "REFIT / REARM NEEDED( PRESS " + Global.getSettings().getControlStringForEnumName("C2_TOGGLE_AUTOPILOT") + " )", true);
            }
        }

        if (!ship.isRetreating() && ((!player && canRefit(ship)) || (player && !Global.getCombatEngine().isUIAutopilotOn()))) {
            if (!ship.isFinishedLanding() && !ship.isLanding()) {
                needsrefit = true;
                retreatToRefit(ship);
            }

            armaa_combat_docking_AI DockingAI = new armaa_combat_docking_AI(ship);

            if (!ship.isLanding() && !ship.isFinishedLanding() && getNearestCarrier(ship) != null && MathUtils.getDistance(ship, getNearestCarrier(ship)) < 500f) {
                if (!Global.getCombatEngine().getCustomData().containsKey("armaa_strikecraftisLanding" + ship.getId())) {
                    ship.setShipAI(DockingAI);
                    DockingAI.init();
                    Global.getCombatEngine().getCustomData().put("armaa_strikecraftisLanding" + ship.getId(), true);
                }
            } else {
                if (Global.getCombatEngine().getCustomData().get("armaa_strikecraftLanded" + ship.getId()) instanceof Boolean) {
                    boolean hasLanded = (boolean) Global.getCombatEngine().getCustomData().get("armaa_strikecraftLanded" + ship.getId());
                    if (player && !hasLanded && ship.isFinishedLanding()) {
                        Random rand = new Random();
                        String txt = landingLines_Critical.get(rand.nextInt(landingLines_Critical.size()));
                        if (ship.getHullLevel() > 0.45f) {
                            txt = landingLines_Good.get(rand.nextInt(landingLines_Good.size()));
                        }
                        Vector2f texLoc;
                        if (txt.contains("$hisorher")) {
                            FullName.Gender gender = ship.getCaptain().getGender();
                            String title = "";
                            switch (gender) {
                                case FEMALE:
                                    title = "ma'am";
                                case MALE:
                                    title = "sir";
                                case ANY:
                                    title = "sir";
                                    break;
                            }
                            String tempTxt = txt;
                            txt = tempTxt.replace("$hisorher", title);
                        }
                        if (DockingAI.getCarrier() != null) {
                            texLoc = DockingAI.getCarrier().getLocation();
                        } else {
                            texLoc = ship.getLocation();
                        }
                        if (Math.random() <= 35f) {
                            ship.getFluxTracker().showOverloadFloatyIfNeeded(txt, Color.white, 2f, true);
                            Global.getSoundPlayer().playSound("ui_noise_static", 1f, 1f, texLoc, new Vector2f());
                        }
                        Global.getCombatEngine().getCustomData().put("armaa_strikecraftLanded" + ship.getId(), true);
                    }
                }
                Global.getCombatEngine().getCustomData().put("armaa_strikecraftRefitTime" + ship.getId(), DockingAI.getCarrierRefitRate());
            }
        }
        return needsrefit;
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        if (!ship.isPhased() && (ship.getEngineController().isFlamedOut() || ship.getHitpoints() <= 0 || ship.isHulk())) {
            ship.setCollisionClass(CollisionClass.SHIP);
        } else if (ship.getCollisionClass() == CollisionClass.SHIP) {
            ship.setCollisionClass(CollisionClass.FIGHTER);
        }

        if (!ship.isAlive()) {
            return;
        }
        /* limited repairs - TODO?
		boolean player = Global.getCombatEngine().getPlayerShip() == ship;
		if(Global.getCombatEngine().getCustomData().get("armaa_hullSupplies_"+ship.getOwner()) == null)
		{
			Global.getCombatEngine().getCustomData().put("armaa_hullSupplies_"+ship.getOwner(),ship.getHitpoints()*3);
			Global.getCombatEngine().getCustomData().put("armaa_ammoSupplies_"+ship.getOwner(),ship.getHitpoints()*3);
		}
		if(player)
		{
			float supplies = (Float)Global.getCombatEngine().getCustomData().get("armaa_hullSupplies_"+ship.getOwner());
			float ammoSupplies = (Float)Global.getCombatEngine().getCustomData().get("armaa_ammoSupplies_"+ship.getOwner());
			Global.getCombatEngine().maintainStatusForPlayerShip("AceSystem2", "graphics/ui/icons/icon_repair_refit.png","SUPPLIES REMAINING", "HULL: " + supplies + " / AMMO: "+ammoSupplies,true);
		}
         */
        if (ship.getShipAI() == null && Global.getCombatEngine().getPlayerShip() == ship) {
            Global.getCombatEngine().getCustomData().remove("armaa_strikecraftisLanding" + ship.getId());
        }

        boolean isFrig = true; //ship.getVariant().hasHullMod("armaa_strikeCraftFrig");
        if (Global.getCombatEngine().getPlayerShip() == ship) {

            warningInterval.advance(amount);
            if (warningInterval.intervalElapsed()) {
                for (ShipAPI pdShip : CombatUtils.getShipsWithinRange(ship.getLocation(), 2000f)) {
                    if (!pdShip.isAlive() || pdShip.isStationModule()) {
                        continue;
                    }
                    if (pdShip.getOwner() == ship.getOwner()) {
                        continue;
                    }

                    if (pdShip.getCaptain() == null || pdShip.getCaptain().isDefault()) {
                        continue;
                    }

                    if (pdShip.getCaptain().getStats().getSkillLevel("point_defense") > 0) {

                        Vector2f loc = new Vector2f(pdShip.getLocation().x, pdShip.getLocation().y + pdShip.getCollisionRadius() + 20f);
                        if (ship.getShipTarget() != null) {
                            if (ship.getShipTarget() == pdShip) {
                                loc = new Vector2f(pdShip.getLocation().x + pdShip.getCollisionRadius(), pdShip.getLocation().y + pdShip.getCollisionRadius() + 80f);
                            }
                        }
                        SpriteAPI warningSprite = Global.getSettings().getSprite("misc", "armaa_pdWarning");
                        MagicRender.battlespace(
                                warningSprite,
                                loc,
                                pdShip.getVelocity(),
                                new Vector2f(64, 64),
                                new Vector2f(),
                                0f,
                                0f,
                                new Color(217, 102, 37, 200),
                                false,
                                0.1f,
                                0.3f,
                                0.1f
                        );
                    }
                }
            }
        }

        if (Global.getCombatEngine() != null) {
            if (!(Global.getCombatEngine().getCustomData().get("armaa_strikecraftLanded" + ship.getId()) instanceof Boolean)) {
                Global.getCombatEngine().getCustomData().put("armaa_strikecraftLanded" + ship.getId(), false);
            }

            if (Global.getCombatEngine().getCustomData().get("armaa_strikecraftPilot" + ship.getId()) instanceof Float == false) {
                getPilotPersonality(ship);
            } else {
                HPPercent = (float) Global.getCombatEngine().getCustomData().get("armaa_strikecraftPilot" + ship.getId());
            }
        }

        float CurrentHull = ship.getHitpoints();
        float MaxHull = ship.getMaxHitpoints();
        float CurrentCR = ship.getCurrentCR();
        boolean needsrefit = false;
        ShipAPI target = null;
        boolean selectedCarrier = false;

        if (Global.getCombatEngine().getPlayerShip() == ship) {
            if (ship.getShipTarget() != null) {
                target = ship.getShipTarget();
            }
            if (!ship.isFinishedLanding()) {
                if (target != null && (target.isAlly() || target.getOwner() == ship.getOwner()) && (!target.isFrigate() || target.isStationModule()) && target.getNumFighterBays() > 0) {
                    selectedCarrier = true;
                }
            }
        }

        if (((CurrentHull < armaa_utils.getMaxHPRepair(ship)) || (CurrentCR < armaa_utils.getMaxCRRepair(ship)) || (needsReload(ship)) || (selectedCarrier)) && canRefit(ship)) {
            if (!ship.isStationModule() || ship.getStationSlot() == null) {
                needsrefit = checkRefitStatus(ship, amount);
            }
        }

        if (ship.getFluxTracker().isOverloaded()) {
            textInterval.advance(amount);
            float shipRadius = armaa_utils.effectiveRadius(ship);
            if (textInterval.intervalElapsed()) {
                for (int i = 0; i < 1; i++) {

                    Vector2f targetPoint = MathUtils.getRandomPointInCircle(ship.getLocation(), (shipRadius * 0.75f + 15f) * 1f);
                    Vector2f anchorPoint = MathUtils.getRandomPointInCircle(ship.getLocation(), shipRadius);
                    AnchoredEntity anchor = new AnchoredEntity(ship, anchorPoint);
                    float thickness = (float) Math.sqrt(((shipRadius * 0.025f + 5f) * 1f) * MathUtils.getRandomNumberInRange(0.75f, 1.25f)) * 3f;
                    Color coreColor = new Color(TEXT_COLOR.getRed(), TEXT_COLOR.getGreen(), TEXT_COLOR.getBlue(), 255);
                    Color overloadColor = new Color(ship.getOverloadColor().getRed(), ship.getOverloadColor().getGreen(), ship.getOverloadColor().getBlue(), 100);
                    EmpArcEntityAPI arc = Global.getCombatEngine().spawnEmpArcPierceShields(ship, targetPoint, anchor, anchor, DamageType.ENERGY,
                            0f, 0f, shipRadius, null, thickness, ship.getOverloadColor(), coreColor);
                }
                if (!Global.getCombatEngine().hasAttachedFloaty(ship) && !isFrig) {
                    Global.getCombatEngine().addFloatingTextAlways(new Vector2f(ship.getLocation().x, ship.getLocation().y + ship.getCollisionRadius()), "Overloaded!", 20f, TEXT_COLOR, ship, 1f, 1f, 1f, 1f, 1f, .5f);
                }
            }
        }

        if (canRetreat(ship) || (ship.getTravelDrive() != null && ship.getTravelDrive().isOn()) || needsrefit) {
            if (!isFrig) {
                adjustHullSize(ship, HullSize.FRIGATE);
            }
            if (needsrefit && !Global.getCombatEngine().isPaused() && ship.getOwner() == 0 && repairInterval.intervalElapsed()) {
                SpriteAPI repairSprite = Global.getSettings().getSprite("ui", "icon_repair_refit");
                MagicRender.objectspace(
                        repairSprite,
                        ship,
                        new Vector2f(0f, ship.getCollisionRadius()),
                        new Vector2f(),
                        new Vector2f(30, 30),
                        new Vector2f(-1f, -1f),
                        0f,
                        0f,
                        false,
                        new Color(0, 200, 0, 200),
                        true,
                        amount,
                        amount,
                        .2f,
                        true
                );
            }
        }

        if (ship.getOriginalOwner() == -1 || Global.getCombatEngine().isCombatOver() || Global.getCombatEngine().isPaused()) {
            if (!isFrig) {
                adjustHullSize(ship, HullSize.FRIGATE);
            }
        }

        boolean ally = ship.isAlly();
        CombatFleetManagerAPI cfm = Global.getCombatEngine().getFleetManager(ship.getOwner());
        CombatTaskManagerAPI ctm = cfm.getTaskManager(ally);
        if (ship.isRetreating() && (ship.isAlly() || ship.getOwner() == 1) && !ctm.isInFullRetreat()) {
            ShipAPI carrier = getNearestCarrier(ship);
            if (carrier != null && MathUtils.getDistance(ship, carrier) < 2000) {
                ctm.orderSearchAndDestroy(cfm.getDeployedFleetMember(ship), false);
            }
        }
    }

    //This listener ensures we die properly
    public static class StrikeCraftDeathMod implements DamageTakenModifier, AdvanceableListener {

        protected ShipAPI ship;

        public StrikeCraftDeathMod(ShipAPI ship) {
            this.ship = ship;
        }

        public void advance(float amount) {
        }

        public String modifyDamageTaken(Object param, CombatEntityAPI target, DamageAPI damage, Vector2f point, boolean shieldHit) {

            if (!(target instanceof ShipAPI)) {
                return null;
            }

            if (shieldHit) {
                return null;
            }
            ShipAPI s = (ShipAPI) target;
            String id = "strikecraft_death";

            float level = ship.getCaptain().isDefault() ? 0 : ship.getCaptain().getStats().getLevel();
            //float bonus = ship.getVariant().getSModdedBuiltIns().contains("cataphract2") ? 1f - (1f - 0.02f*level) : 1f;
            if (damage.getStats() != null) {
                if (ship.getVariant().hasHullMod("armaa_targetingDisruptor")) {
                    Float hullSizeMult = (float) DAMAGE_MAP.get(damage.getStats().getVariant().getHullSize());
                    if (hullSizeMult != null) {
                        damage.getModifier().modifyMult(id, hullSizeMult);
                    }
                } else {
                    float mult = Math.max(1f, Math.min(1.25f, damage.getStats().getDamageToFighters().getModifiedValue()));
                    if (mult > 1.05f && Math.random() < 0.05f) { // only flash if bonus is significant (say >5% bonus)
                        Color textColor = mult >= 1.25f ? Color.RED : (mult >= 1.15f ? Color.ORANGE : Color.YELLOW);
                        Global.getCombatEngine().addFloatingText(
                                ship.getLocation(),
                                "PD-Optimized strike! +" + mult + "x dmg",
                                16f, // text size
                                textColor,
                                ship,
                                0.5f, // fade in time
                                1.0f // full visible time
                        );
                    }
                    damage.getModifier().modifyMult(id, mult);
                }
            }
            return id;
        }
    }
}
