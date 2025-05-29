package data.scripts.weapons;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import java.awt.Color;
import com.fs.starfarer.api.Global;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.ReadableVector2f;



public class armaa_sylphAMWSEffect implements EveryFrameWeaponEffectPlugin 
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
    private float level = 0f;
	
	private boolean runOnce = false;
    private boolean hasFired = false;
	//public float TURRET_OFFSET = 45f;
    private final IntervalUtil particle = new IntervalUtil(0.025f, 0.05f);
	private final IntervalUtil particle2 = new IntervalUtil(.1f, .2f);
	
	float charge = 0f;
	private static final int SMOKE_SIZE_MIN = 30;
    private static final int SMOKE_SIZE_MAX = 60;
	
	private boolean isAnimating = false;
	private IntervalUtil interval = new IntervalUtil(1f,20f);
	private IntervalUtil animInterval = new IntervalUtil(0.06f,0.08f);
	private int target = 0;
	private int currFrame = 0;

	public int getAngle(int a, int b)
	{
		int angle = a + b;
		if(angle > 360)
			angle%= 360;
		
		return angle;
	}

   
    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) 
	{
		ShipAPI ship = weapon.getShip();
		Color color = new Color(247, 176, 52, 255);
		Color color2 = new Color(0, 226, 188, 255);	

		if(weapon.getBeams() != null && !weapon.getBeams().isEmpty())
		{
			color = weapon.getBeams().get(0).getCoreColor();
			color = weapon.getBeams().get(0).getFringeColor();
		}
		
		if(engine.isPaused() || weapon.getShip().getOriginalOwner()==-1 || ship.getFluxTracker().isOverloaded() || weapon.isDisabled())
			return;
			
		TURRET_OFFSET = weapon.getSpec().getTurretFireOffsets().get(0).x;
		float OFFSET_Y = weapon.getSpec().getTurretFireOffsets().get(0).y;

        if (weapon.isFiring()) {
            float charge = weapon.getChargeLevel();
			float size = 1;
			if(weapon.getSize() ==  WeaponAPI.WeaponSize.LARGE)
				size = 3;
            if (!hasFired) {
				
                    Global.getSoundPlayer().playLoop("beamchargeMeso", (Object)weapon, 0.8f, 1.0f, weapon.getLocation(), weapon.getShip().getVelocity());
                    particle.advance(amount);
                if (particle.intervalElapsed()) {
					
					Vector2f origin = new Vector2f(weapon.getLocation());
					Vector2f offset = new Vector2f(TURRET_OFFSET,OFFSET_Y);
					VectorUtils.rotate(offset, weapon.getCurrAngle(), offset);
					Vector2f.add(offset, origin, origin);
                    Vector2f loc = MathUtils.getPoint((Vector2f)weapon.getLocation(), (float)18.5f, (float)weapon.getCurrAngle());
                    Vector2f vel = weapon.getShip().getVelocity();
                    engine.addHitParticle(origin, vel, MathUtils.getRandomNumberInRange((float)30.0f*size, (float)(charge*size * 30.0f)), MathUtils.getRandomNumberInRange((float)0.5f, (float)(0.5f + charge)), MathUtils.getRandomNumberInRange((float)0.1f, (float)(0.1f + charge / 10.0f)), new Color(charge, charge, charge/10f));
				
					Vector2f particleVel = MathUtils.getRandomPointInCircle((Vector2f)new Vector2f(), (float)(35.0f * charge));
					Vector2f particleLoc = new Vector2f();
					Vector2f.sub((Vector2f)origin, (Vector2f)new Vector2f((ReadableVector2f)particleVel), (Vector2f)particleLoc);
					Vector2f.add((Vector2f)vel, (Vector2f)particleVel, (Vector2f)particleVel);

                    for (int i = 0; i < 3; ++i) {
						 engine.addHitParticle(particleLoc, particleVel, MathUtils.getRandomNumberInRange((float)1.0f, (float)(charge * 3.0f)), MathUtils.getRandomNumberInRange((float)0.5f, (float)(0.5f + charge)), MathUtils.getRandomNumberInRange((float)0.75f, (float)(0.75f + charge / 4.0f)), new Color(charge, charge, charge/10f));
                    }
                }
            }
            if (charge == 1.0f) 
			{
				Vector2f origin = new Vector2f(weapon.getLocation());
				Vector2f offset = new Vector2f(TURRET_OFFSET,OFFSET_Y);
				VectorUtils.rotate(offset, weapon.getCurrAngle(), offset);
				Vector2f.add(offset, origin, origin);
				Vector2f loc = MathUtils.getPoint((Vector2f)weapon.getLocation(), (float)18.5f, (float)weapon.getCurrAngle());
				Vector2f vel = weapon.getShip().getVelocity();
                hasFired = true;
				 engine.addHitParticle(origin, vel, MathUtils.getRandomNumberInRange((float)20.0f*size, (float)(charge*size * 20.0f)), MathUtils.getRandomNumberInRange((float)0.5f, (float)(0.5f + charge)), MathUtils.getRandomNumberInRange((float)0.1f, (float)(0.1f + charge / 10.0f)), new Color(charge, charge, charge/10f));
            }
        } else {
            hasFired = false;
		}
		//animate head
		if(interval.intervalElapsed() && !isAnimating)
		{

			isAnimating = true;
			
			currFrame = weapon.getAnimation().getFrame();
			target = currFrame <= 4 ? 8 : 0;
			animInterval = new IntervalUtil(0.055f,0.08f);
			if(weapon.getShip().getShipTarget() != null)
			{
				float facing = weapon.getCurrAngle();
				float targetAngle = VectorUtils.getAngle(weapon.getShip().getLocation(),weapon.getShip().getShipTarget().getLocation());
				
				float difference = Math.abs(facing - targetAngle);

				float increments = 4;
				if (facing > targetAngle) 
				{
					target = (int)Math.floor(Math.min(5f+ (difference / 25f),8f));			
				} 
				else if (facing < targetAngle) 
				{
					target = (int)Math.floor(Math.max(3f- (difference / 25f),0f));
				}
			}
			else if(Math.random() <= 0.25f)
			{
				if(target == 8)
					target = MathUtils.getRandomNumberInRange(4, 6);
				
				else
					target = MathUtils.getRandomNumberInRange(0,4);
			}
			if(Math.random() <= 0.25f)
			{
				Global.getSoundPlayer().playSound("armaa_monoeye_move", 0.8f, 0.8f, weapon.getLocation(), weapon.getShip().getVelocity());				
			}
		}
		
		if(!isAnimating)
		{
			interval.advance(amount);
		}
		
		else
		{
			animInterval.advance(amount);
			if(animInterval.intervalElapsed())
			{
				currFrame = weapon.getAnimation().getFrame();
				
				if(currFrame == target)
				{
					interval = new IntervalUtil(5f,15f);
					isAnimating = false;
				}
				
				else
				{
					if(currFrame < target)
						weapon.getAnimation().setFrame(Math.min(currFrame+1,8));
					
					else
						weapon.getAnimation().setFrame(Math.max(0,currFrame-1));
				}
				
			}
		}
    }
}
