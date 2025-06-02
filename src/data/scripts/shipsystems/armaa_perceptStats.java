package data.scripts.shipsystems;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.Global;
import data.scripts.util.armaa_utils;
import data.scripts.util.armaa_homingDualLaserScript;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;
import java.awt.Color;

public class armaa_perceptStats extends BaseShipSystemScript {

	public static float DAMAGE_MULT = 0.9f;
	public static DamageType resType = DamageType.HIGH_EXPLOSIVE;

	public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) 
	{
		
		ShipAPI ship = (ShipAPI)stats.getEntity();
		boolean mode = Global.getCombatEngine()
		.getCustomData().get("armaa_tranformState_"+ship.getId()) != null ? 
			(Boolean)Global.getCombatEngine().getCustomData().get("armaa_tranformState_"+ship.getId()) : false;			
		armaa_utils.createChargeParticle(effectLevel, ship.getLocation(), ship,new Color(128, 180,242,Math.min(255, (int)(150 * Math.max(0f, effectLevel)))),4f);
		float jitterLevel = effectLevel;
		float jitterRangeBonus = 0;
		float maxRangeBonus = 10f;
		if (state == State.IN) {
			jitterLevel = effectLevel / (1f / ship.getSystem().getChargeUpDur());
			if (jitterLevel > 1) {
				jitterLevel = 1f;
			}
			jitterRangeBonus = jitterLevel * maxRangeBonus;
		} else if (state == State.ACTIVE) {
			jitterLevel = 1f;
			jitterRangeBonus = maxRangeBonus;
		} else if (state == State.OUT) {
			jitterRangeBonus = jitterLevel * maxRangeBonus;
		}
		jitterLevel = (float) Math.sqrt(jitterLevel);		
		ship.setJitterUnder(this, new Color(128, 180,242,Math.min(255, (int)(150 * Math.max(0f, effectLevel)))), jitterLevel, 25, 0f, 7f + jitterRangeBonus);			
	}
	
	public void unapply(MutableShipStatsAPI stats, String id) 
	{
		ShipAPI ship = (ShipAPI)stats.getEntity();
		float gauge = ship.getFluxTracker().getFluxLevel();
		if(ship.isAlive() && !ship.getFluxTracker().isOverloaded())
		{
			Global.getCombatEngine().addPlugin(new armaa_homingDualLaserScript(ship,gauge));
		}
	}
	
	public StatusData getStatusData(int index, State state, float effectLevel) {
		if (index == 0) {
			return new StatusData("CHARGING...", false);
		}

		return null;
	}
}
