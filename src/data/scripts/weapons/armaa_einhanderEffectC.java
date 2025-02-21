package data.scripts.weapons;

import com.fs.starfarer.api.AnimationAPI;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.magiclib.util.MagicAnim;
import org.lazywizard.lazylib.combat.CombatUtils;
import java.awt.Color;
import com.fs.starfarer.api.Global;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.loading.DamagingExplosionSpec;

import java.util.ArrayList;
import java.util.List;
import com.fs.starfarer.api.combat.EmpArcEntityAPI;


/**
 * This is a heavily modified script using
 * @author Tartiflette
 * 's work as a basis
 */
public class armaa_einhanderEffectC implements EveryFrameWeaponEffectPlugin {

    private boolean runOnce = false, lockNloaded = false;
    private ShipSystemAPI system;
    private ShipAPI ship;
    private SpriteAPI sprite;
    private AnimationAPI anim, aGlow, aGlow2;
    private WeaponAPI head, armL, armR, pauldronL, pauldronR, torso, cannon;

    private boolean charging = false;
    private boolean cooling = false;
    private boolean firing = false;
    private final IntervalUtil interval = new IntervalUtil(0.015f, 0.015f);
    private final IntervalUtil interval2 = new IntervalUtil(0, 20f);
    private float level = 0f;
    private static final Vector2f ZERO = new Vector2f();
	private int lastAmmo = 0;
    private SpriteAPI HAND, SHOULDER;
    private float HAND_WIDTH, HAND_HEIGHT;
    private float CHARGEUP_PARTICLE_ANGLE_SPREAD = 360f;
    private float CHARGEUP_PARTICLE_BRIGHTNESS = 1f;
    private float CHARGEUP_PARTICLE_DISTANCE_MAX = 200f;
    private float CHARGEUP_PARTICLE_DISTANCE_MIN = 100f;
    private float CHARGEUP_PARTICLE_DURATION = 0.5f;
    private float CHARGEUP_PARTICLE_SIZE_MAX = 6f;
    private float CHARGEUP_PARTICLE_SIZE_MIN = 1f;
    public float TURRET_OFFSET = 30f;
    private int limbInit = 0; // used to make sure all limbs are accounted for
    private float overlap = 0;
    private final float TORSO_OFFSET = -45, LEFT_ARM_OFFSET = -75, RIGHT_ARM_OFFSET = -25, MAX_OVERLAP = 10;
	
	private List<DamagingProjectileAPI> registeredProjectiles = new ArrayList<DamagingProjectileAPI>();
	private List<DamagingProjectileAPI> explodingProjectiles = new ArrayList<DamagingProjectileAPI>();
	private List<DamagingProjectileAPI> toRemove = new ArrayList<DamagingProjectileAPI>();
	private final Color EMP_COLOR = new Color(200, 200, 0);
    private final Color FLASH_COLOR = new Color(255, 255, 255);

    public void init()
    {
        runOnce = true;

        for (WeaponAPI w : ship.getAllWeapons()) {
            switch (w.getSlot().getId()) {
                case "B_TORSO":
                    if(torso==null) {
                        torso = w;
                        limbInit++;
                    }
                    break;
                case "C_ARML":
                    if(armL==null) {
                        armL = w;
			HAND = w.getSprite();
			HAND_WIDTH=HAND.getWidth()/2;
			HAND_HEIGHT = HAND.getHeight()/2;
                        limbInit++;
                    }
                    break;
                case "C_ARMR":
                    if(armR==null) {
                        armR = w;
                        limbInit++;
                    }
                    break;
                case "D_PAULDRONL":
                    if(pauldronL == null) {
                        pauldronL = w;
                        limbInit++;
                    }
                    break;
                case "D_PAULDRONR":
                    if(pauldronR == null) {
                        pauldronR = w;
			SHOULDER = w.getSprite();
                        limbInit++;
                    }
                    break;
                case "E_HEAD":
                    if(head == null) {
                        head = w;
                        limbInit++;
                    }
                    break;
            }
        }

    }
	
    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {

        ship = weapon.getShip();
        sprite = ship.getSpriteAPI();
        system = ship.getSystem();
        anim = weapon.getAnimation();
        init();


        if(!runOnce || limbInit != 6)
        {
           return;
        }

            if (ship.getEngineController().isAccelerating()) {
                if (overlap > (MAX_OVERLAP - 0.1f)) {
                    overlap = MAX_OVERLAP;
                } else {
                    overlap = Math.min(MAX_OVERLAP, overlap + ((MAX_OVERLAP - overlap) * amount * 5));
                }
            } else if (ship.getEngineController().isDecelerating() || ship.getEngineController().isAcceleratingBackwards()) {
                if (overlap < -(MAX_OVERLAP - 0.1f)) {
                    overlap = -MAX_OVERLAP;
                } else {
                    overlap = Math.max(-MAX_OVERLAP, overlap + ((-MAX_OVERLAP + overlap) * amount * 5));
                }
            } else {
                if (Math.abs(overlap) < 0.1f) {
                    overlap = 0;
                } else {
                    overlap -= (overlap / 2) * amount * 3;
                }
            }

            float sineA = 0, sinceB = 0;
            SHOULDER.setCenterY(weapon.getBarrelSpriteAPI().getCenterY()-23f);
            if (system.isActive()) 
			{
				if(weapon.getChargeLevel() > 0 && system.getEffectLevel() == 1)
				{
					HAND.setCenterY(weapon.getBarrelSpriteAPI().getCenterY()-15f);
				}

                if (system.getEffectLevel() < 1) {
                    if (!lockNloaded) {
                        lockNloaded = true;
                    }
                    sineA = MagicAnim.smoothNormalizeRange(system.getEffectLevel(), 0, 0.7f);
                    sinceB = MagicAnim.smoothNormalizeRange(system.getEffectLevel(), 0.3f, 1f);
                } else {
                    sineA = 1;
                    sinceB = 1;
                }
            } else if (lockNloaded) 
			{
				lockNloaded = false;
            }

            float global = ship.getFacing();
            float aim = MathUtils.getShortestRotation(global, weapon.getCurrAngle());

            if (torso != null)
			{
                torso.setCurrAngle(global + sineA * TORSO_OFFSET + aim * 0.3f);
		
			}

            if (armR != null)
			{
                armR.setCurrAngle(weapon.getCurrAngle() + RIGHT_ARM_OFFSET);
			}

            if (pauldronR != null)
            {
                pauldronR.setCurrAngle(global + sineA * TORSO_OFFSET * 0.5f + aim * 0.75f + RIGHT_ARM_OFFSET * 0.5f);
			}
	    
				ship.syncWeaponDecalsWithArmorDamage();

            if (armL != null) 
			{
                armL.setCurrAngle(global+((aim + LEFT_ARM_OFFSET) * sinceB)+((overlap + aim * 0.25f) * (1 - sinceB)));
            }

            if (pauldronL != null)
                pauldronL.setCurrAngle(torso.getCurrAngle() + MathUtils.getShortestRotation(torso.getCurrAngle(), armL.getCurrAngle()) * 0.6f);
			
			
			if(system.isActive())
			{
				chargeFx(engine,weapon,amount,system);

				for (DamagingProjectileAPI proj : CombatUtils.getProjectilesWithinRange(ship.getLocation(), 80)) 
				{
					//if this is one of our projectiles and hasn't been accounted for
					if (weapon == proj.getWeapon() && !registeredProjectiles.contains(proj) && !explodingProjectiles.contains(proj)) 
					{
						//add it (prevent infinite bullet works)
						registeredProjectiles.add(proj);
						
						//If the weapon can actually fire
						float cooldown = weapon.getCooldownRemaining()*weapon.getShip().getMutableStats().getTimeMult().getMult();
						Color c1 = Color.red;
						if(engine.getPlayerShip() == weapon.getShip())
						{
							cooldown = weapon.getCooldownRemaining()*(1f/weapon.getShip().getMutableStats().getTimeMult().getMult());
							cooldown -= cooldown;
							if(cooldown > .7f)
								cooldown = 0;
							c1 = Color.yellow;
						}
					//weapon.getShip().getFluxTracker().showOverloadFloatyIfNeeded(String.valueOf(cooldown), c1, 4f, true);
					if(cooldown <= 0)
					{
							FluxTrackerAPI fluxLevel = weapon.getShip().getFluxTracker();
							//Spawn the new projectile
							DamagingProjectileAPI newProj = (DamagingProjectileAPI) engine.spawnProjectile(ship, weapon, "armaa_einhanderGun2", proj.getLocation(), proj.getFacing(), ship.getVelocity());
							
							if(fluxLevel.getCurrFlux()+weapon.getFluxCostToFire() > fluxLevel.getMaxFlux())
							{
								//fluxLevel.forceOverload(0f);
							}
							
							else
							fluxLevel.setCurrFlux(fluxLevel.getCurrFlux()+weapon.getFluxCostToFire()*1.21f);
						
							//ad the new proj for fx stuff later
							explodingProjectiles.add(newProj);
							registeredProjectiles.add(newProj);
							Global.getSoundPlayer().playSound("plasma_cannon_fire", 1f, 0.7f, newProj.getLocation(), newProj.getVelocity());
					}
						//remove old bullet
						Global.getCombatEngine().removeEntity(proj);
					}
				}
			}
            level = weapon.getChargeLevel();
			
			interval2.advance(amount);
			
			if(interval2.intervalElapsed())
			{
				for(DamagingProjectileAPI proj : registeredProjectiles)
				{
					if( !Global.getCombatEngine().isEntityInPlay(proj))
					{
						toRemove.add(proj);
					}
						
				}
				registeredProjectiles.removeAll(toRemove);
			}
			if(!explodingProjectiles.isEmpty())
			{
				//DamagingExplosionSpec boom = new DamagingExplosionSpec(.1f,70f,30f, 100f, 50f, CollisionClass.HITS_SHIPS_AND_ASTEROIDS, CollisionClass.PROJECTILE_NO_FF, 5f, 3f, 1f, 20,new Color(255,155,155,255),new Color(250,205,57,255));
				for(DamagingProjectileAPI proj : explodingProjectiles)
				{
					if(proj.isFading() || !Global.getCombatEngine().isEntityInPlay(proj))
					{	
						toRemove.add(proj);
						//engine.spawnDamagingExplosion(boom, ship, proj.getLocation(), false); 
						engine.addSmoothParticle(proj.getLocation(), new Vector2f(), 70, 0.5f, 0.3f, Color.yellow);
						engine.addHitParticle(proj.getLocation(), new Vector2f(), 50, 1f, 0.10f, Color.white);
					}
				}
			}
			explodingProjectiles.removeAll(toRemove);
        }

		private void chargeFx(CombatEngineAPI engine, WeaponAPI weapon, float amount, ShipSystemAPI system)
		{
				
				Vector2f origin = new Vector2f(weapon.getLocation());
				Vector2f offset = new Vector2f(TURRET_OFFSET, -15f);
				VectorUtils.rotate(offset, weapon.getCurrAngle(), offset);
				Vector2f.add(offset, origin, origin);
				float shipFacing = weapon.getCurrAngle();
				Vector2f shipVelocity = ship.getVelocity();
				float alpha = 1f*(weapon.getChargeLevel());
				//Default Color
				Color color = new Color((200f/255f)*alpha, (55f/255f)*alpha, (200f/255f)*alpha, alpha);
				//Particle Color
				Color color2 = new Color(200f/255f, 55f/255f, 200f/255f, 1f);
				//White Core
				Color color3 = new Color((255f/255f)*alpha,(255f/255f)*alpha,(255f/255f)*alpha,alpha);
				//This randomly alternates with Color1
				Color color4 = new Color((255f/255f)*alpha,0f,(255f/255f)*alpha,alpha);
				
				if(system.isActive())
				{
					color = new Color(250f/255f,236f/255f,40f/255f,alpha);
				}
				
				if(weapon.isFiring() && weapon.getChargeLevel() > 0f)
				{
				   float radius = 30f + (weapon.getChargeLevel() * weapon.getChargeLevel()* MathUtils.getRandomNumberInRange(25f, 75f));

					if (Math.random() > 0.5)
						engine.addHitParticle(origin, ZERO,(radius-10)*weapon.getChargeLevel(), 0.1f + weapon.getChargeLevel() * 0.3f, 0.2f, color);
					else
						engine.addHitParticle(origin, ZERO,(radius-10)*weapon.getChargeLevel(), 0.1f + weapon.getChargeLevel() * 0.3f, 0.2f, color4);

					 engine.addHitParticle(origin, ZERO, ((radius-10)/2), 0.1f + weapon.getChargeLevel() * 0.3f, 0.2f, color3);
				}

				if (charging) {
					if (firing && (weapon.getChargeLevel() < 1f)) {
						charging = false;
						cooling = true;
						firing = false;
					} else if (weapon.getChargeLevel() < 1f) {
						interval.advance(amount);
						if (interval.intervalElapsed()) {
							float radius = 20f + (weapon.getChargeLevel() * weapon.getChargeLevel()
									* MathUtils.getRandomNumberInRange(25f, 75f));
							if (Math.random() > 0.5)
								engine.addHitParticle(origin, ZERO,(radius-10)*weapon.getChargeLevel(), 0.1f + weapon.getChargeLevel() * 0.3f, 0.2f, color);
							else
								engine.addHitParticle(origin, ZERO,(radius-10)*weapon.getChargeLevel(), 0.1f + weapon.getChargeLevel() * 0.3f, 0.2f, color4);

							engine.addHitParticle(origin, ZERO, ((radius-10)/2)*weapon.getChargeLevel(), 0.1f + weapon.getChargeLevel() * 0.3f, 0.2f, color3);

							int count = 2 + (int) (weapon.getChargeLevel() * 5);
							for (int i = 0; i < count; i++) {
								float distance = MathUtils.getRandomNumberInRange(CHARGEUP_PARTICLE_DISTANCE_MIN,
										CHARGEUP_PARTICLE_DISTANCE_MAX)
										* weapon.getChargeLevel();
								float size = MathUtils.getRandomNumberInRange(CHARGEUP_PARTICLE_SIZE_MIN, CHARGEUP_PARTICLE_SIZE_MAX)*weapon.getChargeLevel();
								float angle = MathUtils.getRandomNumberInRange(-0.5f * CHARGEUP_PARTICLE_ANGLE_SPREAD, 0.5f
										* CHARGEUP_PARTICLE_ANGLE_SPREAD);
								float speed = (0.75f * distance / CHARGEUP_PARTICLE_DURATION)*weapon.getChargeLevel();
								Vector2f particleVelocity = MathUtils.getPointOnCircumference(shipVelocity, speed, angle + shipFacing
										+ 180f);
								engine.addHitParticle(origin, particleVelocity, size, CHARGEUP_PARTICLE_BRIGHTNESS * Math.min(
										weapon.getChargeLevel() + 0.5f, weapon.getChargeLevel()+1f)
												* MathUtils.getRandomNumberInRange(0.75f, 1.25f), CHARGEUP_PARTICLE_DURATION,
										color2);
							}
						}
					} else {
						firing = true;
					}
				} else {
					if (cooling) {
						if (weapon.getChargeLevel() <= 0f) {
							cooling = false;
						}
					} else if (weapon.getChargeLevel() > level) {
						charging = true;
					}
				}
		}
		
    }

