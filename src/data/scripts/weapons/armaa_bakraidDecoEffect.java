package data.scripts.weapons;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.WeaponAPI;
import java.util.List;

public class armaa_bakraidDecoEffect implements EveryFrameWeaponEffectPlugin {

    private ShipAPI ship;
    private float ogX, ogY;
    private boolean leftie;
    private float sinceB = 0f;
    // ---- TUNING ----
    private static final float SPEED = 0.4f;        // slower
    private static final float SLIDE_END = 0.6f;    // 60% slide, 40% rotate

    private static final float MAX_SLIDE = 14f;
    private static final float MAX_ROT = 25f;
    private boolean moduleDisabled = false;
    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (ship == null) {
            ship = weapon.getShip();
            ogX = weapon.getSprite().getCenterX();
            ogY = weapon.getSprite().getCenterY();
            // guess it doesnt work
            leftie = weapon.getSlot().getId().equals("E_LSHOULDER");
            return;
        }
        float delta = amount * SPEED;
        if (ship.areAnyEnemiesInRange() || moduleDisabled) {
            sinceB += delta;
        } else {
            sinceB -= delta;
        }
        if (sinceB < 0) {
            sinceB = 0;
        } else if (sinceB > 1) {
            sinceB = 1;
        }

        float slide;
        float rot;
        // -------- STAGE LOGIC --------
        if (sinceB <= SLIDE_END) {
            // Stage 1: slide only
            float t = sinceB / SLIDE_END;
            slide = MAX_SLIDE * t;
            rot = 0f;
        } else {
            // Stage 2: rotation only (slide is frozen)
            slide = MAX_SLIDE;
            float t = (sinceB - SLIDE_END) / (1f - SLIDE_END);
            rot = MAX_ROT * t;
        }

        float angle = leftie ? 1f : -1f;
        weapon.setFacing(ship.getFacing() + rot * angle);

        if (!leftie) {
            weapon.getSprite().setCenterX(ogX + angle * slide);
            weapon.getSprite().setCenterY(ogY + angle * slide);
        } else {
            weapon.getSprite().setCenterX(ogX + angle * slide);
            weapon.getSprite().setCenterY(ogY - angle * slide);
        }

        //I can't find where this was done before so
        List<ShipAPI> children = ship.getChildModulesCopy();
        if (children != null) {
            for (ShipAPI module : children) {
                module.ensureClonedStationSlotSpec();

                if (module.getStationSlot() != null) {
                    module.blockCommandForOneFrame(ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK);
                    if (ship.getFluxTracker().isOverloaded() && !module.getFluxTracker().isOverloaded()) {
                        module.getFluxTracker().beginOverloadWithTotalBaseDuration(ship.getFluxTracker().getOverloadTimeRemaining());
                    }
                    if(!module.getEngineController().isFlamedOut())
                        module.getEngineController().forceFlameout(true);
                    module.setCurrentCR(ship.getCurrentCR());
                    if(module.controlsLocked())
                        moduleDisabled = true;
                    else
                        moduleDisabled = false;
                }
            }
        }
    }
}
