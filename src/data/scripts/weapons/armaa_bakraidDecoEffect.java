package data.scripts.weapons;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.WeaponAPI;
import org.lwjgl.util.vector.Vector2f;
import java.awt.Color;
import java.util.List;
import java.util.Random;

public class armaa_bakraidDecoEffect implements EveryFrameWeaponEffectPlugin {

    private ShipAPI ship;
    private float ogX, ogY;
    private boolean leftie;
    private float sinceB = 0f;
    private float prevSinceB = 0f;

    // ---- TUNING ----
    private static final float SPEED = 1f;
    private static final float SLIDE_END = 0.6f;
    private static final float MAX_SLIDE = 14f;
    private static final float MAX_ROT = 25f;

    // ---- PARTICLE TUNING ----
    private static final float SMOKE_SPEED = 35f;
    private static final float SPARK_SPEED = 55f;
    private static final float SMOKE_SIZE = 6f;
    private static final float SPARK_SIZE = 2f;
    private static final float SMOKE_DURATION = 0.5f;
    private static final float SPARK_DURATION = 0.25f;
    private static final int SMOKE_COUNT = 5;
    private static final int SPARK_COUNT = 14;

    // ---- PARTICLE TRIGGER THRESHOLDS ----
    private static final float SLIDE_TRIGGER = 0.04f;
    private static final float ROTATE_TRIGGER = SLIDE_END + 0.04f;

    // ---- STATE ----
    private boolean moduleDisabled = false;
    private boolean slideSmokeSpawned = false;
    private boolean rotateSparksSpawned = false;
    private boolean stowSmokeSpawned = false;
    private boolean prevDeploying = false;

    private final Random rand = new Random();

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (ship == null) {
            ship = weapon.getShip();
            ogX = weapon.getSprite().getCenterX();
            ogY = weapon.getSprite().getCenterY();
            leftie = weapon.getSlot().getId().equals("E_LSHOULDER");
            return;
        }

        float delta = amount * SPEED;
        boolean deploying = ship.areAnyEnemiesInRange() || moduleDisabled;

        if (sinceB == 0f && prevSinceB > 0f) {
            slideSmokeSpawned = false;
            rotateSparksSpawned = false;
            stowSmokeSpawned = false;
        }

        prevSinceB = sinceB;

        if (deploying) {
            sinceB += delta;
        } else {
            sinceB -= delta;
        }
        sinceB = Math.max(0f, Math.min(1f, sinceB));

        float slide;
        float rot;
        if (sinceB <= SLIDE_END) {
            float t = sinceB / SLIDE_END;
            slide = MAX_SLIDE * t;
            rot = 0f;
        } else {
            slide = MAX_SLIDE;
            float t = (sinceB - SLIDE_END) / (1f - SLIDE_END);
            rot = MAX_ROT * t;
        }

        float angle = leftie ? 1f : -1f;
        weapon.setFacing(ship.getFacing() + rot * angle);
        if (!leftie) {
            weapon.getSprite().setCenterX(ogX + angle * slide);
            weapon.getSprite().setCenterY(ogY + angle * slide);
        } else {
            weapon.getSprite().setCenterX(ogX + angle * slide);
            weapon.getSprite().setCenterY(ogY - angle * slide);
        }

        // ---- PARTICLE SPAWNING ----
        if (deploying) {
            if (!slideSmokeSpawned && sinceB >= SLIDE_TRIGGER) {
                slideSmokeSpawned = true;
                for (int i = 0; i < weapon.getSpec().getTurretFireOffsets().size(); i++) {
                    spawnSmoke(engine, weapon.getFirePoint(i), angle, false);
                }
            }
            if (!rotateSparksSpawned && sinceB >= ROTATE_TRIGGER) {
                rotateSparksSpawned = true;
                for (int i = 0; i < weapon.getSpec().getTurretFireOffsets().size(); i++) {
                    spawnSparks(engine, weapon.getFirePoint(i), angle);
                }
            }
        } else {
            if (!stowSmokeSpawned && prevDeploying) {
                stowSmokeSpawned = true;
                for (int i = 0; i < weapon.getSpec().getTurretFireOffsets().size(); i++) {
                    spawnSmoke(engine, weapon.getFirePoint(i), -angle, true);
                }
            }
        }

        prevDeploying = deploying;

        // ---- MODULE SYNC ----
        // probably, this should be somewhere else
        // if anyone comes looking, they probably wont figure
        // we're controlling the module here. TODO
        List<ShipAPI> children = ship.getChildModulesCopy();
        if (children != null) {
            for (ShipAPI module : children) {
                module.ensureClonedStationSlotSpec();
                if (module.getStationSlot() != null) {
                    module.blockCommandForOneFrame(ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK);
                    module.blockCommandForOneFrame(ShipCommand.USE_SYSTEM);
                    if (ship.getFluxTracker().isOverloaded() && !module.getFluxTracker().isOverloaded()) {
                        module.getFluxTracker().beginOverloadWithTotalBaseDuration(
                                ship.getFluxTracker().getOverloadTimeRemaining());
                    }
                    if (!module.getEngineController().isFlamedOut()) {
                        //module.getEngineController().forceFlameout(true);
                    }
                    module.setCurrentCR(ship.getCurrentCR());
                    moduleDisabled = module.controlsLocked();
                }
            }
        }
    }

    // imma be real, i feel like this isnt necessary, but I guess such is the 
    // cost of vibe coding
    private Vector2f localToWorld(float localX, float localY, float shipFacingDeg) {
        double rad = Math.toRadians(shipFacingDeg);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);
        return new Vector2f(
                localX * cos - localY * sin,
                localX * sin + localY * cos
        );
    }

    private void spawnSmoke(CombatEngineAPI engine, Vector2f origin, float angle, boolean reverseColor) {
        // leftie (angle=+1) moves in +X (right), righty (angle=-1) moves in -X (left)
        // particles kick the opposite way: -angle along local X
        Vector2f shipVel = ship.getVelocity();
        float lateralKick = angle * SMOKE_SPEED; // +X for leftie, -X for righty

        Vector2f baseVel = new Vector2f(
                shipVel.x + lateralKick,
                shipVel.y
        );

        Color smokeColor = reverseColor
                ? new Color(160, 160, 180, 120)
                : new Color(200, 190, 170, 140);

        for (int i = 0; i < SMOKE_COUNT; i++) {
            float spreadX = (rand.nextFloat() - 0.5f) * 18f;
            float spreadY = (rand.nextFloat() - 0.5f) * 18f;
            Vector2f spreadVec = localToWorld(spreadX, spreadY, ship.getFacing());

            float size = SMOKE_SIZE * (0.7f + rand.nextFloat() * 0.6f);
            float dur = SMOKE_DURATION * (0.8f + rand.nextFloat() * 0.4f);

            engine.addSmokeParticle(
                    new Vector2f(origin.x, origin.y),
                    new Vector2f(baseVel.x + spreadVec.x, baseVel.y + spreadVec.y),
                    size, 0.6f, dur, smokeColor
            );
        }
    }

    private void spawnSparks(CombatEngineAPI engine, Vector2f origin, float angle) {
        Vector2f shipVel = ship.getVelocity();
        float lateralKick = angle * SPARK_SPEED; // +X for leftie, -X for righty

        Vector2f baseVel = new Vector2f(
                shipVel.x + lateralKick,
                shipVel.y
        );

        Color sparkColor = new Color(255, 210, 120, 255);

        for (int i = 0; i < SPARK_COUNT; i++) {
            float spreadX = (rand.nextFloat() - 0.5f) * 80f;
            float spreadY = (rand.nextFloat() - 0.5f) * 80f;
            Vector2f spreadVec = localToWorld(spreadX, spreadY, ship.getFacing());

            float size = SPARK_SIZE * (0.6f + rand.nextFloat() * 0.8f);
            float dur = SPARK_DURATION * (0.7f + rand.nextFloat() * 0.5f);
            // we basically have to flip angle since we want to spawn this further in their
            // respective direction
            engine.addHitParticle(
                    new Vector2f(origin.x + 10 * angle * -1, origin.y),
                    new Vector2f(baseVel.x + spreadVec.x, baseVel.y + spreadVec.y),
                    size, 1f, dur, sparkColor
            );
        }
    }
}
