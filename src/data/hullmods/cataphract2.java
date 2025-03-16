package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import java.util.Collection;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.impl.hullmods.CompromisedStructure;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.combat.WeaponAPI;


import org.magiclib.util.MagicIncompatibleHullmods;
import org.lwjgl.util.vector.Vector2f;
import java.awt.Color;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import com.fs.starfarer.api.ui.Alignment;

public class cataphract2 extends BaseHullMod {

    //private float GROUND_BONUS = 15;

    private static final float PARA_PAD = 10f;
    private String  ERROR="IncompatibleHullmodWarning";
    private static final Set<String> BLOCKED_HULLMODS = new HashSet<>();
	public static final Map<String, Float> GROUND_BONUS = new HashMap<>();
    static{
        // These hullmods will automatically be removed
        // This prevents unexplained hullmod blocking
        BLOCKED_HULLMODS.add("converted_hangar");
        BLOCKED_HULLMODS.add("cargo_expansion");
        BLOCKED_HULLMODS.add("additional_crew_quarters");
        BLOCKED_HULLMODS.add("fuel_expansion");
        BLOCKED_HULLMODS.add("additional_berthing");
        BLOCKED_HULLMODS.add("auxiliary_fuel_tanks");
        BLOCKED_HULLMODS.add("expanded_cargo_holds");
        BLOCKED_HULLMODS.add("surveying_equipment");
        BLOCKED_HULLMODS.add("recovery_shuttles");
        BLOCKED_HULLMODS.add("operations_center");
		BLOCKED_HULLMODS.add("expanded_deck_crew");
		BLOCKED_HULLMODS.add("combat_docking_module");
		BLOCKED_HULLMODS.add("roider_fighterClamps");
    }
    static 
	{
        GROUND_BONUS.put("armaa_einhander", 25f);
        GROUND_BONUS.put("armaa_aleste_frig", 25f);
		GROUND_BONUS.put("armaa_leynos_frig", 25f);
		GROUND_BONUS.put("armaa_leynos_frig_comet", 25f);
		GROUND_BONUS.put("armaa_valkazard", 50f);
		GROUND_BONUS.put("armaa_valkenx_frig",25f);
    }
    private final float EMP_RESIST=33, DISABLE_RESIST=66;
    private static final float CR_PENALTY = 0.10f;
	private final float MISSILES_DEBUFF = 0.25f;
	public static float SMOD_BONUS = 2f;
    public static float getCRPenalty(ShipVariantAPI variant) {
        float scale = 1f;

        Collection<String> hullMods = variant.getHullMods();
        for (String hullMod : hullMods) {
            HullModSpecAPI modSpec = Global.getSettings().getHullModSpec(hullMod);
            if (modSpec.hasTag(Tags.HULLMOD_DMOD)) {
                scale /= CompromisedStructure.DEPLOYMENT_COST_MULT;
            }
        }

        return scale * CR_PENALTY;
    }

 public static String getNonDHullId(ShipHullSpecAPI spec) {
        if (spec == null) {
            return null;
        }
        if (spec.getDParentHullId() != null && !spec.getDParentHullId().isEmpty()) {
            return spec.getDParentHullId();
        } else {
            return spec.getHullId();
        }
    }

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) 
	{
        stats.getEmpDamageTakenMult().modifyMult(id, (100-EMP_RESIST)/100);
        stats.getEngineDamageTakenMult().modifyMult(id, (100-DISABLE_RESIST)/100);
        stats.getWeaponDamageTakenMult().modifyMult(id, (100-DISABLE_RESIST)/100);
		boolean sMod = stats.getVariant().getSModdedBuiltIns().contains("cataphract2") || isSMod(stats);
		if (sMod) 
		{
			if(stats.getFleetMember() != null)
			{
				float level = stats.getFleetMember().getCaptain().getStats().getLevel();
				int dpCost = (int)stats.getFleetMember().getDeploymentPointsCost();
				stats.getEnergyRoFMult().modifyMult(id,1f+SMOD_BONUS*((level/2)*0.01f));
				stats.getBallisticRoFMult().modifyMult(id,1f+SMOD_BONUS*((level/2)*0.01f)); 
				stats.getFluxDissipation().modifyMult(id,1f+SMOD_BONUS*(level*0.01f));
				stats.getAutofireAimAccuracy().modifyMult(id,SMOD_BONUS*(level*0.01f));
				stats.getMaxTurnRate().modifyMult(id,1f+SMOD_BONUS*(level*0.01f));
				stats.getTurnAcceleration().modifyMult(id,1f+SMOD_BONUS*(level*0.01f));
				stats.getDynamic().getMod(Stats.DEPLOYMENT_POINTS_MOD).modifyFlat(id, dpCost * (SMOD_BONUS*(level*0.01f)));
			}
		}

    }
	
	@Override
	public boolean isSModEffectAPenalty() {
		return false;
	}

	public String getSModDescriptionParam(int index, HullSize hullSize) {
		if (index == 0) return "" + (int) SMOD_BONUS + "%";
		return null;
	}

	@Override
    public void advanceInCampaign(FleetMemberAPI member, float amount) 
	{
		if(member.getFleetData() != null && member.getFleetData().getFleet() != null && member.getFleetData().getFleet().getViewForMember(member) != null)
		{
			member.getFleetData().getFleet().getViewForMember(member).getContrailColor().setBase(new Color(0f,0f,0f,0f));
			member.getFleetData().getFleet().getViewForMember(member).getEngineColor().setBase(new Color(0f,0f,0f,0f));	
			member.setSpriteOverride("");
		}
		float level = member.getCaptain().isDefault() ? 0:member.getCaptain().getStats().getLevel()*1.5f;
        if (member.getRepairTracker().getBaseCR() >= getCRPenalty(member.getVariant())) {
			if(GROUND_BONUS.get(member.getHullSpec().getBaseHullId()) != null)
            member.getStats().getDynamic().getMod(Stats.FLEET_GROUND_SUPPORT).modifyFlat("id", GROUND_BONUS.get(member.getHullSpec().getBaseHullId())+level);
        } else {
            member.getStats().getDynamic().getMod(Stats.FLEET_GROUND_SUPPORT).unmodify("id");
        }
    }

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id)
    {
        for (String tmp : BLOCKED_HULLMODS)
        {
			if(ship.getVariant().getNonBuiltInHullmods().contains(tmp))
			{
   				MagicIncompatibleHullmods.removeHullmodWithWarning(ship.getVariant(), tmp, "cataphract");
			}
        }		
    }

	private final Color E = Global.getSettings().getColor("textEnemyColor");
	private final Color dark = Global.getSettings().getColor("textGrayColor");
	float pad = 10f;
	float padS = 2f;
	final Color flavor = new Color(110,110,110,255);	
	@Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) 
	{
		float pad = 10f;
		float padS = 2f;
		float level = 0;
		if(ship != null)
			level = ship.getCaptain().isDefault() ? 0:ship.getCaptain().getStats().getLevel()*1.5f;

		float n2 = 15f;
		final Color flavor = new Color(110,110,110,255);
		if(ship != null && GROUND_BONUS.get(ship.getHullSpec().getHullId()) != null)
		n2  = GROUND_BONUS.get(ship.getHullSpec().getHullId());	
		float missilerate = MISSILES_DEBUFF*100f;
		int n = (int)(n2+level);
		tooltip.addSectionHeading("Details", Alignment.MID, 10);  
		tooltip.addPara("%s " + "EMP Resistance increased by %s.", pad, Misc.getHighlightColor(), "\u2022", "" + (int)EMP_RESIST + "%");
		tooltip.addPara("%s " + "Weapons and engines are %s less likely to be disabled.", padS, Misc.getHighlightColor(), "\u2022", "" + (int) DISABLE_RESIST + "%");
		tooltip.addPara("%s " + "Increases effective strength of ground ops by %s, (up to number of marines in the fleet)", padS, Misc.getHighlightColor(), "\u2022", Integer.toString(n));
		if(ship != null)
		tooltip.addPara("%s " + "Ground deployments consume %s CR.", padS, Misc.getHighlightColor(), "\u2022", ""+ (int) Math.round(getCRPenalty(ship.getVariant()) * 100f) + "%");
	//	tooltip.addPara("%s " + "Missile firerate reduced by %s.", padS, Misc.getHighlightColor(), "\u2022","" + (int)(100-missilerate) + "%");
		tooltip.addPara("%s " + "Large modifications cannot be made, %s.", padS, Misc.getHighlightColor(), "\u2022","precluding the addition of several hullmods");
        tooltip.addPara("%s", 6f, flavor, new String[] { "\"Morning! How's it feel to be strapped to a walking metal coffin at six AM? Bet you wish you studied harder at the academy! Hit the throttle. We're going to be killing people before breakfast today.\"" }).italicize();
        tooltip.addPara("%s", 1f, flavor, new String[] { "         \u2014 Savarin Johnson, c. 192" });
		tooltip.addSectionHeading("Incompatibilities", Alignment.MID, 10);
		String str = "";
		int size = BLOCKED_HULLMODS.size();
		int counter = 0;
		Color[] arr2 ={Misc.getHighlightColor(),E};
        for (HullModSpecAPI spec : Global.getSettings().getAllHullModSpecs())
        {
			for (String tmp : BLOCKED_HULLMODS)
			{
				if(spec.getId().equals(tmp))
				{				
					counter++;
					str+=spec.getDisplayName();		
					if(counter!= size-1)
						str+=", ";
				}
			}
        }
		
		if(ship == null)
			return;
		
		str = str.substring(0, str.length() - 2);
		tooltip.addPara("%s " + "Incompatible with %s.", pad, arr2, "\u2022", "" + str);	
        if (getCRPenalty(ship.getVariant()) > CR_PENALTY) {
            float penaltyScalePct = 100f * ((getCRPenalty(ship.getVariant()) / CR_PENALTY) - 1f);
            LabelAPI label = tooltip.addPara("CR Deployment cost is penalized by %s due to hull defects.", PARA_PAD, Misc.getNegativeHighlightColor(), "" + Math.round(penaltyScalePct) + "%");
        }
		
        float CR = ship.getCurrentCR();
        if (ship.getFleetMember() != null) {
            CR = ship.getFleetMember().getRepairTracker().getBaseCR();
        }
        if (CR < getCRPenalty(ship.getVariant())) {
            LabelAPI label = tooltip.addPara("Insufficient CR for deployment!", Misc.getNegativeHighlightColor(), PARA_PAD);
        }

	}

	@Override	
	public boolean isApplicableToShip(ShipAPI ship) 
	{
		return false;
	}	
	
	@Override
    public void addSModEffectSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec, boolean isForBuildInList)
	{
		Color color = flavor;
		boolean sMod = ship != null && (ship.getVariant().getSModdedBuiltIns().contains("cataphract2") || isSMod(ship.getMutableStats()));
		if (sMod) 
		{
			color = Misc.getStoryBrightColor(); 
		}
		float level = 0;
		if(ship != null)
			level = ship.getCaptain().isDefault() ? 0:ship.getCaptain().getStats().getLevel();
		float value = SMOD_BONUS;
		//tooltip.addSectionHeading("OS Optimization:", Alignment.MID, 10);
		tooltip.addPara("%s " + "Increases flux dissipation, turning rate, and autofire accuracy by %s per pilot level. The current increase is %s.", pad, color, Misc.getHighlightColor(), "\u2022", "" + (int)value + "%",(int)(value*level) + "%");
		tooltip.addPara("%s " + "Increases non-missile ROF by %s per pilot level. The current increase is %s.", pad, color, Misc.getHighlightColor(), "\u2022", "" + (int)value/2 + "%",(int)(value*level)/2 + "%");
		tooltip.addPara("%s " + "Increases deployment cost by %s.", pad, color, Misc.getHighlightColor(), "\u2022", "" + (int)(value*level) + "%",(int)(value*level) + "%");		
	}		
}
