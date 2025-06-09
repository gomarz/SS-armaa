package data.scripts.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import org.lazywizard.lazylib.FastTrig;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import org.magiclib.util.MagicFakeBeam;
import static org.magiclib.util.MagicFakeBeam.getShipCollisionPoint;
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier;
import com.fs.starfarer.api.loading.ProjectileSpawnType;
import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import org.magiclib.util.MagicRender;
import java.util.Map;
import org.lazywizard.lazylib.CollisionUtils;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

public class armaa_rampagedrive extends BaseShipSystemScript {
    //massy private static Map mass_mult = new HashMap();
    public static Map bugs = new HashMap();
    private static Map wide = new HashMap();
    private static Map damage = new HashMap();
    private static Map SPEED_BOOST = new HashMap();
    private static Map DAMAGE_MULT = new HashMap();
	private static float AFTERIMAGE_THRESHOLD = 0.09f;
    static {
        /*mass_mult.put(HullSize.FRIGATE, 3f);
        mass_mult.put(HullSize.DESTROYER, 3f);
        mass_mult.put(HullSize.CRUISER, 2f);
        mass_mult.put(HullSize.CAPITAL_SHIP, 2f); massy*/
        bugs.put(HullSize.FIGHTER, 80f);
        bugs.put(HullSize.FRIGATE, 80f);
        bugs.put(HullSize.DESTROYER, 100f);
        bugs.put(HullSize.CRUISER, 160f);
        bugs.put(HullSize.CAPITAL_SHIP, 305f);
        wide.put(HullSize.FIGHTER, 40f);		
        wide.put(HullSize.FRIGATE, 80f);
        wide.put(HullSize.DESTROYER, 136f);
        wide.put(HullSize.CRUISER, 177f);
        wide.put(HullSize.CAPITAL_SHIP, 201f);
        damage.put(HullSize.FIGHTER, 10f);
        damage.put(HullSize.FRIGATE, 1000f);
        damage.put(HullSize.DESTROYER, 1500f);
        damage.put(HullSize.CRUISER, 2500f);
        damage.put(HullSize.CAPITAL_SHIP, 3500f);
        SPEED_BOOST.put(HullSize.FIGHTER, 25f);
        SPEED_BOOST.put(HullSize.FRIGATE, 250f);
        SPEED_BOOST.put(HullSize.DESTROYER, 275f);
        SPEED_BOOST.put(HullSize.CRUISER, 300f);
        SPEED_BOOST.put(HullSize.CAPITAL_SHIP, 300f);
        DAMAGE_MULT.put(HullSize.FIGHTER, 0.50f);
        DAMAGE_MULT.put(HullSize.FRIGATE, 0.33f);
        DAMAGE_MULT.put(HullSize.DESTROYER, 0.33f);
        DAMAGE_MULT.put(HullSize.CRUISER, 0.5f);
        DAMAGE_MULT.put(HullSize.CAPITAL_SHIP, 0.5f);
    }
    private static final Color color = new Color(255, 135, 240, 0);
    public static final float MASS_MULT = 1.2f;
    //public static final float RANGE = 600f;
    public static final float ROF_MULT = 0.5f;
    private static String poopystinky = "Engines and Armor boosted";
    private static String poopystinky2 = "Reduced weapons rate of fire";
    private static String poopystinky3 = "READY";
    
    private boolean reset = true;
    //private float activeTime = 0f;
    //private float jitterLevel;
    private boolean DidRam = false;
    
    private Float mass = null;

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        CombatEngineAPI engine =  Global.getCombatEngine();
        ShipAPI ship = (ShipAPI) stats.getEntity();
        if (engine.isPaused() || ship == null) {
            return;
        }

        if (mass == null) {
            mass = ship.getMass();
        }
        
        if (reset) {
            reset = false;
            //activeTime = 0f;
            //jitterLevel = 0f;
            DidRam = false;
        }

        ShipAPI target = ship.getShipTarget();
		ShipAPI nearestAlly = ship;		
		float dangerLevel = -2f;	
		if(engine.getPlayerShip() != ship && target != null)
		{
			List<ShipAPI> potentialTargets = CombatUtils.getShipsWithinRange(ship.getLocation(),2000f);			
			for(ShipAPI pt : potentialTargets)
			{
				if(!ship.isFighter() && pt.isFighter())
					continue;				
				int mult = ship.getOwner() == pt.getOwner() ? -1 : 1;
				if(pt.isFrigate())
					dangerLevel+=1*mult;
				else if(pt.isDestroyer())
					dangerLevel+=2*mult;
				else if(pt.isCruiser())
					dangerLevel+=3*mult;
				else if(pt.isCapital())
					dangerLevel+=4*mult;			
			}
			potentialTargets = CombatUtils.getShipsWithinRange(ship.getLocation(),4000f);
			for(ShipAPI pt : potentialTargets)
			{
				if(!ship.isFighter() && pt.isFighter())
					continue;		
				if(ship.getOwner() != pt.getOwner())
					continue;		
				if(MathUtils.getDistance(ship, pt) < MathUtils.getDistance(ship, nearestAlly))
				{
					nearestAlly = pt;
				}				
			
			}				
			potentialTargets = CombatUtils.getShipsWithinRange(ship.getLocation(),4000f);			
			for(ShipAPI pt : potentialTargets)
			{
				if(!ship.isFighter() && pt.isFighter())
					continue;							
				if(ship.getOwner() == pt.getOwner())
					continue;
				if(MathUtils.getDistance(ship, pt) < MathUtils.getDistance(ship, target))
				{
					ship.setShipTarget(pt);
				}
			}
		}
        float turnrate = ship.getMaxTurnRate()*2;

        if (state == State.OUT) {
            stats.getMaxSpeed().unmodify(id); // to slow down ship to its regular top speed while powering drive down
            stats.getBallisticRoFMult().unmodify(id);
            stats.getEnergyRoFMult().unmodify(id);
            ship.setMass(mass);
            //if (ship.hasListenerOfClass(RampageDriveListener.class)) {ship.removeListenerOfClass(RampageDriveListener.class);}
            DidRam = false;
        } else {
            if (ship.getMass() == mass) {
                ship.setMass(mass * MASS_MULT);
                if (!ship.hasListenerOfClass(RampageDriveListener.class)) {ship.addListener(new RampageDriveListener());}
            }
            stats.getMaxSpeed().modifyFlat(id, (Float) SPEED_BOOST.get(ship.getHullSize()));
            stats.getAcceleration().modifyFlat(id, (Float) SPEED_BOOST.get(ship.getHullSize()) * 4);
            stats.getEmpDamageTakenMult().modifyMult(id, (Float) DAMAGE_MULT.get(ship.getHullSize()));
            stats.getArmorDamageTakenMult().modifyMult(id, (Float) DAMAGE_MULT.get(ship.getHullSize()));
            stats.getHullDamageTakenMult().modifyMult(id, (Float) DAMAGE_MULT.get(ship.getHullSize()));

            stats.getBallisticRoFMult().modifyMult(id, ROF_MULT);
            stats.getEnergyRoFMult().modifyMult(id, ROF_MULT);
            if (!DidRam) {
                Vector2f from = ship.getLocation();
                float angle = ship.getFacing();
                Vector2f end = MathUtils.getPoint(from, (Float) bugs.get(ship.getHullSize()), angle);
                List <CombatEntityAPI> entity = CombatUtils.getEntitiesWithinRange(ship.getLocation(), (Float) bugs.get(ship.getHullSize())+25f);
                if (!entity.isEmpty()) {
                    for (CombatEntityAPI e : entity) {
                        if (e.getCollisionClass() == CollisionClass.NONE){continue;}
                        if (e.getOwner() == ship.getOwner()) {continue;}
                        Vector2f col = new Vector2f(1000000,1000000);                  
                        if (e instanceof ShipAPI ){                    
                            if(e!=ship && ((ShipAPI)e).getParentStation()!=e && (e.getCollisionClass()!=CollisionClass.NONE && e.getCollisionClass() != CollisionClass.FIGHTER) && CollisionUtils.getCollides(ship.getLocation(), end, e.getLocation(), e.getCollisionRadius())) {
                                            //&&
                                            //!(e.getCollisionClass()==CollisionClass.FIGHTER && e.getOwner()==ship.getOwner() && !((ShipAPI)e).getEngineController().isFlamedOut())               
                                ShipAPI s = (ShipAPI) e;
                                Vector2f hitPoint = (Vector2f) getShipCollisionPoint(from, end, s, angle);
                                if (hitPoint != null ){col = hitPoint;}
                            }
                            if (col.x != 1000000 && MathUtils.getDistanceSquared(from, col) < MathUtils.getDistanceSquared(from, end)) {
                                DidRam = true;
                                MagicFakeBeam.spawnFakeBeam(engine, ship.getLocation(), (Float) bugs.get(ship.getHullSize()), ship.getFacing(), (Float) wide.get(ship.getHullSize()), 0.1f, 0.1f, 25, color, color, (Float) damage.get(ship.getHullSize()), DamageType.HIGH_EXPLOSIVE, 0, ship);
                                Global.getSoundPlayer().playSound("collision_ships", 1f, 0.5f, ship.getLocation(), ship.getVelocity());
                                //engine.addFloatingText(ship.getLocation(), "Yamete!", 25f, Color.WHITE, ship, 1f, 0.5f);
                            }
                        }
                    }
                }
            }
            if(ship.isDirectRetreat() && ship.getSystem().isActive())
			{
				ship.setAngularVelocity(Math.min(turnrate, Math.max(-turnrate, MathUtils.getShortestRotation(ship.getFacing(),ship.getOwner() == 0 ? Global.getCombatEngine().getFleetManager(ship.getOwner()).getGoal() == FleetGoal.ESCAPE ? 90f : 270f : Global.getCombatEngine().getFleetManager(ship.getOwner()).getGoal() == FleetGoal.ESCAPE ? 270f : 90f)*2)));
			}
			else if(engine.getPlayerShip() != ship && ship.getSystem().isActive() && (dangerLevel >= 7 || ship.getHardFluxLevel() >= 0.7f) && nearestAlly != ship)
			{
                float facing = ship.getFacing();
                if ((nearestAlly.isFighter() || nearestAlly.isDrone()) && nearestAlly.getWing() != null && nearestAlly.getWing().getSourceShip() != null && nearestAlly.getWing().getSourceShip().isAlive()) {
                    facing=MathUtils.getShortestRotation(facing,VectorUtils.getAngle(ship.getLocation(), nearestAlly.getWing().getSourceShip().getLocation()));
                } else {
                    //if ((target.isFighter() || target.isDrone()) && ship.getAIFlags().getCustom(AIFlags.MANEUVER_TARGET) instanceof ShipAPI) {engine.addFloatingText(ship.getLocation(), "My Life For Ava!", 15f, Color.WHITE, ship, 1f, 0.5f);ship.setShipTarget((ShipAPI) ship.getAIFlags().getCustom(AIFlags.MANEUVER_TARGET));}
                facing=MathUtils.getShortestRotation(
                        facing,
                        VectorUtils.getAngle(ship.getLocation(), nearestAlly.getLocation())
                );}
                if (!nearestAlly.isFighter() && !nearestAlly.isDrone()) {
                    ship.setAngularVelocity(Math.min(turnrate, Math.max(-turnrate, facing*5)));
                } else {ship.setAngularVelocity(Math.min(turnrate, Math.max(-turnrate, facing*2)));}
				
			}
            else if((target != null && target.isAlive() && !target.isAlly()) && ship.getSystem().isActive()) 
			{
                float facing = ship.getFacing();
                if ((target.isFighter() || target.isDrone()) && target.getWing() != null && target.getWing().getSourceShip() != null && target.getWing().getSourceShip().isAlive()) {
                    facing=MathUtils.getShortestRotation(facing,VectorUtils.getAngle(ship.getLocation(), target.getWing().getSourceShip().getLocation()));
                } else {
                    //if ((target.isFighter() || target.isDrone()) && ship.getAIFlags().getCustom(AIFlags.MANEUVER_TARGET) instanceof ShipAPI) {engine.addFloatingText(ship.getLocation(), "My Life For Ava!", 15f, Color.WHITE, ship, 1f, 0.5f);ship.setShipTarget((ShipAPI) ship.getAIFlags().getCustom(AIFlags.MANEUVER_TARGET));}
                facing=MathUtils.getShortestRotation(
                        facing,
                        VectorUtils.getAngle(ship.getLocation(), target.getLocation())
                );}
                if (!target.isFighter() && !target.isDrone()) {
                    ship.setAngularVelocity(Math.min(turnrate, Math.max(-turnrate, facing*5)));
                } else {ship.setAngularVelocity(Math.min(turnrate, Math.max(-turnrate, facing*2)));}
            }
			float amount = Global.getCombatEngine().getElapsedInLastFrame();
			//afterimage shit from tahlan
			ship.getMutableStats().getDynamic().getStat("armaa_NNAfterimageTracker").modifyFlat("armaa_NNAfterimageTrackerNullerID", -1);
			ship.getMutableStats().getDynamic().getStat("armaa_NNAfterimageTracker").modifyFlat("armaa_NNAfterimageTrackerID",
			ship.getMutableStats().getDynamic().getStat("armaa_NNAfterimageTracker").getModifiedValue() + amount);
				
			if(ship.getMutableStats().getDynamic().getStat("armaa_NNAfterimageTracker").getModifiedValue() > AFTERIMAGE_THRESHOLD) 
			{

				// Sprite offset fuckery - Don't you love trigonometry?
				SpriteAPI sprite = ship.getSpriteAPI();
				float offsetX = sprite.getWidth()/2 - sprite.getCenterX();
				float offsetY = sprite.getHeight()/2 - sprite.getCenterY();

				float trueOffsetX = (float)FastTrig.cos(Math.toRadians(ship.getFacing()-90f))*offsetX - (float)FastTrig.sin(Math.toRadians(ship.getFacing()-90f))*offsetY;
				float trueOffsetY = (float)FastTrig.sin(Math.toRadians(ship.getFacing()-90f))*offsetX + (float)FastTrig.cos(Math.toRadians(ship.getFacing()-90f))*offsetY;
				if(!ship.isHulk())
				{
					for(WeaponAPI w : ship.getAllWeapons())
					{
						if(!w.getSlot().isBuiltIn() && !w.getSlot().isDecorative())
							continue;
						//armaa_valkazardEffect;
						if(w.getSpec().getType() == WeaponAPI.WeaponType.MISSILE && w.getAmmo() < 0)
							continue;
						
						if(w.getSprite() == null)
							continue;
						Color sysColo = ship.getSystem().getSpecAPI().getJitterEffectColor();
						if(!w.getSlot().getId().equals("F_LEGS"))
						{
							MagicRender.battlespace(
									Global.getSettings().getSprite(w.getSpec().getTurretSpriteName()),
									new Vector2f(w.getLocation().getX()+trueOffsetX,w.getLocation().getY()+trueOffsetY),
									new Vector2f(0, 0),
									new Vector2f(w.getSprite().getWidth(), w.getSprite().getHeight()),
									new Vector2f(0, 0),
									ship.getFacing()-90f,
									0f,
									sysColo,
									true,
									0f,
									0f,
									0f,
									0f,
									0f,
									0.1f,
									0.1f,
									0.3f,
									CombatEngineLayers.BELOW_SHIPS_LAYER);
						}
						
						else
						{
							int frame = w.getAnimation().getFrame();
							SpriteAPI spr = Global.getSettings().getSprite("graphics/armaa/ships/valkazard/armaa_valkazard_legs0"+frame+".png");
						
							if(frame >= 10)
							spr = Global.getSettings().getSprite("graphics/armaa/ships/valkazard/armaa_valkazard_legs"+frame+".png");				
							
							MagicRender.battlespace(
									spr,
									new Vector2f(w.getLocation().getX()+trueOffsetX,w.getLocation().getY()+trueOffsetY),
									new Vector2f(0, 0),
									new Vector2f(w.getSprite().getWidth(), w.getSprite().getHeight()),
									new Vector2f(0, 0),
									ship.getFacing()-90f,
									0f,
									sysColo,
									true,
									0f,
									0f,
									0f,
									0f,
									0f,
									0.1f,
									0.1f,
									0.3f,
									CombatEngineLayers.BELOW_SHIPS_LAYER);								
						}							
						
						if(w.getBarrelSpriteAPI() != null)
						{
							if(!w.getSlot().getId().equals("C_ARML") && (!w.getSlot().getId().equals("A_GUN")))
								continue;
							
							//Explicitly for Valkazard compat, since apparently can't access barrelsprite id through api and using the api itself also modifies the color of the original
							SpriteAPI spr = Global.getSettings().getSprite(w.getSpec().getHardpointSpriteName());
							
							MagicRender.battlespace(
									spr,
									new Vector2f(w.getLocation().getX()+trueOffsetX,w.getLocation().getY()+trueOffsetY),
									new Vector2f(0, 0),
									new Vector2f(w.getBarrelSpriteAPI().getWidth(), w.getBarrelSpriteAPI().getHeight()),
									new Vector2f(0, 0),
									w.getCurrAngle()-90f,
									0f,
									sysColo,
									true,
									0f,
									0f,
									0f,
									0f,
									0f,
									0.1f,
									0.1f,
									0.3f,
									CombatEngineLayers.BELOW_SHIPS_LAYER);
						}
					}
				}
							ship.getMutableStats().getDynamic().getStat("armaa_NNAfterimageTracker").modifyFlat("armaa_NNAfterimageTrackerID",
							ship.getMutableStats().getDynamic().getStat("armaa_NNAfterimageTracker").getModifiedValue() - AFTERIMAGE_THRESHOLD);
			}			
        }
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        reset = true;
        ShipAPI ship = (ShipAPI) stats.getEntity();
        if (ship == null) {
            return;
        }

        if (mass == null) {
            mass = ship.getMass();
        }
		/*
        if (ship.hasListenerOfClass(RampageDriveListener.class)) 
		{
			ship.removeListenerOfClass(RampageDriveListener.class);
		}
		*/
        if (ship.getMass() != mass) {
            ship.setMass(mass);
        }
        stats.getMaxSpeed().unmodify(id);
        stats.getAcceleration().unmodify(id);
        stats.getEmpDamageTakenMult().unmodify(id);
        stats.getHullDamageTakenMult().unmodify(id);
        stats.getArmorDamageTakenMult().unmodify(id);

        stats.getBallisticRoFMult().unmodify(id);
        stats.getEnergyRoFMult().unmodify(id);
    }

    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (index == 0) {
            return new StatusData(poopystinky, false);
        } else if (index == 1) {
            return new StatusData(poopystinky2, true);
        }
        return null;
    }

    @Override
    public String getInfoText(ShipSystemAPI system, ShipAPI ship) {
        if (system.isOutOfAmmo()) return null;
        if (system.getState() != ShipSystemAPI.SystemState.IDLE) return null;

        //ShipAPI target = findTarget(ship);
        /*if (target != null && target != ship) {
            return "TARGET ENGAGED";
        }

        if ((target == null || target == ship) && ship.getShipTarget() != null) {
            return "OUT OF RANGE";
        }*/
        return poopystinky3;
    }

    
    public static class RampageDriveListener implements DamageTakenModifier {

            @Override
            public String modifyDamageTaken(Object param, CombatEntityAPI target, DamageAPI damage, Vector2f point, boolean shieldHit) {
                // checking for ship explosions
                if (param instanceof DamagingProjectileAPI) {
                    DamagingProjectileAPI proj = (DamagingProjectileAPI) param;
                    /*
                     log.info(proj.getDamageType());
                     log.info(proj.getSource());
                     log.info(proj.getSpawnType());
                     log.info(MathUtils.getDistance(proj.getSpawnLocation(), proj.getSource().getLocation()));
                     */
                    // checks if the damage fits the details of a ship explosion
                    if (proj.getDamageType().equals(DamageType.HIGH_EXPLOSIVE)
                            && proj.getProjectileSpecId() == null
                            && !proj.getSource().isAlive()
                            && proj.getSpawnType().equals(ProjectileSpawnType.OTHER)
                            && MathUtils.getDistance(proj.getSpawnLocation(), proj.getSource().getLocation()) < 2f) {
                        //log.info(damage.computeDamageDealt(0f)); //0.5f??
                        damage.getModifier().modifyMult(this.getClass().getName(), 0.00f);
                        //log.info(damage.computeDamageDealt(0f));
                        //log.info("Reduced explosion damage from " + proj.getSource());
                    }
                }
                return null;
            }
        }
    /*protected ShipAPI findTarget(ShipAPI ship) {
        ShipAPI target = ship.getShipTarget();
        if(
                target!=null
                        &&
                        (!target.isDrone()||!target.isFighter())
                        &&
                        MathUtils.isWithinRange(ship, target, RANGE)
                        &&
                        target.getOwner()!=ship.getOwner()
                ){
            return target;
        } else {
            return null;
        }
    }*/
}
