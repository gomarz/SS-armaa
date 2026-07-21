package data.scripts.ai;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.GuidedMissileAI;
import com.fs.starfarer.api.combat.MissileAIPlugin;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import org.magiclib.util.MagicRender;
import data.scripts.weapons.armaa_energyLashThrowerEffect;
import java.awt.Color;
import java.util.List;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;

/**
 * Energy Lash embedded-spike AI (self-contained; diverged from armaa_harpoonAI by Tartiflette).
 *
 * Behavior: on spawn, latches to the nearest hit target from the thrower's HIT list, then
 * rigidly tracks (sticks to) that anchor with a fixed offset for a FIXED DURATION, then
 * auto-releases (applyDetonation) which lets the rope retract.
 *
 * DIVERGENCE FROM ORIGINAL:
 *  - The ACCELERATION-based tear-off is REMOVED (the lash's own pull would trip it).
 *  - Added an OVER-RANGE tear-off: the pinned spike does NOT expire from range on its own (the AI
 *    force-sets its position every frame, so it never travels), so without this the rope extends
 *    infinitely. This enforces the tether's max length = the weapon's range.
 *  - Embed duration is a named constant (EMBED_DURATION) instead of the magic "99".
 */
public class armaa_energyLashAI implements MissileAIPlugin, GuidedMissileAI {

    private CombatEngineAPI engine;
    private final MissileAPI missile;
    private final ShipAPI launchingShip;
    private CombatEntityAPI target;
    private CombatEntityAPI anchor;

    private Vector2f offset = new Vector2f();
    private float angle = 0;

    private final IntervalUtil timer = new IntervalUtil(0.1f, 0.2f);
    private boolean runOnce = false;
    private boolean tearOff = false;
    private final IntervalUtil blink = new IntervalUtil(1f, 1f);

    // Fixed embed duration (seconds) before auto-release. Tune to match the EMP-over-time window.
    private static final float EMBED_DURATION = 4f;

    // The target can break the embed by getting its shield arc over the spike -- counterplay for the
    // EMP lock (AI keeps shields up, so this frequently severs the embed against shielded targets,
    // turning the lash into a punish-the-shields-down tool). A short grace period guarantees SOME
    // payoff for landing the embed before the target can shield it off.
    private static final float SHIELD_SEVER_GRACE = 0.6f;

    public armaa_energyLashAI(MissileAPI missile, ShipAPI launchingShip) {
        this.missile = missile;
        this.launchingShip = launchingShip;
    }

    @Override
    public void advance(float amount) {
        if (engine != Global.getCombatEngine()) {
            this.engine = Global.getCombatEngine();
        }
        if (engine.isPaused()) {
            return;
        }

        if (!runOnce) {
            runOnce = true;
            List<CombatEntityAPI> list =
                    ((armaa_energyLashThrowerEffect) missile.getWeapon().getEffectPlugin()).getHITS();

            if (list.isEmpty()) {
                missile.flameOut();
                return;
            }

            // nearest hit becomes the anchor
            float range = 1000000;
            for (CombatEntityAPI e : list) {
                if (MathUtils.getDistanceSquared(missile, e) < range) {
                    target = e;
                    anchor = e;
                }
            }
            if (anchor == null) {
                return;
            }

            ((armaa_energyLashThrowerEffect) missile.getWeapon().getEffectPlugin()).setDetonation(anchor);

            offset = new Vector2f(missile.getLocation());
            Vector2f.sub(offset, new Vector2f(anchor.getLocation()), offset);
            VectorUtils.rotate(offset, -anchor.getFacing(), offset);
            angle = MathUtils.getShortestRotation(anchor.getFacing(), missile.getFacing());
            return;
        } else {
            if (anchor == null
                    || ((armaa_energyLashThrowerEffect) missile.getWeapon().getEffectPlugin()).getDetonation(anchor)) {
                missile.setCollisionClass(CollisionClass.MISSILE_FF);
                return;
            }
        }

        if (tearOff) {
            return;
        }

        // ---- legitimate break conditions (acceleration check intentionally removed) ----
        boolean fooled    = (target != anchor);
        boolean escaped   = (anchor.getCollisionClass() == CollisionClass.NONE);
        boolean emped     = missile.isFizzling();
        boolean outlasted = missile.isFading();
        boolean dead = target.getOwner() > 1;
        boolean overloaded = launchingShip.getFluxTracker().isOverloaded();
        // over-range: the anchor has dragged the spike beyond the weapon's range. The pinned spike
        // never expires from range on its own (its position is force-set), so this enforces max length.
        boolean overRange = false;
        ShipAPI src = (launchingShip != null) ? launchingShip : missile.getSource();
        if (src != null && missile.getWeapon() != null) {
            overRange = MathUtils.getDistance(src.getLocation(), missile.getLocation())
                    > missile.getWeapon().getRange();
        }
        // shielded: the target got its active shield arc over the embedded spike -> it knocks the
        // hook off. Counterplay for the EMP lock. Suppressed during the grace window so landing the
        // embed always yields a brief payoff even against a target that immediately raises shields.
        boolean shielded = false;
        if (missile.getElapsed() > SHIELD_SEVER_GRACE && anchor instanceof ShipAPI) {
            ShipAPI as = (ShipAPI) anchor;
            if (as.getShield() != null && as.getShield().isOn()
                    && as.getShield().isWithinArc(missile.getLocation())) {
                shielded = true;
            }
        }

        if (fooled || escaped || emped || outlasted || overRange || shielded || dead || overloaded) {
            tearOff = true;
            missile.setArmingTime(missile.getElapsed() + 0.25f);
            missile.setCollisionClass(CollisionClass.MISSILE_FF);
            missile.flameOut();
            if (missile.getSource() != null) {
                String msg = shielded ? "Lash deflected!" : "Lash severed!";
                Global.getCombatEngine().addFloatingText(missile.getSource().getLocation(),
                        msg, 25f, Color.YELLOW, missile.getSource(), 4f, 1f);
            }
            return;
        }

        // ---- stick to the anchor ----
        Vector2f loc = new Vector2f(offset);
        VectorUtils.rotate(offset, anchor.getFacing(), loc);
        Vector2f.add(loc, anchor.getLocation(), loc);
        missile.getLocation().set(loc);
        missile.setFacing(anchor.getFacing() + angle);

        // ---- fixed-duration auto-release ----
        if (missile.getElapsed() > EMBED_DURATION) {
            missile.setCollisionClass(CollisionClass.MISSILE_FF);
            ((armaa_energyLashThrowerEffect) missile.getWeapon().getEffectPlugin()).applyDetonation(anchor);
            return;
        }

        // countdown blink (color shifts as release nears)
        float frac = missile.getElapsed() / EMBED_DURATION;
        Color color = (frac < 0.5f) ? Color.green : (frac < 0.8f) ? Color.yellow : Color.red;
        blink.advance(amount);
        if (blink.intervalElapsed()) {
            blink.setInterval(Math.max(0.1f, blink.getMinInterval() * 0.66f),
                    Math.max(0.1f, blink.getMinInterval() * 0.66f));
            engine.addHitParticle(missile.getLocation(), missile.getVelocity(), 50, 0.4f, 0.1f, color);
        }

        // stuck sparkle fx
        if (MagicRender.screenCheck(0.25f, loc)) {
            timer.advance(amount);
            if (timer.intervalElapsed()) {
                Vector2f vel = new Vector2f(anchor.getVelocity());
                vel.scale(0.8f);
                Vector2f.add(vel, MathUtils.getRandomPointInCone(new Vector2f(),
                        (6 - missile.getElapsed()) * 15, missile.getFacing() + 160, missile.getFacing() + 200), vel);
                engine.addHitParticle(loc, vel, 3 + 3 * (float) Math.random(), 1,
                        0.1f + 0.5f * (float) Math.random(),
                        new Color(0.75f + 0.25f * (float) Math.random(),
                                0.25f + 0.5f * (float) Math.random(),
                                0.25f * (float) Math.random()));
            }
        }
    }

    @Override
    public CombatEntityAPI getTarget() {
        return target;
    }

    @Override
    public void setTarget(CombatEntityAPI target) {
        this.target = target;
    }

    public void init(CombatEngineAPI engine) {}
}