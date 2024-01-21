package data.scripts.shipsystems;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript;
import com.fs.starfarer.api.Global;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lazywizard.lazylib.CollisionUtils;
import org.lazywizard.lazylib.combat.DefenseUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;
import org.lazywizard.lazylib.MathUtils;
import com.fs.starfarer.api.combat.ShipAPI;
import data.scripts.util.armaa_utils;
import data.scripts.util.armaa_takeOffScript;
import java.awt.Color;
import java.util.*;
import com.fs.starfarer.api.combat.WeaponAPI.*;
import com.fs.starfarer.api.loading.WeaponSlotAPI;

import com.fs.starfarer.api.combat.WeaponGroupAPI;
import com.fs.starfarer.api.combat.MissileRenderDataAPI;
import com.fs.starfarer.api.combat.FighterWingAPI;
import com.fs.starfarer.api.combat.FighterLaunchBayAPI;
public class armaa_TravelDriveStats extends BaseShipSystemScript {

private boolean carriersNearby = false;
private boolean runOnce = false;
private WeaponSlotAPI w;
private ShipAPI carrier;

	public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) 
	{
		if(Global.getCombatEngine().isPaused())
			return;		
		ShipAPI ship = (ShipAPI)stats.getEntity();
		boolean standardDeploy = ship.getFacing() == 90f ? true:false;

		if(ship.getOwner() == 1)
			standardDeploy = ship.getFacing() == 270f ? true:false;
		boolean alreadyDone = Global.getCombatEngine().getCustomData().get("armaa_carrierDeployDone_"+ship.getId()) instanceof Boolean ? true : false;

		if(getRandomCarrier(ship,true) ==null || ship.isRetreating() || !standardDeploy || alreadyDone)
		{
			if (state == ShipSystemStatsScript.State.OUT) {
				stats.getMaxSpeed().unmodify(id); // to slow down ship to its regular top speed while powering drive down
			} else 
			{
				stats.getMaxSpeed().modifyFlat(id, 100f * effectLevel);
				stats.getAcceleration().modifyFlat(id, 100f * effectLevel);
			}
			Global.getCombatEngine().getCustomData().put("armaa_carrierDeployDone_"+ship.getId(),true);
		}
		
		//Carriers detected "launch" from them
		else
		{	
			if(!ship.isLanding()&& !runOnce)
			{
				carrier = getRandomCarrier(ship,false);
				Vector2f takeOffLoc = null;
				for(WeaponSlotAPI wep:carrier.getHullSpec().getAllWeaponSlotsCopy())
				{
					//Vector2f takeOffLoc = carrier.getLocation();
					
					if(wep.getWeaponType() == WeaponType.LAUNCH_BAY)
					{
						if(Global.getCombatEngine().getPlayerShip() == ship)
						{
							Global.getSoundPlayer().playSound("ui_noise_static", 1f+MathUtils.getRandomNumberInRange(-0.3f, .3f), 1f, carrier.getLocation(),new Vector2f());
							carrier.getFluxTracker().showOverloadFloatyIfNeeded("Good luck out there!", Color.white, 2f, true);
						}
						
						ship.setFacing(carrier.getFacing()+wep.getAngle());
						takeOffLoc = new Vector2f(wep.computePosition(carrier));
						
						if(Math.random() <= .50f)
							break;
					}
				}
				
				if(takeOffLoc == null)
				{
					takeOffLoc = carrier.getLocation();
				}
				ship.setLaunchingShip(carrier);
				//VectorUtils.rotate(takeOffLoc,carrier.getFacing());				
				armaa_utils.setLocation(ship,takeOffLoc);
				ship.setAnimatedLaunch();
				Global.getSoundPlayer().playSound("fighter_takeoff", 1f, 1f, ship.getLocation(),new Vector2f());
				CombatUtils.applyForce(ship,ship.getFacing(),carrier.getMaxSpeed());
				runOnce = true;
			}
		}			
	}
	
	public void unapply(MutableShipStatsAPI stats, String id) 
	{
		ShipAPI ship = (ShipAPI)stats.getEntity();
		stats.getMaxSpeed().unmodify(id);
		stats.getMaxTurnRate().unmodify(id);
		stats.getTurnAcceleration().unmodify(id);
		stats.getAcceleration().unmodify(id);
		stats.getDeceleration().unmodify(id);;
	}
	
	public StatusData getStatusData(int index, State state, float effectLevel) 
	{
		if (index == 0) {
			return new StatusData("increased engine power", false);
		}
		return null;
	}

	private ShipAPI getRandomCarrier(ShipAPI ship, boolean initial)
	{
		ShipAPI potCarrier = null;
		//float distance = 99999f;
		for (ShipAPI carrier : CombatUtils.getShipsWithinRange(ship.getLocation(), 20000.0F)) 
		{
			int takenSlots = 0;
			if(Global.getCombatEngine().getCustomData().get("armaa_launchSlots"+carrier.getId()) instanceof Integer)
			{
				takenSlots = (int)Global.getCombatEngine().getCustomData().get("armaa_launchSlots"+carrier.getId());
			}
			
			else
				Global.getCombatEngine().getCustomData().put("armaa_launchSlots"+carrier.getId(),takenSlots);
			
			if(ship.getHullSpec().hasTag("strikecraft_medium"))
			{
				if(carrier.isDestroyer())
					continue;
			}

			else if(ship.getHullSpec().hasTag("strikecraft_large"))
			{
				if(carrier.isCruiser() || carrier.isDestroyer())
					continue;
			}
			if(takenSlots >= carrier.getNumFighterBays())
				continue;
				
			if(carrier.getOwner() != ship.getOwner() || carrier.isFighter() || carrier.isFrigate() || carrier == ship)
				continue;
			
			if(carrier.getOwner() == ship.getOwner() && ((carrier.isAlly() && !ship.isAlly())))
				continue;
			
			if(carrier.isHulk())
				continue;

			if(carrier.getNumFighterBays() > 0 && carrier.getHullSpec().getFighterBays() > 0)
			{
				potCarrier = carrier; 
				if(!initial)
				{
					Global.getCombatEngine().getCustomData().put("armaa_launchSlots"+potCarrier.getId(),takenSlots+1);
					return potCarrier;

				}
			}				
		}		
		return potCarrier;
	}	
}
