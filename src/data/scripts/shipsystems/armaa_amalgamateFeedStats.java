package data.scripts.shipsystems;


import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;

import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.combat.ShipSystemAPI;

public class armaa_amalgamateFeedStats extends BaseShipSystemScript {

	public static final Object KEY_JITTER = new Object();
	private float numFighters = 0;
	private float liveFighters = 0;
	private boolean broken = false;
	
	public static final float ROF_BONUS = .33f;
	public static final float FLUX_REDUCTION = 33f;
	
    private static final float SPEED_MANEUVERABILITY_INCREASE = 10f;
	private static final float SPEED_SPEED_INCREASE = 10f;
	public static final float MAX_TIME_MULT = .15f;	
	
	public static final Color JITTER_UNDER_COLOR = new Color(50,70,255,50);
	public static final Color JITTER_COLOR = new Color(50,70,255,75);
	
	public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
		
		ShipAPI ship = null;
		if (stats.getEntity() instanceof ShipAPI) {
			ship = (ShipAPI) stats.getEntity();
		} else {
			return;
		}
	
		float squadBonus = (liveFighters)/(Math.max(1,numFighters));
		squadBonus = Math.min(1,squadBonus);
		Global.getCombatEngine().getCustomData().put("armaa_amalgamateFeedStats" + "_" +"squadStrength"+"_"+ ship.getId(),squadBonus);
		
		float mult = 1f + (ROF_BONUS * effectLevel*squadBonus);
		stats.getBallisticRoFMult().modifyMult(id, mult);
		stats.getEnergyRoFMult().modifyMult(id, mult);
		
		stats.getBallisticWeaponFluxCostMod().modifyMult(id, 1f - (FLUX_REDUCTION * 0.01f)*effectLevel*squadBonus);
		stats.getEnergyWeaponFluxCostMod().modifyMult(id, 1f - (FLUX_REDUCTION * 0.01f)*effectLevel*squadBonus);

		stats.getMaxTurnRate().modifyPercent(id, SPEED_MANEUVERABILITY_INCREASE * effectLevel*squadBonus);
		stats.getAcceleration().modifyPercent(id, SPEED_MANEUVERABILITY_INCREASE * effectLevel*squadBonus);
		stats.getDeceleration().modifyPercent(id, SPEED_MANEUVERABILITY_INCREASE * effectLevel*squadBonus);
		stats.getTurnAcceleration().modifyPercent(id, SPEED_MANEUVERABILITY_INCREASE * effectLevel*squadBonus);
		stats.getMaxSpeed().modifyPercent(id, SPEED_SPEED_INCREASE * effectLevel*squadBonus);
		
		//float adjTimeMult = (MAX_TIME_MULT*squadBonus)+1f;
		//float shipTimeMult = 1f + (adjTimeMult - 1f) * effectLevel;
		//stats.getTimeMult().modifyMult(id, shipTimeMult);
		
		//if (ship == Global.getCombatEngine().getPlayerShip()) {
		//	Global.getCombatEngine().getTimeMult().modifyMult(id, 1f / shipTimeMult);
		//}
		//else {
		//	Global.getCombatEngine().getTimeMult().unmodify(id);
		//}
		
		if (effectLevel > 0) 
		{
			float jitterLevel = effectLevel*squadBonus;
			float maxRangeBonus = 5f;
			float jitterRangeBonus = jitterLevel * maxRangeBonus*squadBonus;

			ship.setWeaponGlow(effectLevel*squadBonus, Misc.setAlpha(JITTER_UNDER_COLOR, 50), EnumSet.allOf(WeaponType.class));
			
			ship.setJitterUnder(KEY_JITTER, JITTER_COLOR, jitterLevel, 5, 0f, jitterRangeBonus);
			ship.setJitter(KEY_JITTER, JITTER_UNDER_COLOR, jitterLevel, 2, 0f, 0 + jitterRangeBonus * 1f);
			Global.getSoundPlayer().playLoop("system_targeting_feed_loop", ship, 1f, 1f, ship.getLocation(), ship.getVelocity());
			
			for (ShipAPI fighter : getFighters(ship)) 
			{
				MutableShipStatsAPI fStats = fighter.getMutableStats();
				
				//fStats.getBallisticWeaponDamageMult().modifyMult(id, 1f + 0.01f * DAMAGE_INCREASE_PERCENT * effectLevel*squadBonus);
				//fStats.getEnergyWeaponDamageMult().modifyMult(id, 1f + 0.01f * DAMAGE_INCREASE_PERCENT * effectLevel*squadBonus);
				//fStats.getMissileWeaponDamageMult().modifyMult(id, 1f + 0.01f * DAMAGE_INCREASE_PERCENT * effectLevel*squadBonus);
				fStats.getBallisticRoFMult().modifyMult(id, mult);
				fStats.getEnergyRoFMult().modifyMult(id, mult);	
				
				fStats.getBallisticWeaponFluxCostMod().modifyMult(id, 1f - (FLUX_REDUCTION * 0.01f)*effectLevel*squadBonus);
				fStats.getEnergyWeaponFluxCostMod().modifyMult(id, 1f - (FLUX_REDUCTION * 0.01f)*effectLevel*squadBonus);
				
				//fStats.getTimeMult().modifyMult(id, shipTimeMult);
				fStats.getMaxTurnRate().modifyPercent(id, SPEED_MANEUVERABILITY_INCREASE * effectLevel*squadBonus);
				fStats.getAcceleration().modifyPercent(id, SPEED_MANEUVERABILITY_INCREASE * effectLevel*squadBonus);
				fStats.getDeceleration().modifyPercent(id, SPEED_MANEUVERABILITY_INCREASE * effectLevel*squadBonus);
				fStats.getTurnAcceleration().modifyPercent(id, SPEED_MANEUVERABILITY_INCREASE * effectLevel*squadBonus);
				fStats.getMaxSpeed().modifyPercent(id, SPEED_SPEED_INCREASE * effectLevel*squadBonus);
				
				if (jitterLevel > 0) 
				{
					fighter.setWeaponGlow(effectLevel*squadBonus, Misc.setAlpha(JITTER_UNDER_COLOR, 200), EnumSet.allOf(WeaponType.class));
					
					fighter.setJitterUnder(KEY_JITTER, JITTER_COLOR, jitterLevel, 5, 0f, jitterRangeBonus);
					fighter.setJitter(KEY_JITTER, JITTER_UNDER_COLOR, jitterLevel, 2, 0f, 0 + jitterRangeBonus * 1f);
					Global.getSoundPlayer().playLoop("system_targeting_feed_loop", ship, 1f, 1f, fighter.getLocation(), fighter.getVelocity());
				}
			}
		}
	}
	

	private List<ShipAPI> getFighters(ShipAPI carrier) {
	List<ShipAPI> result = new ArrayList<>(30);
		
		int i = 0;
		for (ShipAPI ship : Global.getCombatEngine().getShips()) {
			if (!ship.isFighter()) continue;
			if (ship.getWing() == null) continue;
			if(ship.isHulk())
				continue;
			if(ship.getWing().getSourceShip() == null)
				continue;
			if (ship.getWing().getSourceShip() == carrier) {
				if(ship.getWing().isReturning(ship))
					continue;
				result.add(ship);
				numFighters = ship.getWing().getSpec().getNumFighters();
				i = ship.getWing().getWingMembers().size();
			}
		}
		liveFighters = result.size();
		return result;
	}
	
	public void unapply(MutableShipStatsAPI stats, String id) 
	{
		//stats.getBallisticWeaponDamageMult().unmodify(id);
		//stats.getEnergyWeaponDamageMult().unmodify(id);
		//stats.getMissileWeaponDamageMult().unmodify(id);
		
		stats.getBallisticWeaponFluxCostMod().unmodify(id);
		stats.getEnergyWeaponFluxCostMod().unmodify(id);
		stats.getMissileWeaponFluxCostMod().unmodify(id);
		
		stats.getBallisticRoFMult().unmodify(id);
		
		stats.getEnergyRoFMult().unmodify(id);

		
	//	Global.getCombatEngine().getTimeMult().unmodify(id);
	//	stats.getTimeMult().unmodify(id);
		stats.getMaxTurnRate().unmodify(id);
		stats.getAcceleration().unmodify(id);
		stats.getDeceleration().unmodify(id);
		stats.getTurnAcceleration().unmodify(id);
		stats.getMaxSpeed().unmodify(id);		
		
		ShipAPI ship = null;
		if (stats.getEntity() instanceof ShipAPI) {
			ship = (ShipAPI) stats.getEntity();
		} else {
			return;
		}
		
		for (ShipAPI fighter : getFighters(ship)) {
			if (fighter.isHulk()) continue;
			MutableShipStatsAPI fStats = fighter.getMutableStats();
			//fStats.getBallisticWeaponDamageMult().unmodify(id);
			//fStats.getEnergyWeaponDamageMult().unmodify(id);
			//fStats.getMissileWeaponDamageMult().unmodify(id);
			
			fStats.getBallisticRoFMult().unmodify(id);
		
			fStats.getEnergyRoFMult().unmodify(id);
			
			fStats.getBallisticWeaponFluxCostMod().unmodify(id);
			fStats.getEnergyWeaponFluxCostMod().unmodify(id);
			fStats.getMissileWeaponFluxCostMod().unmodify(id);
		//	fStats.getTimeMult().unmodify(id);
			fStats.getMaxTurnRate().unmodify(id);
			fStats.getAcceleration().unmodify(id);
			fStats.getDeceleration().unmodify(id);
			fStats.getTurnAcceleration().unmodify(id);
			fStats.getMaxSpeed().unmodify(id);	
		}
	}
	
    public static float getGauge(ShipAPI ship) {
        if ((ship == null) || (ship.getSystem() == null)) {
            return 0f;
        }

        Object data = Global.getCombatEngine().getCustomData().get("armaa_amalgamateFeedStats" + "_" +"squadStrength"+"_"+ ship.getId());
        if (data instanceof Float) {
            float gauge = (float) data;

            return gauge;
        } else {
            return 100f;
        }
    }
	
    @Override
    public boolean isUsable(ShipSystemAPI system, ShipAPI ship) 
	{
		getFighters(ship);
		float squadBonus = (liveFighters)/(Math.max(1,numFighters));
		Global.getCombatEngine().getCustomData().put("armaa_amalgamateFeedStats" + "_" +"squadStrength"+"_"+ ship.getId(),squadBonus);
		
		if(liveFighters == 0)
			broken = true;
		
		else broken = false;
		
		float gauge = (liveFighters)/(Math.max(1,numFighters));
        if (gauge < 0) {
            return false;
        }
        return isUsable(ship, system);
    }
	
    public static boolean isUsable(ShipAPI ship, ShipSystemAPI system) {
        if ((ship == null) || (system == null)) {
            return false;
        }

         float overGaugeLevel = 1f/2f;

        float gauge = getGauge(ship);

        return !((gauge <= 0f) && !system.isActive());
    }
	
    @Override
    public String getInfoText(ShipSystemAPI system, ShipAPI ship) 
	{
        float overGaugeLevel = 1f/2f;
        float gauge = getGauge(ship);
        int displayGauge = Math.round(100f * Math.max(0f, gauge));
        if (broken) {
            return "SQUAD BROKEN";
        }
		if (system.isActive()) {
            return "" + displayGauge + "% - ENGAGED";
        }
		
		else
		{
			if ((gauge /1f) > .50f && gauge/1f < 1f) {
				long count300ms = (long) Math.floor(Global.getCombatEngine().getTotalElapsedTime(true) / 0.3f);
				if (count300ms % 3L == 0L) {
					return "" + displayGauge + "% - CASUALTIES SUFFERED";
				} else {
					return "" + displayGauge + "% - ";
				}
			}
			
			else if ((gauge < overGaugeLevel)) {
				long count300ms = (long) Math.floor(Global.getCombatEngine().getTotalElapsedTime(true) / 0.3f);
				if (count300ms % 3L == 0L) {
					return "" + displayGauge + "% - HEAVY LOSSES";
				} else {
					return "" + displayGauge + "% - ";
				}
			}
		}
		
        return "" + displayGauge + "%";
    }
	
	public StatusData getStatusData(int index, State state, float effectLevel) {
		float squadBonus = (liveFighters)/(Math.max(1,numFighters));
		float percent = MAX_TIME_MULT*effectLevel*squadBonus;
		if (index ==0) {
			return new StatusData("+" + Math.round(SPEED_MANEUVERABILITY_INCREASE * effectLevel*squadBonus) + "% squadron maneuverability", false);
		}
		
		if (index==1)
		{
			return new StatusData("+" + Math.round(SPEED_SPEED_INCREASE * effectLevel*squadBonus) + "% squadron speed", false);
		}
		if (index==2)
		{
			return new StatusData("WEP FLX COST -" + Math.round(FLUX_REDUCTION*effectLevel*squadBonus) + "%", false);
		}
		if (index==3)
		{
			return new StatusData("WEP ROF +" + Math.round(ROF_BONUS*100f*effectLevel*squadBonus) + "%", false);
		}

		return null;
	}
}
