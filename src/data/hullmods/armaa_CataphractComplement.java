package data.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.util.Misc;
import java.util.List;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

public class armaa_CataphractComplement extends BaseHullMod {

	//default bonus
	public final float GROUND_BONUS = 4f;
	public boolean runOnce = false;
	public float WING_BONUS = 1f;
	//public float

	public float setBonus(ShipVariantAPI variant)
	{

		List<String> wings = variant.getWings();
		float cataphractCount = 0;
		for(int i = 0; i < wings.size();i++)
		{
			String wing = wings.get(i);
			
			switch(wing) {
			   case "armaa_kouto_wing":
			   case "armaa_valkenx_wing":
				  cataphractCount+=2;
				  break; // optional
				  
			   case "armaa_record_wing":			   
			   case "armaa_valkenmp_wing":
				  cataphractCount++;
				  break; // optional
				  
			   case "armaa_valkencannon_wing":
				  cataphractCount+=3;
				  break; // optional
				  
			   case "armaa_ilorin_wing":
				  cataphractCount+=3;
				  break; // optional

			   case "armaa_aleste_wing":
				  cataphractCount+=3;
				  break; // optional

			   case "armaa_einhander_wing":
				  cataphractCount+=3;
				  break; // optional
				  
			   // You can have any number of case statements.
			   default :
				  cataphractCount++;			   
				  break;
				  // Optional
				  // Statements
			}
		
		}
		WING_BONUS = cataphractCount;
	//	if(Global.getCombatEngine() != null)
	//	Global.getCombatEngine().getCustomData().put("armaa_complement_bonus_"+variant.getFullDesignationWithHullNameForShip(),WING_BONUS);
		return WING_BONUS;
		
	}
	
	public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
		
		stats.getDynamic().getMod(Stats.FLEET_GROUND_SUPPORT).modifyFlat(id, GROUND_BONUS*setBonus(stats.getVariant()));
		//stats.getDynamic().getMod(Stats.FLEET_BOMBARD_COST_REDUCTION).modifyFlat(id, GROUND_BONUS);
	}
	@Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) 
	{
		float pad = 10f;
		float padS = 2f;
		float bonus = setBonus(ship.getVariant())*GROUND_BONUS;
		//float bonus = (float)Global.getCombatEngine().getCustomData().get("armaa_complement_bonus_"+ship.getVariant().getFullDesignationWithHullNameForShip());
		tooltip.addPara("%s " + "Ground support increased by %s.", pad, Misc.getHighlightColor(), "-", (int)(bonus)+"");

	}		

}




