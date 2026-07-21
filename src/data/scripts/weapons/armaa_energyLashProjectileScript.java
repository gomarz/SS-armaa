package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import org.jetbrains.annotations.NotNull;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;
import java.awt.Color;
import java.util.List;

import org.magiclib.util.MagicRender;
import com.fs.starfarer.api.util.IntervalUtil;

/**
 * Energy Lash tether + pull script (self-contained energyLash family).
 *
 * Two clearly-typed far-end references, only one active at a time:
 *   primary  (DamagingProjectileAPI) - the fired projectile; valid in FLIGHT, null after handoff.
 *   embedded (MissileAPI)            - the embedded spike; null until handoff, valid while ATTACHED.
 *
 * Lifecycle: FLIGHT (follow primary) -> ATTACHED (handoff to embedded; pull + swing + EMP) ->
 * RETRACT (reel home). The rope NEVER kills the spike; the AI owns all severing.
 *
 * Pull model: base mass-ratio radial pull, scaled by relative-velocity momentum (fast passes
 * budge heavier things), plus a tangential SWING force from aim-sweep rate (sweep to orbit the
 * target around you), plus lever-arm TORQUE that spins the target from the off-center attachment.
 */
public class armaa_energyLashProjectileScript extends BaseEveryFrameCombatPlugin {

    private DamagingProjectileAPI primary;   // flight-phase far end (null after handoff)
    private MissileAPI embedded;             // attached-phase far end (null until handoff)
    private CombatEntityAPI target;          // pull target (set on handoff)
    private final CombatEntityAPI source;

    private boolean attached = false;
    private boolean retracting = false;
    private float retractElapsed = 0f;
    private final Vector2f lastFarEnd = new Vector2f();

    private final IntervalUtil fireInterval = new IntervalUtil(0.05f, 0.1f);
    private static final float AT_MAX_RANGE = 0.5f;
    private static final float PUSH_CONSTANT = 2000f;
    private static final float MISSILE_SCALAR = 0.2f;

    // --- swing (orbital position-tracking) ---
    // Sweeping the aim rotates the target's "desired orbital position" around you; the target's
    // velocity is steered toward that point. Stable (no runaway): the target orbits to follow your
    // --- swing (tangential FORCE; weighty, momentum-carrying) ---
    // A tangential force is applied at the muzzle's sweep rate and DIVIDED BY TARGET MASS, so it's
    // an acceleration: light things accelerate, swing, coast on their own momentum and overshoot;
    // heavy things barely accelerate (force/big-mass ~ 0) and won't budge -- the "car on a string".
    // The target keeps the velocity it's given (real momentum) rather than being placed on a path,
    // which is what gives the dragged, weighty feel. Bounded: the sweep rate is clamped, so the
    // per-tick acceleration can't runaway the way an unbounded position-error would.
    private static final float SWING_MAX_RATE = 180f;    // clamp sweep rate (deg/sec)
    private static final float SWING_FORCE    = 2000f;   // swing strength (÷ sqrt(mass) = accel)
    private static final float SWING_MASS_FLOOR = 50f;   // floor mass for swing so featherweights don't launch
    private float lastWeaponAngle = Float.NaN;

    // --- momentum / relative-velocity yank ---
    private static final float MOMENTUM_SCALAR = 0.012f; // contribution per unit of separation speed
    private static final float MOMENTUM_MAX    = 2.0f;   // cap the multiplier

    // --- target spin (lever-arm torque ONLY) ---
    // The only rotation imparted is from the off-center embedded spike being pulled (real torque).
    // We do NOT rotate the target's facing to "follow the swing" -- a ball on a string translates
    // around you, it doesn't spin on its own axis. Swing is pure translation; this is the only spin.
    private static final float TORQUE_SCALAR = 1.5f;    // angular velocity per unit torque (amplified for feel)

    // EMP-along-rope
    private static final float EMP_PROC_CHANCE = 0.8f;
    private static final Color EMP_FRINGE = new Color(150, 210, 255, 200);
    private static final Color EMP_CORE   = new Color(220, 240, 255, 255);

    // rope
    private static final int   ROPE_NODES        = 24;
    private static final float ROPE_SLACK        = 1.08f;
    private static final float ROPE_DAMPING      = 0.86f;
    private static final float ROPE_GRAVITY      = 0f;
    private static final int   ROPE_RELAX_PASSES = 4;
    private static final float ROPE_THICKNESS    = 8f;
    private static final Color ROPE_COLOR        = new Color(255, 200, 200, 255);
    private static final String ROPE_SPRITE_PATH = "graphics/fx/beamcorec.png";

    private static final float RETRACT_DURATION = 0.5f;

    private Vector2f[] ropeNodes = null;
    private Vector2f[] ropePrev  = null;

    public armaa_energyLashProjectileScript(@NotNull DamagingProjectileAPI primary, CombatEntityAPI source, CombatEntityAPI target) {
        this.primary = primary;
        this.source = source;
        this.target = target;
    }

    /** OnHit effect: swap the far end from the (spent) primary to the embedded secondary missile. */
    public void handoff(MissileAPI secondary, CombatEntityAPI hitTarget) {
        this.embedded = secondary;
        this.primary = null;
        this.target = hitTarget;
        this.attached = true;
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused()) {
            return;
        }
        if (!(source instanceof ShipAPI) || !engine.isEntityInPlay(source)) {
            engine.removePlugin(this);
            return;
        }

        Vector2f origin = computeMuzzle();
        boolean alive = spikeAlive(engine);

        if (!retracting && !alive) {
            retracting = true;
            retractElapsed = 0f;
        }

        if (!retracting) {
            Vector2f far = currentFarEnd();
            lastFarEnd.set(far);

            simRope(origin, far, false);
            if (MagicRender.screenCheck(0.2f, far)) {
                drawRope();
            }

            if (attached) {
                applyPullAndEmp(engine, origin);
            }
        } else {
            retractElapsed += amount;
            float t = Math.min(1f, retractElapsed / RETRACT_DURATION);
            Vector2f far = new Vector2f(
                    lastFarEnd.x + (origin.x - lastFarEnd.x) * ease(t),
                    lastFarEnd.y + (origin.y - lastFarEnd.y) * ease(t));

            simRope(origin, far, true);
            if (MagicRender.screenCheck(0.2f, origin)) {
                drawRope();
            }

            if (t >= 1f) {
                engine.removePlugin(this);
            }
        }
    }

    // ---- far-end resolution (typed per phase) ----

    private Vector2f currentFarEnd() {
        if (embedded != null) {
            return new Vector2f(embedded.getLocation());
        }
        if (primary != null) {
            return new Vector2f(primary.getLocation());
        }
        return new Vector2f(lastFarEnd);
    }

    private boolean spikeAlive(CombatEngineAPI engine) {
        if (embedded != null) {
            return !embedded.isFizzling() && !embedded.didDamage() && engine.isEntityInPlay(embedded);
        }
        if (primary != null) {
            return !primary.isFading() && !primary.didDamage() && engine.isEntityInPlay(primary);
        }
        return false;
    }

    private WeaponAPI currentWeapon() {
        if (embedded != null) return embedded.getWeapon();
        if (primary != null)  return primary.getWeapon();
        return null;
    }

    private Vector2f computeMuzzle() {
        WeaponAPI weapon = currentWeapon();
        if (weapon == null) {
            return new Vector2f(((ShipAPI) source).getLocation());
        }
        float ox = weapon.getSpec().getTurretFireOffsets().get(0).x;
        float oy = weapon.getSpec().getTurretFireOffsets().get(0).y;
        Vector2f origin = new Vector2f(weapon.getLocation());
        Vector2f off = new Vector2f(ox, oy);
        VectorUtils.rotate(off, weapon.getCurrAngle(), off);
        Vector2f.add(off, origin, origin);
        return origin;
    }

    // ===================== pull + swing + EMP (attached phase only; embedded is the missile) =====================

    private void applyPullAndEmp(CombatEngineAPI engine, Vector2f origin) {
        if (embedded == null) {
            return;
        }
        if (!(target instanceof ShipAPI) && !(target instanceof MissileAPI)) {
            return;
        }
        float myMass = ((ShipAPI) source).getMassWithModules();
        float targetMass;

        fireInterval.advance(0.05f);
        if (!fireInterval.intervalElapsed()) {
            return;
        }

        if (target instanceof ShipAPI) {
            // The thing we actually speared (used as the EMP target). If it's a module, the PULL
            // retargets to the parent (haul the whole assembly), but the EMP stays on the module
            // we grabbed -- we zap what we hit, not the core ship.
            CombatEntityAPI empTarget = target;

            if (((ShipAPI) target).getParentStation() != null && ((ShipAPI) target).isAlive()) {
                target = ((ShipAPI) target).getParentStation();
            }
            targetMass = ((ShipAPI) target).getMassWithModules();

            if (Math.random() > EMP_PROC_CHANCE) {
                spawnEmpAlongRope(engine);
                float dam = embedded.getWeapon().getDamage().getDamage() * 0.2f;
                float emp = embedded.getWeapon().getDamage().getFluxComponent();
                engine.spawnEmpArc((ShipAPI) source, embedded.getLocation(), empTarget, empTarget,
                        DamageType.ENERGY, dam, emp, 10000f,
                        "system_emp_emitter_impact", 2f, EMP_FRINGE, EMP_CORE);
            }
        } else {
            targetMass = target.getMass() * MISSILE_SCALAR;
        }
        if (targetMass == 0f) targetMass = 1f;

        Vector2f targetLoc = embedded.getLocation();
        Vector2f dir = Vector2f.sub(origin, targetLoc, new Vector2f()); // target -> muzzle
        float distance = dir.length();
        if (dir.lengthSquared() > 0f) dir.normalise();

        float relativeMass = myMass / (myMass + targetMass);
        float distanceModifier = 1f - (distance / 600f * AT_MAX_RANGE);

        // --- momentum: scale radial pull by separation speed along the rope ---
        Vector2f relVel = Vector2f.sub(target.getVelocity(), source.getVelocity(), new Vector2f());
        float sepSpeed = relVel.x * (-dir.x) + relVel.y * (-dir.y); // + = separating
        float momentumMult = 1f;
        if (sepSpeed > 0f) {
            momentumMult = Math.min(MOMENTUM_MAX, 1f + sepSpeed * MOMENTUM_SCALAR);
        }

        float pushMe  = PUSH_CONSTANT / myMass * (1f - relativeMass) * momentumMult;
        float pushYou = PUSH_CONSTANT / targetMass * relativeMass * momentumMult;

        // --- swing: BOTH aim-sweep AND hull rotation sweep the attachment point around us ---
        // Aim-sweep is a deliberate player input (the AI can't do it); hull rotation is something
        // both player and AI do constantly while maneuvering. Folding in hull angular velocity makes
        // the swing work for AI ships too, and unifies the physics. The rate becomes a tangential
        // FORCE (below), divided by mass -> light things swing, heavy things won't budge.
        float aimRate = 0f;
        if (embedded.getWeapon() != null && !Float.isNaN(lastWeaponAngle)) {
            aimRate = MathUtils.getShortestRotation(lastWeaponAngle, embedded.getWeapon().getCurrAngle()) / 0.05f;
        }
        float hullRate = ((ShipAPI) source).getAngularVelocity(); // out of the weapon guard now
        float swingRate = Math.max(-SWING_MAX_RATE, Math.min(SWING_MAX_RATE, aimRate + hullRate));

        if (distance > 100) {
            // radial pull (momentum-scaled)
            Vector2f.add((Vector2f) new Vector2f(dir).scale(pushMe * distanceModifier * -1f), source.getVelocity(), source.getVelocity());
            Vector2f.add((Vector2f) new Vector2f(dir).scale(pushYou * distanceModifier), target.getVelocity(), target.getVelocity());

            // --- swing: tangential force ÷ sqrt(mass) = acceleration (weighty, but visible across
            // the full mass range -- sqrt compresses the 40x mass span to ~6x so light things swing
            // briskly, heavy things sluggishly, and nothing is invisible or launched). ---
            if (Math.abs(swingRate) > 0.001f) {
                Vector2f perp = new Vector2f(dir.y, -dir.x); // perpendicular to rope; flipped so target swings WITH your rotation
                float swingForce = SWING_FORCE * (swingRate / SWING_MAX_RATE); // signed magnitude
                float swingMass = Math.max(SWING_MASS_FLOOR, targetMass);
                float accelYou = swingForce / (float) Math.sqrt(swingMass) * distanceModifier * 0.05f;
                Vector2f.add((Vector2f) new Vector2f(perp).scale(accelYou), target.getVelocity(), target.getVelocity());
                // reaction on self, ÷ sqrt(our mass) and reduced (keep our aim clean)
                float accelMe = swingForce / (float) Math.sqrt(myMass) * distanceModifier * 0.05f * 0.25f;
                Vector2f.add((Vector2f) new Vector2f(perp).scale(-accelMe), source.getVelocity(), source.getVelocity());
            }

            // --- target spin: lever-arm torque ONLY (off-center pull = real torque) ---
            // No "rotate facing to follow the swing" -- a swung mass translates, it doesn't spin on
            // its own axis. The only rotation is from the embedded spike being yanked off-center.
            if (target instanceof ShipAPI) {
                Vector2f lever = Vector2f.sub(targetLoc, target.getLocation(), new Vector2f());
                Vector2f force = new Vector2f(dir.x * pushYou * distanceModifier, dir.y * pushYou * distanceModifier);
                float torque = lever.x * force.y - lever.y * force.x;
                ShipAPI ts = (ShipAPI) target;
                ts.setAngularVelocity(ts.getAngularVelocity() + torque * TORQUE_SCALAR / targetMass);
            }
        } else {
            //embedded.explode(); // close enough -> pop (delivers on-explode payload); retract follows
        }

        if (embedded.getWeapon() != null) {
            lastWeaponAngle = embedded.getWeapon().getCurrAngle();
        }
    }

    private void spawnEmpAlongRope(CombatEngineAPI engine) {
        if (ropeNodes == null) return;
        int arcs = (int) (3 * Math.random());
        for (int k = 0; k < arcs; k++) {
            int i = MathUtils.getRandomNumberInRange(0, ROPE_NODES - 2);
            engine.spawnEmpArcVisual(
                    new Vector2f(ropeNodes[i]), null,
                    new Vector2f(ropeNodes[i + 1]), null,
                    ROPE_THICKNESS, EMP_FRINGE, EMP_CORE);
        }
    }

    // ===================== verlet rope =====================

    private void simRope(Vector2f muzzle, Vector2f farEnd, boolean retract) {
        if (ropeNodes == null) {
            ropeNodes = new Vector2f[ROPE_NODES];
            ropePrev  = new Vector2f[ROPE_NODES];
            for (int i = 0; i < ROPE_NODES; i++) {
                float t = (float) i / (ROPE_NODES - 1);
                Vector2f p = new Vector2f(
                        muzzle.x + (farEnd.x - muzzle.x) * t,
                        muzzle.y + (farEnd.y - muzzle.y) * t);
                ropeNodes[i] = p;
                ropePrev[i]  = new Vector2f(p);
            }
        }

        float straight = MathUtils.getDistance(muzzle, farEnd);
        float lengthMult = ROPE_SLACK;
        if (retract) {
            float t = Math.min(1f, retractElapsed / RETRACT_DURATION);
            lengthMult = ROPE_SLACK * (1f - t);
        }
        float segLen = (straight * lengthMult) / (ROPE_NODES - 1);

        for (int i = 1; i < ROPE_NODES - 1; i++) {
            Vector2f cur = ropeNodes[i];
            Vector2f prev = ropePrev[i];
            float vx = (cur.x - prev.x) * ROPE_DAMPING;
            float vy = (cur.y - prev.y) * ROPE_DAMPING;
            prev.set(cur);
            cur.x += vx;
            cur.y += vy + ROPE_GRAVITY;
        }

        ropeNodes[0].set(muzzle);
        ropePrev[0].set(muzzle);
        ropeNodes[ROPE_NODES - 1].set(farEnd);
        ropePrev[ROPE_NODES - 1].set(farEnd);

        for (int pass = 0; pass < ROPE_RELAX_PASSES; pass++) {
            for (int i = 0; i < ROPE_NODES - 1; i++) {
                Vector2f a = ropeNodes[i];
                Vector2f b = ropeNodes[i + 1];
                float dx = b.x - a.x;
                float dy = b.y - a.y;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d < 0.0001f) d = 0.0001f;
                float diff = (d - segLen) / d;
                float offX = dx * 0.5f * diff;
                float offY = dy * 0.5f * diff;
                boolean aPinned = (i == 0);
                boolean bPinned = (i + 1 == ROPE_NODES - 1);
                if (!aPinned) { a.x += offX; a.y += offY; }
                if (!bPinned) { b.x -= offX; b.y -= offY; }
                if (aPinned && !bPinned) { b.x -= offX; b.y -= offY; }
                if (bPinned && !aPinned) { a.x += offX; a.y += offY; }
            }
            ropeNodes[0].set(muzzle);
            ropeNodes[ROPE_NODES - 1].set(farEnd);
        }
    }

    private void drawRope() {
        if (ropeNodes == null) return;
        for (int i = 0; i < ROPE_NODES - 1; i++) {
            SpriteAPI sprite = Global.getSettings().getSprite(ROPE_SPRITE_PATH);
            if (sprite == null) return;
            Vector2f a = ropeNodes[i];
            Vector2f b = ropeNodes[i + 1];
            Vector2f mid = new Vector2f((a.x + b.x) * 0.5f, (a.y + b.y) * 0.5f);
            float segDist = MathUtils.getDistance(a, b);
            float angle = VectorUtils.getAngle(a, b);
            MagicRender.singleframe(
                    sprite, mid,
                    new Vector2f(segDist * 1.02f, ROPE_THICKNESS),
                    angle, ROPE_COLOR, false);
        }
    }

    private float ease(float t) {
        return 1f - (1f - t) * (1f - t);
    }
}