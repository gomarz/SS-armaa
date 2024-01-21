package data.scripts.weapons;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
//import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;

public class armaa_spikeEffect implements OnHitEffectPlugin {
    
    private final String ID="armaa_pikeSecondary";
    
    @Override
        public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine)  {
          if(!projectile.isFading())
		{
            if(!shieldHit){
                ((armaa_spikeThrowerEffect) projectile.getWeapon().getEffectPlugin()).putHIT(target);
                engine.spawnProjectile(
                                projectile.getSource(),
                                projectile.getWeapon(),
                                ID,
                                point,
                                projectile.getFacing(),
                                target.getVelocity()
                );
            }
        }
    }
}
