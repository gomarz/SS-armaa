package data.scripts.shipsystems;

import java.awt.Color;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript;

public class EnergySurgeStats extends BaseShipSystemScript {

	public static final float DAMAGE_BONUS_PERCENT = 0f;
	public static float SPEED_BONUS = 50f;
	public static float TURN_BONUS = 100f;
	public float bonusPercent = 0f;
	//private Color color = new Color(100,255,100,255);
	private Color color = new Color(255,0,115,255);

	public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
		
		ShipAPI ship = (ShipAPI) stats.getEntity();
		bonusPercent = (DAMAGE_BONUS_PERCENT + (ship.getFluxLevel())*25f) * effectLevel;
		//stats.getEnergyWeaponDamageMult().modifyPercent(id, bonusPercent);

		if (state == ShipSystemStatsScript.State.OUT) {
			stats.getMaxSpeed().unmodify(id); //slow ship to regular top speed while powering drive down
			stats.getMaxTurnRate().unmodify(id);
		} else {
			stats.getMaxSpeed().modifyFlat(id, SPEED_BONUS);
			stats.getAcceleration().modifyPercent(id, SPEED_BONUS * 3f * effectLevel);
			stats.getDeceleration().modifyPercent(id, SPEED_BONUS * 3f * effectLevel);
			stats.getTurnAcceleration().modifyFlat(id, TURN_BONUS * effectLevel);
			stats.getTurnAcceleration().modifyPercent(id, TURN_BONUS * 5f * effectLevel);
			
			stats.getMaxTurnRate().modifyFlat(id, 15f);
			stats.getMaxTurnRate().modifyPercent(id, 100f);
			
			stats.getEnergyRoFMult().modifyMult(id,0.50f);
			stats.getMaxRecoilMult().modifyMult(id,2f);
			stats.getRecoilPerShotMult().modifyMult(id,2f);
			
			stats.getEnergyWeaponDamageMult().modifyPercent(id, bonusPercent);
		}
		
		if (stats.getEntity() instanceof ShipAPI) 
		{
			//ship = (ShipAPI) stats.getEntity();
			
			ship.getEngineController().fadeToOtherColor(this, color, new Color(0,0,0,0), effectLevel, 0.67f);
			ship.getEngineController().extendFlame(this, 1.35f * effectLevel, 1.35f * effectLevel, 0f * effectLevel);
		}
	}
	public void unapply(MutableShipStatsAPI stats, String id) {
		stats.getMaxSpeed().unmodify(id);
		stats.getMaxTurnRate().unmodify(id);
		stats.getTurnAcceleration().unmodify(id);
		stats.getAcceleration().unmodify(id);
		stats.getDeceleration().unmodify(id);
		
		stats.getMaxRecoilMult().unmodify(id);
		stats.getRecoilPerShotMult().unmodify(id);
		stats.getEnergyRoFMult().unmodify(id);
		stats.getEnergyWeaponDamageMult().unmodify(id);

	}
	
	public StatusData getStatusData(int index, State state, float effectLevel) {
		//float bonusPercent = DAMAGE_BONUS_PERCENT * effectLevel;
		if (index == 0) 
		{
			return new StatusData("Improved maneuverability, +" + (int)bonusPercent+"% DMG", false);
		}
		return null;
	}
}
