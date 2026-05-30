package data.scripts.weapons;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.graphics.SpriteAPI;
import data.scripts.util.armaa_utils;
import java.awt.Color;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicRender;

public class armaa_pilaBladeEffect implements EveryFrameWeaponEffectPlugin {

    private float speed = 0f;

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (!Global.getCombatEngine().isEntityInPlay(weapon.getShip())) {
            return;
        }

        if (!MagicRender.screenCheck(0.1f, weapon.getLocation()) || Global.getCombatEngine().isPaused()) {
            return;
        }

        ShipAPI ship = weapon.getShip();

        if (!ship.isAlive()) {
            return;
        }
        float mult = -1f;
        if (!ship.areAnyEnemiesInRange()) {
            weapon.setRemainingCooldownTo(weapon.getCooldown());
            speed -= 0.01f;
        } else {
            speed += 0.01f;
        }

        if (speed < 0) {
            speed = 0;
        } else if (speed > 40) {
            speed = 40;
        }
        armaa_utils.makeAfterImages(ship, 0.15f, Global.getCombatEngine().getElapsedInLastFrame() / engine.getTimeMult().getModifiedValue(), Color.red);
        if (!weapon.isDisabled()) {
            weapon.setCurrAngle(weapon.getCurrAngle() + speed * mult);
            //Global.getSettings().loadTexture("graphics/armaa/ships/armaa_claw_lower.png");
            SpriteAPI spr = Global.getSettings().getSprite("misc", "armaa_claw_top");
            
            MagicRender.singleframe(spr, ship.getLocation(), new Vector2f(spr.getWidth()*ship.getCombinedAlphaMult(), spr.getHeight()*ship.getCombinedAlphaMult()), weapon.getCurrAngle() + 90f, new Color(1f,1f,1f,1f*ship.getCombinedAlphaMult()), false, CombatEngineLayers.ABOVE_SHIPS_LAYER);
            if (ship.getWing() != null && !ship.getWing().isReturning(ship) && ship.areAnyEnemiesInRange()) {
                weapon.setForceFireOneFrame(true);
            }
        }
        if (ship.getFluxLevel() >= 1f || ship.getFluxTracker().isOverloadedOrVenting()) {
            ship.getWing().orderReturn(ship);
        }
    }
}
