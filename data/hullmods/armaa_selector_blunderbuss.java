package data.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.combat.ShieldAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShieldAPI.ShieldType;

public class armaa_selector_blunderbuss extends BaseHullMod {
	
	private static final float ARMOR_BONUS_MULT = 1.1f;
	private static final float SHIELD_MALUS = -75f;
	private static final float CAPACITY_MULT = 1.2f;
	private static final float DISSIPATION_MULT = 1.2f;
	private static final float HANDLING_MULT = 1.2f;
	
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
	

    @Override
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
 		if (index == 0) return "Blunderbuss";
		if (index == 1) return "Remove this hullmod to cycle between cores.";
        return null;    
    }

}
