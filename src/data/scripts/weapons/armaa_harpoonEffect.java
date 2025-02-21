package data.scripts.weapons;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
//import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import data.scripts.weapons.armaa_harpoonProjectileScript;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lazywizard.lazylib.VectorUtils;
public class armaa_harpoonEffect implements OnHitEffectPlugin {
    
    private final String ID="armaa_harpoonSecondary";
    
    @Override
        public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine)  {
          if(!projectile.isFading())
		{
            if(!shieldHit)
			{
				
				((armaa_harpoonThrowerEffect) projectile.getWeapon().getEffectPlugin()).putHIT(target);
				DamagingProjectileAPI proj =(DamagingProjectileAPI)engine.spawnProjectile(
								projectile.getSource(),
								projectile.getWeapon(),
								ID,
								point,
								projectile.getFacing(),
								target.getVelocity()
				);
							engine.addPlugin(new armaa_harpoonProjectileScript(proj,projectile.getSource(),target));

            }
        }
    }
}
