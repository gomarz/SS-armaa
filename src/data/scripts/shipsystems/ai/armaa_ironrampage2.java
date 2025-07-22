package data.scripts.shipsystems.ai;

import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipSystemAIScript;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.util.IntervalUtil;
import java.util.List;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lwjgl.util.vector.Vector2f;
import java.util.HashMap;
import java.util.Map;
import org.lazywizard.lazylib.combat.CombatUtils;
public class armaa_ironrampage2 implements ShipSystemAIScript {
    private ShipAPI ship;
    private CombatEngineAPI engine;
    private ShipwideAIFlags flags;
    private ShipSystemAPI system;
    private IntervalUtil tracker = new IntervalUtil(0.5f, 1f);
	private static Map approachComfort = new HashMap();
    static {

        approachComfort.put(ShipAPI.HullSize.FIGHTER, 0f);
        approachComfort.put(ShipAPI.HullSize.FRIGATE, 1f);
        approachComfort.put(ShipAPI.HullSize.DESTROYER, 0.75f);
        approachComfort.put(ShipAPI.HullSize.CRUISER, 0.5f);
        approachComfort.put(ShipAPI.HullSize.CAPITAL_SHIP, 0.20f);
    }
    private float normalizeAngle(float f) {
        if (f < 0f)
            return f + 360f;
        if (f > 360f)
            return f - 360f;
        return f;
    }	
    @Override
    public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
        if (!engine.isPaused()) {
            tracker.advance(amount);
            if (AIUtils.canUseSystemThisFrame(ship)) {
                if (tracker.intervalElapsed()) {
                    if (ship.isDirectRetreat()) {ship.useSystem();return;}
                    
                    if (target == null) 
					{
                        return;
                    }
					if(ship.getHardFluxLevel() >= 0.8f)
						flags.setFlag(AIFlags.BACK_OFF);
					if(ship.getHullLevel() < 0.3f)
						return;
                    if (ship.getShipTarget() == null) {		
                        ship.setShipTarget(target);
                        return;
                    }
					float dangerLevel = -3f;	
					List<ShipAPI> potentialTargets = CombatUtils.getShipsWithinRange(ship.getLocation(),2500f);			
					for(ShipAPI pt : potentialTargets)
					{
						if(!ship.isFighter() && pt.isFighter())
							continue;				
						int mult = ship.getOwner() == pt.getOwner() ? -1 : 1;
						if(pt.isFrigate())
							dangerLevel+=1*mult;
						else if(pt.isDestroyer())
							dangerLevel+=2*mult;
						else if(pt.isCruiser())
							dangerLevel+=3*mult;
						else if(pt.isCapital())
							dangerLevel+=4*mult;			
					}
					if(dangerLevel >= 8)
						return;
                    if (!target.isAlive() || target.getOwner() == ship.getOwner()) {						
                        return;
                    }
                    //Vector2f end = MathUtils.getPoint(ship.getLocation(), 600f, ship.getFacing());
					if(ship.getCollisionClass() != CollisionClass.FIGHTER)
					{
						List<ShipAPI> entity = AIUtils.getNearbyAllies(ship, 450f);
						if (!entity.isEmpty()){
							for (ShipAPI e : entity) {                                
								if(e.getCollisionClass()!=CollisionClass.NONE && e.getCollisionClass() != CollisionClass.FIGHTER) {
									//if (getShipCollisionPoint(ship.getLocation(), end, s, ship.getFacing()) != null) {
										if (Math.abs(MathUtils.getShortestRotation(VectorUtils.getAngle(ship.getLocation(), e.getLocation()), ship.getFacing())) <= MathUtils.getRandomNumberInRange(25f, 65f)) {
											//engine.addFloatingText(ship.getLocation(), "Nya get out!!!", 30f, Color.WHITE, ship, 1f, 0.5f);
											return;
										}
									//}
								}
							}
						}
					}
                    if (target.isFighter() || target.isDrone() || target.isStation() || target.isStationModule() || ship.isRetreating()) {
                        return;
                    }
					float flanking = 1f;
					float angle = normalizeAngle(target.getFacing()) - normalizeAngle(ship.getFacing());
					angle = normalizeAngle(angle);
					if (angle <= 150f * 0.5f || angle >= 360f - (150f * 0.5f) || target.getFluxTracker().isOverloaded()) {
						flanking = 3f;
					}
					if (Math.random() > ((float) approachComfort.get(target.getHullSize())) * flanking * (1 + (1f * (target.getFluxLevel() - ship.getFluxLevel()))))
						return;		
					/*
                    if (MathUtils.isWithinRange(ship, target, 625f*ship.getSystem().getAmmo()) || (flags.hasFlag(AIFlags.MANEUVER_RANGE_FROM_TARGET) && (float)flags.getCustom(AIFlags.MANEUVER_RANGE_FROM_TARGET) <= 475f*ship.getSystem().getAmmo())) {
                        ship.useSystem(); //If you're still close to the damn ship. WHO CARES IF YOU'RE HIGH FLUX. GLORY TO THE FIRST MAN TO DIE, CHARGE!
                        flags.setFlag(AIFlags.DO_NOT_BACK_OFF, 3.5f);
                        return;
                    }
                    */
                    if (MathUtils.isWithinRange(ship, target, 2200f) && ship.getSystem().getAmmo() < MathUtils.getRandomNumberInRange(1, 2)) {
                        return; //wtf ur TOO FAR GO AWAY JUST BACK OFF
                    }

                    if (ship.getShipTarget() != null && (ship.getShipTarget().getHullLevel() <= 0.5f || ship.getShipTarget().getHardFluxLevel() >= 0.70f || Global.getCombatEngine().getCustomData().get("armaa_"+ship.getId()+"_combo") != null) || (flags.hasFlag(AIFlags.PURSUING) || flags.hasFlag(AIFlags.HARASS_MOVE_IN))) {
                        ship.useSystem();
						if(Global.getCombatEngine().getCustomData().get("armaa_"+ship.getId()+"_combo") != null)
							Global.getCombatEngine().getCustomData().remove("armaa_"+ship.getId()+"_combo");
                        flags.setFlag(AIFlags.DO_NOT_BACK_OFF, 3.5f);
                    }
                }
            }
        }
    }
    
    public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
        this.ship = ship;
        this.flags = flags;
        this.engine = engine;
        this.system = system;
    }
}
