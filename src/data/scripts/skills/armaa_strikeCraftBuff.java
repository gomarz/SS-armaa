package data.scripts.skills;


import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.skills.*;
import com.fs.starfarer.api.characters.FleetTotalItem;
import com.fs.starfarer.api.characters.FleetTotalSource;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.characters.ShipSkillEffect;
import com.fs.starfarer.api.characters.SkillSpecAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.combat.listeners.AdvanceableListener;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.characters.AfterShipCreationSkillEffect;

public class armaa_strikeCraftBuff {
	
	public static float DISSIPATION_BONUS = 15f;	
	public static float CAPACITY_BONUS = 10f;	

	public static boolean isStrikecraftAndOfficer(MutableShipStatsAPI stats) {
		if (stats.getEntity() instanceof ShipAPI) {
			ShipAPI ship = (ShipAPI) stats.getEntity();
			if (!ship.getVariant().hasHullMod("strikeCraft")) return false;
			return !ship.getCaptain().isDefault();
		} else {
			FleetMemberAPI member = stats.getFleetMember();
			if (member == null) return false;
			if (!member.getVariant().hasHullMod("strikeCraft")) return false;
			return !member.getCaptain().isDefault();
		}
	}

	public static class Level1 implements ShipSkillEffect {
		public void apply(MutableShipStatsAPI stats, HullSize hullSize, String id, float level) {

		}
		
		public void unapply(MutableShipStatsAPI stats, HullSize hullSize, String id) {

		}
		
		public String getEffectPerLevelDescription() {
			return null;
		}		
		
		public String getEffectDescription(float level) {
			return "Increases Flux dissipation by " + (int)(DISSIPATION_BONUS-1f)+"% " + "and capacity by " + (int)(CAPACITY_BONUS-1f)+"%";
		}
				
		public ScopeDescription getScopeDescription() {
			return ScopeDescription.ALL_SHIPS;
		}
	}		
	
	public static class Level2 implements ShipSkillEffect {
		public void apply(MutableShipStatsAPI stats, HullSize hullSize, String id, float level) {

		}
		
		public void unapply(MutableShipStatsAPI stats, HullSize hullSize, String id) {

		}	
		
		public String getEffectDescription(float level) {
			return "reduces damage taken to shields by " + (int)(CAPACITY_BONUS-1f)+"%";
		}
		
		public String getEffectPerLevelDescription() {
			return null;
		}
		
		public ScopeDescription getScopeDescription() {
			return ScopeDescription.ALL_SHIPS;
		}
	}	
	public static class Level3 extends BaseSkillEffectDescription implements ShipSkillEffect, FleetTotalSource, AfterShipCreationSkillEffect {
		
		public FleetTotalItem getFleetTotalItem() {
			return getPhaseOPTotal();
		}
		public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
			if(isStrikecraftAndOfficer(ship.getMutableStats()))
				ship.addListener(new DeadmansDash(ship));
		}

		public void unapplyEffectsAfterShipCreation(ShipAPI ship, String id) {
			ship.removeListenerOfClass(DeadmansDash.class);
		}		
		public void apply(MutableShipStatsAPI stats, HullSize hullSize, String id, float level) 
		{
			if (isStrikecraftAndOfficer(stats) && !isCivilian(stats)) 
			{		
				stats.getFluxDissipation().modifyPercent(id, DISSIPATION_BONUS);	
				stats.getFluxCapacity().modifyPercent(id, CAPACITY_BONUS);									
				stats.getShieldDamageTakenMult().modifyMult(id, 1f - 0.1f);					
			}
		}
		
		public void unapply(MutableShipStatsAPI stats, HullSize hullSize, String id) {
			stats.getFluxDissipation().unmodifyPercent(id);	
			stats.getFluxCapacity().unmodifyPercent(id);			
			stats.getShieldDamageTakenMult().unmodifyMult(id);
		}
		
		public String getEffectDescription(float level) {
			return "When flux is over 80%, speed increases by 30%, rapidly degrading over 3s. This effect can only trigger once ever 8s.";
		}
		
		public void createCustomDescription(MutableCharacterStatsAPI stats, SkillSpecAPI skill, 
											TooltipMakerAPI info, float width) {

		}
		
		public ScopeDescription getScopeDescription() {
			return ScopeDescription.ALL_SHIPS;
		}
	}

	public static class DeadmansDash implements AdvanceableListener {
		protected ShipAPI ship;
		protected IntervalUtil upInterval = new IntervalUtil(3f, 3f);
		protected IntervalUtil downInterval = new IntervalUtil(8f, 8f);		
		protected boolean triggered = false;
		protected boolean cooldown = false;
		public DeadmansDash(ShipAPI ship) {
			this.ship = ship;
		}
				
		public void advance(float amount) {
			
			String id = ship.getId();
			boolean player = ship == Global.getCombatEngine().getPlayerShip();		
			float ratio = upInterval.getElapsed()/upInterval.getMaxInterval();
			MutableShipStatsAPI stats = ship.getMutableStats();
			if(ship.getFluxLevel() >= 0.80f && !triggered && !cooldown)
			{
				triggered = true;				
			}
			if(ratio >= 1 && triggered && !cooldown)
			{
				cooldown = true;
				triggered = false;
				stats.getMaxSpeed().unmodify(id);
				stats.getMaxTurnRate().unmodify(id);					
			}
			if(triggered)
			{
				stats.getMaxSpeed().modifyPercent(id, 30f * (1f - ratio));
				stats.getMaxTurnRate().modifyPercent(id, 30f * (1f - ratio));				
				upInterval.advance(amount);
			}
			if(cooldown)
			{
				downInterval.advance(amount);
				if(downInterval.intervalElapsed())
				{
					cooldown = false;
					downInterval.setInterval(8f,8f);
					upInterval.setInterval(3f,3f);
				}
			}
			if(player)
			{
				String deadString = triggered ? "ACTIVE" : "INACTIVE";
				if(triggered)
					Global.getCombatEngine().maintainStatusForPlayerShip("armaa_dd", "graphics/icons/tactical/engine_boost2.png","Deadman's Dash -" + deadString, String.valueOf(25f * (1f - ratio))+"%" ,triggered);		
			}
		}
	}
}	
