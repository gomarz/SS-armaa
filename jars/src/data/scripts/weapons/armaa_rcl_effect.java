package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lwjgl.util.vector.Vector2f;
import org.lazywizard.lazylib.combat.CombatUtils;
import java.util.ArrayList;
import java.util.List;
import java.awt.Color;
import com.fs.starfarer.api.util.Misc;

import org.lazywizard.lazylib.VectorUtils;
import org.magiclib.util.MagicRender;
import org.magiclib.util.MagicLensFlare;
import org.lazywizard.lazylib.MathUtils;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.util.IntervalUtil;

public class armaa_rcl_effect implements EveryFrameWeaponEffectPlugin, OnFireEffectPlugin, OnHitEffectPlugin {

	private float count = 1.5f;
	private boolean recoiling = false;
	private IntervalUtil recoilTracker = new IntervalUtil(1f,1f);
    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) 
	{
		if(recoiling && count > 0f)
		{
			count-= amount;
			weapon.getShip().setFacing(weapon.getShip().getFacing()-count);	
		}

		if(count <= 0f)
		{
			count = 1.5f;
			recoiling = false;
		}		
				
    }
	
	public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) 
	{
		
		if(weapon.getChargeLevel() == 1)
		{
			if(!recoiling)
			{
				recoiling = true;
			}
			CombatUtils.applyForce(weapon.getShip(),weapon.getShip().getFacing()-180f,75f);
		}
		
		float angle = 180+weapon.getCurrAngle();
		Vector2f point= MathUtils.getPoint(weapon.getFirePoint(0), 45, angle);
		
		//smoke
		for(int i=0; i<20; i++){                    
			Vector2f vel = MathUtils.getRandomPointInCone(new Vector2f(), 100, angle-20, angle+20);
			vel.scale((float)Math.random());
			Vector2f.add(vel, weapon.getShip().getVelocity(), vel);
			float grey = MathUtils.getRandomNumberInRange(0.5f, 0.75f);
			engine.addSmokeParticle(
					MathUtils.getRandomPointInCircle(point, 5),
					vel,
					MathUtils.getRandomNumberInRange(5, 20),
					MathUtils.getRandomNumberInRange(0.25f, 0.75f),
					MathUtils.getRandomNumberInRange(0.25f, 1f),
					new Color(grey,grey,grey,MathUtils.getRandomNumberInRange(0.25f, 0.75f))
			);
		}
		//debris
		for(int i=0; i<10; i++){                    
			Vector2f vel = MathUtils.getRandomPointInCone(new Vector2f(), 250, angle-20, angle+20);
			Vector2f.add(vel, weapon.getShip().getVelocity(), vel);
			engine.addHitParticle(
					point,
					vel,
					MathUtils.getRandomNumberInRange(2, 4),
					1,
					MathUtils.getRandomNumberInRange(0.05f, 0.25f),
					new Color(255,125,50)
			);
		}
		//flash
			Vector2f vel = MathUtils.getRandomPointInCone(new Vector2f(), 100, angle-20, angle+20);
			vel.scale((float)Math.random());
			Vector2f.add(vel, weapon.getShip().getVelocity(), vel);
		engine.addHitParticle(
				point,
				vel,
				100,
				0.5f,
				0.5f,
				new Color(255,20,10)
		);
		engine.addHitParticle(
				point,
				vel,
				80,
				0.75f,
				0.15f,
				new Color(255,200,50)
		);
		engine.addHitParticle(
				point,
				vel,
				50,
				1,
				0.05f,
				new Color(255,200,150)
		);

		point= MathUtils.getPoint(weapon.getFirePoint(0), 0, angle);
		//flash
		engine.addHitParticle(
				point,
				projectile.getVelocity(),
				100,
				0.5f,
				0.5f,
				new Color(255,20,10)
		);
		engine.addHitParticle(
				point,
				projectile.getVelocity(),
				80,
				0.75f,
				0.15f,
				new Color(255,200,50)
		);
		engine.addHitParticle(
				point,
				projectile.getVelocity(),
				50,
				1,
				0.05f,
				new Color(255,200,150)
		);
	}
		
	@Override
	public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine)  
	{

		engine.addNebulaSmokeParticle(projectile.getLocation(),
			new Vector2f(),
			 40f * (0.75f + (float) Math.random() * 0.5f),
			5f + 3f * Misc.getHitGlowSize(75f, projectile.getDamage().getBaseDamage(), damageResult) / 100f,
			0f,
			0f,
			1f,
			new Color(175,100,100,255));
		if(MagicRender.screenCheck(0.2f, point))
		{
			// Spawn visual effects
			//flash
			engine.addSmoothParticle(
					point,
					new Vector2f(),
					200,
					2,
					0.30f,
					Color.WHITE
			);
			engine.addSmoothParticle(
					point,
					new Vector2f(),
					200,
					2,
					0.20f,
					Color.WHITE
			);
			engine.addSmoothParticle(
					point,
					new Vector2f(),
					300,
					2,
					0.1f,
					Color.WHITE
			);
			MagicLensFlare.createSmoothFlare(
					engine,
					projectile.getSource(),
					point, 
					15, 
					400,
					0,
					new Color(255,150,75),
					Color.DARK_GRAY
			);
			
			//cloud
			for(int i=0; i<30; i++)
			{
				int grey=MathUtils.getRandomNumberInRange(20, 100);
				engine.addSmokeParticle(
						MathUtils.getRandomPointInCircle(point, 40),
						MathUtils.getRandomPointInCone(
								new Vector2f(),
								30,
								projectile.getFacing()+90,
								projectile.getFacing()+270
						),
						MathUtils.getRandomNumberInRange(30, 60),
						1,
						MathUtils.getRandomNumberInRange(2, 5),
						new Color(
								(int)grey/10,
								(int)grey,
								(int)(grey/MathUtils.getRandomNumberInRange(1.5f, 2)),
								MathUtils.getRandomNumberInRange(8, 32)
						)
				);
			}
			
			for (int x = 0; x < 15; x++) 
			{
				engine.addHitParticle(
						point, 
						MathUtils.getRandomPointInCone(new Vector2f(), x*10, projectile.getFacing()+90, projectile.getFacing()+270),
						5f,
						1f,
						2-(x/10),
						new Color(255,125,20,200));
			}
		} 		
						
    }   
}