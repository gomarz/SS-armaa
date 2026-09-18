package data.scripts.weapons.harper;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import java.awt.Color;
import java.io.IOException;
import org.lazywizard.lazylib.FastTrig;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicRender;

public class armaa_harperLungePlume {

    // ---- lunge plume ----
    private static final String PLUME_PATH = "graphics/armaa/weapons/killme/armaa_emthrust_";
    private static final int PLUME_FRAMES = 6;
    /**
     * Sprite is authored pointing up, so it needs -90 to lie along an angle.
     */
    private static final float PLUME_SPRITE_ROT = -90f;
    /**
     * How far behind the fire point the flame root sits.
     */
    private static final float PLUME_NOZZLE_BACK = 12f;
    private static final Vector2f PLUME_BASE_SIZE = new Vector2f(22f, 90f);
    private static final float PLUME_FRAME_TIME = 0.05f;

    private static SpriteAPI[] plumeSprites = null;
    private static final float PLUME_RISE = 6f;
    private static final float PLUME_FALL = 1.5f;
    private float plumeLevel = 0f;
    private float plumeFrameTimer = 0f;
    private int plumeFrame = 0;
    private final float plumeWobbleOffset = (float) (Math.random() * Math.PI * 2f);

    private armaa_harperAnimEffect anim;

    private static SpriteAPI[] plumeSprites() {
        if (plumeSprites != null) {
            return plumeSprites;
        }
        SpriteAPI[] loaded = new SpriteAPI[PLUME_FRAMES];
        for (int i = 0; i < PLUME_FRAMES; i++) {
            String path = PLUME_PATH + String.format("%02d", i) + ".png";
            try {
                Global.getSettings().loadTexture(path);
                loaded[i] = Global.getSettings().getSprite(path);
            } catch (IOException e) {
                Global.getLogger(armaa_harperLanceEffect.class)
                        .error("lance plume frame missing: " + path, e);
                return null;
            }
        }
        plumeSprites = loaded;
        return plumeSprites;
    }

    /**
     * Arm displacement this frame, read from the animation script that owns it.
     * Zero when the mech has no animation plugin, which just leaves the nozzle
     * pinned to the fire point.
     */
    private float armOffset(ShipAPI ship) {
        if (anim == null) {
            Object o = Global.getCombatEngine().getCustomData()
                    .get(armaa_harperAnimEffect.customDataKey(ship));
            if (o instanceof armaa_harperAnimEffect) {
                anim = (armaa_harperAnimEffect) o;
            }
        }
        return anim != null ? anim.getArmOffset() : 0f;
    }

    /**
     * Draws the lunge plume for this frame. Pass 0 to shut it off - the level
     * is smoothed, so it fades rather than popping, and returns early once
     * dark. Length tracks forward speed; width and colour track the drive
     * level.
     */
    public void renderLungePlume(float amount, ShipAPI ship, float forwardSpeed, WeaponAPI weapon, float drive) {

        float rate = (drive > plumeLevel) ? PLUME_RISE : PLUME_FALL;
        plumeLevel += (drive - plumeLevel) * Math.min(1f, rate * amount);
        if (plumeLevel <= 0.02f) {
            plumeLevel = 0f;
            return;
        }

        SpriteAPI[] frames = plumeSprites();
        if (frames == null) {
            return;
        }

        float axis = weapon.getCurrAngle();

        // Follow the arm. The wind-back and punch translate the arm SPRITE via
        // setCenterY, which leaves the weapon's fire point exactly where it
        // was - so a nozzle anchored to the fire point alone hangs in midair
        // while the arm slides out from under it. armOffset is positive
        // backward and axis + 180 is backward, so it adds straight onto the
        // nozzle setback.
        float armOffset = armOffset(ship);
        Vector2f root = MathUtils.getPointOnCircumference(
                weapon.getFirePoint(0), PLUME_NOZZLE_BACK + armOffset, axis + 180f);
        if (!MagicRender.screenCheck(0.25f, root)) {
            return;
        }

        // 20fps frame churn - rolling a new frame every render frame just strobes
        plumeFrameTimer += amount;
        if (plumeFrameTimer >= PLUME_FRAME_TIME) {
            plumeFrameTimer = 0f;
            plumeFrame = MathUtils.getRandomNumberInRange(0, PLUME_FRAMES - 1);
        }

        float speedRatio = Math.max(0f, forwardSpeed / Math.max(1f, ship.getMaxSpeed()));
        float stretch = 0.45f + 0.85f * Math.min(1.6f, speedRatio);
        float wobble = 1f + 0.07f * (float) FastTrig.cos(
                ship.getFullTimeDeployed() * 14f + plumeWobbleOffset);

        float length = PLUME_BASE_SIZE.y * plumeLevel * stretch * wobble + 8f;
        float width = PLUME_BASE_SIZE.x * plumeLevel + 4f;
        if (length < 4f) {
            return;
        }

        Vector2f centre = root;
        float spriteAngle = axis + 180f + PLUME_SPRITE_ROT;

        int alpha = 255;
        MagicRender.singleframe(frames[plumeFrame], centre,
                new Vector2f(width, length), spriteAngle+wobble,
                new Color(255, 170 + (int) (60f * plumeLevel), 90 + (int) (90f * plumeLevel), alpha),
                true, CombatEngineLayers.BELOW_SHIPS_LAYER);

        if (MathUtils.getRandomNumberInRange(0f, 1f) < plumeLevel) {
            float spread = MathUtils.getRandomNumberInRange(-12f, 12f);
            float speed = MathUtils.getRandomNumberInRange(60f, 200f) * plumeLevel;
            Vector2f vel = MathUtils.getPointOnCircumference(null, speed, axis + 180f + spread);
            Vector2f.add(vel, (Vector2f) new Vector2f(ship.getVelocity()).scale(0.7f), vel);

            Global.getCombatEngine().addNebulaSmokeParticle(
                    MathUtils.getPointOnCircumference(root, length * 0.55f, axis + 180f),
                    vel,
                    14f + 10f * plumeLevel, // size
                    1.6f, // endSizeMult - growth over its life
                    0.25f, // rampUpFraction
                    0.45f, // fullBrightnessFraction
                    0.55f, // totalDuration
                    new Color(90, 70, 60, 110));
        }
    }
}