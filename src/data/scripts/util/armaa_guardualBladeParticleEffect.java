package data.scripts.util;

import com.fs.starfarer.api.Global;
import java.util.*;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;

import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.loading.DamagingExplosionSpec;
import com.fs.starfarer.api.util.Misc;
import java.awt.Color;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

public class armaa_guardualBladeParticleEffect extends BaseEveryFrameCombatPlugin {

    private final IntervalUtil interval = new IntervalUtil(0.05f, 0.05f);
    private DamagingProjectileAPI proj;
    private ShipAPI ship;

    public armaa_guardualBladeParticleEffect(DamagingProjectileAPI proj) {
        this.proj = proj;
        this.ship = ship;

    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (Global.getCombatEngine().isPaused()) {
            return;
        }
        if (proj.isFading()) {
            DamagingExplosionSpec spec = new DamagingExplosionSpec(0.5f, 50f, 25f, 100f, 50f, CollisionClass.HITS_SHIPS_AND_ASTEROIDS, CollisionClass.HITS_SHIPS_AND_ASTEROIDS, 5f, 10f, 1f, 25, new Color (0,111,50,150), new Color(0, 250, 150, 155));
            Global.getCombatEngine().spawnDamagingExplosion(spec, proj.getSource(), proj.getLocation());
            Global.getCombatEngine().removeEntity(proj);
        }
        if (proj == null || proj.isFading() || proj.isExpired()) {
            Global.getCombatEngine().removePlugin(this);
        }
        interval.advance(amount);
        if (interval.intervalElapsed()) {
            org.magiclib.util.MagicRender.battlespace(
                    Global.getSettings().getSprite(proj.getProjectileSpec().getBulletSpriteName()),
                    proj.getLocation(),
                    new Vector2f(),
                    new Vector2f(proj.getProjectileSpec().getWidth(), proj.getProjectileSpec().getWidth() / 2),
                    new Vector2f(50f, 50f),
                    proj.getFacing() - 90f,
                    0f,
                    new Color(155, 155, 155, 150),
                    true,
                    0f,
                    0f,
                    0f,
                    0f,
                    0f,
                    0f,
                    0.1f,
                    0.05f,
                    CombatEngineLayers.BELOW_SHIPS_LAYER
            );
            spawnAcrossWidth(proj);
        }
    }
    /**
     * Spawns particles across the projectile's width, perpendicular to its
     * travel.
     *
     * @param engine CombatEngineAPI
     * @param proj DamagingProjectileAPI (or pass loc/angle yourself)
     * @param width visual/desired width in world units (px)
     * @param count how many particles across (>=2)
     * @param size particle size
     * @param life particle lifetime (seconds)
     * @param color particle color
     */
    //beam particles
    private static final Color PARTICLE_COLOR = new Color (0,111,50,150);
    private static final float PARTICLE_SIZE_MIN = 5f;
    private static final float PARTICLE_SIZE_MAX = 15f;
    private static final float PARTICLE_DURATION_MIN = 0.5f;
    private static final float PARTICLE_DURATION_MAX = 1f;
    private static final float PARTICLE_INERTIA_MULT = 0.5f; //This is how much the particles retain their spawning ship's velocity; 0f means no retained velocity, 1f means full retained velocity.
    private static final float PARTICLE_DRIFT = 50f; //This is how much the particles "move" in a random direction when spawned, at most; their actual speed is between 0 and this value (plus any inertia, see above)
    private static final float PARTICLE_DENSITY = 0.05f; //Measured in particles per SU^2 (area unit) and second; lower means less particles. This is multiplied by charge level, too
    private static final float PARTICLE_SPAWN_WIDTH_MULT = 0.5f; //Multiplier for how wide the particles are allowed to spawn from the beam center; at 1f, it's equal to beam width, at 0f they only spawn in the center. Note that true beam width is usually much bigger than the visual beam width

    public static void spawnAcrossWidth(DamagingProjectileAPI proj) {
        //yoinked from LoA
        //Calculate how many particles should be spawned this frame
        Vector2f loc = proj.getLocation();           // projectile center
        float facing = proj.getFacing();             // world angle in degrees
        float halfWidth = proj.getProjectileSpec().getWidth() * 0.5f;

        // Turn facing into radians
        float ang = (float) Math.toRadians(facing);

        // Facing unit vector
        float dx = (float) Math.cos(ang);
        float dy = (float) Math.sin(ang);

        // Perpendicular (normal)
        Vector2f normal = new Vector2f(-dy, dx);

        // Left/right offsets
        Vector2f left = new Vector2f(loc.x + normal.x * halfWidth,
                loc.y + normal.y * halfWidth);

        Vector2f right = new Vector2f(loc.x - normal.x * halfWidth,
                loc.y - normal.y * halfWidth);
        float beamWidth = proj.getProjectileSpec().getWidth();
        float particleCount = beamWidth * PARTICLE_SPAWN_WIDTH_MULT * MathUtils.getDistance(left, right) * Global.getCombatEngine().getElapsedInLastFrame() * PARTICLE_DENSITY * 1f;

        if (Math.random() < 1f) {
            for (int i = 0; i < particleCount; i++) {
                //First, get a random point on the beam's line
                Vector2f spawnPoint = MathUtils.getRandomPointOnLine(left, right);
                spawnPoint = MathUtils.getRandomPointInCircle(spawnPoint, halfWidth);

                //If this point is too far off-screen, we go on to the next particle instead
                if (!Global.getCombatEngine().getViewport().isNearViewport(spawnPoint, PARTICLE_SIZE_MAX * 3f)) {
                    continue;
                }
                Vector2f velocity = new Vector2f(-proj.getVelocity().x / 2 * PARTICLE_INERTIA_MULT, -proj.getVelocity().y / 2 * PARTICLE_INERTIA_MULT);
                velocity = MathUtils.getRandomPointInCircle(velocity, PARTICLE_DRIFT);
                //And finally spawn the particle
                Global.getCombatEngine().addSmoothParticle(spawnPoint, velocity, MathUtils.getRandomNumberInRange(PARTICLE_SIZE_MIN, PARTICLE_SIZE_MAX), 1f,
                        MathUtils.getRandomNumberInRange(PARTICLE_DURATION_MIN, PARTICLE_DURATION_MAX), new Color(180, 40, 20, 150));
            }
            for (int i = 0; i < particleCount; i++) {
                Vector2f spawnPoint = MathUtils.getRandomPointOnLine(left, right);
                spawnPoint = MathUtils.getRandomPointInCircle(spawnPoint, halfWidth);
                Global.getCombatEngine().spawnEmpArcVisual(spawnPoint, null, proj.getLocation(), null, 15f, new Color (0,111,50,150), new Color(0, 250, 150, 155));                 
            }
        }
    }

}
