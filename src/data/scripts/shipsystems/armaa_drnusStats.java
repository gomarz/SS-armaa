package data.scripts.shipsystems;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.Global;
import data.scripts.util.armaa_utils;
import data.scripts.util.armaa_drnsScriptMorgana;
import java.awt.Color;

public class armaa_drnusStats extends BaseShipSystemScript {

	public static float DAMAGE_MULT = 0.9f;
	public static DamageType resType = DamageType.HIGH_EXPLOSIVE;

	public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) 
	{
		
		ShipAPI s = (ShipAPI)stats.getEntity();
		if(Global.getCombatEngine().getCustomData().containsKey("armaa_drnus_"+s.getId()))
		{
			resType = (DamageType)Global.getCombatEngine().getCustomData().get("armaa_drnus_"+s.getId());
		}			
		stats.getShieldDamageTakenMult().modifyMult("armaa_drnus_"+s.getId(),0.50f);
		armaa_utils.createChargeParticleNoCore(0.7f, s.getLocation(), s,new Color(200, 0,0,150),6f);
		s.setJitter(s, new Color(199, 146,0,100),0.7f, 1, 4, 25);
		s.setJitterShields(true); 		
		if((Float)Global.getCombatEngine().getCustomData().get("armaa_morgana_bonus_"+s.getId()) != null)
		{
			float guardBar = (Float)Global.getCombatEngine().getCustomData().get("armaa_morgana_bonus_"+s.getId());
			double strength = guardBar;
			float currentBonus = Math.max(0f,(float)strength);
				
			if(1f-(currentBonus/100f) <= 0f)
				unapply(stats,id);
		}
	}
	
	public void unapply(MutableShipStatsAPI stats, String id) 
	{
		ShipAPI ship = (ShipAPI)stats.getEntity();
		
		float gauge = (Float)Global.getCombatEngine().getCustomData().get(
		"armaa_morgana_absorbed_"+ship.getId()) != null ? (Float)Global.getCombatEngine().getCustomData().get("armaa_morgana_absorbed_"+ship.getId()):0;
		stats.getShieldDamageTakenMult().unmodify("armaa_drnus_"+ship.getId());		
		if(gauge > 0 && Global.getCombatEngine().getCustomData().get("armaa_morgana_absorbed_"+ship.getId()+"_countering") == null)
		{
			Global.getCombatEngine().addPlugin(new armaa_drnsScriptMorgana(ship,gauge));
			Global.getCombatEngine().getCustomData().put("armaa_morgana_absorbed_"+ship.getId()+"_countering","-");
		}
		
	}
	
	public StatusData getStatusData(int index, State state, float effectLevel) {
		if (index == 0) {
			return new StatusData("shield absorbs 10x damage"+resType, false);
		}

		return null;
	}
}
