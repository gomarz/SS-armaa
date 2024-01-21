package data.scripts.skills;

import java.awt.Color;

import com.fs.starfarer.api.characters.DescriptionSkillEffect;
import com.fs.starfarer.api.characters.ShipSkillEffect;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.impl.campaign.skills.*;
import java.util.List;

public class armaa_cataphract {
	
	public static float DETECTED_BONUS = 25f;
	public static float SENSOR_BONUS = 25f;
	
	public static float SLOW_BURN_BONUS = 3f;
	
	public static float GO_DARK_MULT = 0.5f;
	public static float SENSOR_BURST_PENALTY_MULT = 0.5f;
	
	

	public static class Level0 implements DescriptionSkillEffect {
		public String getString() {
			//int base = (int) RingSystemTerrainPlugin.MAX_SNEAK_BURN_LEVEL;
//			return "*A slow-moving fleet is harder to detect in some types of terrain, and can avoid some hazards. " +
//				"Several abilities also make the fleet move slowly when they are activated. The base burn " +
//				"level at which a fleet is considered to be slow-moving is " + base + ".";			
			//int reduction = (int)Math.round((1f - Misc.SNEAK_BURN_MULT) * 100f);
			return "*A slow-moving fleet is harder to detect in some types of terrain, and can avoid some hazards. " +
				"Some abilities also make the fleet move slowly when activated. A fleet is considered " +
				"slow-moving at a burn level of half that of its slowest ship.";			
		}
		public Color[] getHighlightColors() {
			return null;
//			Color h = Misc.getHighlightColor();
//			h = Misc.getDarkHighlightColor();
//			return new Color[] {h};
		}
		public String[] getHighlights() {
			return null;
//			int base = (int) RingSystemTerrainPlugin.MAX_SNEAK_BURN_LEVEL;
//			return new String [] {"" + base};
		}
		public Color getTextColor() {
			return null;
		}
	}

	
	public static class Level1 extends BaseSkillEffectDescription implements ShipSkillEffect
	{
		public void checkForCats(MutableShipStatsAPI stats)
		{
			List<String> wings = stats.getVariant().getWings();
			ShipVariantAPI ship = stats.getVariant();
			boolean hasMechs = false;
			//apparently the list isn't empty even when the carrier has no wings...
			if(!wings.isEmpty())
			for(int i = 0; i < wings.size();i++)
			{
				if(ship.getWing(i) == null)
					break;
				if(ship.getWing(i).getTags().contains("cataphract"))
				{
					hasMechs = true;
					if (!ship.hasHullMod("cataphractBonus")) 
					{
						ship.addMod("cataphractBonus");
						//NewChange = true;
					}
				}											
			}
			
			if(!hasMechs)
			{
				
				ship.removeMod("cataphractBonus");
			}
		}
		public void apply(MutableShipStatsAPI stats, HullSize hullSize, String id, float level) {
			checkForCats(stats);
		}
		
		public void unapply(MutableShipStatsAPI stats, HullSize hullSize, String id) {
			checkForCats(stats);
		}
		
		public String getEffectDescription(float level) {
			return "-" + (int) DETECTED_BONUS + "% detected-at range";
		}
		
		public String getEffectPerLevelDescription() {
			return null;
		}
		
		public ScopeDescription getScopeDescription() {
			return ScopeDescription.FLEET;
		}
	}
}



