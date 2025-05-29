package data.scripts.weapons;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.BeamEffectPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.util.IntervalUtil;

public class armaa_ionBeamEffect implements BeamEffectPlugin {

	private IntervalUtil fireInterval = new IntervalUtil(0.25f, 1.75f);
	private boolean wasZero = true;
    private static final float MISSILE_SCALAR = 0.2f;	
    private static final float AT_MAX_RANGE = 0.5f;
    private static final float PUSH_CONSTANT = 3000f;	
	
	public void advance(float amount, CombatEngineAPI engine, BeamAPI beam) {
		CombatEntityAPI target = beam.getDamageTarget();
		if (target instanceof ShipAPI && beam.getBrightness() >= 1f) {
			float dur = beam.getDamage().getDpsDuration();
			// needed because when the ship is in fast-time, dpsDuration will not be reset every frame as it should be
			if (!wasZero) dur = 0;
			wasZero = beam.getDamage().getDpsDuration() <= 0;
			fireInterval.advance(dur);
				//piercedShield = true;
				ShipAPI src = beam.getWeapon().getShip();
				boolean hitShield = target.getShield() != null && target.getShield().isWithinArc(beam.getTo());				
				//if(!hitShield)
				//{
					float myMass = src.getMassWithModules();
					Vector2f origin = beam.getFrom();
					float targetMass;	
					if (target instanceof ShipAPI) 
					{
						if (((ShipAPI) target).getParentStation() != null && ((ShipAPI) target).isAlive())
							target = ((ShipAPI) target).getParentStation();
						targetMass = ((ShipAPI) target).getMassWithModules();	
					} else if (target instanceof MissileAPI){
						// Missiles are treated as being lighter
						targetMass = target.getMass() * MISSILE_SCALAR;
					} else {
						targetMass = target.getMass();
					}

					// Prevent 1/0 errors
					if (targetMass == 0f) targetMass = 1f;

					// Shield hits reduce the effect
					float shieldModifier = 1f;
					
					if(hitShield)
						shieldModifier *= 0.5f;

					Vector2f dir = Vector2f.sub(origin, beam.getTo(), new Vector2f());
					float distance = dir.length();

					if (dir.lengthSquared() > 0f) dir.normalise();

					// The beam's total force is divided proportionally between the objects, by mass
					float relativeMass = myMass / (myMass + targetMass);

					// The force proportions are again scaled by mass -
					// a linear increase in proportional mass creates an exponential decrease in proportional movement
					float pushMe = PUSH_CONSTANT / myMass * (1f - relativeMass);
					float pushYou = PUSH_CONSTANT / targetMass * relativeMass;

					// Weakens the beam over distance
					float distanceModifier = 1f - (distance / 600f * AT_MAX_RANGE);

					// Applies forces to both parties
					//if(distance > 100)
					//{
						Vector2f dirClone = new Vector2f(dir);
						Vector2f.add((Vector2f)dir.scale(pushMe * shieldModifier * distanceModifier * -1f),src.getVelocity(),src.getVelocity());
						Vector2f.add((Vector2f)dirClone.scale(pushYou * shieldModifier * distanceModifier),target.getVelocity(),target.getVelocity());
					//}
					
			if (fireInterval.intervalElapsed()) {
				ShipAPI ship = (ShipAPI) target;
				float pierceChance = ((ShipAPI)target).getHardFluxLevel() - 0.1f;
				pierceChance *= ship.getMutableStats().getDynamic().getValue(Stats.SHIELD_PIERCED_MULT);
				
				boolean piercedShield = hitShield && (float) Math.random() < pierceChance;

				if (!hitShield || piercedShield) {
					Vector2f point = beam.getRayEndPrevFrame();
					float emp = beam.getDamage().getFluxComponent() * 1f;
					float dam = beam.getDamage().getDamage() * 1f;
					engine.spawnEmpArcPierceShields(
									   beam.getSource(), point, beam.getDamageTarget(), beam.getDamageTarget(),
									   DamageType.ENERGY, 
									   dam, // damage
									   emp, // emp 
									   100000f, // max range 
									   "tachyon_lance_emp_impact",
									   beam.getWidth() + 9f,
									   beam.getFringeColor(),
									   beam.getCoreColor()
									   );
				}
			}
		}
//			Global.getSoundPlayer().playLoop("system_emp_emitter_loop", 
//											 beam.getDamageTarget(), 1.5f, beam.getBrightness() * 0.5f,
//											 beam.getTo(), new Vector2f());
	}
}
