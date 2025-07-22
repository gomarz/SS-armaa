package data.scripts.weapons;


import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ArmorGridAPI;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponSize;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.impl.combat.DisintegratorEffect;
import com.fs.starfarer.api.util.Misc;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

//Author: Nia Tahl

public class armaa_BarrettaOnHitEffect implements OnHitEffectPlugin {

	//tbh, this should scale based off weapon size
    private static Map DAMAGE = new HashMap();
	static{
        DAMAGE.put(WeaponSize.SMALL, 50f);
        DAMAGE.put(WeaponSize.MEDIUM, 100f);
        DAMAGE.put(WeaponSize.LARGE, 200f);
	}

    @Override
	public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target,
					  Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {
		if (!shieldHit && target instanceof ShipAPI) {
			dealArmorDamage(projectile, (ShipAPI) target, point);
			engine.spawnExplosion(projectile.getLocation(), target.getVelocity(), new Color(255,120,190,20), (Float)DAMAGE.get(projectile.getWeapon().getSize())*0.6f, 0.2f);
		}
                // just t o check if it worked or not... engine.addFloatingText(point, "PENIS", 20f, Color.yellow, target, 2f, 2f);
	}
	
	public static void dealArmorDamage(DamagingProjectileAPI projectile, ShipAPI target, Vector2f point) {
		CombatEngineAPI engine = Global.getCombatEngine();

		ArmorGridAPI grid = target.getArmorGrid();
		int[] cell = grid.getCellAtLocation(point);
		if (cell == null) return;
		
		int gridWidth = grid.getGrid().length;
		int gridHeight = grid.getGrid()[0].length;
		
		float damageTypeMult = DisintegratorEffect.getDamageTypeMult(projectile.getSource(), target);
                //float damageTypeMult = projectile.getDamageAmount()/projectile.getWeapon().getDamage().getBaseDamage();
		
		float damageDealt = 0f;
		for (int i = -2; i <= 2; i++) {
			for (int j = -2; j <= 2; j++) {
				if ((i == 2 || i == -2) && (j == 2 || j == -2)) continue; // skip corners
				
				int cx = cell[0] + i;
				int cy = cell[1] + j;
				
				if (cx < 0 || cx >= gridWidth || cy < 0 || cy >= gridHeight) continue;
				
				float damMult = 1/30f;
				if (i == 0 && j == 0) {
					damMult = 1/15f;
				} else if (i <= 1 && i >= -1 && j <= 1 && j >= -1) { // S hits
					damMult = 1/15f;
				} else { // T hits
					damMult = 1/30f;
				}
				
				float armorInCell = grid.getArmorValue(cx, cy);
				float damage = (Float)DAMAGE.get(projectile.getWeapon().getSize()) * damMult * damageTypeMult;
				damage = Math.min(damage, armorInCell);
				if (damage <= 0) continue;
				
				target.getArmorGrid().setArmorValue(cx, cy, Math.max(0, armorInCell - damage));
				damageDealt += damage;
			}
		}
		
		if (damageDealt > 0) {
			if (Misc.shouldShowDamageFloaty(projectile.getSource(), target)) {
				engine.addFloatingDamageText(point, damageDealt, Misc.FLOATY_ARMOR_DAMAGE_COLOR, target, projectile.getSource());
			}
			target.syncWithArmorGridState();
		}
	}
}
