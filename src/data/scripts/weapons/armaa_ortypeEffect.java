package data.scripts.weapons;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import java.awt.Color;
import com.fs.starfarer.api.Global;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;
import org.lazywizard.lazylib.VectorUtils;


/**
 * This is a heavily modified script using
 * @author Tartiflette
 * 's work as a basis
 */
public class armaa_ortypeEffect implements EveryFrameWeaponEffectPlugin 
{
    private static final Vector2f ZERO = new Vector2f();
    private float CHARGEUP_PARTICLE_ANGLE_SPREAD = 360f;
    private float CHARGEUP_PARTICLE_BRIGHTNESS = 1f;
    private float CHARGEUP_PARTICLE_DISTANCE_MAX = 200f;
    private float CHARGEUP_PARTICLE_DISTANCE_MIN = 100f;
    private float CHARGEUP_PARTICLE_DURATION = 0.5f;
    private float CHARGEUP_PARTICLE_SIZE_MAX = 5f;
    private float CHARGEUP_PARTICLE_SIZE_MIN = 1f;
    public float TURRET_OFFSET = 30f;
	//sliver cannon charging fx
    private boolean charging = false;
    private boolean cooling = false;
    private boolean firing = false;
    private final IntervalUtil interval = new IntervalUtil(0.015f, 0.015f);
    private final IntervalUtil interval2 = new IntervalUtil(0.075f, 0.075f);
    private float level = 0f;

   
    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) 
	{
		ShipAPI ship = weapon.getShip();
		MutableShipStatsAPI stats = ship.getMutableStats();
		String id = ship.getId();
		
		if(weapon.isFiring() && weapon.getAmmo() <= 0)
		{
			ship.blockCommandForOneFrame(ShipCommand.TURN_LEFT);
			ship.blockCommandForOneFrame(ShipCommand.TURN_RIGHT);			
		}
		
		if(weapon.isFiring() || weapon.getChargeLevel() > 0.2f)
		{
			
			stats.getMaxSpeed().modifyMult(id, 1-(weapon.getChargeLevel()*0.5f));		
			//stats.getMaxTurnRate().modifyMult(id, 1-.5f*weapon.getChargeLevel());
			if(ship.getWeaponGroupFor(weapon)!=null )
			{
				//WEAPON IN AUTOFIRE
				ShipAPI target = null;
				if(ship.getWeaponGroupFor(weapon).isAutofiring()){ //weapon group is not the selected group
					target = ship.getWeaponGroupFor(weapon).getAutofirePlugin(weapon).getTargetShip();
				}
				else {
					target = ship.getShipTarget();
				}
				if(target != null)
				{
					float facingtarget = VectorUtils.getAngle(ship.getLocation(), target.getLocation());
					if(ship.getFacing() != facingtarget)
					{
						if(ship.getFacing() < facingtarget)
							ship.setFacing(ship.getFacing()+0.05f);
						else
							ship.setFacing(ship.getFacing()-0.05f);
					}
					//ship.setFacing(VectorUtils.getAngle(ship.getLocation(), target.getLocation()));
				}
			}
			
		}
		
		else
		{
			stats.getMaxSpeed().unmodify(id);
			stats.getMaxTurnRate().unmodify(id);
			
		}
		
		
		if(engine.isPaused() || weapon.getShip().getOriginalOwner()==-1 || ship.getFluxTracker().isOverloaded() || weapon.isDisabled())
			return;
					
			Vector2f origin = new Vector2f(weapon.getLocation());
			Vector2f offset = new Vector2f(TURRET_OFFSET, -0f);
            VectorUtils.rotate(offset, weapon.getCurrAngle(), offset);
            Vector2f.add(offset, origin, origin);
            //float shipFacing = weapon.getCurrAngle();
		float shipFacing = weapon.getCurrAngle();
		Vector2f shipVelocity = weapon.getShip().getVelocity();
		
			if(weapon.isFiring() && weapon.getChargeLevel() > 0f && weapon.getChargeLevel() < 1f)
			{
							   float radius = 30f + (weapon.getChargeLevel() * weapon.getChargeLevel()* MathUtils.getRandomNumberInRange(25f, 75f));
			   Color color = new Color(255, 0, 255, 255);

				 engine.addHitParticle(origin, ZERO, (radius)-10, 0.1f + weapon.getChargeLevel() * 0.3f, 0.2f, color);
				 engine.addHitParticle(origin, ZERO, (radius/2)-10, 0.1f + weapon.getChargeLevel() * 0.3f, 0.2f, new Color(255,255,255,255));
			}
		   if (charging) {
			   float radius = 30f + (weapon.getChargeLevel() * weapon.getChargeLevel()* MathUtils.getRandomNumberInRange(25f, 75f));
			   Color color = new Color(200, 0, 200, 255);

                if (firing && (weapon.getChargeLevel() < 1f)) {
                    charging = false;
                    cooling = true;
                    firing = false;
					//Global.getSoundPlayer().playSound("beamfire", 1f, 1f, origin, weapon.getShip().getVelocity());
                } else if (weapon.getChargeLevel() < 1f) {
                    interval.advance(amount);
                    if (interval.intervalElapsed()) {
                    
                        engine.addHitParticle(origin, ZERO, radius*weapon.getChargeLevel(), 0.1f + weapon.getChargeLevel() * 0.3f, 0.2f, color);
						 engine.addHitParticle(origin, ZERO, (radius/2)*weapon.getChargeLevel(), 0.1f + weapon.getChargeLevel() * 0.3f, 0.2f, new Color(255,255,255,255));

                        int count = 1 + (int) (weapon.getChargeLevel() * 4);
                        for (int i = 0; i < count; i++) {
                            float distance = MathUtils.getRandomNumberInRange(CHARGEUP_PARTICLE_DISTANCE_MIN,
                                    CHARGEUP_PARTICLE_DISTANCE_MAX)
                                    * weapon.getChargeLevel();
                            float size = MathUtils.getRandomNumberInRange(CHARGEUP_PARTICLE_SIZE_MIN, CHARGEUP_PARTICLE_SIZE_MAX)*weapon.getChargeLevel();
                            float angle = MathUtils.getRandomNumberInRange(-0.5f * CHARGEUP_PARTICLE_ANGLE_SPREAD, 0.5f
                                    * CHARGEUP_PARTICLE_ANGLE_SPREAD);
                            float speed = 0.75f * (distance / CHARGEUP_PARTICLE_DURATION)*weapon.getChargeLevel();
                            Vector2f particleVelocity = MathUtils.getPointOnCircumference(shipVelocity, speed, angle + shipFacing
                                    + 180f);
                            engine.addHitParticle(origin, particleVelocity, size, CHARGEUP_PARTICLE_BRIGHTNESS * Math.min(
                                    weapon.getChargeLevel() + 0.5f, 1f)
                                            * MathUtils.getRandomNumberInRange(0.75f, 1.25f), CHARGEUP_PARTICLE_DURATION,
                                    color);
                        }
                    }
                } else {
                    firing = true;
						 engine.addHitParticle(origin, ZERO, (radius)-10, 0.1f + weapon.getChargeLevel() * 0.3f, 0.2f, color);
						 engine.addHitParticle(origin, ZERO, (radius/2)-10, 0.1f + weapon.getChargeLevel() * 0.3f, 0.2f, new Color(255,255,255,255));

                }
            } else {
                if (cooling) {
                    if (weapon.getChargeLevel() <= 0f) {
                        cooling = false;
						//Global.getSoundPlayer().playSound("beamfire", 1f, 1f, origin, weapon.getShip().getVelocity());
                    }
                } else if (weapon.getChargeLevel() > level) {
					Global.getSoundPlayer().playSound("beamchargeM", 1f, 1f, origin, weapon.getShip().getVelocity());
                    charging = true;
                }
            }
            level = weapon.getChargeLevel();

        
		
    }

}
