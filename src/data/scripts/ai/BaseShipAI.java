package data.scripts.ai;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAIPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lwjgl.util.vector.Vector2f;

//Code by Sundog

public abstract class BaseShipAI implements ShipAIPlugin {
    protected ShipAPI ship;
    protected float dontFireUntil = 0f;
    //protected IntervalTracker circumstanceEvaluationTimer = new IntervalTracker(0.05f, 0.15f);
    static final float DEFAULT_FACING_THRESHHOLD = 3;

    public boolean mayFire() {
        return dontFireUntil <= Global.getCombatEngine().getTotalElapsedTime(false);
    }
    public void init() { }
    public void evaluateCircumstances() { }
    public boolean isFacing(CombatEntityAPI target) {
        return isFacing(target.getLocation(), DEFAULT_FACING_THRESHHOLD);
    }
    public boolean isFacing(CombatEntityAPI target, float threshholdDegrees) {
        return isFacing(target.getLocation(), threshholdDegrees);
    }
    public boolean isFacing(Vector2f point) {
        return isFacing(point, DEFAULT_FACING_THRESHHOLD);
    }
    public boolean isFacing(Vector2f point, float threshholdDegrees) {
        return (Math.abs(getAngleTo(point)) <= threshholdDegrees);
    }
    public float getAngleTo(CombatEntityAPI entity) {
        return getAngleTo(entity.getLocation());
    }
    public float getAngleTo(Vector2f point) {
        float angleTo = VectorUtils.getAngle(ship.getLocation(), point);
        return MathUtils.getShortestRotation(ship.getFacing(), angleTo);
    }
    
    public ShipCommand strafe(float degreeAngle, boolean strafeAway) {
        float angleDif = MathUtils.getShortestRotation(ship.getFacing(), degreeAngle);

        if((!strafeAway && Math.abs(angleDif) < DEFAULT_FACING_THRESHHOLD)
                || (strafeAway && Math.abs(angleDif) > 180 - DEFAULT_FACING_THRESHHOLD))
            return null;

        ShipCommand direction = (angleDif > 0) ^ strafeAway
                ? ShipCommand.STRAFE_LEFT
                : ShipCommand.STRAFE_RIGHT;

        ship.giveCommand(direction, null, 0);

        return direction;
    }
    public ShipCommand strafe(Vector2f location, boolean strafeAway) {
        return strafe(VectorUtils.getAngle(ship.getLocation(), location), strafeAway);
    }
    public ShipCommand strafe(CombatEntityAPI entity, boolean strafeAway) {
        return strafe(entity.getLocation(), strafeAway);
    }
    public ShipCommand strafeToward(float degreeAngle) {
        return strafe(degreeAngle, false);
//        float angleDif = MathUtils.getShortestRotation(ship.getFacing(), degreeAngle);
//
//        if(Math.abs(angleDif) < 5) return null;
//
//        ShipCommand direction = (angleDif > 0) ? ShipCommand.STRAFE_LEFT : ShipCommand.STRAFE_RIGHT;
//        ship.giveCommand(direction, null, 0);
//
//        return direction;
    }
    public ShipCommand strafeToward(Vector2f location) {
        return strafeToward(VectorUtils.getAngle(ship.getLocation(), location));
    }
    public ShipCommand strafeToward(CombatEntityAPI entity) {
        return strafeToward(entity.getLocation());
    }
    public ShipCommand strafeAway(float degreeAngle) {
        return strafe(degreeAngle, true);
    }
    public ShipCommand strafeAway(Vector2f location) {
        return strafeAway(VectorUtils.getAngle(ship.getLocation(), location));
    }
    public ShipCommand strafeAway(CombatEntityAPI entity) {
        return strafeAway(entity.getLocation());
    }
    public ShipCommand turn(float degreeAngle, boolean turnAway) {
        float angleDif = MathUtils.getShortestRotation(ship.getFacing(), degreeAngle);
        
        //Check to see if we should slow down to avoid overshooting
        float secondsTilDesiredFacing = angleDif / ship.getAngularVelocity();
        if(secondsTilDesiredFacing > 0) {
            float turnAcc = ship.getMutableStats().getTurnAcceleration().getModifiedValue();
            float rotValWhenAt = Math.abs(ship.getAngularVelocity()) - secondsTilDesiredFacing * turnAcc;
            if(rotValWhenAt > 0) turnAway = !turnAway;
        }
        
//        if((!turnAway && Math.abs(angleDif) < DEFAULT_FACING_THRESHHOLD)
//                || (turnAway && Math.abs(angleDif) > 180 - DEFAULT_FACING_THRESHHOLD))
//            return null;

        ShipCommand direction = (angleDif > 0) ^ turnAway
                ? ShipCommand.TURN_LEFT   
                : ShipCommand.TURN_RIGHT;

        ship.giveCommand(direction, null, 0);
        
//        float amount = Global.getCombatEngine().getElapsedInLastFrame();
//        float turnAcc = ship.getMutableStats().getTurnAcceleration().getModifiedValue();
//        float maxTurn = ship.getMutableStats().getMaxTurnRate().getModifiedValue();
//        float angleVel = ship.getAngularVelocity();
//        float dAngleVel = turnAcc * ((direction == ShipCommand.TURN_RIGHT) ? -1 : 1) * amount;
//        float newAngleVel = angleVel + dAngleVel;
//        
//        ship.setAngularVelocity(Math.max(-maxTurn, Math.min(maxTurn, newAngleVel)));

        return direction;
    }
    public ShipCommand turn(Vector2f location, boolean strafeAway) {
        return turn(VectorUtils.getAngle(ship.getLocation(), location), strafeAway);
    }
    public ShipCommand turn(CombatEntityAPI entity, boolean strafeAway) {
        return turn(entity.getLocation(), strafeAway);
    }
    public ShipCommand turnToward(float degreeAngle) {
        return turn(degreeAngle, false);
    }
    public ShipCommand turnToward(Vector2f location) {
        return turnToward(VectorUtils.getAngle(ship.getLocation(), location));
    }
    public ShipCommand turnToward(CombatEntityAPI entity) {
        return turnToward(entity.getLocation());
    }
    public ShipCommand turnAway(float degreeAngle) {
        return turn(degreeAngle, true);
//        float angleDif = MathUtils.getShortestRotation(ship.getFacing(), degreeAngle);
//
//        if(Math.abs(angleDif) < 5) return null;
//
//        ShipCommand direction = (angleDif <= 0) ? ShipCommand.TURN_LEFT : ShipCommand.TURN_RIGHT;
//        ship.giveCommand(direction, null, 0);
//
//        return direction;
    }
    public ShipCommand turnAway(Vector2f location) {
        return turnAway(VectorUtils.getAngle(ship.getLocation(), location));
    }
    public ShipCommand turnAway(CombatEntityAPI entity) {
        return turnAway(entity.getLocation());
    }
    public void accelerate() {
        ship.giveCommand(ShipCommand.ACCELERATE, null, 0);
    }
    public void accelerateBackward() {
        ship.giveCommand(ShipCommand.ACCELERATE_BACKWARDS, null, 0);
    }
    public void decelerate() {
        ship.giveCommand(ShipCommand.DECELERATE, null, 0);
    }
    public void turnLeft() {
        ship.giveCommand(ShipCommand.TURN_LEFT, null, 0);
    }
    public void turnRight() {
        ship.giveCommand(ShipCommand.TURN_RIGHT, null, 0);
    }
    public void strafeLeft() {
        ship.giveCommand(ShipCommand.STRAFE_LEFT, null, 0);
    }
    public void strafeRight() {
        ship.giveCommand(ShipCommand.STRAFE_RIGHT, null, 0);
    }
    public void vent() {
        ship.giveCommand(ShipCommand.VENT_FLUX, null, 0);

//        return !ship.getFluxTracker().isOverloadedOrVenting()
//                && ship.getFluxTracker().getCurrFlux() > 0;
    }
    public boolean useSystem() {
        boolean canDo = AIUtils.canUseSystemThisFrame(ship);

        if(canDo) ship.giveCommand(ShipCommand.USE_SYSTEM, null, 0);

        return canDo;
    }
    public void toggleDefenseSystem() {
        ship.giveCommand(ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK, null, 0);
    }
    public void toggleAutofire(int group) {
        ship.giveCommand(ShipCommand.TOGGLE_AUTOFIRE, null, group);
    }
    public void selectWeaponGroup(int group) {
        ship.giveCommand(ShipCommand.SELECT_GROUP, null, group);
    }

    public BaseShipAI(ShipAPI ship) { this.ship = ship; }

    @Override
    public void advance(float amount) {
        //if(circumstanceEvaluationTimer.intervalElapsed()) evaluateCircumstances();
        evaluateCircumstances();
    }

    @Override
    public void forceCircumstanceEvaluation() {
    	init();
        evaluateCircumstances();
    }

    @Override
    public boolean needsRefit() {
        return false;
    }

    @Override
    public void setDoNotFireDelay(float amount) {
        dontFireUntil = amount + Global.getCombatEngine().getTotalElapsedTime(false);
    }
    
}