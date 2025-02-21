package data.scripts.shipsystems;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import java.util.List;

public class armaa_sensorDroneStats extends BaseShipSystemScript
{

	public static final float SENSOR_RANGE_PERCENT = 25f;
	public static final float WEAPON_RANGE_PERCENT = 15f;
	public static final float ROF_MULT = 0.95f;	
	
	public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) 
	{
		float sensorRangePercent = SENSOR_RANGE_PERCENT * effectLevel;
		float weaponRangePercent = WEAPON_RANGE_PERCENT * effectLevel;
		float weaponRoFPercent = 1f-(1f-ROF_MULT) * effectLevel;
		
		stats.getSightRadiusMod().modifyPercent(id, sensorRangePercent);
		
		stats.getBallisticWeaponRangeBonus().modifyPercent(id, weaponRangePercent);
		stats.getEnergyWeaponRangeBonus().modifyPercent(id, weaponRangePercent);
		stats.getBallisticRoFMult().modifyMult(id, weaponRoFPercent);
		stats.getEnergyRoFMult().modifyMult(id, weaponRoFPercent);		
		ShipAPI ship = (ShipAPI)stats.getEntity();
		List<ShipAPI> children = ship.getChildModulesCopy();
			if(children != null)
			{
				for(ShipAPI module: children)	
				{
					module.ensureClonedStationSlotSpec();

					if(module.getStationSlot() != null)
					{
						module.getMutableStats().getBallisticWeaponRangeBonus().modifyPercent(id, weaponRangePercent);
						module.getMutableStats().getEnergyWeaponRangeBonus().modifyPercent(id, weaponRangePercent);
						module.getMutableStats().getBallisticRoFMult().modifyMult(id, weaponRoFPercent);
						module.getMutableStats().getEnergyRoFMult().modifyMult(id, weaponRoFPercent);							
					}
				}
			}		
		
	}
	public void unapply(MutableShipStatsAPI stats, String id) {
		stats.getSightRadiusMod().unmodify(id);
		
		stats.getBallisticWeaponRangeBonus().unmodify(id);
		stats.getEnergyWeaponRangeBonus().unmodify(id);
		stats.getBallisticRoFMult().unmodify(id);
		stats.getEnergyRoFMult().unmodify(id);				
		ShipAPI ship = (ShipAPI)stats.getEntity();	
		List<ShipAPI> children = ship.getChildModulesCopy();
		if(children != null)
		{
			for(ShipAPI module: children)	
			{
				module.ensureClonedStationSlotSpec();

				if(module.getStationSlot() != null)
				{
					module.getMutableStats().getBallisticWeaponRangeBonus().unmodify(id);
					module.getMutableStats().getEnergyWeaponRangeBonus().unmodify(id);
					module.getMutableStats().getBallisticRoFMult().unmodify(id);
					module.getMutableStats().getEnergyRoFMult().unmodify(id);		
				}
			}
		}			
	}
	
	public StatusData getStatusData(int index, State state, float effectLevel) {
		float sensorRangePercent = SENSOR_RANGE_PERCENT * effectLevel;
		float weaponRangePercent = WEAPON_RANGE_PERCENT * effectLevel;
		float weaponRoFPercent = 1f-(1f-ROF_MULT)*100f*effectLevel;		
		if (index == 0) {
			return new StatusData("sensor range +" + (int) sensorRangePercent + "%", false);
		} else if (index == 1) {
			//return new StatusData("increased energy weapon range", false);
			return null;
		} else if (index == 2) {
			return new StatusData("weapon range +" + (int) weaponRangePercent + "%", false);
		}
		else if (index == 3) {
			return new StatusData("weapon RoF " + (int) weaponRoFPercent + "%", false);
		}		
		return null;
	}
}
