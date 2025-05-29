package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.combat.OnFireEffectPlugin;
import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import org.lazywizard.lazylib.MathUtils;

import java.util.Random;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;


public class armaa_minigunEveryFrameEffect extends BaseCombatLayeredRenderingPlugin implements OnFireEffectPlugin,
																					OnHitEffectPlugin,
																					EveryFrameWeaponEffectPlugin {

    private int lastAmmo = 0;
	private boolean soundPlayed = false;
    private boolean windingUp = true;
    private IntervalUtil cockSound = new IntervalUtil(0.33f,0.38f);
    private IntervalUtil reloadTime = new IntervalUtil(2.8f,2.85f);
	private DamagingProjectileAPI  dummy;
	private List<DamagingProjectileAPI> registeredProjectiles = new ArrayList<DamagingProjectileAPI>();

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) 
	{
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
			//Global.getSoundPlayer().playSound("armaa_minigun_windup", 1f, 1f, weapon.getLocation(), weapon.getShip().getVelocity());
			//soundPlayed = true;
		}

    }
	
    private static final int EXPLOSION_DAMAGE_MIN = 0;
    private static final int EXPLOSION_DAMAGE_MAX = 10;

    // -- explosion graphics -------------------------------------------------
    // color of the explosion
    private static final Color EXPLOSION_COLOR = new Color(255,105,100,200);
    // radius of the explosion
    private static final float EXPLOSION_RADIUS = 30f;
    // how long the explosion lingers for
    private static final float EXPLOSION_DURATION = 0.07f;
    
    // placeholder, please change this once you have a nice explosion sound :)
    private static final String SFX = "magellan_bonesaw_crit";
    // == don't mess with this stuff =========================================
    private static Random rng = new Random();
	
	private static float explosionDamage()
    {
        return (float) (rng.nextInt(
                (EXPLOSION_DAMAGE_MAX - EXPLOSION_DAMAGE_MIN) + 1)
                + EXPLOSION_DAMAGE_MIN);
    }
		
	public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) 
	{
        // check whether we've hit armour/hull
        if (target instanceof ShipAPI && !shieldHit)
        {
            engine.applyDamage(target, point, // where to apply damage
                    explosionDamage(), // amount of damage
                    DamageType.HIGH_EXPLOSIVE, // damage type
                    0f, // amount of EMP damage
                    false, // does this bypass shields? (no)
                    false, // does this deal soft flux? (no)
                    projectile.getSource());
            // get the target's velocity to render the crit FX
            Vector2f v_target = new Vector2f(target.getVelocity());

            // do visual effects
            engine.spawnExplosion(point, v_target,
                    EXPLOSION_COLOR, // color of the explosion
                    EXPLOSION_RADIUS,
                    EXPLOSION_DURATION
            );
            //play a sound
            //Global.getSoundPlayer().playSound(SFX, 1f, 1f, target.getLocation(), target.getVelocity());
        }
		
	}
	
    private static final int SMOKE_SIZE_MIN = 5;
    private static final int SMOKE_SIZE_MAX = 30;
	
	public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) 
	{
		float size = MathUtils.getRandomNumberInRange(SMOKE_SIZE_MIN , SMOKE_SIZE_MAX);
		float x = MathUtils.getRandomNumberInRange(-44,44);
		float y = MathUtils.getRandomNumberInRange(-48,48);
		Global.getCombatEngine().addSmokeParticle(projectile.getLocation(), new Vector2f(x,y), size, 0.5f, 0.5f, Color.gray);

		
	}

}