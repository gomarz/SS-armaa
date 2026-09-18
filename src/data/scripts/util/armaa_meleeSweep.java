package data.scripts.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import org.lazywizard.lazylib.CollisionUtils;

/**
 * Swept-segment hit detection for melee weapons.
 *
 * The problem this solves: testing a blade segment once per frame at its
 * current pose works fine for a wide angular sweep, because a target inside
 * the arc this frame was probably inside it last frame too. It fails for
 * anything thin and fast - a thrusting lance, a fighter-sized target, a
 * charging ship - because the damaging edge can pass entirely through a
 * target between two samples and never register.
 *
 * So instead of testing the segment [A,B] once, this tests it several times
 * along the path from where it was last frame to where it is now, with the
 * number of samples driven by how far it actually travelled rather than by
 * framerate. Broadphase runs ONCE over the whole swept volume; only the
 * narrowphase repeats per substep, which is what keeps 8 substeps close to
 * free.
 *
 * The caller supplies whatever portion of the blade should deal damage. Pass
 * the full shaft for a sword, or just the last 40 units for a lance tip.
 */
public class armaa_meleeSweep {

    /** Called once per target per sweep, after the target has been marked hit. */
    public interface HitHandler {

        /**
         * @param target     what was hit
         * @param point      world-space contact point
         * @param shieldHit  true if the contact was on a shield, false for hull
         * @param sweepAngle direction the damaging edge was travelling, degrees
         */
        void onHit(CombatEntityAPI target, Vector2f point, boolean shieldHit, float sweepAngle);
    }

    /** Distance the edge may travel between narrowphase samples, in world units. */
    public static final float DEFAULT_MAX_STEP = 15f;

    /** Hard ceiling on substeps so a teleport or phase jump can't spike a frame. */
    public static final int MAX_SUBSTEPS = 8;

    /** Extra broadphase radius beyond the swept volume. */
    private static final float BROADPHASE_MARGIN = 50f;

    public static void sweep(ShipAPI source,
            Vector2f prevA, Vector2f prevB,
            Vector2f a, Vector2f b,
            Set<CombatEntityAPI> alreadyHit,
            HitHandler handler) {
        sweep(source, prevA, prevB, a, b, alreadyHit, DEFAULT_MAX_STEP, handler);
    }

    /**
     * @param source     the wielding ship; used for the friendly-fire filter and
     *                   passed back out through the handler
     * @param prevA      shaft-side end of the damaging segment, last frame
     * @param prevB      tip-side end of the damaging segment, last frame
     * @param a          shaft-side end, this frame
     * @param b          tip-side end, this frame
     * @param alreadyHit targets already struck by this attack instance; mutated
     * @param maxStep    world units per narrowphase sample
     */
    public static void sweep(ShipAPI source,
            Vector2f prevA, Vector2f prevB,
            Vector2f a, Vector2f b,
            Set<CombatEntityAPI> alreadyHit,
            float maxStep,
            HitHandler handler) {

        if (source == null || handler == null) {
            return;
        }

        // ---- how many samples does the distance travelled actually need? ----
        float travel = Math.max(MathUtils.getDistance(prevB, b),
                MathUtils.getDistance(prevA, a));
        int steps = (int) Math.ceil(travel / Math.max(1f, maxStep));
        if (steps < 1) {
            steps = 1;
        }
        if (steps > MAX_SUBSTEPS) {
            steps = MAX_SUBSTEPS;
        }

        // ---- broadphase over the whole swept quad, once ----
        Vector2f center = new Vector2f(
                (prevA.x + prevB.x + a.x + b.x) * 0.25f,
                (prevA.y + prevB.y + a.y + b.y) * 0.25f);

        float radius = 0f;
        radius = Math.max(radius, MathUtils.getDistance(center, prevA));
        radius = Math.max(radius, MathUtils.getDistance(center, prevB));
        radius = Math.max(radius, MathUtils.getDistance(center, a));
        radius = Math.max(radius, MathUtils.getDistance(center, b));
        radius += BROADPHASE_MARGIN;

        List<CombatEntityAPI> candidates = new ArrayList<CombatEntityAPI>();
        for (CombatEntityAPI target : CombatUtils.getEntitiesWithinRange(center, radius)) {
            if (!isValidTarget(source, target, alreadyHit)) {
                continue;
            }
            candidates.add(target);
        }
        if (candidates.isEmpty()) {
            return;
        }

        // Direction the edge is travelling. Falls back to the blade's own
        // orientation when it is thrusting straight rather than sweeping.
        float sweepAngle;
        if (travel > 0.01f) {
            sweepAngle = angleOf(prevB, b);
        } else {
            sweepAngle = angleOf(a, b);
        }

        // ---- narrowphase, sampled along the sweep ----
        Vector2f segA = new Vector2f();
        Vector2f segB = new Vector2f();

        for (int i = 1; i <= steps; i++) {
            float t = (float) i / (float) steps;
            lerp(prevA, a, t, segA);
            lerp(prevB, b, t, segB);

            for (int c = candidates.size() - 1; c >= 0; c--) {
                CombatEntityAPI target = candidates.get(c);

                Vector2f point = contactPoint(target, segA, segB);
                if (point == null) {
                    continue;
                }

                boolean shieldHit = isShieldContact(target, segA, segB);

                alreadyHit.add(target);
                candidates.remove(c);
                handler.onHit(target, point, shieldHit, sweepAngle);
            }

            if (candidates.isEmpty()) {
                return;
            }
        }
    }

    // ------------------------------------------------------------------
    // filtering
    // ------------------------------------------------------------------

    private static boolean isValidTarget(ShipAPI source, CombatEntityAPI target,
            Set<CombatEntityAPI> alreadyHit) {

        if (target == null || target == source) {
            return false;
        }
        if (target.getOwner() == source.getOwner()) {
            return false;
        }
        if (alreadyHit.contains(target)) {
            return false;
        }
        // ordinary projectiles pass straight through; missiles are choppable
        if (target instanceof DamagingProjectileAPI && !(target instanceof MissileAPI)) {
            return false;
        }
        if (target.getCollisionClass() == CollisionClass.NONE) {
            return false;
        }
        if (target instanceof ShipAPI) {
            ShipAPI ship = (ShipAPI) target;
            if (ship.isPhased() || ship.isHulk()) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // narrowphase
    // ------------------------------------------------------------------

    /**
     * Shield first, because a shield is a simple arc test and a hit there ends
     * the query. Only fall through to the collision mesh when the segment got
     * past the shield.
     */
    private static Vector2f contactPoint(CombatEntityAPI target, Vector2f a, Vector2f b) {

        Vector2f shieldHit = armaa_utils.intersectShield(target, a, b);
        if (shieldHit != null) {
            return shieldHit;
        }

        // missiles and anything without usable bounds: treat as a circle
        if (target instanceof MissileAPI || target.getExactBounds() == null) {
            float r = Math.max(4f, target.getCollisionRadius());
            if (distanceFromSegment(a, b, target.getLocation()) <= r) {
                return nearestPointOnSegment(a, b, target.getLocation());
            }
            return null;
        }

        return CollisionUtils.getCollisionPoint(a, b, target);
    }

    private static boolean isShieldContact(CombatEntityAPI target, Vector2f a, Vector2f b) {
        return armaa_utils.intersectShield(target, a, b) != null;
    }

    // ------------------------------------------------------------------
    // geometry
    // ------------------------------------------------------------------

    private static void lerp(Vector2f from, Vector2f to, float t, Vector2f out) {
        out.x = from.x + (to.x - from.x) * t;
        out.y = from.y + (to.y - from.y) * t;
    }

    private static float angleOf(Vector2f from, Vector2f to) {
        return (float) Math.toDegrees(Math.atan2(to.y - from.y, to.x - from.x));
    }

    public static Vector2f nearestPointOnSegment(Vector2f a, Vector2f b, Vector2f p) {
        float abx = b.x - a.x;
        float aby = b.y - a.y;
        float ab2 = abx * abx + aby * aby;
        if (ab2 == 0f) {
            return new Vector2f(a);
        }
        float t = ((p.x - a.x) * abx + (p.y - a.y) * aby) / ab2;
        if (t < 0f) {
            t = 0f;
        }
        if (t > 1f) {
            t = 1f;
        }
        return new Vector2f(a.x + abx * t, a.y + aby * t);
    }

    public static float distanceFromSegment(Vector2f a, Vector2f b, Vector2f p) {
        return MathUtils.getDistance(p, nearestPointOnSegment(a, b, p));
    }
}
