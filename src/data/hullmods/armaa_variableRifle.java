package data.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import data.scripts.MechaModPlugin;
import com.fs.starfarer.api.Global;
import java.awt.Color;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

public class armaa_variableRifle extends BaseHullMod {
	

	
	/*private static Map mag = new HashMap();
	static {
		mag.put(HullSize.FIGHTER, 0.0f);
		mag.put(HullSize.FRIGATE, 0.25f);
		mag.put(HullSize.DESTROYER, 0.15f);
		mag.put(HullSize.CRUISER, 0.10f);
		mag.put(HullSize.CAPITAL_SHIP, 0.05f);
	}*/	
	
	@Override
	public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) 
	{
		ShipVariantAPI variant = stats.getVariant();
		boolean anythingGoes = MechaModPlugin.varrifle_weapons.contains("ALL");
		if(anythingGoes)
			return;
		if(variant != null && variant.getWeaponId("TRUE_GUN") != null)
			if(!MechaModPlugin.varrifle_weapons.contains(variant.getWeaponId("TRUE_GUN")))
			{
				if(Global.getSector() != null)
				Global.getSector().getPlayerFleet().getCargo().addWeapons(variant.getWeaponId("TRUE_GUN"),1);
				variant.clearSlot("TRUE_GUN");
				Global.getSoundPlayer().playUISound("cr_playership_warning", .8f, 1f);
			}
	}
	private final Color E = Global.getSettings().getColor("textFriendlyColor");
	private final Color dark = Global.getSettings().getColor("textGrayColor");	
	@Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) 
	{
		float pad = 10f;
		float padS = 2f;
		tooltip.addSectionHeading("Details", Alignment.MID, 10);  
		tooltip.addPara("%s " + "This unit carries a rifle that can be configured to mimic the characteristics of various starship-grade weapons developed throughout the sector, however not all weapons are compatible.", pad, Misc.getHighlightColor(), "-");
		tooltip.addPara("%s " + "These limitations can be adjusted via modSettings.", padS, Misc.getHighlightColor(), "-");
		tooltip.addSectionHeading("Compatible with:", Alignment.MID, 10);
		String str = "";
		if(MechaModPlugin.varrifle_weapons.contains("ALL"))
			return;
		int size = MechaModPlugin.varrifle_weapons.size();
		int counter = 0;
		Color[] arr2 ={Misc.getHighlightColor(),E};
        for (WeaponSpecAPI spec : Global.getSettings().getAllWeaponSpecs())
        {
			for (String tmp : MechaModPlugin.varrifle_weapons)
			{
				if(spec.getWeaponId().equals(tmp))
				{				
					counter++;
					str+=spec.getWeaponName();		
					if(counter!= size-1)
						str+=", ";
				}
			}
        }
		
		str = str.substring(0, str.length() - 2);
		tooltip.addPara("%s " + "Compatible with %s.", pad, arr2, "-", "" + str);	
	}
}
