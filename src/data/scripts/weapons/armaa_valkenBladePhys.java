package data.scripts.weapons;

import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;

import org.magiclib.util.MagicRender;
import java.util.List;
import java.util.ArrayList;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.lazywizard.lazylib.combat.CombatUtils;

import java.awt.Color;
import org.lazywizard.lazylib.MathUtils;


public class armaa_valkenBladePhys implements BeamEffectPlugin {

	private IntervalUtil fireInterval = new IntervalUtil(0.05f, 0.05f);

	private final Vector2f ZERO = new Vector2f();
	private final float BLADE_KNOCKBACK_MAX = 200f;
    // -- stuff for tweaking particle characteristics ------------------------
    // color of spawned particles
    private static final Color PARTICLE_COLOR = new Color(250,236,111,255);
    // size of spawned particles (possibly in pixels?)
    private static final float PARTICLE_SIZE = 4f;
    // brightness of spawned particles (i have no idea what this ranges from)
    private static final float PARTICLE_BRIGHTNESS = 150f;
    // how long the particles last (i'm assuming this is in seconds)
    private static final float PARTICLE_DURATION = .8f;
    private static final int PARTICLE_COUNT = 4;

    // -- particle geometry --------------------------------------------------
    // cone angle in degrees
    private static final float CONE_ANGLE = 150f;
    // constant that effects the lower end of the particle velocity
    private static final float VEL_MIN = 0.1f;
    // constant that effects the upper end of the particle velocity
    private static final float VEL_MAX = 0.3f;

    // one half of the angle. used internally, don't mess with thos
    private static final float A_2 = CONE_ANGLE / 2;
	private float  arc = 20f;
    private float level = 0f;
	private boolean firstStrike = false;
	private boolean firstTrail = false;
	private float id;
	private float id2;
	private boolean runOnce = false;
	private WeaponAPI weapon;
	private List<CombatEntityAPI> targets = new ArrayList<CombatEntityAPI>();
	private List<CombatEntityAPI> hitTargets = new ArrayList<CombatEntityAPI>();
	
	public void advance(float amount, CombatEngineAPI engine, BeamAPI beam) {
		
		weapon = beam.getWeapon();
		beam.getDamage().setDamage(0);
		CombatEntityAPI target = beam.getDamageTarget();
		if(!targets.contains(target))
		{
			targets.add(target);
		}

		Vector2f spawnPoint = MathUtils.getRandomPointOnLine(beam.getFrom(), beam.getTo());
	
			if(weapon.isFiring() && beam.getBrightness() >= 0.7f)
			{
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
							float dmg = Math.max(weapon.getDamage().getDamage(),weapon.getDamage().getDamage()*weapon.getSpec().getBurstDuration()*(weapon.getShip().getMutableStats().getBallisticWeaponDamageMult().computeMultMod()+weapon.getShip().getMutableStats().getBallisticWeaponDamageMult().getPercentMod()/100f)*(weapon.getShip().getMutableStats().getEnergyWeaponDamageMult().computeMultMod()+weapon.getShip().getMutableStats().getBeamWeaponDamageMult().getPercentMod()/100f));
							
							float mag = weapon.getShip().getFluxBasedEnergyWeaponDamageMultiplier() - 1f;
							if(mag > 0)
								dmg = dmg*(1+mag);
							
							engine.applyDamage(enemy, beam.getTo(),dmg,weapon.getDamageType(), dmg, false, false, weapon.getShip()); 
							hitTargets.add(enemy);
						}
					}
				}

				fireInterval.advance(amount);

				if(fireInterval.intervalElapsed() && beam.getBrightness() == 1f)
				{
					float angle = weapon.getCurrAngle()+90f;
	

				}				
			}
			
		if (target instanceof CombatEntityAPI) 
		{
			float dur = beam.getDamage().getDpsDuration();

			if (!firstStrike && beam.getBrightness() > .9f)
			{
				Vector2f point = beam.getTo();
				float variance = MathUtils.getRandomNumberInRange(-0.3f, .3f);
				Global.getSoundPlayer().playSound("armaa_sword_strike",1f+variance,1f+variance,point,ZERO);
				firstStrike = true;
				CombatUtils.applyForce(weapon.getShip(), weapon.getShip().getFacing()-180f, Math.min(target.getMass(),BLADE_KNOCKBACK_MAX));
				Vector2f dir = Vector2f.sub(beam.getTo(), beam.getFrom(), new Vector2f());
				if (dir.lengthSquared() > 0) dir.normalise();
				dir.scale(50f);
				point = Vector2f.sub(beam.getTo(), dir, new Vector2f());
			
			
				if(MagicRender.screenCheck(0.2f, point))
				{
					if(weapon.isFiring() && weapon.getChargeLevel() > 0f && weapon.getChargeLevel() < 1f || weapon.getChargeLevel() == 1f)
					{
						SpriteAPI waveSprite = Global.getSettings().getSprite("misc", "armaa_sfxpulse");
						if (waveSprite != null)
						{
							MagicRender.battlespace(
										waveSprite,
										beam.getTo(),
										new Vector2f(),
										new Vector2f(5f,5f),
										new Vector2f(200f,200f),
										15f,
										15f,
										new Color(150,155,155,155), 
										true,
										.3f,
										0f,
										.3f
								);
						}

						float radius = 10f + (weapon.getChargeLevel() * weapon.getChargeLevel()* MathUtils.getRandomNumberInRange(25f, 75f));
						Color color = beam.getFringeColor();
						Color core = beam.getCoreColor();

						
						float speed = 500f;
						float facing = beam.getWeapon().getCurrAngle();
					
						for (int i = 0; i <= PARTICLE_COUNT; i++)
						{
							float angle = MathUtils.getRandomNumberInRange(facing - A_2,
									facing + A_2);
							float vel = MathUtils.getRandomNumberInRange(speed * -VEL_MIN,
									speed * -VEL_MAX);
							Vector2f vector = MathUtils.getPointOnCircumference(null,
									vel,
									angle);
							engine.addHitParticle(beam.getTo(),
									vector,
									PARTICLE_SIZE,
									PARTICLE_BRIGHTNESS,
									PARTICLE_DURATION,
									PARTICLE_COLOR);
						}
					}
				}
			}
		}
	}
}
