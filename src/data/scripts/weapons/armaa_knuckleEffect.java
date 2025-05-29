package data.scripts.weapons;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import data.scripts.weapons.armaa_boostHammerProjectileScript;
import org.magiclib.util.MagicRender;


/**

 */
 

public class armaa_knuckleEffect extends BaseCombatLayeredRenderingPlugin implements OnFireEffectPlugin,
																					OnHitEffectPlugin,
																					EveryFrameWeaponEffectPlugin {
	private static final Color AFTERIMAGE_COLOR = new Color(255,165,90,155);
	private static final float AFTERIMAGE_THRESHOLD = 0.4f;
	private static final List<DamagingProjectileAPI> FIST_TRACKER = new ArrayList<>();

	public armaa_knuckleEffect() {
	}
	private boolean soundPlayed = false;
    private boolean windingUp = true;
	private boolean hasFired = false;
	public float TURRET_OFFSET = 40f;	
	float charge = 0f;
	public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) 
	{
		TURRET_OFFSET = weapon.getSpec().getTurretFireOffsets().get(0).x;
		float OFFSET_Y = weapon.getSpec().getTurretFireOffsets().get(0).y;
		ShipAPI ship = weapon.getShip();
		if(weapon.isFiring())
		charge = weapon.getChargeLevel();

		//hasFired = charge >= 1 ? true : false;
		
		if(weapon.getChargeLevel() > 0 && charge != 1)
		{
			if(!hasFired && weapon.getCooldownRemaining() <= 0)
			{
				Vector2f origin = new Vector2f(weapon.getLocation());
				Vector2f offset = new Vector2f(TURRET_OFFSET,OFFSET_Y);
				VectorUtils.rotate(offset, weapon.getCurrAngle(), offset);
				Vector2f.add(offset, origin, origin);
				Vector2f loc = MathUtils.getPoint((Vector2f)weapon.getLocation(), (float)18.5f, (float)weapon.getCurrAngle());
				Vector2f vel = weapon.getShip().getVelocity();
				//float charge = weapon.getChargeLevel();
				float size = 1.2f;
				windingUp = true;
				//engine.addHitParticle(origin, vel, MathUtils.getRandomNumberInRange((float)20.0f*size, (float)(charge*size * 60.0f + 20.0f)), MathUtils.getRandomNumberInRange((float)0.5f, (float)(0.5f + charge)), MathUtils.getRandomNumberInRange((float)0.1f, (float)(0.1f + charge / 10.0f)), new Color(charge / 1.5f, charge / 2f, charge/10f,.5f));
			}
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
		
		if(weapon.getAmmo() == 0)
		{
			hasFired = true;
			weapon.getSprite().setColor(new Color(0,0,0,0));
			ship.getMutableStats().getMaxSpeed().modifyMult(ship.getId()+"_powerfist",1.1f);
			//weapon.getGlowSpriteAPI().setColor(new Color(0,0,0,0));
		}
		
		else 
		{
			hasFired = false;
			weapon.getSprite().setColor(new Color(255,255,255,255));
			ship.getMutableStats().getMaxSpeed().unmodify(ship.getId()+"_powerfist");
		}
		 
	}
	
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
    private static final Color MUZZLE_FLASH_COLOR = new Color(250, 146, 0, 255);
    private static final Color MUZZLE_FLASH_COLOR_ALT = new Color(255, 255, 255, 100);
    private static final Color MUZZLE_FLASH_COLOR_GLOW = new Color(255, 0, 0, 100);
    private static final float MUZZLE_FLASH_DURATION = 1f;
    private static final float MUZZLE_FLASH_SIZE = 50.0f;		
	public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) 
	{
        ShipAPI source = projectile.getSource();
		WeaponAPI weapon = projectile.getWeapon();

		if(MagicRender.screenCheck(0.2f, point))
		{
			if (Math.random() > 0.75) {
				engine.spawnExplosion(point, new Vector2f(), MUZZLE_FLASH_COLOR, MUZZLE_FLASH_SIZE * 0.10f, MUZZLE_FLASH_DURATION);
			} else {
				engine.spawnExplosion(point, new Vector2f(), MUZZLE_FLASH_COLOR, MUZZLE_FLASH_SIZE, MUZZLE_FLASH_DURATION);
			}
			engine.addSmoothParticle(point, new Vector2f(), MUZZLE_FLASH_SIZE * 3f, 1f, MUZZLE_FLASH_DURATION * 2f, MUZZLE_FLASH_COLOR_GLOW);
			engine.addSmoothParticle(point, new Vector2f(0,0), 300, 2, 0.15f, Color.white);
            engine.spawnExplosion(point, new Vector2f(0,0), Color.DARK_GRAY, 125, 2);
            engine.spawnExplosion(point, new Vector2f(0,0), Color.BLACK, 60*5, 3);			
			
			for (int x = 0; x < 15; x++) 
			{
				engine.addHitParticle(point,
						MathUtils.getPointOnCircumference(null, MathUtils.getRandomNumberInRange(150f, 325f), (float) Math.random() * 360f),
						6f, 1f, MathUtils.getRandomNumberInRange(0.6f, 1f), Color.white);
			}
			
		}

        if (source == null) {
            return;
        }
		
        float angle = VectorUtils.getAngle(point, source.getLocation());
        //WeaponAPI weapon = projectile.getWeapon();
        DamagingProjectileAPI newProj = (DamagingProjectileAPI) engine.spawnProjectile(
                source,
                weapon,
                weapon.getId()+"_return",
                MathUtils.getPointOnCircumference(point, 40f, angle),
                angle+MathUtils.getRandomNumberInRange(-45f, 45f),
                target.getVelocity());
        engine.addPlugin(new armaa_BoomerangShieldGuidance(newProj, source));
	}
	
	public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) 
	{
		engine.addHitParticle(projectile.getLocation(), projectile.getVelocity(), 100, 1f, .2f, new Color(1f, 1f / 1.5f, 1f/10f));
		ShipAPI source = weapon.getShip();		

		Vector2f.add(MathUtils.getPoint(new Vector2f(), 50, projectile.getFacing()+180), source.getVelocity(), source.getVelocity());

		Vector2f drift = MathUtils.getPoint(new Vector2f(), MathUtils.getRandomNumberInRange(0, 75),  projectile.getFacing());
		Vector2f.add(drift, new Vector2f(source.getVelocity()), drift);
		
		ShipAPI target = null;
		
		if(source.getWeaponGroupFor(weapon)!=null ){
			//WEAPON IN AUTOFIRE
			if(source.getWeaponGroupFor(weapon).isAutofiring()  //weapon group is autofiring
					&& source.getSelectedGroupAPI()!=source.getWeaponGroupFor(weapon)){ //weapon group is not the selected group
				target = source.getWeaponGroupFor(weapon).getAutofirePlugin(weapon).getTargetShip();
			}
			else {
				target = source.getShipTarget();
			}
		}	
		if(!projectile.isFromMissile())
			engine.addPlugin(new armaa_boostHammerProjectileScript(projectile, target))	;	
		
	}
}




