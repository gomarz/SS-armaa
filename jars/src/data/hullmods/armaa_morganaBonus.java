package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import java.util.*;
import java.awt.Color;

import org.lwjgl.util.vector.Vector2f;
import data.scripts.util.armaa_utils;
import com.fs.starfarer.api.util.IntervalUtil;

import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.combat.listeners.*;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.magiclib.util.MagicRender;
import org.lazywizard.lazylib.combat.CombatUtils;

import org.lazywizard.lazylib.combat.DefenseUtils;

public class armaa_morganaBonus extends BaseHullMod
{
	private final float BONUS_MAX = 100f;
	private final float DAMAGE_CAP = 10000f;
	private IntervalUtil interval = new IntervalUtil(1.5f,1.5f);
	public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) 
	{
		
	}
	
	@Override
	public void applyEffectsAfterShipCreation(ShipAPI ship, java.lang.String id)
	{
		if(!ship.hasListenerOfClass(MorganaDamageListener.class))
		{
			ship.addListener(new MorganaDamageListener(ship));
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
		if(Global.getCombatEngine().getCustomData().get("armaa_morgana_bonus_"+ship.getId()) instanceof Float)
		{
			float damageDealt = (Float)Global.getCombatEngine().getCustomData().get("armaa_morgana_bonus_"+ship.getId());
			double strength = damageDealt;
			float currentBonus = Math.min(BONUS_MAX,(float)strength);
			stats.getEnergyWeaponDamageMult().modifyPercent(id,currentBonus);
			stats.getBallisticWeaponDamageMult().modifyPercent(id,currentBonus);
			stats.getTimeMult().modifyPercent(id,currentBonus);
			if(player)
				Global.getCombatEngine().maintainStatusForPlayerShip("armaa_morganaBonus", "graphics/ui/icons/icon_repair_refit.png","Air Superiority", "TRN RATE/DISS/AIM: +" + (float)currentBonus + "%",false);
			if(currentBonus <= 100f && interval.intervalElapsed())
				Global.getCombatEngine().getCustomData().put("armaa_morgana_bonus_"+ship.getId(),currentBonus+amount);
		}
		else
		{
			stats.getEnergyWeaponDamageMult().unmodify(id); 
			stats.getBallisticWeaponDamageMult().unmodify(id);
			stats.getTimeMult().unmodify(id);
		}
    }
	//This listener ensures we die properly
	public static class MorganaDamageListener implements DamageDealtModifier, AdvanceableListener 
	{
		float multiplier = 1f;
		protected ShipAPI ship;
		public MorganaDamageListener(ShipAPI ship) {
			this.ship = ship;
		}
		
		public void advance(float amount) 
		{

		}

		public String modifyDamageDealt(Object param,CombatEntityAPI target, DamageAPI damage, Vector2f point, boolean shieldHit) 
		{
			if(!(target instanceof ShipAPI))
				return null;
						
			ShipAPI s = (ShipAPI) target;
			if(s.isHulk())
				return null;
			if(ship.getSystem().isActive())
				return null;
			String id = "armaa_morgana_buff";			
			float damageVal = (damage.getDamage()/10000f)*100f;
			float armor = DefenseUtils.getArmorValue(s,point);
			multiplier = damageVal;
			float currentBonus = 0f;
			if(Global.getCombatEngine().getCustomData().get("armaa_morgana_bonus_"+ship.getId()) instanceof Float)
			{
				currentBonus = (Float)Global.getCombatEngine().getCustomData().get("armaa_morgana_bonus_"+ship.getId());
			}
			Global.getCombatEngine().getCustomData().put("armaa_morgana_bonus_"+ship.getId(),currentBonus+damageVal);
			float bonus = (float)Global.getCombatEngine().getCustomData().get("armaa_morgana_bonus_"+ship.getId());
			Global.getCombatEngine().addFloatingTextAlways(new Vector2f(target.getLocation().x,target.getLocation().y+target.getCollisionRadius()), "+"+bonus, 20f, Color.white, target, 1f, 1f, 1f, 1f, 1f, .5f);

			return id;
		}
	}
	/*	
	public static class eis_wingClipperDamageListener implements DamageDealtModifier 
	{
		ShipAPI ship;
		
		public eis_wingClipperDamageListener(ShipAPI ship) 
		{
			this.ship = ship;
		}

		public void reportDamageApplied(Object source, CombatEntityAPI target, ApplyDamageResultAPI result) 
		{
				ShipAPI fighter = (ShipAPI) target;	
			if(source instanceof WeaponAPI)
			{
				WeaponAPI wep = (WeaponAPI)source;
				if(wep.getShip() != ship)
				{
					return;
				}
			}
			
			else if(source instanceof DamagingProjectileAPI)
			{
				DamagingProjectileAPI src = (DamagingProjectileAPI)source;
				if(src.getSource() != ship)
					return;					
			}
			
			else if(source instanceof ShipAPI)
			{
				if((ShipAPI)source != ship)
					return;
			}
				
			if(fighter.getHullLevel() <= 0)
			{
				Global.getCombatEngine().addFloatingTextAlways(new Vector2f(fighter.getLocation().x,fighter.getLocation().y+fighter.getCollisionRadius()), "U killed me peacekeeper-chan", 20f, Color.white, fighter, 1f, 1f, 1f, 1f, 1f, .5f);
				float currentBonus = 0f;
				if(Global.getCombatEngine().getCustomData().get("eis_wingClipper_bonus_"+ship.getId()) instanceof Float)
				{
					currentBonus = (Float)Global.getCombatEngine().getCustomData().get("eis_wingClipper_bonus_"+ship.getId());
				}
				Global.getCombatEngine().getCustomData().put("eis_wingClipper_bonus_"+ship.getId(),currentBonus+1f);
				fighter.removeListener(this);
			}

		}
	}
	*/
}

