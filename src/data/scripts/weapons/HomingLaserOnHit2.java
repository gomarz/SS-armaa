package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;

import org.magiclib.util.MagicRender;
import org.magiclib.util.MagicLensFlare;

import org.lwjgl.util.vector.Vector2f;

import org.lazywizard.lazylib.MathUtils;

import java.util.Random;
import java.awt.Color;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * A simple on-hit effect for HE guns like the Howler
 *
 * @author Eliza Weisman
 */
public class HomingLaserOnHit2 implements OnHitEffectPlugin
{

    // Declare important values as constants so that our
    // code isn't littered with magic numbers. If we want to
    // re-use this effect, we can easily just copy this class
    // and tweak some of these constants to get a similar effect.
    // -- crit damage -------------------------------------------------------
    // minimum amount of extra damage
    private static final int CRIT_DAMAGE_MIN = 0;
    // maximum amount of extra damage dealt
    private static final int CRIT_DAMAGE_MAX = 0;
     //probability (0-1) of dealing a critical hit
    private static final float CRIT_CHANCE = .25f;
    //
    private ShipAPI ship;

    //  -- crit fx ----------------------------------------------------------
    // I took this from the 'core' color of the Howler projectile.
    // It can be changed
    private static final Color EXPLOSION_COLOR = new Color(128,180,242,155);

    // -- stuff for tweaking particle characteristics ------------------------
    // color of spawned particles
    private static final Color PARTICLE_COLOR = new Color(100,197,255,255);
    // size of spawned particles (possibly in pixels?)
    private static final float PARTICLE_SIZE = 2f;
    // brightness of spawned particles (i have no idea what this ranges from)
    private static final float PARTICLE_BRIGHTNESS = 150f;
    // how long the particles last (i'm assuming this is in seconds)
    private static final float PARTICLE_DURATION = 0.5f;
    private static final int PARTICLE_COUNT = 3;

    // -- particle geometry --------------------------------------------------
    // cone angle in degrees
    private static final float CONE_ANGLE = 150f;
    // constant that effects the lower end of the particle velocity
    private static final float VEL_MIN = 0.1f;
    // constant that effects the upper end of the particle velocity
    private static final float VEL_MAX = 0.5f;

    // one half of the angle. used internally, don't mess with thos
    private static final float A_2 = CONE_ANGLE / 2;

    @Override
    public void onHit(DamagingProjectileAPI projectile,
            CombatEntityAPI target,
            Vector2f point,
            boolean shieldHit,
	    ApplyDamageResultAPI damageResult,
            CombatEngineAPI engine)
    {
        // do visual effects ---------------------------------------------
		MissileAPI miss = (MissileAPI)projectile;
		Color core = new Color(199, 146,0,100);
		if(MagicRender.screenCheck(0.2f, point))
		{
			engine.addNebulaParticle(miss.getLocation(),
				new Vector2f(),
				 40f * (0.75f + (float) Math.random() * 0.5f),
				1f + 1f * Misc.getHitGlowSize(75f, miss.getDamage().getBaseDamage(), damageResult) / 100f,
				0f,
				0f,
				1f,
				new Color(100,100,200,200),
				true);
			MagicLensFlare.createSharpFlare(
					engine,
					projectile.getSource(),
					point,
					6,
					200,
					0,
					core,
					Color.white
					);				
			engine.spawnExplosion(point, new Vector2f(), new Color(200,200,255,200), 40f, 0.1f);				


            float speed = projectile.getVelocity().length();
            float facing = projectile.getFacing();
			
            for (int i = 0; i <= PARTICLE_COUNT; i++)
            {
                float angle = MathUtils.getRandomNumberInRange(facing - A_2,
                        facing + A_2);
                float vel = MathUtils.getRandomNumberInRange(speed * -VEL_MIN,
                        speed * -VEL_MAX);
                Vector2f vector = MathUtils.getPointOnCircumference(null,
                        vel,
                        angle);
                engine.addHitParticle(point,
                        vector,
                        PARTICLE_SIZE,
                        PARTICLE_BRIGHTNESS,
                        PARTICLE_DURATION,
                        PARTICLE_COLOR);
            }
	}
    }
}
