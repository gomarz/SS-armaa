//By Nicke535, licensed under CC-BY-NC-SA 4.0 (https://creativecommons.org/licenses/by-nc-sa/4.0/)
//General script meant to be modified for each implementation. Causes a projectile to rotate mid-flight depending on several settings, simulating guidance
//HOW TO USE:
//	Copy this file where you want it and rename+adjust values
//	Find the projectile to guide using any method you want (everyframe script, weapon-mounted everyframe script, mine-spawning etc.)
//	run "engine.addPlugin(armaa_vajraProjectileScript(proj, target));" with:
//		armaa_vajraProjectileScript being replaced with your new class name
//		proj being the projectile to guide
//		target being the initial target (if any)
//	You're done!
package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.input.InputEventAPI;
import org.jetbrains.annotations.NotNull;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;
import java.awt.Color;
import java.util.List;

import org.magiclib.util.MagicRender;
import org.magiclib.util.MagicFakeBeam;
import com.fs.starfarer.api.util.IntervalUtil;
public class armaa_harpoonProjectileScript extends BaseEveryFrameCombatPlugin 
{
	private MissileAPI proj; //The projectile itself
	private CombatEntityAPI target;
	private CombatEntityAPI source;
	private float id;
	private IntervalUtil fireInterval = new IntervalUtil(0.05f, 0.1f);
    private static final float AT_MAX_RANGE = 0.5f;
    private static final float PUSH_CONSTANT = 2000f;
    private static final float MISSILE_SCALAR = 0.2f;	
	public armaa_harpoonProjectileScript(@NotNull DamagingProjectileAPI proj, CombatEntityAPI source, CombatEntityAPI target) 
	{
		this.proj = (MissileAPI)proj;
		this.target = target;
		this.source = source;
		//this.id = MagicTrailPlugin.getUniqueID();
			
	}


	//Main advance method
	@Override
	public void advance(float amount, List<InputEventAPI> events) 
	{
		//Sanity checks
		if (Global.getCombatEngine() == null) {
			return;
		}
		if (Global.getCombatEngine().isPaused()) {
			return;
		}

		//Checks if our script should be removed from the combat engine
		if (proj == null || proj.didDamage() || proj.isFizzling() || !Global.getCombatEngine().isEntityInPlay(proj) || !(source instanceof ShipAPI) ||!(target instanceof ShipAPI)) 
		{
			Global.getCombatEngine().removePlugin(this);
			return;
		}
			 float myMass = ((ShipAPI)source).getMassWithModules();
			float TURRET_OFFSET = proj.getWeapon().getSpec().getTurretFireOffsets().get(0).x;
			float OFFSET_Y = proj.getWeapon().getSpec().getTurretFireOffsets().get(0).y;
			Vector2f origin = new Vector2f(proj.getWeapon().getLocation());
			Vector2f offset = new Vector2f(TURRET_OFFSET,OFFSET_Y);
			VectorUtils.rotate(offset, proj.getWeapon().getCurrAngle(), offset);
			Vector2f.add(offset, origin, origin);
		if(MagicRender.screenCheck(0.2f, proj.getLocation()))
		{						
			MagicFakeBeam.spawnFakeBeam ( 
				Global.getCombatEngine(), 
				origin, 
				MathUtils.getDistance(proj,origin), 
				VectorUtils.getAngle(origin, new Vector2f(proj.getLocation().getX(),proj.getLocation().getY())),
				8f, 
				amount, 
				amount, 
				0f, 
				new Color(100,100,100,200), 
				new Color(15,15,15,255), 
				0f, 
				DamageType.ENERGY, 
				0f, 
				proj.getSource() 
				);
		}
		
		float targetMass;
		fireInterval.advance(.05f);		
		if (fireInterval.intervalElapsed()) 
		{

			if (target instanceof ShipAPI) 
			{
			   // hitShield = target.getShield() != null && target.getShield().isWithinArc(beam.getTo());
				if (((ShipAPI) target).getParentStation() != null && ((ShipAPI) target).isAlive())
					target = ((ShipAPI) target).getParentStation();
				targetMass = ((ShipAPI) target).getMassWithModules();
				if(Math.random() > 0.8f)
				{
					EmpArcEntityAPI vizArc = Global.getCombatEngine().spawnEmpArcVisual(
					origin, source, proj.getLocation(), proj, 0.5f, new Color(255,150,255,200), new Color(255,175,255,255)); 
					float dam = proj.getWeapon().getDamage().getDamage() * 0.4f;
					float empdam =proj.getWeapon().getDamage().getFluxComponent() * 0.5f;
					EmpArcEntityAPI arc =  Global.getCombatEngine().spawnEmpArc((ShipAPI)source, proj.getLocation(), target, target,
							DamageType.ENERGY,
							dam,
							dam,
							10000f,
							"system_emp_emitter_impact",
							2f,
							new Color(255,150,255,200),
							new Color(255,175,255,255)
							);
				}
			
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
		 //   if (hitShield) {
		  //      shieldModifier *= SHIELD_EFFECT;
		   // }

			// Direction & distance of the beam
			Vector2f dir = Vector2f.sub(origin, proj.getLocation(), new Vector2f());
			float distance = dir.length();
			if(distance > proj.getWeapon().getRange())
			{
				Global.getCombatEngine().removePlugin(this);
				return;				
			}
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
			if(distance > 100)
			{
				Vector2f dirClone = new Vector2f(dir);
				Vector2f.add((Vector2f)dir.scale(pushMe * shieldModifier * distanceModifier * -1f),source.getVelocity(),source.getVelocity());
				Vector2f.add((Vector2f)dirClone.scale(pushYou * shieldModifier * distanceModifier),target.getVelocity(),target.getVelocity());
			}
                        
                        else
                        {
                            proj.explode();
                            Global.getCombatEngine().removePlugin(this);                            
                        }

		}
		
	}
}