package data.scripts.weapons.harper;

import java.awt.Color;
import java.util.EnumSet;

import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;

public class armaa_capePlugin extends BaseCombatLayeredRenderingPlugin {

    /**
     * One-shot startup logging. Never log per frame - it is file I/O.
     */
    public static final boolean DEBUG = true;

    private static final CombatEngineLayers LAYER_UNDER = CombatEngineLayers.FRIGATES_LAYER;
    private static final CombatEngineLayers LAYER_OVER = CombatEngineLayers.FIGHTERS_LAYER;
    /**
     * Under-hull span, as a drawn distance from the anchor in full-length
     * segments.
     */
    private static final int LAYER_SPLIT_NODE = 3;
    /**
     * Half a texel at 256px. Keeps v=1 from sampling past the last row.
     */
    private static final float UV_INSET = 0.5f / 256f;
    // ---- simulation ----
    private static final float FIXED_DT = 1f / 60f;
    private static final int MAX_STEPS_PER_FRAME = 4;
    private static final int CONSTRAINT_ITERATIONS = 4;
    /**
     * Velocity retained per step. Lower is limper.
     */
    private static final float DAMPING = 0.92f;
    /**
     * Pose pull per step at the collar.
     */
    private static final float POSE_STIFFNESS = 0.10f;
    /**
     * How much weaker the pull is at the hem. Higher lets the hem lag into
     * curves.
     */
    private static final float POSE_HEM_FALLOFF = 0.8f;
    /**
     * Anchor speed at which the pose pull drops to its floor.
     */
    private static final float POSE_FADE_SPEED = 90f;
    /**
     * Floor on the pose pull at speed. Without some, the cape flails.
     */
    private static final float POSE_MIN_WEIGHT = 0.35f;
    /**
     * Below this speed, velocity direction is noise - fall back to hull rear.
     * Never 0.
     */
    private static final float VELOCITY_POSE_MIN_SPEED = 25f;
    /**
     * Max sweep rate of the trail direction, deg/s.
     */
    private static final float TRAIL_TURN_RATE = 200f;
    /**
     * Max bend per joint, degrees. Lowering this far makes turns jittery.
     */
    private static final float MAX_JOINT_BEND = 28f;

    // ---- body ----
    /**
     * How far off the rear the cape may swing, degrees either side.
     */
    private static final float REAR_CONE_HALF_ANGLE = 78f;
    private static final float BODY_RADIUS_MULT = 0.55f;
    private static final float BODY_PUSH = 0.6f;
    /**
     * Drawn length, in collar half-widths, over which the collar untwists from
     * the shoulder line. Below ~1.4 the collar quads fold when the cape hangs
     * far to one side.
     */
    private static final float COLLAR_LOCK_WIDTHS = 1.6f;

    // ---- sway ----
    /**
     * Peak hem deviation, degrees, before gusts and speed.
     */
    private static final float SWAY_AMP = 20f;
    /**
     * Three swells: speed (rad/s), waves along the cape, weight. Non-harmonic
     * on purpose.
     */
    private static final float[] SWAY_FREQ = {1.9f, 3.1f, 5.3f};
    private static final float[] SWAY_WAVES = {0.55f, 0.9f, 1.6f};
    private static final float[] SWAY_WEIGHT = {1.0f, 0.45f, 0.18f};
    /**
     * Gust envelope floor and how often it picks a new level (1/rate seconds).
     */
    private static final float SWAY_CALM = 0.3f;
    private static final float SWAY_GUST_RATE = 0.3f;
    /**
     * Slow drift of the centre line, degrees at the hem.
     */
    private static final float SWAY_LEAN = 7f;
    private static final float SWAY_LEAN_RATE = 0.15f;
    /**
     * Sway at full speed relative to at rest. Taut cloth ripples less.
     */
    private static final float SWAY_SPEED_MIX = 0.55f;
    /**
     * Sway while hanging relative to lifted. Hanging cloth barely moves seen
     * from above.
     */
    private static final float SWAY_REST_MIX = 0.15f;

    // ---- fake 3D ----
    /**
     * Drawn length at rest. Lower hangs more.
     */
    private static final float REST_FORESHORTEN = 0.15f;
    /**
     * Hull speed at which the cape fully lifts.
     */
    private static final float LIFT_FULL_SPEED = 110f;
    /**
     * Speed-to-lift curve. 2 follows drag: slow drifting barely lifts it.
     */
    private static final float LIFT_EXPONENT = 2f;
    /**
     * Speed filter time constant, seconds. Stops the length breathing with aim
     * jitter.
     */
    private static final float FORESHORTEN_SMOOTH_TIME = 0.5f;
    /**
     * Brightness at rest. Never change width with foreshortening - it turns
     * into a blob.
     */
    private static final float REST_DARKEN = 0.5f;
    /**
     * Extra darkening toward the hem at rest, as the cloth falls away into
     * shadow.
     */
    private static final float REST_HEM_DARKEN = 0.5f;

    // ---- appearance ----
    private static final String TEXTURE = "graphics/armaa/fx/armaa_cape_gold_trim.png";
    /**
     * Keep near 1. A faded hem reads as an engine trail; cut the hem in the
     * texture alpha.
     */
    private static final float HEM_ALPHA = 0.90f;

    private final ShipAPI ship;
    private final Vector2f anchorOffset;
    private final int nodes;
    private final float segLength;
    private final float halfWidthBase;
    private final float hemWidthMult;
    private final float renderRadius;
    private final Color tint;

    // simulation state
    private final float[] x, y, px, py;
    // per-frame render scratch: drawn positions and ribbon normals
    private final float[] rx, ry, rnx, rny;

    private final int noiseSeed = (int) (Math.random() * 1000000);
    private final float[] swayPhase = new float[SWAY_FREQ.length];

    private float accumulator = 0f;
    private float time = 0f;
    private boolean seeded = false;
    private boolean loggedOnce = false;

    private float anchorX, anchorY, prevAnchorX, prevAnchorY;
    private float anchorSpeed = 0f;
    /**
     * Slow-filtered hull speed; drives the fake 3D only.
     */
    private float foreshortenSpeed = 0f;
    private float trailAngle = Float.NaN;

    private CombatEntityAPI hostEntity;
    private SpriteAPI sprite;

    public armaa_capePlugin(ShipAPI ship, Vector2f anchorOffset,
            int nodes, float segLength, float halfWidth) {
        this(ship, anchorOffset, nodes, segLength, halfWidth, 1.25f, Color.white);
    }

    /**
     * @param anchorOffset attachment point in ship sprite space (x right, y
     * forward)
     * @param nodes chain length; more, shorter segments curve more smoothly
     * @param segLength world units between nodes
     * @param halfWidth half the ribbon width at the collar, about half the
     * torso
     * @param hemWidthMult hem width relative to the collar; above 1 flares like
     * cloth
     * @param tint multiplied into the texture; white leaves it alone
     */
    public armaa_capePlugin(ShipAPI ship, Vector2f anchorOffset,
            int nodes, float segLength, float halfWidth,
            float hemWidthMult, Color tint) {
        this.ship = ship;
        this.anchorOffset = new Vector2f(anchorOffset);
        this.nodes = Math.max(3, nodes);
        this.segLength = segLength;
        this.halfWidthBase = halfWidth;
        this.hemWidthMult = hemWidthMult;
        this.tint = tint != null ? tint : Color.white;
        this.renderRadius = this.nodes * segLength
                + halfWidth * Math.max(1f, hemWidthMult) * 2f + 40f;

        int n = this.nodes;
        x = new float[n];
        y = new float[n];
        px = new float[n];
        py = new float[n];
        rx = new float[n];
        ry = new float[n];
        rnx = new float[n];
        rny = new float[n];

        for (int k = 0; k < swayPhase.length; k++) {
            swayPhase[k] = (hash(k, noiseSeed) + 1f) * (float) Math.PI;
        }
    }

    @Override
    public void init(CombatEntityAPI entity) {
        super.init(entity);
        hostEntity = entity;
        try {
            Global.getSettings().loadTexture(TEXTURE);
        } catch (Exception e) {
            Global.getLogger(armaa_capePlugin.class)
                    .error("cape texture failed to load: " + TEXTURE, e);
        }
        sprite = Global.getSettings().getSprite(TEXTURE);

        if (DEBUG && !loggedOnce) {
            loggedOnce = true;
            Global.getLogger(armaa_capePlugin.class).info(
                    "cape init: sprite=" + (sprite == null ? "NULL" : "ok")
                    + " nodes=" + nodes + " length=" + (nodes * segLength)
                    + " collarWidth=" + (halfWidthBase * 2f)
                    + " hemWidth=" + (halfWidthBase * 2f * hemWidthMult)
                    + " bodyRadius=" + bodyRadius()
                    + " splitNode=" + splitNode());
        }
    }

    @Override
    public EnumSet<CombatEngineLayers> getActiveLayers() {
        return EnumSet.of(LAYER_UNDER, LAYER_OVER);
    }

    @Override
    public float getRenderRadius() {
        return renderRadius;
    }

    @Override
    public boolean isExpired() {
        return ship == null || !ship.isAlive() || ship.isHulk();
    }

    private int splitNode() {
        return Math.max(1, Math.min(LAYER_SPLIT_NODE, nodes - 1));
    }

    private float bodyRadius() {
        return ship == null ? 0f : ship.getCollisionRadius() * BODY_RADIUS_MULT;
    }

    // ------------------------------------------------------------------
    // simulation
    // ------------------------------------------------------------------
    @Override
    public void advance(float amount) {
        if (ship == null || Global.getCombatEngine().isPaused() || amount <= 0f) {
            return;
        }

        updateAnchor(amount);
        updateForeshortenSpeed(amount);
        updateTrailAngle(amount);

        // The engine culls against the wrapper entity; keep it on the cape.
        if (hostEntity != null && hostEntity.getLocation() != null) {
            hostEntity.getLocation().set(anchorX, anchorY);
        }

        if (!seeded) {
            seed();
            seeded = true;
            return;
        }

        if (!Global.getCombatEngine().getViewport().isNearViewport(
                new Vector2f(anchorX, anchorY), renderRadius * 2f)) {
            seed();
            accumulator = 0f;
            return;
        }

        accumulator += amount;
        int steps = 0;
        while (accumulator >= FIXED_DT && steps < MAX_STEPS_PER_FRAME) {
            step();
            accumulator -= FIXED_DT;
            steps++;
        }
        if (steps >= MAX_STEPS_PER_FRAME) {
            accumulator = 0f;   // fell behind; drop the backlog rather than spiral
        }
    }

    private void updateAnchor(float amount) {
        float offLen = anchorOffset.length();
        float offAng = (float) Math.toDegrees(Math.atan2(anchorOffset.y, anchorOffset.x));
        Vector2f world = MathUtils.getPointOnCircumference(
                ship.getLocation(), offLen, ship.getFacing() - 90f + offAng);

        prevAnchorX = anchorX;
        prevAnchorY = anchorY;
        anchorX = world.x;
        anchorY = world.y;
        if (!seeded) {
            return;
        }

        // Real elapsed time, not FIXED_DT, or speed is wrong off 60 fps.
        float dx = anchorX - prevAnchorX;
        float dy = anchorY - prevAnchorY;
        float inst = (float) Math.sqrt(dx * dx + dy * dy) / amount;
        anchorSpeed = anchorSpeed * 0.8f + Math.min(inst, 600f) * 0.2f;
    }

    /**
     * Hull velocity, not anchor motion, so rotating in place doesn't count.
     */
    private void updateForeshortenSpeed(float amount) {
        float speed = ship.getVelocity().length();
        if (!seeded) {
            foreshortenSpeed = speed;
            return;
        }
        float k = 1f - (float) Math.exp(-amount / FORESHORTEN_SMOOTH_TIME);
        foreshortenSpeed += (speed - foreshortenSpeed) * k;
    }

    /**
     * Rest direction: velocity trail when moving, hull rear when not, rate
     * limited and clamped to the rear cone. The clamp is what makes reversing
     * spill the cloth sideways instead of over the head.
     */
    private void updateTrailAngle(float amount) {
        float rear = ship.getFacing() + 180f;
        Vector2f vel = ship.getVelocity();
        float target = vel.length() > VELOCITY_POSE_MIN_SPEED
                ? VectorUtils.getFacing(vel) + 180f : rear;
        target = clampToRearCone(target, rear);

        if (Float.isNaN(trailAngle)) {
            trailAngle = target;
            return;
        }
        float delta = MathUtils.getShortestRotation(trailAngle, target);
        float maxStep = TRAIL_TURN_RATE * amount;
        delta = Math.max(-maxStep, Math.min(maxStep, delta));
        trailAngle = clampToRearCone(MathUtils.clampAngle(trailAngle + delta), rear);
    }

    private float clampToRearCone(float angle, float rear) {
        float delta = MathUtils.getShortestRotation(rear, angle);
        delta = Math.max(-REAR_CONE_HALF_ANGLE, Math.min(REAR_CONE_HALF_ANGLE, delta));
        return MathUtils.clampAngle(rear + delta);
    }

    private void seed() {
        float ang = Float.isNaN(trailAngle) ? ship.getFacing() + 180f : trailAngle;
        float ax = (float) Math.cos(Math.toRadians(ang));
        float ay = (float) Math.sin(Math.toRadians(ang));
        for (int i = 0; i < nodes; i++) {
            x[i] = px[i] = anchorX + ax * segLength * i;
            y[i] = py[i] = anchorY + ay * segLength * i;
        }
    }

    private void step() {
        time += FIXED_DT;

        // node 0 is pinned to the anchor
        x[0] = px[0] = anchorX;
        y[0] = py[0] = anchorY;

        // integrate
        for (int i = 1; i < nodes; i++) {
            float vx = (x[i] - px[i]) * DAMPING;
            float vy = (y[i] - py[i]) * DAMPING;
            px[i] = x[i];
            py[i] = y[i];
            x[i] += vx;
            y[i] += vy;
        }

        // pose pull, aimed from each node's parent so turns propagate down the chain
        float speedT = Math.min(1f, anchorSpeed / POSE_FADE_SPEED);
        float poseWeight = POSE_STIFFNESS * (POSE_MIN_WEIGHT + (1f - POSE_MIN_WEIGHT) * (1f - speedT));

        float speedMix = 1f - (1f - SWAY_SPEED_MIX) * speedT;
        float hangMix = SWAY_REST_MIX + (1f - SWAY_REST_MIX) * lift();
        float gust = SWAY_CALM + (1f - SWAY_CALM)
                * (0.5f + 0.5f * noise1(time * SWAY_GUST_RATE, noiseSeed));
        float swayScale = SWAY_AMP * gust * speedMix * hangMix;
        float lean = SWAY_LEAN * hangMix * noise1(time * SWAY_LEAN_RATE, noiseSeed + 7919);

        for (int i = 1; i < nodes; i++) {
            float t = (float) i / (float) (nodes - 1);
            float w = poseWeight * (1f - POSE_HEM_FALLOFF * t);
            float rad = (float) Math.toRadians(
                    trailAngle + (swayAngle(t) * swayScale + lean) * t);
            float tx = x[i - 1] + (float) Math.cos(rad) * segLength;
            float ty = y[i - 1] + (float) Math.sin(rad) * segLength;
            x[i] += (tx - x[i]) * w;
            y[i] += (ty - y[i]) * w;
        }

        // constraints
        for (int it = 0; it < CONSTRAINT_ITERATIONS; it++) {
            for (int i = 0; i < nodes - 1; i++) {
                float dx = x[i + 1] - x[i];
                float dy = y[i + 1] - y[i];
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d < 1e-5f) {
                    continue;
                }
                float diff = (d - segLength) / d;
                if (i == 0) {
                    // anchor is immovable; the child takes all of it
                    x[1] -= dx * diff;
                    y[1] -= dy * diff;
                } else {
                    x[i] += dx * diff * 0.5f;
                    y[i] += dy * diff * 0.5f;
                    x[i + 1] -= dx * diff * 0.5f;
                    y[i + 1] -= dy * diff * 0.5f;
                }
            }
            applyBendLimit();
            applyBodyCollision();
        }
    }

    /**
     * Sum of the three swells at position t along the cape, roughly -1..1.
     */
    private float swayAngle(float t) {
        float sum = 0f, wsum = 0f;
        for (int k = 0; k < SWAY_FREQ.length; k++) {
            float phase = time * SWAY_FREQ[k] - t * SWAY_WAVES[k] * 6.2831853f + swayPhase[k];
            sum += (float) Math.sin(phase) * SWAY_WEIGHT[k];
            wsum += SWAY_WEIGHT[k];
        }
        return wsum > 0f ? sum / wsum : 0f;
    }

    /**
     * Smooth 1D value noise, -1..1.
     */
    private static float noise1(float t, int salt) {
        int i = (int) Math.floor(t);
        float f = t - i;
        float a = hash(i, salt);
        float s = f * f * (3f - 2f * f);
        return a + (hash(i + 1, salt) - a) * s;
    }

    /**
     * Integer hash to -1..1.
     */
    private static float hash(int n, int salt) {
        int h = n * 374761393 + salt * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFF) / 32767.5f - 1f;
    }

    /**
     * Push nodes out of the wearer so the cloth goes around the body, not
     * through.
     */
    private void applyBodyCollision() {
        float r = bodyRadius();
        if (r <= 1f) {
            return;
        }
        Vector2f c = ship.getLocation();
        for (int i = 1; i < nodes; i++) {
            float dx = x[i] - c.x;
            float dy = y[i] - c.y;
            float d2 = dx * dx + dy * dy;
            if (d2 >= r * r || d2 < 1e-6f) {
                continue;
            }
            float d = (float) Math.sqrt(d2);
            float push = (r - d) * BODY_PUSH;
            x[i] += (dx / d) * push;
            y[i] += (dy / d) * push;
        }
    }

    /**
     * Clamp the angle at each joint so the strip can't fold through itself. px
     * moves with x so the clamp doesn't kick the node - keeps low limits
     * stable.
     */
    private void applyBendLimit() {
        for (int i = 1; i < nodes - 1; i++) {
            float inAng = (float) Math.toDegrees(Math.atan2(y[i] - y[i - 1], x[i] - x[i - 1]));
            float outAng = (float) Math.toDegrees(Math.atan2(y[i + 1] - y[i], x[i + 1] - x[i]));
            float delta = MathUtils.getShortestRotation(inAng, outAng);
            if (Math.abs(delta) <= MAX_JOINT_BEND) {
                continue;
            }
            float rad = (float) Math.toRadians(inAng + (delta > 0 ? MAX_JOINT_BEND : -MAX_JOINT_BEND));
            float newX = x[i] + (float) Math.cos(rad) * segLength;
            float newY = y[i] + (float) Math.sin(rad) * segLength;
            px[i + 1] += newX - x[i + 1];
            py[i + 1] += newY - y[i + 1];
            x[i + 1] = newX;
            y[i + 1] = newY;
        }
    }

    // ------------------------------------------------------------------
    // fake 3D
    // ------------------------------------------------------------------
    /**
     * 0 = hanging straight down, 1 = streaming. Drives length, shading and
     * sway.
     */
    private float lift() {
        float s = Math.min(1f, foreshortenSpeed / LIFT_FULL_SPEED);
        return (float) Math.pow(s, LIFT_EXPONENT);
    }

    /**
     * Drawn length fraction. Uniform along the chain, as rotation about the
     * shoulders is.
     */
    private float foreshorten() {
        return REST_FORESHORTEN + (1f - REST_FORESHORTEN) * lift();
    }

    // ------------------------------------------------------------------
    // render
    // ------------------------------------------------------------------
    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        if (!seeded || ship == null || sprite == null) {
            return;
        }
        float alpha = ship.getCombinedAlphaMult();
        if (alpha <= 0.01f) {
            return;
        }

        float f = foreshorten();
        computeFrame(f);

        // Seam at a fixed drawn distance: node split/f. Fractional so it slides.
        float split = Math.min(splitNode() / Math.max(f, 1e-3f), nodes - 1);
        if (layer == LAYER_UNDER) {
            drawStrip(0f, split, alpha);
        } else if (layer == LAYER_OVER) {
            drawStrip(split, nodes - 1, alpha);
        }
    }

    /**
     * Fills rx/ry with drawn positions and rnx/rny with ribbon normals.
     */
    private void computeFrame(float f) {
        // Shoulder line, signed to match a cape trailing straight back. Fixed
        // sign: choosing it by dot product flips the collar when sideways.
        float fr = (float) Math.toRadians(ship.getFacing());
        float shoulderX = (float) Math.sin(fr);
        float shoulderY = -(float) Math.cos(fr);
        float hang = 1f - lift();

        // While hanging, straighten toward the collar-to-hem chord (keeps lag visible).
        float chX = x[nodes - 1] - anchorX;
        float chY = y[nodes - 1] - anchorY;
        float chL = (float) Math.sqrt(chX * chX + chY * chY);
        if (chL > 1e-3f) {
            chX /= chL;
            chY /= chL;
        } else {
            chX = (float) Math.cos(Math.toRadians(trailAngle));
            chY = (float) Math.sin(Math.toRadians(trailAngle));
        }
        for (int i = 0; i < nodes; i++) {
            float projX = anchorX + (x[i] - anchorX) * f;
            float projY = anchorY + (y[i] - anchorY) * f;
            float strX = anchorX + chX * segLength * i * f;
            float strY = anchorY + chY * segLength * i * f;
            rx[i] = projX + (strX - projX) * hang;
            ry[i] = projY + (strY - projY) * hang;
        }

        float arc = 0f;
        float lockLen = COLLAR_LOCK_WIDTHS * Math.max(1f, halfWidthBase);
        for (int i = 0; i < nodes; i++) {
            int a = i < nodes - 1 ? i : i - 1;
            float dx = rx[a + 1] - rx[a];
            float dy = ry[a + 1] - ry[a];
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len < 1e-5f) {
                len = 1f;
            }
            float nx = -dy / len;
            float ny = dx / len;

            // Collar lies across the shoulders, untwisting over lockLen of drawn
            // length; while hanging, every cross-section is held there by hang.
            if (i > 0) {
                float adx = rx[i] - rx[i - 1];
                float ady = ry[i] - ry[i - 1];
                arc += (float) Math.sqrt(adx * adx + ady * ady);
            }
            float cw = Math.max(Math.max(0f, 1f - arc / lockLen), hang);
            if (cw > 0f) {
                float bx = nx + (shoulderX - nx) * cw;
                float by = ny + (shoulderY - ny) * cw;
                float bl = (float) Math.sqrt(bx * bx + by * by);
                if (bl > 1e-3f) {
                    nx = bx / bl;
                    ny = by / bl;
                } else {
                    nx = shoulderX;
                    ny = shoulderY;
                }
            }
            rnx[i] = nx;
            rny[i] = ny;
        }
    }

    /**
     * Draws between fractional node positions. t spans the full chain, so the
     * seam is continuous.
     */
    private void drawStrip(float from, float to, float alpha) {
        if (to - from < 1e-4f) {
            return;
        }
        float lift = lift();
        float shade = REST_DARKEN + (1f - REST_DARKEN) * lift;
        float hang = 1f - lift;

        sprite.bindTexture();

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        GL11.glBegin(GL11.GL_QUAD_STRIP);
        emitCrossSection(from, alpha, shade, hang);
        for (int i = (int) from + 1; i < to; i++) {
            emitCrossSection(i, alpha, shade, hang);
        }
        emitCrossSection(to, alpha, shade, hang);
        GL11.glEnd();

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    /**
     * One pair of strip vertices at fractional node position p.
     */
    private void emitCrossSection(float p, float alpha, float shade, float hang) {
        int i0 = Math.min((int) p, nodes - 2);
        float a = p - i0;

        float cx = rx[i0] + (rx[i0 + 1] - rx[i0]) * a;
        float cy = ry[i0] + (ry[i0 + 1] - ry[i0]) * a;
        float nx = rnx[i0] + (rnx[i0 + 1] - rnx[i0]) * a;
        float ny = rny[i0] + (rny[i0 + 1] - rny[i0]) * a;
        float nl = (float) Math.sqrt(nx * nx + ny * ny);
        if (nl > 1e-4f) {
            nx /= nl;
            ny /= nl;
        } else {
            nx = rnx[i0];
            ny = rny[i0];
        }

        float t = p / (float) (nodes - 1);
        float hw = halfWidthBase * (1f + (hemWidthMult - 1f) * t);
        float al = alpha * (1f - (1f - HEM_ALPHA) * t);
        float s = shade * (1f - REST_HEM_DARKEN * hang * t);

        GL11.glColor4ub((byte) (int) (tint.getRed() * s),
                (byte) (int) (tint.getGreen() * s),
                (byte) (int) (tint.getBlue() * s),
                (byte) (int) (255 * al));
        float tv = UV_INSET + t * (1f - 2f * UV_INSET);

        GL11.glTexCoord2f(UV_INSET, tv);
        GL11.glVertex2f(cx + nx * hw, cy + ny * hw);
        GL11.glTexCoord2f(1f - UV_INSET, tv);
        GL11.glVertex2f(cx - nx * hw, cy - ny * hw);
    }
}
