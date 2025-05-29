package data.scripts.weapons;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;

import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;

/**
 * A simple on-hit effect for HE guns like the Howler
 *
 * @author Eliza Weisman
 */
public class MissileOnHit implements OnHitEffectPlugin
{

    // Declare important values as constants so that our
    // code isn't littered with magic numbers. If we want to
    // re-use this effect, we can easily just copy this class
    // and tweak some of these constants to get a similar effect.

    // I took this from the 'core' color of the Howler projectile.
    // It can be changed
    private final Color EXPLOSION_COLOR = new Color(255,120,255,255);

    // placeholder, please change this once you have a nice explosion sound :)
   // private final String SFX = "istl_energy_crit";

    public void onHit(DamagingProjectileAPI projectile,
            CombatEntityAPI target,
            Vector2f point,
            boolean shieldHit,
            CombatEngineAPI engine)
    {
        // get the target's velocity to render the crit FX
        Vector2f v_target = new Vector2f(target.getVelocity());

            // do visual effects
            engine.spawnExplosion(point, v_target,
                    EXPLOSION_COLOR, // color of the explosion
                    75f, // sets the size of the explosion
                    0.55f // how long the explosion lingers for
            );
            //play a sound
            //Global.getSoundPlayer().playSound(SFX, 1f, 1f, target.getLocation(), target.getVelocity());
        }

    @Override
    public void onHit(DamagingProjectileAPI dpapi, CombatEntityAPI ceapi, Vector2f vctrf, boolean bln, ApplyDamageResultAPI adrapi, CombatEngineAPI ceapi1) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }
}
