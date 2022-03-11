package data.scripts.ai;

import data.scripts.util.armaa_utils;

import java.awt.Color;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Random;

import org.lazywizard.lazylib.CollisionUtils;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.CombatUtils;

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
    private boolean needrepair = false;
    private float HPPercent = 0.50f;
    private float CRmarker = 0.4f;
    private float CRrestored = 0.20f;
    private float CRmax = 1f;
	private float BaseTimer = .30f;
	private boolean hasLanded = false;
	private boolean intervalCheck = false;
	private float pptMax = 180f;
	private int bayNo = 0;
    private String timer = "0";
	private float nearest = 10000f;

    private final IntervalUtil countdown = new IntervalUtil(4f, 4f);
    public final IntervalUtil BASE_REFIT = new IntervalUtil(25f, 25f);
	
    Vector2f getDestination() {
        return new Vector2f();
    }

    public armaa_combat_docking_AI(ShipAPI ship) {
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
		
		if(ship.getPhaseCloak() != null)
		{
			ship.getPhaseCloak().deactivate();
		}
		boolean isTaken = false;
    	
    	if(ship.isLanding() && ship.isFighter() && ship.getWing() != null) 
		{
    		countdown.advance(amount);
    		if (countdown.intervalElapsed()) {
    			ship.getWing().getSource().land(ship);
    			return;
    		}
    	}
		if(carrier != null)
		{
			if(Global.getCombatEngine().getCustomData().get("armaa_hangarIsOpen"+carrier.getId()+"_"+bayNo) instanceof Boolean)
			{							
				isTaken = (boolean)Global.getCombatEngine().getCustomData().get("armaa_hangarIsOpen"+carrier.getId()+"_"+bayNo);
				
				if(isTaken && !ship.isFinishedLanding())
				{
					ship.setControlsLocked(false);
					//bayNo = 0;
					init();
				}
			}				
			if(ship==playerShip && !ship.isFinishedLanding())
				Global.getCombatEngine().maintainStatusForPlayerShip("AceSystem3", "graphics/ui/icons/icon_repair_refit.png", "Refitting at Bay "+bayNo+  ": " + carrier.getName() , String.valueOf((int)MathUtils.getDistance(ship,carrier)) + " SU",false);	
		}
    	
    	if(ship.isLanding()) 
		{
    		ship.setShipSystemDisabled(true);
    		ship.setControlsLocked(true);	
    		countdown.advance(amount);
    		if (countdown.intervalElapsed()) {
    			 ship.setControlsLocked(true);
    			 
				if(fighterbay!=null) 
				{
					armaa_utils.setLocation(ship, fighterbay.getWing().getSource().getLandingLocation(fighterbay)); //set to location of carrier in case of drift
				}
    			 
     	    	if(carrier == null || !carrier.isAlive()) 
				{ 
					//abort landing if carrier destroyed before we finish
					ship.abortLanding();
					ship.setShipSystemDisabled(false);
					ship.setControlsLocked(false);
					ship.setHullSize(HullSize.FRIGATE);
					ship.resetDefaultAI();
					return; 
				}
    			 
    		}
    	}
    	
    	if(ship.isFinishedLanding()) {
			fleetManager = Global.getCombatEngine().getFleetManager(ship.getOwner());
			Global.getCombatEngine().getCustomData().put("armaa_hangarIsOpen"+carrier.getId()+"_"+bayNo,true);

     	    	if(carrier == null) 
				{ 
					//abort landing if carrier somehow is lost
					ship.abortLanding();
					ship.setShipSystemDisabled(false);
					ship.setControlsLocked(false);
					ship.setHullSize(HullSize.FRIGATE);
					ship.resetDefaultAI();
					Global.getCombatEngine().getCustomData().put("armaa_hangarIsOpen"+carrier.getId()+"_"+bayNo,false);
					return; 
				}
    		
    		for (ShipAPI modules : ship.getChildModulesCopy()) { //Modules also is finished landing.
    			if (modules == null) continue;
    			modules.isFinishedLanding();
    			}
    		
    		if(!carrier.isAlive()) 
			{ 
				if(fleetManager.getRetreatedCopy().contains(carrier.getFleetMember()))
				{
					ship.setHullSize(HullSize.FRIGATE);
					Global.getCombatEngine().getFleetManager(ship.getOwner()).getTaskManager(true).orderRetreat(fleetManager.getDeployedFleetMember(ship),false,false);
					ship.resetDefaultAI();
				}
				
				else
				{
					//Destroy ship if carrier is destroyed after landing
					ship.setHullSize(HullSize.FRIGATE);
					armaa_utils.destroy(ship);
					return; 
				}
			}
    		
    		for (FleetMemberAPI member : fleetManager.getRetreatedCopy()) 
			{ 
				//Check if the carrier retreats during landing
    			if(fleetManager.getShipFor(member)!=carrier) {
    				continue;
    			}
    			if(fleetManager.getShipFor(member)==carrier) {
    				fleetManager.addToReserves(ship.getFleetMember());
    				Global.getCombatEngine().removeEntity(ship);
    			}
    			
    		}
    		if(!hasLanded)
			{
				hasLanded = true;
				Global.getCombatEngine().getCustomData().put("armaa_hangarIsOpen"+carrier.getId()+"_"+bayNo,true);
			}
			float refitMod = getCarrierRefitRate();
			float wepMalus = 0f;
			
			if(Global.getCombatEngine().getCustomData().get("armaa_strikecraftTotalMalus"+ship.getId()) instanceof Float)
				wepMalus = (float)Global.getCombatEngine().getCustomData().get("armaa_strikecraftTotalMalus"+ship.getId());
			
			float hullBonus = (float)Math.max(ship.getHullLevel()*1.5f,1f);
			float refitRate = (amount*hullBonus)*(1f-wepMalus)*refitMod;
			float adjustedRate = (float)Math.max(refitRate,amount*BaseTimer);

			BASE_REFIT.advance(adjustedRate);
			float elapsed = BASE_REFIT.getElapsed();
			float maxinterval = BASE_REFIT.getMaxInterval();
			float refit_timer = Math.round((elapsed/maxinterval)*100f);
    		timer = String.valueOf(refit_timer);
 			
			float crLevel = ship.getCurrentCR()/1f;
			float remainder = (ship.getMaxHitpoints()-ship.getHitpoints())*ship.getHullLevel()*((adjustedRate/maxinterval)*elapsed);
			float crRemainder = (1f-ship.getCurrentCR())*crLevel*((adjustedRate/maxinterval)*elapsed)/10f;
			float currArmor = armaa_utils.getArmorPercent(ship);
			float CRRefit = Math.min(ship.getCurrentCR() + crRemainder/10f, CRmax); //Add CRrestored up to the maximum 
			//prevent (noticeable)cr loss while docked
			ship.getMutableStats().getCRLossPerSecondPercent().modifyMult(ship.getId(),0.001f);
			//armaa_utils.print(ship,String.valueOf(currArmor));
			ship.setHitpoints(Math.min(ship.getHitpoints()+remainder*(elapsed/maxinterval),ship.getMaxHitpoints()));
			armaa_utils.setArmorPercentage(ship, currArmor + ((1f-currArmor)*(adjustedRate/maxinterval))*elapsed); //Armor to 100%
			if ((ship.getCurrentCR() <= CRmarker)) { 
				ship.setCurrentCR(CRRefit);
			}
			//reduce bay repl rate
			fighterbay.getWing().getSource().setCurrRate(fighterbay.getWing().getSource().getCurrRate()-(amount*(adjustedRate)*2f));	
			String abortString = "";
    		if (ship == playerShip && BASE_REFIT.getElapsed() >= 0f) //Display count down timer
			{
				Global.getCombatEngine().maintainStatusForPlayerShip("AceSystem2", "graphics/ui/icons/icon_repair_refit.png",getBlinkyString("REPAIR STATUS"), String.valueOf(ship.getHullLevel()*100f)+"%" ,true);
				abortString = Global.getSettings().getControlStringForEnumName("C2_TOGGLE_AUTOPILOT");
				Global.getCombatEngine().maintainStatusForPlayerShip("AceSystem", "graphics/ui/icons/icon_repair_refit.png","RESTORING PPT/AMMO (PRESS "+abortString+")to abort", timer+"%" ,true);
			}
			Global.getCombatEngine().maintainStatusForPlayerShip("AceSystem3", "graphics/ui/icons/icon_repair_refit.png", "PPT:"+ship.getMutableStats().getPeakCRDuration().computeEffective(0f), String.valueOf((int)ship.getTimeDeployedForCRReduction()) +" PPT Countdown",false);
			boolean abort = Keyboard.isKeyDown(Keyboard.getKeyIndex(abortString));    		
    		if (BASE_REFIT.intervalElapsed() || abort) 
			{
				takeOff(ship,fighterbay,abort);
				
				ship.setHullSize(HullSize.FRIGATE); //Einhander AI fix, fighter hullsize cannot resetDefaultAI, will crash
				CombatFleetManagerAPI cfm = Global.getCombatEngine().getFleetManager(ship.getOwner()); 
				CombatTaskManagerAPI ctm = cfm.getTaskManager(ship.isAlly());
				DeployedFleetMemberAPI dfm = cfm.getDeployedFleetMember(ship);
				ctm.orderSearchAndDestroy(dfm, false);

				ship.resetDefaultAI();
				
				//toggle weapons back on
				for(WeaponGroupAPI w:ship.getWeaponGroupsCopy())
				{
					if(!w.getActiveWeapon().usesAmmo())
						w.toggleOn();
				}							
				return;
				
    		}
    	}
        goToDestination();
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
    	
        if(!ship.isLanding() && MathUtils.getDistance(ship, landingLoc) < 20f) {
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
		if(carrier == null || carrier == ship)
			return false;
		if(Global.getCombatEngine().getCustomData().get("armaa_strikecraftPilot"+ship.getId()) instanceof Float == false)
		{
		}
		
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
				ship.setHullSize(HullSize.FRIGATE);
				ship.resetDefaultAI();
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
		
    	if(target == carrier && distToCarrier < 1.0f || ship.isLanding() == true) {
            float f= 1.0f-Math.min(1,distToCarrier);
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
	
	public String getBlinkyString(String str)
	{
		long count200ms = (long) Math.floor(Global.getCombatEngine().getTotalElapsedTime(true) / 0.2f);
		if (count200ms % 2L == 0L) {
			return str +": ";
		} else {
			return "";
		}		
	}

	public void takeOff(ShipAPI ship,ShipAPI fighterbay,boolean expand)
	{
		boolean done = false;
		Global.getSoundPlayer().playSound("fighter_takeoff", 1f, 1f, ship.getLocation(),new Vector2f());
		ship.setCollisionClass(CollisionClass.SHIP);
		ship.clearDamageDecals();
		ship.setHitpoints(ship.getMaxHitpoints()); //Hull to 100%
		if ((ship.getCurrentCR() <= CRmarker)) 
		{ 
			ship.setCurrentCR(CRmarker+.1f);
		}
		ship.getFluxTracker().stopOverload();
		ship.getFluxTracker().setCurrFlux(0f);
		armaa_utils.setArmorPercentage(ship, 100f); //Armor to 100%
		Global.getCombatEngine().getCustomData().put("armaa_strikecraftisLanding"+ship.getId(),false);
		Global.getCombatEngine().getCustomData().put("armaa_strikecraft_hasWaypoint"+ship.getId(),false);
		
		if (fighterbay!=null) {
			armaa_utils.setLocation(ship, fighterbay.getWing().getSource().getLandingLocation(fighterbay)); //set to location of carrier in case of drift
		}
		if(!expand)
		{
			int times = 1;
			if(Global.getCombatEngine().getCustomData().get("armaa_strikecraft_docked_times_"+ship.getId()) instanceof Integer)
				times = (int)Global.getCombatEngine().getCustomData().get("armaa_strikecraft_docked_times_"+ship.getId());
			pptMax = ship.getVariant().getHullSpec().getNoCRLossTime(); 				
			float trueppt = pptMax*Math.max(0,ship.getMutableStats().getPeakCRDuration().getMult());
			if(ship.getMutableStats().getPeakCRDuration().computeEffective(0f) < ship.getTimeDeployedForCRReduction())
			ship.getMutableStats().getPeakCRDuration().modifyFlat(ship.getId(),ship.getTimeDeployedForCRReduction());
			times++;
			Global.getCombatEngine().getCustomData().put("armaa_strikecraft_docked_times_"+ship.getId(),times);		
						
			ship.clearDamageDecals();
			List<WeaponAPI> weapons = ship.getAllWeapons();
			 for (WeaponAPI w : weapons) {
				if (w.usesAmmo()) w.resetAmmo();
				}	
		}
		((ShipAPI) ship).setAnimatedLaunch();
		Global.getCombatEngine().getCustomData().put("armaa_hangarIsOpen"+carrier.getId()+"_"+bayNo,false);
		ship.setControlsLocked(false);
		ship.setShipSystemDisabled(false);
		ship.getMutableStats().getCRLossPerSecondPercent().unmodify(ship.getId());
		carrierworking = false;
		hasLanded = false;
		returning = false;
		needrepair = false;
		for (ShipAPI modules : ship.getChildModulesCopy()) 
		{
			if (modules == null) continue;
			modules.setHitpoints(ship.getMaxHitpoints());
			modules.getFluxTracker().stopOverload();
			modules.getFluxTracker().setCurrFlux(0f);
			modules.clearDamageDecals();
			armaa_utils.setArmorPercentage(ship, 100f); //Armor to 100%
			((ShipAPI) modules).setAnimatedLaunch();
		}
	}	
 
    public void init() 
	{
    	//engine = Global.getCombatEngine();
    	//carrier = ship;
		if (!ship.isAlive()) return;
		if(bayNo < 0)
		{
			bayNo = 0;
			carrier = null;
		}
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
							targetOffset = armaa_utils.toRelative(carrier,fighter.getWing().getSource().getLandingLocation(fighter));
							hasTargettedCarrier = true;
							landingLoc = fighter.getWing().getSource().getLandingLocation(fighter);
						}
					}
				}
		}
		
		
		if(!hasTargettedCarrier && carrier == null)
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
				
				if(potentialCarrier.isFighter() || potentialCarrier.isFrigate() && !potentialCarrier.isStationModule() || potentialCarrier == ship || potentialCarrier.getCurrentCR() <= 0 || !potentialCarrier.isAlive())
				{
					continue;
				}
				
				if(!Global.getCombatEngine().isEntityInPlay(potentialCarrier))
					continue;
				
				if(MathUtils.getDistance(ship,potentialCarrier) > nearest) continue;

				if (fighterAI != null && potentialCarrier != null) 
				{
					ShipAPI fighter = null;
					boolean isTaken = false;
					for(int i = 0; i < potentialCarrier.getNumFighterBays(); i++)
					{	
						//bayNo = i;
						if(Global.getCombatEngine().getCustomData().get("armaa_hangarIsOpen"+potentialCarrier.getId()+"_"+i) instanceof Boolean)
						{							
							isTaken = (boolean)Global.getCombatEngine().getCustomData().get("armaa_hangarIsOpen"+potentialCarrier.getId()+"_"+i);
							
						}

						else
						{
							Global.getCombatEngine().getCustomData().put("armaa_hangarIsOpen"+potentialCarrier.getId()+"_"+i,false);
							isTaken = false;
						}
						if(isTaken)
							continue;
						if(potentialCarrier.getLaunchBaysCopy() == null)
							continue;
						bayNo = i;					
							fighter = potentialCarrier.getLaunchBaysCopy().get(bayNo).getWing().getLeader();
							if(fighter == null)
								continue;
						//bayNo = i;					
						if(MathUtils.getDistance(ship,potentialCarrier) < nearest)
							nearest = MathUtils.getDistance(ship,potentialCarrier);
						fighterbay = fighter;
						carrier = potentialCarrier;
						target = carrier;
						targetOffset = armaa_utils.toRelative(carrier, fighterbay.getWing().getSource().getLandingLocation(fighterbay));
						landingLoc =  fighterbay.getWing().getSource().getLandingLocation(fighterbay);
						break;
					}
				}

			}
		}
		
		//if(target != null)
		//{
		//	Global.getCombatEngine().getCustomData().put("armaa_hangarIsOpen"+target.getId()+"_"+target.getLaunchBaysCopy().indexOf(bayNo),true);			
		//}
			
    }
}
