package data.scripts.weapons;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Causes fighters to attempt a "runway landing" rather than the more traditional VTOL-style landing
 * @author Nicke535
 */
public class VassDirectedLandingBayScript implements EveryFrameWeaponEffectPlugin {
    //The "acceleration boost" the fighter gets while landing
    private static final float ACCEL_BOOST = 3f;

    //The "speed boost" the fighter gets while landing
    private static final float SPEED_BOOST = 2f;

    //The "turnrate boost" the fighter gets while landing
    private static final float TURNRATE_BOOST = 1.5f;

    //How far away does the landing "start"? IE at 0 seconds, where should the fighters try to move?
    //  This is then moved closer and closer to the weapon over the duration
    private static final float START_OFFSET_LENGTH = 450f;

    //Long long do we expect a landing to take?
    private static final float MOVE_DURATION = 3f;

    //Store how long we've been tracking each fighter.
    private Map<ShipAPI, Float> timers = new HashMap<>();

    //We occasionally clean our timer map
    private static final float CLEAN_WAIT = 10f;
    private float cleanTimer = 0f;

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        ShipAPI ship = weapon.getShip();
        if (ship == null || ship.isHulk()) {
            return;
        }

        //Handles cleanup of our fighter map now and then
        cleanTimer += amount;
        if (cleanTimer > CLEAN_WAIT) {
            cleanTimer = 0f;
            List<ShipAPI> toRemove = new ArrayList<>();
            for (ShipAPI fighter : timers.keySet()) {
                if (!engine.isEntityInPlay(fighter) || fighter.isHulk() || !fighter.isLanding() || fighter.isFinishedLanding()) {
                    toRemove.add(fighter);
                }
            }
            for (ShipAPI fighter : toRemove) {
                timers.remove(fighter);
            }
        }

        //Gets all our nearby fighters, and grab each one that is currently landing (and we haven't grabbed earlier)
        for (ShipAPI fighter : CombatUtils.getShipsWithinRange(ship.getLocation(), ship.getCollisionRadius()*1.5f)) {
            //Check for ignored/removed fighters to avoid infinity loops of re-adding. Also, only track landing fighters
            if (!engine.isEntityInPlay(fighter) || fighter.isHulk() || !fighter.isLanding() || fighter.isFinishedLanding()) {
                continue;
            }

            //Don't add already-tracked fighters
            if (timers.containsKey(fighter)) {
                continue;
            }

            //Only *our* fighters are affected
            if (fighter.getWing() != null && fighter.getWing().getSourceShip() == ship) {
                timers.put(fighter, 0f);
            }
        }

        //Go through all fighters and handle their actual movement-manipulation
        for (ShipAPI fighter : timers.keySet()) {
            //Compensates for time-mult
            float trueAmount = amount * fighter.getMutableStats().getTimeMult().getModifiedValue() / ship.getMutableStats().getTimeMult().getModifiedValue();
            timers.put(fighter, timers.get(fighter)+trueAmount);

            //Try to move the fighter to a point away from the ship, with the point closing in over time
            Vector2f targetPoint = MathUtils.getPoint(weapon.getLocation(), START_OFFSET_LENGTH * (1f - (timers.get(fighter) / MOVE_DURATION)), weapon.getCurrAngle());
            moveFighter(trueAmount, fighter, targetPoint, ship.getFacing());
        }
    }

    //Utility function for moving a fighter towards a point, while trying to avoid overcompensating with speed
    //  "trueAmount" is expected to have time mult baked in
    private void moveFighter (float trueAmount, ShipAPI fighter, Vector2f target, float carrierAngle) {
        //Gets the maximum acceleration and turnspeed for this frame
        float maxAccel = trueAmount * fighter.getAcceleration() * ACCEL_BOOST;
        float maxTurn = trueAmount * fighter.getMaxTurnRate() * TURNRATE_BOOST;

        //Gets the distance and angle to the target point
        float dist = MathUtils.getDistance(fighter.getLocation(), target);
        float angle = VectorUtils.getAngle(fighter.getLocation(), target);

        //Always try turning towards the carrier's angle
        float toTurn = maxTurn * Math.signum(MathUtils.getShortestRotation(fighter.getFacing(), carrierAngle));
        if (Math.abs(toTurn) > Math.abs(MathUtils.getShortestRotation(fighter.getFacing(), carrierAngle))) {
            toTurn = MathUtils.getShortestRotation(fighter.getFacing(), carrierAngle);
        }
        fighter.setFacing(fighter.getFacing() + toTurn);

        //If we are going to overshoot the target, decelerate
        if (fighter.getVelocity().length() / (fighter.getAcceleration()*ACCEL_BOOST) > dist/fighter.getVelocity().length()) {
            Vector2f decelVel = MathUtils.getPoint(Misc.ZERO, maxAccel, VectorUtils.getAngle(fighter.getVelocity(), Misc.ZERO));
            fighter.getVelocity().x += decelVel.x;
            fighter.getVelocity().y += decelVel.y;
        }

        //Otherwise, we try accelerating towards the target (taking our max speed into account)
        else {
            //Accelerate first...
            Vector2f targetVel = MathUtils.getPoint(fighter.getVelocity(), maxAccel, angle);
            fighter.getVelocity().x = targetVel.x;
            fighter.getVelocity().y = targetVel.y;

            //...then compensate if we went too far
            if (fighter.getVelocity().length() > fighter.getMaxSpeed()*SPEED_BOOST) {
                targetVel = MathUtils.getPoint(Misc.ZERO, fighter.getMaxSpeed()*SPEED_BOOST, VectorUtils.getAngle(Misc.ZERO, fighter.getVelocity()));
                fighter.getVelocity().x = targetVel.x;
                fighter.getVelocity().y = targetVel.y;
            }
        }
    }
}