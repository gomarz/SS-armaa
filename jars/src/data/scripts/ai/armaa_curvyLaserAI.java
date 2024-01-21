package data.scripts.ai;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.GuidedMissileAI;
import com.fs.starfarer.api.combat.MissileAIPlugin;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import org.magiclib.util.MagicRender;
import org.magiclib.util.MagicTargeting;
import org.magiclib.util.MagicLensFlare;
import java.awt.Color;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.FaderUtil;
import com.fs.starfarer.api.graphics.SpriteAPI;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;
import data.scripts.weapons.armaa_curveLaserProjectileScript;
import org.lazywizard.lazylib.combat.CombatUtils;
import com.fs.starfarer.api.loading.DamagingExplosionSpec;

public class armaa_curvyLaserAI extends BaseCombatLayeredRenderingPlugin implements MissileAIPlugin, GuidedMissileAI {
	//This script combines the cyroflamer script with the Projectile Tracking script developed by Nicke535
	//Modified by shoi
    //////////////////////
    //     SETTINGS     //
    //////////////////////
    
    //Damping of the turn speed when closing on the desired aim. The smaller the snappier.
    private final float DAMPING=0.1f;    
    
    //max speed of the missile after modifiers.
    private final float MAX_SPEED;
    private CombatEngineAPI engine;
    private final MissileAPI missile;
    private CombatEntityAPI target;
    private Vector2f lead = new Vector2f();
	private Vector2f ZERO = new Vector2f();
    private boolean launch=true;
    private IntervalUtil trailtimer= new IntervalUtil(0.15f,0.15f);
	private IntervalUtil timer= new IntervalUtil(0.2f,0.3f);
	private IntervalUtil interval = new IntervalUtil(0.05f,0.07f);
	private IntervalUtil interval2 = new IntervalUtil(.06f,.06f);
	protected List<List> trailOfTrails;
	protected List<armaa_curvyLaserAI> trails;
    private static final Color MUZZLE_FLASH_COLOR = new Color(100, 200, 255, 50);
    private static final Color MUZZLE_FLASH_COLOR_ALT = new Color(255, 255, 255, 100);
    private static final Color MUZZLE_FLASH_COLOR_GLOW = new Color(0, 75, 255, 50);
    private static final float MUZZLE_FLASH_DURATION = 0.10f;
    private static final float MUZZLE_FLASH_SIZE = 40.0f;
	
	private boolean inRange = false;
	private int count = 0; // number of proj's that have been created;
	private int total = 0;
	private int beamLength = 1; //The number of projectiles making up the stream;
	private int beamNo = 0;
	private float angle = 0f;
	private float angleIncrease = 0f;
	private int side = 1;
	private String targetPointKey;
    private static final float CONE_ANGLE = 180f;
    // one half of the angle. used internally, don't mess with thos
    private static final float A_2 = CONE_ANGLE / 2;
    private List<MissileAPI> alreadyRegisteredProjectiles = new ArrayList<MissileAPI>();

    public armaa_curvyLaserAI(MissileAPI missile, ShipAPI launchingShip) {	
	
		
        if (engine != Global.getCombatEngine()) {
            this.engine = Global.getCombatEngine();
        }        
        this.missile = missile;
		WeaponAPI weapon = missile.getWeapon();
        MAX_SPEED = missile.getMaxSpeed();
        missile.setCollisionClass(CollisionClass.MISSILE_NO_FF); 
		if(Math.random() > 0.5f)
			angle = (MathUtils.getRandomNumberInRange(missile.getFacing()-A_2,missile.getFacing()+A_2))*side;
		targetPointKey = "armaa_beamTarget_"+ weapon.getShip().getId() + "_" + weapon.getSlot().getId()+"_"+beamNo;

    }

    @Override
    public void advance(float amount) {
        
        if(missile.isFizzling() || missile.isFading())
		{
            if(MagicRender.screenCheck(0.25f, missile.getLocation())){
                engine.addSmoothParticle(missile.getLocation(), missile.getVelocity(), 100, 0.5f, 0.25f, Color.blue);
                engine.addHitParticle(missile.getLocation(), missile.getVelocity(), 100, 1f, 0.1f, Color.white);
            }
			 DamagingExplosionSpec boom = new DamagingExplosionSpec(
								1f,
								300,
								150,
								500,
								50,
								CollisionClass.PROJECTILE_NO_FF,
								CollisionClass.PROJECTILE_FIGHTER,
								2,
								5,
								5,
								25,
								new Color(245,188,44),
								new Color(200,200,200)
						);
						boom.setDamageType(DamageType.ENERGY);
						boom.setShowGraphic(false);
						boom.setSoundSetId("mine_explosion");
						engine.spawnDamagingExplosion(boom, missile.getSource(), missile.getLocation(),false);
						
						//visual effect
						engine.addSmoothParticle(missile.getLocation(), new Vector2f(0,0), 300*2, 2, 0.1f, Color.white);
						engine.addHitParticle(missile.getLocation(), new Vector2f(0,0), 300*2, 1, 0.4f, MUZZLE_FLASH_COLOR_ALT);
						engine.spawnExplosion(missile.getLocation(), new Vector2f(0,0), MUZZLE_FLASH_COLOR, 125*2, 2);
						engine.spawnExplosion(missile.getLocation(), new Vector2f(0,0), Color.BLACK, 60*7, 3);
						engine.spawnExplosion(missile.getLocation(),new Vector2f(0,0), MUZZLE_FLASH_COLOR_GLOW, 50*8, 1.5f);			
            engine.removeEntity(missile);
            return;
        }
			
        if(!missile.isArmed())
		{
			if (Math.random() > 0.75) {
				engine.spawnExplosion(missile.getLocation(), missile.getVelocity(), MUZZLE_FLASH_COLOR_ALT, MUZZLE_FLASH_SIZE * 0.10f, MUZZLE_FLASH_DURATION);
			} else {
				engine.spawnExplosion(missile.getLocation(), missile.getVelocity(), MUZZLE_FLASH_COLOR, MUZZLE_FLASH_SIZE, MUZZLE_FLASH_DURATION);
			}
			engine.addSmoothParticle(missile.getLocation(), missile.getVelocity(), MUZZLE_FLASH_SIZE * 3f, 1f, MUZZLE_FLASH_DURATION * 2f, MUZZLE_FLASH_COLOR_GLOW);
            	
			missile.giveCommand(ShipCommand.ACCELERATE);
			trailtimer.advance(amount);
			if(trailtimer.intervalElapsed())
			{
				engine.addSmoothParticle(missile.getLocation(), ZERO, MUZZLE_FLASH_SIZE, 1f, MUZZLE_FLASH_DURATION * 4f, MUZZLE_FLASH_COLOR_GLOW);
				for(int i = 0; i <4; i++)
				{
					if (Math.random() > 0.75) {
						engine.spawnExplosion(missile.getLocation(), ZERO, MUZZLE_FLASH_COLOR_ALT, MUZZLE_FLASH_SIZE * 0.10f, MUZZLE_FLASH_DURATION);
					} else {
						engine.spawnExplosion(missile.getLocation(), ZERO, MUZZLE_FLASH_COLOR, MUZZLE_FLASH_SIZE, MUZZLE_FLASH_DURATION);
					}
					engine.addSmoothParticle(missile.getLocation(), ZERO, MUZZLE_FLASH_SIZE * 3f, 1f, MUZZLE_FLASH_DURATION * 2f, MUZZLE_FLASH_COLOR_GLOW);
						
					MagicLensFlare.createSharpFlare(
						engine,
						missile.getWeapon().getShip(),
						missile.getLocation(),
						4f,
						150f,
						missile.getFacing(),
						MUZZLE_FLASH_COLOR_GLOW,
						MUZZLE_FLASH_COLOR
						);
					MagicLensFlare.createSharpFlare(
						engine,
						missile.getWeapon().getShip(),
						missile.getLocation(),
						4f,
						150f,
						missile.getFacing(),
						MUZZLE_FLASH_COLOR_GLOW,
						MUZZLE_FLASH_COLOR_ALT
						);
				}
			}			
		}		
		
		else 
		{
			if(missile.getMaxFlightTime()*.6 > missile.getFlightTime())
			{
				missile.giveCommand(ShipCommand.DECELERATE);
				missile.giveCommand(ShipCommand.TURN_LEFT);
			}
			
			else
			{
				missile.giveCommand(ShipCommand.ACCELERATE);
			}
			
			if (Math.random() > 0.75) {
				engine.spawnExplosion(missile.getLocation(), missile.getVelocity(), MUZZLE_FLASH_COLOR_ALT, MUZZLE_FLASH_SIZE * 0.20f, MUZZLE_FLASH_DURATION);
			} else {
				engine.spawnExplosion(missile.getLocation(), missile.getVelocity(), MUZZLE_FLASH_COLOR, MUZZLE_FLASH_SIZE*2f, MUZZLE_FLASH_DURATION);
			}
			engine.addSmoothParticle(missile.getLocation(), missile.getVelocity(), MUZZLE_FLASH_SIZE * 6f, 1f, MUZZLE_FLASH_DURATION * 2f, MUZZLE_FLASH_COLOR_GLOW);
					
		}
        
        if(launch){            
            setTarget(MagicTargeting.pickTarget(missile,MagicTargeting.targetSeeking.NO_RANDOM,(int)missile.getWeapon().getRange(),90,0,1,1,1,1,false));
            timer.forceIntervalElapsed();
            launch=false;
        }
        if(target == null || (target instanceof ShipAPI && !((ShipAPI)target).isAlive()) || !engine.isEntityInPlay(target))
		{
			reacquireTarget();
		}
        //skip the AI if the game is paused, the missile is engineless or fading
        if (engine.isPaused() 
                || target == null 
                || (target instanceof ShipAPI && !((ShipAPI)target).isAlive()) 
                || !engine.isEntityInPlay(target) ){
			
            return;
        }
        
        
        timer.advance(amount);
        //finding lead point to aim to        
        if(timer.intervalElapsed()){
            //best intercepting point
            lead = AIUtils.getBestInterceptPoint(
                    missile.getLocation(),
                    MAX_SPEED, //if eccm is intalled the point is accurate, otherwise it's placed closer to the target (almost tailchasing)
                    target.getLocation(),
                    target.getVelocity()
            );                
            //null pointer protection
            if (lead == null) {
                lead = target.getLocation(); 
            }
        }
        
        //best velocity vector angle for interception
        float correctAngle = VectorUtils.getAngle(
                        missile.getLocation(),
                        lead
                );
        
        //target angle for interception        
        float aimAngle = MathUtils.getShortestRotation( missile.getFacing(), correctAngle);
        
        if (aimAngle < 0) {
            missile.giveCommand(ShipCommand.TURN_RIGHT);
        } else {
            missile.giveCommand(ShipCommand.TURN_LEFT);
        }  
        
        // Damp angular velocity if the missile aim is getting close to the targeted angle
        if (Math.abs(aimAngle) < Math.abs(missile.getAngularVelocity()) * DAMPING) {
            missile.setAngularVelocity(aimAngle / DAMPING);
        }
		
		///CURVY LASER///
		WeaponAPI weapon = missile.getWeapon();
		if(missile.isArmed())		
			interval.advance(amount);
		if(count < beamLength+1)
		{
			if(interval.intervalElapsed())
			{
				inRange = true;
				String weaponId = "armaa_aleste_flamer_right_copy";
				if(count != beamLength)
				{	
					MissileAPI projectile = (MissileAPI)engine.spawnProjectile(weapon.getShip(),weapon,weaponId,missile.getLocation(),angle,missile.getVelocity());
					
					engine.addSmoothParticle(projectile.getLocation(), projectile.getVelocity(), MUZZLE_FLASH_SIZE*1.5f, 1f, MUZZLE_FLASH_DURATION * 2f, MUZZLE_FLASH_COLOR_GLOW);
					if(count == 0)
					{
						String prevKey = "cryo_prev_" + weapon.getShip().getId() + "_" + weapon.getSlot().getId();
						targetPointKey = "armaa_beamTarget_"+ weapon.getShip().getId() + "_" + weapon.getSlot().getId()+"_"+beamNo;
						engine.getCustomData().put(targetPointKey,null);
						engine.getCustomData().put(prevKey,null);
						float variance = MathUtils.getRandomNumberInRange(-0.3f,0.3f);
						Global.getSoundPlayer().playSound("disintegrator_fire", 1f+variance, 1f+variance, projectile.getLocation(), projectile.getVelocity());
					}

					String prevKey = "cryo_prev_" + weapon.getShip().getId() + "_" + weapon.getSlot().getId();
					MissileAPI prev = (MissileAPI) engine.getCustomData().get(prevKey);
					String projBeam = "armaa_prevTrail_" + weapon.getShip().getId()+"_" + weapon.getSlot().getId();
					int prevBeam = beamNo;
					if(engine.getCustomData().get(projBeam) instanceof Integer)
						prevBeam = (int)engine.getCustomData().get(projBeam);

					ShipAPI source = weapon.getShip();
					CombatEntityAPI target1 = null;

					if(source.getWeaponGroupFor(weapon)!=null ){
						//WEAPON IN AUTOFIRE
						if(source.getWeaponGroupFor(weapon).isAutofiring()  //weapon group is autofiring
								&& source.getSelectedGroupAPI()!=source.getWeaponGroupFor(weapon)){ //weapon group is not the selected group
							target1 = source.getWeaponGroupFor(weapon).getAutofirePlugin(weapon).getTargetShip();
						}
						else {
							target1 = target;
						}
					}
						armaa_curvyLaserAI trail = new armaa_curvyLaserAI(projectile, prev,prevBeam);
						//CombatEntityAPI e = engine.addLayeredRenderingPlugin(trail);
						//e.getLocation().set(projectile.getLocation());
					engine.getCustomData().put(prevKey, projectile);
					engine.getCustomData().put(projBeam, prevBeam);
						if (projectile.getWeapon() == weapon && !alreadyRegisteredProjectiles.contains(projectile) && engine.isEntityInPlay(projectile) && !projectile.didDamage()) 
						{
							engine.addPlugin(new armaa_curveLaserProjectileScript(projectile, target1,beamNo,targetPointKey));
							alreadyRegisteredProjectiles.add(projectile);
						}		
						if(trailOfTrails == null)
							trailOfTrails = new ArrayList<List>();
					if (count == 0) 
					{
						trails = new ArrayList<armaa_curvyLaserAI>();
					}
					trails.add(0, trail);
					
				}
				
				count+=1;
				total++;
				//And clean up our registered projectile list
				List<MissileAPI> cloneList = new ArrayList<>(alreadyRegisteredProjectiles);
				for (MissileAPI proj : cloneList) {
					if (!engine.isEntityInPlay(proj) || proj.didDamage()) {
						alreadyRegisteredProjectiles.remove(proj);
					}
				}
			}
			
		}
		
		else
		{
			// we need to set the trail to null to prevent projectiles fired from other offset to merge
			// this seems to 'orphan' the last projectile of the previous beam, so we delete it
		//	trails = null;
			interval2.advance(amount);
			if(interval2.intervalElapsed())
			{
				trailOfTrails.add(0,trails);
				if(total != 100)
				trails =null;
				if(side == 1)
					side = -1;
				else
					side = 1;
				count = 0;
				engine.getCustomData().put(targetPointKey,null);
				beamNo++;
				angle = (MathUtils.getRandomNumberInRange(missile.getFacing()-A_2,missile.getFacing()+A_2))*side;//+angleIncrease;
				//angleIncrease+=10;
			}
			
		}
		
		if(trailOfTrails == null) return;
		if(trailOfTrails.isEmpty()) return;
		if(trailOfTrails.get(0) instanceof armaa_curvyLaserAI)
		{
		List<armaa_curvyLaserAI> currTrail2 = trailOfTrails.get(0);
		}
//		if (trails == null) return;
		
		for(int i=0; i < trailOfTrails.size();i++)
		{
			List<armaa_curvyLaserAI> currTrail = trailOfTrails.get(i);
			if(currTrail != null)
			{
				Iterator<armaa_curvyLaserAI> iter = currTrail.iterator();
				while (iter.hasNext()) {
					if (iter.next().isExpired()) iter.remove();
				}
			}
			// sound loop playback
			if (weapon.getShip() != null) 
			{
				float maxRange = weapon.getRange();
				ShipAPI ship = weapon.getShip();
				Vector2f com = new Vector2f();
				float weight = 0f;
				float totalDist = 0f;
				Vector2f source = weapon.getLocation();

				for (armaa_curvyLaserAI curr : currTrail) {
					if (curr.proj != null) {
						Vector2f.add(com, curr.proj.getLocation(), com);
						weight += curr.proj.getBrightness();
						totalDist += Misc.getDistance(source, curr.proj.getLocation());
					}
				}
				if (weight > 0.1f) {
					com.scale(1f / weight);
					float volume = Math.min(weight, 1f);
					if (currTrail.size() > 0) {
						totalDist /= (float) currTrail.size();
						float mult = totalDist / Math.max(maxRange, 1f);
						mult = 1f - mult;
						if (mult > 1f) mult = 1f;
						if (mult < 0f) mult = 0f;
						mult = (float) Math.sqrt(mult);
						volume *= mult;
					}
					//Global.getSoundPlayer().playLoop("high_intensity_laser_loop", ship, 1f, volume, com, ship.getVelocity());
				}
			}
			//String timer = String.valueOf(currTrail.size());
			//engine.maintainStatusForPlayerShip("Trails", "graphics/ui/icons/icon_repair_refit.png","Trails: ", timer ,true);
			//System.out.println("Trails: " + currTrail.size());
			float numIter = 1f; // more doesn't actually change anything
			amount /= numIter;
			// drag along the previous projectile, starting with the most recently launched; new ones are added at the start
			// note: prev is fired before and so is in front of proj
				for (int j = 0; j < numIter; j++) 
				{ 
					for (armaa_curvyLaserAI trail : currTrail) 
					{
						//trail.proj.setFacing(trail.proj.getFacing() + 180f * amount);
						if (trail.prev != null && !trail.prev.isExpired() && Global.getCombatEngine().isEntityInPlay(trail.prev)) 
						{
							float dist1 = Misc.getDistance(trail.prev.getLocation(), trail.proj.getLocation());

							if (dist1 < 80) 
							{
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
								
								Vector2f driftTo = Misc.closestPointOnLineToPoint(trail.proj.getLocation(), trail.proj.getLocation(), trail.prev.getLocation());
								float dist = Misc.getDistance(driftTo, trail.prev.getLocation());
								Vector2f diff = Vector2f.sub(driftTo, trail.prev.getLocation(), new Vector2f());
								diff = Misc.normalise(diff);
								diff.scale(Math.min(dist, maxSpeed * amount));
								Vector2f.add(trail.prev.getLocation(), diff, trail.prev.getLocation());
								//Vector2f.add(trail.prev.getTailEnd(), diff, trail.prev.getTailEnd());
							}
						}
					}
				}
		}
    }
	
	private void reacquireTarget() {
		List<CombatEntityAPI> potentialTargets = new ArrayList<>();
		CombatEntityAPI newTarget = null;
		for (ShipAPI potTarget : CombatUtils.getShipsWithinRange(missile.getLocation(), 1000f)) {
			if (potTarget.getOwner() == missile.getOwner()
					|| Math.abs(VectorUtils.getAngle(missile.getLocation(), potTarget.getLocation()) - missile.getFacing()) > 1000f
					|| potTarget.isHulk()) {
				continue;
			}
			if (potTarget.isPhased()) {
				continue;
			}
			if (potTarget.getHullSize().equals(ShipAPI.HullSize.FIGHTER)) {
				potentialTargets.add(potTarget);
			}
			if (potTarget.getHullSize().equals(ShipAPI.HullSize.FRIGATE)) {
				potentialTargets.add(potTarget);
			}
			if (potTarget.getHullSize().equals(ShipAPI.HullSize.DESTROYER)) {
				potentialTargets.add(potTarget);
			}
			if (potTarget.getHullSize().equals(ShipAPI.HullSize.CRUISER)) {
				potentialTargets.add(potTarget);
			}
			if (potTarget.getHullSize().equals(ShipAPI.HullSize.CAPITAL_SHIP)) {
				potentialTargets.add(potTarget);
			}
		}
		//If we found any eligible target, continue selection, otherwise we'll have to stay with no target
		if (!potentialTargets.isEmpty()) {
			for (CombatEntityAPI potTarget : potentialTargets) {
				if (newTarget == null) {
					newTarget = potTarget;
				} else if (MathUtils.getDistance(newTarget, missile.getLocation()) > MathUtils.getDistance(potTarget, missile.getLocation())) {
					newTarget = potTarget;
				}
			}

			//Once all that is done, set our target to the new target and select a new swarm point (if appropriate)
			target = newTarget;
		}
	}	
	
public static class ParticleData {
		public SpriteAPI sprite;
		public Vector2f offset = new Vector2f();
		public Vector2f vel = new Vector2f();
		public float scale = 1f;
		public MissileAPI proj;
		public float scaleIncreaseRate = 1f;
		public float turnDir = 1f;
		public float angle = 1f;
		public FaderUtil fader;
		
		public ParticleData(MissileAPI proj) {
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
			vel.scale(proj.getSpec().getGlowRadius() / maxDur * 0.33f);
			
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
	
	protected MissileAPI proj;
	protected MissileAPI prev;
	protected int prevBeam = beamNo;
	protected float baseFacing = 0f;
	public armaa_curvyLaserAI(MissileAPI proj, MissileAPI prev, int prevBeam) {
		this.proj = proj;
		this.prev = prev;
		this.prevBeam = prevBeam;
		MAX_SPEED = 0f;
		missile = null;
		
		baseFacing = proj.getFacing();
		
	int num = 7;
		for (int i = 0; i < num; i++) {
			particles.add(new ParticleData(proj));
		}
		
		float length = 10;
		float width = 15;
		
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

    @Override
    public CombatEntityAPI getTarget()
    {
        return target;
    }

    @Override
    public void setTarget(CombatEntityAPI target)
    {
        this.target = target;
    }
}