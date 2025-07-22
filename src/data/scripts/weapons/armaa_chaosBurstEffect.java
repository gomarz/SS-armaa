package data.scripts.weapons;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;
import com.fs.starfarer.api.util.IntervalUtil;
import java.awt.Color;
import com.fs.starfarer.api.Global;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.util.*;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.ReadableVector2f;

import java.util.List;
import java.util.Iterator;


public class armaa_chaosBurstEffect implements EveryFrameWeaponEffectPlugin 
{
    private static final Vector2f ZERO = new Vector2f();
    private float CHARGEUP_PARTICLE_ANGLE_SPREAD = 360f;
    private float CHARGEUP_PARTICLE_BRIGHTNESS = 1f;
    private float CHARGEUP_PARTICLE_DISTANCE_MAX = 200f;
    private float CHARGEUP_PARTICLE_DISTANCE_MIN = 100f;
    private float CHARGEUP_PARTICLE_DURATION = 0.5f;
    private float CHARGEUP_PARTICLE_SIZE_MAX = 5f;
    private float CHARGEUP_PARTICLE_SIZE_MIN = 1f;
    public float TURRET_OFFSET = 30f;
	//sliver cannon charging fx
    private boolean charging = false;
    private boolean cooling = false;
    private boolean firing = false;
    private final IntervalUtil interval = new IntervalUtil(0.015f, 0.015f);
	protected IntervalUtil interval2 = new IntervalUtil(0.015f, 0.25f);
    private float level = 0f;
	public static float TARGET_RANGE = 100f;
	public static float RIFT_RANGE = 50f;
	
	private boolean runOnce = false;
    private boolean hasFired = false;
	//public float TURRET_OFFSET = 45f;
    private final IntervalUtil particle = new IntervalUtil(0.025f, 0.05f);
	private final IntervalUtil particle2 = new IntervalUtil(.1f, .2f);
	
	float charge = 0f;
	private static final int SMOKE_SIZE_MIN = 10;
    private static final int SMOKE_SIZE_MAX = 30;

   
    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) 
	{
		ShipAPI ship = weapon.getShip();
		Color color = new Color(247, 176, 52, 255);
		Color color2 = new Color(247, 226, 188, 255);		
		if(engine.isPaused() || weapon.getShip().getOriginalOwner()==-1 || ship.getFluxTracker().isOverloaded() || weapon.isDisabled())
			return;
			
		TURRET_OFFSET = weapon.getSpec().getTurretFireOffsets().get(0).x;
		float OFFSET_Y = weapon.getSpec().getTurretFireOffsets().get(0).y;
		if(weapon.getChargeLevel() > 0f && weapon.getCooldownRemaining() > 0f)
		{
			particle2.advance(amount);
			if (particle2.intervalElapsed()) 
			{
				Vector2f origin = new Vector2f(weapon.getLocation());
				Vector2f offset = new Vector2f(TURRET_OFFSET,OFFSET_Y);
				VectorUtils.rotate(offset, weapon.getCurrAngle(), offset);
				Vector2f.add(offset, origin, origin);
				float size = MathUtils.getRandomNumberInRange(SMOKE_SIZE_MIN , SMOKE_SIZE_MAX);
				float x = MathUtils.getRandomNumberInRange(-20,20);
				float y = MathUtils.getRandomNumberInRange(-20,20);
				Global.getCombatEngine().addNebulaSmoothParticle(origin, offset, size*weapon.getChargeLevel(), 1f*weapon.getChargeLevel(), 0.5f*weapon.getChargeLevel(), 0.25f, 1f, new Color(0.7f,0.7f,0.7f,0.8f), true);				
				//Global.getCombatEngine().addSmokeParticle(origin, offset, size*weapon.getChargeLevel(), 1f*weapon.getChargeLevel(), 1f*weapon.getChargeLevel(), new Color(1f,1f,1f,.7f));
			}
		}
        if (weapon.isFiring()) {
            float charge = weapon.getChargeLevel();
			float size = 1;
			if(weapon.getSize() ==  WeaponAPI.WeaponSize.LARGE)
				size = 2;
            if (!hasFired) {
				
                    Global.getSoundPlayer().playLoop("beamchargeMeso", (Object)weapon, 1.0f, 1.0f, weapon.getLocation(), weapon.getShip().getVelocity());
                    particle.advance(amount);
                if (particle.intervalElapsed()) {
					
					Vector2f origin = new Vector2f(weapon.getLocation());
					Vector2f offset = new Vector2f(TURRET_OFFSET,OFFSET_Y);
					VectorUtils.rotate(offset, weapon.getCurrAngle(), offset);
					Vector2f.add(offset, origin, origin);
                    Vector2f loc = MathUtils.getPoint((Vector2f)weapon.getLocation(), (float)18.5f, (float)weapon.getCurrAngle());
                    Vector2f vel = weapon.getShip().getVelocity();
                    engine.addHitParticle(origin, vel, MathUtils.getRandomNumberInRange((float)20.0f*size, (float)(charge*size * 60.0f + 20.0f)), MathUtils.getRandomNumberInRange((float)0.5f, (float)(0.5f + charge)), MathUtils.getRandomNumberInRange((float)0.1f, (float)(0.1f + charge / 10.0f)), new Color(charge, charge/1.5f, charge/2.0f));
					engine.addSwirlyNebulaParticle(origin, new Vector2f(0.0f, 0.0f),MathUtils.getRandomNumberInRange((float)20.0f*size, (float)(charge*size * 60.0f + 20.0f)), 1.2f, 0.15f, 0.0f, 0.35f*charge, new Color(charge, charge/1.3f, charge/1.7f),false);

					
					Vector2f particleVel = MathUtils.getRandomPointInCircle((Vector2f)new Vector2f(), (float)(35.0f * charge));
					Vector2f particleLoc = new Vector2f();
					Vector2f.sub((Vector2f)origin, (Vector2f)new Vector2f((ReadableVector2f)particleVel), (Vector2f)particleLoc);
					Vector2f.add((Vector2f)vel, (Vector2f)particleVel, (Vector2f)particleVel);
					//if((float) Math.random() <= 0.5f)	
					//MagicLensFlare.createSharpFlare(engine, weapon.getShip(), particleLoc, 2, 6, 0, new Color(50f/255f, 150f/255f, 1f,1f*charge), new Color(1f, 1f, 1f,1f*charge));

                    for (int i = 0; i < 5; ++i) {
						 engine.addHitParticle(particleLoc, particleVel, MathUtils.getRandomNumberInRange((float)2.0f, (float)(charge * 2.0f + 2.0f)), MathUtils.getRandomNumberInRange((float)0.5f, (float)(0.5f + charge)), MathUtils.getRandomNumberInRange((float)0.75f, (float)(0.75f + charge / 4.0f)), new Color(charge, charge / 1.5f, charge/2f));
                    }
                }
            }
            if (charge == 1.0f) 
			{
				Vector2f origin = new Vector2f(weapon.getLocation());
				Vector2f offset = new Vector2f(TURRET_OFFSET,OFFSET_Y);
				VectorUtils.rotate(offset, weapon.getCurrAngle(), offset);
				Vector2f.add(offset, origin, origin);
				Vector2f loc = MathUtils.getPoint((Vector2f)weapon.getLocation(), (float)18.5f, (float)weapon.getCurrAngle());
				Vector2f vel = weapon.getShip().getVelocity();
                hasFired = true;
				 engine.addHitParticle(origin, vel, MathUtils.getRandomNumberInRange((float)20.0f*size, (float)(charge*size * 60.0f + 20.0f)), MathUtils.getRandomNumberInRange((float)0.5f, (float)(0.5f + charge)), MathUtils.getRandomNumberInRange((float)0.1f, (float)(0.1f + charge / 10.0f)), new Color(charge, charge/1.5f, charge/2.0f));
            }
        } else {
            hasFired = false;
		}
			
		List<BeamAPI> beams = weapon.getBeams();
		if (beams.isEmpty()) return;
		BeamAPI beam = beams.get(0);
		if (beam.getBrightness() < 1f) return;
	
		interval2.advance(amount * 2f);
		if (interval2.intervalElapsed()) {
			if (beam.getLengthPrevFrame() < 10) return;
			
			Vector2f loc;
			CombatEntityAPI target = findTarget(beam, beam.getWeapon(), engine);
			if (target == null || Math.random() < 0.25f) {
				loc = pickNoTargetDest(beam, beam.getWeapon(), engine);
			} else {
				loc = target.getLocation();
			}
			
			Vector2f from = Misc.closestPointOnSegmentToPoint(beam.getFrom(), beam.getRayEndPrevFrame(), loc);
			Vector2f to = Misc.getUnitVectorAtDegreeAngle(Misc.getAngleInDegrees(from, loc));
			//to.scale(Math.max(RIFT_RANGE * 0.5f, Math.min(Misc.getDistance(from, loc), RIFT_RANGE)));
			to.scale(Math.min(Misc.getDistance(from, loc), RIFT_RANGE));
			Vector2f.add(from, to, to);
			
			spawnMine(beam.getSource(), to);
//			float thickness = beam.getWidth();
//			EmpArcEntityAPI arc = engine.spawnEmpArcVisual(from, null, to, null, thickness, beam.getFringeColor(), Color.white);
//			arc.setCoreWidthOverride(Math.max(20f, thickness * 0.67f));
			//Global.getSoundPlayer().playSound("tachyon_lance_emp_impact", 1f, 1f, arc.getLocation(), arc.getVelocity());
		}
    }
	
		public void spawnMine(ShipAPI source, Vector2f mineLoc) {
		CombatEngineAPI engine = Global.getCombatEngine();
		
		
		//Vector2f currLoc = mineLoc;
		MissileAPI mine = (MissileAPI) engine.spawnProjectile(source, null, 
															  "armaa_valkazard_torso_chaosburst_minelayer", 
															  mineLoc, 
															  (float) Math.random() * 360f, null);
		if (source != null) {
			Global.getCombatEngine().applyDamageModifiersToSpawnedProjectileWithNullWeapon(
											source, WeaponType.MISSILE, false, mine.getDamage());
		}
		
		
		float fadeInTime = 0.05f;
		mine.getVelocity().scale(0);
		mine.fadeOutThenIn(fadeInTime);
		
		float liveTime = 0f;
		//liveTime = 0.01f;
		mine.setFlightTime(mine.getMaxFlightTime() - liveTime);
		mine.addDamagedAlready(source);
		mine.setNoMineFFConcerns(true);
	}

	public Vector2f pickNoTargetDest(BeamAPI beam, WeaponAPI weapon, CombatEngineAPI engine) {
		Vector2f from = beam.getFrom();
		Vector2f to = beam.getRayEndPrevFrame();
		float length = beam.getLengthPrevFrame();
		
		float f = 0.25f + (float) Math.random() * 0.75f;
		Vector2f loc = Misc.getUnitVectorAtDegreeAngle(Misc.getAngleInDegrees(from, to));
		loc.scale(length * f);
		Vector2f.add(from, loc, loc);
		
		return Misc.getPointWithinRadius(loc, RIFT_RANGE);
	}
	
	public CombatEntityAPI findTarget(BeamAPI beam, WeaponAPI weapon, CombatEngineAPI engine) {
		Vector2f to = beam.getRayEndPrevFrame();
		
		Iterator<Object> iter = Global.getCombatEngine().getAllObjectGrid().getCheckIterator(to,
																			RIFT_RANGE * 2f, RIFT_RANGE * 2f);
		int owner = weapon.getShip().getOwner();
		WeightedRandomPicker<CombatEntityAPI> picker = new WeightedRandomPicker<CombatEntityAPI>();
		while (iter.hasNext()) {
			Object o = iter.next();
			if (!(o instanceof MissileAPI) &&
					!(o instanceof ShipAPI)) continue;
			CombatEntityAPI other = (CombatEntityAPI) o;
			if (other.getOwner() == owner) continue;
			if (other instanceof ShipAPI) {
				ShipAPI ship = (ShipAPI) other;
				if (!ship.isFighter() && !ship.isDrone()) continue;
			}
			
			float radius = Misc.getTargetingRadius(to, other, false);
			Vector2f p = Misc.closestPointOnSegmentToPoint(beam.getFrom(), beam.getRayEndPrevFrame(), other.getLocation());
			float dist = Misc.getDistance(p, other.getLocation()) - radius;
			if (dist > TARGET_RANGE) continue;
			
			picker.add(other);
			
		}
		return picker.pick();
	}

}
