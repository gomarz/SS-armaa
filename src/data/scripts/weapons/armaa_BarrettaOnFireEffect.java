package data.scripts.weapons;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.OnFireEffectPlugin;
import com.fs.starfarer.api.combat.WeaponAPI;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Author: Nia Tahl
// So you didn't want to add weapons to your weapon mod.. so you just want to give it to us?
// Okay!

public class  armaa_BarrettaOnFireEffect implements EveryFrameWeaponEffectPlugin,OnFireEffectPlugin {

    private static final List<String> USED_IDS = new ArrayList<>();

    static {
        USED_IDS.add("FLASH_ID_1");
        USED_IDS.add("FLASH_ID_2");
        USED_IDS.add("FLASH_ID_3");
        USED_IDS.add("FLASH_ID_4");
        USED_IDS.add("FLASH_ID_5");
        USED_IDS.add("FLASH_ID_6");
        USED_IDS.add("BRIGHT_L");
        USED_IDS.add("BRIGHT_R");
        USED_IDS.add("BRIGHT_C");
    }

    //The amount of particles spawned immediately when the weapon reaches full charge level
    //  -For projectile weapons, this is when the projectile is actually fired
    //  -For beam weapons, this is when the beam has reached maximum brightness
    private static final Map<String, Integer> ON_SHOT_PARTICLE_COUNT = new HashMap<>();

    static {
        ON_SHOT_PARTICLE_COUNT.put("default", 20);
        ON_SHOT_PARTICLE_COUNT.put("FLASH_ID_3", 15);
        ON_SHOT_PARTICLE_COUNT.put("FLASH_ID_4", 15);
        ON_SHOT_PARTICLE_COUNT.put("FLASH_ID_6", 34);
        ON_SHOT_PARTICLE_COUNT.put("BRIGHT_L", 5);
        ON_SHOT_PARTICLE_COUNT.put("BRIGHT_R", 5);
        ON_SHOT_PARTICLE_COUNT.put("BRIGHT_C", 10);
    }

    //If this is set to true, the particles spawn with regard to *barrel*, not *center*. Only works for ALTERNATING barrel types on weapons: for LINKED barrels you
    //  should instead set up their coordinates manually with PARTICLE_SPAWN_POINT_TURRET and PARTICLE_SPAWN_POINT_HARDPOINT
    private static final Map<String, Boolean> SPAWN_POINT_ANCHOR_ALTERNATION = new HashMap<>();
    static {
        SPAWN_POINT_ANCHOR_ALTERNATION.put("default", true);
    }

    //The position the particles are spawned (or at least where their arc originates when using offsets) compared to their weapon's center [or shot offset, see
    //SPAWN_POINT_ANCHOR_ALTERNATION above], if the weapon is a turret (or HIDDEN)
    private static final Map<String, Vector2f> PARTICLE_SPAWN_POINT_TURRET = new HashMap<>();

    static {
        PARTICLE_SPAWN_POINT_TURRET.put("default", new Vector2f(3f, -8f));
        PARTICLE_SPAWN_POINT_TURRET.put("FLASH_ID_2", new Vector2f(-3f, -8f));
        PARTICLE_SPAWN_POINT_TURRET.put("FLASH_ID_3", new Vector2f(3f, -8f));
        PARTICLE_SPAWN_POINT_TURRET.put("FLASH_ID_4", new Vector2f(-3f, -8f));
        PARTICLE_SPAWN_POINT_TURRET.put("FLASH_ID_5", new Vector2f(0f, -4f));
        PARTICLE_SPAWN_POINT_TURRET.put("FLASH_ID_6", new Vector2f(0f, -4f));
        PARTICLE_SPAWN_POINT_TURRET.put("BRIGHT_C", new Vector2f(0f, -4f));
        PARTICLE_SPAWN_POINT_TURRET.put("BRIGHT_L", new Vector2f(-3f, -8f));
        PARTICLE_SPAWN_POINT_TURRET.put("BRIGHT_R", new Vector2f(-3f, -8f));
    }

    //The position the particles are spawned (or at least where their arc originates when using offsets) compared to their weapon's center [or shot offset, see
    //SPAWN_POINT_ANCHOR_ALTERNATION above], if the weapon is a hardpoint
    private static final Map<String, Vector2f> PARTICLE_SPAWN_POINT_HARDPOINT = new HashMap<>();

    static {
        PARTICLE_SPAWN_POINT_HARDPOINT.put("default", new Vector2f(3f, -8f));
        PARTICLE_SPAWN_POINT_HARDPOINT.put("FLASH_ID_2", new Vector2f(-3f, -8f));
        PARTICLE_SPAWN_POINT_HARDPOINT.put("FLASH_ID_3", new Vector2f(3f, -8f));
        PARTICLE_SPAWN_POINT_HARDPOINT.put("FLASH_ID_4", new Vector2f(-3f, -8f));
        PARTICLE_SPAWN_POINT_HARDPOINT.put("FLASH_ID_5", new Vector2f(0f, -4f));
        PARTICLE_SPAWN_POINT_HARDPOINT.put("FLASH_ID_6", new Vector2f(0f, -4f));
        PARTICLE_SPAWN_POINT_HARDPOINT.put("BRIGHT_C", new Vector2f(0f, -4f));
        PARTICLE_SPAWN_POINT_HARDPOINT.put("BRIGHT_L", new Vector2f(-3f, -8f));
        PARTICLE_SPAWN_POINT_HARDPOINT.put("BRIGHT_R", new Vector2f(-3f, -8f));
    }

    //Which kind of particle is spawned (valid values are "SMOOTH", "BRIGHT" and "SMOKE")
    private static final Map<String, String> PARTICLE_TYPE = new HashMap<>();

    static {
        PARTICLE_TYPE.put("default", "NEBULA");
        PARTICLE_TYPE.put("BRIGHT_L", "BRIGHT");
        PARTICLE_TYPE.put("BRIGHT_R", "BRIGHT");
        PARTICLE_TYPE.put("BRIGHT_C", "BRIGHT");
    }

    //What color does the particles have?
    private static final Map<String, Color> PARTICLE_COLOR = new HashMap<>();

    static {
        PARTICLE_COLOR.put("default", new Color(110, 100, 100, 100));
        PARTICLE_COLOR.put("BRIGHT_L", new Color(255,150,80, 125));
        PARTICLE_COLOR.put("BRIGHT_R", new Color(255,150,80, 125));
        PARTICLE_COLOR.put("BRIGHT_C", new Color(255,150,80, 125));
    }

    //What's the smallest size the particles can have?
    private static final Map<String, Float> PARTICLE_SIZE_MIN = new HashMap<>();

    static {
        PARTICLE_SIZE_MIN.put("default", 10f);
        PARTICLE_SIZE_MIN.put("FLASH_ID_5", 15f);
        PARTICLE_SIZE_MIN.put("FLASH_ID_6", 15f);
    }

    //What's the largest size the particles can have?
    private static final Map<String, Float> PARTICLE_SIZE_MAX = new HashMap<>();

    static {
        PARTICLE_SIZE_MAX.put("default", 25f);
        PARTICLE_SIZE_MAX.put("FLASH_ID_5", 30f);
        PARTICLE_SIZE_MAX.put("FLASH_ID_6", 30f);
    }

    //What's the lowest velocity a particle can spawn with (can be negative)?
    private static final Map<String, Float> PARTICLE_VELOCITY_MIN = new HashMap<>();

    static {
        PARTICLE_VELOCITY_MIN.put("default", 5f);
        PARTICLE_VELOCITY_MIN.put("BRIGHT_L", 0f);
        PARTICLE_VELOCITY_MIN.put("BRIGHT_R", 0f);
        PARTICLE_VELOCITY_MIN.put("BRIGHT_C", 0f);
    }

    //What's the highest velocity a particle can spawn with (can be negative)?
    private static final Map<String, Float> PARTICLE_VELOCITY_MAX = new HashMap<>();

    static {
        PARTICLE_VELOCITY_MAX.put("default", 50f);
        PARTICLE_VELOCITY_MAX.put("FLASH_ID_3", 30f);
        PARTICLE_VELOCITY_MAX.put("FLASH_ID_4", 30f);
        PARTICLE_VELOCITY_MAX.put("FLASH_ID_5", 30f);
        PARTICLE_VELOCITY_MAX.put("FLASH_ID_6", 50f);
        PARTICLE_VELOCITY_MAX.put("BRIGHT_L", 10f);
        PARTICLE_VELOCITY_MAX.put("BRIGHT_R", 10f);
        PARTICLE_VELOCITY_MAX.put("BRIGHT_C", 10f);
    }

    //The shortest duration a particle will last before completely fading away
    private static final Map<String, Float> PARTICLE_DURATION_MIN = new HashMap<>();

    static {
        PARTICLE_DURATION_MIN.put("default", 0.8f);
        PARTICLE_DURATION_MIN.put("BRIGHT_L", 0.2f);
        PARTICLE_DURATION_MIN.put("BRIGHT_R", 0.2f);
        PARTICLE_DURATION_MIN.put("BRIGHT_C", 0.2f);
    }

    //The longest duration a particle will last before completely fading away
    private static final Map<String, Float> PARTICLE_DURATION_MAX = new HashMap<>();

    static {
        PARTICLE_DURATION_MAX.put("default", 1.6f);
        PARTICLE_DURATION_MAX.put("BRIGHT_L", 0.4f);
        PARTICLE_DURATION_MAX.put("BRIGHT_R", 0.4f);
        PARTICLE_DURATION_MAX.put("BRIGHT_C", 0.4f);
    }

    //The shortest along their velocity vector any individual particle is allowed to spawn (can be negative to spawn behind their origin point)
    private static final Map<String, Float> PARTICLE_OFFSET_MIN = new HashMap<>();

    static {
        PARTICLE_OFFSET_MIN.put("default", 5f);
    }

    //The furthest along their velocity vector any individual particle is allowed to spawn (can be negative to spawn behind their origin point)
    private static final Map<String, Float> PARTICLE_OFFSET_MAX = new HashMap<>();

    static {
        PARTICLE_OFFSET_MAX.put("default", 10f);
        PARTICLE_OFFSET_MAX.put("FLASH_ID_5", 10f);
        PARTICLE_OFFSET_MAX.put("FLASH_ID_6", 60f);
        PARTICLE_OFFSET_MAX.put("BRIGHT_C", 30f);
    }

    //The width of the "arc" the particles spawn in; affects both offset and velocity. 360f = full circle, 0f = straight line
    private static final Map<String, Float> PARTICLE_ARC = new HashMap<>();

    static {
        PARTICLE_ARC.put("default", 10f);
        PARTICLE_ARC.put("FLASH_ID_3", 80f);
        PARTICLE_ARC.put("FLASH_ID_4", 80f);
        PARTICLE_ARC.put("FLASH_ID_5", 60f);
        PARTICLE_ARC.put("BRIGHT_L", 0f);
        PARTICLE_ARC.put("BRIGHT_R", 0f);
        PARTICLE_ARC.put("BRIGHT_C", 0f);
    }

    //The offset of the "arc" the particles spawn in, compared to the weapon's forward facing.
    //  For example: 90f = the center of the arc is 90 degrees clockwise around the weapon, 0f = the same arc center as the weapon's facing.
    private static final Map<String, Float> PARTICLE_ARC_FACING = new HashMap<>();

    static {
        PARTICLE_ARC_FACING.put("default", 90f);
        PARTICLE_ARC_FACING.put("FLASH_ID_2", -90f);
        PARTICLE_ARC_FACING.put("FLASH_ID_3", 90f);
        PARTICLE_ARC_FACING.put("FLASH_ID_4", -90f);
        PARTICLE_ARC_FACING.put("FLASH_ID_5", 0f);
        PARTICLE_ARC_FACING.put("FLASH_ID_6", 0f);
        PARTICLE_ARC_FACING.put("BRIGHT_L", -90f);
        PARTICLE_ARC_FACING.put("BRIGHT_R", 90f);
        PARTICLE_ARC_FACING.put("BRIGHT_C", 0f);
    }

    //How far away from the screen's edge the particles are allowed to spawn. Lower values mean better performance, but
    //too low values will cause pop-in of particles. Generally, the longer the particle's lifetime, the higher this
    //value should be
    private static final Map<String, Float> PARTICLE_SCREENSPACE_CULL_DISTANCE = new HashMap<>();

    static {
        PARTICLE_SCREENSPACE_CULL_DISTANCE.put("default", 600f);
    }


    //These ones are used in-script, so don't touch them!
    private int currentBarrel = 0;

    @Override
    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {

        engine.spawnExplosion(projectile.getLocation(), weapon.getShip().getVelocity(), new Color(255,150,80,10), 60f, 0.1f);

        //We go through each of our particle systems and handle their particle spawning
        for (String ID : USED_IDS) {
            //Screenspace check: simplified but should do the trick 99% of the time
            float screenspaceCullingDistance = PARTICLE_SCREENSPACE_CULL_DISTANCE.get("default");
            if (PARTICLE_SCREENSPACE_CULL_DISTANCE.containsKey(ID)) {
                screenspaceCullingDistance = PARTICLE_SCREENSPACE_CULL_DISTANCE.get(ID);
            }
            if (!engine.getViewport().isNearViewport(weapon.getLocation(), screenspaceCullingDistance)) {
                continue;
            }

            boolean spawnPointAnchorAlternation = SPAWN_POINT_ANCHOR_ALTERNATION.get("default");
            if (SPAWN_POINT_ANCHOR_ALTERNATION.containsKey(ID)) { spawnPointAnchorAlternation = SPAWN_POINT_ANCHOR_ALTERNATION.get(ID); }

            //Here, we only store one value, depending on if we're a hardpoint or not
            Vector2f particleSpawnPoint = PARTICLE_SPAWN_POINT_TURRET.get("default");
            if (weapon.getSlot().isHardpoint()) {
                particleSpawnPoint = PARTICLE_SPAWN_POINT_HARDPOINT.get("default");
                if (PARTICLE_SPAWN_POINT_HARDPOINT.containsKey(ID)) {
                    particleSpawnPoint = PARTICLE_SPAWN_POINT_HARDPOINT.get(ID);
                }
            } else {
                if (PARTICLE_SPAWN_POINT_TURRET.containsKey(ID)) {
                    particleSpawnPoint = PARTICLE_SPAWN_POINT_TURRET.get(ID);
                }
            }

            String particleType = PARTICLE_TYPE.get("default");
            if (PARTICLE_TYPE.containsKey(ID)) {
                particleType = PARTICLE_TYPE.get(ID);
            }

            Color particleColor = PARTICLE_COLOR.get("default");
            if (PARTICLE_COLOR.containsKey(ID)) {
                particleColor = PARTICLE_COLOR.get(ID);
            }

            float particleSizeMin = PARTICLE_SIZE_MIN.get("default");
            if (PARTICLE_SIZE_MIN.containsKey(ID)) {
                particleSizeMin = PARTICLE_SIZE_MIN.get(ID);
            }
            float particleSizeMax = PARTICLE_SIZE_MAX.get("default");
            if (PARTICLE_SIZE_MAX.containsKey(ID)) {
                particleSizeMax = PARTICLE_SIZE_MAX.get(ID);
            }

            float particleVelocityMin = PARTICLE_VELOCITY_MIN.get("default");
            if (PARTICLE_VELOCITY_MIN.containsKey(ID)) {
                particleVelocityMin = PARTICLE_VELOCITY_MIN.get(ID);
            }
            float particleVelocityMax = PARTICLE_VELOCITY_MAX.get("default");
            if (PARTICLE_VELOCITY_MAX.containsKey(ID)) {
                particleVelocityMax = PARTICLE_VELOCITY_MAX.get(ID);
            }

            float particleDurationMin = PARTICLE_DURATION_MIN.get("default");
            if (PARTICLE_DURATION_MIN.containsKey(ID)) {
                particleDurationMin = PARTICLE_DURATION_MIN.get(ID);
            }
            float particleDurationMax = PARTICLE_DURATION_MAX.get("default");
            if (PARTICLE_DURATION_MAX.containsKey(ID)) {
                particleDurationMax = PARTICLE_DURATION_MAX.get(ID);
            }

            float particleOffsetMin = PARTICLE_OFFSET_MIN.get("default");
            if (PARTICLE_OFFSET_MIN.containsKey(ID)) {
                particleOffsetMin = PARTICLE_OFFSET_MIN.get(ID);
            }
            float particleOffsetMax = PARTICLE_OFFSET_MAX.get("default");
            if (PARTICLE_OFFSET_MAX.containsKey(ID)) {
                particleOffsetMax = PARTICLE_OFFSET_MAX.get(ID);
            }

            float particleArc = PARTICLE_ARC.get("default");
            if (PARTICLE_ARC.containsKey(ID)) {
                particleArc = PARTICLE_ARC.get(ID);
            }
            float particleArcFacing = PARTICLE_ARC_FACING.get("default");
            if (PARTICLE_ARC_FACING.containsKey(ID)) {
                particleArcFacing = PARTICLE_ARC_FACING.get(ID);
            }
            //---------------------------------------END OF DECLARATIONS-----------------------------------------

            //Count spawned particles: only trigger if the spawned particles are more than 0
            float particleCount = ON_SHOT_PARTICLE_COUNT.get("default");
            if (ON_SHOT_PARTICLE_COUNT.containsKey(ID)) {
                particleCount = ON_SHOT_PARTICLE_COUNT.get(ID);
            }

            if (particleCount > 0) {
                spawnParticles(engine, weapon, spawnPointAnchorAlternation, particleCount, particleType, particleSpawnPoint, particleColor, particleSizeMin, particleSizeMax, particleVelocityMin, particleVelocityMax,
                        particleDurationMin, particleDurationMax, particleOffsetMin, particleOffsetMax, particleArc, particleArcFacing);
            }
        }

        currentBarrel++;

        //We can *technically* have different barrel counts for hardpoints, hiddens and turrets, so take that into account
        int barrelCount = weapon.getSpec().getTurretAngleOffsets().size();
        if (weapon.getSlot().isHardpoint()) {
            barrelCount = weapon.getSpec().getHardpointAngleOffsets().size();
        } else if (weapon.getSlot().isHidden()) {
            barrelCount = weapon.getSpec().getHiddenAngleOffsets().size();
        }

        if (currentBarrel >= barrelCount) {
            currentBarrel = 0;
        }

    }

    //Shorthand function for actually spawning the particles
    private void spawnParticles(CombatEngineAPI engine, WeaponAPI weapon, boolean anchorAlternation, float count, String type, Vector2f spawnPoint, Color color, float sizeMin, float sizeMax,
                                float velocityMin, float velocityMax, float durationMin, float durationMax,
                                float offsetMin, float offsetMax, float arc, float arcFacing) {

        //First, ensure we take barrel position into account if we use Anchor Alternation (note that the spawn location is actually rotated 90 degrees wrong, so we invert their x and y values)
        Vector2f trueCenterLocation = new Vector2f(spawnPoint.y, spawnPoint.x);
        float trueArcFacing = arcFacing;
        if (anchorAlternation) {
            if (weapon.getSlot().isHardpoint()) {
                trueCenterLocation.x += weapon.getSpec().getHardpointFireOffsets().get(currentBarrel).x;
                trueCenterLocation.y += weapon.getSpec().getHardpointFireOffsets().get(currentBarrel).y;
                trueArcFacing += weapon.getSpec().getHardpointAngleOffsets().get(currentBarrel);
            } else if (weapon.getSlot().isTurret()) {
                trueCenterLocation.x += weapon.getSpec().getTurretFireOffsets().get(currentBarrel).x;
                trueCenterLocation.y += weapon.getSpec().getTurretFireOffsets().get(currentBarrel).y;
                trueArcFacing += weapon.getSpec().getTurretAngleOffsets().get(currentBarrel);
            } else {
                trueCenterLocation.x += weapon.getSpec().getHiddenFireOffsets().get(currentBarrel).x;
                trueCenterLocation.y += weapon.getSpec().getHiddenFireOffsets().get(currentBarrel).y;
                trueArcFacing += weapon.getSpec().getHiddenAngleOffsets().get(currentBarrel);
            }
        }

        //Then, we offset the true position and facing with our weapon's position and facing, while also rotating the position depending on facing
        trueArcFacing += weapon.getCurrAngle();
        trueCenterLocation = VectorUtils.rotate(trueCenterLocation, weapon.getCurrAngle(), new Vector2f(0f, 0f));
        trueCenterLocation.x += weapon.getLocation().x;
        trueCenterLocation.y += weapon.getLocation().y;

        //Then, we can finally start spawning particles
        float counter = count;
        while (Math.random() < counter) {
            //Ticks down the counter
            counter--;

            //Gets a velocity for the particle
            float arcPoint = MathUtils.getRandomNumberInRange(trueArcFacing - (arc / 2f), trueArcFacing + (arc / 2f));
            Vector2f velocity = MathUtils.getPointOnCircumference(weapon.getShip().getVelocity(), MathUtils.getRandomNumberInRange(velocityMin, velocityMax),
                    arcPoint);

            //Gets a spawn location in the cone, depending on our offsetMin/Max
            Vector2f spawnLocation = MathUtils.getPointOnCircumference(trueCenterLocation, MathUtils.getRandomNumberInRange(offsetMin, offsetMax),
                    arcPoint);

            //Gets our duration
            float duration = MathUtils.getRandomNumberInRange(durationMin, durationMax);

            //Gets our size
            float size = MathUtils.getRandomNumberInRange(sizeMin, sizeMax);

            //Color variation
            int deviation = MathUtils.getRandomNumberInRange(-10,10);
            color = new Color(
                    clamp255(color.getRed()+deviation),
                    clamp255(color.getGreen()+deviation),
                    clamp255(color.getBlue()+deviation),
                    color.getAlpha());

            //Finally, determine type of particle to actually spawn and spawns it
            switch (type) {
                case "SMOOTH":
                    engine.addSmoothParticle(spawnLocation, velocity, size, 1f, duration, color);
                    break;
                case "SMOKE":
                    engine.addSmokeParticle(spawnLocation, velocity, size, 1f, duration, color);
                    break;
                case "NEBULA":
                    engine.addNebulaParticle(spawnLocation,velocity,size,1.4f,0f,0.3f,duration,color);
                    break;
                default:
                    engine.addHitParticle(spawnLocation, velocity, size, 10f, duration, color);
                    break;
            }
        }
    }

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        // do nothing lmao
    }

    private static int clamp255(int x) {
        return Math.max(0, Math.min(255, x));
    }
}
