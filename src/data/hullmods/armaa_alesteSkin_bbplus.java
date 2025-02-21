package data.hullmods;

import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.BaseHullMod;
import java.awt.*;

public class armaa_alesteSkin_sephira extends BaseHullMod { 
    public static final float ENGINE_SPECS = 1f;
    private final String LIGMA_RED = "bbplus_sm_unstable";
    private final String LIGMA_BLUE = "bbplus_sm_highgrade";
    private final String LIGMA_GREEN = "bbplus_sm_lowgrade";
    private final Color DEFAULT = new Color(200,55,210,215);
    private final Color LIGMA_ACCEL = new Color(255,25,25,255);
    private final Color LIGMA_CONDENSED = new Color(115,240,250,255);
    private final Color LIGMA_STABILIZED = new Color(80,240,190,255); 
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

	@Override
	public void advanceInCombat(final ShipAPI ship, final float amount) {
		if (ship.getVariant().hasHullMod("bbplus_sm_unstable_dummy")) {
			ship.getEngineController().fadeToOtherColor(this,
					LIGMA_ACCEL, null, ENGINE_SPECS, ENGINE_SPECS);
		}
		else if (ship.getVariant().hasHullMod("bbplus_sm_highgrade_dummy")) {
			ship.getEngineController().fadeToOtherColor(this,
					LIGMA_CONDENSED, null, ENGINE_SPECS, ENGINE_SPECS);
		}
		else if (ship.getVariant().hasHullMod("bbplus_sm_lowgrade_dummy")) {
			ship.getEngineController().fadeToOtherColor(this,
					LIGMA_STABILIZED, null, ENGINE_SPECS, ENGINE_SPECS);
		}
		else {
			ship.getEngineController().fadeToOtherColor(this,
					DEFAULT, null, ENGINE_SPECS, ENGINE_SPECS);
		}
	}		  
    @Override
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
 		if (index == 0) return "Gauss";
		if (index == 1) return "Remove this hullmod to cycle between weapons.";
        return null;    
    }
}
