package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import org.magiclib.util.MagicRender;
import org.magiclib.util.MagicLensFlare;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.combat.EmpArcEntityAPI;

import java.awt.Color;
import org.lwjgl.util.vector.Vector2f;

public class armaa_gBusterOnHitEffect implements OnHitEffectPlugin {

    private static final Color COLOR1 = new Color(255,0,0,255);
    private static final Color COLOR2 = new Color(245,188,44,255);
    private static final Vector2f ZERO = new Vector2f();

    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) 
	{
        boolean hitShields = shieldHit;
        if (point == null) {
            return;
        }

        if (target instanceof ShipAPI) {
            ShipAPI ship = (ShipAPI) target;

            float hitLevel = 0f;
            float emp = projectile.getEmpAmount() * 0.25f;
            float dam = projectile.getDamageAmount() * 0.25f;
            for (int x = 0; x < 4; x++) {
                float pierceChance = ((ShipAPI) target).getHardFluxLevel() - 0.1f;
                pierceChance *= ship.getMutableStats().getDynamic().getValue(Stats.SHIELD_PIERCED_MULT);

                boolean piercedShield = hitShields && (float) Math.random() < pierceChance;
                if (!hitShields || piercedShield) {
                    hitLevel += 0.25f;
                    ShipAPI empTarget = ship;
                   EmpArcEntityAPI arc =  engine.spawnEmpArcPierceShields(projectile.getSource(), point, empTarget, empTarget,
                            DamageType.ENERGY, dam, emp, 100000f, null, 15f, COLOR1, COLOR2);
                }
            }

            if (hitLevel > 0f) 
			{
				if(MagicRender.screenCheck(0.2f, point))
				{
					engine.addSmoothParticle(point, ZERO, 150f * hitLevel, hitLevel, 0.75f, COLOR1);
					engine.addSmoothParticle(projectile.getLocation(), new Vector2f(0,0), 300, 2, 0.15f, Color.white);
					engine.addHitParticle(projectile.getLocation(), new Vector2f(0,0), 159, 1, 0.4f, new Color(200,100,25));
				    engine.spawnExplosion(projectile.getLocation(), new Vector2f(0,0), Color.BLACK, 60*3, 3);
					engine.spawnExplosion(projectile.getLocation(),new Vector2f(0,0), COLOR2, 50*3, 1.5f);
					
					MagicLensFlare.createSmoothFlare(
					engine,
					projectile.getSource(),
					point,
					hitLevel*6,
					400,
					projectile.getFacing()-90f,
					COLOR1,
					COLOR2
					);
				}
				
                Global.getSoundPlayer().playSound("armaa_buster_hit", 1f, 1f * hitLevel, point, ZERO);
            }
        }
    }
}
