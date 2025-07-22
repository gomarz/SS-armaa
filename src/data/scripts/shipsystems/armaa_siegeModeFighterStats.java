package data.scripts.shipsystems;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.util.IntervalUtil;
import java.util.List;

public class armaa_siegeModeFighterStats extends BaseShipSystemScript {

	public static final float SENSOR_RANGE_PERCENT = 30f;
	public static final float WEAPON_RANGE_PERCENT = 30f;
	private float rotation = 0f;
	private IntervalUtil interval = new IntervalUtil(0.05f,0.05f);	
	private List<ShipAPI> targets;	
	private boolean reloaded = false;
	public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
		
		ShipAPI ship = (ShipAPI)stats.getEntity();
		float sensorRangePercent = SENSOR_RANGE_PERCENT * effectLevel;
		float weaponRangePercent = WEAPON_RANGE_PERCENT * effectLevel;
		for(WeaponAPI weapon : ship.getAllWeapons())
		{
			if(!weapon.getSlot().isHardpoint())
			{}
				//weapon.setForceNoFireOneFrame(true);
			else
			{
				if(!reloaded)
				{
					reloaded = true;
					weapon.setAmmo(weapon.getAmmo()+1);
					weapon.setRemainingCooldownTo(0);
				}
			}
		}
		if(ship.getWing() != null)
		{
			for(ShipAPI fighter: ship.getWing().getWingMembers())
			{
				if(!fighter.getSystem().isOn() && fighter.areAnyEnemiesInRange())
					fighter.useSystem();
			}
		}		
		stats.getSightRadiusMod().modifyPercent(id, sensorRangePercent);
		stats.getMaxSpeed().modifyMult(id,0.5f*effectLevel);
		//stats.getMaxTurnRate().modifyMult(id,0.50f*effectLevel);
		stats.getMaxRecoilMult().modifyMult(id,weaponRangePercent*effectLevel);
		stats.getBallisticWeaponRangeBonus().modifyPercent(id, ship.getMutableStats().getSystemRangeBonus().computeEffective(weaponRangePercent)*effectLevel);
		stats.getEnergyWeaponRangeBonus().modifyPercent(id, ship.getMutableStats().getSystemRangeBonus().computeEffective(weaponRangePercent)*effectLevel);
		stats.getEnergyWeaponFluxCostMod().modifyMult(id, 1f - (50f * 0.01f));
		stats.getBallisticWeaponFluxCostMod().modifyMult(id, 1f - (50f * 0.01f));
		stats.getBallisticRoFMult().modifyPercent(id, 1.1f*effectLevel);
		stats.getEnergyRoFMult().modifyPercent(id, 1.1f*effectLevel);
		stats.getEnergyProjectileSpeedMult().modifyPercent(id, weaponRangePercent*effectLevel);
		stats.getBallisticProjectileSpeedMult().modifyPercent(id, weaponRangePercent*effectLevel);	

	}
	public void unapply(MutableShipStatsAPI stats, String id) {
		ShipAPI ship = (ShipAPI)stats.getEntity();
		stats.getSightRadiusMod().unmodify(id);
		
		stats.getBallisticWeaponRangeBonus().unmodify(id);
		stats.getEnergyWeaponRangeBonus().unmodify(id);
		
		stats.getBallisticRoFMult().unmodify(id);
		stats.getEnergyRoFMult().unmodify(id);	
		stats.getBallisticWeaponFluxCostMod().unmodify(id);
		stats.getEnergyWeaponFluxCostMod().unmodify(id);		
		stats.getBallisticProjectileSpeedMult().unmodify(id);
		stats.getEnergyProjectileSpeedMult().unmodify(id);		
		stats.getMaxRecoilMult().unmodify(id);
		stats.getMaxSpeed().unmodify(id);
	
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
			return new StatusData("WPN/SNSR RNG+" + (int) weaponRangePercent + "%", false);
		}
		return null;
	}
	
}
