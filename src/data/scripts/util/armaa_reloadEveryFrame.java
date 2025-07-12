package data.scripts.util;

import java.util.EnumSet;

import java.awt.Color;

import org.lazywizard.lazylib.MathUtils;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import java.util.List;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.input.InputEventAPI;
import java.util.ArrayList;
import org.magiclib.util.*;

public class armaa_reloadEveryFrame extends BaseEveryFrameCombatPlugin {

    private ShipAPI ship;

    private static float DAMAGE_BONUS = 2f;
    private float count = 1f;
    private boolean recoiling = false;
    private IntervalUtil buffTimer = new IntervalUtil(3f, 3f);
    private float hitStrength;
    private WeaponAPI wep;

    public armaa_reloadEveryFrame(ShipAPI ship, int hitStrength) {
        this.ship = ship;
        this.hitStrength = hitStrength;
        if (this.hitStrength == 1f) {
            this.hitStrength = 1.5f;
        }
        for (WeaponAPI weapon : ship.getAllWeapons()) {
            if (weapon.getId().equals("armaa_musha_rightPauldron_frig")) {
                this.wep = weapon;
            }
        }
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        //don't run while paused because duh
        if (Global.getCombatEngine().isPaused()) {
            return;
        }

        MutableShipStatsAPI stats = ship.getMutableStats();
        //stats.getBallisticRoFMult().modifyMult(ship.getId(),DAMAGE_BONUS);
        //stats.getBallisticWeaponFluxCostMod().modifyMult(ship.getId(),DAMAGE_BONUS);
        //stats.getBallisticWeaponDamageMult().modifyMult(ship.getId(), DAMAGE_BONUS);
        ship.setWeaponGlow(buffTimer.getIntervalDuration() - buffTimer.getElapsed(), Color.red, EnumSet.of(WeaponAPI.WeaponType.BALLISTIC));
        buffTimer.advance(amount);
        if (wep.getChargeLevel() == 1) {
            Global.getSoundPlayer().playSound("armaa_rcl_fire", 0.8f, 1f, ship.getLocation(), new Vector2f());
            float angle = 180 + wep.getCurrAngle();
            List<DamagingProjectileAPI> toRemove = new ArrayList<>();
            for (DamagingProjectileAPI proj : Global.getCombatEngine().getProjectiles()) {
                if (proj.getWeapon() == wep) {
                    toRemove.add(proj);
                }
            }
            for (DamagingProjectileAPI proj : toRemove) {
                Global.getCombatEngine().removeEntity(proj);
            }
            toRemove.clear();            
            Vector2f point = MathUtils.getPoint(wep.getFirePoint(0), 45, angle);
            MagicFakeBeam.spawnFakeBeam(
                    Global.getCombatEngine(),
                    wep.getFirePoint(0),
                    wep.getRange()*1.10f,
                    wep.getCurrAngle(),
                    5f * hitStrength,
                    0.15f,
                    0.25f,
                    200,
                    Color.white,
                    Color.white,
                    wep.getDamage().getDamage() * hitStrength,
                    DamageType.KINETIC,
                    200f*hitStrength,
                    ship);
            for (int i = 0; i < 20; i++) {
                Vector2f vel = MathUtils.getRandomPointInCone(new Vector2f(), 100, angle - 20, angle + 20);
                vel.scale((float) Math.random());
                Vector2f.add(vel, wep.getShip().getVelocity(), vel);
                float grey = MathUtils.getRandomNumberInRange(0.5f, 0.75f);
                Global.getCombatEngine().addSmokeParticle(
                        MathUtils.getRandomPointInCircle(wep.getFirePoint(0), 5),
                        vel,
                        MathUtils.getRandomNumberInRange(5, 20),
                        MathUtils.getRandomNumberInRange(0.25f, 0.75f),
                        MathUtils.getRandomNumberInRange(0.25f, 1f),
                        new Color(grey, grey, grey, MathUtils.getRandomNumberInRange(0.25f, 0.75f))
                );
            }
            //debris
            for (int i = 0; i < 10; i++) {
                Vector2f vel = MathUtils.getRandomPointInCone(new Vector2f(), 250, angle - 20, angle + 20);
                Vector2f.add(vel, wep.getShip().getVelocity(), vel);
                Global.getCombatEngine().addHitParticle(
                        point,
                        vel,
                        MathUtils.getRandomNumberInRange(2, 4),
                        1,
                        MathUtils.getRandomNumberInRange(0.05f, 0.25f),
                        new Color(255, 125, 50)
                );
            }
            //flash
            Vector2f vel = MathUtils.getRandomPointInCone(new Vector2f(), 100, angle - 20, angle + 20);
            vel.scale((float) Math.random());
            Vector2f.add(vel, wep.getShip().getVelocity(), vel);
            Global.getCombatEngine().addHitParticle(
                    point,
                    vel,
                    100,
                    0.5f,
                    0.5f,
                    new Color(255, 20, 10)
            );
            Global.getCombatEngine().addHitParticle(
                    point,
                    vel,
                    80,
                    0.75f,
                    0.15f,
                    new Color(255, 200, 50)
            );
            Global.getCombatEngine().addHitParticle(
                    point,
                    vel,
                    50,
                    1,
                    0.05f,
                    new Color(255, 200, 150)
            );

            point = MathUtils.getPoint(wep.getFirePoint(0), 0, angle);
            //flash
            Global.getCombatEngine().addHitParticle(
                    point,
                    ship.getVelocity(),
                    100,
                    0.5f,
                    0.5f,
                    new Color(255, 20, 10)
            );
            Global.getCombatEngine().addHitParticle(
                    point,
                    ship.getVelocity(),
                    80,
                    0.75f,
                    0.15f,
                    new Color(255, 200, 50)
            );
            Global.getCombatEngine().addHitParticle(
                    point,
                    ship.getVelocity(),
                    50,
                    1,
                    0.05f,
                    new Color(255, 200, 150)
            );
            if (!recoiling) {
                recoiling = true;
            }
        }

        if (recoiling && count > 0f) {
            count -= amount;
            ship.setFacing(ship.getFacing() - count);
        }

        if (count <= 0f) {
            count = 0.4f;
            recoiling = false;
        }
        if (buffTimer.intervalElapsed() || !ship.isAlive()) {
            //stats.getBallisticWeaponFluxCostMod().unmodify(ship.getId());
            stats.getBallisticWeaponDamageMult().unmodify(ship.getId());
            //stats.getBallisticRoFMult().unmodify(ship.getId());
            ship.setWeaponGlow(0f, new Color(255, 125, 50, 220), EnumSet.of(WeaponAPI.WeaponType.BALLISTIC, WeaponAPI.WeaponType.MISSILE, WeaponAPI.WeaponType.ENERGY));
            Global.getCombatEngine().removePlugin(this);
        }
    }

}
