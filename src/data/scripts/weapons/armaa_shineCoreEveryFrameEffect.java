package data.scripts.weapons;
import com.fs.starfarer.api.util.IntervalUtil;
import java.awt.Color;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.combat.*;

import org.lwjgl.util.vector.ReadableVector2f;
import org.lwjgl.util.vector.Vector2f;


public class armaa_shineCoreEveryFrameEffect implements EveryFrameWeaponEffectPlugin, OnFireEffectPlugin, OnHitEffectPlugin
{
    private boolean runOnce = false;
    private boolean hasFired = false;
	public float TURRET_OFFSET = 45f;
    private final IntervalUtil particle = new IntervalUtil(0.025f, 0.05f);
	private final IntervalUtil particle2 = new IntervalUtil(.1f, .2f);
	
	float charge = 0f;
	private static final int SMOKE_SIZE_MIN = 10;
    private static final int SMOKE_SIZE_MAX = 30;
		private boolean soundPlayed = false;
    private boolean windingUp = true;
	
    public void init(ShipAPI ship)
    {

    }

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) 
	{
		TURRET_OFFSET = weapon.getSpec().getTurretFireOffsets().get(0).x;
		float OFFSET_Y = weapon.getSpec().getTurretFireOffsets().get(0).y;
		ShipAPI ship = weapon.getShip();
		if(weapon.getChargeLevel() > 0)
		{
			windingUp = true;
			
		}
		
		else
		{
				windingUp  = false;
				soundPlayed = false;
		}
		
		if(!soundPlayed && windingUp)
		{
			Global.getSoundPlayer().playSound("mechmove", 1f, 1f, weapon.getLocation(), weapon.getShip().getVelocity());
			soundPlayed = true;
		}
		
        if (weapon.isFiring() && charge != 1f) {
            float charge = weapon.getChargeLevel();
			float size = 2;
			if(weapon.getSize() ==  WeaponAPI.WeaponSize.LARGE)
				size = 2;
            if (!hasFired) {
				
                    Global.getSoundPlayer().playLoop("beamchargeM", (Object)weapon, 1.0f, 1.0f, weapon.getLocation(), weapon.getShip().getVelocity());
                    particle.advance(amount);
                if (particle.intervalElapsed()) {
					float targetShipAngle = weapon.getShip().getFacing()-180;
					CombatUtils.applyForce(weapon.getShip(),targetShipAngle, 10f*weapon.getChargeLevel());
					Vector2f origin = new Vector2f(weapon.getLocation());

                    Vector2f loc = MathUtils.getPoint((Vector2f)weapon.getLocation(), (float)18.5f, (float)weapon.getCurrAngle());
                    Vector2f vel = weapon.getShip().getVelocity();
                    engine.addHitParticle(origin, vel, MathUtils.getRandomNumberInRange((float)20.0f*size, (float)(charge*size * 60.0f + 20.0f)), MathUtils.getRandomNumberInRange((float)0.5f, (float)(0.5f + charge)), MathUtils.getRandomNumberInRange((float)0.1f, (float)(0.1f + charge / 10.0f)), new Color(charge / 1.0f, charge / 2f, charge/1f));
					engine.addSwirlyNebulaParticle(origin, new Vector2f(0.0f, 0.0f),MathUtils.getRandomNumberInRange((float)20.0f*size, (float)(charge*size * 60.0f + 20.0f)), 1.2f, 0.15f, 0.0f, 0.35f*charge, new Color(charge / 1.0f, charge / 3.0f, charge/1.0f,0.5f*charge),false);
					weapon.getShip().setJitter(weapon.getShip(), new Color(255f/255f,68f/255f,204f/255f,weapon.getChargeLevel()*0.8f), weapon.getChargeLevel(), 4, 25);					
					Vector2f particleVel = MathUtils.getRandomPointInCircle((Vector2f)new Vector2f(), (float)(35.0f * charge));
					Vector2f particleLoc = new Vector2f();
					Vector2f.sub((Vector2f)origin, (Vector2f)new Vector2f((ReadableVector2f)particleVel), (Vector2f)particleLoc);
					Vector2f.add((Vector2f)vel, (Vector2f)particleVel, (Vector2f)particleVel);
					//if((float) Math.random() <= 0.5f)	
					//MagicLensFlare.createSharpFlare(engine, weapon.getShip(), particleLoc, 2, 6, 0, new Color(50f/255f, 150f/255f, 1f,1f*charge), new Color(1f, 1f, 1f,1f*charge));

                    for (int i = 0; i < 5; ++i) {
						 engine.addHitParticle(particleLoc, particleVel, MathUtils.getRandomNumberInRange((float)20.0f, (float)(charge * 20.0f + 20.0f)), MathUtils.getRandomNumberInRange((float)0.5f, (float)(0.5f + charge)), MathUtils.getRandomNumberInRange((float)0.75f, (float)(0.75f + charge / 4.0f)), new Color(charge / 1.0f, charge / 3.0f, charge/1.0f));
                    }
                }
            }
            if (charge == 1.0f && !hasFired) 
			{
				Global.getSoundPlayer().playSound("explosion_from_damage",1.0f, 1.0f, weapon.getLocation(), weapon.getShip().getVelocity());
				float targetShipAngle = weapon.getShip().getFacing();
				CombatUtils.applyForce(weapon.getShip(),targetShipAngle, 1000f);
				engine.spawnExplosion(weapon.getLocation(), new Vector2f(), new Color(255,0,255), 200f, 1f);
				engine.addSmoothParticle(weapon.getLocation(), new Vector2f(0,0), 300, 2, 0.1f, Color.white);
				engine.addHitParticle(weapon.getLocation(), new Vector2f(0,0), 300, 1, 0.4f, new Color(200,100,255));
				engine.spawnExplosion(weapon.getLocation(), new Vector2f(0,0), Color.DARK_GRAY, 125, 2);
				engine.spawnExplosion(weapon.getLocation(), new Vector2f(0,0), Color.BLACK, 60*5, 3);
                hasFired = true;
            }
        } else {
            hasFired = false;
		}		
    }
	public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) 
	{


	}
	
	//@Override
	public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine)  
	{
		
	}

}
