package data.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.impl.campaign.HullModItemManager;
import com.fs.starfarer.api.campaign.CargoAPI.CargoItemType;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import java.util.Map;
import java.util.Random;
import java.awt.Color;
public class armaa_automatedCognitionShell extends BaseHullMod {

    public static final String ITEM = Commodities.GAMMA_CORE;
	public static final String DATA_PREFIX = "gamma_core_acs_check_";
	public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) 
	{
		stats.getMinCrewMod().modifyFlat(id, -1f);
		if(stats.getFleetMember() != null)
		{
			if(stats.getFleetMember().getCaptain().isDefault() || !stats.getFleetMember().getCaptain().getId().contains("armaa_automata"))
			{
				//Apparently this can be the case
				if (Misc.getAICoreOfficerPlugin("gamma_core") == null)
				{
					return;
				}				
				PersonAPI pilot = Misc.getAICoreOfficerPlugin("gamma_core").createPerson("gamma_core","player",new Random());
				int portraitNum = (int)(Math.random()*3);
                                String autoType = Math.random() < 0.20f ? "_c" : "";
				pilot.setId("armaa_automata"+autoType+portraitNum);				
				pilot.setPortraitSprite("graphics/armaa/portraits/armaa_automaton"+portraitNum+".png");
                                if(pilot.getId().equals("armaa_automata_c2"))
                                {
                                    pilot.getName().setFirst("XN");
                                    pilot.getName().setLast("KRIEGOR MK. IV");
                                }
				pilot.getName().setFirst("Automaton");
				stats.getFleetMember().setCaptain(pilot);
				Misc.setUnremovable(pilot,true);				
			}
		}
	}

	@Override
	public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
		ship.setInvalidTransferCommandTarget(true);
	}
	
    @Override
    public CargoStackAPI getRequiredItem() {
        return Global.getSettings().createCargoStack(CargoItemType.RESOURCES, ITEM, null);
    }
    
    @Override
    public void addRequiredItemSection(TooltipMakerAPI tooltip, FleetMemberAPI member, ShipVariantAPI currentVariant, MarketAPI dockedAt, float width, boolean isForModSpec) {
        CargoStackAPI req = getRequiredItem();
        if (req != null) {
            float opad = 2f;
            if (isForModSpec || Global.CODEX_TOOLTIP_MODE) {
                Color color = Misc.getBasePlayerColor();
                if (isForModSpec) {
                    color = Misc.getHighlightColor();
                }
                String name = req.getDisplayName();
                String aOrAn = Misc.getAOrAnFor(name);
                //tooltip.addPara("Requires " + aOrAn + " %s to install.", opad, color, name);
                final TooltipMakerAPI text = tooltip.beginImageWithText(Global.getSettings().getCommoditySpec(ITEM).getIconName(), 20f);
                text.addPara("Requires " + aOrAn + " %s to install.", opad, color, name);
                tooltip.addImageWithText(5f);
            } else if (currentVariant != null && member != null) {
                if (currentVariant.hasHullMod(spec.getId())) {
                    if (!currentVariant.getHullSpec().getBuiltInMods().contains(spec.getId())) {
                        Color color = Misc.getPositiveHighlightColor();
                        //tooltip.addPara("Using item: " + req.getDisplayName(), color, opad);
                        final TooltipMakerAPI text2 = tooltip.beginImageWithText(Global.getSettings().getCommoditySpec(ITEM).getIconName(), 20f);
                        text2.addPara("Using item: " + req.getDisplayName(), color, opad);
                        tooltip.addImageWithText(5f);
                    }
                } else {
                        int available = HullModItemManager.getInstance().getNumAvailableMinusUnconfirmed(req, member, currentVariant, dockedAt);
                        Color color = Misc.getPositiveHighlightColor();
                        if (available < 1) color = Misc.getNegativeHighlightColor();
                        if (available < 0) available = 0;
                        final TooltipMakerAPI text3 = tooltip.beginImageWithText(Global.getSettings().getCommoditySpec(ITEM).getIconName(), 20f);
                        text3.addPara("Requires item: " + req.getDisplayName() + " (" + available + " available)", color, opad);
                        tooltip.addImageWithText(5f);
//                        tooltip.addPara("Requires item: " + req.getDisplayName() + " (" + available + " available)", 
//                                                                color, opad);
                }
            }
        }
    }
    @Override	
	public String getCanNotBeInstalledNowReason(ShipAPI ship, MarketAPI marketOrNull, CampaignUIAPI.CoreUITradeMode mode)
	{
		if(!ship.getCaptain().isDefault() && !ship.getCaptain().getId().equals("armaa_automata"))
			return "Pilot already assigned";
		
        return super.getCanNotBeInstalledNowReason(ship, marketOrNull, mode);
		
	}
	
	@Override
	public void advanceInCampaign(FleetMemberAPI member, float amount)
	{
        final Map<String, Object> data = Global.getSector().getPersistentData();

        if (data.containsKey(DATA_PREFIX + member.getId())) {
            return;
        }

		if(!member.getVariant().hasHullMod("armaa_acsutilityscript"))
		{
			data.put(DATA_PREFIX + member.getId(),"_");	
			member.getVariant().addPermaMod("armaa_acsutilityscript");			
		}
	}	
		
	@Override	
	public boolean isApplicableToShip(ShipAPI ship) 
	{
		return (ship.getVariant().hasHullMod("strikeCraft") && (ship.getCaptain() == null || ship.getCaptain().isDefault() || ship.getCaptain().getId().contains("armaa_automata")));
	}	
	
	@Override
	public String getUnapplicableReason(ShipAPI ship) 
	{
        if (ship == null) 
			return "Can not be assigned";
		
		if(ship.getCaptain() != null)
			return "Pilot already assigned";
		
		return "Only installable on strikecraft or carriers larger than frigates";
	}
	
	public String getDescriptionParam(int index, HullSize hullSize) {
		return null;
	}

    @Override
    public void addPostDescriptionSection(final TooltipMakerAPI tooltip, final ShipAPI.HullSize hullSize, final ShipAPI ship, final float width, final boolean isForModSpec) {
        if (isForModSpec || ship == null) {
            return;
        }   

        tooltip.addPara("%s", 6f, Misc.getGrayColor(), new String[] { "\"They salute. They sit. They fly. Technically, no laws are broken.\"" }).italicize();
        tooltip.addPara("%s", 1f, Misc.getGrayColor(), new String[] { "         \u2014 Marginalia scribbled on an ArmaA requisition form, unsigned" });    
    }	
	
}
