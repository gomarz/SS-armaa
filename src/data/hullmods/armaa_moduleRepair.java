package data.hullmods;

import java.awt.Color;

import org.lwjgl.util.vector.Vector2f;
import data.scripts.util.armaa_utils;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.ShipSystemAPI.SystemState;
import com.fs.starfarer.api.combat.listeners.AdvanceableListener;
import com.fs.starfarer.api.combat.listeners.HullDamageAboutToBeTakenListener;
import com.fs.starfarer.api.impl.campaign.ids.HullMods;
import com.fs.starfarer.api.impl.campaign.ids.Strings;
import com.fs.starfarer.api.impl.campaign.skills.NeuralLinkScript;
import com.fs.starfarer.api.util.FaderUtil;
import com.fs.starfarer.api.util.Misc;

public class armaa_moduleRepair extends BaseHullMod {

	public static float PHASE_DISSIPATION_MULT = 2f;
	public static float ACTIVATION_COST_MULT = 0f;
	
	public static float CR_LOSS_MULT_FOR_EMERGENCY_DIVE = 1f;
	
	public static class moduleRepairScript implements AdvanceableListener, HullDamageAboutToBeTakenListener {
		public ShipAPI ship;
		public boolean isDestroyed = false;
		public float diveProgress = 0f;
		public FaderUtil diveFader = new FaderUtil(1f, 1f);
		public moduleRepairScript(ShipAPI ship) {
			this.ship = ship;
		}
		public boolean notifyAboutToTakeHullDamage(Object param, ShipAPI ship, Vector2f point, float damageAmount) {
				String key = "moduleRepair_isDestroyed"+"_"+ship.getId();
				float hull = ship.getHitpoints();
				if (damageAmount >= hull) {
					ship.setHitpoints(1f);
					
					if (!ship.isPhased()) {
						Global.getSoundPlayer().playSound("explosion_from_damage", 1f, 1f, ship.getLocation(), ship.getVelocity());
					}
					isDestroyed = true;
					//if (ship.getFluxTracker().showFloaty()) {
						float timeMult = ship.getMutableStats().getTimeMult().getModifiedValue();
						Global.getCombatEngine().addFloatingTextAlways(ship.getLocation(),
								ship.getHullSpec().getHullName()+" Destroyed!",
								NeuralLinkScript.getFloatySize(ship)+10f, Color.yellow, ship, 16f * timeMult, 3.2f/timeMult, 1f/timeMult, 0f, 1.5f,
								1f);
					//}
					Global.getCombatEngine().spawnExplosion(ship.getLocation(),ship.getVelocity(),Color.red,100f,1f);
					Global.getCombatEngine().spawnExplosion(ship.getLocation(),ship.getVelocity(),Color.yellow,75f,1f);
					Global.getCombatEngine().getCustomData().put(key,true);
				}
			
			
			if(isDestroyed) {
				return true;
			}
			
			return false;
		}

		public void advance(float amount) {
			String key = "moduleRepair_isDestroyed"+"_"+ship.getId();
			if (isDestroyed) {
				ship.getSpriteAPI().setColor(new Color(0,0,0,0));
				ship.setCollisionClass(CollisionClass.NONE);
				ship.getMutableStats().getHullDamageTakenMult().modifyMult("invincible",0f);
				ship.getMutableStats().getArmorDamageTakenMult().modifyMult("invincible",0f);
				ship.blockCommandForOneFrame(ShipCommand.USE_SYSTEM); 
				ship.getMutableStats().getAcceleration().modifyMult(ship.getId()+"_shield",1.25f);
				ship.getMutableStats().getMaxSpeed().modifyMult(ship.getId()+"_shield",1.25f);
				ship.getMutableStats().getMaxTurnRate().modifyMult(ship.getId()+"_shield",1.25f);		
				ship.getMutableStats().getTurnAcceleration().modifyMult(ship.getId()+"_shield",1.25f);						

			}
			
			if(ship.getHullLevel() > 0.5 && isDestroyed)
			{
				isDestroyed = false;
				ship.setCollisionClass(CollisionClass.FIGHTER);
				ship.getSpriteAPI().setColor(new Color(255,255,255,255));
				ship.getMutableStats().getHullDamageTakenMult().unmodify("invincible");
				ship.getMutableStats().getArmorDamageTakenMult().unmodify("invincible");
				Global.getCombatEngine().getCustomData().remove(key);				
				ship.getMutableStats().getAcceleration().unmodify(ship.getId()+"_shield");
				ship.getMutableStats().getMaxSpeed().unmodify(ship.getId()+"_shield");
				ship.getMutableStats().getMaxTurnRate().unmodify(ship.getId()+"_shield");		
				ship.getMutableStats().getTurnAcceleration().unmodify(ship.getId()+"_shield");						
			}

		}

	}
	
	@Override
	public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
		ship.addListener(new moduleRepairScript(ship));
	}
	
	public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
		stats.getPhaseCloakActivationCostBonus().modifyMult(id, 0f);
	}
	
	public String getDescriptionParam(int index, HullSize hullSize) {
		if (index == 0) return "zero";
		if (index == 1) return "" + (int)PHASE_DISSIPATION_MULT + Strings.X;
		if (index == 2) return "" + (int)CR_LOSS_MULT_FOR_EMERGENCY_DIVE + Strings.X;

		return null;
	}	
}

