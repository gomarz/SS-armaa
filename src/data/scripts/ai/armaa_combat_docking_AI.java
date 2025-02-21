package data.scripts.ai;

import data.scripts.util.armaa_utils;
import data.scripts.util.armaa_strikeCraftRepairTracker;

import java.awt.Color;
import java.util.*;

import org.lazywizard.lazylib.CollisionUtils;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import org.lwjgl.util.Point;
import org.lwjgl.util.vector.Vector2f;
import org.lwjgl.input.Keyboard;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SoundAPI;
import com.fs.starfarer.api.combat.ArmorGridAPI;
import com.fs.starfarer.api.combat.CombatAssignmentType;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.combat.FighterLaunchBayAPI;
import com.fs.starfarer.api.combat.FighterWingAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShieldAPI;
import com.fs.starfarer.api.combat.ShipAIConfig;
import com.fs.starfarer.api.combat.ShipAIPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.combat.entities.Ship;
import com.fs.starfarer.api.combat.CombatTaskManagerAPI;

//based on code from Sundog's ICE repair drone, Dark.Revenant's Imperium Titan, ED Shipyards Maltese AI
//Modified from Harupea's base Combat Docking Module

public class armaa_combat_docking_AI extends BaseShipAI {
	private final ShipwideAIFlags flags = new ShipwideAIFlags();
    private final ShipAIConfig config = new ShipAIConfig();
    
    public ShipAPI carrier;
	public Vector2f landingLoc;
    private ShipAPI fighterbay;
    private CombatFleetManagerAPI combatfleet;
    private CombatFleetManagerAPI fleetManager;
    private ShipAPI target;
    private Vector2f targetOffset;
    private boolean returning = false;
    private float targetFacingOffset = Float.MIN_VALUE;
    private boolean needrepair = false;
    private float HPPercent = 0.50f;
    private float CRmarker = 0.4f;
    private float CRrestored = 0.20f;
    private float CRmax = 1f;
	private float BaseTimer = .30f;
	private boolean hasLanded = false;
	private boolean intervalCheck = false;
	private int bayNo = 0;
	private float nearest = 10000f;
	private final IntervalUtil dockingLimit = new IntervalUtil(50f, 50f);

    private final IntervalUtil countdown = new IntervalUtil(4f, 4f);
    public final IntervalUtil BASE_REFIT = new IntervalUtil(25f, 25f);
	
    Vector2f getDestination() {
        return new Vector2f();
    }
	
	//Store the ships currently on the carrier
	private class landingData
	{
		private Map<ShipAPI,List<ShipAPI>> occupiedBays = new HashMap<>();
		{
			//occupiedBays.put(aCarrier
		}			
	}

    public armaa_combat_docking_AI(ShipAPI ship) {
		super(ship);		
	}

    @Override
    public void advance(float amount) 
	{
		
		ShipAPI playerShip = Global.getCombatEngine().getPlayerShip();
    	if(carrier == null || carrier == ship || carrier.isHulk() || !carrier.isAlive()) 
		{
    		init();
    	}
		
		if(!ship.isLanding() || !ship.isFinishedLanding())
		{
			dockingLimit.advance(amount);
		}
		
		if(dockingLimit.intervalElapsed())
		{
			abortOp(ship);
		}
		
		boolean isTaken = false;

		if(carrier != null)
		{			
			if(ship==playerShip && !ship.isFinishedLanding())
				Global.getCombatEngine().maintainStatusForPlayerShip("AceSystem3", "graphics/ui/icons/icon_repair_refit.png", "Refitting at Bay "+bayNo+  ": " + carrier.getName() , String.valueOf((int)MathUtils.getDistance(ship,carrier)) + " SU",false);	
			if(MathUtils.getDistance(ship, carrier) > 500)
			{
				abortOp(ship);
			}
		}
    	
    	if(ship.isLanding()) 
		{
    		ship.setShipSystemDisabled(true);
    		ship.setControlsLocked(true);
			ship.setInvalidTransferCommandTarget(true); 
			if(ship.getPhaseCloak() != null)
			{
				ship.getPhaseCloak().deactivate();
			}			

     	    	if(carrier == null || !carrier.isAlive()) 
				{ 
					//abort landing if carrier destroyed before we finish
					abortOp(ship);
					return; 
				}
    	}
    	
    	if(ship.isFinishedLanding())
		{			
			ship.setHullSize(HullSize.FRIGATE);
			ship.resetDefaultAI();	
			return;
    	}
		
		else
			goToDestination();
    }
	
	private void abortOp(ShipAPI ship)
	{
		//ship.getFluxTracker().showOverloadFloatyIfNeeded("Landing aborted", Color.yellow, 2f, true);
		ship.abortLanding();
		ship.setShipSystemDisabled(false);
		ship.setControlsLocked(false);
		ship.setHullSize(HullSize.FRIGATE);
		ship.resetDefaultAI();
		Global.getCombatEngine().getCustomData().remove("armaa_strikecraftisLanding"+ship.getId());		
		
	}
	public float getCarrierRefitRate()
	{
		if(carrier == null)
			return 1;
		return carrier.getSharedFighterReplacementRate();
	}
	
	public ShipAPI getCarrier()
	{
		return carrier;
	}
    
    public void beginlandingAI() 
	{
    	if(carrier == null || !carrier.isAlive()) 
		{
			abortOp(ship);
			return;
		}
		float distance = 150f;
        if(!ship.isLanding() && MathUtils.getDistance(ship, carrier) <= distance) {
        	ship.beginLandingAnimation(carrier);
				for( FighterWingAPI fighters: ship.getAllWings())
				{
					for(ShipAPI fighter: fighters.getWingMembers())
					{
						fighters.orderReturn(fighter);
					}
				}
        	for (ShipAPI modules : ship.getChildModulesCopy()) {
        		if (modules == null) continue;
        		modules.beginLandingAnimation(carrier);
        	}
        }

		Vector2f landingLocation;
		landingLocation = carrier.getLocation();
		hasLanded = true;
		if(!Global.getCombatEngine().getCustomData().containsKey("armaa_repairTracker_"+ship.getId()))
		{
			Global.getCombatEngine().getCustomData().put("armaa_repairTracker_"+ship.getId(),"-");
			Global.getCombatEngine().addPlugin(new armaa_strikeCraftRepairTracker(ship,carrier,landingLocation,bayNo));
		}	
    }
    
    @Override
    public boolean needsRefit() 
	{
		if(ship.isFinishedLanding() || ship.isLanding())
			return true;
		if(carrier == null || carrier == ship)
			return false;
		if(Global.getCombatEngine().getCustomData().get("armaa_strikecraftPilot"+ship.getId()) instanceof Float == false){}
		
		else
			HPPercent = (float)Global.getCombatEngine().getCustomData().get("armaa_strikecraftPilot"+ship.getId());
    	float CurrentHull = ship.getHitpoints();
		float MaxHull = ship.getMaxHitpoints();
		float CurrentCR = ship.getCurrentCR();

			if ((CurrentHull < MaxHull * HPPercent) || (CurrentCR < CRmarker)){
				needrepair = true;
				returning = true;
			}
		
		boolean hasMissileSlots = false; //Missile ammo check
		boolean hasMissileLeft = false;

		for (WeaponAPI w : ship.getAllWeapons()) 
		{
			if(w.usesAmmo() && w.getAmmo() <= 0 && !(w.getSlot().isDecorative()))
			{
				if(w.getAmmoPerSecond() == 0)
				{
				needrepair = true;
				returning = true;
					break;
				}
			}
		}			
        return needrepair;
    }

    @Override
    public void cancelCurrentManeuver() {}

    @Override
    public void evaluateCircumstances() {

		 if(!needsRefit())
		 {
			abortOp(ship);
		 }
		if(carrier != null)
		{
			if(carrier.isHulk() || !carrier.isAlive() || MathUtils.getDistance(ship, carrier) > 1000) 
			{
				abortOp(ship);
			}
		}
		if(carrier == ship|| carrier == null)
		{
			init();
		}
		
		else
		{			
			setTarget(chooseTarget());
		}
        
        if(returning&&fighterbay!=null && targetOffset == null) {
            targetOffset = armaa_utils.toRelative(target, fighterbay.getWing().getSource().getLandingLocation(fighterbay));
        }

    }
    
    ShipAPI chooseTarget() {
    	if(carrier == null) {
    		return ship; // Targets shelf if no carrier nearby to prevent null exception.
    	}
    	
        if(needsRefit() && (carrier != null || carrier != ship)) {
            returning = true;
            return carrier;
        } 
		else
		{			
			returning = false;
		}
                
        return ship;     
    }
    
    void setTarget(ShipAPI t) {
        if(target == t) return;
        target = t;
        this.ship.setShipTarget(t);
    }
    
    void goToDestination() 
	{
    	
    	if (target == null || carrier == null) {
    		return;
    	}
    	
        Vector2f to = armaa_utils.toAbsolute(target, targetOffset);
        float distance = MathUtils.getDistance(ship, to);
        
    	float distToCarrier = (float)(MathUtils.getDistanceSquared(carrier.getLocation(),ship.getLocation()) / Math.pow(target.getCollisionRadius(),2));
		
    	if(target == carrier && distToCarrier < 2.0f || ship.isLanding() == true) {
            float f= 2.0f-Math.min(1,distToCarrier);
            if(returning == false) f = f*0.1f;
            turnToward(target.getFacing());
            ship.getLocation().x = (to.x * (f*0.1f) + ship.getLocation().x * (2 - f*0.1f)) / 2;
            ship.getLocation().y = (to.y * (f*0.1f) + ship.getLocation().y * (2 - f*0.1f)) / 2;
            ship.getVelocity().x = (target.getVelocity().x * f + ship.getVelocity().x * (2 - f)) / 2;
            ship.getVelocity().y = (target.getVelocity().y * f + ship.getVelocity().y * (2 - f)) / 2;
			beginlandingAI();
        }
		else 
		{
			int i = 0;
			boolean obstacle = false;
        	targetFacingOffset = Float.MIN_VALUE;
            float angleDif = MathUtils.getShortestRotation(ship.getFacing(), VectorUtils.getAngle(ship.getLocation(), to));

            if(Math.abs(angleDif) < 30 )
			{
                accelerate();
            } 
			else
			{

			if(!obstacle)
				turnToward(to);
                //decelerate();
			}
		}        
            strafeToward(to);
	}   

    @Override
    public ShipwideAIFlags getAIFlags() {
        return flags;
    }

    @Override
    public void setDoNotFireDelay(float amount) {
    }

    @Override
    public ShipAIConfig getConfig() {
        return config;
    }
	
    public void init() 
	{
		if (!ship.isAlive()) return;
		if(bayNo < 0)
		{
			bayNo = 0;
			carrier = null;
		}

		ShipAPI playerShip = Global.getCombatEngine().getPlayerShip();
		boolean valid = false;		
		boolean hasTargettedCarrier = false;
		if(ship == playerShip && !Global.getCombatEngine().isUIAutopilotOn())
			valid = true;
		if(valid && ship == playerShip && ship.getShipTarget() != null && ship.getShipTarget().getOwner() == ship.getOwner())
		{
			ShipAPI potentialCarrier = ship.getShipTarget();
			if(ship.getHullSpec().hasTag("strikecraft_medium"))
			{
				if(potentialCarrier.isDestroyer())
					return;
			}

			else if(ship.getHullSpec().hasTag("strikecraft_large"))
			{
				
				if(potentialCarrier.isCruiser() || potentialCarrier.isDestroyer())
					return;
			}
			
			if(potentialCarrier.isFighter())
				return;
			//not valid if not on same side
			if(potentialCarrier.getOwner() == ship.getOwner())					
				if(( !potentialCarrier.isFrigate() || potentialCarrier.isStationModule() || potentialCarrier.getHullSpec().hasTag("strikecraft_with_bays")) 
					&& potentialCarrier != ship && potentialCarrier.getCurrentCR() > 0){
					if(potentialCarrier.hasLaunchBays())
					{
						//Random rand = new Random();
						//int size = rand.nextInt(potentialCarrier.getLaunchBaysCopy().size());
						ShipAPI fighter = null;
						boolean isTaken = false;
						for(int i = 0; i < potentialCarrier.getNumFighterBays(); i++)
						{	
							if(Global.getCombatEngine().getCustomData().get("armaa_hangarIsOpen"+potentialCarrier.getId()+"_"+i) instanceof Boolean)			
								isTaken = (boolean)Global.getCombatEngine().getCustomData().get("armaa_hangarIsOpen"+potentialCarrier.getId()+"_"+i);		
								if(isTaken)
									continue;
								bayNo = i;									
								fighter = potentialCarrier.getLaunchBaysCopy().get(bayNo).getWing().getLeader();
								break;
						}
						if(fighter != null)
						{
							fighterbay = fighter;
							carrier = potentialCarrier;
							target = carrier;
							if(fighter.getWing() != null || fighter.getWing().getSource() != null || fighter.getWing().getSource().getLandingLocation(fighter) != null)
							{
								targetOffset = armaa_utils.toRelative(carrier,fighter.getWing().getSource().getLandingLocation(fighter));
								landingLoc = fighter.getWing().getSource().getLandingLocation(fighter);landingLoc = fighter.getWing().getSource().getLandingLocation(fighter);
							}
							else
							{
								targetOffset = carrier.getLocation();
								landingLoc = carrier.getLocation();
							}
							hasTargettedCarrier = true;
						}
					}
				}
		}
		
		
		if(!hasTargettedCarrier && carrier == null)
		{
		//Works, land in carrier bay by referencing a fighter from that carrier. Unable to grab location data from the carrier it itself.
		
			for (ShipAPI potentialCarrier : CombatUtils.getShipsWithinRange(ship.getLocation(), 10000.0F)) 
			{ 
				if(potentialCarrier.getOwner() != ship.getOwner())
					continue;
				
				if(potentialCarrier.isFighter() || potentialCarrier.isFrigate() && (!potentialCarrier.isStationModule() || 
				!potentialCarrier.getHullSpec().hasTag("strikecraft_with_bays")) || potentialCarrier == ship || potentialCarrier.getCurrentCR() <= 0 
				|| !potentialCarrier.isAlive())
				{
					continue;
				}
				
				if(ship.getHullSpec().hasTag("strikecraft_medium"))
				{
					if(potentialCarrier.isDestroyer())
						continue;
				}

				else if(ship.getHullSpec().hasTag("strikecraft_large"))
				{
					if(potentialCarrier.isCruiser() || potentialCarrier.isDestroyer())
						return;
				}
				
				if(!Global.getCombatEngine().isEntityInPlay(potentialCarrier))
					continue;
				
				if(MathUtils.getDistance(ship,potentialCarrier) > nearest) continue;

				for(int i = 0; i < potentialCarrier.getNumFighterBays(); i++)
				{	
					if(MathUtils.getDistance(ship,potentialCarrier) < nearest)
						nearest = MathUtils.getDistance(ship,potentialCarrier);
					carrier = potentialCarrier;
					target = carrier;
					if(carrier.hasLaunchBays())
					{
						Random randomGenerator = new Random();
						int size = randomGenerator.nextInt(carrier.getLaunchBaysCopy().size());
						bayNo = size;
						WeaponSlotAPI wep = carrier.getLaunchBaysCopy().get(size).getWeaponSlot();
						landingLoc = new Vector2f(carrier.getLocation().x+wep.getLocation().y, carrier.getLocation().y+wep.getLocation().x);
						targetOffset = armaa_utils.toRelative(carrier, landingLoc);						
					}
						else
						{
							landingLoc =  carrier.getLocation();							
							targetOffset = armaa_utils.toRelative(carrier, landingLoc);
						}
					
					break;
				}
			}
		}
	}	
}

