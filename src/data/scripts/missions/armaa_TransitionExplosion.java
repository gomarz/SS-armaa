package data.scripts.missions;
 
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import org.lwjgl.util.vector.Vector2f;
 
import java.awt.Color;
import java.util.Random;
 
/**
 * Usage:
 *   armaa_TransitionExplosion.spawn(retreatPos);
 */
public class armaa_TransitionExplosion {
 

 
    private static final float CORE_SIZE     = 450f;
    private static final float CORE_DURATION = 1.8f;
 
    // Secondary explosion ring
    private static final int   SECONDARY_COUNT    = 14;
    private static final float SECONDARY_MIN_DIST = 80f;
    private static final float SECONDARY_MAX_DIST = 350f;
    private static final float SECONDARY_MIN_SIZE = 80f;
    private static final float SECONDARY_MAX_SIZE = 280f;
    private static final float SECONDARY_MAX_DELAY = 0.25f;
 
    // Tertiary small pops outermost ring
    private static final int   TERTIARY_COUNT    = 20;
    private static final float TERTIARY_MIN_DIST = 200f;
    private static final float TERTIARY_MAX_DIST = 600f;
    private static final float TERTIARY_MIN_SIZE = 30f;
    private static final float TERTIARY_MAX_SIZE = 120f;
    private static final float TERTIARY_MAX_DELAY = 0.5f;
 
    // Sparks
    private static final int   SPARK_COUNT       = 80;
    private static final float SPARK_MIN_SPEED   = 150f;
    private static final float SPARK_MAX_SPEED   = 600f;
    private static final float SPARK_MIN_SIZE    = 4f;
    private static final float SPARK_MAX_SIZE    = 12f;
    private static final float SPARK_DURATION    = 1.2f;
 
    // Embers  slower, longer-lived
    private static final int   EMBER_COUNT       = 40;
    private static final float EMBER_MIN_SPEED   = 50f;
    private static final float EMBER_MAX_SPEED   = 250f;
    private static final float EMBER_MIN_SIZE    = 6f;
    private static final float EMBER_MAX_SIZE    = 18f;
    private static final float EMBER_DURATION    = 2.5f;
 
    // Smoke
    private static final int   SMOKE_COUNT       = 30;
    private static final float SMOKE_MIN_SPEED   = 20f;
    private static final float SMOKE_MAX_SPEED   = 120f;
    private static final float SMOKE_MIN_SIZE    = 60f;
    private static final float SMOKE_MAX_SIZE    = 200f;
    private static final float SMOKE_DURATION    = 3.5f;
 
    // -----------------------------------------------------------------------
    // Colors
    // -----------------------------------------------------------------------
    private static final Color COLOR_CORE       = new Color(255, 220, 120, 255);
    private static final Color COLOR_TERTIARY   = new Color(220, 80,  20,  255);
 
    /**
     * Spawns the full layered explosion at the given world position.
     *
     * @param origin World position of the explosion center (boss retreat pos).
     */
    public static void spawn(Vector2f origin) {
        CombatEngineAPI engine = Global.getCombatEngine();
        Random rand = new Random();
        Global.getSoundPlayer().playSound("explosion_from_damage", 1f, 1f, origin, origin);
        spawnCore(engine, origin);
        spawnSecondaryRing(engine, origin, rand);
        spawnTertiaryRing(engine, origin, rand);
        spawnSparks(engine, origin, rand);
        spawnEmbers(engine, origin, rand);
        spawnSmoke(engine, origin, rand);
    }
 
    // -----------------------------------------------------------------------
    // Layers
    // -----------------------------------------------------------------------
 
    private static void spawnCore(CombatEngineAPI engine, Vector2f origin) {
        engine.spawnExplosion(
                new Vector2f(origin),
                new Vector2f(0f, 0f),
                COLOR_CORE,
                CORE_SIZE,
                CORE_DURATION
        );
    }
 
    private static void spawnSecondaryRing(CombatEngineAPI engine, Vector2f origin, Random rand) {
        for (int i = 0; i < SECONDARY_COUNT; i++) {
            float angle = (360f / SECONDARY_COUNT) * i + rand.nextFloat() * (360f / SECONDARY_COUNT);
            float dist  = SECONDARY_MIN_DIST + rand.nextFloat() * (SECONDARY_MAX_DIST - SECONDARY_MIN_DIST);
            float rad   = (float) Math.toRadians(angle);
 
            Vector2f pos = new Vector2f(
                    origin.x + (float) Math.cos(rad) * dist,
                    origin.y + (float) Math.sin(rad) * dist
            );
            Vector2f vel = new Vector2f(
                    (float) Math.cos(rad) * (60f + rand.nextFloat() * 100f),
                    (float) Math.sin(rad) * (60f + rand.nextFloat() * 100f)
            );
 
            float size     = SECONDARY_MIN_SIZE + rand.nextFloat() * (SECONDARY_MAX_SIZE - SECONDARY_MIN_SIZE);
            float duration = 0.6f + rand.nextFloat() * 0.8f;
            float delay    = rand.nextFloat() * SECONDARY_MAX_DELAY;
 
            // Vary the color slightly between orange and red
            Color color = new Color(
                    255,
                    80 + rand.nextInt(100),
                    10 + rand.nextInt(40),
                    255
            );
 
            // Stagger via a delayed particle instead of direct explosion for variation
            engine.addHitParticle(pos, vel, size * 0.3f, 1f, delay + duration, color);
            engine.spawnExplosion(pos, vel, color, size, duration);
        }
    }
 
    private static void spawnTertiaryRing(CombatEngineAPI engine, Vector2f origin, Random rand) {
        for (int i = 0; i < TERTIARY_COUNT; i++) {
            float angle = rand.nextFloat() * 360f;
            float dist  = TERTIARY_MIN_DIST + rand.nextFloat() * (TERTIARY_MAX_DIST - TERTIARY_MIN_DIST);
            float rad   = (float) Math.toRadians(angle);
 
            Vector2f pos = new Vector2f(
                    origin.x + (float) Math.cos(rad) * dist,
                    origin.y + (float) Math.sin(rad) * dist
            );
            Vector2f vel = new Vector2f(
                    (float) Math.cos(rad) * (30f + rand.nextFloat() * 80f),
                    (float) Math.sin(rad) * (30f + rand.nextFloat() * 80f)
            );
 
            float size     = TERTIARY_MIN_SIZE + rand.nextFloat() * (TERTIARY_MAX_SIZE - TERTIARY_MIN_SIZE);
            float duration = 0.3f + rand.nextFloat() * 0.5f;
 
            engine.spawnExplosion(pos, vel, COLOR_TERTIARY, size, duration);
        }
    }
 
    private static void spawnSparks(CombatEngineAPI engine, Vector2f origin, Random rand) {
        for (int i = 0; i < SPARK_COUNT; i++) {
            float angle = rand.nextFloat() * 360f;
            float speed = SPARK_MIN_SPEED + rand.nextFloat() * (SPARK_MAX_SPEED - SPARK_MIN_SPEED);
            float rad   = (float) Math.toRadians(angle);
 
            Vector2f vel = new Vector2f(
                    (float) Math.cos(rad) * speed,
                    (float) Math.sin(rad) * speed
            );
 
            float size = SPARK_MIN_SIZE + rand.nextFloat() * (SPARK_MAX_SIZE - SPARK_MIN_SIZE);
 
            // Slight color variation
            Color color = new Color(
                    255,
                    200 + rand.nextInt(55),
                    50 + rand.nextInt(150),
                    255
            );
 
            engine.addHitParticle(
                    new Vector2f(origin),
                    vel,
                    size,
                    1f,
                    SPARK_DURATION + rand.nextFloat() * 0.5f,
                    color
            );
        }
    }
 
    private static void spawnEmbers(CombatEngineAPI engine, Vector2f origin, Random rand) {
        for (int i = 0; i < EMBER_COUNT; i++) {
            float angle = rand.nextFloat() * 360f;
            float speed = EMBER_MIN_SPEED + rand.nextFloat() * (EMBER_MAX_SPEED - EMBER_MIN_SPEED);
            float rad   = (float) Math.toRadians(angle);
 
            Vector2f vel = new Vector2f(
                    (float) Math.cos(rad) * speed,
                    (float) Math.sin(rad) * speed
            );
 
            float size = EMBER_MIN_SIZE + rand.nextFloat() * (EMBER_MAX_SIZE - EMBER_MIN_SIZE);
 
            Color color = new Color(
                    220 + rand.nextInt(35),
                    60 + rand.nextInt(80),
                    10 + rand.nextInt(30),
                    180 + rand.nextInt(75)
            );
 
            engine.addHitParticle(
                    new Vector2f(origin),
                    vel,
                    size,
                    0.8f,
                    EMBER_DURATION + rand.nextFloat() * 1.0f,
                    color
            );
        }
    }
 
    private static void spawnSmoke(CombatEngineAPI engine, Vector2f origin, Random rand) {
        for (int i = 0; i < SMOKE_COUNT; i++) {
            float angle = rand.nextFloat() * 360f;
            float speed = SMOKE_MIN_SPEED + rand.nextFloat() * (SMOKE_MAX_SPEED - SMOKE_MIN_SPEED);
            float rad   = (float) Math.toRadians(angle);
 
            Vector2f vel = new Vector2f(
                    (float) Math.cos(rad) * speed,
                    (float) Math.sin(rad) * speed
            );
 
            float size = SMOKE_MIN_SIZE + rand.nextFloat() * (SMOKE_MAX_SIZE - SMOKE_MIN_SIZE);
 
            // Vary smoke from dark grey to slightly brownish
            int grey = 100 + rand.nextInt(40);
            Color color = new Color(
                    grey + rand.nextInt(20),
                    grey,
                    grey - rand.nextInt(10),
                    100 + rand.nextInt(60)
            );
 
            engine.addSmokeParticle(
                    new Vector2f(origin),
                    vel,
                    size,
                    0.6f + rand.nextFloat() * 0.3f,
                    SMOKE_DURATION + rand.nextFloat() * 1.5f,
                    color
            );
        }
    }
}