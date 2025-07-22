package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import java.awt.Color;

import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.util.IntervalUtil;

import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.combat.listeners.*;
import org.magiclib.util.MagicUI;

public class armaa_morganaBonus extends BaseHullMod
{
	private final float BONUS_MAX = 100f;
	private static float DAMAGE_CAP = 15000f;
	private IntervalUtil interval = new IntervalUtil(1.5f,1.5f);
	boolean overloaded = false;
	public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) 
	{
	}
	
	@Override
	public void applyEffectsAfterShipCreation(ShipAPI ship, java.lang.String id)
	{
		if(!ship.hasListenerOfClass(MorganaDamageListener.class) && ship.isFighter())
		{
			ship.addListener(new MorganaDamageListener(ship));
		}
		MutableShipStatsAPI stats = ship.getMutableStats();
		if(stats.getVariant().getModuleVariant("MORGANASUP") != null)
		{
			if(stats.getFleetMember() != null && stats.getVariant() != null && stats.getVariant().getWing(0) != null && stats.getFleetMember().getFleetData() != null)
			{
				ShipVariantAPI sourceVar = stats.getVariant().getModuleVariant("MORGANASUP").clone();
				ShipVariantAPI variant = null;				
				for(FleetMemberAPI member: stats.getFleetMember().getFleetData().getMembersListWithFightersCopy())
				{			
					if(!member.isFighterWing())
						continue;
					if(member.getHullId().contains("armaa_morganab"))
					{
						//System.out.println(member.getHullId());
						variant = member.getVariant();
						System.out.println((sourceVar==null) + " and " + (variant==null));
						if(sourceVar != null && variant != null)
						{
							for(String slot : member.getVariant().getFittedWeaponSlots())
							{
								if(sourceVar.getWeaponId(slot) == null)
									continue;
								variant.clearSlot(slot);
								variant.addWeapon(slot,sourceVar.getWeaponId(slot));
								
							}							
							//member.setVariant(sourceVar,true,true);
						}
					}
				}			
			}
		}
	}
	
	public Color shiftColor(Color start, Color end, float ratio)
	{
		Color intermediateColor = Color.WHITE;		
		if(ratio >= 1)
			return end;
		
		int red = (int) (start.getRed() * (1 - ratio) + end.getRed() * ratio);
		int green = (int) (start.getGreen() * (1 - ratio) + end.getGreen() * ratio);
		int blue = (int) (start.getBlue() * (1 - ratio) + end.getBlue() * ratio);
		int alpha = (int) (start.getAlpha() * (1 - ratio) + end.getAlpha() * ratio);
		intermediateColor = new Color(red, green, blue, alpha);	
		
		return intermediateColor;
		
		
	}
	
    @Override
    public void advanceInCombat(ShipAPI ship, float amount)
    {
		
		String id = ship.getId();
		boolean player = ship == Global.getCombatEngine().getPlayerShip();		

		interval.advance(amount);
		MutableShipStatsAPI stats = ship.getMutableStats();
		
		if(ship.getOwner() >= 0 && ship.getChildModulesCopy() != null && ship.getChildModulesCopy().size() > 0)
		{
			Global.getCombatEngine().getFleetManager(ship.getOwner()).getTaskManager(true).orderRetreat(Global.getCombatEngine().getFleetManager(ship.getOwner()).getDeployedFleetMember(ship.getChildModulesCopy().get(0)),false,false);	
			//ship.getChildModulesCopy().get(0).setParentStation(null);
			ship.getChildModulesCopy().get(0).getLocation().set(-10000000,-100000000);
		}
		if(Global.getCombatEngine().getCustomData().containsKey("armaa_drnus_"+ship.getId()))
		{

		}
		else
		{
			Global.getCombatEngine().getCustomData().put("armaa_drnus_"+ship.getId(),DamageType.KINETIC);
		}
		// track current shield type
		// track absorption threshold?
		if(Global.getCombatEngine().getCustomData().get("armaa_morgana_bonus_"+ship.getId()) instanceof Float)
		{
			float guardBar = (Float)Global.getCombatEngine().getCustomData().get("armaa_morgana_bonus_"+ship.getId());
			double strength = guardBar;
			float currentBonus = Math.max(0f,(float)strength);
			if(!ship.getSystem().isActive() && currentBonus > 0f && !ship.getSystem().isCoolingDown())
			{
				if(!overloaded)
				{
					if(ship.getFluxTracker().isVenting())
						currentBonus-= amount*ship.getFluxTracker().getTimeToVent();
					else
						currentBonus-= amount*(1f+3.5f*(ship.getCurrFlux()/ship.getMaxFlux()));
				}
				else
				{
					currentBonus-= amount*3;
					ship.blockCommandForOneFrame(ShipCommand.USE_SYSTEM);
				}
			}
			Color color = shiftColor(Misc.getPositiveHighlightColor(),Color.red,(currentBonus/100f));
			String text = "GUARD";
			if(overloaded)
			{
				text = "WARNING";
				color = Color.red;
			}
			MagicUI.drawHUDStatusBar(
			ship,
			1f-(currentBonus/100f),
			color,
			color,
			0.50f,
			"GUARD",
			null,
			true);
			 float dmg = 0;
			 if(Global.getCombatEngine().getCustomData().get("armaa_morgana_absorbed_"+ship.getId()) != null)
			 dmg = (float)Global.getCombatEngine().getCustomData().get("armaa_morgana_absorbed_"+ship.getId());
			if(currentBonus <= 100f && !overloaded)
				Global.getCombatEngine().getCustomData().put("armaa_morgana_bonus_"+ship.getId(),currentBonus);
			else
			{
				if(!overloaded)
				{
					overloaded = true;
					ship.getFluxTracker().forceOverload(10f);
				}
				Global.getCombatEngine().getCustomData().put("armaa_morgana_bonus_"+ship.getId(),currentBonus);				
			}
			if(overloaded && currentBonus < 0f)
				overloaded = false;
		}
		else
		{
			stats.getEnergyWeaponDamageMult().unmodify(id); 
			stats.getBallisticWeaponDamageMult().unmodify(id);
			stats.getTimeMult().unmodify(id);
		}
    }
	//This listener ensures we die properly
	public static class MorganaDamageListener implements DamageTakenModifier, AdvanceableListener 
	{
		float multiplier = 1f;
		protected ShipAPI ship;
		public MorganaDamageListener(ShipAPI ship) {
			this.ship = ship;
		}
		
		public void advance(float amount) 
		{

		}

		public String modifyDamageTaken(Object param,CombatEntityAPI target, DamageAPI damage, Vector2f point, boolean shieldHit) 
		{
			if(!(target instanceof ShipAPI))
				return null;
						
			if(!shieldHit)
				return null;
			ShipAPI s = (ShipAPI) target;
			if(s.isHulk())
				return null;
			if(!ship.getSystem().isActive())
				return null;
			String id = "armaa_morgana_buff";
			float adjDamage = damage.getDamage();
			if(damage.getType().equals(DamageType.FRAGMENTATION))
			{
				adjDamage*= 0.25f;
			}
			else if(damage.getType().equals(DamageType.KINETIC))
			{
				adjDamage*= 2f;
			}
			else if(damage.getType().equals(DamageType.HIGH_EXPLOSIVE))
			{
				adjDamage*= 0.5f;
			}
			float damageVal = (adjDamage/ship.getMaxFlux())*100f;
			float absorbedVal = adjDamage;
			if(damage.isDps())
			{
				damageVal*=Global.getCombatEngine().getElapsedInLastFrame();
				absorbedVal*=Global.getCombatEngine().getElapsedInLastFrame();
			}
			float currentBonus = 0f;
			float currentAbsorbed = 0f;
			if(Global.getCombatEngine().getCustomData().get("armaa_morgana_bonus_"+ship.getId()) instanceof Float)
			{
				currentBonus = (Float)Global.getCombatEngine().getCustomData().get("armaa_morgana_bonus_"+ship.getId());
				currentAbsorbed = (Float)Global.getCombatEngine().getCustomData().get("armaa_morgana_absorbed_"+ship.getId());
			}
			Global.getCombatEngine().getCustomData().put("armaa_morgana_bonus_"+ship.getId(),currentBonus+damageVal);
			Global.getCombatEngine().getCustomData().put("armaa_morgana_absorbed_"+ship.getId(),currentAbsorbed+absorbedVal);			
			float bonus = (float)Global.getCombatEngine().getCustomData().get("armaa_morgana_bonus_"+ship.getId());
			Vector2f loc = new Vector2f(ship.getLocation().x+ship.getCollisionRadius(),ship.getLocation().y+ship.getCollisionRadius()-80f);			
			Global.getCombatEngine().addFloatingDamageText(loc, absorbedVal, Color.cyan, ship, damage.getStats() != null ? damage.getStats().getEntity():null); 

			return id;
		}
	}
}

