package data.scripts.util;

import java.awt.Color;
import java.util.*;

import org.lwjgl.util.vector.Vector2f;
import org.lwjgl.input.Keyboard;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SoundAPI;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.WeaponAPI.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.combat.entities.Ship;
import com.fs.starfarer.api.combat.CombatTaskManagerAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;


public class armaa_strikeCraftRepairTracker extends BaseEveryFrameCombatPlugin
{
	private final IntervalUtil BASE_REFIT = new IntervalUtil(25f, 25f);
	private CombatFleetManagerAPI fleetManager;
	private ShipAPI carrier;
	private ShipAPI ship;
	private Vector2f landingLocation;
	private boolean hasLanded = false;
	private int bayNo;
	private WeaponSlotAPI w;
	private float pptMax = 180f;
	private String timer = "0";
	private float HPPercent = 0.50f;
    private float CRmarker = 0.5f;
    private float CRrestored = 0.20f;
    private float CRmax = 1f;
	private float BaseTimer = .30f;
	private FighterLaunchBayAPI bay;
	
    public armaa_strikeCraftRepairTracker(ShipAPI ship, ShipAPI carrier, Vector2f landingLocation, int bayNo) 
	{
		this.carrier = carrier;
		this.ship = ship;
		this.landingLocation = landingLocation;
		this.bayNo = bayNo;
		boolean ally = ship.isAlly();
		fleetManager = Global.getCombatEngine().getFleetManager(ship.getOwner());

		for(WeaponSlotAPI wep:carrier.getHullSpec().getAllWeaponSlotsCopy())
		{
			if(wep.getWeaponType() == WeaponType.LAUNCH_BAY)
			{
				w= wep;
				landingLocation = new Vector2f(VectorUtils.rotate(w.computePosition(carrier),carrier.getFacing()));
				if(Math.random() <= .50f)
					break;
			}
		}
    }

	public void lerp(float currentX, float currentY, float destX, float destY, float amount)
	{
		float xValue = Math.abs(currentX - destX);
		float yValue = Math.abs(currentY - destY);
		
		float amountToMoveX = xValue / (amount);
		float amountToMoveY = yValue / (amount);
		Vector2f loc = ship.getLocation();
		ship.getLocation().setX(loc.getX() + amountToMoveX);
		ship.getLocation().setY(loc.getY() + amountToMoveY);
	}

    @Override
	public void advance(float amount, List<InputEventAPI> events)
	{
		if(Global.getCombatEngine().isPaused())
			return;
		
		for (FleetMemberAPI member : fleetManager.getRetreatedCopy()) 
		{ 
			if(fleetManager.getShipFor(member)!=carrier) {
				continue;
			}
			if(fleetManager.getShipFor(member)==carrier) {
				fleetManager.addToReserves(ship.getFleetMember());
				Global.getCombatEngine().removeEntity(ship);
			}	
		}		

		if(carrier == null || !carrier.isAlive() || !Global.getCombatEngine().isEntityInPlay(carrier) || carrier.isHulk() || carrier.getHitpoints() <= 0 || carrier.getOwner() != ship.getOwner()) 
		{
			if(ship.isFinishedLanding() || hasLanded)
			{
				ship.getMutableStats().getHullDamageTakenMult().unmodify("invincible");
				ship.getMutableStats().getArmorDamageTakenMult().unmodify("invincible");
				if(carrier != null && !fleetManager.getRetreatedCopy().contains(carrier.getFleetMember()))
				{					
					armaa_utils.destroy(ship);
					Global.getCombatEngine().removeEntity(ship);					
				}
			}
			takeOff(ship,landingLocation,true);
		}

		if(!ship.isFinishedLanding() && !hasLanded)
			return;
		armaa_utils.setLocation(ship, landingLocation); //set to location of carrier in case of drift	
		
		if(w!=null)
		{
			landingLocation = w.computePosition(carrier);
			ship.setFacing(w.computeMidArcAngle(carrier));
		}
		
		else
			landingLocation = carrier.getLocation();
		if(carrier != null && fleetManager.getRetreatedCopy().contains(carrier.getFleetMember()))
		{
			Global.getCombatEngine().getFleetManager(ship.getOwner()).getTaskManager(true).orderRetreat(fleetManager.getDeployedFleetMember(ship),false,false);		
		}

		Global.getCombatEngine().getCustomData().put("armaa_hangarIsOpen"+carrier.getId()+"_"+bayNo,true);
								
		if(!hasLanded)
		{
			hasLanded = true;
			Global.getCombatEngine().getCustomData().put("armaa_hangarIsOpen"+carrier.getId()+"_"+bayNo,true);
		}
		float refitMod = getCarrierRefitRate();
		if(carrier.getVariant().hasHullMod("armaa_serviceBays"))
			refitMod += .5f;
		float wepMalus = 0f;
		
		if(Global.getCombatEngine().getCustomData().get("armaa_strikecraftTotalMalus"+ship.getId()) instanceof Float)
			wepMalus = (float)Global.getCombatEngine().getCustomData().get("armaa_strikecraftTotalMalus"+ship.getId());
		
		float hullBonus = (float)Math.max(ship.getHullLevel()*1.5f,1f);
		float refitRate = (amount*hullBonus)*(1f-wepMalus)*refitMod;
		float adjustedRate = (float)Math.max(refitRate,amount*BaseTimer);
		if(ship.isStationModule())
		{
			ship.setShipAI(null);
		}
		BASE_REFIT.advance(adjustedRate);
		ship.setHullSize(HullSize.FIGHTER);
		float elapsed = BASE_REFIT.getElapsed();
		float maxinterval = BASE_REFIT.getMaxInterval();
		float refit_timer = Math.round((elapsed/maxinterval)*100f);
		timer = String.valueOf(refit_timer);
		
		float crLevel = ship.getCurrentCR()/1f;
		float remainder = (ship.getMaxHitpoints()-ship.getHitpoints())*ship.getHullLevel()*((adjustedRate/maxinterval)*elapsed);
		float crRemainder = (1f-ship.getCurrentCR())*crLevel*((adjustedRate/maxinterval)*elapsed)/10f;
		float currArmor = armaa_utils.getArmorPercent(ship);
		float CRRefit = Math.min(ship.getCurrentCR() + crRemainder*amount, CRmax); //Add CRrestored up to the maximum 
		//prevent (noticeable)cr loss while docked
		ship.getMutableStats().getCRLossPerSecondPercent().modifyMult(ship.getId(),0.001f);
		
		ship.getMutableStats().getHullDamageTakenMult().modifyMult("invincible",0f);
		ship.getMutableStats().getArmorDamageTakenMult().modifyMult("invincible",0f);

		ship.setHitpoints(Math.min(ship.getHitpoints()+remainder*(elapsed/maxinterval),ship.getMaxHitpoints()));
		armaa_utils.setArmorPercentage(ship, currArmor + ((1f-currArmor)*(adjustedRate/maxinterval))*elapsed*amount); //Armor to 100%
		if ((ship.getCurrentCR() <= CRmarker)) { 
			ship.setCurrentCR(CRRefit);
		}
		//reduce bay repl rate
		if(carrier.getLaunchBaysCopy() != null && carrier.getLaunchBaysCopy().size()-1 >= bayNo)
		carrier.getLaunchBaysCopy().get(bayNo).setCurrRate(carrier.getLaunchBaysCopy().get(bayNo).getCurrRate()-(amount*(adjustedRate)));
		
		String abortString = "";
		ShipAPI playerShip = Global.getCombatEngine().getPlayerShip();			
		if (ship == playerShip && BASE_REFIT.getElapsed() >= 0f) //Display count down timer
		{
			Global.getCombatEngine().maintainStatusForPlayerShip("AceSystem2", "graphics/ui/icons/icon_repair_refit.png",getBlinkyString("REPAIR STATUS"), String.valueOf(ship.getHullLevel()*100f)+"%" ,true);
			abortString = Global.getSettings().getControlStringForEnumName("C2_TOGGLE_AUTOPILOT");
			Global.getCombatEngine().maintainStatusForPlayerShip("AceSystem", "graphics/ui/icons/icon_repair_refit.png","RESTORING PPT/AMMO (PRESS "+abortString+")to abort", timer+"%" ,true);
		}

		boolean abort = Keyboard.isKeyDown(Keyboard.getKeyIndex(abortString));    		
		if (BASE_REFIT.intervalElapsed() || abort) 
		{
			takeOff(ship,landingLocation,abort);			
			ship.setHullSize(HullSize.FRIGATE); //Einhander AI fix, fighter hullsize cannot resetDefaultAI, will crash
			ship.setShipSystemDisabled(false);
			ship.resetDefaultAI();
			//ctm.orderSearchAndDestroy(fleetManager.getDeployedFleetMember(ship),false);		

			for(WeaponGroupAPI w:ship.getWeaponGroupsCopy())
			{
				if(!w.getActiveWeapon().usesAmmo())
					w.toggleOn();
			}
			Global.getCombatEngine().getCustomData().remove("armaa_repairTracker_"+ship.getId());
			Global.getCombatEngine().removePlugin(this);
		}		
	}
	
	private float getCarrierRefitRate()
	{
		if(carrier == null)
			return 1;
		return carrier.getSharedFighterReplacementRate();
	}
	
	private String getBlinkyString(String str)
	{
		long count200ms = (long) Math.floor(Global.getCombatEngine().getTotalElapsedTime(true) / 0.2f);
		if (count200ms % 2L == 0L) {
			return str +": ";
		} else {
			return "";
		}		
	}
	
	private void takeOff(ShipAPI ship,Vector2f landingLocation,boolean abort)
	{
		boolean done = false;
		Global.getSoundPlayer().playSound("fighter_takeoff", 1f, 1f, ship.getLocation(),new Vector2f());
		ship.getMutableStats().getHullDamageTakenMult().unmodify("invincible");
		ship.getMutableStats().getArmorDamageTakenMult().unmodify("invincible");
		ship.setInvalidTransferCommandTarget(false); 		
		ship.setCollisionClass(CollisionClass.SHIP);

		ship.getFluxTracker().stopOverload();
		ship.getFluxTracker().setCurrFlux(0f);
		Global.getCombatEngine().getCustomData().remove("armaa_strikecraftisLanding"+ship.getId());
		Global.getCombatEngine().getCustomData().put("armaa_strikecraft_hasWaypoint"+ship.getId(),false);
		Global.getCombatEngine().getCustomData().put("armaa_hangarIsOpen"+carrier.getId()+"_"+bayNo,false);		
		//if (fighterbay!=null && fighterbay.getWing() != null && fighterbay.getWing().getSource() != null) {
			armaa_utils.setLocation(ship,landingLocation); //set to location of carrier in case of drift
		//}
		
		if(!abort)
		{
			if ((ship.getCurrentCR() <= CRmarker)) 
			{ 
				ship.setCurrentCR(CRmarker+.1f);
			}
			ship.clearDamageDecals();
			ship.setHitpoints(ship.getMaxHitpoints()); //Hull to 100%
			pptMax = ship.getVariant().getHullSpec().getNoCRLossTime(); 				
			if(ship.getMutableStats().getPeakCRDuration().computeEffective(0f) < ship.getTimeDeployedForCRReduction())
				ship.getMutableStats().getPeakCRDuration().modifyFlat(ship.getId(),ship.getTimeDeployedForCRReduction());
						
			ship.clearDamageDecals();
			armaa_utils.setArmorPercentage(ship, 100f); //Armor to 100%
			List<WeaponAPI> weapons = ship.getAllWeapons();
			 for (WeaponAPI w : weapons) 
			 {
				if (w.usesAmmo()) w.resetAmmo();
			}	
		}
		((ShipAPI)ship).setAnimatedLaunch();
		ship.setControlsLocked(false);
		ship.setShipSystemDisabled(false);
		ship.getMutableStats().getCRLossPerSecondPercent().unmodify(ship.getId());

		for (ShipAPI modules : ship.getChildModulesCopy()) 
		{

			modules.setHitpoints(modules.getMaxHitpoints());
			modules.getFluxTracker().stopOverload();
			modules.getFluxTracker().setCurrFlux(0f);
			modules.clearDamageDecals();
			armaa_utils.setArmorPercentage(modules, 100f); //Armor to 100%
			modules.setAnimatedLaunch();
		}
		if(ship.isRetreating())
			ship.setRetreating(false, false); 
		if(Global.getCombatEngine().getPlayerShip() == ship)
			if(ship.getShipTarget() == carrier)
				ship.setShipTarget(null);
		ship.resetDefaultAI();
		Global.getCombatEngine().getCustomData().remove("armaa_repairTracker_"+ship.getId());
		Global.getCombatEngine().removePlugin(this);
	}	
}