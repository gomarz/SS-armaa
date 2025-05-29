package data.scripts.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lazywizard.lazylib.combat.entities.SimpleEntity;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicLensFlare;
import org.magiclib.util.MagicRender;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import java.awt.Color;
import data.hullmods.armaa_KarmaMod;
import com.fs.starfarer.api.loading.DamagingExplosionSpec;
import data.scripts.MechaModPlugin;
import org.lazywizard.lazylib.MathUtils;


public class armaa_karma extends BaseShipSystemScript {
	private ShipAPI ship;
	private final Color PARTICLE_COLOR = new Color(100, 166, 255);
    private final float PARTICLE_SIZE = 10f;
    private final float PARTICLE_BRIGHTNESS = 150f;
    private final float PARTICLE_DURATION = 1f;
	private final float EXPLOSION_SIZE_OUTER = 350f;
    private  final float EXPLOSION_SIZE_INNER = 150f;
    private final float EXPLOSION_DAMAGE_MAX = 500f;
    private final float EXPLOSION_DAMAGE_MIN = 1f;
    private final float EXPLOSION_DURATION = 0.05f;
    private final float PARTICLE_DURATION2 = 0.2f;
	private final Color VFX_COLOR = new Color(149, 206, 240, 200);
    private final int PARTICLE_COUNT = 50;
    private final int PARTICLE_SIZE_MIN = 8;
    private final int PARTICLE_SIZE_RANGE = 3;
	private final Vector2f ZERO = new Vector2f();

	private float MIN_SPEED = 5;
	private float MAX_SPEED = 10;
	private float BASE_INCREMENT = 36; //the number of times this increment can be added up to 360 = # projectiles spawned 
	
    private final Color GLOW_COLOUR = new Color(254, 205, 255, 255);
    private final Color MUZZLE_GLOW_COLOUR_EXTRA = new Color(182, 22, 235, 255);
	private final float MUZZLE_GLOW_SIZE = 95f;
  
	public boolean activated = true;

	public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) 
	{

		if (stats.getEntity() instanceof ShipAPI) {
			ship = (ShipAPI) stats.getEntity();
		} else {
			return;
		}
			CombatEngineAPI engine = Global.getCombatEngine();
			
        if (state == State.IN) 
		{
            activated = false;
        }
		WeaponAPI maingun = null;
		for(WeaponAPI weapon : ship.getAllWeapons())
		{
			if(weapon.getSlot().getId().equals("E_RARM"))
			{
				maingun = weapon;
			}
		}
		
		if(engine.getCustomData().get("armaa_karmaTotal"+ship.getId()) instanceof Float)
		{
			float value = (float)engine.getCustomData().get("armaa_karmaTotal"+ship.getId());
	
			if(value >= 1f)
			{
				if(!ship.getVariant().getHullSpec().getHullId().equals("armaa_altagrave_c"))
				{
					MissileAPI boom = (MissileAPI)engine.spawnProjectile(ship,maingun,"armaa_karmicburst",maingun.getLocation(),0,ship.getVelocity());
					
					float time = 1.25f;
					/*
					if(Global.getSettings().getModManager().isModEnabled("shaderLib"))
					{
						RippleDistortion ripple = new RippleDistortion(ship.getLocation(), ship.getVelocity());
						ripple.setSize(400f);
						ripple.setIntensity(100f * 1.25f);
						ripple.setFrameRate(60f / (time));
						ripple.fadeInSize(time);
						ripple.fadeOutIntensity(time);
						DistortionShader.addDistortion(ripple);
					}
					*/
					
					engine.spawnExplosion(boom.getLocation(), boom.getVelocity(), new Color(149,200,255,255), 200f, 0.1f);
					
						DamagingExplosionSpec boom2 = new DamagingExplosionSpec(
							EXPLOSION_DURATION,
							EXPLOSION_SIZE_OUTER,
							EXPLOSION_SIZE_INNER,
							EXPLOSION_DAMAGE_MAX,
							EXPLOSION_DAMAGE_MIN,
							CollisionClass.PROJECTILE_NO_FF,
							CollisionClass.PROJECTILE_FIGHTER,
							PARTICLE_SIZE_MIN,
							PARTICLE_SIZE_RANGE,
							PARTICLE_DURATION2,
							PARTICLE_COUNT,
							VFX_COLOR,
							VFX_COLOR
						);
						
					boom2.setDamageType(DamageType.ENERGY);
					boom2.setShowGraphic(true);
					boom2.setSoundSetId("explosion_ship");
					engine.spawnDamagingExplosion(boom2, ship, boom.getLocation(), false);	
					float speed = ship.getVelocity().length();
					for (int i = 0; i <= PARTICLE_COUNT; i++)
					{
						float angle = MathUtils.getRandomNumberInRange(0,
								360);
						float vel = MathUtils.getRandomNumberInRange(speed * MIN_SPEED,
								speed * MAX_SPEED);
						Vector2f vector = MathUtils.getPointOnCircumference(null,
								vel,
								angle);
						engine.addHitParticle(ship.getLocation(),
								vector,
								PARTICLE_SIZE,
								PARTICLE_BRIGHTNESS,
								PARTICLE_DURATION+0.05f,
								PARTICLE_COLOR);
					}
					
					float increment = (1f/value);
					
					//not the actual number of projectiles, but the number of times this can be added to 360 determines
					//number of projectile spawned
					int numProjs = (int)(BASE_INCREMENT*increment);
					if(numProjs < 30)
						numProjs = 30;
						
					for(int i = 0; i < 360; i+=numProjs)
					{
						//if(i > 20) break;
						DamagingProjectileAPI proj = (DamagingProjectileAPI)engine.spawnProjectile(ship,maingun,"armaa_karma",maingun.getLocation(),i,ship.getVelocity());
						proj.setFacing(i);
					}
					
					engine.getCustomData().put("armaa_karmaTotal"+ship.getId(),.99f);

					activated = true;
					Global.getSoundPlayer().playSound("system_interdictor", 1.1f, 1f, ship.getLocation(), ZERO);
					Global.getSoundPlayer().playSound("explosion_from_damage", 1f, 1f, ship.getLocation(), ZERO);
					return;
				}
				
				else
				{
					engine.getCustomData().put("armaa_karmaTotal"+ship.getId(),1f);
					
				}
			
			}
			
		}
		
		if (state == State.OUT && !activated) 
		{
			float total = 0;
			float amount = 0;
			float range = 600f;
			range = ship.getMutableStats().getSystemRangeBonus().computeEffective(range);

			for (DamagingProjectileAPI proj : CombatUtils.getProjectilesWithinRange(ship.getLocation(), range))
			{
				if(proj.getSource().getOwner() != ship.getOwner())
				{
					if (proj.getBaseDamageAmount() <= 0 || !engine.isEntityInPlay(proj)) continue;
					    
                        if (proj.getDamageType() == DamageType.FRAGMENTATION) 
						{
                            amount = proj.getDamageAmount() * 0.25f + proj.getEmpAmount() * 0.25f;
                        } 
						else 
						{
                            amount = proj.getDamageAmount() + proj.getEmpAmount() * 0.25f;
                        }
					
					if(amount > 0)
					{
						total+= amount;
						//armaa_KarmaMod.addKarma(proj.getDamageAmount()/armaa_KarmaMod.getKarmaThreshold());
						if(engine.isEntityInPlay(proj))
						{

						
							engine.spawnEmpArc(ship, new Vector2f(proj.getLocation()), 
								new SimpleEntity(proj.getLocation()), 
								ship, 
								DamageType.OTHER, 
								0f, 
								0f, 
								500f, 
								"tachyon_lance_emp_impact", 
								10f, 
								new Color(100,206,240), 
								new Color(100,206,255));
							if(MagicRender.screenCheck(0.2f, proj.getLocation()))
							{
								MagicLensFlare.createSharpFlare(
								engine,
								ship,
								proj.getLocation(),
								10f,
								100f,
								(float)Math.random()*360,
								new Color(100,206,240),
								new Color(100,206,255)
								);
								engine.addSmoothParticle(proj.getLocation(), new Vector2f(), 150, 0.5f, 0.3f, Color.blue);
								engine.addHitParticle(proj.getLocation(), new Vector2f(), 150, 1f, 0.15f, Color.white);
								//engine.addSwirlyNebulaParticle(proj.getLocation(), new Vector2f(), 150f, 1.5f, 0.15f, 0f, 0.3f, Color.blue, true); 
								engine.addSwirlyNebulaParticle(proj.getLocation(), new Vector2f(0.0f, 0.0f), 100f, 1.2f, 0.15f, 0.0f, 0.35f, Color.blue,false);
							}
								
						}
					}
					
					engine.removeEntity(proj);
				}
			}
			
			Global.getSoundPlayer().playSound("system_phase_skimmer", 1f, 0.25f + (float) Math.sqrt(amount) / 30f, ship.getLocation(), ZERO);
						
			for (MissileAPI m : CombatUtils.getMissilesWithinRange(ship.getLocation(), range))
			{
				if(m.getSource().getOwner() == ship.getOwner()) continue;
				
				if(m.getSource().getOwner() != ship.getOwner())
				{
					if(m.isGuided())
					{
						if(!MechaModPlugin.KARMA_IMMUNE.contains(m.getProjectileSpecId()))
						{
							if(m instanceof MissileAPI)	
							{
								if (m.getMissileAI() instanceof GuidedMissileAI)
								{
									((GuidedMissileAI) m.getMissileAI()).setTarget(null);
								}
								
								engine.addFloatingText(m.getLocation(),
								 "REDIRECTED!",
								 20f,
								 Color.yellow,
								 m,
								 2f,
								  .2f);
								m.setFacing(-m.getFacing());
								m.setOwner(ship.getOwner());
								m.setSource(ship);
								engine.addSmoothParticle(m.getLocation(), new Vector2f(), 150, 0.5f, 0.3f, Color.red);
								engine.addHitParticle(m.getLocation(), new Vector2f(), 150, 1f, 0.15f, Color.white);
								
								//engine.removeEntity(m);
							}
						}
						
						else
						{
							engine.addFloatingText(m.getLocation(),
							 "NO EFFECT!",
							 20f,
							 Color.red,
							 m,
							 2f,
							  .2f);
							
						}
					}
					
					else
					{
						if (m.getDamageType() == DamageType.FRAGMENTATION) 
						{
                            amount = m.getDamageAmount() * 0.25f + m.getEmpAmount() * 0.25f;
                        } 
						else 
						{
                            amount = m.getDamageAmount() + m.getEmpAmount() * 0.25f;
                        }
					
						if(amount > 0)
						{
							total+= amount;
							engine.getCustomData().put("armaa_hasGainedKarma"+ship.getId(),true);
							engine.getCustomData().put("armaa_gainedKarmaAount"+ship.getId(),(amount/armaa_KarmaMod.getKarmaThreshold()));
							//armaa_KarmaMod.addKarma(m.getDamageAmount()/armaa_KarmaMod.getKarmaThreshold());
							if(engine.isEntityInPlay(m))
							{
								
								engine.spawnEmpArc(ship, new Vector2f(m.getLocation()), 
									new SimpleEntity(m.getLocation()), 
									ship, 
									DamageType.OTHER, 
									0f, 
									0f, 
									500f, 
									"tachyon_lance_emp_impact", 
									10f, 
									new Color(100,206,240), 
									new Color(100,206,255));
								if(MagicRender.screenCheck(0.2f, m.getLocation()))
								{
									MagicLensFlare.createSharpFlare(
									engine,
									ship,
									m.getLocation(),
									10f,
									100f,
									(float)Math.random()*360,
									new Color(100,206,240),
									new Color(100,206,255)
									);						
								}
							}
						}
								Global.getSoundPlayer().playSound("system_interdictor", 1.5f, .8f, m.getLocation(), ZERO);

								engine.removeEntity(m);				
					}
					
				}	

			}
			
			engine.getCustomData().put("armaa_hasGainedKarma"+ship.getId(),true);
			engine.getCustomData().put("armaa_gainedKarmaAount"+ship.getId(),(total/armaa_KarmaMod.getKarmaThreshold()));
            activated = true;			
				
		}
	}
	
	public void unapply(MutableShipStatsAPI stats, String id) 
	{
		activated = false;
	}
	
}
