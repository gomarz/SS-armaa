package data.scripts.weapons;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import java.awt.Color;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicRender;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;

public class armaa_spikeSecondaryEffect implements OnHitEffectPlugin {   
    private static final Color COLOR1 = new Color(255,0,0,255);
    private static final Color COLOR2 = new Color(245,188,44,255);
    private static final Vector2f ZERO = new Vector2f(); 
    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) 
	{
		if (target instanceof ShipAPI) 
		{

			ShipAPI ship = (ShipAPI) target;	
			
			float hitLevel = 0f;
			float emp = projectile.getEmpAmount();
			float dam = projectile.getWeapon().getDamage().getDamage()*.5f;
			for (int x = 0; x < 3; x++) 
			{


					hitLevel += 0.25f;
					ShipAPI empTarget = ship;
					engine.spawnEmpArcPierceShields(projectile.getSource(), point, empTarget, empTarget,
							DamageType.ENERGY, dam, emp, 100000f, null, 10f, COLOR1, COLOR2);

			}
			if (hitLevel > 0f) 
			{
				if(MagicRender.screenCheck(0.2f, point))
				{
					engine.addSmoothParticle(point, ZERO, (150f * hitLevel)/2, hitLevel, 0.75f, COLOR1);
					engine.addSmoothParticle(projectile.getLocation(), new Vector2f(0,0), 300, 2, 0.10f, Color.white);
					engine.addHitParticle(projectile.getLocation(), new Vector2f(0,0), 159/2, 1, 0.4f, new Color(200,100,25));
					engine.spawnExplosion(projectile.getLocation(), new Vector2f(0,0), Color.BLACK, (60*3)/2, 3);
					engine.spawnExplosion(projectile.getLocation(),new Vector2f(0,0), COLOR2, (50*3)/2, 1.5f);
				}
				Global.getSoundPlayer().playSound("armaa_buster_hit", 1f, 1f * hitLevel*.5f, point, ZERO);
			}
		}
	}
}