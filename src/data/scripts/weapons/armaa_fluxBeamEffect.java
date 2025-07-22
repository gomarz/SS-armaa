package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import data.scripts.util.armaa_utils;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import org.lwjgl.util.vector.Vector2f;
import org.lazywizard.lazylib.MathUtils;
import java.awt.Color;
import org.lazywizard.lazylib.VectorUtils;
import com.fs.starfarer.api.util.IntervalUtil;
import org.magiclib.util.MagicRender;


import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;

public class armaa_fluxBeamEffect implements EveryFrameWeaponEffectPlugin{ 
    private static final Vector2f ZERO = new Vector2f();
    private float CHARGEUP_PARTICLE_ANGLE_SPREAD = 150f;
    private float CHARGEUP_PARTICLE_BRIGHTNESS = 1f;
    private float CHARGEUP_PARTICLE_DISTANCE_MAX = 200f;
    private float CHARGEUP_PARTICLE_DISTANCE_MIN = 100f;
    private float CHARGEUP_PARTICLE_DURATION = 0.5f;
    private float CHARGEUP_PARTICLE_SIZE_MAX = 5f;
    private float CHARGEUP_PARTICLE_SIZE_MIN = 1f;
    public float TURRET_OFFSET = 40f;
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
			return;
		if(!MagicRender.screenCheck(0.2f, weapon.getLocation()))
		{
			return;
		}
			Vector2f origin = new Vector2f(weapon.getLocation());
			Vector2f offset = new Vector2f(TURRET_OFFSET, -0f);
			if(weapon.getSlot().isTurret())
				offset = new Vector2f(weapon.getSpec().getTurretFireOffsets().get(0));
			else
				offset = new Vector2f(weapon.getSpec().getHardpointFireOffsets().get(0));
            VectorUtils.rotate(offset, weapon.getCurrAngle(), offset);
            Vector2f.add(offset, origin, origin);
			float shipFacing = weapon.getCurrAngle();
			Vector2f shipVelocity = weapon.getShip().getVelocity();
			float chargeLevel = weapon.getChargeLevel();
			Color color = new Color(150, 150, 150, 100);
			Color red = new Color(255, 0, 0, 255); // RGB for purple
			float r = (red.getRed()/255f)+chargeLevel*(color.getRed()/255f)-(red.getRed()/255f)*chargeLevel;
			float g = (red.getGreen()/255f)+chargeLevel*(color.getGreen()/255f)-(red.getGreen()/255f)*chargeLevel;
			float b = (red.getBlue()/255f)+chargeLevel*(color.getBlue()/255f)-(red.getBlue()/255f)*chargeLevel;
			if (charging || !charging && weapon.getChargeLevel() < 1 && weapon.getChargeLevel() > 0) 
			{	
				float radius = 25f + (chargeLevel * chargeLevel* MathUtils.getRandomNumberInRange(25f, 50f));			
				engine.addHitParticle(origin, ZERO, (radius), chargeLevel * 0.3f, 0.2f, new Color(r*chargeLevel,b*chargeLevel,g*chargeLevel,chargeLevel));
			}
			if (charging) 
			{
                if (firing && (chargeLevel < 1f)) {
                    charging = false;
                    cooling = true;
                    firing = false;
					//Global.getSoundPlayer().playSound("beamfire", 1f, 1f, origin, weapon.getShip().getVelocity());
                } else if (chargeLevel < 1f) {
						armaa_utils.createChargeParticle(chargeLevel, origin, weapon.getShip(), color, 3);
                } else {
                    firing = true;
						interval.advance(amount);
						int count = 1 + (int) (weapon.getChargeLevel() * 2);
						if(interval.intervalElapsed())
                        for (int i = 0; i < count; i++) 
						{
									float A_2 = CHARGEUP_PARTICLE_ANGLE_SPREAD / 2;
									float facing = weapon.getCurrAngle();
									float distance = MathUtils.getRandomNumberInRange(CHARGEUP_PARTICLE_DISTANCE_MIN+1f,
                                    CHARGEUP_PARTICLE_DISTANCE_MAX+1f)
                                    * weapon.getChargeLevel();
								// constant that effects the lower end of the particle velocity
							float VEL_MIN = 0.5f;
							// constant that effects the upper end of the particle velocity
							float VEL_MAX = 1f;
							//private WeaponAPI weapon;							
							float speed = 0.5f * distance / CHARGEUP_PARTICLE_DURATION*weapon.getChargeLevel();

							float angle = MathUtils.getRandomNumberInRange(facing - A_2,
									facing + A_2);
							float vel = MathUtils.getRandomNumberInRange(speed * -VEL_MIN,
									speed * -VEL_MAX);
							Vector2f vector = MathUtils.getPointOnCircumference(weapon.getShip().getVelocity(),
									vel,
									angle);

                            float size = MathUtils.getRandomNumberInRange(CHARGEUP_PARTICLE_SIZE_MIN+1f, CHARGEUP_PARTICLE_SIZE_MAX+3f)*weapon.getChargeLevel();
                           // float angle = MathUtils.getRandomNumberInRange(-0.5f * CHARGEUP_PARTICLE_ANGLE_SPREAD, 0.5f
                             //       * CHARGEUP_PARTICLE_ANGLE_SPREAD);
                           // Vector2f particleVelocity = MathUtils.getPointOnCircumference(shipVelocity, speed, angle + shipFacing
                                  //  + 180f);
                            engine.addHitParticle(origin, vector, size, CHARGEUP_PARTICLE_BRIGHTNESS * Math.min(
                                    weapon.getChargeLevel() + 0.5f, 1f)
                                            * MathUtils.getRandomNumberInRange(0.75f, 1.25f), CHARGEUP_PARTICLE_DURATION,
                                    color);
                        }
					
                }
            } else {
                if (cooling) {
                    if (chargeLevel <= 0f) {
                        cooling = false;
						//Global.getSoundPlayer().playSound("beamfire", 1f, 1f, origin, weapon.getShip().getVelocity());
                    }
                } else if (chargeLevel > level) {
					Global.getSoundPlayer().playSound("beamchargeM", .95f, 1.05f, origin, weapon.getShip().getVelocity());
                    charging = true;
                }
            }
            level = chargeLevel;
	}
	
	
}
