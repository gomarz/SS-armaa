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

import java.util.HashMap;
import java.util.Map;

	public class armaa_selector_blunderbuss extends BaseHullMod 
	{

		public static final float AMMO_BONUS = 50f;
		private static final float CAPACITY_MULT = 1.05f;
		private static final float DISSIPATION_MULT = 1.05f;

		private static Map mag = new HashMap();
		static 
		{
			mag.put(HullSize.FRIGATE, 15f);
			mag.put(HullSize.DESTROYER, 20f);
			mag.put(HullSize.CRUISER, 15f);
			mag.put(HullSize.CAPITAL_SHIP, 15f);
		}
	
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
			stats.getBallisticAmmoBonus().modifyPercent(id, AMMO_BONUS);
			stats.getEnergyAmmoBonus().modifyPercent(id, AMMO_BONUS);
			stats.getMissileAmmoBonus().modifyPercent(id, AMMO_BONUS);
			stats.getMaxSpeed().modifyFlat(id, (Float) mag.get(hullSize));
		}
	
	public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {

	}
	
    @Override
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
 		if (index == 0) return "XBB-01 Shrike";
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
		tooltip.addPara("%s " + "All ammo increased by %s.", pad, arr, "-", (int) Math.round(AMMO_BONUS) + "%");
		tooltip.addPara("%s " + "Max speed increased by %s.", padS, arr, "-", (int) Math.round((1f-.75f) * 100f)+"");
	}


}
