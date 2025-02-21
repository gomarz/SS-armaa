package data.scripts.weapons;

import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.ProximityExplosionEffect;
import com.fs.starfarer.api.impl.combat.NegativeExplosionVisual;
import com.fs.starfarer.api.impl.combat.NegativeExplosionVisual.NEParams;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.loading.MissileSpecAPI;
import com.fs.starfarer.api.Global;

import java.awt.Color;

import org.lwjgl.util.vector.Vector2f;

public class armaa_chaosBurstMineExplosion extends NegativeExplosionVisual.NEParams implements ProximityExplosionEffect{
	
	public void onExplosion(DamagingProjectileAPI explosion, DamagingProjectileAPI originalProjectile) {
		NEParams p = armaa_chaosBurstMineExplosion.createStandardRiftParams("armaa_valkazard_torso_chaosburst_minelayer", 10f);
		//p.hitGlowSizeMult = 0.5f;
		p.thickness = 50f;
		CombatEngineAPI engine = Global.getCombatEngine();
		//armaa_chaosBurstMineExplosion.spawnStandardRift(explosion, p);
            engine.addSmoothParticle(originalProjectile.getLocation(), new Vector2f(0,0), 200, 2, 0.07f, Color.white);
            engine.addHitParticle(originalProjectile.getLocation(), new Vector2f(0,0), 150f, 1.5f, 0.4f, new Color(245,184,54,255));
			engine.addHitParticle(originalProjectile.getLocation(), new Vector2f(0,0), 150f/2f, 1.5f, 0.4f, Color.white);
           // engine.spawnExplosion(originalProjectile.getLocation(), new Vector2f(0,0), Color.BLACK, 225, 3);
            //engine.spawnExplosion(originalProjectile.getLocation(),new Vector2f(0,0), new Color (255,50,0), 87, 1.0f);		
	}
	
	public static void spawnStandardRift(DamagingProjectileAPI explosion, NEParams params) {
		CombatEngineAPI engine = Global.getCombatEngine();
		explosion.addDamagedAlready(explosion.getSource());
		
		CombatEntityAPI prev = null;
		for (int i = 0; i < 2; i++) {
			NEParams p = armaa_chaosBurstMineExplosion.createStandardRiftParams("armaa_valkazard_torso_chaosburst_minelayer", 10f);
			p.radius *= 0.75f + 0.5f * (float) Math.random();

			p.withHitGlow = prev == null;
			
			Vector2f loc = new Vector2f(explosion.getLocation());
			//loc = Misc.getPointWithinRadius(loc, p.radius * 1f);
			loc = Misc.getPointAtRadius(loc, p.radius * 0.4f);
			
			CombatEntityAPI e = engine.addLayeredRenderingPlugin(new NegativeExplosionVisual(p));
			e.getLocation().set(loc);
			
			if (prev != null) {
				float dist = Misc.getDistance(prev.getLocation(), loc);
				Vector2f vel = Misc.getUnitVectorAtDegreeAngle(Misc.getAngleInDegrees(loc, prev.getLocation()));
			vel.scale(dist / (p.fadeIn + p.fadeOut) * 0.7f);
				e.getVelocity().set(vel);
			}
			
		//	prev = e;
		}
		
	}
	
public static NEParams createStandardRiftParams(String minelayerId, float baseRadius) {
		Color color = new Color(220,125,55,155);
		Object o = Global.getSettings().getWeaponSpec(minelayerId).getProjectileSpec();
		if (o instanceof MissileSpecAPI) {
			MissileSpecAPI spec = (MissileSpecAPI) o;
			color = spec.getGlowColor();
		}
		NEParams p = createStandardRiftParams(color, baseRadius);
		return p;
	}
	
	public static NEParams createStandardRiftParams(Color borderColor, float radius) {
		NEParams p = new NEParams();
		//p.radius = 50f;
		p.hitGlowSizeMult = .75f;
		//p.hitGlowSizeMult = .67f;
		p.spawnHitGlowAt = 0f;
		p.noiseMag = 1f;
		//p.fadeIn = 0f;
		//p.fadeOut = 0.25f;
		
		//p.color = new Color(175,100,255,255);
		
		//p.hitGlowSizeMult = .75f;
		p.fadeIn = 0.1f;
		//p.noisePeriod = 0.05f;
		p.underglow = new Color(225, 125, 25, 100);
		//p.withHitGlow = i == 0;
		p.withHitGlow = true;
		
		//p.radius = 20f;
		p.radius = radius;
		//p.radius *= 0.75f + 0.5f * (float) Math.random();
		
		p.color = borderColor;
		return p;
	}
}


