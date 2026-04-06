//By Nicke535, licensed under CC-BY-NC-SA 4.0 (https://creativecommons.org/licenses/by-nc-sa/4.0/)
//General script meant to be modified for each implementation. Causes a projectile to rotate mid-flight depending on several settings, simulating guidance
//HOW TO USE:
//	Copy this file where you want it and rename+adjust values
//	Find the projectile to guide using any method you want (everyframe script, weapon-mounted everyframe script, mine-spawning etc.)
//	run "engine.addPlugin(armaa_counterShieldScript(proj, target));" with:
//		armaa_counterShieldScript being replaced with your new class name
//		proj being the projectile to guide
//		target being the initial target (if any)
//	You're done!
package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.Misc;
import org.jetbrains.annotations.NotNull;
import org.lazywizard.lazylib.CollisionUtils;
import org.lazywizard.lazylib.FastTrig;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;
import java.awt.Color;
import com.fs.starfarer.api.util.IntervalUtil;
import java.util.ArrayList;
import java.util.List;

public class armaa_counterShieldScript extends BaseEveryFrameCombatPlugin {
	//---Settings: adjust to fill the needs of your implementation---
	private static final String GUIDANCE_MODE_PRIMARY = "INTERCEPT_SWARM";
	private static final String GUIDANCE_MODE_SECONDARY = "REACQUIRE_NEAREST_PROJ";

	private static final List<String> VALID_TARGET_TYPES = new ArrayList<>();
	static {
		VALID_TARGET_TYPES.add("FIGHTER");
		VALID_TARGET_TYPES.add("FRIGATE");
		VALID_TARGET_TYPES.add("DESTROYER");
		VALID_TARGET_TYPES.add("CRUISER");
		VALID_TARGET_TYPES.add("CAPITAL");
	}

	// Reduced from 4000f — tighten reacquisition to nearby targets only
	private static final float TARGET_REACQUIRE_RANGE = 1200f;
	private static final float TARGET_REACQUIRE_ANGLE = 360f;

	private static final float TURN_RATE = 180f;

	// Homing degrades over projectile lifetime.
	// HOMING_DEGRADATION_MIN: fraction of TURN_RATE remaining at end of life (0.1 = 10% turn rate at expiry)
	// HOMING_DEGRADATION_CURVE: >1 falls off faster early, <1 stays sharp longer, 1 = linear
	private static final float HOMING_DEGRADATION_MIN   = 0.1f;
	private static final float HOMING_DEGRADATION_CURVE = 0.9f;

	private static final float SWAY_AMOUNT_PRIMARY    = 1f;
	private static final float SWAY_AMOUNT_SECONDARY  = 1f;
	private static final float SWAY_PERIOD_PRIMARY    = 1f;
	private static final float SWAY_PERIOD_SECONDARY  = 1f;
	private static final float SWAY_FALLOFF_FACTOR    = 1f;

	private static final float ONE_TURN_DUMB_INACCURACY   = 0f;
	private static final float ONE_TURN_TARGET_INACCURACY = 0f;

	private static final int   INTERCEPT_ITERATIONS      = 3;
	private static final float INTERCEPT_ACCURACY_FACTOR = 1f;

	private static final float GUIDANCE_DELAY_MAX = .05f;
	private static final float GUIDANCE_DELAY_MIN = 0f;

	private static final boolean BROKEN_BY_PHASE        = true;
	private static final boolean RETARGET_ON_SIDE_SWITCH = false;

	//---Internal script variables: don't touch!---
	private DamagingProjectileAPI proj;
	private CombatEntityAPI target;
	private Vector2f targetPoint;
	private float targetAngle;
	private float swayCounter1;
	private float swayCounter2;
	private float lifeCounter;
	private float estimateMaxLife;
	private float delayCounter;
	private Vector2f offsetVelocity;
	private Vector2f lastTargetPos;
	private float actualGuidanceDelay;

	public armaa_counterShieldScript(@NotNull DamagingProjectileAPI proj, CombatEntityAPI target) {
		this.proj = proj;
		this.target = target;
		lastTargetPos = target != null ? target.getLocation() : new Vector2f(proj.getLocation());
		swayCounter1 = MathUtils.getRandomNumberInRange(0f, 1f);
		swayCounter2 = MathUtils.getRandomNumberInRange(0f, 1f);
		lifeCounter = 0f;
		estimateMaxLife = proj.getWeapon().getRange() / new Vector2f(proj.getVelocity().x - proj.getSource().getVelocity().x, proj.getVelocity().y - proj.getSource().getVelocity().y).length();
		delayCounter = 0f;
		actualGuidanceDelay = MathUtils.getRandomNumberInRange(GUIDANCE_DELAY_MIN, GUIDANCE_DELAY_MAX);

		if (GUIDANCE_MODE_PRIMARY.equals("ONE_TURN_DUMB")) {
			targetAngle = proj.getWeapon().getCurrAngle() + MathUtils.getRandomNumberInRange(-ONE_TURN_DUMB_INACCURACY, ONE_TURN_DUMB_INACCURACY);
			offsetVelocity = proj.getSource().getVelocity();
		} else if (GUIDANCE_MODE_PRIMARY.equals("ONE_TURN_TARGET")) {
			targetPoint = MathUtils.getRandomPointInCircle(getApproximateInterception(25), ONE_TURN_TARGET_INACCURACY);
		} else if (GUIDANCE_MODE_PRIMARY.contains("SWARM") && target != null) {
			applySwarmOffset();
		} else {
			targetPoint = new Vector2f(Misc.ZERO);
		}
	}

	@Override
	public void advance(float amount, List<InputEventAPI> events) {
		if (Global.getCombatEngine() == null) return;
		if (Global.getCombatEngine().isPaused()) amount = 0f;

		if (proj == null || proj.didDamage() || proj.isFading() || !Global.getCombatEngine().isEntityInPlay(proj)) {
                    Global.getCombatEngine().spawnExplosion(proj.getLocation(), new Vector2f(), new Color(100, 200, 255, 200), 75f, 0.1f);                    
                    Global.getCombatEngine().removePlugin(this);
			return;
		}

		lifeCounter += amount;
		if (lifeCounter > estimateMaxLife) lifeCounter = estimateMaxLife;

		if (delayCounter < actualGuidanceDelay) {
			delayCounter += amount;
			return;
		}

		if (Math.random() < .05f && !Global.getCombatEngine().isPaused()) {
			Global.getCombatEngine().addHitParticle(
				proj.getLocation(),
				new Vector2f(),
				MathUtils.getRandomNumberInRange(2, 5),
				1f,
				MathUtils.getRandomNumberInRange(0.4f, 0.8f),
				new Color(0.502f, 0.70f, 0.95f, 1f));
			Global.getCombatEngine().addSwirlyNebulaParticle(proj.getLocation(), new Vector2f(0.0f, 0.0f), MathUtils.getRandomNumberInRange(10.0f, 60.0f), 1.2f, 0.15f, 0.0f, 0.5f, new Color(0.502f, 0.70f, 0.95f, 1f), false);
		}

		swayCounter1 += amount * SWAY_PERIOD_PRIMARY;
		swayCounter2 += amount * SWAY_PERIOD_SECONDARY;
		float swayThisFrame = (float) Math.pow(1f - (lifeCounter / estimateMaxLife), SWAY_FALLOFF_FACTOR) *
				((float) (FastTrig.sin(Math.PI * 2f * swayCounter1) * SWAY_AMOUNT_PRIMARY) + (float) (FastTrig.sin(Math.PI * 2f * swayCounter2) * SWAY_AMOUNT_SECONDARY));

		// Compute degraded turn rate: starts at TURN_RATE, falls toward TURN_RATE*HOMING_DEGRADATION_MIN by end of life
		float lifeFraction = lifeCounter / estimateMaxLife;
		float homingMult = 1f - (float) Math.pow(lifeFraction, HOMING_DEGRADATION_CURVE) * (1f - HOMING_DEGRADATION_MIN);
		float effectiveTurnRate = TURN_RATE * homingMult;

		if (!GUIDANCE_MODE_PRIMARY.contains("ONE_TURN")) {
			if (target != null) {
				if (!Global.getCombatEngine().isEntityInPlay(target)) target = null;
				if (target instanceof ShipAPI) {
					if (((ShipAPI) target).isHulk()
							|| (((ShipAPI) target).isPhased() && BROKEN_BY_PHASE)
							|| (target.getOwner() == proj.getOwner() && RETARGET_ON_SIDE_SWITCH)) {
						target = null;
					}
				}
			}

			if (target == null) {
				if (GUIDANCE_MODE_SECONDARY.equals("NONE")) {
					Global.getCombatEngine().removePlugin(this);
					return;
				} else if (GUIDANCE_MODE_SECONDARY.equals("DISAPPEAR")) {
					Global.getCombatEngine().removeEntity(proj);
					Global.getCombatEngine().removePlugin(this);
					return;
				} else {
					reacquireTarget();
				}
			} else {
				lastTargetPos = new Vector2f(target.getLocation());
			}
		}

		if (!GUIDANCE_MODE_PRIMARY.contains("ONE_TURN") && target == null) return;

		if (GUIDANCE_MODE_PRIMARY.equals("ONE_TURN_DUMB")) {
			float facingSwayless = proj.getFacing() - swayThisFrame;
			float angleDiffAbsolute = Math.abs(targetAngle - facingSwayless);
			while (angleDiffAbsolute > 180f) { angleDiffAbsolute = Math.abs(angleDiffAbsolute - 360f); }
			facingSwayless += Misc.getClosestTurnDirection(facingSwayless, targetAngle) * Math.min(angleDiffAbsolute, effectiveTurnRate * amount);
			Vector2f pureVelocity = new Vector2f(proj.getVelocity());
			pureVelocity.x -= offsetVelocity.x;
			pureVelocity.y -= offsetVelocity.y;
			proj.setFacing(facingSwayless + swayThisFrame);
			proj.getVelocity().x = MathUtils.getPoint(new Vector2f(Misc.ZERO), pureVelocity.length(), facingSwayless + swayThisFrame).x + offsetVelocity.x;
			proj.getVelocity().y = MathUtils.getPoint(new Vector2f(Misc.ZERO), pureVelocity.length(), facingSwayless + swayThisFrame).y + offsetVelocity.y;

		} else if (GUIDANCE_MODE_PRIMARY.equals("ONE_TURN_TARGET")) {
			float facingSwayless = proj.getFacing() - swayThisFrame;
			float angleToHit = VectorUtils.getAngle(proj.getLocation(), targetPoint);
			float angleDiffAbsolute = Math.abs(angleToHit - facingSwayless);
			while (angleDiffAbsolute > 180f) { angleDiffAbsolute = Math.abs(angleDiffAbsolute - 360f); }
			facingSwayless += Misc.getClosestTurnDirection(facingSwayless, angleToHit) * Math.min(angleDiffAbsolute, effectiveTurnRate * amount);
			proj.setFacing(facingSwayless + swayThisFrame);
			proj.getVelocity().x = MathUtils.getPoint(new Vector2f(Misc.ZERO), proj.getVelocity().length(), facingSwayless + swayThisFrame).x;
			proj.getVelocity().y = MathUtils.getPoint(new Vector2f(Misc.ZERO), proj.getVelocity().length(), facingSwayless + swayThisFrame).y;

		} else if (GUIDANCE_MODE_PRIMARY.contains("DUMBCHASER")) {
			float facingSwayless = proj.getFacing() - swayThisFrame;
			Vector2f targetPointRotated = VectorUtils.rotate(new Vector2f(targetPoint), target.getFacing());
			float angleToHit = VectorUtils.getAngle(proj.getLocation(), Vector2f.add(target.getLocation(), targetPointRotated, new Vector2f(Misc.ZERO)));
			float angleDiffAbsolute = Math.abs(angleToHit - facingSwayless);
			while (angleDiffAbsolute > 180f) { angleDiffAbsolute = Math.abs(angleDiffAbsolute - 360f); }
			facingSwayless += Misc.getClosestTurnDirection(facingSwayless, angleToHit) * Math.min(angleDiffAbsolute, effectiveTurnRate * amount);
			proj.setFacing(facingSwayless + swayThisFrame);
			proj.getVelocity().x = MathUtils.getPoint(new Vector2f(Misc.ZERO), proj.getVelocity().length(), facingSwayless + swayThisFrame).x;
			proj.getVelocity().y = MathUtils.getPoint(new Vector2f(Misc.ZERO), proj.getVelocity().length(), facingSwayless + swayThisFrame).y;

		} else if (GUIDANCE_MODE_PRIMARY.contains("INTERCEPT")) {
			int iterations = INTERCEPT_ITERATIONS;
			float facingSwayless = proj.getFacing() - swayThisFrame;
			Vector2f targetPointRotated = VectorUtils.rotate(new Vector2f(targetPoint), target.getFacing());
			float angleToHit = VectorUtils.getAngle(proj.getLocation(), Vector2f.add(getApproximateInterception(iterations), targetPointRotated, new Vector2f(Misc.ZERO)));
			float angleDiffAbsolute = Math.abs(angleToHit - facingSwayless);
			while (angleDiffAbsolute > 180f) { angleDiffAbsolute = Math.abs(angleDiffAbsolute - 360f); }
			facingSwayless += Misc.getClosestTurnDirection(facingSwayless, angleToHit) * Math.min(angleDiffAbsolute, effectiveTurnRate * amount);
			proj.setFacing(facingSwayless + swayThisFrame);
			proj.getVelocity().x = MathUtils.getPoint(new Vector2f(Misc.ZERO), proj.getVelocity().length(), facingSwayless + swayThisFrame).x;
			proj.getVelocity().y = MathUtils.getPoint(new Vector2f(Misc.ZERO), proj.getVelocity().length(), facingSwayless + swayThisFrame).y;
		}
	}

	private void reacquireTarget() {
		CombatEntityAPI newTarget = null;
		Vector2f centerOfDetection = lastTargetPos;
		if (GUIDANCE_MODE_SECONDARY.contains("_PROJ")) {
			centerOfDetection = proj.getLocation();
		}
		List<CombatEntityAPI> potentialTargets = new ArrayList<>();
		if (VALID_TARGET_TYPES.contains("ASTEROID")) {
			for (CombatEntityAPI potTarget : CombatUtils.getAsteroidsWithinRange(centerOfDetection, TARGET_REACQUIRE_RANGE)) {
				if (potTarget.getOwner() != proj.getOwner() && Math.abs(VectorUtils.getAngle(proj.getLocation(), potTarget.getLocation()) - proj.getFacing()) < TARGET_REACQUIRE_ANGLE) {
					potentialTargets.add(potTarget);
				}
			}
		}
		if (VALID_TARGET_TYPES.contains("MISSILE")) {
			for (CombatEntityAPI potTarget : CombatUtils.getMissilesWithinRange(centerOfDetection, TARGET_REACQUIRE_RANGE)) {
				if (potTarget.getOwner() != proj.getOwner() && Math.abs(VectorUtils.getAngle(proj.getLocation(), potTarget.getLocation()) - proj.getFacing()) < TARGET_REACQUIRE_ANGLE) {
					potentialTargets.add(potTarget);
				}
			}
		}
		for (ShipAPI potTarget : CombatUtils.getShipsWithinRange(centerOfDetection, TARGET_REACQUIRE_RANGE)) {
			if (potTarget.getOwner() == proj.getOwner()
					|| Math.abs(VectorUtils.getAngle(proj.getLocation(), potTarget.getLocation()) - proj.getFacing()) > TARGET_REACQUIRE_ANGLE
					|| potTarget.isHulk()) {
				continue;
			}
			if (potTarget.isPhased() && BROKEN_BY_PHASE) continue;
			if (potTarget.getHullSize().equals(ShipAPI.HullSize.FIGHTER)      && VALID_TARGET_TYPES.contains("FIGHTER"))   potentialTargets.add(potTarget);
			if (potTarget.getHullSize().equals(ShipAPI.HullSize.FRIGATE)      && VALID_TARGET_TYPES.contains("FRIGATE"))   potentialTargets.add(potTarget);
			if (potTarget.getHullSize().equals(ShipAPI.HullSize.DESTROYER)    && VALID_TARGET_TYPES.contains("DESTROYER")) potentialTargets.add(potTarget);
			if (potTarget.getHullSize().equals(ShipAPI.HullSize.CRUISER)      && VALID_TARGET_TYPES.contains("CRUISER"))   potentialTargets.add(potTarget);
			if (potTarget.getHullSize().equals(ShipAPI.HullSize.CAPITAL_SHIP) && VALID_TARGET_TYPES.contains("CAPITAL"))   potentialTargets.add(potTarget);
		}
		if (!potentialTargets.isEmpty()) {
			if (GUIDANCE_MODE_SECONDARY.contains("REACQUIRE_NEAREST")) {
				for (CombatEntityAPI potTarget : potentialTargets) {
					if (newTarget == null) {
						newTarget = potTarget;
					} else if (MathUtils.getDistance(newTarget, centerOfDetection) > MathUtils.getDistance(potTarget, centerOfDetection)) {
						newTarget = potTarget;
					}
				}
			} else if (GUIDANCE_MODE_SECONDARY.contains("REACQUIRE_RANDOM")) {
				newTarget = potentialTargets.get(MathUtils.getRandomNumberInRange(0, potentialTargets.size() - 1));
			}
			target = newTarget;
			if (GUIDANCE_MODE_PRIMARY.contains("SWARM")) applySwarmOffset();
		}
	}

	private Vector2f getApproximateInterception(int calculationSteps) {
		Vector2f returnPoint = new Vector2f(target.getLocation());
		for (int i = 0; i < calculationSteps; i++) {
			float arrivalTime = MathUtils.getDistance(proj.getLocation(), returnPoint) / proj.getVelocity().length();
			returnPoint.x = target.getLocation().x + (target.getVelocity().x * arrivalTime * INTERCEPT_ACCURACY_FACTOR - (lifeCounter / 100f));
			returnPoint.y = target.getLocation().y + (target.getVelocity().y * arrivalTime * INTERCEPT_ACCURACY_FACTOR - (lifeCounter / 100f));
		}
		return returnPoint;
	}

	private void applySwarmOffset() {
		int i = 40;
		boolean success = false;
		while (i > 0 && target != null) {
			i--;
			Vector2f potPoint = MathUtils.getRandomPointInCircle(target.getLocation(), target.getCollisionRadius());
			if (CollisionUtils.isPointWithinBounds(potPoint, target)) {
				potPoint.x -= target.getLocation().x;
				potPoint.y -= target.getLocation().y;
				potPoint = VectorUtils.rotate(potPoint, -target.getFacing());
				targetPoint = new Vector2f(potPoint);
				success = true;
				break;
			}
		}
		if (!success) targetPoint = new Vector2f(Misc.ZERO);
	}
}