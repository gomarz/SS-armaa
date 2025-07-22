package data.scripts.weapons;

import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.BeamEffectPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lazywizard.lazylib.VectorUtils;

import org.magiclib.util.MagicRender;

import java.awt.Color;
import org.lazywizard.lazylib.MathUtils;


public class armaa_fluxBeam implements BeamEffectPlugin {

	private IntervalUtil fireInterval = new IntervalUtil(0.2f, 0.3f);
	private boolean wasZero = true;
	
	private final Vector2f ZERO = new Vector2f();
    private float CHARGEUP_PARTICLE_ANGLE_SPREAD = 150f;
	private final float A_2 = CHARGEUP_PARTICLE_ANGLE_SPREAD / 2;
    private float CHARGEUP_PARTICLE_BRIGHTNESS = 1f;
    private float CHARGEUP_PARTICLE_DISTANCE_MAX = 150f;
    private float CHARGEUP_PARTICLE_DISTANCE_MIN = 100f;
    private float CHARGEUP_PARTICLE_DURATION = 0.5f;
    private float CHARGEUP_PARTICLE_SIZE_MAX = 5f;
    private float CHARGEUP_PARTICLE_SIZE_MIN = 1f;
    public float TURRET_OFFSET = 20f;
	//sliver cannon charging fx
    private boolean charging = false;
    private boolean cooling = false;
    private boolean firing = false;
    private final IntervalUtil interval = new IntervalUtil(0.015f, 0.015f);
    private final IntervalUtil interval2 = new IntervalUtil(0.075f, 0.075f);
    private float level = 0f;
	    // constant that effects the lower end of the particle velocity
    private final float VEL_MIN = 1f;
    // constant that effects the upper end of the particle velocity
    private final float VEL_MAX = 1.5f;
	//private WeaponAPI weapon;
	private boolean runOnce = false;
	
	//beam particles
	private static final Color PARTICLE_COLOR = new Color(200, 200, 200, 255);
    private static final float PARTICLE_SIZE_MIN = 5f;
    private static final float PARTICLE_SIZE_MAX = 10f;
    private static final float PARTICLE_DURATION_MIN = 0.4f;
    private static final float PARTICLE_DURATION_MAX = 0.9f;
    private static final float PARTICLE_INERTIA_MULT = 0.5f; //This is how much the particles retain their spawning ship's velocity; 0f means no retained velocity, 1f means full retained velocity.
    private static final float PARTICLE_DRIFT = 50f; //This is how much the particles "move" in a random direction when spawned, at most; their actual speed is between 0 and this value (plus any inertia, see above)
    private static final float PARTICLE_DENSITY = 0.05f; //Measured in particles per SU^2 (area unit) and second; lower means less particles. This is multiplied by charge level, too
    private static final float PARTICLE_SPAWN_WIDTH_MULT = 0.5f; //Multiplier for how wide the particles are allowed to spawn from the beam center; at 1f, it's equal to beam width, at 0f they only spawn in the center. Note that true beam width is usually much bigger than the visual beam width

	
	public void advance(float amount, CombatEngineAPI engine, BeamAPI beam) 
	{
		CombatEntityAPI target = beam.getDamageTarget();
		WeaponAPI weapon = beam.getWeapon();
		float beamWidth = beam.getWidth();
		ShipAPI ship = weapon.getShip();

		
		if(weapon.getChargeLevel() >= 1f)
		{
				if (!runOnce)
                runOnce = true;
			
		}
		if(!MagicRender.screenCheck(0.2f, weapon.getLocation()) || Global.getCombatEngine().isPaused())
		{
			return;
		}
		if (target instanceof CombatEntityAPI) 
		{
			float dur = beam.getDamage().getDpsDuration();
			// needed because when the ship is in fast-time, dpsDuration will not be reset every frame as it should be
			if (!wasZero) dur = 0;
			wasZero = beam.getDamage().getDpsDuration() <= 0;
			fireInterval.advance(dur);

				Vector2f origin = new Vector2f(weapon.getLocation());
				Vector2f offset = new Vector2f(TURRET_OFFSET, -0f);
				VectorUtils.rotate(offset, weapon.getCurrAngle(), offset);
				Vector2f.add(offset, origin, origin);

				float shipFacing = weapon.getCurrAngle();
				Vector2f shipVelocity = weapon.getShip().getVelocity();
				Vector2f dir = Vector2f.sub(beam.getTo(), beam.getFrom(), new Vector2f());
				if (dir.lengthSquared() > 0) dir.normalise();
				dir.scale(50f);
				Vector2f point = Vector2f.sub(beam.getTo(), dir, new Vector2f());
			if(weapon.isFiring() || (weapon.getChargeLevel() > 0f))
			{
				float radius = 30f + (weapon.getChargeLevel() * weapon.getChargeLevel()* MathUtils.getRandomNumberInRange(25f, 75f));
				//Color color = new Color(200, 200, 200, 255);
				
				float facing = beam.getWeapon().getCurrAngle();
					if((float) Math.random() <= 0.5f)
					{
						int count = 1 + (int) (weapon.getChargeLevel() * 3);
                        for (int i = 0; i < count; i++) 
						{
							Color color = Math.random() <= 0.75f ? beam.getFringeColor() : new Color(255, 255, 200, 255);
									float distance = MathUtils.getRandomNumberInRange(CHARGEUP_PARTICLE_DISTANCE_MIN+1f,
                                    CHARGEUP_PARTICLE_DISTANCE_MAX+1f)
                                    * weapon.getChargeLevel();
							
							float speed = 0.75f * distance / CHARGEUP_PARTICLE_DURATION*weapon.getChargeLevel();

							float angle = MathUtils.getRandomNumberInRange(facing - A_2,
									facing + A_2);
							float vel = MathUtils.getRandomNumberInRange(speed * -VEL_MIN,
									speed * -VEL_MAX);
							Vector2f vector = MathUtils.getPointOnCircumference(null,
									vel,
									angle);

                            float size = MathUtils.getRandomNumberInRange(CHARGEUP_PARTICLE_SIZE_MIN+1f, CHARGEUP_PARTICLE_SIZE_MAX+5f)*weapon.getChargeLevel();
                           // float angle = MathUtils.getRandomNumberInRange(-0.5f * CHARGEUP_PARTICLE_ANGLE_SPREAD, 0.5f
                             //       * CHARGEUP_PARTICLE_ANGLE_SPREAD);
                           // Vector2f particleVelocity = MathUtils.getPointOnCircumference(shipVelocity, speed, angle + shipFacing
                                  //  + 180f);
                            engine.addHitParticle(beam.getTo(), vector, size, CHARGEUP_PARTICLE_BRIGHTNESS * Math.min(
                                    weapon.getChargeLevel() + 0.5f, 1f)
                                            * MathUtils.getRandomNumberInRange(0.75f, 1.25f), CHARGEUP_PARTICLE_DURATION,
                                    new Color(color.getRed()/255,color.getGreen()/255,color.getBlue()/255,(color.getAlpha()/255)*weapon.getChargeLevel()));
                        }
					}
	
				if(Math.random() <= 0.05f)
				{
					engine.addHitParticle(beam.getTo(), ZERO, (radius*1.75f), 0.1f + weapon.getChargeLevel() * 0.3f, 0.2f, beam.getCoreColor());
				}
			}

			if (cooling) 
			{
				if (weapon.getChargeLevel() <= 0f) 
				{
					cooling = false;
					//Global.getSoundPlayer().playSound("beamfire", 1f, 1f, origin, weapon.getShip().getVelocity());
				}
			} 
		}
            level = weapon.getChargeLevel();
			
			//yoinked from LoA
        if (runOnce) 
		{
            //Calculate how many particles should be spawned this frame
            float particleCount = beamWidth * PARTICLE_SPAWN_WIDTH_MULT * MathUtils.getDistance(beam.getTo(), beam.getFrom()) * amount * PARTICLE_DENSITY * weapon.getChargeLevel();

            //Generate the particles and assign them their random characteristics
			if(Math.random() <0.5f)
            for (int i = 0; i < particleCount; i++) 
			{
                //First, get a random point on the beam's line
                Vector2f spawnPoint = MathUtils.getRandomPointOnLine(beam.getFrom(), beam.getTo());

                //Then, we offset that depending on width
                spawnPoint = MathUtils.getRandomPointInCircle(spawnPoint, beamWidth * PARTICLE_SPAWN_WIDTH_MULT);

                //If this point is too far off-screen, we go on to the next particle instead
                if (!Global.getCombatEngine().getViewport().isNearViewport(spawnPoint, PARTICLE_SIZE_MAX * 3f)) 
				{
                    continue;
                }

                //After that, we calculate our velocity
                Vector2f velocity = new Vector2f(ship.getVelocity().x*PARTICLE_INERTIA_MULT, ship.getVelocity().y*PARTICLE_INERTIA_MULT);
                velocity = MathUtils.getRandomPointInCircle(velocity, PARTICLE_DRIFT);
				/*
					if((float) Math.random() <= 0.05f)
					{
						MagicLensFlare.createSharpFlare(
							engine,
							beam.getSource(),
							spawnPoint,
							4,
							150,
							beam.getWeapon().getCurrAngle(),
							new Color (255,255,255,150),
							new Color(255,255,255,255)
						);
					}
				*/
                //And finally spawn the particle
                engine.addSmoothParticle(spawnPoint, velocity, MathUtils.getRandomNumberInRange(PARTICLE_SIZE_MIN, PARTICLE_SIZE_MAX), weapon.getChargeLevel(),
                        MathUtils.getRandomNumberInRange(PARTICLE_DURATION_MIN, PARTICLE_DURATION_MAX), PARTICLE_COLOR);
            }
        }
	}
}