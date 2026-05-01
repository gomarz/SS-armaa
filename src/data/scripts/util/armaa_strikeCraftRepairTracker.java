package data.scripts.util;

import java.util.*;

import org.lwjgl.util.vector.Vector2f;
import org.lwjgl.input.Keyboard;

import lunalib.lunaSettings.LunaSettings;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipEngineControllerAPI.ShipEngineAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.loading.WeaponSlotAPI;

public class armaa_strikeCraftRepairTracker extends BaseEveryFrameCombatPlugin {

    private static float REPAIR_POOL_DEFAULT = 50f;
    private static final String REPAIR_POOL_TAG_PREFIX = "armaa_repairPool_";
    private static final float REPAIR_POOL_MIN_COST_FRACTION = 0.1f;
    // Pool threshold as fraction of DP to allow hull/CR repair (half a "full repair")
    private static final float REPAIR_POOL_HULLCR_THRESHOLD_FRACTION = 0.5f;

    private final IntervalUtil BASE_REFIT = new IntervalUtil(25f, 25f);
    private CombatFleetManagerAPI fleetManager;
    private ShipAPI carrier;
    private ShipAPI ship;
    private Vector2f landingLocation;
    private boolean hasLanded = false;
    private int bayNo;
    private WeaponSlotAPI w;
    private String timer = "0";
    private final float BaseTimer = .30f;

    private float arrivalHull = 1f;
    private float arrivalCR = 1f;
    private FleetMemberAPI carrierMember;
    public armaa_strikeCraftRepairTracker(ShipAPI ship, ShipAPI carrier, Vector2f landingLocation, int bayNo) {
        this.carrier = carrier;
        this.carrierMember = carrier.getFleetMember();
        this.ship = ship;
        this.landingLocation = landingLocation;
        this.bayNo = bayNo;
        fleetManager = Global.getCombatEngine().getFleetManager(ship.getOwner());
    }

    private float getInitialPool(ShipAPI ship) {
        for (String tag : ship.getHullSpec().getTags()) {
            if (tag.startsWith(REPAIR_POOL_TAG_PREFIX)) {
                try {
                    return Float.parseFloat(tag.substring(REPAIR_POOL_TAG_PREFIX.length()));
                } catch (NumberFormatException e) {
                }
            }
        }
            if (Global.getSettings().getModManager().isModEnabled("lunalib") == true) 
            {
                REPAIR_POOL_DEFAULT = LunaSettings.getInt("armaa", "armaa_repairPool");
            }
        return REPAIR_POOL_DEFAULT;
    }

    private float getDP(ShipAPI ship) {
        if (ship.getFleetMember() == null) {
            return 10f; // allback
        }
        return Math.max(1f, ship.getFleetMember().getDeploymentPointsCost());
    }

    // Called by armaa_strikeCraft for AI gating and UI display
    public static float getRepairPool(ShipAPI ship) {
        String poolKey = "armaa_repairPool_" + ship.getId();
        Object obj = Global.getCombatEngine().getCustomData().get(poolKey);
        // guh 
        if (Global.getSettings().getModManager().isModEnabled("lunalib") == true) 
        {
            REPAIR_POOL_DEFAULT = LunaSettings.getInt("armaa", "armaa_repairPool");
        }        
        return (obj instanceof Float) ? (float) obj : REPAIR_POOL_DEFAULT;
    }

    public static int getRepairsRemaining(ShipAPI ship) {
        if (ship.getFleetMember() == null) {
            return 99;
        }
        float pool = getRepairPool(ship);
        float dp = Math.max(1f, ship.getFleetMember().getDeploymentPointsCost());
        return (int) Math.floor(pool / dp);
    }

    private void initPoolIfAbsent(ShipAPI ship) {
        String poolKey = "armaa_repairPool_" + ship.getId();
        if (!Global.getCombatEngine().getCustomData().containsKey(poolKey)) {
            Global.getCombatEngine().getCustomData().put(poolKey, getInitialPool(ship));
        }
    }

    private void deductRepairCost(ShipAPI ship, boolean abort) {
        if (ship.getFleetMember() == null) {
            return;
        }

        initPoolIfAbsent(ship);
        String poolKey = "armaa_repairPool_" + ship.getId();

        float dp = getDP(ship);
        float damageFraction = Math.max(0f, 1f - arrivalHull);
        float maxCR = Math.max(0.01f, armaa_utils.getMaxCRRepair(ship));
        float crFraction = Math.max(0f, 1f - (arrivalCR / maxCR)) * 0.5f;
        float cost = dp * (damageFraction + crFraction);
        float minCost = dp * REPAIR_POOL_MIN_COST_FRACTION;
        cost = Math.max(cost, minCost);

        if (abort) {
            float repairProgress = BASE_REFIT.getElapsed() / BASE_REFIT.getMaxInterval();
            cost = Math.max(cost * repairProgress, minCost);
        }

        float currentPool = (float) Global.getCombatEngine().getCustomData().get(poolKey);
        Global.getCombatEngine().getCustomData().put(poolKey, Math.max(0f, currentPool - cost));
    }

    public static boolean canRepairHullCR(ShipAPI ship) {
        if (ship.getFleetMember() == null) {
            return true;
        }
        float pool = getRepairPool(ship);
        float dp = Math.max(1f, ship.getFleetMember().getDeploymentPointsCost());
        return pool >= dp * REPAIR_POOL_HULLCR_THRESHOLD_FRACTION;
    }

    public static boolean canRepairAmmo(ShipAPI ship) {
        if (ship.getFleetMember() == null) {
            return true;
        }
        float pool = getRepairPool(ship);
        float dp = Math.max(1f, ship.getFleetMember().getDeploymentPointsCost());
        return pool >= dp * REPAIR_POOL_MIN_COST_FRACTION;
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (Global.getCombatEngine().isPaused()) {
            return;
        }
        for (FleetMemberAPI member : fleetManager.getRetreatedCopy()) {
            if (member == carrierMember) {
                fleetManager.addToReserves(ship.getFleetMember());
                ship.setRetreating(true, false);
                ship.getLocation().set(0, -1000000f);
                Global.getCombatEngine().removePlugin(this);
                return;
            }
        }
        /* No longer needed. Probably
        if (carrier == null && carrierMember != null && fleetManager.getRetreatedCopy().contains(carrierMember)) {
            Global.getCombatEngine().getFleetManager(ship.getOwner()).getTaskManager(true).orderRetreat(fleetManager.getDeployedFleetMember(ship), false, false);
            ship.getLocation().setY(-1000000);
            Global.getCombatEngine().removePlugin(this);
            return;
        }\
        */
        
        if (carrier == null || !carrier.isAlive() || !Global.getCombatEngine().isEntityInPlay(carrier) || carrier.isHulk() || carrier.getHitpoints() <= 0 || carrier.getOwner() != ship.getOwner()) {
            takeOff(ship, landingLocation, true);
            armaa_utils.destroy(ship);
        }

        if (!ship.isFinishedLanding() && !hasLanded) {
            return;
        }
        armaa_utils.setLocation(ship, landingLocation); //set to location of carrier in case of drift

        if (w != null) {
            landingLocation = w.computePosition(carrier);
            ship.setFacing(w.computeMidArcAngle(carrier));
        } else {
            landingLocation = carrier.getLocation();
        }

        Global.getCombatEngine().getCustomData().put("armaa_hangarIsOpen" + carrier.getId() + "_" + bayNo, true);

        if (!hasLanded) {
            hasLanded = true;
            arrivalHull = ship.getHullLevel();
            arrivalCR = ship.getCurrentCR();
            initPoolIfAbsent(ship);
            Global.getCombatEngine().getCustomData().put("armaa_hangarIsOpen" + carrier.getId() + "_" + bayNo, true);
        }

        float refitMod = getCarrierRefitRate();
        if (carrier.getVariant().hasHullMod("armaa_serviceBays")) {
            refitMod += .5f;
        }
        float wepMalus = 0f;

        if (Global.getCombatEngine().getCustomData().get("armaa_strikecraftTotalMalus" + ship.getId()) instanceof Float) {
            wepMalus = (float) Global.getCombatEngine().getCustomData().get("armaa_strikecraftTotalMalus" + ship.getId());
        }

        float hullBonus = (float) Math.max(ship.getHullLevel() * 1.5f, 1f);
        float refitRate = (amount * hullBonus) * (1f - wepMalus) * refitMod;
        float adjustedRate = (float) Math.max(refitRate, amount * BaseTimer);
        if (ship.isStationModule()) {
            ship.setShipAI(null);
        }
        BASE_REFIT.advance(adjustedRate);
        if (ship.getHullSize() != HullSize.FIGHTER) {
            ship.setHullSize(HullSize.FIGHTER);
        }
        float elapsed = BASE_REFIT.getElapsed();
        float maxinterval = BASE_REFIT.getMaxInterval();
        float refit_timer = Math.round((elapsed / maxinterval) * 100f);
        timer = String.valueOf(refit_timer);

        // Only do gradual hull/CR restoration if pool supports it
        boolean poolAllowsHullCR = canRepairHullCR(ship);

        if (poolAllowsHullCR) {
            float crLevel = ship.getCurrentCR() / 1f;
            float remainder = (armaa_utils.getMaxHPRepair(ship) - ship.getHitpoints()) * ((adjustedRate / maxinterval) * elapsed);
            float crRemainder = (1f - ship.getCurrentCR()) * crLevel * ((adjustedRate / maxinterval) * elapsed);
            float currArmor = armaa_utils.getArmorPercent(ship);
            float CRRefit = Math.min(ship.getCurrentCR() + crRemainder * amount, armaa_utils.getMaxCRRepair(ship));

            ship.setHitpoints(Math.min(ship.getHitpoints() + remainder * (elapsed / maxinterval), ship.getMaxHitpoints()));
            armaa_utils.setArmorPercentage(ship, currArmor + ((1f - currArmor) * (adjustedRate / maxinterval)) * elapsed * amount);
            if ((ship.getCurrentCR() < armaa_utils.getMaxCRRepair(ship))) {
                ship.setCurrentCR(CRRefit);
            }
        }

        ship.getMutableStats().getCRLossPerSecondPercent().modifyMult(ship.getId(), 0.001f);
        if (hasLanded) {
            ship.getMutableStats().getHullDamageTakenMult().modifyMult("armaa_invincible", 0f);
            ship.getMutableStats().getArmorDamageTakenMult().modifyMult("armaa_invincible", 0f);
        }

        //reduce bay repl rate
        if (carrier.getLaunchBaysCopy() != null && carrier.getLaunchBaysCopy().size() - 1 >= bayNo) {
            carrier.getLaunchBaysCopy().get(bayNo).setCurrRate(carrier.getLaunchBaysCopy().get(bayNo).getCurrRate() - (amount * (adjustedRate)));
        }

        String abortString = "";
        ShipAPI playerShip = Global.getCombatEngine().getPlayerShip();
        if (ship == playerShip && BASE_REFIT.getElapsed() >= 0f) {
            Global.getCombatEngine().maintainStatusForPlayerShip("AceSystem2", "graphics/ui/icons/icon_repair_refit.png", getBlinkyString("REPAIR STATUS"), String.valueOf(ship.getHullLevel() * 100f) + "%", true);
            abortString = Global.getSettings().getControlStringForEnumName("C2_TOGGLE_AUTOPILOT");
            Global.getCombatEngine().maintainStatusForPlayerShip("AceSystem", "graphics/ui/icons/icon_repair_refit.png", "RESTORING PPT/AMMO (PRESS " + abortString + " to abort)", timer + "%", true);
            // Show repair pool status to player
            int repairsLeft = getRepairsRemaining(ship);
            String poolDisplay = repairsLeft > 0 ? "REPAIR CAPACITY: " + repairsLeft : "REPAIR CAPACITY EXHAUSTED";
            Global.getCombatEngine().maintainStatusForPlayerShip("AceSystem3", "graphics/ui/icons/icon_repair_refit.png", poolDisplay, "", false);
        }

        boolean abort = Keyboard.isKeyDown(Keyboard.getKeyIndex(abortString));
        if (BASE_REFIT.intervalElapsed() || abort) {
            takeOff(ship, landingLocation, abort);
            ship.setShipSystemDisabled(false);
            ship.resetDefaultAI();

            for (WeaponGroupAPI w : ship.getWeaponGroupsCopy()) {
                if (!w.getActiveWeapon().usesAmmo()) {
                    w.toggleOn();
                }
            }
        }
    }

    private float getCarrierRefitRate() {
        if (carrier == null) {
            return 1;
        }
        return carrier.getSharedFighterReplacementRate();
    }

    private String getBlinkyString(String str) {
        long count200ms = (long) Math.floor(Global.getCombatEngine().getTotalElapsedTime(true) / 0.2f);
        if (count200ms % 2L == 0L) {
            return str + ": ";
        } else {
            return "";
        }
    }

    private void takeOff(ShipAPI ship, Vector2f landingLocation, boolean abort) {
        deductRepairCost(ship, abort);

        Global.getSoundPlayer().playSound("fighter_takeoff", 1f, 1f, ship.getLocation(), new Vector2f());
        ship.setInvalidTransferCommandTarget(false);
        ship.setCollisionClass(CollisionClass.SHIP);
        ship.getFluxTracker().forceOverload(1f);
        ship.getFluxTracker().stopOverload();
        ship.getFluxTracker().setCurrFlux(0f);
        Global.getCombatEngine().getCustomData().remove("armaa_strikecraftisLanding" + ship.getId());
        Global.getCombatEngine().getCustomData().put("armaa_strikecraft_hasWaypoint" + ship.getId(), false);
        Global.getCombatEngine().getCustomData().put("armaa_hangarIsOpen" + carrier.getId() + "_" + bayNo, false);

        armaa_utils.setLocation(ship, landingLocation); //set to location of carrier in case of drift

        if (!abort) {
            boolean poolAllowsHullCR = canRepairHullCR(ship);

            if (poolAllowsHullCR) {
                if ((ship.getCurrentCR() <= armaa_utils.getMaxCRRepair(ship))) {
                    ship.setCurrentCR(Math.max(ship.getCurrentCR(), armaa_utils.getMaxCRRepair(ship)));
                }
                ship.getMutableStats().getWeaponMalfunctionChance().unmodify("cr_effect");
                ship.getMutableStats().getCriticalMalfunctionChance().unmodify("cr_effect");
                ship.getMutableStats().getEngineMalfunctionChance().unmodify("cr_effect");
                ship.getMutableStats().getShieldMalfunctionChance().unmodify("cr_effect");
                ship.clearDamageDecals();
                ship.setHitpoints(Math.max(ship.getHitpoints(), armaa_utils.getMaxHPRepair(ship)));
                ship.getVariant().getHullSpec().getNoCRLossTime();
                if (ship.getMutableStats().getPeakCRDuration().computeEffective(0f) < ship.getTimeDeployedForCRReduction()) {
                    ship.getMutableStats().getPeakCRDuration().modifyFlat(ship.getId(), ship.getTimeDeployedForCRReduction());
                }
                ship.clearDamageDecals();
                armaa_utils.setArmorPercentage(ship, 100f);
            }

            List<WeaponAPI> weapons = ship.getAllWeapons();
            for (WeaponAPI w : weapons) {
                if (w.usesAmmo()) {
                    w.resetAmmo();
                }
            }
        }

        ((ShipAPI) ship).setAnimatedLaunch();
        ship.setControlsLocked(false);
        ship.setShipSystemDisabled(false);
        ship.getMutableStats().getCRLossPerSecondPercent().unmodify(ship.getId());
        for (ShipAPI modules : ship.getChildModulesCopy()) {
            modules.setHitpoints(modules.getMaxHitpoints());
            modules.getFluxTracker().stopOverload();
            modules.getFluxTracker().setCurrFlux(0f);
            modules.clearDamageDecals();
            armaa_utils.setArmorPercentage(modules, 100f);
            modules.setAnimatedLaunch();
        }
        if (ship.isRetreating()) {
            ship.setRetreating(false, false);
        }
        if (Global.getCombatEngine().getPlayerShip() == ship) {
            if (ship.getShipTarget() == carrier) {
                ship.setShipTarget(null);
            }
        }
        if (ship.isDefenseDisabled()) {
            ship.setDefenseDisabled(false);
        }
        if (ship.getEngineFractionPermanentlyDisabled() > 0) {
            for (ShipEngineAPI engine : ship.getEngineController().getShipEngines()) {
                if (engine.isPermanentlyDisabled()) {
                    engine.repair();
                }
            }
        }
        ship.setHullSize(HullSize.FRIGATE); //Einhander AI fix, fighter hullsize cannot resetDefaultAI, will crash
        ship.getFluxTracker().ventFlux();
        ship.resetDefaultAI();
        Global.getCombatEngine().getCustomData().remove("armaa_repairTracker_" + ship.getId());
        Global.getCombatEngine().getCustomData().put("armaa_cachedNeedsRefit_" + ship.getId(), false);
        Global.getCombatEngine().getCustomData().put("armaa_cachedCarrier_" + ship.getId(), null);
        Global.getCombatEngine().getCustomData().put("armaa_strikecraftLanded" + ship.getId(), false);
        ship.getMutableStats().getHullDamageTakenMult().unmodify("armaa_invincible");
        ship.getMutableStats().getArmorDamageTakenMult().unmodify("armaa_invincible");
        Global.getCombatEngine().removePlugin(this);
    }
}
