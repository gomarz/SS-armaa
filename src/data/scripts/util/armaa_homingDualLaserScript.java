package data.scripts.util;

import data.scripts.weapons.armaa_counterShieldScript;
import java.awt.Color;
import java.util.*;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lazywizard.lazylib.MathUtils;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.lazywizard.lazylib.combat.CombatUtils;

public class armaa_homingDualLaserScript extends BaseEveryFrameCombatPlugin {

    private IntervalUtil interval = new IntervalUtil(0.08f, 0.08f);
    private ShipAPI ship;
    private float gauge;
    private float MAX_GAUGE;
    private boolean isFiring = false;
    private CombatEngineAPI engine;
    private WeaponAPI weapon;
    private boolean even = false;
    private float maxDamage = 1500f;
    private float maxProjs = 16f;
    private float guardBar = 0f;
    private SpriteAPI waveSprite;

    public armaa_homingDualLaserScript(ShipAPI ship, float gauge) {
        this.ship = ship;
        this.gauge = gauge;
        this.MAX_GAUGE = gauge;
        maxProjs = Math.max(1f, maxProjs * gauge);
        float damageAmt = ship.getFluxTracker().getCurrFlux() * 0.33f;
        maxDamage = damageAmt;
        engine = Global.getCombatEngine();
        weapon = ship.getAllWeapons().get(0);
        waveSprite = Global.getSettings().getSprite("misc", "armaa_sfxpulse");
        if (waveSprite != null) {
            MagicRender.battlespace(
                    waveSprite,
                    ship.getLocation(),
                    new Vector2f(ship.getVelocity().getX() / 2, ship.getVelocity().getY() / 2),
                    new Vector2f((float) Math.random() * ship.getCollisionRadius(), (float) Math.random() * ship.getCollisionRadius()),
                    new Vector2f(300f, 300f),
                    5f,
                    5f,
                    new Color(128f / 255f, 180f / 255f, 242f / 255f, Math.max(0f, gauge) / MAX_GAUGE),
                    true,
                    .1f,
                    .2f,
                    .4f
            );
        }
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        boolean state = engine.getCustomData().get("armaa_tranformState_" + ship.getId()) != null
                ? (Boolean) engine.getCustomData().get("armaa_tranformState_" + ship.getId()) : false;
        if (gauge <= 0f || MAX_GAUGE * (1f - 0.33f) >= gauge || maxProjs <= 0) {
            isFiring = false;
            Global.getCombatEngine().getCustomData().put("armaa_morgana_absorbed_" + ship.getId(), 0f);
            Global.getCombatEngine().getCustomData().remove("armaa_morgana_absorbed_" + ship.getId() + "_countering");
            engine.removePlugin(this);
        } else {
            if (gauge > 0f) {
                isFiring = true;
            }
        }

        if (isFiring == true) {
            interval.advance(amount);
        }

        if (interval.intervalElapsed() && isFiring) {
            if (state == false) {
                interval = new IntervalUtil(0.05f, 0.05f);
                String weaponId = "armaa_percept_homing";
                String weaponId2 = "armaa_percept_homing";
                Vector2f muzzleLocation = weapon.getSpec().getTurretFireOffsets().get(0);
                int level = 1;
                if (guardBar < 0.50f) {
                    level = 2;
                }
                for (int i = 0; i < level; i++) {

                    ShipAPI target1 = null;
                    if (ship.getWeaponGroupFor(weapon) != null) {
                        //WEAPON IN AUTOFIRE
                        if (ship.getWeaponGroupFor(weapon).isAutofiring() //weapon group is autofiring
                                && ship.getSelectedGroupAPI() != ship.getWeaponGroupFor(weapon)) { //weapon group is not the selected group
                            target1 = ship.getWeaponGroupFor(weapon).getAutofirePlugin(weapon).getTargetShip();
                        } else {
                            target1 = ship.getShipTarget();

                        }
                    }

                    if (target1 == null) {
                        for (ShipAPI target : CombatUtils.getShipsWithinRange(ship.getLocation(), 2000f)) {
                            if (target.getOwner() == ship.getOwner()) {
                                continue;
                            }

                            if (!target.isAlive() || target.isHulk()) {
                                continue;
                            }

                            target = target1;
                        }
                    }

                    float angle = even ? 180f + ship.getFacing() + (float) Math.random() * 90f : 180f + ship.getFacing() - (float) Math.random() * 90f;
                    String id = weaponId;
                    if (gauge > 0.50f) {
                        id = weaponId2;
                    }
                    Global.getCombatEngine().getCustomData().put("armaa_percept_homing_fluxLevel_"+ship.getId(),ship.getFluxLevel());
                    DamagingProjectileAPI proj = (DamagingProjectileAPI) engine.spawnProjectile(ship, weapon, id, MathUtils.getRandomPointInCircle(weapon.getLocation(), 10f), angle, new Vector2f());

                    engine.addNebulaSmokeParticle(ship.getLocation(),
                            new Vector2f((float) Math.random() * (proj.getVelocity().getX() / 4 + ship.getVelocity().getX()), (proj.getVelocity().getY() / 4 + ship.getVelocity().getY()) * (float) Math.random()),
                            40f * (0.75f + (float) Math.random() * 0.5f),
                            3f + 1f * (float) Math.random() * 2f,
                            0f,
                            0f,
                            1f,
                            new Color(128f / 255f, 180f / 255f, 242f / 255f, Math.max(0f, gauge) / MAX_GAUGE));

                    if (level > 2) {
                        proj.setHitpoints(proj.getHitpoints() * 1.5f);
                    }
                    float variance = MathUtils.getRandomNumberInRange(-0.6f, 0f);

                    Global.getSoundPlayer().playSound("vajra_fire", 1f + variance, 1f + variance, proj.getLocation(), proj.getVelocity());
                    if (MagicRender.screenCheck(0.25f, proj.getLocation())) {
                        engine.addSmoothParticle(ship.getLocation(), ship.getVelocity(), 100, 0.5f, 0.25f, new Color(128f / 255f, 180f / 255f, 242f / 255f, Math.max(0f, gauge) / MAX_GAUGE));
                        //engine.addHitParticle(proj.getLocation(), new Vector2f(), 50, 1f, 0.1f, Color.white);
                    }
                    maxProjs--;
                    Global.getCombatEngine().getCustomData().put("armaa_morgana_absorbed_" + ship.getId(), maxDamage);
                    Global.getCombatEngine().getCustomData().put("armaa_morgana_absorbed_" + ship.getId(), gauge);
                    if (gauge <= 0) {
                        ship.getSystem().deactivate();
                        isFiring = false;
                    }
                    if (engine.isEntityInPlay(proj) && !proj.didDamage()) {
                        engine.addPlugin(new armaa_counterShieldScript(proj, target1));
                        //alreadyRegisteredProjectiles.add(projectile);
                    }
                    even = even == true ? false : true;
                }
            } else {
                //interval = new IntervalUtil(amount,amount);
                float variance = MathUtils.getRandomNumberInRange(-0.6f, -1f);
                Global.getSoundPlayer().playSound("vajra_fire", 0.8f + variance, 1f, ship.getLocation(), ship.getVelocity());
                engine.addNebulaParticle(ship.getLocation(),
                        new Vector2f(ship.getVelocity().getX(), ship.getVelocity().getY()),
                        40f * (0.75f + (float) Math.random() * 0.5f),
                        3f + 1f * (float) Math.random() * 2f,
                        0f,
                        0f,
                        1f,
                        new Color(255, 255, 255, 155));

                ShipAPI target1 = null;
                if (ship.getWeaponGroupFor(weapon) != null) {
                    //WEAPON IN AUTOFIRE
                    if (ship.getWeaponGroupFor(weapon).isAutofiring() //weapon group is autofiring
                            && ship.getSelectedGroupAPI() != ship.getWeaponGroupFor(weapon)) { //weapon group is not the selected group
                        target1 = ship.getWeaponGroupFor(weapon).getAutofirePlugin(weapon).getTargetShip();
                    } else {
                        target1 = ship.getShipTarget();

                    }
                }

                if (target1 == null) {
                    for (ShipAPI target : CombatUtils.getShipsWithinRange(ship.getLocation(), 2000f)) {
                        if (target.getOwner() == ship.getOwner()) {
                            continue;
                        }

                        if (!target.isAlive() || target.isHulk()) {
                            continue;
                        }

                        target1 = target;
                    }
                }
                /*MagicFakeBeam.spawnFakeBeam(
					engine,
					ship.getLocation(),
					10000,
					VectorUtils.getAngle(ship.getLocation(),target1.getLocation()),
					40f,
					amount,
					1f,
					80f,
					new Color(255,255,255),
					new Color(255,0,255),
					200f,
					DamageType.ENERGY,
					0f,
					ship
				);
                 */
                DamagingProjectileAPI proj = (DamagingProjectileAPI) engine.spawnProjectile(ship, weapon, "armaa_curvyLaser", MathUtils.getRandomPointInCircle(weapon.getLocation(), 10f), ship.getFacing(), new Vector2f());
                gauge -= 9999f;
                maxDamage -= 99999f;
                maxProjs--;
                if (engine.isEntityInPlay(ship)) {
                    //engine.addPlugin(new armaa_counterShieldScript(proj, target1));
                    //alreadyRegisteredProjectiles.add(projectile);
                }
                even = even == true ? false : true;
            }
        }

    }
}
