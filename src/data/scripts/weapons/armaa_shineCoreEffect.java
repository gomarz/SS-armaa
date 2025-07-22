package data.scripts.weapons;
import com.fs.starfarer.api.util.IntervalUtil;
import java.awt.Color;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.util.vector.ReadableVector2f;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicRender;
import org.magiclib.util.MagicLensFlare;
import com.fs.starfarer.api.loading.DamagingExplosionSpec;


public class armaa_shineCoreEffect implements  BeamEffectPlugin
{
    private boolean runOnce = false;
    private boolean hasFired = false;
	public float TURRET_OFFSET = 45f;
    private final IntervalUtil particle = new IntervalUtil(0.025f, 0.05f);
	private final IntervalUtil particle2 = new IntervalUtil(.1f, .2f);
	private static final Color PARTICLE_COLOR = new Color(250, 50, 255, 174);
    private static final Color BLAST_COLOR = new Color(255, 255, 26, 255);
    private static final Color CORE_COLOR = new Color(200, 200, 255);
    private static final Color FLASH_COLOR = new Color(152, 225, 255);
    private static final int NUM_PARTICLES = 15;
	private boolean firstStrike = false;	
	float charge = 0f;
	private static final int SMOKE_SIZE_MIN = 10;
    private static final int SMOKE_SIZE_MAX = 30;
	private List<CombatEntityAPI> targets = new ArrayList<CombatEntityAPI>();
	private List<CombatEntityAPI> hitTargets = new ArrayList<CombatEntityAPI>();	
    public void init(ShipAPI ship)
    {

    }

    @Override
    public void advance(float amount, CombatEngineAPI engine, BeamAPI beam) 
	{
		WeaponAPI weapon = beam.getWeapon();
		CombatEntityAPI target = beam.getDamageTarget();
		if(!targets.contains(target))
		{
			targets.add(target);
		}

		if(weapon.isFiring())
		{
			weapon.getShip().setCollisionClass(CollisionClass.SHIP);
			for(CombatEntityAPI enemy:targets)
			{
				if(enemy == beam.getDamageTarget())
				{
					if(hitTargets.contains(beam.getDamageTarget()))
					{
						continue;
					}
					
					else
					{
						boolean softFlux = 	true;
						
						if(weapon.getDamage().isForceHardFlux() || weapon.getShip().getVariant().getHullMods().contains("high_scatter_amp"))
						{
							softFlux = false;
						}

						float dmg = weapon.getDamage().getDamage()*weapon.getSpec().getBurstDuration();
						
						float mag = weapon.getShip().getFluxBasedEnergyWeaponDamageMultiplier() - 1f;
						if(mag > 0)
							dmg = dmg*(1+mag);
						
						engine.applyDamage(enemy, beam.getTo(),dmg,weapon.getDamageType(), dmg, false, softFlux, weapon.getShip()); 
						hitTargets.add(enemy);
						if (enemy instanceof ShipAPI)
						{
							ShipAPI empTarget = (ShipAPI)enemy;
							for(int i = 0; i < 2; i++)
							{
							EmpArcEntityAPI arc =  engine.spawnEmpArc(weapon.getShip(), beam.getTo(), weapon.getShip(), empTarget,
								DamageType.ENERGY, dmg/2, dmg/2, 10000f, null, 10f, Color.yellow, Color.white);
							}
						}
					}
				}
			}
		}

       // if(weapon.isFiring() && weapon.getChargeLevel() > 0f && weapon.getChargeLevel() < 1f || weapon.getChargeLevel() == 1f) 
	//	{
            float charge = weapon.getChargeLevel();
			float size = 2;
			if(weapon.getSize() ==  WeaponAPI.WeaponSize.LARGE)
				size = 2;
            if (!hasFired) {
		                    particle.advance(amount);
                if (particle.intervalElapsed()) {
					
					Vector2f origin = new Vector2f(weapon.getLocation());
					//Vector2f offset = new Vector2f(TURRET_OFFSET,OFFSET_Y);
					//VectorUtils.rotate(offset, weapon.getCurrAngle(), offset);
					//Vector2f.add(offset, origin, origin);
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

					engine.addHitParticle(particleLoc, particleVel, MathUtils.getRandomNumberInRange((float)2.0f, (float)(charge * 2.0f + 2.0f)), MathUtils.getRandomNumberInRange((float)0.5f, (float)(0.5f + charge)), MathUtils.getRandomNumberInRange((float)0.75f, (float)(0.75f + charge / 4.0f)), new Color(charge / 1.0f, charge / 3.0f, charge/1.0f));
                }
            //}
            if (charge == 1.0f) 
			{
				float targetShipAngle = weapon.getShip().getFacing();
				//CombatUtils.applyForce(weapon.getShip(),targetShipAngle, 600f);
                hasFired = true;
            }
        } 
		else 
		{
			hasFired = false;
		}		
		//CombatEntityAPI target = beam.getDamageTarget();

		if(target instanceof CombatEntityAPI)
		{
			if(!firstStrike)
			{
				Global.getSoundPlayer().playSound("explosion_ship",1.0f, 1.0f, weapon.getLocation(), weapon.getShip().getVelocity());
				firstStrike = true;
			}
			particle.advance(amount);
				float targetShipAngle = weapon.getShip().getFacing()-180;
				CombatUtils.applyForce(weapon.getShip(),targetShipAngle, 1200f);
			if (particle.intervalElapsed()) 
			{
				engine.spawnExplosion(beam.getTo(), new Vector2f(), PARTICLE_COLOR, 100f, 1f);
				engine.spawnExplosion(beam.getTo(), new Vector2f(), CORE_COLOR, 50f, 1f);
				MagicLensFlare.createSharpFlare(engine, beam.getSource(), beam.getTo(), 8, 400, 0, new Color(186, 240, 255), new Color(255, 255, 255));
				float bonusDamage = beam.getDamage().getDamage()/2f;
				DamagingExplosionSpec blast = new DamagingExplosionSpec(0.1f,
						75f,
						50f,
						bonusDamage,
						bonusDamage/2f,
						CollisionClass.PROJECTILE_FF,
						CollisionClass.PROJECTILE_FIGHTER,
						10f,
						10f,
						0f,
						0,
						BLAST_COLOR,
						null);
				blast.setDamageType(DamageType.ENERGY);
				blast.setShowGraphic(false);
				//WeaponAPI weapon = beam.getWeapon();
				engine.spawnDamagingExplosion(blast,beam.getSource(),beam.getTo(),false);
				if(MagicRender.screenCheck(0.2f, beam.getTo()))
				{				
					engine.addSmoothParticle(beam.getTo(), new Vector2f(0,0), 200, 2, 0.1f, Color.white);
					engine.addSmoothParticle(beam.getTo(), new Vector2f(), 400f, 0.5f, 0.1f, PARTICLE_COLOR);
					engine.addHitParticle(beam.getTo(), new Vector2f(), 200f, 0.5f, 0.25f, FLASH_COLOR);
					for (int x = 0; x < NUM_PARTICLES; x++) 
					{
						engine.addHitParticle(beam.getTo(),
								MathUtils.getPointOnCircumference(null, MathUtils.getRandomNumberInRange(150f, 300f), (float) Math.random() * 360f),
								5f, 1f, MathUtils.getRandomNumberInRange(0.6f, 1f), PARTICLE_COLOR);
					}	
				}
			}			
			
			
			//float targetShipAngle = weapon.getShip().getFacing()-180f;
			//CombatUtils.applyForce(weapon.getShip(),targetShipAngle, 400f);

		}
	}
}
