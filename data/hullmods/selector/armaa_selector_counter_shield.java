package data.hullmods.selector;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.combat.ShieldAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShieldAPI.ShieldType;
import com.fs.starfarer.api.impl.campaign.ids.Stats;

import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.Alignment;
import java.awt.Color;

public class armaa_selector_counter_shield extends BaseHullMod {

	private static final float SHIELD_MALUS = 0.5f;
	public static final float SHIELD_BONUS_TURN = 100f;
	public static final float SHIELD_BONUS_UNFOLD = 100f;
        public static float SHIELD_BONUS = 10f;
	public static float PIERCE_MULT = 0.15f;	

	
	  @Override
    	  public int getDisplaySortOrder() 
	  {
        	return 2000;
    	  }

  	  @Override
  	  public int getDisplayCategoryIndex() 
	  {
		return 3;
    	  }

	public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) 
	{
		stats.getShieldArcBonus().modifyMult(id, 1f-SHIELD_MALUS);
		stats.getShieldTurnRateMult().modifyPercent(id, SHIELD_BONUS_TURN);
		stats.getShieldUnfoldRateMult().modifyPercent(id, SHIELD_BONUS_UNFOLD);
		stats.getShieldUnfoldRateMult().modifyPercent(id, SHIELD_BONUS_UNFOLD);
		stats.getShieldDamageTakenMult().modifyMult(id, 1f - SHIELD_BONUS * 0.01f);
		stats.getDynamic().getStat(Stats.SHIELD_PIERCED_MULT).modifyMult(id, PIERCE_MULT);
		
	}
	
	public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
		ShieldAPI shield = ship.getShield();
		if (shield != null) {
			shield.setType(ShieldType.OMNI);
		}

	}
	
    @Override
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
 		if (index == 0) return "XCS-03 Counter Shield";
		if (index == 1) return "Remove this hullmod to cycle between cores.";
        return null;    
    }

	private final Color HL=Global.getSettings().getColor("hColor");	
	private final Color TT = Global.getSettings().getColor("buttonBgDark");
	private final Color F = Global.getSettings().getColor("textFriendColor");
	private final Color E = Global.getSettings().getColor("textEnemyColor");
	@Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) 
	{
		float pad = 10f;
		float padS = 2f;
		Color[] arr ={Misc.getHighlightColor(),F};
		Color[] arrB ={Misc.getHighlightColor(),F,F};
		Color[] arr2 ={Misc.getHighlightColor(),E};
		tooltip.addSectionHeading("Details" ,Alignment.MID, 10);
		tooltip.addPara("%s " + "Shield arc decreased by %s.", pad,arr2, "-", (int)(SHIELD_MALUS*100f)+"%");
		tooltip.addPara("%s " + "Shield deployment and turn rate increased by %s.", padS, arr, "-", (int) Math.round(SHIELD_BONUS_UNFOLD) + "%");
		tooltip.addPara("%s " + "Shield damage absorption increased by %s.", padS, arr, "-", (int) Math.round(SHIELD_BONUS) + "%");
		tooltip.addPara("%s " + "Shield pierce resistance +%s ", padS, arr, "-",(int)(PIERCE_MULT * 100f) + "%");	
		tooltip.addPara("%s " + "Discharge accumulated energy by %s ", padS, HL, "-", "venting at or above 65% flux or completely filling the gauge.");			
	}


}
