package data.scripts.campaign.intel;

import java.awt.Color;
import java.util.Set;
import java.util.Random;


import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.command.WarSimScript;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.loading.Description;
import com.fs.starfarer.api.loading.Description.Type;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.impl.campaign.intel.group.GenericRaidFGI.GenericRaidParams;
import com.fs.starfarer.api.impl.campaign.intel.group.FGBlockadeAction.FGBlockadeParams;
import com.fs.starfarer.api.impl.campaign.missions.FleetCreatorMission.FleetStyle;
import data.scripts.campaign.intel.group.armaa_mrcExpedition;
public class armaa_reprisalIntel extends BaseIntelPlugin {
	
	protected SectorEntityToken entity;
	
	public static void addIntelIfNeeded(String id, TextPanelAPI text) {
		addIntelIfNeeded(getEntity(id), text);
	}
	public static void addIntelIfNeeded(SectorEntityToken entity, TextPanelAPI text) {
		addIntelIfNeeded(entity, text, false);
	}
	public static void addIntelIfNeeded(SectorEntityToken entity, TextPanelAPI text, boolean quiet) {
		if (getIntel(entity) == null) {
			armaa_reprisalIntel intel = new armaa_reprisalIntel(entity);
			Global.getSector().getIntelManager().addIntel(intel, quiet, text);
		}
	}
		
	public static armaa_reprisalIntel getIntel(SectorEntityToken entity) {
		for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel(armaa_reprisalIntel.class)) {
			if (((armaa_reprisalIntel)intel).getEntity() == entity) return (armaa_reprisalIntel)intel;
		}
		return null;
	}
	
	public static SectorEntityToken getEntity(String id) {
		MarketAPI market = Global.getSector().getEconomy().getMarket(id);
		if (market != null) {
			if (market.getPlanetEntity() != null) {
				return market.getPlanetEntity();
			}
			return market.getPrimaryEntity();
		} else {
			return Global.getSector().getEntityById(id);
		}
	}
	
	public armaa_reprisalIntel(SectorEntityToken entity) {
		this.entity = entity;
	}
	
	protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode) {
		
		Color h = Misc.getHighlightColor();
		Color g = Misc.getGrayColor();
		float pad = 3f;
		float opad = 10f;
		
		float initPad = pad;
		if (mode == ListInfoMode.IN_DESC) initPad = opad;
		
		Color tc = getBulletColorForMode(mode);
		
		bullet(info);
		boolean isUpdate = getListInfoParam() != null;
						
		if (mode == ListInfoMode.INTEL) {
			// not sure if this is needed
			//info.addPara("In the " + entity.getContainingLocation().getNameWithLowercaseType(), tc, 0f);
		}
		
		unindent(info);
	}
	
	@Override
	public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
		String pre = "";
		String post = "";
		if (mode == ListInfoMode.MESSAGES) {
			pre = "Discovered: ";
		}
		if (mode == ListInfoMode.INTEL) {
		}
		
		Color c = getTitleColor(mode);
		info.addPara(pre + getName() + post, c, 0f);
		addBulletPoints(info, mode);
	}
	
	@Override
	public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
		Color h = Misc.getHighlightColor();
		Color g = Misc.getGrayColor();
		Color tc = Misc.getTextColor();
		float pad = 3f;
		float opad = 10f;
		
		String id = entity.getId();
		if (entity.getMarket() != null && !entity.getMarket().isPlanetConditionMarketOnly()) {
			id = entity.getMarket().getId();
		}
		
		info.addImage(Global.getSettings().getSpriteName("illustrations", "armaa_harry"), width, opad);
		
		Description desc = Global.getSettings().getDescription("armaa_liberationDesc", Type.CUSTOM);
		info.addPara(desc.getText1(), opad);
		
		boolean completedAtmoBattle = false;
		if(Global.getSector().getMemoryWithoutUpdate().get("$armaa_WFCompletedAtmoBattle") != null)
			completedAtmoBattle = true;
		if (completedAtmoBattle) {
		//TextPanelAPI panel = new TextPanelAPI();
		info.addPara("Forces have secured an area of operations on the surface of " + entity.getName() + ". You should return in 15 days for more work.",opad,Color.red, "15");
		sendUpdateIfPlayerHasIntel(getListInfoParam(),false);		
		}
		
		//addShowShrinesButton(this, width, height, info);		
	}

	@Override
	public String getIcon() {
		return Global.getSettings().getSpriteName("intel", "luddic_shrine");
	}
	
	@Override
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		tags.add("Jenius Ops");
		return tags;
	}
	
	public String getSortString() {
		return getName();
	}
	
	public String getName() {

		return "Liberation - " + entity.getName();
	}
	
	@Override
	public FactionAPI getFactionForUIColors() {
		return Global.getSector().getFaction(Factions.LUDDIC_CHURCH);
	}

	public String getSmallDescriptionTitle() {
		//return getName() + " - " + entity.getContainingLocation().getNameWithTypeShort();
		return getName();
	}

	@Override
	public SectorEntityToken getMapLocation(SectorMapAPI map) {
		return entity;
	}
	
	@Override
	public boolean shouldRemoveIntel() 
	{
            return Global.getSector().getMemoryWithoutUpdate().get("$armaa_WFCompletedAtmoBattle") != null;
	}

	@Override
	public String getCommMessageSound() {
		return "ui_discovered_entity";
	}

	public SectorEntityToken getEntity() {
		return entity;
	}
	
	public static boolean startExpedition(MarketAPI source, MarketAPI target,Random random) {

		GenericRaidParams params = new GenericRaidParams(new Random(random.nextLong()), true);
		params.factionId = source.getFactionId();
		params.source = source;
		
		params.prepDays = 7f + random.nextFloat() * 14f;
		params.payloadDays = 180f;
		
		params.makeFleetsHostile = false;
		
		FGBlockadeParams bParams = new FGBlockadeParams();
		bParams.where = target.getStarSystem();
		bParams.targetFaction = target.getFactionId();
		bParams.specificMarket = target;
		
		params.noun = "liberation";
		params.forcesNoun = "Mercenary forces";
		
		params.style = FleetStyle.STANDARD;
		
		
		params.fleetSizes.add(8); // first size 10 pick becomes the Armada
		
		// and a few smaller picket forces
		params.fleetSizes.add(4);
		params.fleetSizes.add(4);
		params.fleetSizes.add(3);
		params.fleetSizes.add(3);

		
		armaa_mrcExpedition blockade = new armaa_mrcExpedition(params, bParams);
		//blockade.setListener(this);
		Global.getSector().getIntelManager().addIntel(blockade);
		
		return true;
	}

	public static boolean startExpedition(MarketAPI source,String expeditionFactionId, MarketAPI target,Random random) {

		GenericRaidParams params = new GenericRaidParams(new Random(random.nextLong()), true);
		params.factionId = expeditionFactionId;
		params.source = source;
		
		params.prepDays = 7f + random.nextFloat() * 14f;
		params.payloadDays = 180f;
		
		params.makeFleetsHostile = false;
		
		FGBlockadeParams bParams = new FGBlockadeParams();
		bParams.where = target.getStarSystem();
		bParams.targetFaction = target.getFactionId();
		bParams.specificMarket = target;
		
		params.noun = "reprisal";
		params.forcesNoun = "reprisal forces";
		
		params.style = FleetStyle.STANDARD;
		float enemyStr = WarSimScript.getEnemyStrength(target.getFaction(), target.getStarSystem(), false);		
		enemyStr +=  WarSimScript.getStationStrength(target.getFaction(), target.getStarSystem(), target.getPrimaryEntity());
		for (MarketAPI market : Misc.getMarketsInLocation(target.getContainingLocation()))
			enemyStr = enemyStr + WarSimScript.getStationStrength(market.getFaction(), market.getStarSystem(), market.getPrimaryEntity());
		
		double strF = (Math.random()*enemyStr*1.25f)/25f;
		int str = (int)strF;
		System.out.println(enemyStr + " vs " + str + " vs " +WarSimScript.getEnemyStrength(target.getFaction(), target.getStarSystem(), false));
		int fleetStr = Math.max((int)(Math.random()*6), 2);
		params.fleetSizes.add(5);
		str-=5;
		for(int i = str; i > 0; i-=fleetStr)
		{
			params.fleetSizes.add(fleetStr);                    
			fleetStr = Math.max(i-fleetStr, 2);
		}	
		armaa_mrcExpedition blockade = new armaa_mrcExpedition(params, bParams);
		//blockade.setListener(this);
		Global.getSector().getIntelManager().addIntel(blockade);
		
		return true;
	}		

}
