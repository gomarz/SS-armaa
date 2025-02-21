package data.scripts.shipsystems.ai;

import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAIScript;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.util.IntervalUtil;
import java.util.List;
import java.awt.Color;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lwjgl.util.vector.Vector2f;

public class armaa_ironrampage implements ShipSystemAIScript {
    private ShipAPI ship;
    private CombatEngineAPI engine;
    private ShipwideAIFlags flags;
    private ShipSystemAPI system;
    private IntervalUtil tracker = new IntervalUtil(0.5f, 1f);

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
					if(ship.getHardFluxLevel() >= 0.7f)
						flags.setFlag(AIFlags.BACK_OFF);
					if(ship.getHullLevel() < 0.3f)
						return;
                    if (ship.getShipTarget() == null) {		
                        ship.setShipTarget(target);
                        return;
                    }

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
                    if (target.isFighter() || target.isDrone() || target.isStation() || target.isStationModule() || target.getEngineController().isFlamedOut() || ship.isRetreating()) {
                        return;
                    }
                    
                    if (MathUtils.isWithinRange(ship, target, 625f*ship.getSystem().getAmmo()) || (flags.hasFlag(AIFlags.MANEUVER_RANGE_FROM_TARGET) && (float)flags.getCustom(AIFlags.MANEUVER_RANGE_FROM_TARGET) <= 475f*ship.getSystem().getAmmo())) {
                        ship.useSystem(); //If you're still close to the damn ship. WHO CARES IF YOU'RE HIGH FLUX. GLORY TO THE FIRST MAN TO DIE, CHARGE!
                        flags.setFlag(AIFlags.DO_NOT_BACK_OFF, 3.5f);
                        return;
                    }
                    
                    if (MathUtils.isWithinRange(ship, target, 2200f) && ship.getSystem().getAmmo() < MathUtils.getRandomNumberInRange(1, 2)) {
                        return; //wtf ur TOO FAR GO AWAY JUST BACK OFF
                    }

                    /*if (ship.getHardFluxLevel()-(ship.getHullLevel()*0.2) > ship.getSystem().getAmmo()*MathUtils.getRandomNumberInRange(0.5f, 1f)) {
                        return;
                    }*/
                    
                    if (flags.hasFlag(AIFlags.BACKING_OFF) && ship.getHardFluxLevel() >= (ship.getSystem().getAmmo())*MathUtils.getRandomNumberInRange(0.8f, 1.0f)) {
                        if (flags.hasFlag(AIFlags.HAS_INCOMING_DAMAGE)) {
                            ship.useSystem();
                            flags.setFlag(AIFlags.DO_NOT_BACK_OFF, 3.5f);
                        }
                        return;
                    }

                    if (flags.hasFlag(AIFlags.PURSUING) || flags.hasFlag(AIFlags.HARASS_MOVE_IN)) {
                        ship.useSystem();
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
