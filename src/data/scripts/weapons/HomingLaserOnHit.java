package data.scripts.weapons;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;

import org.magiclib.util.MagicRender;
import org.magiclib.util.MagicLensFlare;

import org.lwjgl.util.vector.Vector2f;

import org.lazywizard.lazylib.MathUtils;

import java.awt.Color;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;

/**
 * A simple on-hit effect for HE guns like the Howler
 *
 * @author Eliza Weisman
 */
public class HomingLaserOnHit implements OnHitEffectPlugin
{

    // Declare important values as constants so that our
    // code isn't littered with magic numbers. If we want to
    // re-use this effect, we can easily just copy this class
    // and tweak some of these constants to get a similar effect.
    // -- crit damage -------------------------------------------------------
    // minimum amount of extra damage
    private static final int CRIT_DAMAGE_MIN = 5;
    // maximum amount of extra damage dealt
    private static final int CRIT_DAMAGE_MAX = 30;
    // probability (0-1) of dealing a critical hit
    private static final float CRIT_CHANCE = .25f;
    //
    private ShipAPI ship;

    //  -- crit fx ----------------------------------------------------------
    // I took this from the 'core' color of the Howler projectile.
    // It can be changed
    private static final Color EXPLOSION_COLOR = new Color(255,182,64,255);

    // -- stuff for tweaking particle characteristics ------------------------
    // color of spawned particles
    private static final Color PARTICLE_COLOR = new Color(130,100,255,255);
    // size of spawned particles (possibly in pixels?)
    private static final float PARTICLE_SIZE = 5f;
    // brightness of spawned particles (i have no idea what this ranges from)
    private static final float PARTICLE_BRIGHTNESS = 150f;
    // how long the particles last (i'm assuming this is in seconds)
    private static final float PARTICLE_DURATION = 3f;
    private static final int PARTICLE_COUNT = 6;

    // -- particle geometry --------------------------------------------------
    // cone angle in degrees
    private static final float CONE_ANGLE = 150f;
    // constant that effects the lower end of the particle velocity
    private static final float VEL_MIN = 0.1f;
    // constant that effects the upper end of the particle velocity
    private static final float VEL_MAX = 0.15f;

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

        // check whether or not we want to apply critical damage
        if (target instanceof ShipAPI && !shieldHit && Math.random() <= CRIT_CHANCE)
        {

            // apply the extra damage to the target
            engine.applyDamage(target, point, // where to apply damage
                    MathUtils.getRandomNumberInRange(
                            CRIT_DAMAGE_MIN, CRIT_DAMAGE_MAX),
                    DamageType.HIGH_EXPLOSIVE, // damage type
                    0f, // amount of EMP damage (none)
                    false, // does this bypass shields? (no)
                    false, // does this deal soft flux? (no)
                    projectile.getSource());

if(MagicRender.screenCheck(0.2f, point)){
MagicLensFlare.createSmoothFlare(
        engine,
        projectile.getSource(),
        point,
        50,
        400,
        0,
        new Color(23,25,255),
        new Color(200,200,200)
        );
}

	
        }
            // do visual effects ---------------------------------------------

if(MagicRender.screenCheck(0.2f, point)){
MagicLensFlare.createSharpFlare(
        engine,
        projectile.getSource(),
        point,
        6,
        300,
        0,
        new Color(155,20,255),
        new Color(255,20,255)
        );

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
