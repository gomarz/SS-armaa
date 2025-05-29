package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import java.awt.Color;

import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.util.IntervalUtil;

import com.fs.starfarer.api.combat.listeners.*;

import org.lazywizard.lazylib.combat.DefenseUtils;

public class eis_wingClipper extends BaseHullMod
{
	private final float BONUS_MAX = 10f;
	private final float LIFE_BONUS = 3f;
	private static final float INCREASE_AMT = 1f;
	private IntervalUtil interval = new IntervalUtil(1.5f,1.5f);
	public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) 
	{
		
	}
	
	@Override
	public void applyEffectsAfterShipCreation(ShipAPI ship, java.lang.String id)
	{
		if(!ship.hasListenerOfClass(PeacekeeperDeatMod.class))
		{
			ship.addListener(new PeacekeeperDeatMod(ship));
		}		
	}
	
	@Override
	public void advanceInCampaign(FleetMemberAPI member, float amount)
	{

	}
	
    @Override
    public void advanceInCombat(ShipAPI ship, float amount)
    {
		String id = ship.getId();
		boolean player = ship == Global.getCombatEngine().getPlayerShip();		

		interval.advance(amount);
		MutableShipStatsAPI stats = ship.getMutableStats();
		if(Global.getCombatEngine().getCustomData().get("eis_wingClipper_bonus_"+ship.getId()) instanceof Float)
		{
			float currentBonus = Math.min(BONUS_MAX,(Float)Global.getCombatEngine().getCustomData().get("eis_wingClipper_bonus_"+ship.getId()));
			stats.getFluxDissipation().modifyPercent(id,currentBonus);
			stats.getFluxCapacity().modifyPercent(id,currentBonus);			
			stats.getBallisticRoFMult().modifyPercent(id, currentBonus);
			stats.getEnergyRoFMult().modifyPercent(id, currentBonus);		
			if(player)
				Global.getCombatEngine().maintainStatusForPlayerShip("eis_wingClipper", "graphics/ui/icons/icon_repair_refit.png","Air Superiority", "CAPACITY/DISS/ROF: +" + (int)currentBonus + "%",false);
			if(currentBonus > 0f && interval.intervalElapsed())
				Global.getCombatEngine().getCustomData().put("eis_wingClipper_bonus_"+ship.getId(),currentBonus-.1f);

			
		}
		else
		{
			stats.getFluxDissipation().unmodify(id);
			stats.getFluxCapacity().unmodify(id);
			stats.getBallisticRoFMult().unmodify(id);
			stats.getEnergyRoFMult().unmodify(id);		
		}
    }
	public String getDescriptionParam(int index, HullSize hullSize) {
		if (index == 0) return "" + (int)BONUS_MAX+"%";
		if (index == 1) return "" + (int)LIFE_BONUS+"%";		
		return null;
	}	
	//This listener ensures we die properly
	public static class PeacekeeperDeatMod implements DamageDealtModifier, AdvanceableListener 
	{
		float multiplier = 1f;
		protected ShipAPI ship;
		public PeacekeeperDeatMod(ShipAPI ship) {
			this.ship = ship;
		}
		
		public void advance(float amount) 
		{

		}

		public String modifyDamageDealt(Object param,CombatEntityAPI target, DamageAPI damage, Vector2f point, boolean shieldHit) 
		{
			if(!(target instanceof ShipAPI))
				return null;
			
			if(shieldHit)
				return null;
			
			ShipAPI s = (ShipAPI) target;
			if(s.isHulk())
				return null;
			if(!s.isFighter())
				return null;
			String id = "strikecraft_death";			
			float damageVal = damage.getDamage();
			float armor = DefenseUtils.getArmorValue(s,point);
			multiplier = damageVal;
			if(damageVal >= s.getHitpoints() && s.getHitpoints() > 0)
			{
				if(ship.getHitpoints() < ship.getMaxHitpoints())
				{
					ship.setHitpoints(Math.min(ship.getMaxHitpoints(),ship.getHitpoints()+s.getMaxHitpoints()*0.03f));
				}
				s.setHitpoints(0f);
				float fp = s.getWing() != null ? (float)s.getWing().getSpec().getFleetPoints()/(float)s.getWing().getSpec().getNumFighters() : INCREASE_AMT;
				//Global.getCombatEngine().applyDamage(s,point,99999f,DamageType.ENERGY,0f,true,false,ship);
				Global.getCombatEngine().addFloatingTextAlways(new Vector2f(target.getLocation().x,target.getLocation().y+target.getCollisionRadius()), "U killed me peacekeeper-chan", 20f, Color.white, target, 1f, 1f, 1f, 1f, 1f, .5f);
				float currentBonus = 0f;
				if(Global.getCombatEngine().getCustomData().get("eis_wingClipper_bonus_"+ship.getId()) instanceof Float)
				{
					currentBonus = (Float)Global.getCombatEngine().getCustomData().get("eis_wingClipper_bonus_"+ship.getId());
				}
				Global.getCombatEngine().getCustomData().put("eis_wingClipper_bonus_"+ship.getId(),currentBonus+fp);
			}
			return id;
		}
	}
}

