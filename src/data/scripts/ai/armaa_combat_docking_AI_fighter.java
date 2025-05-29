package data.scripts.ai;

import data.scripts.util.armaa_utils;

import java.util.List;
import java.util.Random;

import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.Point;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SoundAPI;
import com.fs.starfarer.api.combat.ArmorGridAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.combat.FighterLaunchBayAPI;
import com.fs.starfarer.api.combat.FighterWingAPI;
import com.fs.starfarer.api.combat.ShipAIConfig;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.util.IntervalUtil;
//import com.fs.starfarer.combat.CombatEngine;

//based on code from Sundog's ICE repair drone and Dark.Revenant's Imperium Titan. Copied Maltese AI

public class armaa_combat_docking_AI_fighter extends BaseShipAI {
	private final ShipwideAIFlags flags = new ShipwideAIFlags();
    private final ShipAIConfig config = new ShipAIConfig();
    
    public ShipAPI carrier;
    private ShipAPI fighterbay;
    private CombatFleetManagerAPI combatfleet;
    private CombatFleetManagerAPI fleetManager;
    private ShipAPI fightercopy;
    private List<FighterLaunchBayAPI> carrierdata;
    private boolean carrierworking;
    private ShipAPI target;
    private Vector2f targetOffset;
    private Point cellToFix = new Point();
    private Random rng = new Random();
    private ArmorGridAPI armorGrid;
    private SoundAPI repairSound;
    private float max, cellSize;
    private int gridWidth, gridHeight, cellCount;
    private boolean returning = false;
    private boolean spark = false;
    private float targetFacingOffset = Float.MIN_VALUE;
    private float range = 4000f;
    private boolean needrepair = false;
    private float HPPercent = 0.50f;
    private float CRmarker = 0.4f;
    private float CRrestored = 0.20f;
    private float CRmax = 1f;
   // protected float refit_timer = 0f;
	private boolean hasLanded = false;
	private boolean intervalCheck = false;
	private float pptMax = 180f;
    private String timer = "0";
	private float nearest = 10000f;

    private final IntervalUtil interval = new IntervalUtil(0.25f, 0.33f);
    private final IntervalUtil countdown = new IntervalUtil(4f, 4f);
	private final IntervalUtil landingTimer = new IntervalUtil(20f, 20f);
    public final IntervalUtil BASE_REFIT = new IntervalUtil(20f, 20f);
	private final IntervalUtil BASE_REFIT_CR = new IntervalUtil(20f, 20f);

    
    Vector2f getDestination() {
        return new Vector2f();
    }

    public armaa_combat_docking_AI_fighter(ShipAPI ship) {
		super(ship);		
	}

    @Override
    public void advance(float amount) {
		
		ShipAPI playerShip = Global.getCombatEngine().getPlayerShip();
    	if(carrier == null || carrier == ship || carrier.isHulk() || !carrier.isAlive()) 
		{
			ship.setControlsLocked(false);
    		init();
    	}
	    	
    	if(ship.isLanding() && ship.isFighter() && ship.getWing() != null) {
    		//doingMx = false; //stop the repair movement
    		countdown.advance(amount);
    		if (countdown.intervalElapsed()) {
    			ship.getWing().getSource().land(ship);
    			return;
    		}
    	}
   	
    	if(ship.isLanding()) 
		{
    		//doingMx = false; //stop the repair movement
    		
    		ship.setShipSystemDisabled(true);
    		ship.setControlsLocked(true);
    			
    		countdown.advance(amount);
    		if (countdown.intervalElapsed()) {
    			 ship.setControlsLocked(true);
    			 
    			 if(fighterbay!=null) {
    	    			armaa_utils.setLocation(ship, fighterbay.getWing().getSource().getLandingLocation(fighterbay)); //set to location of carrier in case of drift
    	    		}
    			 
     	    	if(carrier == null || !carrier.isAlive()) 
				{ 
					///Destroy ship if carrier is destroyed during landing
					//armaa_utils.destroy(ship);
					ship.abortLanding();
					ship.setShipSystemDisabled(false);
					ship.setControlsLocked(false);
					ship.setHullSize(HullSize.FRIGATE);
					ship.resetDefaultAI();
					ship.setHullSize(HullSize.FIGHTER);
        	    return; 
				}
    			 
    		}
    	}
    	
    	if(ship.isFinishedLanding()) 
		{
			fleetManager = Global.getCombatEngine().getFleetManager(FleetSide.PLAYER);
			if(carrier == null) 
			{ 
				ship.abortLanding();
				ship.setShipSystemDisabled(false);
				ship.setControlsLocked(false);
				init();

				return; 
			}
    		
    		for (ShipAPI modules : ship.getChildModulesCopy()) { //Modules also is finished landing.
    			if (modules == null) continue;
    			modules.isFinishedLanding();
    			}
    		
    		if(!carrier.isAlive()) 
			{ 
				armaa_utils.destroy(ship);
				return; 
			}
		}
    		
		if(!hasLanded)
		{
			hasLanded = true;
		}
  
        goToDestination();
		beginlandingAI();
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
    	if(carrier == null) return;
    	
        if(returning && !ship.isLanding() && MathUtils.getDistance(ship, carrier) < carrier.getCollisionRadius()/3f) {
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
    	
    }
    
    @Override
    public boolean needsRefit() 
	{						
        return true;
    }

    @Override
    public void cancelCurrentManeuver() {
    }

    @Override
    public void evaluateCircumstances() {

		 if(!needsRefit())
		 {
			 ship.setHullSize(HullSize.FRIGATE);
			 ship.resetDefaultAI();
		 }
		if(carrier != null)
		{
			if(carrier.isHulk() || !carrier.isAlive()) 
			{
				ship.setControlsLocked(false);
				init();
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
		
		//no fighterbay = no valid carrier to dock at means we have no target
		else
		{
			if(fighterbay == null)
			{
				//Random rand = new Random();
				//String txt =  landingLines_notPossible.get(rand.nextInt(landingLines_notPossible.size()));
				//ship.getFluxTracker().showOverloadFloatyIfNeeded(txt, Color.yellow, 2f, true);
			}
			//ship.setHullSize(HullSize.FRIGATE);
			//ship.resetDefaultAI();
		}
    }
    
    ShipAPI chooseTarget() {
    	if(carrier == null) {
    		return ship; // Targets shelf if no carrier nearby to prevent null exception.
    	}
    	
        if(needsRefit() && (carrier != null || carrier != ship)) {
            returning = true;
            //ship.getFluxTracker().setCurrFlux(ship.getFluxTracker().getMaxFlux());
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
    
    void goToDestination() {
    	
    	if (target == null || carrier == null) {
    		return;
    	}
    	
        Vector2f to = armaa_utils.toAbsolute(target, targetOffset);
        float distance = MathUtils.getDistance(ship, to);
        
    	float distToCarrier = (float)(MathUtils.getDistanceSquared(carrier.getLocation(),ship.getLocation()) / Math.pow(target.getCollisionRadius(),2));
    	if(target == carrier && distToCarrier < 1.0f || ship.isLanding() == true) {
            float f= 1.0f-Math.min(1,distToCarrier);
            if(returning == false) f = f*0.1f;
            turnToward(target.getFacing());
            ship.getLocation().x = (to.x * (f*0.1f) + ship.getLocation().x * (2 - f*0.1f)) / 2;
            ship.getLocation().y = (to.y * (f*0.1f) + ship.getLocation().y * (2 - f*0.1f)) / 2;
            ship.getVelocity().x = (target.getVelocity().x * f + ship.getVelocity().x * (2 - f)) / 2;
            ship.getVelocity().y = (target.getVelocity().y * f + ship.getVelocity().y * (2 - f)) / 2;
			if(!ship.isLanding() && !ship.isFinishedLanding())
			ship.beginLandingAnimation(target);
        }else {
        	targetFacingOffset = Float.MIN_VALUE;
            float angleDif = MathUtils.getShortestRotation(ship.getFacing(), VectorUtils.getAngle(ship.getLocation(), to));

            if(Math.abs(angleDif) < 30){
                accelerate();
            } else {
                turnToward(to);
                //decelerate();
            }        
            strafeToward(to);
        }   
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
    
    public void init() {
    	//engine = Global.getCombatEngine();
    	//carrier = ship;
            if (!ship.isAlive()) return;
    		ShipAPI playerShip = Global.getCombatEngine().getPlayerShip();
    		boolean hasTargettedCarrier = false;
			if(ship.getShipTarget() != null)
			{
				ShipAPI potentialCarrier = ship.getShipTarget();
				//not valid if not on same side
				if(potentialCarrier.getOwner() == ship.getOwner())					
					if(!potentialCarrier.isFighter() &&( !potentialCarrier.isFrigate() || potentialCarrier.isStationModule() ) 
						&& potentialCarrier != ship && potentialCarrier.getCurrentCR() > 0){
						if(potentialCarrier.hasLaunchBays())
						{
							Random rand = new Random();
							int size = rand.nextInt(potentialCarrier.getLaunchBaysCopy().size());
							ShipAPI fighter = potentialCarrier.getLaunchBaysCopy().get(0).getWing().getLeader();
							
							if(fighter != null)
							{
								fighterbay = fighter;
								carrier = potentialCarrier;
								target = carrier;
								targetOffset = armaa_utils.toRelative(carrier,fighter.getWing().getSource().getLandingLocation(fighter));
								hasTargettedCarrier = true;
							}
						}
					}
			}
			if(!hasTargettedCarrier)
			{
    	    //Works, land in carrier bay by referencing a fighter from that carrier. Unable to grab location data from the carrier it itself.
				for (ShipAPI fighterAI : CombatUtils.getShipsWithinRange(ship.getLocation(), 10000.0F)) 
				{ 
					//SKip anything not a fighter or on our side
					if(fighterAI.getOwner() != ship.getOwner() || !fighterAI.isFighter())
						continue;
					//skip fighters with no wing
					if(fighterAI.getWing() == null)
						continue;
					
					//skip anything without a mothership
					if(fighterAI.getWing().getSourceShip() == null)
						continue;
					
					ShipAPI potentialCarrier = fighterAI.getWing().getSourceShip();
					
					if(potentialCarrier.isFighter() || potentialCarrier.isFrigate() && !potentialCarrier.isStationModule() || potentialCarrier == ship || potentialCarrier.getCurrentCR() <= 0)
					{
						continue;
					}
					
					if(!Global.getCombatEngine().isEntityInPlay(potentialCarrier))
						continue;
					
					if(MathUtils.getDistance(ship,potentialCarrier) > nearest) continue;

					if (fighterAI != null && potentialCarrier != null) 
					{
						if(MathUtils.getDistance(ship,potentialCarrier) < nearest)
							nearest = MathUtils.getDistance(ship,potentialCarrier);
						fighterbay = fighterAI;
						carrier = potentialCarrier;
						target = carrier;
						targetOffset = armaa_utils.toRelative(carrier, fighterAI.getWing().getSource().getLandingLocation(fighterAI));
						//break;
					}
				}
			}
			
    }
    
}
