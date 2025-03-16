package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import org.lazywizard.lazylib.MathUtils;
import com.fs.starfarer.api.combat.ShipCommand;
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

import org.magiclib.util.MagicIncompatibleHullmods;
import data.scripts.MechaModPlugin;

import java.awt.Color;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import com.fs.starfarer.api.ui.Alignment;

public class cataphract extends BaseHullMod {

    private float GROUND_BONUS_DEFAULT = 5;

    private static final float PARA_PAD = 10f;
    private String  ERROR="IncompatibleHullmodWarning";
    private static final Set<String> BLOCKED_HULLMODS = new HashSet<>();
    private static final Set<String> SOMETIMES_OK_HULLMODS = new HashSet<>();
	public static final Map<String, Float> GROUND_BONUS = new HashMap<>();
    static{
        // These hullmods will automatically be removed
        // This prevents unexplained hullmod blocking
        BLOCKED_HULLMODS.add("converted_hangar");
        BLOCKED_HULLMODS.add("magazines");
        BLOCKED_HULLMODS.add("missleracks");
        BLOCKED_HULLMODS.add("VEmagazines");
        BLOCKED_HULLMODS.add("VEmissleracks");
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
        SOMETIMES_OK_HULLMODS.add("magazines");
        SOMETIMES_OK_HULLMODS.add("missleracks");
        SOMETIMES_OK_HULLMODS.add("VEmagazines");
        SOMETIMES_OK_HULLMODS.add("VEmissleracks");
    }
	
    static 
	{
        GROUND_BONUS.put("armaa_einhander", 30f);
        GROUND_BONUS.put("armaa_aleste_frig", 15f);
		GROUND_BONUS.put("armaa_leynos_frig", 15f);
		GROUND_BONUS.put("armaa_leynos_frig_comet", 10f);
		GROUND_BONUS.put("armaa_valkazard", 30f);
		GROUND_BONUS.put("armaa_record_frig", 10f);
				
    }
    private final float EMP_RESIST=33, DISABLE_RESIST=33;
    private static final float CR_PENALTY = 0.10f;
	private final float MISSILES_DEBUFF = 0.5f;

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
		
		if(!stats.getVariant().hasHullMod("armaa_strikeCraftFrig"))
			stats.getMissileRoFMult().modifyMult(id, MISSILES_DEBUFF);
    }
	
	@Override
    public void advanceInCampaign(FleetMemberAPI member, float amount) 
	{
		float level = member.getCaptain().isDefault() ? 1:member.getCaptain().getStats().getLevel()*1.5f;
		
        if (member.getRepairTracker().getBaseCR() >= getCRPenalty(member.getVariant())) {
			if(GROUND_BONUS.get(member.getHullSpec().getBaseHullId()) != null)
				member.getStats().getDynamic().getMod(Stats.FLEET_GROUND_SUPPORT).modifyFlat("id", GROUND_BONUS.get(member.getHullSpec().getBaseHullId())+level);
        } else {
				member.getStats().getDynamic().getMod(Stats.FLEET_GROUND_SUPPORT).modifyFlat("id", GROUND_BONUS_DEFAULT+level);
        }
    }

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id)
    {
        for (String tmp : BLOCKED_HULLMODS)
        {
			if(ship.getVariant().getNonBuiltInHullmods().contains(tmp))
			{
				if(!ship.getVariant().hasHullMod("armaa_strikeCraftFrig") &&SOMETIMES_OK_HULLMODS.contains(tmp))
					MagicIncompatibleHullmods.removeHullmodWithWarning(ship.getVariant(), tmp, "cataphract");
			}
        }
    }

	private final Color E = Global.getSettings().getColor("textEnemyColor");
	private final Color dark = Global.getSettings().getColor("textGrayColor");	
	@Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) 
	{
		float pad = 10f;
		float padS = 2f;
		final Color flavor = new Color(110,110,110,255);
		float n2 = GROUND_BONUS_DEFAULT;
		if(ship == null)
			return;
		if(GROUND_BONUS.get(ship.getHullSpec().getHullId()) != null)
		n2  = GROUND_BONUS.get(ship.getHullSpec().getHullId());	
		float missilerate = MISSILES_DEBUFF*100f;
		int level = ship.getFleetMember().getCaptain().isDefault() ? 1:ship.getFleetMember().getCaptain().getStats().getLevel();

		int n = (int) n2+level;
		tooltip.addSectionHeading("Details", Alignment.MID, 10);  
		tooltip.addPara("%s " + "EMP Resistance increased by %s.", pad, Misc.getHighlightColor(), "\u2022", "" + (int)EMP_RESIST + "%");
		tooltip.addPara("%s " + "Weapons and engines are %s less likely to be disabled.", padS, Misc.getHighlightColor(), "\u2022", "" + (int) DISABLE_RESIST + "%");
		tooltip.addPara("%s " + "Increases effective strength of ground ops by %s, (up to number of marines in the fleet)", padS, Misc.getHighlightColor(), "\u2022", Integer.toString(n));
		tooltip.addPara("%s " + "Ground deployments consume %s CR.", padS, Misc.getHighlightColor(), "\u2022", ""+ (int) Math.round(getCRPenalty(ship.getVariant()) * 100f) + "%");
		
		//if(!ship.getVariant().hasHullMod("armaa_strikeCraftFrig"))
		tooltip.addPara("%s " + "Missile firerate reduced by %s.", padS, Misc.getHighlightColor(), "\u2022","" + (int) missilerate + "%");
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
				if(ship.getVariant().hasHullMod("armaa_strikeCraftFrig") && SOMETIMES_OK_HULLMODS.contains(tmp))
				{
					counter++;
					continue;
				}
					
				if(spec.getId().equals(tmp))
				{				
					counter++;
					str+=spec.getDisplayName();		
					if(counter!= size-1)
						str+=", ";
				}
			}
        }
		
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
}
