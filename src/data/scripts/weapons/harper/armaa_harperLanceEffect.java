package data.scripts.weapons.harper;

import java.awt.Color;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicLensFlare;
import org.magiclib.util.MagicRender;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.OnFireEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.WeaponAPI;

import data.scripts.util.armaa_meleeSweep;

public class armaa_harperLanceEffect implements EveryFrameWeaponEffectPlugin, OnFireEffectPlugin {

    /**
     * Floating text on state changes and hits, plus range markers.
     */
    public static final boolean DEBUG = false;

    // ---- reach ----
    private static final float LANCE_LENGTH = 54f;
    private static final float TIP_LENGTH = 20f;
    private static final float COUCH_RETRACT = 0f;
    /**
     * How far back the spike is parked to hide it in the shroud. Must be at
     * least the spike's own pixel length or its cut edge shows in the cone.
     */
    private static final float TIP_HIDE_OFFSET = 25f;
    /**
     * How fast the spike slides in and out, fraction per second.
     */
    private static final float TIP_DEPLOY_RATE = 2.5f;
    private static final float COLLAR_DIST = 17f;

    // ---- lunge ----
    private static final float LUNGE_THRUST = 3.2f;
    /**
     * Steering allowed during the lunge, degrees per second. Matched to
     * MAX_COUCH_TURN on purpose: any correction the player can make during a
     * lunge is one that will not break the charge they are building.
     */
    private static final float LUNGE_TURN_RATE = 35f;
    /**
     * Steering allowed to an AI-flown lunge. Higher than the player's, because
     * the AI cannot make fine corrections and arriving on target at all is its
     * whole problem. Note this now exceeds MAX_COUCH_TURN, so a hard AI
     * correction can break its own couch mid-lunge - a deliberate trade for a
     * mech that otherwise could not connect.
     */
    private static final float LUNGE_TURN_RATE_AI = 55f;
    private static final float LUNGE_SPEED_MULT = 2f;
    private static final float LUNGE_MIN_CHARGE = 0.05f;
    /**
     * Ground a full lunge covers. Feeds effectiveReach().
     */
    public static final float LUNGE_RANGE = 420f;
    private static final float CONTACT_MARGIN = 0f;

    // ---- couch tuning ----
    private static final float FULL_COUCH_DISTANCE = 500f;
    private static final float MIN_COUCH_SPEED = 40f;
    private static final float MAX_COUCH_TURN = 30f;
    private static final float COUCH_DECAY_PER_SEC = 900f;
    private static final float IMPACT_SPEED_REF = 150f;
    private static final float MAX_DAMAGE_MULT = 3.5f;

    // ---- thrust tuning ----
    private static final float THRUST_DURATION = 0.22f;
    private static final float THRUST_EXTEND = 40f;
    private static final float ARREST_VELOCITY_SCALE = 0.55f;

    private static final String HIT_SOUND = "armaa_polearm";
    private static final String READY_SOUND = "armaa_polearm";
    private static final String STATUS_ICON = "graphics/icons/hullsys/damper_field.png";

    private boolean runOnce = false;
    private ShipAPI ship;
    private WeaponAPI lanceWeapon;
    private armaa_harperLanceAI lanceAI;

    private float couch = 0f;
    private float couchAtRelease = 0f;
    private boolean wasFull = false;

    private boolean lunging = false;
    private float lungeHeading = 0f;

    private boolean thrusting = false;
    private boolean stopThrust = false;
    private float thrustElapsed = 0f;
    private float extension = 0f;
    private float ogBarrelY = 0f;

    private float prevCooldown = 0f;
    private int prevAmmo = -999;

    /**
     * > 0 while the current strike is a burst rather than a normal stab.
     */
    private float burstDamage = 0f;
    /**
     * False while the spike is missing, after a burst and before reload.
     */
    private boolean tipPresent = true;
    /**
     * Animated form of tipPresent. 0 = hidden in the shroud, 1 = fully out.
     */
    private float tipDeploy = 1f;

    private final Set<CombatEntityAPI> hitThisThrust = new HashSet<CombatEntityAPI>();

    private final Vector2f prevTipA = new Vector2f();
    private final Vector2f prevTipB = new Vector2f();
    private boolean prevValid = false;
    private final armaa_harperLungePlume plume = new armaa_harperLungePlume();

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {

        if (engine.isPaused() || weapon == null) {
            return;
        }
        ship = weapon.getShip();
        if (ship == null) {
            return;
        }
        lanceWeapon = weapon;

        if (!runOnce) {
            engine.getCustomData().put(customDataKey(ship), this);
            if (weapon.getBarrelSpriteAPI() != null) {
                ogBarrelY = weapon.getBarrelSpriteAPI().getCenterY();
            }
            prevCooldown = weapon.getCooldownRemaining();
            prevAmmo = weapon.getAmmo();
            runOnce = true;

            engine.addLayeredRenderingPlugin(
                    new armaa_capePlugin(ship, new Vector2f(0f, -14f), 12, 3f, 15f));
            engine.addPlugin(new armaa_moduleArcMirrorScript(ship, "A_GUN"));
            if (DEBUG) {
                Global.getLogger(armaa_harperLanceEffect.class).info(
                        "lance init: bladeReach=" + LANCE_LENGTH
                        + " wpnRange=" + weapon.getRange()
                        + " effectiveReach=" + effectiveReach(weapon));
            }
        }

        updateTipDeploy(amount);
        detectFire(engine, weapon);
        updateCouch(amount, weapon);
        plume.renderLungePlume(amount, ship, forwardSpeed(), weapon, lunging ? weapon.getChargeLevel() : 0f);
        updateLunge(amount, engine, weapon);
        float lvl = weapon.getChargeLevel();
        if (lvl > 0.02f) {
            // playLoop must be re-called every frame; it stops when you stop asking.
            // Volume and pitch ride the same curve as the plume so the audio does not
            // outlive or undercut the flame.
            Global.getSoundPlayer().playLoop("system_burn_drive_loop", ship,
                    0.85f + 0.3f * lvl, // pitch
                    0.7f * lvl, // volume
                    ship.getLocation(), ship.getVelocity());
        }
        if (thrusting) {
            advanceThrust(amount, engine, weapon);
        } else {
            extension = Math.max(0f, extension - THRUST_EXTEND * 3f * amount);
            prevValid = false;
        }

        applyBarrelOffset(weapon);
        renderChargeFill(engine, weapon);
        maintainStatus(engine, weapon);
        //debugRender(engine, weapon);

        // The joust cycle only exists for AI-flown ships. Created lazily
        // because getAI() is null on the frame a ship is first built.
        if (ship.getAI() != null) {
            if (lanceAI == null) {
                lanceAI = new armaa_harperLanceAI(this);
            }
            lanceAI.advance(amount, engine, weapon, ship);
        }
    }
    // ------------------------------------------------------------------
    // public surface for the AI and the sibling mounts
    // ------------------------------------------------------------------

    public static String customDataKey(ShipAPI ship) {
        return "armaa_harperLance_" + ship.getId();
    }

    public WeaponAPI getWeapon() {
        return lanceWeapon;
    }

    /**
     * Couch bar fill, 0-1.
     */
    public float getCouchRatio() {
        return Math.min(1f, couch / FULL_COUCH_DISTANCE);
    }

    public boolean isThrusting() {
        return thrusting;
    }

    public boolean isLunging() {
        return lunging;
    }

    public boolean isBusy() {
        return thrusting || lunging;
    }

    /**
     * The spike is gone between a burst and its reload. The lance cannot stab
     * without one, and the barrel sprite is parked inside the shroud.
     */
    public void setTipPresent(boolean present) {
        tipPresent = present;
    }

    public boolean isTipPresent() {
        return tipPresent;
    }

    /**
     * How far away the lance can actually threaten, as opposed to how long the
     * blade is. Derived from the declared weapon range so widening range in the
     * .wpn widens the commit envelope with it - the lunge covers the
     * difference.
     */
    public float effectiveReach(WeaponAPI weapon) {
        return weapon.getRange() + LUNGE_RANGE;
    }

    public boolean canFire(WeaponAPI weapon) {
        return weapon.getCooldownRemaining() <= 0f
                && !weapon.isDisabled()
                && (!weapon.usesAmmo() || weapon.getAmmo() > 0);
    }

    /**
     * Is there anything the lance itself could hit right now - inside its own
     * reach AND inside its arc? Used by the repeater to decide which of the two
     * mounts steers the assembly.
     */
    public boolean hasTargetInReach(WeaponAPI weapon) {
        if (ship == null || weapon == null) {
            return false;
        }
        float reach = effectiveReach(weapon);
        float centre = MathUtils.clampAngle(ship.getFacing() + weapon.getArcFacing());
        float half = weapon.getArc() * 0.5f;

        for (ShipAPI s : AIUtils.getNearbyEnemies(ship, reach)) {
            if (!s.isAlive() || s.isHulk() || s.isFighter()) {
                continue;
            }
            float a = VectorUtils.getAngle(weapon.getFirePoint(0), s.getLocation());
            if (Math.abs(MathUtils.getShortestRotation(centre, a)) < half) {
                return true;
            }
        }
        return false;
    }

    /**
     * Called by the burst plugin. Routes through the normal strike path so the
     * burst inherits the sweep, the arrest and the cooldown handling; only the
     * damage figure and type differ.
     */
    public boolean requestBurstStrike(CombatEngineAPI engine, float damage) {
        if (lanceWeapon == null || thrusting) {
            return false;
        }
        burstDamage = damage;
        startThrust(engine, lanceWeapon, "BURST");
        return true;
    }

    // ------------------------------------------------------------------
    // fire detection
    // ------------------------------------------------------------------
    private void detectFire(CombatEngineAPI engine, WeaponAPI weapon) {

        float cd = weapon.getCooldownRemaining();
        int ammo = weapon.getAmmo();

        boolean cooldownJumped = cd > prevCooldown + 0.0001f;
        boolean ammoDropped = prevAmmo != -999 && ammo < prevAmmo;

        prevCooldown = cd;
        prevAmmo = ammo;

        if ((cooldownJumped || ammoDropped) && !thrusting) {
            startThrust(engine, weapon, "FULL");
        }
    }

    // ------------------------------------------------------------------
    // lunge - driven by the weapon's own charge level
    // ------------------------------------------------------------------
    private void updateLunge(float amount, CombatEngineAPI engine, WeaponAPI weapon) {

        float charge = weapon.getChargeLevel();
        boolean charging = charge > LUNGE_MIN_CHARGE
                && !thrusting
                && weapon.getCooldownRemaining() <= 0f;

        if (charging && !lunging) {
            lunging = true;
            lungeHeading = weapon.getCurrAngle();
            if (DEBUG) {
                engine.addFloatingText(ship.getLocation(), "LUNGE",
                        20f, Color.cyan, ship, 1f, 1f);
            }
        }

        if (!charging) {
            lunging = false;
            return;
        }

        float delta = MathUtils.getShortestRotation(lungeHeading,
                desiredLungeHeading(engine, weapon));
        float turnRate = (ship.getAI() == null) ? LUNGE_TURN_RATE : LUNGE_TURN_RATE_AI;
        float maxStep = turnRate * amount;
        if (Math.abs(delta) > maxStep) {
            delta = delta > 0 ? maxStep : -maxStep;
        }
        lungeHeading = MathUtils.clampAngle(lungeHeading + delta);

        if (ship.getAI() == null) {
            ship.blockCommandForOneFrame(ShipCommand.DECELERATE);
        }
        float toHeading = MathUtils.getShortestRotation(ship.getFacing(), lungeHeading);
        ship.setAngularVelocity(Math.max(-turnRate,
                Math.min(turnRate, toHeading * 4f)));

        float cap = ship.getMaxSpeed() * (1f + (LUNGE_SPEED_MULT - 1f) * charge);
        if (ship.getVelocity().length() < cap) {
            CombatUtils.applyForce(ship, lungeHeading,
                    ship.getMass() * LUNGE_THRUST * charge * amount * 60f);
        }

        if (contactInCorridor(weapon) && canFire(weapon)) {
            weapon.setForceFireOneFrame(true);
            startThrust(engine, weapon, "CONTACT");
        }
    }

    /**
     * Where the lunge should be pointing.
     *
     * NOT weapon.getCurrAngle(). That was the original source and it made the
     * lunge a rail: lungeHeading chased the weapon, the ship's facing was then
     * driven toward lungeHeading, and on a hardpoint or a narrow arc the weapon
     * angle is just the ship's facing plus its arc facing - so all three chase
     * each other and delta settles at zero. The rate limit was limiting
     * nothing, because the input never moved.
     *
     * Taking the heading from a real aim point instead gives the player mouse
     * steering through a lunge, and gives the AI genuine homing onto whatever
     * it committed to.
     */
    private float desiredLungeHeading(CombatEngineAPI engine, WeaponAPI weapon) {
        Vector2f aim = null;

        if (ship.getAI() == null) {
            aim = ship.getMouseTarget();
        } else if (lanceAI != null && lanceAI.getCommittedTarget() != null) {
            aim = lanceAI.getCommittedTarget().getLocation();
        } else if (ship.getShipTarget() != null) {
            aim = ship.getShipTarget().getLocation();
        }

        if (aim == null) {
            return weapon.getCurrAngle();
        }
        return VectorUtils.getAngle(ship.getLocation(), aim);
    }

    /**
     * Is there a hostile in the corridor the strike would sweep? Scales with
     * tipDeploy - a lance with no spike on it has nothing to make contact with.
     */
    private boolean contactInCorridor(WeaponAPI weapon) {

        if (tipDeploy <= 0.05f) {
            return false;
        }

        Vector2f from = weapon.getFirePoint(0);
        float reach = (LANCE_LENGTH + THRUST_EXTEND + CONTACT_MARGIN) * tipDeploy;
        Vector2f to = MathUtils.getPointOnCircumference(from, reach, weapon.getCurrAngle());

        Vector2f mid = new Vector2f((from.x + to.x) * 0.5f, (from.y + to.y) * 0.5f);
        List<CombatEntityAPI> near = CombatUtils.getEntitiesWithinRange(mid, reach);

        for (CombatEntityAPI e : near) {
            if (e == ship || e.getOwner() == ship.getOwner()) {
                continue;
            }
            if (e.getCollisionClass() == CollisionClass.NONE) {
                continue;
            }
            if (e instanceof DamagingProjectileAPI && !(e instanceof MissileAPI)) {
                continue;
            }
            if (e instanceof ShipAPI) {
                ShipAPI s = (ShipAPI) e;
                if (s.isPhased() || s.isHulk()) {
                    continue;
                }
            }
            float r = Math.max(6f, e.getCollisionRadius() * 0.7f);
            if (armaa_meleeSweep.distanceFromSegment(from, to, e.getLocation()) <= r) {
                return true;
            }
        }
        return false;
    }

    private void startThrust(CombatEngineAPI engine, WeaponAPI weapon, String why) {

        weapon.setRemainingCooldownTo(1f);
        // Sync the watcher to the cooldown we just caused. Without this,
        // detectFire sees our own write as a fresh shot next frame and starts a
        // second thrust - which is what showed up as STRIKE appearing twice on
        // every burst.
        prevCooldown = weapon.getCooldownRemaining();

        float speedFactor = Math.min(1f,
                Math.max(0f, forwardSpeed()) / IMPACT_SPEED_REF);
        couchAtRelease = couch * speedFactor;
        couch = 0f;
        wasFull = false;

        lunging = false;
        thrusting = true;
        stopThrust = false;
        thrustElapsed = 0f;
        extension = 0f;
        hitThisThrust.clear();

        Vector2f tipB = tipPoint(weapon);
        Vector2f tipA = pointBackFromTip(weapon, tipB, TIP_LENGTH);
        prevTipA.set(tipA);
        prevTipB.set(tipB);
        prevValid = true;

        if (DEBUG) {
            engine.addFloatingText(ship.getLocation(),
                    "STRIKE(" + why + ") spd=" + (int) forwardSpeed()
                    + " gate=" + String.format("%.2f", speedFactor)
                    + " x" + String.format("%.2f", damageMult(releaseRatio())),
                    22f, Color.orange, ship, 1f, 1f);
        }
    }

    // ------------------------------------------------------------------
    // couch
    // ------------------------------------------------------------------
    private float forwardSpeed() {
        float rad = (float) Math.toRadians(ship.getFacing());
        Vector2f v = ship.getVelocity();
        return v.x * (float) Math.cos(rad) + v.y * (float) Math.sin(rad);
    }

    private void updateCouch(float amount, WeaponAPI weapon) {

        if (ship.getFluxTracker().isOverloadedOrVenting() || weapon.isDisabled()) {
            couch = 0f;
            wasFull = false;
            return;
        }

        float fwd = forwardSpeed();
        boolean couched = fwd > MIN_COUCH_SPEED
                && Math.abs(ship.getAngularVelocity()) < MAX_COUCH_TURN;

        if (couched) {
            // distance travelled this frame, not speed
            couch = Math.min(FULL_COUCH_DISTANCE, couch + fwd * amount);
        } else {
            couch = Math.max(0f, couch - COUCH_DECAY_PER_SEC * amount);
        }

        boolean full = getCouchRatio() >= 0.999f;
        if (full && !wasFull && !thrusting && !lunging) {
            Vector2f tip = tipPoint(weapon);
            Global.getSoundPlayer().playSound(READY_SOUND, 1.6f, 0.7f, tip, new Vector2f());
            if (MagicRender.screenCheck(0.1f, tip)) {
                MagicLensFlare.createSharpFlare(Global.getCombatEngine(), ship, tip,
                        4, 260f, 0f, new Color(255, 200, 130, 220), Color.white);
            }
        }
        wasFull = full;
    }

    /**
     * What was actually cashed on the current thrust, 0-1.
     */
    private float releaseRatio() {
        return Math.min(1f, couchAtRelease / FULL_COUCH_DISTANCE);
    }

    private float damageMult(float ratio) {
        return 1f + ratio * (MAX_DAMAGE_MULT - 1f);
    }

    // ------------------------------------------------------------------
    // offsets
    // ------------------------------------------------------------------
    /**
     * Slide the spike toward whichever state tipPresent is asking for.
     */
    private void updateTipDeploy(float amount) {
        float goal = tipPresent ? 1f : 0f;

        if (!tipPresent) {
            tipDeploy = 0f;
            return;
        }
        if (tipDeploy == goal) {
            return;
        }
        float step = TIP_DEPLOY_RATE * amount;
        if (Math.abs(goal - tipDeploy) <= step) {
            tipDeploy = goal;
        } else {
            tipDeploy += (goal > tipDeploy ? step : -step);
        }
    }

    /**
     * WORLD distance from the fire point to the damaging tip. Scaled by
     * tipDeploy so reach and sprite stay in step while the spike slides.
     */
    private float tipDistance() {
        float full = LANCE_LENGTH - COUCH_RETRACT * (1f - getCouchRatio()) + extension;
        return full * tipDeploy;
    }

    /**
     * SPRITE-space offset for the barrel. Barrel Y runs opposite to world
     * forward, so this is the negation of the reach change, not the same
     * number. Blends between the hidden pose and the live one.
     */
    private float barrelOffset() {
        float live = COUCH_RETRACT * (1f - getCouchRatio()) - extension;
        return TIP_HIDE_OFFSET * (1f - tipDeploy) + live * tipDeploy;
    }

    // ------------------------------------------------------------------
    // thrust
    // ------------------------------------------------------------------
    private void advanceThrust(float amount, CombatEngineAPI engine, WeaponAPI weapon) {

        thrustElapsed += amount;
        float t = Math.min(1f, thrustElapsed / THRUST_DURATION);

        float eased = t < 0.5f ? (t / 0.5f) * (t / 0.5f) : 1f;
        extension = THRUST_EXTEND * eased;

        Vector2f tipB = tipPoint(weapon);
        Vector2f tipA = pointBackFromTip(weapon, tipB, TIP_LENGTH);

        final float mult = damageMult(releaseRatio());

        if (prevValid) {
            final WeaponAPI wep = weapon;
            final CombatEngineAPI eng = engine;

            armaa_meleeSweep.sweep(ship, prevTipA, prevTipB, tipA, tipB, hitThisThrust,
                    new armaa_meleeSweep.HitHandler() {
                @Override
                public void onHit(CombatEntityAPI target, Vector2f point,
                        boolean shieldHit, float sweepAngle) {
                    applyLanceHit(eng, wep, target, point, shieldHit, mult);
                }
            });
        }

        prevTipA.set(tipA);
        prevTipB.set(tipB);
        prevValid = true;

        if (stopThrust) {
            arrestThrust(weapon, mult);
            return;
        }

        if (t >= 1f) {
            endThrust();
        }
    }

    private void arrestThrust(WeaponAPI weapon, float mult) {
        endThrust();
        // Bleeding velocity here is also what stops a second strike landing at
        // full strength - the impact-speed gate reads what is left.
        ship.getVelocity().scale(ARREST_VELOCITY_SCALE);
        CombatUtils.applyForce(ship, weapon.getCurrAngle() + 180f,
                ship.getMass() * mult * 0.5f);
    }

    private void endThrust() {
        burstDamage = 0f;
        thrusting = false;
        stopThrust = false;
        thrustElapsed = 0f;
        prevValid = false;
        hitThisThrust.clear();
        if (lanceAI != null) {
            lanceAI.onStrikeComplete();
        }
    }

    private void applyLanceHit(CombatEngineAPI engine, WeaponAPI weapon,
            CombatEntityAPI target, Vector2f point, boolean shieldHit, float mult) {

        boolean burst = burstDamage > 0f;
        float damage = (burst ? burstDamage : weapon.getDamage().getDamage()) * mult;
        DamageType type = burst ? DamageType.HIGH_EXPLOSIVE : weapon.getDamageType();

        engine.applyDamage(target, point, damage, type,
                0f, false, false, ship, true);

        float axis = weapon.getCurrAngle();
        CombatUtils.applyForce(target, axis, ship.getMass() * mult * 0.8f);
        CombatUtils.applyForce(ship, axis + 180f, ship.getMass() * 0.1f);

        stopThrust = true;

        float variance = MathUtils.getRandomNumberInRange(-0.15f, 0.15f);
        Global.getSoundPlayer().playSound(HIT_SOUND,
                1f + variance, 1f + variance, point, new Vector2f());

        if (DEBUG) {
            engine.addFloatingText(point,
                    (shieldHit ? "SHIELD " : "HULL ") + (int) damage
                    + " x" + String.format("%.2f", mult),
                    20f, shieldHit ? Color.cyan : Color.yellow, null, 1f, 1f);
        }

        if (MagicRender.screenCheck(0.1f, point)) {
            MagicLensFlare.createSharpFlare(engine, ship, point,
                    5, 200f + 200f * mult, 0f,
                    shieldHit ? new Color(120, 200, 255, 200) : new Color(255, 200, 120, 200),
                    Color.white);

            int count = (int) (6 + 10 * mult);
            for (int i = 0; i < count; i++) {
                float angle = MathUtils.getRandomNumberInRange(axis + 150f, axis + 210f);
                float speed = MathUtils.getRandomNumberInRange(120f, 380f) * mult;
                Vector2f v = MathUtils.getPointOnCircumference(null, speed, angle);
                engine.addHitParticle(point, v, 6f, 1f, 0.35f, new Color(255, 230, 190));
            }
        }
    }

    // ------------------------------------------------------------------
    // geometry
    // ------------------------------------------------------------------
    private Vector2f tipPoint(WeaponAPI weapon) {
        return MathUtils.getPointOnCircumference(weapon.getFirePoint(0),
                tipDistance(), weapon.getCurrAngle());
    }

    private Vector2f pointBackFromTip(WeaponAPI weapon, Vector2f tip, float back) {
        return MathUtils.getPointOnCircumference(tip, back, weapon.getCurrAngle() + 180f);
    }

    // ------------------------------------------------------------------
    // presentation
    // ------------------------------------------------------------------
    /**
     * The engine's built-in recoil only travels backwards, so the spike offset
     * is applied by hand.
     */
    private void applyBarrelOffset(WeaponAPI weapon) {
        if (weapon.getBarrelSpriteAPI() == null) {
            return;
        }
        weapon.getBarrelSpriteAPI().setCenterY(ogBarrelY + barrelOffset());
    }

    private void renderChargeFill(CombatEngineAPI engine, WeaponAPI weapon) {

        float ratio = getCouchRatio();
        if (ratio <= 0.02f) {
            return;
        }

        Vector2f origin = weapon.getFirePoint(0);
        if (!MagicRender.screenCheck(0.1f, origin)) {
            return;
        }

        float tipDist = tipDistance();
        if (tipDist <= COLLAR_DIST) {
            return;
        }

    }

    /**
     * Shows the couch bar and what it would pay right now. Those diverge when
     * the player is charged but has slowed down, which is exactly the case the
     * speed gate exists to catch. Wind-up shows separately.
     */
    private void maintainStatus(CombatEngineAPI engine, WeaponAPI weapon) {
        if (ship != engine.getPlayerShip()) {
            return;
        }
        float gate = Math.min(1f, Math.max(0f, forwardSpeed()) / IMPACT_SPEED_REF);
        float effective = damageMult(getCouchRatio() * gate);
        int pct = (int) (getCouchRatio() * 100f);

        engine.maintainStatusForPlayerShip(
                "armaa_harper_couch",
                STATUS_ICON,
                "Lance charge",
                pct + "%  (x" + String.format("%.1f", effective) + " now)",
                effective < damageMult(1f) - 0.01f);

        float wind = weapon.getChargeLevel();
        if (wind > LUNGE_MIN_CHARGE) {
            engine.maintainStatusForPlayerShip(
                    "armaa_harper_windup",
                    STATUS_ICON,
                    "Lunge",
                    (int) (wind * 100f) + "%",
                    false);
        }

        if (tipDeploy < 0.99f) {
            engine.maintainStatusForPlayerShip(
                    "armaa_harper_tip",
                    STATUS_ICON,
                    "Lance tip",
                    tipPresent ? "deploying" : "expended",
                    true);
        }
    }

    /**
     * cyan - the fire point everything is measured from green - start of the
     * damaging segment red - the damaging tip orange- contact-cancel corridor
     * end magenta - AI commit envelope
     */
    private void debugRender(CombatEngineAPI engine, WeaponAPI weapon) {
        if (!DEBUG) {
            return;
        }
        Vector2f origin = weapon.getFirePoint(0);
        engine.addSmoothParticle(origin, new Vector2f(), 8f, 1f, 0.05f, Color.cyan);

        Vector2f tipB = tipPoint(weapon);
        Vector2f tipA = pointBackFromTip(weapon, tipB, TIP_LENGTH);
        engine.addSmoothParticle(tipA, new Vector2f(), 8f, 1f, 0.05f, Color.green);
        engine.addSmoothParticle(tipB, new Vector2f(), 8f, 1f, 0.05f, Color.red);

        Vector2f corridor = MathUtils.getPointOnCircumference(origin,
                (LANCE_LENGTH + THRUST_EXTEND + CONTACT_MARGIN) * tipDeploy,
                weapon.getCurrAngle());
        engine.addSmoothParticle(corridor, new Vector2f(), 6f, 0.6f, 0.05f, Color.orange);

        Vector2f reach = MathUtils.getPointOnCircumference(
                origin, effectiveReach(weapon), weapon.getCurrAngle());
        engine.addSmoothParticle(reach, new Vector2f(), 6f, 0.6f, 0.05f, Color.magenta);
    }

    // ------------------------------------------------------------------
    // trigger projectile
    // ------------------------------------------------------------------
    @Override
    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {
        // Stateless on purpose - see the class comment.
        projectile.setDamageAmount(0f);
        projectile.setCollisionClass(CollisionClass.NONE);
        projectile.setHitpoints(0f);
    }
}
