package data.scripts.shipsystems;

import java.util.HashMap;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript;
import org.lazywizard.lazylib.combat.CombatUtils;
import com.fs.starfarer.api.util.IntervalUtil;
import java.util.ArrayList;
import java.util.List;
import java.awt.*;

public class armaa_siegeModeStats extends BaseShipSystemScript {

	public static final float SENSOR_RANGE_PERCENT = 25f;
	public static final float WEAPON_RANGE_PERCENT = 80f;
	private IntervalUtil interval = new IntervalUtil(0.05f,0.05f);	
	
	public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
		
		ShipAPI ship = (ShipAPI)stats.getEntity();
		interval.advance(Global.getCombatEngine().getElapsedInLastFrame());
		if(interval.intervalElapsed())
		{
			List<ShipAPI> targets;
			
			if(Global.getCombatEngine().getCustomData().get("armaa_guardianProtocolShips"+ship.getId()) instanceof List)
				targets = (List)Global.getCombatEngine().getCustomData().get("armaa_guardianProtocolShips"+ship.getId());
			
			else
				targets = new ArrayList<>();

			for(ShipAPI target:CombatUtils.getShipsWithinRange(ship.getLocation(), 1000f))
			{
				if(target == ship)
					continue;
				if(ship.getOwner() != target.getOwner())
					continue;
				if(!targets.contains(target))
					targets.add(target);
			}
			
			for(ShipAPI target:targets)
			{
				target.getMutableStats().getShieldDamageTakenMult().modifyMult("armaa_guardianProtocol", (1f-0.1f)*effectLevel);
				if(Global.getCombatEngine().getPlayerShip() == target)
					Global.getCombatEngine().maintainStatusForPlayerShip(target.getId(), "graphics/icons/hullsys/fortress_shield.png","GRDIAN PRTCL","Shield damage reduced by " + (int)((1f-.9f)*100*effectLevel), false);
				
				target.setJitterUnder(ship, Color.white, .5f*effectLevel, 3, 15*effectLevel); 
				target.setJitterShields(true);
			}
			Global.getCombatEngine().getCustomData().put("armaa_guardianProtocolShips"+ship.getId(),targets);
		}		
		float sensorRangePercent = SENSOR_RANGE_PERCENT * effectLevel;
		float weaponRangePercent = WEAPON_RANGE_PERCENT * effectLevel;
		
		stats.getSightRadiusMod().modifyPercent(id, sensorRangePercent);
		stats.getMaxSpeed().modifyMult(id,0.50f);
		stats.getMaxTurnRate().modifyMult(id,0.50f);
		stats.getMaxRecoilMult().modifyMult(id,0.8f);		
		stats.getBallisticWeaponRangeBonus().modifyPercent(id, ship.getMutableStats().getSystemRangeBonus().computeEffective(weaponRangePercent));
		//stats.getBallisticRoFMult().modifyPercent(id, weaponRangePercent/3);
		//stats.getEnergyRoFMult().modifyPercent(id, weaponRangePercent/3);		
		stats.getEnergyWeaponRangeBonus().modifyPercent(id, weaponRangePercent);
		stats.getEnergyProjectileSpeedMult().modifyPercent(id, weaponRangePercent);
		stats.getBallisticProjectileSpeedMult().modifyPercent(id, weaponRangePercent);	

	}
	public void unapply(MutableShipStatsAPI stats, String id) {
		ShipAPI ship = (ShipAPI)stats.getEntity();
		stats.getSightRadiusMod().unmodify(id);
		
		stats.getBallisticWeaponRangeBonus().unmodify(id);
		stats.getEnergyWeaponRangeBonus().unmodify(id);
		stats.getBallisticRoFMult().unmodify(id);
		stats.getEnergyRoFMult().unmodify(id);	
		stats.getBallisticProjectileSpeedMult().unmodify(id);
		stats.getEnergyProjectileSpeedMult().unmodify(id);		
		stats.getMaxRecoilMult().unmodify(id);	
		stats.getMaxSpeed().unmodify(id);
		stats.getMaxTurnRate().unmodify(id);
		
		List<ShipAPI> targets; 
		if(Global.getCombatEngine().getCustomData().get("armaa_guardianProtocolShips"+ship.getId()) instanceof List)
		{
			targets = (List)Global.getCombatEngine().getCustomData().get("armaa_guardianProtocolShips"+ship.getId());		
			for(ShipAPI target:targets)
			{
				target.getMutableStats().getShieldDamageTakenMult().unmodify("armaa_guardianProtocol");
				target.setJitterShields(false);				
			}
			Global.getCombatEngine().getCustomData().put("armaa_guardianProtocolShips"+ship.getId(),null);	
		}
		
	}
	
	public StatusData getStatusData(int index, State state, float effectLevel) {
		float sensorRangePercent = SENSOR_RANGE_PERCENT * effectLevel;
		float weaponRangePercent = WEAPON_RANGE_PERCENT * effectLevel;
		if (index == 0) {
			return new StatusData("WPN PROJ SPEED +" + (int) weaponRangePercent + "%", false);
		} else if (index == 1) {
			//return new StatusData("increased energy weapon range", false);
			return null;
		} else if (index == 2) {
			return new StatusData("WPN/SNSR RNG, RECOIL +" + (int) weaponRangePercent + "%", false);
		}
		return null;
	}
	
}
