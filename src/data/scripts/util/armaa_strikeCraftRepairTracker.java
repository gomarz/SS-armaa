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
private static final float BAY_DRAIN_PER_SERVICE = 0.4f; // total repl rate consumed
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
    private float arrivalArmor = 1f;
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
        if (Global.getSettings().getModManager().isModEnabled("lunalib") == true) {
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
        if (Global.getSettings().getModManager().isModEnabled("lunalib") == true) {
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

    // True if there is any pool left to spend at all.
    public static boolean hasRepairPool(ShipAPI ship) {
        if (ship.getFleetMember() == null) {
            return true;
        }
        return getRepairPool(ship) > 0f;
    }

    // 0..1 ow much of a full repair the pool can afford, based on arrival deficit.
    private float getAffordableFraction(ShipAPI ship) {
        if (ship.getFleetMember() == null) {
            return 1f;
        }
        float dp = getDP(ship);
        float damageFraction = Math.max(0f, 1f - arrivalHull);
        float maxCR = Math.max(0.01f, armaa_utils.getMaxCRRepair(ship));
        float crFraction = Math.max(0f, 1f - (arrivalCR / maxCR)) * 0.5f;
        float fullCost = dp * (damageFraction + crFraction);
        if (fullCost <= 0f) {
            return 1f; // nothing to repair
        }
        float pool = getRepairPool(ship);
        return Math.min(1f, pool / fullCost);
    }

    // Bill for what actually healed (hull + CR). Works identically for full
    // completion and abort  abort simply healed less, so it costs less.
    private void deductRepairCost(ShipAPI ship) {
        if (ship.getFleetMember() == null) {
            return;
        }
        initPoolIfAbsent(ship);
        String poolKey = "armaa_repairPool_" + ship.getId();

        float dp = getDP(ship);
        float hullHealed = Math.max(0f, ship.getHullLevel() - arrivalHull); // fraction of max HP
        float maxCR = Math.max(0.01f, armaa_utils.getMaxCRRepair(ship));
        float crHealed = Math.max(0f, (ship.getCurrentCR() - arrivalCR) / maxCR) * 0.5f;

        float cost = dp * (hullHealed + crHealed);

        float pool = getRepairPool(ship);
        Global.getCombatEngine().getCustomData().put(poolKey, Math.max(0f, pool - cost));
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (Global.getCombatEngine().isPaused()) {
            return;
        }
        for (FleetMemberAPI member : fleetManager.getRetreatedCopy()) {
            if (member == carrierMember) {
                //fleetManager.addToReserves(ship.getFleetMember());
                ship.setRetreating(true, false);
                ship.getLocation().set(0, -1000000f);
                Global.getCombatEngine().removePlugin(this);
                return;
            }
        }

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
            arrivalArmor = armaa_utils.getArmorPercent(ship);
            initPoolIfAbsent(ship);
            Global.getCombatEngine().getCustomData().put("armaa_hangarIsOpen" + carrier.getId() + "_" + bayNo, true);
        }

        float refitMod = getCarrierRefitRate();
        float wepMalus = 0f;

        if (Global.getCombatEngine().getCustomData().get("armaa_strikecraftTotalMalus" + ship.getId()) instanceof Float) {
            wepMalus = (float) Global.getCombatEngine().getCustomData().get("armaa_strikecraftTotalMalus" + ship.getId());
        }

        float hullBonus = (float) Math.max(ship.getHullLevel() * 1.5f, 1f);
        float refitRate = (amount * hullBonus) * (1f - wepMalus) * refitMod;
        float adjustedRate = (float) Math.max(refitRate, amount * BaseTimer);
                adjustedRate*= carrier.getMutableStats().getDynamic().getStat("armaa_strikeCraftRefitMod").getModifiedValue();

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

        // Gradual hull/CR/armor restoration, capped at the fraction the pool can afford.
        float fraction = getAffordableFraction(ship);

        if (fraction > 0f) {
            // Normalized refit progress (0..1). Already carrier-rate-scaled, because
            // BASE_REFIT is advanced by adjustedRate. This is the speed driver.
            float progress = Math.min(1f, BASE_REFIT.getElapsed() / BASE_REFIT.getMaxInterval());

            float maxHP = armaa_utils.getMaxHPRepair(ship);
            float arrivalHP = arrivalHull * ship.getMaxHitpoints();
            float hpCeiling = Math.min(arrivalHP + (maxHP - arrivalHP) * fraction, ship.getMaxHitpoints());

            float maxCR = armaa_utils.getMaxCRRepair(ship);
            float crCeiling = arrivalCR + (maxCR - arrivalCR) * fraction;

            float armorCeil = arrivalArmor + (1f - arrivalArmor) * fraction;

            // Lerp arrival -> ceiling by progress. All three finish exactly when the
            // refit timer completes, at whatever speed adjustedRate dictated.
            ship.setHitpoints(Math.min(arrivalHP + (hpCeiling - arrivalHP) * progress, hpCeiling));

            if (ship.getCurrentCR() < crCeiling) {
                ship.setCurrentCR(Math.min(arrivalCR + (crCeiling - arrivalCR) * progress, crCeiling));
            }

            armaa_utils.setArmorPercentage(ship,
                    Math.min(arrivalArmor + (armorCeil - arrivalArmor) * progress, armorCeil));
        }

        ship.getMutableStats().getCRLossPerSecondPercent().modifyMult(ship.getId(), 0.001f);
        if (hasLanded) {
            ship.getMutableStats().getHullDamageTakenMult().modifyMult("armaa_invincible", 0f);
            ship.getMutableStats().getArmorDamageTakenMult().modifyMult("armaa_invincible", 0f);
        }

        //reduce bay repl rate
        if (carrier.getLaunchBaysCopy() != null && carrier.getLaunchBaysCopy().size() - 1 >= bayNo) {
            carrier.getLaunchBaysCopy().get(bayNo).setCurrRate(carrier.getLaunchBaysCopy().get(bayNo).getCurrRate() - adjustedRate * (BAY_DRAIN_PER_SERVICE / BASE_REFIT.getMaxInterval()));
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
            ship.getMutableStats().getWeaponMalfunctionChance().unmodify("cr_effect");
            ship.getMutableStats().getCriticalMalfunctionChance().unmodify("cr_effect");
            ship.getMutableStats().getEngineMalfunctionChance().unmodify("cr_effect");
            ship.getMutableStats().getShieldMalfunctionChance().unmodify("cr_effect");
            // Ammo always restores.
            List<WeaponAPI> weapons = ship.getAllWeapons();
            for (WeaponAPI w : weapons) {
                if (w.usesAmmo()) {
                    w.resetAmmo();
                }
            }            
        }
        // PPT always recovers, regardless of pool or abort.
        if (Global.getCombatEngine().getCustomData().get("armaa_strikeCraft_pptSnapshot_" + ship.getId()) != null) {
            StatBonus snapshot = (StatBonus) Global.getCombatEngine().getCustomData()
                    .get("armaa_strikeCraft_pptSnapshot_" + ship.getId());
            ship.getMutableStats().getPeakCRDuration().unmodify();
            ship.getMutableStats().getPeakCRDuration().applyMods(snapshot);
        }
        ship.getMutableStats().getPeakCRDuration().modifyFlat("armaa_strikeCraftRepair_" + ship.getId(), ship.getTimeDeployedForCRReduction());

        // Bill for what actually healed (hull + CR). Abort healed less, so costs less.
        deductRepairCost(ship);

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
        ship.clearDamageDecals();
        ship.syncWithArmorGridState();
        ship.syncWeaponDecalsWithArmorDamage();
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
