package data.hullmods;

import java.util.*;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import com.fs.starfarer.api.util.Misc;
import java.awt.Color;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import org.magiclib.util.MagicIncompatibleHullmods;
import data.scripts.util.armaa_sykoEveryFrame;
import com.fs.starfarer.api.ui.Alignment;

import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;

import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;
public class armaa_sykoStims extends BaseHullMod {

	private final float DAMAGE_BONUS = 100f;
	private final float MIN_PROJECTILE_DMG = 40f;
	private final float GRAZE_DAMAGE_BONUS = 10f;
	private final float GRAZE_TIME_BONUS = 3f;
	private final float DAMAGE_MALUS = 1.1f;	
	private final float GRAZE_TIME_BONUS_MIN = 1.1f;
	private final float MAX_TIME_MULT = 1f;
	private final float MIN_TIME_MULT = 0.1f;
	private final float up = 0.7f;
	private final float down = 10f;
	public static final int DRUGS_CONSUMED = 3;
	private IntervalUtil buffTimer = new IntervalUtil(up,up);
	private IntervalUtil coolDownTimer = new IntervalUtil(down,down);
	
	private static final Set<String> BLOCKED_HULLMODS = new HashSet<>();
	static
	{
		BLOCKED_HULLMODS.add("armaa_SilverSwordHM");

	}
		
	@Override
	public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) 
	{
		if(ship == null)
			return;
		float HEIGHT = 64f;
        float PAD = 10f;
        Color YELLOW = new Color(241, 199, 0);
		String DrugsIcon = "graphics/armaa/icons/hullsys/armaa_drugs_icon.png";				
		String CSTitle = "'Sy-Ko' Combat Stimulant";
		String CSText1 = "\u2022 Narrowly evading projectiles with %s administers a high-grade synthetic stimulant that includes a psychotropic aggression enhancer.";
		String CSText2 = "\u2022 Time Dilation is increased by %s.";
		String CSText3 = "\u2022 Energy and Ballistic weapon damage is increased by %s.";
		String CSText4 = "\u2022 Evasion bonus degrades over %s seconds, after which system cannot trigger again for %s seconds.";
		String CSText5 = "\u2022 Consumes %s unit of %s per engagement.";
		
		float pad = 2f;
		Color[] arr ={Misc.getPositiveHighlightColor(),Misc.getHighlightColor()};		
		tooltip.addSectionHeading("Details", Alignment.MID, 5);  
        TooltipMakerAPI combatDrugs = tooltip.beginImageWithText(DrugsIcon, HEIGHT);
		Color[] disabletext ={Global.getSettings().getColor("textGrayColor"),Misc.getNegativeHighlightColor()};
        combatDrugs.addPara(CSTitle, pad, YELLOW, CSTitle);
		boolean inCampaign = Global.getSector().getPlayerFleet() != null;		
		if(inCampaign && Global.getSector().getPlayerFleet().getCargo().getCommodityQuantity(Commodities.DRUGS) > DRUGS_CONSUMED)
		{
			combatDrugs.addPara(CSText5, pad, Misc.getNegativeHighlightColor(), ""+DRUGS_CONSUMED,"Recreational Drugs");
			combatDrugs.addPara(CSText1, pad, Misc.getHighlightColor(),"shields lowered");
			combatDrugs.addPara(CSText2, pad, Misc.getPositiveHighlightColor(), (int)(Math.round((GRAZE_TIME_BONUS-1f)*100f))+"%");
			combatDrugs.addPara(CSText3, pad, Misc.getPositiveHighlightColor(), Misc.getRoundedValueMaxOneAfterDecimal(GRAZE_DAMAGE_BONUS)+"%");
			combatDrugs.addPara(CSText4, pad, Misc.getNegativeHighlightColor(), Misc.getRoundedValueMaxOneAfterDecimal(buffTimer.getMaxInterval())+"",(int)coolDownTimer.getMaxInterval()+"");
			combatDrugs.addPara("\u2022 Damage received to hull increased by %s", pad, Misc.getNegativeHighlightColor(), (int)(Math.round((DAMAGE_MALUS-1f)*100f))+"%");			
		}
		
		else
			combatDrugs.addPara("%s - components of the stimulant require %s.", pad, disabletext,"DISABLED",""+DRUGS_CONSUMED+ " Recreational Drugs");	
		
        UIPanelAPI temp = tooltip.addImageWithText(PAD);		
		final Color flavor = new Color(110,110,110,255);
        tooltip.addPara("%s", 6f, flavor, new String[] { "\"... pilots on stims benefit from enhanced reflexes, but are subject to long-term side effects including insomnia, mania/hypomania, internal hemorrhaging, and cerebral deterioration ... Nonetheless, both commanders and pilots stand by the use of these stims as essential to their continued survival and effectiveness on the field...\"" }).italicize();
        tooltip.addPara("%s", 1f, flavor, new String[] { "         \u2014 Excerpt from 'Rad-Addled: Spacer Life'" });

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
		tooltip.addPara("%s " + "Incompatible with %s.", PAD, arr2, "\u2022", "" + str);
    }
	
	@Override	
	public boolean isApplicableToShip(ShipAPI ship) 
	{
		return (!ship.isStationModule() && ship.getVariant().hasHullMod("strikeCraft"));
	}

	public String getUnapplicableReason(ShipAPI ship) 
	{
        if (ship == null) 
			return "Can not be assigned";
		
		return "Only installable on strikecraft.";
	}

	@Override
	public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) 
	{
		stats.getHullDamageTakenMult().modifyMult(id,1.1f);
	
	}

	public void applyEffectsAfterShipCreation(ShipAPI ship, String id) 
	{
		for (String tmp : BLOCKED_HULLMODS)
        {
			if(ship.getVariant().getHullMods().contains(tmp))
			{
   				MagicIncompatibleHullmods.removeHullmodWithWarning(ship.getVariant(), tmp, "armaa_sykoStims");
			}
        }
	}
	
  @Override
    public void advanceInCombat(ShipAPI ship, float amount) 
	{

        //don't run while paused because duh
        if (Global.getCombatEngine().isPaused() || ship.getOwner() == -1) {
            return;
        }
		
		if(!Global.getCombatEngine().getCustomData().containsKey("armaa_sykoStims_"+ship.getId()))
		{
			Global.getCombatEngine().addPlugin(new armaa_sykoEveryFrame(ship,MIN_PROJECTILE_DMG,GRAZE_DAMAGE_BONUS,GRAZE_TIME_BONUS,GRAZE_TIME_BONUS_MIN,up,down));
			Global.getCombatEngine().getCustomData().put("armaa_sykoStims_"+ship.getId(),"_");
		}
		
	}
	
	public String getDescriptionParam(int index, HullSize hullSize) {
		if (index == 0) return "" + (int)Math.round(DAMAGE_BONUS) + "%";
		if (index == 1) return "incompatible with Safety Overrides.";
		if (index == 2) return "additional large scale modifications cannot be made to the hull";
		return null;
	}


}
