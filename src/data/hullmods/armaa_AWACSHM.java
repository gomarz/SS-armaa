package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.weapons.armaa_AWACSCeylon;
import java.awt.*;

public class armaa_AWACSHM extends BaseHullMod
{
	// nothing to do, just a marker hullmod - features are handled in weapon script
	private final float RANGE_INCREASE = armaa_AWACSCeylon.RANGE_BOOST;
	private final float SPEED_INCREASE = armaa_AWACSCeylon.SPEED_BOOST;
	private final Color HL=Global.getSettings().getColor("hColor");
	private final Color F = Global.getSettings().getColor("textFriendColor");	
	private final Color E = Global.getSettings().getColor("textEnemyColor");
	@Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) 
	{
		float pad = 10f;
		float padS = 2f;
		boolean isXiv = ship.getVariant().hasHullMod("fourteenth") ? true : false;
		Color[] arr ={Misc.getHighlightColor(),F};
		Color[] arrB ={Misc.getHighlightColor(),F,F};
		Color[] arr2 ={Misc.getHighlightColor(),E};	
		Color[] arr3 ={Misc.getHighlightColor(),Misc.getHighlightColor(),E};	
		
		//String normalAura =
		//String xivAura =
		
		
		tooltip.addSectionHeading("Details", Alignment.MID, 10);
		
		tooltip.addPara("%s " + "Deploys 3 electronic warfare drones that enhance the aura strength and radius of a passive aura", pad, Misc.getHighlightColor(), "-", "3");
		if(!isXiv)
		{
			tooltip.addPara("%s " + "This aura increases the range of all friendly ships by %s", padS, Misc.getHighlightColor(), "-", ""+RANGE_INCREASE+" units.");
			tooltip.addPara("%s " + "While the ship system is active, enemy ships within range deal %s less damage.", padS, Misc.getHighlightColor(), "-", "20%");
		}
		else
		{
			tooltip.addPara("%s " + "This aura increases the move speed of all friendly ships by %s", padS, Misc.getHighlightColor(), "-", SPEED_INCREASE+"%");
			tooltip.addPara("%s " + "While the ship system is active, enemy missiles in range are slowed by %s, and have %s.", padS, Misc.getHighlightColor(), "-","50%", "reduced tracking");		
		}
	}
}

