package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lwjgl.util.vector.Vector2f;
import org.lazywizard.lazylib.combat.CombatUtils;
import java.util.ArrayList;
import java.util.List;
import org.lazywizard.lazylib.VectorUtils;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.FaderUtil;
import com.fs.starfarer.api.graphics.SpriteAPI;
import java.util.Iterator;

public class armaa_curvyLaserMirvEffect extends BaseCombatLayeredRenderingPlugin implements EveryFrameWeaponEffectPlugin {

	private IntervalUtil interval = new IntervalUtil(0.05f,0.05f);
	protected List<armaa_curvyLaserMirvEffect> trails;
    private List<DamagingProjectileAPI> alreadyRegisteredProjectiles = new ArrayList<DamagingProjectileAPI>();

	public armaa_curvyLaserMirvEffect() {
	}
    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) 
	{
		interval.advance(amount);
		
		if(interval.intervalElapsed())
		{
			String weaponId = "armaa_aleste_flamer_right_copy";
			DamagingProjectileAPI projectile = (DamagingProjectileAPI)engine.spawnProjectile(weapon.getShip(),weapon,weaponId,weapon.getLocation(),0f,weapon.getShip().getVelocity());
		
			String prevKey = "cryo_prev_" + weapon.getShip().getId() + "_" + weapon.getSlot().getId();
			DamagingProjectileAPI prev = (DamagingProjectileAPI) engine.getCustomData().get(prevKey);
			ShipAPI source = weapon.getShip();
			ShipAPI target = null;

			if(source.getWeaponGroupFor(weapon)!=null ){
				//WEAPON IN AUTOFIRE
				if(source.getWeaponGroupFor(weapon).isAutofiring()  //weapon group is autofiring
						&& source.getSelectedGroupAPI()!=source.getWeaponGroupFor(weapon)){ //weapon group is not the selected group
					target = source.getWeaponGroupFor(weapon).getAutofirePlugin(weapon).getTargetShip();
				}
				else {
					target = source.getShipTarget();
				}
			}
				armaa_curvyLaserMirvEffect trail = new armaa_curvyLaserMirvEffect(projectile, prev);
				CombatEntityAPI e = engine.addLayeredRenderingPlugin(trail);
				e.getLocation().set(projectile.getLocation());
			engine.getCustomData().put(prevKey, projectile);
				if (projectile.getWeapon() == weapon && !alreadyRegisteredProjectiles.contains(projectile) && engine.isEntityInPlay(projectile) && !projectile.didDamage()) 
				{
					engine.addPlugin(new armaa_curveLaserProjectileScript(projectile, target));
					alreadyRegisteredProjectiles.add(projectile);
				}		
			if (trails == null) {
				trails = new ArrayList<armaa_curvyLaserMirvEffect>();		
			}
			trails.add(0, trail);

			//And clean up our registered projectile list
			List<DamagingProjectileAPI> cloneList = new ArrayList<>(alreadyRegisteredProjectiles);
			for (DamagingProjectileAPI proj : cloneList) {
				if (!engine.isEntityInPlay(proj) || proj.didDamage()) {
					alreadyRegisteredProjectiles.remove(proj);
				}
			}
			
		}
		
		if (trails == null) return;
		
		Iterator<armaa_curvyLaserMirvEffect> iter = trails.iterator();
		while (iter.hasNext()) {
			if (iter.next().isExpired()) iter.remove();
		}
		
		// sound loop playback
		if (weapon.getShip() != null) {
			float maxRange = weapon.getRange();
			ShipAPI ship = weapon.getShip();
			Vector2f com = new Vector2f();
			float weight = 0f;
			float totalDist = 0f;
			Vector2f source = weapon.getLocation();
			for (armaa_curvyLaserMirvEffect curr : trails) {
				if (curr.proj != null) {
					Vector2f.add(com, curr.proj.getLocation(), com);
					weight += curr.proj.getBrightness();
					totalDist += Misc.getDistance(source, curr.proj.getLocation());
				}
			}
			if (weight > 0.1f) {
				com.scale(1f / weight);
				float volume = Math.min(weight, 1f);
				if (trails.size() > 0) {
					totalDist /= (float) trails.size();
					float mult = totalDist / Math.max(maxRange, 1f);
					mult = 1f - mult;
					if (mult > 1f) mult = 1f;
					if (mult < 0f) mult = 0f;
					mult = (float) Math.sqrt(mult);
					volume *= mult;
				}
				Global.getSoundPlayer().playLoop("high_intensity_laser_loop", ship, 1f, volume, com, ship.getVelocity());
			}
		}
		
		
		//System.out.println("Trails: " + trails.size());
		float numIter = 1f; // more doesn't actually change anything
		amount /= numIter;
		// drag along the previous projectile, starting with the most recently launched; new ones are added at the start
		// note: prev is fired before and so is in front of proj
		for (int i = 0; i < numIter; i++) { 
			for (armaa_curvyLaserMirvEffect trail : trails) {
				//trail.proj.setFacing(trail.proj.getFacing() + 180f * amount);
				if (trail.prev != null && !trail.prev.isExpired() && Global.getCombatEngine().isEntityInPlay(trail.prev)) {
					float dist1 = Misc.getDistance(trail.prev.getLocation(), trail.proj.getLocation());
					if (dist1 < trail.proj.getProjectileSpec().getLength() * 1f) {
						float maxSpeed = trail.prev.getMoveSpeed() * 0.5f;// * Math.max(0.5f, 1f - trail.prev.getElapsed() * 0.5f);
						// goal here is to prevent longer shot series (e.g. from Paragon) from moving too unnaturally
						float e = trail.prev.getElapsed();
						float t = 0.5f;
						if (e > t) {
							maxSpeed *= Math.max(0.25f, 1f - (e - t) * 0.5f);
						}
						if (dist1 < 20f && e > t) {
							maxSpeed *= dist1 / 20f;
						}
						
						Vector2f driftTo = Misc.closestPointOnLineToPoint(trail.proj.getLocation(), trail.proj.getTailEnd(), trail.prev.getLocation());
						float dist = Misc.getDistance(driftTo, trail.prev.getLocation());
						Vector2f diff = Vector2f.sub(driftTo, trail.prev.getLocation(), new Vector2f());
						diff = Misc.normalise(diff);
						diff.scale(Math.min(dist, maxSpeed * amount));
						Vector2f.add(trail.prev.getLocation(), diff, trail.prev.getLocation());
						Vector2f.add(trail.prev.getTailEnd(), diff, trail.prev.getTailEnd());
					}
				}
			}
		}

    }
	public static class ParticleData {
		public SpriteAPI sprite;
		public Vector2f offset = new Vector2f();
		public Vector2f vel = new Vector2f();
		public float scale = 1f;
		public DamagingProjectileAPI proj;
		public float scaleIncreaseRate = 1f;
		public float turnDir = 1f;
		public float angle = 1f;
		public FaderUtil fader;
		
		public ParticleData(DamagingProjectileAPI proj) {
			this.proj = proj;
			sprite = Global.getSettings().getSprite("misc", "nebula_particles");
			//sprite = Global.getSettings().getSprite("misc", "dust_particles");
			float i = Misc.random.nextInt(4);
			float j = Misc.random.nextInt(4);
			sprite.setTexWidth(0.25f);
			sprite.setTexHeight(0.25f);
			sprite.setTexX(i * 0.25f);
			sprite.setTexY(j * 0.25f);
			sprite.setAdditiveBlend();
			
			angle = (float) Math.random() * 360f;
			
			float maxDur = proj.getWeapon().getRange() / proj.getWeapon().getProjectileSpeed();
			scaleIncreaseRate = 2f / maxDur;
			scale = 1f;
//			scale = 0.1f;
//			scaleIncreaseRate = 2.9f / maxDur;
//			scale = 0.1f;
//			scaleIncreaseRate = 2.5f / maxDur;
//			scale = 0.5f;
			
			turnDir = Math.signum((float) Math.random() - 0.5f) * 60f * (float) Math.random();
			//turnDir = 0f;
			
			float driftDir = (float) Math.random() * 360f;
			vel = Misc.getUnitVectorAtDegreeAngle(driftDir);
			vel.scale(proj.getProjectileSpec().getWidth() / maxDur * 0.33f);
			
//			offset.x += vel.x * 1f;
//			offset.y += vel.y * 1f;
			fader = new FaderUtil(0f, 0.25f, 0.5f);
			fader.fadeIn();
		}
		
		public void advance(float amount) {
			scale += scaleIncreaseRate * amount;
			if (scale < 1f) {
				scale += scaleIncreaseRate * amount * 1f;
			}
			
			offset.x += vel.x * amount;
			offset.y += vel.y * amount;
			
			angle += turnDir * amount;
			
			fader.advance(amount);
		}
	}
	
	protected List<ParticleData> particles = new ArrayList<ParticleData>();
	
	protected DamagingProjectileAPI proj;
	protected DamagingProjectileAPI prev;
	protected float baseFacing = 0f;
	public armaa_curvyLaserMirvEffect(DamagingProjectileAPI proj, DamagingProjectileAPI prev) {
		this.proj = proj;
		this.prev = prev;
		
		baseFacing = proj.getFacing();
		
		int num = 7;
		for (int i = 0; i < num; i++) {
			particles.add(new ParticleData(proj));
		}
		
		float length = proj.getProjectileSpec().getLength();
		float width = proj.getProjectileSpec().getWidth();
		
		float index = 0;
		for (ParticleData p : particles) {
			float f = index / (particles.size() - 1);
			Vector2f dir = Misc.getUnitVectorAtDegreeAngle(proj.getFacing() + 180f);
			dir.scale(length * f);
			Vector2f.add(p.offset, dir, p.offset);
			
			p.offset = Misc.getPointWithinRadius(p.offset, width * 0.5f);
			p.scale = 0.25f + 0.75f * (1 - f);
			
			index++;
		}
	}

    private Vector2f calculateMuzzle(WeaponAPI weapon){
        float muzzle;
				Vector2f origin = new Vector2f(weapon.getLocation());
				Vector2f offset = new Vector2f(5f, -15f);
				VectorUtils.rotate(offset, weapon.getCurrAngle(), offset);
				Vector2f.add(offset, origin, origin);
				return origin;

    }
}