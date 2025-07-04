package data.hullmods;

import java.awt.Color;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lazywizard.lazylib.MathUtils;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.ShipEngineControllerAPI.ShipEngineAPI;
import com.fs.starfarer.api.combat.listeners.AdvanceableListener;
import com.fs.starfarer.api.combat.listeners.HullDamageAboutToBeTakenListener;
import com.fs.starfarer.api.impl.campaign.ids.HullMods;
import com.fs.starfarer.api.impl.campaign.ids.Strings;
import com.fs.starfarer.api.impl.campaign.skills.NeuralLinkScript;
import com.fs.starfarer.api.util.FaderUtil;
import com.fs.starfarer.api.util.Misc;

import data.scripts.ai.armaa_combat_docking_AI;

public class armaa_emergencyRecallDevice extends BaseHullMod {

	public static float PHASE_DISSIPATION_MULT = 2f;
	public static float ACTIVATION_COST_MULT = 0f;
	
	public static float CR_LOSS_MULT_FOR_EMERGENCY_DIVE = 1f;
	
	public static class EmergencyRecallScript implements AdvanceableListener, HullDamageAboutToBeTakenListener {
		public ShipAPI ship;
		public boolean recalled = false;
		public boolean invincibleApplied = false;
		public boolean emergencyRecall = false;
		public float diveProgress = 0f;
		public FaderUtil diveFader = new FaderUtil(1f, 1f);
		public EmergencyRecallScript(ShipAPI ship) {
			this.ship = ship;
		}
		
		public boolean notifyAboutToTakeHullDamage(Object param, ShipAPI ship, Vector2f point, float damageAmount) {
			//if (ship.getCurrentCR() <= 0) return false;
			
			if (!emergencyRecall) {
				String key = "armaa_"+ship.getId()+"_emergencyRecall_canRecall";
				boolean canRecall = !Global.getCombatEngine().getCustomData().containsKey(key);
				float depCost = 0f;
				if (ship.getFleetMember() != null) {
					depCost = ship.getFleetMember().getDeployCost();
				}
				float crLoss = CR_LOSS_MULT_FOR_EMERGENCY_DIVE * depCost;
				canRecall &= ship.getCurrentCR() >= crLoss;
				
				float hull = ship.getHitpoints();
				if (damageAmount >= hull && canRecall) {
					ship.setHitpoints(1f);
					
					//ship.setCurrentCR(Math.max(0f, ship.getCurrentCR() - crLoss));
					if (ship.getFleetMember() != null) { // fleet member is fake during simulation, so this is fine
						ship.getFleetMember().getRepairTracker().applyCREvent(-crLoss, "Emergency phase dive");
						//ship.getFleetMember().getRepairTracker().setCR(ship.getFleetMember().getRepairTracker().getBaseCR() + crLoss);
					}
					emergencyRecall = true;
					Global.getCombatEngine().getCustomData().put(key, true);
					
					if (!ship.isPhased()) {
						Global.getSoundPlayer().playSound("system_phase_cloak_activate", 1f, 1f, ship.getLocation(), ship.getVelocity());
					}
				}
			}
			
			if (emergencyRecall) {
				return true;
			}
			
			return false;
		}

		public void advance(float amount) {
			String id = "armaa_emergency_recall_modifier";
			if (emergencyRecall && !recalled) {
				Color c = ship.getFluxTracker().getOverloadColor();
				c = Misc.setAlpha(c, 255);
				c = Misc.interpolateColor(c, Color.white, 0.5f);
				
				if (diveProgress == 0f) {
					if (ship.getFluxTracker().showFloaty()) {
						if(!invincibleApplied)
						{
							ship.getMutableStats().getHullDamageTakenMult().modifyMult("erd", 0f);
							invincibleApplied = true;
						}
						float timeMult = ship.getMutableStats().getTimeMult().getModifiedValue();
						Global.getCombatEngine().addFloatingTextAlways(ship.getLocation(),
								"Emergency Recall!",
								NeuralLinkScript.getFloatySize(ship), c, ship, 16f * timeMult, 3.2f/timeMult, 1f/timeMult, 0f, 0f,
								1f);
					}
				}
				
				diveFader.advance(amount);
				
				ship.blockCommandForOneFrame(ShipCommand.USE_SYSTEM);
				diveProgress += amount * 5f;
				float curr = ship.getExtraAlphaMult();
				//ship.getPhaseCloak().forceState(SystemState.IN, Math.min(1f, Math.max(curr, diveProgress)));
				
				if (diveProgress >= 1f) {
					if (diveFader.isIdle()) {
						Global.getSoundPlayer().playSound("phase_anchor_vanish", 1f, 1f, ship.getLocation(), ship.getVelocity());
					}
					diveFader.fadeOut();
					diveFader.advance(amount);
					float b = diveFader.getBrightness();
					ship.setExtraAlphaMult2(b);
					
					float r = ship.getCollisionRadius() * 5f;
					ship.setJitter(this, c, b, 20, r * (1f - b));
					ShipAPI carrier = getNearestCarrier(ship);
					if (diveFader.isFadedOut()) 
					{
						if(carrier != null)
						{
							armaa_combat_docking_AI DockingAI = new armaa_combat_docking_AI(ship);
							if(!Global.getCombatEngine().getCustomData().containsKey("armaa_strikecraftisLanding"+ship.getId()))
							{					
								ship.setShipAI(DockingAI);
								DockingAI.init();
								Global.getCombatEngine().getCustomData().put("armaa_strikecraftisLanding"+ship.getId(),true);			
							}											
							for(ShipEngineAPI engine: ship.getEngineController().getShipEngines())
								engine.repair();
							ship.getLocation().set(carrier.getLocation().x, carrier.getLocation().y);
						}
						else
						{
							ship.setRetreating(true, false);
							ship.getLocation().set(0, -1000000f);
						}
						recalled = true;
						ship.setExtraAlphaMult2(1f);
						ship.getMutableStats().getHullDamageTakenMult().unmodifyMult("erd");						
						ship.getMutableStats().getHullDamageTakenMult().unmodify("erd");
						ship.setHitpoints(ship.getMaxHitpoints()*0.5f);
						ship.removeListener(this);
					}
				}
			}
		}
	}
	
	@Override
	public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
		ship.addListener(new EmergencyRecallScript(ship));
	}
	
	public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {

	}
	
	private static ShipAPI getNearestCarrier(ShipAPI ship)
	{
		ShipAPI potCarrier = null;
		float distance = 99999f;
		for (ShipAPI carrier : CombatUtils.getShipsWithinRange(ship.getLocation(), 10000.0F)) 
		{
			if(carrier == ship)
				continue;
			if(carrier.getVariant().hasHullMod("strikeCraft"))
				continue;
			if(carrier.getHullSpec().hasTag("no_wingcom_docking"))
				continue;
			if(carrier.getOwner() != ship.getOwner() || !carrier.getHullSpec().hasTag("strikecraft_with_bays") && (carrier.isFighter() || carrier.isFrigate()) && !carrier.isStationModule() || carrier == ship)
				continue;
			if(carrier.isHulk())
				continue;

			if(carrier.getNumFighterBays() > 0)
			{
				if(MathUtils.getDistance(ship, carrier) < distance)
				{
					distance = MathUtils.getDistance(ship, carrier);
					potCarrier = carrier; 
				}
			}				
		}
		
		return potCarrier;
	}		
	
	public String getDescriptionParam(int index, HullSize hullSize) {
		if (index == 0) return "zero";
		if (index == 1) return "" + (int)PHASE_DISSIPATION_MULT + Strings.X;
		if (index == 2) return "" + (int)CR_LOSS_MULT_FOR_EMERGENCY_DIVE + Strings.X;

		return null;
	}
	
	@Override
	public boolean isApplicableToShip(ShipAPI ship) {
		if (ship.getVariant().hasHullMod(HullMods.PHASE_ANCHOR)) return false;
		
		return true;
	}

	@Override
	public String getUnapplicableReason(ShipAPI ship) {
		if (ship.getVariant().hasHullMod(HullMods.PHASE_ANCHOR)) {
			return "Incompatible with Adaptive Phase Coils";
		}
		if (!ship.getVariant().hasHullMod("strikeCraft")) {
			return "Only compatible with strikecraft";
		}
		return super.getUnapplicableReason(ship);
	}
	
}

