package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import org.lwjgl.util.vector.Vector2f;
import org.lazywizard.lazylib.MathUtils;
import java.awt.Color;
import org.lazywizard.lazylib.VectorUtils;
import com.fs.starfarer.api.util.IntervalUtil;



import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;

public class armaa_javelinBeamEffect implements EveryFrameWeaponEffectPlugin{ 
    private static final Vector2f ZERO = new Vector2f();
    private float CHARGEUP_PARTICLE_ANGLE_SPREAD = 360f;
    private float CHARGEUP_PARTICLE_BRIGHTNESS = 0.75f;
    private float CHARGEUP_PARTICLE_DISTANCE_MAX = 150f;
    private float CHARGEUP_PARTICLE_DISTANCE_MIN = 70f;
    private float CHARGEUP_PARTICLE_DURATION = 0.4f;
    private float CHARGEUP_PARTICLE_SIZE_MAX = 3f;
    private float CHARGEUP_PARTICLE_SIZE_MIN = 2f;
    public float TURRET_OFFSET = 20f;
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
		if(engine.isPaused() || weapon.getShip().getOriginalOwner()==-1)
			
		if(weapon.getChargeLevel()==1)
		{
		                    Vector2f.add(MathUtils.getPoint(new Vector2f(), 6, weapon.getCurrAngle()+180), weapon.getShip().getVelocity(), weapon.getShip().getVelocity());

                      Vector2f drift = MathUtils.getPoint(new Vector2f(), MathUtils.getRandomNumberInRange(0, 6), weapon.getCurrAngle());
                        Vector2f.add(drift, new Vector2f(weapon.getShip().getVelocity()), drift);

		}
		
			Vector2f origin = new Vector2f(weapon.getLocation());
			Vector2f offset = new Vector2f(TURRET_OFFSET, -0f);
            VectorUtils.rotate(offset, weapon.getCurrAngle(), offset);
            Vector2f.add(offset, origin, origin);
            //float shipFacing = weapon.getCurrAngle();
		float shipFacing = weapon.getCurrAngle();
		Vector2f shipVelocity = weapon.getShip().getVelocity();
		
			if(weapon.isFiring() && weapon.getChargeLevel() > 0f && weapon.getChargeLevel() < 1f)
			{
							   float radius = 20f + (weapon.getChargeLevel() * weapon.getChargeLevel()* MathUtils.getRandomNumberInRange(25f, 75f));
			   Color color = new Color(255, 0, 255, 255);

				 engine.addHitParticle(origin, ZERO, (radius)-10, 0.1f + weapon.getChargeLevel() * 0.3f, 0.2f, color);
				 engine.addHitParticle(origin, ZERO, (radius/2)-10, 0.1f + weapon.getChargeLevel() * 0.3f, 0.2f, new Color(255,255,255,255));
			}
		   if (charging) {
			   float radius = 20f + (weapon.getChargeLevel() * weapon.getChargeLevel()* MathUtils.getRandomNumberInRange(25f, 75f));
			   Color color = new Color(255, 0, 255, 255);

                if (firing && (weapon.getChargeLevel() < 1f)) {
                    charging = false;
                    cooling = true;
                    firing = false;
					//Global.getSoundPlayer().playSound("beamfire", 1f, 1f, origin, weapon.getShip().getVelocity());
                } else if (weapon.getChargeLevel() < 1f) {
                    interval.advance(amount);
                    if (interval.intervalElapsed()) {
                    
                        engine.addHitParticle(origin, ZERO, radius, 0.1f + weapon.getChargeLevel() * 0.3f, 0.2f, color);
						 engine.addHitParticle(origin, ZERO, radius/2, 0.1f + weapon.getChargeLevel() * 0.3f, 0.2f, new Color(255,255,255,255));

                        int count = 2 + (int) (weapon.getChargeLevel() * 25);
                        for (int i = 0; i < count; i++) {
                            float distance = MathUtils.getRandomNumberInRange(CHARGEUP_PARTICLE_DISTANCE_MIN,
                                    CHARGEUP_PARTICLE_DISTANCE_MAX)
                                    * weapon.getChargeLevel();
                            float size = MathUtils.getRandomNumberInRange(CHARGEUP_PARTICLE_SIZE_MIN, CHARGEUP_PARTICLE_SIZE_MAX)*weapon.getChargeLevel();
                            float angle = MathUtils.getRandomNumberInRange(-0.5f * CHARGEUP_PARTICLE_ANGLE_SPREAD, 0.5f
                                    * CHARGEUP_PARTICLE_ANGLE_SPREAD);
                            float speed = 0.75f * distance / CHARGEUP_PARTICLE_DURATION;
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
					Global.getSoundPlayer().playSound("beamcharge", 1f, 0.85f, origin, weapon.getShip().getVelocity());
                    charging = true;
                }
            }
            level = weapon.getChargeLevel();
	}
	
	
}
