//By Nicke535, licensed under CC-BY-NC-SA 4.0 (https://creativecommons.org/licenses/by-nc-sa/4.0/)
//General script meant to be modified for each implementation. Causes a projectile to rotate mid-flight depending on several settings, simulating guidance
//HOW TO USE:
//	Copy this file where you want it and rename+adjust values
//	Find the projectile to guide using any method you want (everyframe script, weapon-mounted everyframe script, mine-spawning etc.)
//	run "engine.addPlugin(MagicGuidedProjectileScript(proj, target));" with:
//		MagicGuidedProjectileScript being replaced with your new class name
//		proj being the projectile to guide
//		target being the initial target (if any)
//	You're done!
package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.Misc;
import org.jetbrains.annotations.NotNull;
import org.lazywizard.lazylib.FastTrig;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;
import java.awt.Color;
import java.util.List;

public class armaa_BoomerangShieldGuidance extends BaseEveryFrameCombatPlugin {

    //If non-zero, the projectile will sway back-and-forth by this many degrees during its guidance (with a sway period determined by SWAY_PERIOD).
    //High values, as one might expect, give very poor tracking. Also, high values will decrease effective range (as the projectiles travel further) so be careful
    //Secondary and primary sway both run in parallel, allowing double-sine swaying if desired
    private static final float SWAY_AMOUNT_PRIMARY = 5f;
    private static final float SWAY_AMOUNT_SECONDARY = 5f;

    //Used together with SWAY_AMOUNT, determines how fast the swaying happens
    //1f means an entire sway "loop" (max sway right -> min sway -> max sway left -> min sway again) per second, 2f means 2 loops etc.
    //Projectiles start at a random position in their sway loop on spawning
    //Secondary and primary sway both run in parallel, allowing double-sine swaying if desired
    private static final float SWAY_PERIOD_PRIMARY = 0.5f;
    private static final float SWAY_PERIOD_SECONDARY = 0.5f;

    //How fast, if at all, sway falls off with the projectile's lifetime.
    //At 1f, it's a linear falloff, at 2f it's quadratic. At 0f, there is no falloff
    private static final float SWAY_FALLOFF_FACTOR = 1f;

    //Only used for the INTERCEPT targeting types: number of iterations to run for calculations.
    //At 0 it's indistinguishable from a dumbchaser, at 15 it's frankly way too high. 4-7 recommended for slow weapons, 2-3 for weapons with more firerate/lower accuracy
    private static final int INTERCEPT_ITERATIONS = 5;

    //Only used for the INTERCEPT targeting type: a factor for how good the AI judges target leading
    //At 1f it tries to shoot the "real" intercept point, while at 0f it's indistinguishable from a dumbchaser.
    private static final float INTERCEPT_ACCURACY_FACTOR = 1f;

    //Delays the activation of the script by a random amount of seconds between this MIN and MAX.
    //Note that ONE_TURN shots will still decide on target angle/point at spawn-time, not when this duration is up
    private static final float GUIDANCE_DELAY_MAX = 0f;
    private static final float GUIDANCE_DELAY_MIN = 0.1f;

    //Whether phased ships are ignored for targeting (and an already phased target counts as "lost" and procs secondary targeting)
    private static final boolean BROKEN_BY_PHASE = true;

    //Whether the projectile switches to a new target if the current one becomes an ally
    private static final boolean RETARGET_ON_SIDE_SWITCH = false;

    //---Internal script variables: don't touch!---
    private final DamagingProjectileAPI proj; //The projectile itself
    private CombatEntityAPI target; // Current target of the projectile
    private final Vector2f targetPoint; // For ONE_TURN_TARGET, actual target position. Otherwise, an offset from the target's "real" position. Not used for ONE_TURN_DUMB

    private float swayCounter1; // Counter for handling primary sway
    private float swayCounter2; // Counter for handling secondary sway
    private float lifeCounter; // Keeps track of projectile lifetime
    private final float estimateMaxLife; // How long we estimate this projectile should be alive
    private float delayCounter; // Counter for delaying targeting
    private final float actualGuidanceDelay; // The actual guidance delay for this specific projectile

    /**
     * Initializer for the guided projectile script
     *
     * @param proj The projectile to affect. proj.getWeapon() must be non-null.
     *
     * @param target The target missile/asteroid/ship for the script's guidance.
     * Can be null, if the script does not follow a target ("ONE_TURN_DUMB") or
     * to instantly activate secondary guidance mode.
     */
    public armaa_BoomerangShieldGuidance(@NotNull DamagingProjectileAPI proj, CombatEntityAPI target) 
	{
        this.proj = proj;
        this.target = target;
        swayCounter1 = MathUtils.getRandomNumberInRange(0f, 1f);
        swayCounter2 = MathUtils.getRandomNumberInRange(0f, 1f);
        lifeCounter = 0f;
        estimateMaxLife = proj.getWeapon().getRange() / new Vector2f(proj.getVelocity().x - proj.getSource().getVelocity().x, proj.getVelocity().y - proj.getSource().getVelocity().y).length();
        delayCounter = 0f;
        actualGuidanceDelay = MathUtils.getRandomNumberInRange(GUIDANCE_DELAY_MIN, GUIDANCE_DELAY_MAX);

        targetPoint = new Vector2f(Misc.ZERO);
    }

    //Main advance method
    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        //Sanity checks
        if (Global.getCombatEngine() == null) {
            return;
        }
        if (Global.getCombatEngine().isPaused()) {
            amount = 0f;
        }
        //Checks if our script should be removed from the combat engine
        if (proj == null || proj.didDamage() || proj.isFading() || !Global.getCombatEngine().isEntityInPlay(proj)) {
            Global.getCombatEngine().removePlugin(this);
            return;
        }

        //Ticks up our life counter: if we miscalculated, also top it off
        lifeCounter += amount;
        if (lifeCounter > estimateMaxLife) {
            lifeCounter = estimateMaxLife;
        }

        //Delays targeting if we have that enabled
        if (delayCounter < actualGuidanceDelay) {
            delayCounter += amount;
            return;
        }

        //Tick the sway counter up here regardless of if we need it or not: helps reduce boilerplate code
        swayCounter1 += amount * SWAY_PERIOD_PRIMARY;
        swayCounter2 += amount * SWAY_PERIOD_SECONDARY;
        float swayThisFrame = (float) Math.pow(1f - (lifeCounter / estimateMaxLife), SWAY_FALLOFF_FACTOR)
                * ((float) (FastTrig.sin(Math.PI * 2f * swayCounter1) * SWAY_AMOUNT_PRIMARY) + (float) (FastTrig.sin(Math.PI * 2f * swayCounter2) * SWAY_AMOUNT_SECONDARY));

        //Check if we need to find a new target
        if (target != null) {
            if (!Global.getCombatEngine().isEntityInPlay(target)) {
                target = null;
            }
            if (target instanceof ShipAPI) {
                if (((ShipAPI) target).isHulk() || (((ShipAPI) target).isPhased() && BROKEN_BY_PHASE) || (target.getOwner() == proj.getOwner() && RETARGET_ON_SIDE_SWITCH)) {
                    target = null;
                }
            }
        }

        //If we need to retarget, check our retarget strategy and act accordingly
        if (target == null) {
            //With no retarget plan, the script just shuts itself off
            Global.getCombatEngine().removePlugin(this);
            return;
        }

        //If we're using anything that needs a target, and our retargeting failed, just head in a straight line: no script is run
        //We use fewer calculation steps for projectiles that are very close, as they aren't needed at close distances
        int iterations = INTERCEPT_ITERATIONS;

        float facingSwayless = proj.getFacing() - swayThisFrame;
        Vector2f targetPointRotated = VectorUtils.rotate(new Vector2f(targetPoint), target.getFacing());
        float angleToHit = VectorUtils.getAngle(proj.getLocation(), Vector2f.add(getApproximateInterception(iterations), targetPointRotated, new Vector2f(Misc.ZERO)));
        float angleDiffAbsolute = Math.abs(angleToHit - facingSwayless);
        while (angleDiffAbsolute > 180f) {
            angleDiffAbsolute = Math.abs(angleDiffAbsolute - 360f);
        }

        float turnRate = 50f;
        if ("vayra_medium_boomerangshield".equals(this.proj.getProjectileSpecId())) {
            turnRate = 25f;
        }

		if(MathUtils.getDistance(proj.getLocation(), target.getLocation()) < 20f)
		{
			proj.getWeapon().setAmmo(1);
			Global.getSoundPlayer().playSound("mechmoveRev", 1f, 1f, proj.getLocation(), new Vector2f());
			
			for(int i = 0; i < 6; i++)
			{
				float size = MathUtils.getRandomNumberInRange(15 , 30);
				float x = MathUtils.getRandomNumberInRange(-44,44);
				float y = MathUtils.getRandomNumberInRange(-48,48);
				Global.getCombatEngine().addSmokeParticle(proj.getLocation(), new Vector2f(x,y), size, 0.5f, 0.5f, Color.gray);
			}				
			Global.getCombatEngine().removeEntity(proj);
            Global.getCombatEngine().removePlugin(this);
		}
        facingSwayless += Misc.getClosestTurnDirection(facingSwayless, angleToHit) * Math.min(angleDiffAbsolute, turnRate * amount);
        proj.setFacing(facingSwayless + swayThisFrame);
        proj.getVelocity().x = MathUtils.getPoint(new Vector2f(Misc.ZERO), proj.getVelocity().length(), facingSwayless + swayThisFrame).x;
        proj.getVelocity().y = MathUtils.getPoint(new Vector2f(Misc.ZERO), proj.getVelocity().length(), facingSwayless + swayThisFrame).y;

    }

    //Iterative intercept point calculation: has option for taking more or less calculation steps to trade calculation speed for accuracy
    private Vector2f getApproximateInterception(int calculationSteps) {
        Vector2f returnPoint = new Vector2f(target.getLocation());

        //Iterate a set amount of times, improving accuracy each time
        for (int i = 0; i < calculationSteps; i++) {
            //Get the distance from the current iteration point and the projectile, and calculate the approximate arrival time
            float arrivalTime = MathUtils.getDistance(proj.getLocation(), returnPoint) / proj.getVelocity().length();

            //Calculate the targeted point with this arrival time
            returnPoint.x = target.getLocation().x + (target.getVelocity().x * arrivalTime * INTERCEPT_ACCURACY_FACTOR);
            returnPoint.y = target.getLocation().y + (target.getVelocity().y * arrivalTime * INTERCEPT_ACCURACY_FACTOR);
        }

        return returnPoint;
    }
}
