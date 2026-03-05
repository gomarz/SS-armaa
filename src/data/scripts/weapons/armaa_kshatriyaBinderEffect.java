package data.scripts.weapons;

import com.fs.starfarer.api.combat.*;
import java.util.List;
import org.lazywizard.lazylib.MathUtils;

public class armaa_kshatriyaBinderEffect implements EveryFrameWeaponEffectPlugin {

    private ShipAPI ship;
    private float currentRotateL = 0;
    private float currentRotateR = 0;
    private float sway = 0f;
    private final float maxbinderrotate = 40f;
    private int numModules = 4;
    public static float SHIELD_BONUS = 5f;
    public boolean init = false;
    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {

        ship = weapon.getShip();

        if (engine.isPaused()) {
            return;
        }
        animateBinders();

        // Modules
        List<ShipAPI> children = ship.getChildModulesCopy();
        if (children != null) {
            for (ShipAPI m : children) {
                m.ensureClonedStationSlotSpec();
                if (m.getStationSlot() == null) {
                    numModules--;
                    continue;
                }
                if(!init)
                {
                    m.setHullSize(ShipAPI.HullSize.FRIGATE);
                    m.resetDefaultAI();;
                }
                if (ship.getSystem().isActive()) {
                    m.useSystem();
                }
                if (ship.getFluxTracker().isVenting()) {
                    m.getFluxTracker().ventFlux();
                }
                if (ship.isPullBackFighters() == false) {
                    m.setPullBackFighters(false);
                } else {
                    m.setPullBackFighters(true);
                }
                if (ship.getShipTarget() != null) {
                    m.setShipTarget(ship.getShipTarget());
                }

                if (m.getLayer() != CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER) {
                    m.setLayer(CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER);
                }

                if (engine.getCustomData().get("" + m.getStationSlot().getId() + "_centerX") == null) {
                    engine.getCustomData().put("" + m.getStationSlot().getId() + "_centerX", m.getSpriteAPI().getCenterX());
                }
                if (engine.getCustomData().get("" + m.getStationSlot().getId() + "_centerY") == null) {
                    engine.getCustomData().put("" + m.getStationSlot().getId() + "_centerY", m.getSpriteAPI().getCenterY());
                }

                if (m.getStationSlot().getId().equals("BINDER_01") || m.getStationSlot().getId().equals("BINDER_02")) {
                    if (!m.getStationSlot().getId().equals("BINDER_01")) {
                        m.setFacing(m.getFacing() + currentRotateR);
                    } else {
                        engine.getCustomData().put(ship.getId() + "_BINDER_01", m);
                        m.setFacing(m.getFacing() + currentRotateR / 2);
                    }
                } else {
                    if (!m.getStationSlot().getId().equals("BINDER_04")) {
                        m.setFacing(m.getFacing() + currentRotateL);;
                    } else {
                        engine.getCustomData().put(ship.getId() + "_BINDER_04", m);
                        m.setFacing(m.getFacing() + currentRotateL / 2);
                    }
                }
            }
            init = true;
        }
        ship.getMutableStats().getShieldDamageTakenMult().modifyMult(ship.getId()+"_binders",1f-SHIELD_BONUS * numModules * 0.01f);
        
    }
    private void animateBinders()
    {
        float ltarget = 0;
        float rtarget = 0;

        if (ship.getEngineController().isAccelerating()) {
            ltarget += maxbinderrotate / 2; //=Math.min(maxbinderrotate, ltarget +((maxbinderrotate-overlap)*amount*5));
            rtarget -= maxbinderrotate / 2;
            if (sway > -1) {
                sway -= 0.08;
            }

        } else if (ship.getEngineController().isDecelerating() || ship.getEngineController().isAcceleratingBackwards()) {
            ltarget -= maxbinderrotate / 2;
            rtarget += maxbinderrotate / 2;
            if (sway < 1) {
                sway += 0.08;
            }
        }
        if (ship.getEngineController().isStrafingLeft()) {
            ltarget += maxbinderrotate / 3;
            rtarget += maxbinderrotate / 1.5f;
        } else if (ship.getEngineController().isStrafingRight()) {
            ltarget -= maxbinderrotate / 1.5f;
            rtarget -= maxbinderrotate / 3;
        }
        if (ship.getEngineController().isTurningLeft()) {
            ltarget -= maxbinderrotate / 2;
            rtarget -= maxbinderrotate / 2;
        } else if (ship.getEngineController().isTurningRight()) {
            ltarget += maxbinderrotate / 2;
            rtarget += maxbinderrotate / 2;
        }

        float rtl = MathUtils.getShortestRotation(currentRotateL, ltarget);
        if (Math.abs(rtl) < 0.5f) {
            currentRotateL = ltarget;
        } else if (rtl > 0) {
            currentRotateL += 0.4f;
        } else {
            currentRotateL -= 0.4f;
        }

        float rtr = MathUtils.getShortestRotation(currentRotateR, rtarget);
        if (Math.abs(rtr) < 0.5f) {
            currentRotateR = rtarget;
        } else if (rtr > 0) {
            currentRotateR += 0.4f;
        } else {
            currentRotateR -= 0.4f;
        }

        if (sway > 0) {
            sway -= 0.05;
        } else {
            sway += 0.05;
        }
    }
}
