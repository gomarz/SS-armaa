package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.OnFireEffectPlugin;
import com.fs.starfarer.api.combat.ShieldAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.magiclib.util.MagicLensFlare;
import data.scripts.util.armaa_valkazardBladeParticleEffect;
import java.awt.Color;
import java.util.ArrayList;
import org.lazywizard.lazylib.CollisionUtils;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicAnim;
import org.magiclib.util.MagicFakeBeam;

public class armaa_valkazardBladeLv2 implements EveryFrameWeaponEffectPlugin, OnFireEffectPlugin {

    private boolean runOnce = false;
    private ShipAPI ship;
    private WeaponAPI pauldronR, torso;
    private int limbInit = 0;
    private float swingLevel = 0f;
    private boolean swinging = false;
    private boolean cooldownR = false;
    private boolean beamFired = false;
    private IntervalUtil animInterval = new IntervalUtil(0.012f, 0.012f);
    private ArrayList<CombatEntityAPI> targets = new ArrayList();
    private float overlap = 0;
    private final float TORSO_OFFSET = -45, RIGHT_ARM_OFFSET = -25, MAX_OVERLAP = 10;

    public void init() {        
        runOnce = true;
        for (WeaponAPI w : ship.getAllWeapons()) {
            switch (w.getSlot().getId()) {
                case "B_TORSO":
                    if (torso == null) {
                        torso = w;
                        limbInit++;
                    }
                    break;
                case "D_PAULDRONR":
                    if (pauldronR == null) {
                        pauldronR = w;
                        pauldronR.getSprite().getCenterY();
                        limbInit++;
                    }
                    break;
            }
        }
    }

    public boolean getWeaponSide(Vector2f center, float facing, Vector2f weaponPosition) {
        // Calculate forward direction vector
        float facingRadians = (float) Math.toRadians(facing);
        Vector2f forward = new Vector2f((float) Math.cos(facingRadians), (float) Math.sin(facingRadians));

        // Calculate relative vector from center to weapon
        Vector2f centerToWeapon = new Vector2f(weaponPosition.x - center.x, weaponPosition.y - center.y);

        // Compute cross product
        float crossProduct = forward.x * centerToWeapon.y - forward.y * centerToWeapon.x;

        // Determine side
        if (crossProduct > 0) {
            return true;
        }
        return false;
    }

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        ship = weapon.getShip();
        if (!runOnce) {
            init();
            weapon.ensureClonedSpec();    
        }

        if (engine.isPaused()) {
            return;
        }
        if (!ship.getAIFlags().hasFlag(ShipwideAIFlags.AIFlags.BACKING_OFF) && !ship.getAIFlags().hasFlag(ShipwideAIFlags.AIFlags.DO_NOT_PURSUE)) {
            if (ship.getShipTarget() != null && MathUtils.getDistance(ship, ship.getShipTarget()) < 500 && !weapon.isDisabled()) {
                if (ship.getAI() != null) {
                    ship.blockCommandForOneFrame(ShipCommand.DECELERATE);
                    engine.headInDirectionWithoutTurning(ship, weapon.getCurrAngle(), ship.getMaxSpeed());
                }
            }
        }        
        if (ship.getEngineController().isAccelerating()) {
            if (overlap > (MAX_OVERLAP - 0.1f)) {
                overlap = MAX_OVERLAP;
            } else {
                overlap = Math.min(MAX_OVERLAP, overlap + ((MAX_OVERLAP - overlap) * amount * 5));
            }
        } else if (ship.getEngineController().isDecelerating() || ship.getEngineController().isAcceleratingBackwards()) {
            if (overlap < -(MAX_OVERLAP - 0.1f)) {
                overlap = -MAX_OVERLAP;
            } else {
                overlap = Math.max(-MAX_OVERLAP, overlap + ((-MAX_OVERLAP + overlap) * amount * 5));
            }
        } else {
            if (Math.abs(overlap) < 0.1f) {
                overlap = 0;
            } else {
                overlap -= (overlap / 2) * amount * 3;
            }
        }

        float sineA = 0f, sineC = 0f, sinceD = 0f;
        float global = ship.getFacing();
        float aim = MathUtils.getShortestRotation(global, weapon.getCurrAngle());

        if (weapon != null) {
            if (weapon.getChargeLevel() < 1) {
                sineC = MagicAnim.smoothNormalizeRange(weapon.getChargeLevel(), 0.3f, 0.9f);
                sinceD = MagicAnim.smoothNormalizeRange(weapon.getChargeLevel(), 0.3f, 1f);
            } else {
                sineC = 1;
                sinceD = 1;
            }
            boolean leftie = getWeaponSide(ship.getLocation(), ship.getFacing(), weapon.getLocation());
            float mult = -1f;
            if (leftie) {
                mult = 1f;
            }
            if (weapon.getCooldownRemaining() <= 0f && !weapon.isFiring()) {
                cooldownR = false;
            }

            if (!swinging && !cooldownR && weapon.getChargeLevel() > 0f) {
                weapon.setCurrAngle(weapon.getCurrAngle() + (sineC * (mult * TORSO_OFFSET) * 0.30f) * weapon.getChargeLevel());
            }
            if (weapon.getChargeLevel() >= 1f) {
                swinging = true;
            }

            if (swinging && Math.abs(weapon.getCurrAngle() - (weapon.getShip().getFacing() - (mult * 45f))) > 0.1f) {
                animInterval.advance(amount);

                if (mult == 1) {
                    // Left swing: use min to limit the angle correctly
                    weapon.setCurrAngle((float) Math.min(weapon.getCurrAngle() + swingLevel, weapon.getCurrAngle() + weapon.getArc() / 2));
                } else {
                    // Right swing: use max to limit the angle correctly
                    weapon.setCurrAngle((float) Math.max(weapon.getCurrAngle() - swingLevel, weapon.getCurrAngle() - weapon.getArc() / 2));
                }
            }

            if (swinging == true && (weapon.getChargeLevel() <= 0f)) {
                swinging = false;
                swingLevel = 0f;
                cooldownR = true;
            }

            if (animInterval.intervalElapsed()) {
                swingLevel += 0.5;
            }

            if (swingLevel > 9) {
                swingLevel = 9;
            }
            if (swinging == false) {
                swingLevel = 0f;
            }
            float chargeLevel = weapon.getCooldownRemaining()/weapon.getCooldown();
            if(swingLevel >= 9 && chargeLevel > 0.5f)
            {
                beamFired = true;
            }
            
            if(weapon.isFiring() && !beamFired)
            {
                MagicFakeBeam.spawnFakeBeam(engine, weapon.getFirePoint(0), 50f, weapon.getCurrAngle(), 25f, amount, 0.15f*chargeLevel, 25f, new Color(1f,.9f,.9f,1f*chargeLevel), new Color(0.94f,0.44f,0.16f,.9f*chargeLevel), 0f, DamageType.ENERGY, 0f, ship);
                Vector2f point = getBeamEndpoint(weapon.getFirePoint(0),weapon.getCurrAngle(),50f);
                for(CombatEntityAPI target : CombatUtils.getEntitiesWithinRange(weapon.getFirePoint(0), 75f))
                {
                    if(targets.contains(target))
                        continue;
                    if(target.getOwner() == ship.getOwner())
                        continue;
                    // 1) try shield
                    Vector2f shieldHit = intersectShield(target, weapon.getFirePoint(0), point);
                    if (shieldHit != null) {
                        // do shield hit effects / spawn projectile at shield surface
                        engine.spawnProjectile(ship, weapon, "armaa_alestePilebunkerLeft_s", shieldHit, weapon.getCurrAngle(), new Vector2f());
                        targets.add(target);
                        float variance = MathUtils.getRandomNumberInRange(-0.3f, .3f);
                        Global.getSoundPlayer().playSound("armaa_saber_slash",1.1f+variance,1f+variance,point,new Vector2f());
                        continue; // don’t also hit hull behind the shield
                    }                    
                    Vector2f targetPoint = CollisionUtils.getCollisionPoint(weapon.getFirePoint(0), point, target);                    
                    if(targetPoint != null)
                    {   
                        engine.spawnProjectile(ship, weapon, "armaa_alestePilebunkerLeft_s", targetPoint, weapon.getCurrAngle(), new Vector2f());
                        float variance = MathUtils.getRandomNumberInRange(-0.3f, .3f);
                        Global.getSoundPlayer().playSound("armaa_saber_slash",1.1f+variance,1f+variance,point,new Vector2f());                        
                        targets.add(target);
                    }
                }
            }
            
            if (!weapon.isFiring())
            {
                beamFired = false;
                if(!targets.isEmpty())
                    targets.clear();
            }
        }
    }
    //Probably a func for this in lazy lib, but fug it
    public Vector2f getBeamEndpoint(Vector2f origin, float angleRadians, float length) {
        float dx = length * (float) Math.cos(angleRadians);
        float dy = length * (float) Math.sin(angleRadians);
        return new Vector2f(origin.x + dx, origin.y + dy);
    }
    
    @Override
    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {
        String weaponId = "armaa_valkazard_blade_lv2_bullet";
        projectile.setDamageAmount(0f);
        Vector2f vector = projectile.getVelocity();
        Vector2f loc = weapon.getFirePoint(0);

        MagicLensFlare.createSharpFlare(
                engine,
                weapon.getShip(),
                projectile.getLocation(),
                4f,
                150f,
                projectile.getFacing(),
                new Color (255,0,0,150),
                new Color(255,255,255,255)
        );
            engine.spawnExplosion(weapon.getLocation(), new Vector2f(), new Color (239,111,41,150), 75f, 0.25f);
            engine.spawnExplosion(weapon.getLocation(), new Vector2f(), Color.white, 25f, .1f);
            engine.addNebulaParticle(projectile.getLocation(),
                    new Vector2f(),
                    20f * (0.75f + (float) Math.random() * 0.5f),
                    5f + 3f * Misc.getHitGlowSize(100f, projectile.getDamage().getBaseDamage(),null ) / 100f,
                    0f,
                    0f,
                    1f,
                    new Color (239,111,41,150),
                    true);
        engine.addNebulaSmoothParticle(loc, vector, 25f * 4f, 1.5f, 0.5f, 1f, 1f, Color.red, true);
        float targetArc = weapon.distanceFromArc(ship.getMouseTarget()) == 0 ? VectorUtils.getAngle(weapon.getFirePoint(0),ship.getMouseTarget()) : ship.getFacing(); 
        DamagingProjectileAPI proj = (DamagingProjectileAPI) engine.spawnProjectile(ship, weapon, weaponId, weapon.getFirePoint(0),targetArc, weapon.getShip().getVelocity());
        engine.addPlugin(new armaa_valkazardBladeParticleEffect(proj));
        engine.removeEntity(projectile);
    }
    
/** Returns the first intersection point of segment [A,B] with target's shield, or null if no shield hit. */
public static Vector2f intersectShield(CombatEntityAPI target, Vector2f A, Vector2f B) {
    if (target == null) return null;
    if(!(target instanceof ShipAPI))
        return null;
    ShieldAPI sh = target.getShield();
    if (sh == null || sh.isOff() || sh.getActiveArc() <= 0f) return null;

    // Shield circle
    final Vector2f C = sh.getLocation();   // center
    final float R = sh.getRadius();        // radius

    // Segment parametric: P(t) = A + t*(B - A), t in [0,1]
    final float dx = B.x - A.x;
    final float dy = B.y - A.y;

    // Solve |A + t*d - C|^2 = R^2  =>  (d·d) t^2 + 2 d·(A-C) t + |A-C|^2 - R^2 = 0
    final float fx = A.x - C.x;
    final float fy = A.y - C.y;

    final float a = dx*dx + dy*dy;
    final float b = 2f * (dx*fx + dy*fy);
    final float c = fx*fx + fy*fy - R*R;

    final float disc = b*b - 4f*a*c;
    if (disc < 0f || a <= 1e-6f) return null; // no intersection or degenerate segment

    final float s = (float) Math.sqrt(disc);
    // two roots, pick the closest valid t in [0,1]
    float t1 = (-b - s) / (2f * a);
    float t2 = (-b + s) / (2f * a);

    Float bestT = null;
    if (t1 >= 0f && t1 <= 1f) bestT = t1;
    if (t2 >= 0f && t2 <= 1f) {
        if (bestT == null || t2 < bestT) bestT = t2;
    }
    if (bestT == null) return null;

    Vector2f hit = new Vector2f(A.x + dx * bestT, A.y + dy * bestT);

    // Verify the point lies within the shield's current arc
    if (!sh.isWithinArc(hit)) return null;

    return hit;
}
    

}
