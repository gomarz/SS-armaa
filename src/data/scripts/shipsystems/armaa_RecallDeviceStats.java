package data.scripts.shipsystems;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.HashMap;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.combat.ShipEngineControllerAPI.ShipEngineAPI;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import data.scripts.util.armaa_utils;
import org.lazywizard.lazylib.MathUtils;
import org.magiclib.util.MagicLensFlare;
import lunalib.lunaSettings.LunaSettings;

public class armaa_RecallDeviceStats extends BaseShipSystemScript {

    public static final Object KEY_JITTER = new Object();
    public static final Color JITTER_COLOR = new Color(100, 165, 255, 155);
    private static final Color BASIC_FLASH_COLOR = new Color(184, 155, 218, 200);
    private static final Color BASIC_GLOW_COLOR = new Color(180, 161, 255, 200);

    public static String getKeyByValue(Map<String, String> map, String value) {
        for (Entry<String, String> entry : map.entrySet()) {
            if (entry.getValue().equals(value)) {
                return entry.getKey();
            }
        }
        return null;
    }

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI ship;
        if (stats.getEntity() instanceof ShipAPI shipAPI) {
            ship = shipAPI;
        } else {
            return;
        }

        ShipAPI fighter = null;
        if (effectLevel > 0) {
            float jitterLevel = effectLevel;
            final String fightersKey = ship.getId() + "_recall_device_target";
            boolean firstTime = false;
            ShipAPI target = ship.getShipTarget();
            if (target != null && target.getOwner() == ship.getOwner() && target.getVariant().hasHullMod("strikeCraft")) {
                fighter = target;

            } else {
                List<ShipAPI> fighters;
                String recalledKey = "armaa_recalledFighters_" + ship.getOwner();
                HashMap<String, String> recalledFighters;
                if (Global.getCombatEngine().getCustomData().get(recalledKey) == null) {
                    recalledFighters = new HashMap<String, String>();
                    Global.getCombatEngine().getCustomData().put(recalledKey, recalledFighters);
                } else {
                    recalledFighters = (HashMap<String, String>) Global.getCombatEngine().getCustomData().get(recalledKey);
                }

                if (!Global.getCombatEngine().getCustomData().containsKey(fightersKey)) {
                    fighters = getFighters(ship);
                    Global.getCombatEngine().getCustomData().put(fightersKey, fighters);
                    firstTime = true;
                } else {
                    fighters = (List<ShipAPI>) Global.getCombatEngine().getCustomData().get(fightersKey);
                }
                if (fighters == null) {
                    // shouldn't be possible, but still
                    fighters = new ArrayList<ShipAPI>();
                }

                //if(firstTime)
                for (ShipAPI fighter2 : fighters) {
                    if (fighter2.isHulk()) {
                        continue;
                    }
                    // not null, fighter has been seen before
                    if (recalledFighters.get(fighter2.getId()) != null) {
                        // he's not our man, keep it pushin
                        if (!recalledFighters.get(fighter2.getId()).equals(ship.getId())) {
                            continue;
                        }
                    } else {
                        recalledFighters.put(fighter2.getId(), ship.getId());
                    }
                    fighter = fighter2;
                    break;
                }
            }
            if (fighter == null) {
                return;
            }
            float maxRangeBonus = fighter.getCollisionRadius() * 1f;
            float jitterRangeBonus = 5f + jitterLevel * maxRangeBonus;

            if (firstTime) {
                Global.getSoundPlayer().playSound("system_phase_skimmer", 1f, 0.5f, fighter.getLocation(), fighter.getVelocity());
            }

            fighter.setJitter(KEY_JITTER, JITTER_COLOR, jitterLevel, 10, 0f, jitterRangeBonus);
            if (fighter.isAlive()) {
                //fighter.setPhased(true);
            }

            if (state == State.IN) {
                float alpha = 1f - effectLevel * 0.5f;
                fighter.setExtraAlphaMult(alpha);
            }

            if (effectLevel == 1) {
                if (ship != null) 
                {
                    //fighter.setPhased(false);
                    fighter.getFluxTracker().forceOverload(1f);
                    if (fighter.getSystem() != null) {
                        fighter.getSystem().deactivate();
                    }
                    armaa_utils.setLocation(fighter, MathUtils.getRandomPointInCircle(ship.getLocation(), 500f));
                    fighter.setExtraAlphaMult(1f);
                    for (ShipEngineAPI engine : fighter.getEngineController().getShipEngines()) {
                        engine.repair();
                    }
                    MagicLensFlare.createSharpFlare(Global.getCombatEngine(), fighter, fighter.getLocation(), 5f, 100f, 0f, BASIC_FLASH_COLOR, Color.white);
                    Global.getCombatEngine().addSmoothParticle(fighter.getLocation(), new Vector2f(), 100f, 0.7f, 0.1f, BASIC_FLASH_COLOR);
                    Global.getCombatEngine().addSmoothParticle(fighter.getLocation(), new Vector2f(), 150f, 0.7f, 1f, BASIC_GLOW_COLOR);
                    Global.getCombatEngine().addHitParticle(fighter.getLocation(), new Vector2f(), 200f, 1f, 0.05f, Color.white);

                }
            }
        }
    }

    public static boolean isStrikeCraftNearCarrier(ShipAPI ship) {
        for (ShipAPI carrier : Global.getCombatEngine().getShips()) {
            if (ship.getOwner() != carrier.getOwner()) {
                continue;
            }
            if (!carrier.hasLaunchBays()) {
                continue;
            }
            if (carrier.getVariant().hasHullMod("strikeCraft")) {
                continue;
            }

            if (carrier.isAlive() && MathUtils.getDistance(ship, carrier) < 1000f) {
                return true;
            }
        }
        return false;
    }

    public static List<ShipAPI> getFighters(ShipAPI carrier) {
        List<ShipAPI> result = new ArrayList<>();

        for (ShipAPI ship : Global.getCombatEngine().getShips()) 
        {
            if (ship.getOwner() != carrier.getOwner()) {
                continue;
            }
            if (!ship.getVariant().hasHullMod("strikeCraft")) {
                continue;
            }
            if (ship.getVariant().hasHullMod("armaa_emergencyRecallDevice")) {
                String key = "armaa_" + ship.getId() + "_emergencyRecall_canRecall";
                boolean usedEmergencyRecall = Global.getCombatEngine().getCustomData().containsKey(key);
                // Let these guys use their emergency recall first
                // ship sys version doesn't guarantee survival
                if (!usedEmergencyRecall) {
                    continue;
                }
            }
            float hullThreshold = 0.50f;
            float crThreshold = .30f;
            if (Global.getSettings().getModManager().isModEnabled("lunalib")) {
                // someone wanted option to not be recalled  unless holding fire..so sure
                boolean playerRecallEnabled = LunaSettings.getBoolean("armaa", "armaa_playerRecall");
                if (Global.getCombatEngine().getPlayerShip() == ship) {
                    if (!playerRecallEnabled && !ship.isHoldFire()) {
                        continue;
                    }
                }
                // why is this a double anyway
                if(LunaSettings.getDouble("armaa", "armaa_repairLevel") != null)
                    hullThreshold = LunaSettings.getDouble("armaa", "armaa_repairLevel").floatValue();
                if(ship.getHitpoints() >= armaa_utils.getMaxHPRepair(ship) && ship.getCurrentCR() >= armaa_utils.getMaxCRRepair(ship))
                {
                    continue;
                } 
            }
            if (ship.getHullLevel() <= hullThreshold || ship.getCurrentCR() <= crThreshold || ship.getHullLevel() <= 0.50f && ship.getFluxTracker().isOverloaded()) {
                if (ship.isAlive() && MathUtils.getDistance(ship, carrier) > 1000f && !ship.controlsLocked() && !ship.isRetreating() && !isStrikeCraftNearCarrier(ship)) {
                    result.add(ship);
                }
            }
        }

        return result;
    }

    public void unapply(MutableShipStatsAPI stats, String id) {
        ShipAPI ship = null;
        if (stats.getEntity() instanceof ShipAPI) {
            ship = (ShipAPI) stats.getEntity();
        } else {
            return;
        }

        String recalledKey = "armaa_recalledFighters_" + ship.getOwner();
        final String fightersKey = ship.getId() + "_recall_device_target";
        if (Global.getCombatEngine().getCustomData().containsKey(fightersKey)) {
            Global.getCombatEngine().getCustomData().remove(fightersKey);
        }
        HashMap<String, String> recalledFighters = null;
        if (Global.getCombatEngine().getCustomData().get(recalledKey) != null) {
            recalledFighters = (HashMap<String, String>) Global.getCombatEngine().getCustomData().get(recalledKey);

            String fighterToRemove = getKeyByValue(recalledFighters, ship.getId());
            if (recalledFighters != null) {
                //ship.getFluxTracker().showOverloadFloatyIfNeeded(fighterToRemove, Color.yellow, 2f, true);
                recalledFighters.remove(fighterToRemove);
            }
            Global.getCombatEngine().getCustomData().put(recalledKey, recalledFighters);
        }
    }

    public StatusData getStatusData(int index, State state, float effectLevel) {
        return null;
    }

}
