package data.hullmods;

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

public class armaa_selector_boson extends BaseHullMod 
{

	public static final float DAMAGE_BONUS = 50f;
	
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
		
	}
	
	public void applyEffectsAfterShipCreation(ShipAPI ship, String id) 
	{

	}
	
    @Override
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
 		if (index == 0) return "HPC-02S Zenith";
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
		tooltip.addPara("%s " + "No particular strengths or weaknesses.", pad,arr2, "-");	
	}


}
