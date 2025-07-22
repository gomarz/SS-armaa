package data.hullmods;

import java.util.Iterator;

import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import com.fs.starfarer.api.util.Misc;
import java.awt.Color;

import org.lazywizard.lazylib.CollisionUtils;
import org.lazywizard.lazylib.combat.CombatUtils;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import org.magiclib.util.MagicIncompatibleHullmods;
import data.scripts.util.armaa_utils;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;

import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.util.vector.Vector2f;
public class armaa_SilverSwordHM extends BaseHullMod {

	private static final float DAMAGE_MULT = 1.25f;
	private static final float DAMAGE_BONUS = 100f;
	private static final float MIN_PROJECTILE_DMG = 40f;
	private static final float GRAZE_DAMAGE_BONUS = 20f;
	private static final float GRAZE_TIME_BONUS = 3f;
	private static final float GRAZE_TIME_BONUS_MIN = 1.3f;
	private static final float MAX_TIME_MULT = 1.20f;
	private static final float MIN_TIME_MULT = 0.1f;
	private static final float AUTOAIM_BONUS = 1f / 3f;
	private IntervalUtil buffTimer = new IntervalUtil(2f,2f);
	private IntervalUtil coolDownTimer = new IntervalUtil(5f,5f);
	private boolean dodgeBonus = false;
	private boolean cooldown = false;
	private boolean runOnce = false;
	private float multBonus = 1f;
	
	private static final Set<String> BLOCKED_HULLMODS = new HashSet<>();
	static
	{
		// These hullmods will automatically be removed
		// This prevents unexplained hullmod blocking
		BLOCKED_HULLMODS.add("pointdefenseai");
		BLOCKED_HULLMODS.add("safetyoverrides");
		BLOCKED_HULLMODS.add("armaa_silverSwordHM");
	}
		
	@Override
	public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) 
	{
		float HEIGHT = 64f;
        float PAD = 10f;
        Color YELLOW = new Color(241, 199, 0);
		String DNIIcon = "graphics/armaa/icons/hullsys/armaa_pilot_icon.png";
		String AIcon = "graphics/armaa/icons/hullsys/armaa_ai_core.png";
		String SOIcon = "graphics/icons/hullsys/displacer.png";
		String DrugsIcon = "graphics/armaa/icons/hullsys/armaa_drugs_icon.png";		
        String DNITitle = "Direct Neural Interface";
		String DNIText1 = "- Time dilation increased by %s.";
		String DNIText2 = "- Relative time slows by %s in the presence of dangerous entities within %s SU.";
		String DNIText3 = "- Damage taken to hull and shields increased by %s.";
		
		String PDTitle = "Integrated PDAI";
		String PDText1 = "- Damage against missiles increased by %s.";
		String PDText2 = "- Damage against fighters increased by %s.";
		String PDText3 =  "- All weapons %s.";
		String PDText4 =  "- Autofire accuracy increased by %s.";
		
		String CSTitle = "'Sy-Ko' Combat Stimulant";
		String CSText1 = "- Narrowly evading projectiles with %s administers a high-grade synthetic stimulant that includes a psychotropic aggression enhancer.";
		String CSText2 = "- Time Dilation is increased by %s.";
		String CSText3 = "- Energy and Ballistic weapon damage is increased by %s.";
		String CSText4 = "- Evasion bonus degrades over %s seconds, after which system cannot trigger again for %s seconds.";
		String CSText5 = "- Consumes %s unit of %s per engagement.";
		
		float pad = 2f;
		Color[] arr ={Misc.getPositiveHighlightColor(),Misc.getHighlightColor()};		
		tooltip.addSectionHeading("Details", Alignment.MID, 5);  
        TooltipMakerAPI neuralInterface = tooltip.beginImageWithText(DNIIcon, HEIGHT);
        neuralInterface.addPara(DNITitle, pad, YELLOW, DNITitle);
        neuralInterface.addPara(DNIText1, pad, Misc.getPositiveHighlightColor(), (int)(Math.round((MAX_TIME_MULT-1f)*100f))+"%");
        neuralInterface.addPara(DNIText2, pad, arr, (int)(Math.round((MAX_TIME_MULT-1f)*100f))+"%","1000");
        neuralInterface.addPara(DNIText3, pad, Misc.getNegativeHighlightColor(), (int)(Math.round((DAMAGE_MULT-1f)*100f))+"%");
		UIPanelAPI temp =tooltip.addImageWithText(PAD);
        ///*
        TooltipMakerAPI combatDrugs = tooltip.beginImageWithText(DrugsIcon, HEIGHT);
		Color[] disabletext ={Global.getSettings().getColor("textGrayColor"),Misc.getNegativeHighlightColor()};
        combatDrugs.addPara(CSTitle, pad, YELLOW, CSTitle);
		boolean inCampaign = Global.getSector().getPlayerFleet() != null;		
		if(inCampaign && Global.getSector().getPlayerFleet().getCargo().getCommodityQuantity(Commodities.DRUGS) > 0)
		{
			combatDrugs.addPara(CSText5, pad, Misc.getNegativeHighlightColor(), "one","Recreational Drugs");
			combatDrugs.addPara(CSText1, pad, Misc.getHighlightColor(),"shields lowered");
			combatDrugs.addPara(CSText2, pad, Misc.getPositiveHighlightColor(), (int)(Math.round((GRAZE_TIME_BONUS-1f)*100f))+"%");
			combatDrugs.addPara(CSText3, pad, Misc.getPositiveHighlightColor(), (int)Math.round(GRAZE_DAMAGE_BONUS)+"%");
			combatDrugs.addPara(CSText4, pad, Misc.getNegativeHighlightColor(), (int)buffTimer.getMaxInterval()+"",(int)coolDownTimer.getMaxInterval()+"");
		}
		
		else
			combatDrugs.addPara("%s - components of the stimulant require %s.", pad, disabletext,"DISABLED", "Recreational Drugs");	
		
        temp = tooltip.addImageWithText(PAD);		
        if (hullSize == ShipAPI.HullSize.FRIGATE) 
		{
			TooltipMakerAPI pd = tooltip.beginImageWithText(AIcon, HEIGHT);
			pd.addPara(PDTitle, pad, YELLOW, PDTitle);
			pd.addPara(PDText1, pad,Misc.getPositiveHighlightColor(),(int)Math.round(DAMAGE_BONUS) + "%");
			pd.addPara(PDText2, pad, Misc.getPositiveHighlightColor(),(int)Math.round(DAMAGE_BONUS) + "%");
			pd.addPara(PDText3, pad, Misc.getPositiveHighlightColor(),"ignore flares");
			pd.addPara(PDText4, pad, Misc.getPositiveHighlightColor(),(int)(Math.round((AUTOAIM_BONUS)*100f))+"%");
			temp = tooltip.addImageWithText(PAD);
        }
        

		tooltip.addPara("\"Is this the subject? ...Oh, so they took the usual route here. Must have dashed their dreams. But, they will be reborn in this experiment. That is, if they live. Mmh. Let's get started.\" - Recovered voice recorder",Global.getSettings().getColor("textGrayColor"),PAD);
		tooltip.addSectionHeading("Incompatibilities", Alignment.MID, 10); 		
		String str = "";
		int size = BLOCKED_HULLMODS.size();
		int counter = 0;
		Color[] arr2 ={Misc.getHighlightColor(),Misc.getNegativeHighlightColor()};
        for (HullModSpecAPI spec : Global.getSettings().getAllHullModSpecs())
        {
			for (String tmp : BLOCKED_HULLMODS)
			{
				if(spec.getId().equals(tmp))
				{				
					str+=spec.getDisplayName();		
					if(counter!= size-1)
					{
						str+=", ";
						counter++;
					}
				}
			}
        }
		
		str = str.substring(0, str.length() - 1);
		tooltip.addPara("%s " + "Incompatible with %s.", PAD, arr2, "-", "" + str);
    }

	@Override
	public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) 
	{
		//h--h-hayai!!
		stats.getTimeMult().modifyMult(id, MAX_TIME_MULT);
		
		//extra damage for some reason
		stats.getHullDamageTakenMult().modifyMult(id, DAMAGE_MULT);	
		stats.getShieldDamageTakenMult().modifyMult(id, DAMAGE_MULT);	
		//weapons are PD_ALSO now, don't know if this does anything
		stats.getDynamic().getMod(Stats.PD_BEST_TARGET_LEADING).modifyFlat(id, 1f);
		
		//fuck missiles
		stats.getDamageToMissiles().modifyPercent(id, DAMAGE_BONUS);
		//fuck fighters
		stats.getDamageToFighters().modifyPercent(id, DAMAGE_BONUS);
		
		//aimbot
		stats.getAutofireAimAccuracy().modifyFlat(id, AUTOAIM_BONUS);
	}

	public void applyEffectsAfterShipCreation(ShipAPI ship, String id) 
	{
		List weapons = ship.getAllWeapons();
		Iterator iter = weapons.iterator();
		while (iter.hasNext()) 
		{
			WeaponAPI weapon = (WeaponAPI)iter.next();

			weapon.getSpec().addTag("IGNORES_FLARES");
		}

		for (String tmp : BLOCKED_HULLMODS)
        {
			if(ship.getVariant().getHullMods().contains(tmp))
			{
   				MagicIncompatibleHullmods.removeHullmodWithWarning(ship.getVariant(), tmp, "armaa_SilverSwordHM");
			}
        }
	}
	
  @Override
    public void advanceInCombat(ShipAPI ship, float amount) 
	{

        //don't run while paused because duh
        if (Global.getCombatEngine().isPaused()) {
            return;
        }

        boolean player = ship == Global.getCombatEngine().getPlayerShip();
		boolean hasCopium = false;
		boolean inCampaign = Global.getCombatEngine().isInCampaign();
		if(!(Global.getCombatEngine().getCustomData().get("armaa_hasCopium_"+ship.getId()) instanceof Boolean))
			hasCopium = ship.getOwner() == 0 ? !inCampaign || Global.getSector().getPlayerFleet().getCargo().getCommodityQuantity(Commodities.DRUGS) > 0 : false;
		

        if ( !ship.isAlive() || ship.isPiece())
		{
			Global.getCombatEngine().getTimeMult().unmodify(ship.getId());				
            return;
		}
		if(player && !runOnce && !ship.isStationModule() && ship.getOwner() == 0)
		{
			Global.getSoundPlayer().playUISound("armaa_valkazard_intro",1,0.90f);		
			runOnce = true;
		}		
		if(!dodgeBonus&&!cooldown && hasCopium && !CombatUtils.getEntitiesWithinRange(ship.getLocation(), 1000f).isEmpty() && !ship.isFinishedLanding())
		{
			
			List<DamagingProjectileAPI> possibleTargets = new ArrayList<>(100);
			possibleTargets.addAll(CombatUtils.getMissilesWithinRange(ship.getLocation(), ship.getCollisionRadius()));
			possibleTargets.addAll(CombatUtils.getProjectilesWithinRange(ship.getLocation(), ship.getCollisionRadius()));
			
			for (DamagingProjectileAPI proj : possibleTargets) 
			{
				float dmg = 0f;
				if (proj.getOwner() == ship.getOwner() || proj.isFading() || proj.getCollisionClass() == CollisionClass.NONE) 
					continue;
				
				if((proj instanceof MissileAPI))
				{
					MissileAPI m = (MissileAPI)proj;
					if(m.isFlare())
						continue;
				}
				
				if(!Global.getCombatEngine().isEntityInPlay(proj))
					continue;
				
				if(proj.didDamage() || proj.getBaseDamageAmount() <= 0f)
					continue;
					
				if (proj.getDamageType() == DamageType.FRAGMENTATION) 
				{
					dmg = proj.getDamageAmount() * 0.5f + proj.getEmpAmount() * 0.5f;
				} 
				else 
				{
					dmg = proj.getDamageAmount() + proj.getEmpAmount() * 0.25f;
				}			
				
				//Is the proj in our collision Circle, we should bother to check if it hit/missed
				if(CollisionUtils.isPointWithinCollisionCircle(proj.getLocation(), ship) && (ship.getShield() == null || ship.getShield().isOff()) )
				{			
					if(!CollisionUtils.getCollides(proj.getLocation(),proj.getVelocity(),ship.getLocation(), 30f) && dmg >= 40f)
					{
						if(proj.didDamage())
							continue;
						
						
						Global.getCombatEngine().addFloatingText(ship.getLocation(), "Grazed!", 15f,Color.white,proj,0f,0f);
						Global.getSoundPlayer().playSound("ui_neural_transfer_begin", .7f, 1f, ship.getLocation(),new Vector2f());
						armaa_utils.blink(ship.getLocation());
						dodgeBonus = true;
						
					}
				}
			}
		}
		
		boolean enemiesInRange = false;
		float closest = 99999f;
		float effectLevel = 1f;

		if(dodgeBonus)
		{
			buffTimer.advance(amount);
			float elapsed = buffTimer.getElapsed();
			float maxinterval = buffTimer.getMaxInterval();
			effectLevel = elapsed/maxinterval;
			multBonus = GRAZE_TIME_BONUS*(1f-effectLevel);
			float refit_timer = Math.round((elapsed/maxinterval)*100f);
    		String timer = String.valueOf(refit_timer);
			Global.getCombatEngine().maintainStatusForPlayerShip("adrenal", "graphics/ui/icons/icon_search_and_destroy.png","Grazed","DMG +" + (int)(GRAZE_DAMAGE_BONUS*(1f-effectLevel))+"%",false);

		}
		
		if(buffTimer.intervalElapsed() && dodgeBonus)
		{
			dodgeBonus = false;
			cooldown = true;
			
		}
		if(cooldown)
		{
			coolDownTimer.advance(amount);
			effectLevel = 0f;
			float elapsed = coolDownTimer.getElapsed();
			float maxinterval = coolDownTimer.getMaxInterval();
			float refit_timer = Math.round((elapsed/maxinterval)*100f);
    		String timer = String.valueOf(refit_timer);
			
			if(player)
			Global.getCombatEngine().maintainStatusForPlayerShip("adrenal", "graphics/ui/icons/icon_repair_refit.png","Crash", timer+"%" ,true);
			
		}
		
		if(coolDownTimer.intervalElapsed() && cooldown)
		{
			cooldown = false;
			effectLevel = 0f;
		}
		
		ship.getMutableStats().getTimeMult().modifyMult(ship.getId(), MAX_TIME_MULT+multBonus);		
		
		if(cooldown || dodgeBonus)
		{
			ship.getMutableStats().getEnergyWeaponDamageMult().modifyPercent(ship.getId()+"_graze",GRAZE_DAMAGE_BONUS*(1f-effectLevel)); 
			ship.getMutableStats().getBallisticWeaponDamageMult() .modifyPercent(ship.getId()+"_graze",GRAZE_DAMAGE_BONUS*(1f-effectLevel));
		}
		
		else
		{
			multBonus = 0;
			ship.getMutableStats().getEnergyWeaponDamageMult().unmodify(ship.getId()+"_graze");
			ship.getMutableStats().getBallisticWeaponDamageMult().unmodify(ship.getId()+"_graze");			
		}

		if (player && ship.isAlive()) 
		{
			for(CombatEntityAPI entity : CombatUtils.getEntitiesWithinRange(ship.getLocation(), 1000f))
			{
				//if(entity.
				if(entity.getOwner() != ship.getOwner() && entity.getOwner() != 100)
				{
					enemiesInRange = true;
					if(!ship.isLanding() && !ship.isFinishedLanding())
					{
						Global.getCombatEngine().maintainStatusForPlayerShip("timeflow", "graphics/icons/hullsys/temporal_shell.png", "Heightened Reaction", "Timeflow at " + (int)(100f+(MAX_TIME_MULT+multBonus-1f)*100f)+"%", true);
						break;
					}
				}
			}
			if(enemiesInRange && !ship.isLanding() && !ship.isFinishedLanding())
			{
				float mult = 1f/((MAX_TIME_MULT+multBonus));
				Global.getCombatEngine().getTimeMult().modifyMult(ship.getId(), mult);	
			}
		}

		else 
		{
			Global.getCombatEngine().getTimeMult().unmodify(ship.getId());
		}
	}
	
	public String getDescriptionParam(int index, HullSize hullSize) {
		if (index == 0) return "" + (int)Math.round(DAMAGE_BONUS) + "%";
		if (index == 1) return "incompatible with Safety Overrides.";
		if (index == 2) return "additional large scale modifications cannot be made to the hull";
		return null;
	}


}
