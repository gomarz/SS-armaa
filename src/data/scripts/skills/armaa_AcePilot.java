package data.scripts.skills;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.AfterShipCreationSkillEffect;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.characters.ShipSkillEffect;
import com.fs.starfarer.api.characters.SkillSpecAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.listeners.AdvanceableListener;
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.impl.campaign.skills.*;
import com.fs.starfarer.api.combat.listeners.*;
import lunalib.lunaSettings.LunaSettings;
import com.fs.starfarer.api.util.IntervalUtil;
import java.awt.Color;
import org.lazywizard.lazylib.combat.DefenseUtils;

public class armaa_AcePilot {
	
	public static float BUFF_FACTOR = 1.2f;
	public static float PEAK_TIME_BONUS = 60;
	public static float DEGRADE_REDUCTION_PERCENT = 25f;
	public static float MAX_CR_BONUS = 15;
	public static int CREW_THRESHOLD = 20;
	public static float BONUS_MAX = 25f;
	private static final float INCREASE_AMT = 1f;
	public static float MAX_REGEN_LEVEL = 0.25f;
	public static float TIME_RATE = 0.10f;
	public static float TOTAL_REGEN_MAX_POINTS = 2000f;
	public static float TOTAL_REGEN_MAX_HULL_FRACTION = 0.5f;

	public static class Level1 implements ShipSkillEffect {
		public void apply(MutableShipStatsAPI stats, HullSize hullSize, String id, float level) 
		{
			if(stats.getEntity() != null)
			{
				ShipAPI ship = (ShipAPI)stats.getEntity();
				if(ship.getHullSpec().getMinCrew() <= CREW_THRESHOLD)
				{
					stats.getFluxDissipation().modifyMult(id, BUFF_FACTOR);
					stats.getFluxCapacity().modifyMult(id, BUFF_FACTOR);
					stats.getMaxSpeed().modifyMult(id, BUFF_FACTOR);
					stats.getAcceleration().modifyMult(id, BUFF_FACTOR);
					stats.getAutofireAimAccuracy().modifyMult(id, BUFF_FACTOR);
					stats.getShieldDamageTakenMult().modifyMult(id,1/ BUFF_FACTOR);
					stats.getPeakCRDuration().modifyFlat(id,stats.getVariant().getHullSpec().getNoCRLossSeconds()*.20f);
				}
			}
			
		}
		
		public void unapply(MutableShipStatsAPI stats, HullSize hullSize, String id) {
			stats.getFluxDissipation().unmodify(id);
			stats.getFluxCapacity().unmodify(id);
			stats.getMaxSpeed().unmodify(id);
			stats.getAcceleration().unmodify(id);
			stats.getAutofireAimAccuracy().unmodify(id);
			stats.getShieldDamageTakenMult().unmodify(id);			
			stats.getPeakCRDuration().unmodify(id);
		}	
		
		public String getEffectDescription(float level) {
			return "+" + (BUFF_FACTOR) + "x performance when assigned to a ship with skeleton crew of 20 or less";
		}
		
		public String getEffectPerLevelDescription() {
			return null;
		}
		
		public ScopeDescription getScopeDescription() {
			return ScopeDescription.PILOTED_SHIP;
		}
	}
	
	public static class Level2 implements ShipSkillEffect {
		public void apply(MutableShipStatsAPI stats, HullSize hullSize, String id, float level) {

		}
		
		public void unapply(MutableShipStatsAPI stats, HullSize hullSize, String id) {

		}	
		
		public String getEffectDescription(float level) {
			return "Shooting down fighters increases manuverability and auto-aim, up to " + (int)BONUS_MAX+"%";
		}
		
		public String getEffectPerLevelDescription() {
			return null;
		}
		
		public ScopeDescription getScopeDescription() {
			return ScopeDescription.PILOTED_SHIP;
		}
	}

	public static class Level3 implements ShipSkillEffect {

		public void apply(MutableShipStatsAPI stats, HullSize hullSize, String id, float level) {
			stats.getPeakCRDuration().modifyMult(id,BUFF_FACTOR);			
		}
		
		public void unapply(MutableShipStatsAPI stats, HullSize hullSize, String id) {
			stats.getPeakCRDuration().unmodify(id);	
		}
		
		public String getEffectDescription(float level) {
			return "The amount gained per fighter is determined by its DP divided by wing size";
		}
		
		public String getEffectPerLevelDescription() {
			return null;
		}

		public ScopeDescription getScopeDescription() {
			return ScopeDescription.PILOTED_SHIP;
		}
	}
	
	public static class Level4 extends BaseSkillEffectDescription implements ShipSkillEffect, AfterShipCreationSkillEffect {
		public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
			ship.addListener(new CombatEnduranceRegen(ship));
		}

		public void unapplyEffectsAfterShipCreation(ShipAPI ship, String id) {
			ship.removeListenerOfClass(CombatEnduranceRegen.class);
		}
		
		
		public void apply(MutableShipStatsAPI stats, HullSize hullSize, String id, float level) {}
		
		public void unapply(MutableShipStatsAPI stats, HullSize hullSize, String id) {}
		
		public void createCustomDescription(MutableCharacterStatsAPI stats, SkillSpecAPI skill, 
								TooltipMakerAPI info, float width) {
			initElite(stats, skill);
			
			info.addPara("When below %s hull, non-missile RoF is increased by %s.", 0f, hc, hc,
					"" + (int)Math.round(MAX_REGEN_LEVEL * 100f) + "%",
					"" + (int)BONUS_MAX + "%"
			);
		}
	}
	
	public static class CombatEnduranceRegen implements DamageTakenModifier, AdvanceableListener {
		protected ShipAPI ship;
		protected boolean inited = false;
		protected boolean runOnce = false;
		protected float limit = 0f;
		protected float repaired = 0f;
		protected String repKey1;
		protected String repKey2;
		protected IntervalUtil interval = new IntervalUtil(2f, 5f);
		protected IntervalUtil interval2 = new IntervalUtil(1.5f,1.5f);			
		public CombatEnduranceRegen(ShipAPI ship) {
			this.ship = ship;
		}
		
		protected void init() {
			if(!ship.hasListenerOfClass(PeacekeeperDeatMod.class))
			{
				ship.addListener(new PeacekeeperDeatMod(ship));
			}	
			inited = true;
		}
				
		public void advance(float amount) {
			if (!inited) {
				init();
			}
			if(!runOnce)
				interval.advance(amount);
			boolean chatterEnabled = true;
			if (Global.getSettings().getModManager().isModEnabled("lunalib"))
			{
				chatterEnabled = LunaSettings.getBoolean("armaa", "armaa_enableDawnVoice");
			}
			if(interval.intervalElapsed() && !runOnce && !ship.isStationModule() && chatterEnabled)
			{
				Global.getSoundPlayer().playUISound("armaa_dawn_intro",1,0.90f);		
				runOnce = true;
			}
			
			String id = ship.getId();
			boolean player = ship == Global.getCombatEngine().getPlayerShip();		

			interval.advance(amount);
			interval2.advance(amount);
			MutableShipStatsAPI stats = ship.getMutableStats();
			if(ship.getHullLevel() < 0.25f)
			{
				stats.getEnergyRoFMult().modifyPercent(id,25f);
				stats.getBallisticRoFMult().modifyPercent(id,25f);					
			}
			else
			{
				stats.getEnergyRoFMult().unmodify(id);
				stats.getBallisticRoFMult().unmodify(id);					
			}			
			if(Global.getCombatEngine().getCustomData().get("eis_wingClipper_bonus_"+ship.getId()) instanceof Float)
			{
				float currentBonus = Math.min(BONUS_MAX,(Float)Global.getCombatEngine().getCustomData().get("eis_wingClipper_bonus_"+ship.getId()));
				stats.getFluxDissipation().modifyPercent(id,currentBonus);
				stats.getAutofireAimAccuracy().modifyPercent(id, currentBonus);
				stats.getMaxTurnRate().modifyPercent(id, currentBonus);
				stats.getAcceleration().modifyPercent(id, currentBonus * 2f);
				stats.getDeceleration().modifyPercent(id, currentBonus);
				stats.getTurnAcceleration().modifyPercent(id, currentBonus * 2f);			
				if(currentBonus > 0f && interval2.intervalElapsed())
					Global.getCombatEngine().getCustomData().put("eis_wingClipper_bonus_"+ship.getId(),currentBonus-.1f);

				
			}
			else
			{
				stats.getFluxDissipation().unmodify(id);
				stats.getAutofireAimAccuracy().unmodify(id);
				stats.getMaxTurnRate().unmodify(id);
				stats.getTurnAcceleration().unmodify(id);			
				stats.getAcceleration().unmodify(id);
				stats.getDeceleration().unmodify(id);			
			}
		}

		public String modifyDamageTaken(Object param,
								   		CombatEntityAPI target, DamageAPI damage,
								   		Vector2f point, boolean shieldHit) {
			return null;
		}
		
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
				s.setHitpoints(0f);
				float fp = s.getWing() != null ? (float)s.getWing().getSpec().getFleetPoints()/(float)s.getWing().getSpec().getNumFighters() : INCREASE_AMT;
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
}
