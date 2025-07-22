package data.scripts.shipsystems.ai;

import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.CombatAssignmentType;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI.AssignmentInfo;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAIScript;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import data.scripts.shipsystems.armaa_JauntBoosterStats;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import org.lazywizard.lazylib.CollectionUtils;
import org.lazywizard.lazylib.CollisionUtils;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.combat.ShipCommand;

public class armaa_JauntBoosterAI implements ShipSystemAIScript {

    private CombatEngineAPI engine;
    private ShipwideAIFlags flags;
    private ShipAPI ship;
	private boolean phase = false;
    private ShipSystemAPI system;

    private final IntervalUtil tracker = new IntervalUtil(0.05f, 0.1f);

    private static final float FIGHTER_CRITICAL_RANGE = 500f;
    private static final float FIGHTER_STRIKE_RANGE = 1200f;

    private static final boolean DEBUG = false;
    private final Object STATUSKEY1 = new Object();
    private final Object STATUSKEY2 = new Object();
    private float desireShow = 0f;
    private float targetDesireShow = 0f;
    private float angleToTargetShow = 0f;

    @Override
    public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
        if (engine == null) {
            return;
        }

        if (engine.isPaused()) {
            if (DEBUG) {
                if (engine.getPlayerShip() == ship) {
                    engine.maintainStatusForPlayerShip(STATUSKEY1, system.getSpecAPI().getIconSpriteName(),
                            "AI", "Desire: " + Math.round(100f * desireShow) + "/" + Math.round(100f * targetDesireShow), desireShow < targetDesireShow);
                    engine.maintainStatusForPlayerShip(STATUSKEY2, system.getSpecAPI().getIconSpriteName(),
                            "AI", "Angle: " + Math.round(angleToTargetShow), false);
                }
            }
            return;
        }

        tracker.advance(amount);

        if (tracker.intervalElapsed()) {
            if (ship.getFluxTracker().isOverloadedOrVenting() || (system.getAmmo() == 0) || !AIUtils.canUseSystemThisFrame(ship) || ship.isLanding()) {
                if (DEBUG) {
                    if (engine.getPlayerShip() == ship) {
                        engine.maintainStatusForPlayerShip(STATUSKEY1, system.getSpecAPI().getIconSpriteName(),
                                "AI", "(Desire: " + Math.round(100f * desireShow) + "/" + Math.round(100f * targetDesireShow) + ")", desireShow < targetDesireShow);
                        engine.maintainStatusForPlayerShip(STATUSKEY2, system.getSpecAPI().getIconSpriteName(),
                                "AI", "Angle: " + Math.round(angleToTargetShow), false);
                    }
                }
                return;
            }

            /* Skip if we're flamed out.  Except for Armor, which wants to immediately use the system if it's flamed out. */
            float desire = 0f;
                if (ship.getEngineController().isFlamedOut()) {
                    if (DEBUG) {
                        if (engine.getPlayerShip() == ship) {
                            engine.maintainStatusForPlayerShip(STATUSKEY1, system.getSpecAPI().getIconSpriteName(),
                                    "AI", "Flamed out!", true);
                            engine.maintainStatusForPlayerShip(STATUSKEY2, system.getSpecAPI().getIconSpriteName(),
                                    "AI", "Angle: " + Math.round(angleToTargetShow), false);
                        }
                    }
                    return;
                }
            

            boolean returning = false;
            if ((ship.getWing() != null) && ship.getWing().isReturning(ship)) {
                returning = true;
            }

            float engageRange;
            if (ship.getWing() != null) {
                if (returning) {
                    engageRange = 500f;
                } else {
                    engageRange = ship.getWing().getSpec().getAttackRunRange();
                }
            } else {
                engageRange = 1000f;
                for (WeaponAPI weapon : ship.getUsableWeapons()) {
                    if (weapon.getType() == WeaponType.MISSILE) {
                        continue;
                    }
                    if (weapon.getRange() > engageRange) {
                        engageRange = weapon.getRange();
                    }
                }
            }

            CombatEntityAPI immediateTarget = null;
            ShipAPI carrier = null;
            if ((ship.getWing() != null) && ship.getWing().getSourceShip() != null) {
                carrier = ship.getWing().getSourceShip();
                if (returning) {
                    immediateTarget = carrier;
                } else if ((carrier.getAIFlags() != null) && carrier.getAIFlags().getCustom(AIFlags.CARRIER_FIGHTER_TARGET) instanceof CombatEntityAPI) {
                    immediateTarget = (CombatEntityAPI) carrier.getAIFlags().getCustom(AIFlags.CARRIER_FIGHTER_TARGET);
                } else if ((carrier.getAIFlags() != null) && carrier.getAIFlags().getCustom(AIFlags.MANEUVER_TARGET) instanceof CombatEntityAPI) {
                    immediateTarget = (CombatEntityAPI) carrier.getAIFlags().getCustom(AIFlags.MANEUVER_TARGET);
                } else {
                    immediateTarget = carrier.getShipTarget();
                }
            }
            if ((immediateTarget == null) && ship.isFighter() && (flags.getCustom(AIFlags.CARRIER_FIGHTER_TARGET) instanceof CombatEntityAPI)) {
                immediateTarget = (CombatEntityAPI) flags.getCustom(AIFlags.CARRIER_FIGHTER_TARGET);
            }
            if ((immediateTarget == null) && (flags.getCustom(AIFlags.MANEUVER_TARGET) instanceof CombatEntityAPI)) {
                immediateTarget = (CombatEntityAPI) flags.getCustom(AIFlags.MANEUVER_TARGET);
            }
            if (immediateTarget == null) {
                immediateTarget = ship.getShipTarget();
            }

            AssignmentInfo assignment = engine.getFleetManager(ship.getOwner()).getTaskManager(ship.isAlly()).getAssignmentFor(ship);
            Vector2f targetSpot;
            if (ship.isFighter()) {
                assignment = null;
                targetSpot = null;
            } else {
                if ((assignment != null) && (assignment.getTarget() != null) && (assignment.getType() != CombatAssignmentType.AVOID)) {
                    targetSpot = assignment.getTarget().getLocation();
                } else {
                    targetSpot = null;
                }
            }

            Vector2f direction = new Vector2f();
            float boostScale = armaa_JauntBoosterStats.BASELINE_MULT.get(ship.getHullSize());
            if (ship.getEngineController().isAccelerating()) {
                direction.y += armaa_JauntBoosterStats.BASELINE_MULT.get(ship.getHullSize()) - armaa_JauntBoosterStats.FORWARD_PENALTY.get(ship.getHullSize());
                boostScale -= armaa_JauntBoosterStats.FORWARD_PENALTY.get(ship.getHullSize());
            } else if (ship.getEngineController().isAcceleratingBackwards() || ship.getEngineController().isDecelerating()) {
                direction.y -= armaa_JauntBoosterStats.BASELINE_MULT.get(ship.getHullSize()) - armaa_JauntBoosterStats.REVERSE_PENALTY.get(ship.getHullSize());
                boostScale -= armaa_JauntBoosterStats.REVERSE_PENALTY.get(ship.getHullSize());
            }
            if (ship.getEngineController().isStrafingLeft()) {
                direction.x -= 1f;
                boostScale += 1f - armaa_JauntBoosterStats.BASELINE_MULT.get(ship.getHullSize());
            } else if (ship.getEngineController().isStrafingRight()) {
                direction.x += 1f;
                boostScale += 1f - armaa_JauntBoosterStats.BASELINE_MULT.get(ship.getHullSize());
            }
            if (direction.length() <= 0f) {
                direction.y = armaa_JauntBoosterStats.BASELINE_MULT.get(ship.getHullSize()) - armaa_JauntBoosterStats.FORWARD_PENALTY.get(ship.getHullSize());
                boostScale -= armaa_JauntBoosterStats.FORWARD_PENALTY.get(ship.getHullSize());
            }
            Misc.normalise(direction);
            VectorUtils.rotate(direction, ship.getFacing() - 90f, direction);

            float angleToTargetSpot = 0f;
            if (targetSpot != null) {
                float targetSpotDir = VectorUtils.getAngleStrict(ship.getLocation(), targetSpot);
                angleToTargetSpot = MathUtils.getShortestRotation(VectorUtils.getFacing(direction), targetSpotDir);
            }
            float angleToImmediateTarget = 0f;
            if (immediateTarget != null) {
                float immediateTargetDir = VectorUtils.getAngleStrict(ship.getLocation(), immediateTarget.getLocation());
                angleToImmediateTarget = MathUtils.getShortestRotation(VectorUtils.getFacing(direction), immediateTargetDir);
            }
            angleToTargetShow = angleToImmediateTarget;

            float onTargetThreshold;
            if (ship.isFighter()) {
                onTargetThreshold = 45f;
            } else {
                onTargetThreshold = 60f;
            }

            if (!ship.isFighter()) {
                if (flags.hasFlag(AIFlags.RUN_QUICKLY)) {
                    if (immediateTarget != null) {
                        if (Math.abs(angleToImmediateTarget) >= 90f) {
                            desire += 1.25f;
                        }
                    } else {
                        desire += 0.75f;
                    }
                }

                if (flags.hasFlag(AIFlags.PURSUING)) {
                    if (immediateTarget != null) {
                        if (Math.abs(angleToImmediateTarget) <= onTargetThreshold) {
                            desire += 0.75f;
                        }
                    } else if (targetSpot != null) {
                        if (Math.abs(angleToTargetSpot) <= onTargetThreshold) {
                            desire += 0.5f;
                        }
                    } else {
                        desire += 0.25f;
                    }
                }

                if (flags.hasFlag(AIFlags.HARASS_MOVE_IN)) {
                    if (immediateTarget != null) {
                        if (Math.abs(angleToImmediateTarget) <= onTargetThreshold) {
                            desire += 1f;
                        }
                    } else if (targetSpot != null) {
                        if (Math.abs(angleToTargetSpot) <= onTargetThreshold) {
                            desire += 0.75f;
                        }
                    } else {
                        desire += 0.5f;
                    }
                }
            } else {
                if (!returning && flags.hasFlag(AIFlags.IN_ATTACK_RUN)) {
                    if (immediateTarget != null) {
                        if (Math.abs(angleToImmediateTarget) <= onTargetThreshold) {
                            if (MathUtils.getDistance(immediateTarget, ship) < (FIGHTER_CRITICAL_RANGE - ship.getCollisionRadius())) {
                                desire -= 1f;
                            } else if (MathUtils.getDistance(immediateTarget, ship) < (FIGHTER_STRIKE_RANGE - ship.getCollisionRadius())) {
                            } else {
                                desire += 1.25f;
                            }
                        } else if (Math.abs(angleToImmediateTarget) >= (180f - onTargetThreshold)) {
                            if (MathUtils.getDistance(immediateTarget, ship) < (FIGHTER_CRITICAL_RANGE - ship.getCollisionRadius())) {
                                desire += 1.25f;
                            } else if (MathUtils.getDistance(immediateTarget, ship) < (FIGHTER_STRIKE_RANGE - ship.getCollisionRadius())) {
                                if (flags.hasFlag(AIFlags.POST_ATTACK_RUN)) {
                                    desire += 1f;
                                }
                            } else {
                                desire -= 1.25f;
                            }
                        } else {
                            if (MathUtils.getDistance(immediateTarget, ship) < (FIGHTER_CRITICAL_RANGE - ship.getCollisionRadius())) {
                                desire += 1f;
                            } else if (MathUtils.getDistance(immediateTarget, ship) < (FIGHTER_STRIKE_RANGE - ship.getCollisionRadius())) {
                                if (flags.hasFlag(AIFlags.POST_ATTACK_RUN)) {
                                    desire += 1.25f;
                                } else {
                                    desire += 0.75f;
                                }
                            }
                        }
                    } else if (targetSpot != null) {
                        if (Math.abs(angleToTargetSpot) <= onTargetThreshold) {
                            desire += 1f;
                        }
                    } else {
                        desire += 0.5f;
                    }
                }
            }

            boolean immediateTargetInRange = false;
            if ((immediateTarget != null) && (MathUtils.getDistance(immediateTarget, ship) < (engageRange - ship.getCollisionRadius()))) {
                immediateTargetInRange = true;
            }

            if ((immediateTarget != null) && !immediateTargetInRange) {
                if ((carrier == null) || !carrier.isPullBackFighters() || (immediateTarget == carrier)) {
                    if (Math.abs(angleToImmediateTarget) <= onTargetThreshold) {
                        desire += 0.5f;
                    }
                }
            }

            if (!ship.isFighter()) {
                float desiredRange = 500f;
                if ((assignment != null)
                        && ((assignment.getType() == CombatAssignmentType.ENGAGE)
                        || (assignment.getType() == CombatAssignmentType.HARASS)
                        || (assignment.getType() == CombatAssignmentType.INTERCEPT)
                        || (assignment.getType() == CombatAssignmentType.LIGHT_ESCORT)
                        || (assignment.getType() == CombatAssignmentType.MEDIUM_ESCORT)
                        || (assignment.getType() == CombatAssignmentType.HEAVY_ESCORT)
                        || (assignment.getType() == CombatAssignmentType.STRIKE))) {
                    desiredRange = engageRange;
                }
                if ((targetSpot != null) && (MathUtils.getDistance(targetSpot, ship.getLocation()) >= desiredRange) && !immediateTargetInRange) {
                    if ((immediateTarget != null) && (MathUtils.getDistance(immediateTarget, targetSpot) <= engageRange)) {
                        if (Math.abs(angleToTargetSpot) <= onTargetThreshold) {
                            desire += 0.5f; // Adds to the other 0.5
                        }
                    } else if (immediateTarget != null) {
                        if (Math.abs(angleToTargetSpot) <= onTargetThreshold) {
                            desire += 0.25f; // Adds to the other 0.5
                        }
                    } else {
                        if (Math.abs(angleToTargetSpot) <= onTargetThreshold) {
                            desire += 0.75f;
                        }
                    }
                }
            } else {
                if (returning && !immediateTargetInRange) {
                    if (immediateTarget != null) {
                        if (Math.abs(angleToImmediateTarget) <= onTargetThreshold) {
                            desire += 2f;
                        }
                    } else if (targetSpot != null) {
                        if (Math.abs(angleToTargetSpot) <= onTargetThreshold) {
                            desire += 2f;
                        }
                    } else {
                        desire += 1f;
                    }
                }
            }

            if (!ship.isFighter()) {
                if (flags.hasFlag(AIFlags.TURN_QUICKLY)) {
                    desire += 0.35f;
                }

                if (flags.hasFlag(AIFlags.BACKING_OFF)) {
                    if (immediateTarget != null) {
                        if (Math.abs(angleToImmediateTarget) >= 90f) {
                            desire += 0.75f;
                        }
                    } else {
                        desire += 0.5f;
                    }
                }

                if (flags.hasFlag(AIFlags.DO_NOT_PURSUE)) {
                    if (immediateTarget != null) {
                        if (Math.abs(angleToImmediateTarget) <= onTargetThreshold) {
                            desire -= 1f;
                        }
                    } else {
                        desire -= 0.5f;
                    }
                }

                if (flags.hasFlag(AIFlags.DO_NOT_USE_FLUX)) {
                    desire += 0.35f;
                }

                if (flags.hasFlag(AIFlags.NEEDS_HELP) || flags.hasFlag(AIFlags.IN_CRITICAL_DPS_DANGER)) {
                    if (immediateTarget != null) {
                        if (Math.abs(angleToImmediateTarget) <= onTargetThreshold) {
                            desire -= 1f;
                        } else if (Math.abs(angleToImmediateTarget) >= (180f - onTargetThreshold)) {
                            desire += 1.5f;
                        } else {
                            desire += 1f;
                        }
                    } else {
                        desire += 1f;
                    }
                }

                if (flags.hasFlag(AIFlags.HAS_INCOMING_DAMAGE)) {
                    if (immediateTarget != null) {
                        if (Math.abs(angleToImmediateTarget) <= onTargetThreshold) {
                            desire -= 0.5f;
                        } else if (Math.abs(angleToImmediateTarget) >= (180f - onTargetThreshold)) {
                            desire += 0.5f;
                        } else {
                            desire += 0.75f;
                        }
                    } else {
                        desire += 0.75f;
                    }
                }

                if ((assignment != null) && (assignment.getType() == CombatAssignmentType.RETREAT)) {
                    float retreatDirection = (ship.getOwner() == 0) ? 270f : 90f;
                    if (Math.abs(MathUtils.getShortestRotation(VectorUtils.getFacing(direction), retreatDirection)) <= onTargetThreshold) {
                        desire += 1.5f;
                    } else if (Math.abs(MathUtils.getShortestRotation(VectorUtils.getFacing(direction), retreatDirection)) >= 90f) {
                        desire -= 1.5f;
                    }
                }
            } else {
                if (flags.hasFlag(AIFlags.WANTED_TO_SLOW_DOWN)) {
                    desire -= 0.5f;
                }
            }

            float range = 500f * boostScale;
            List<ShipAPI> directTargets = CombatUtils.getShipsWithinRange(ship.getLocation(), range);
            if (!directTargets.isEmpty() && !ship.isFighter()) {
                Vector2f endpoint = new Vector2f(direction);
                endpoint.scale(range);
                Vector2f.add(endpoint, ship.getLocation(), endpoint);

                Collections.sort(directTargets, new CollectionUtils.SortEntitiesByDistance(ship.getLocation()));
                ListIterator<ShipAPI> iter = directTargets.listIterator();
                while (iter.hasNext()) {
                    ShipAPI tmp = iter.next();
                    if ((tmp != ship) && (ship.getCollisionClass() != CollisionClass.NONE) && !tmp.isFighter() && !tmp.isDrone()) {
                        Vector2f loc = tmp.getLocation();
                        float areaChange = 1f;
                        if (tmp.getOwner() == ship.getOwner()) {
                            areaChange *= 1.5f;
                        }
                        if (CollisionUtils.getCollides(ship.getLocation(), endpoint, loc,
                                (tmp.getCollisionRadius() * 0.5f) + (ship.getCollisionRadius() * 0.75f * areaChange))) {
                            if (ship.isFrigate()) {
                                if (tmp.isFrigate()) {
                                    desire -= 1f;
                                } else if (tmp.isDestroyer()) {
                                    desire -= 2f;
                                } else if (tmp.isCruiser()) {
                                    desire -= 4f;
                                } else {
                                    desire -= 8f;
                                }
                            } else if (ship.isDestroyer()) {
                                if (tmp.isFrigate() && !tmp.isHulk()) {
                                    desire -= 2f;
                                } else if (tmp.isDestroyer()) {
                                    desire -= 1f;
                                } else if (tmp.isCruiser()) {
                                    desire -= 2f;
                                } else {
                                    desire -= 4f;
                                }
                            } else if (ship.isCruiser()) {
                                if (tmp.isFrigate() && !tmp.isHulk()) {
                                    desire -= 4f;
                                } else if (tmp.isDestroyer() && !tmp.isHulk()) {
                                    desire -= 2f;
                                } else if (tmp.isCruiser()) {
                                    desire -= 1f;
                                } else {
                                    desire -= 2f;
                                }
                            } else {
                                if (tmp.isFrigate() && !tmp.isHulk()) {
                                    desire -= 8f;
                                } else if (tmp.isDestroyer() && !tmp.isHulk()) {
                                    desire -= 4f;
                                } else if (tmp.isCruiser() && !tmp.isHulk()) {
                                    desire -= 2f;
                                } else {
                                    desire -= 1f;
                                }
                            }
                        }
                    }
                }
            }

            float targetDesire;
            if (system.getMaxAmmo() <= 2) {
                if (system.getAmmo() <= 1) {
                    targetDesire = 1f;
                } else { // 2
                    targetDesire = 0.5f;
                }
            } else if (system.getMaxAmmo() == 3) {
                if (system.getAmmo() <= 1) {
                    targetDesire = 1.1f;
                } else if (system.getAmmo() == 2) {
                    targetDesire = 0.667f;
                } else { // 3
                    targetDesire = 0.45f;
                }
            } else if (system.getMaxAmmo() == 4) {
                if (system.getAmmo() <= 1) {
                    targetDesire = 1.2f;
                } else if (system.getAmmo() == 2) {
                    targetDesire = 0.8f;
                } else if (system.getAmmo() == 3) {
                    targetDesire = 0.533f;
                } else { // 4
                    targetDesire = 0.4f;
                }
            } else { // 6
                if (system.getAmmo() <= 1) {
                    targetDesire = 1.4f;
                } else if (system.getAmmo() == 2) {
                    targetDesire = 1.033f;
                } else if (system.getAmmo() == 3) {
                    targetDesire = 0.74f;
                } else if (system.getAmmo() == 4) {
                    targetDesire = 0.52f;
                } else if (system.getAmmo() == 5) {
                    targetDesire = 0.373f;
                } else { // 6
                    targetDesire = 0.3f;
                }
            }
            desireShow = desire;
            targetDesireShow = targetDesire;
            if (desire >= targetDesire) {
				if(!phase)
					ship.useSystem();
				else
					ship.giveCommand(ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK, null,0);
            }
        }

        if (DEBUG) {
            if (engine.getPlayerShip() == ship) {
                engine.maintainStatusForPlayerShip(STATUSKEY1, system.getSpecAPI().getIconSpriteName(),
                        "AI", "Desire: " + Math.round(100f * desireShow) + "/" + Math.round(100f * targetDesireShow), desireShow < targetDesireShow);
                engine.maintainStatusForPlayerShip(STATUSKEY2, system.getSpecAPI().getIconSpriteName(),
                        "AI", "Angle: " + Math.round(angleToTargetShow), false);
            }
        }
    }

    @Override
    public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
        this.ship = ship;
        this.flags = flags;
        this.system = system;
        this.engine = engine;
		//at this point we know the ship has  a system
		if(system != null)
		{
			//check if it has phase
			if(ship.getPhaseCloak() != null)
			{
				//We know it has a phase system, check if it is our system
				if(ship.getPhaseCloak().getId().equals("armaa_jauntbooster") || ship.getPhaseCloak().getId().equals("ask_jauntvernier"))
				{
					this.system = ship.getPhaseCloak();
					phase = true;
				}
			}
		}
		
		//if there's no system
		else
		{
			phase = true;
			this.system = ship.getPhaseCloak();
			
		}
		
    }
}
