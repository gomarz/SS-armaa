package data.scripts;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;

import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.CampaignPlugin;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.combat.MissileAIPlugin;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.campaign.PersonImportance;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;

import com.fs.starfarer.api.characters.OfficerDataAPI;

import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.impl.campaign.intel.contacts.ContactIntel;
import com.fs.starfarer.api.impl.campaign.intel.contacts.ContactIntel.ContactState;

import com.fs.starfarer.api.impl.campaign.ids.Tags;

import org.dark.shaders.light.LightData;
import org.dark.shaders.util.ShaderLib;
import org.dark.shaders.util.TextureData;
import data.scripts.campaign.armaa_raid;
import org.magiclib.util.MagicSettings;
import data.scripts.ai.armaa_spikeAI;
import data.scripts.ai.armaa_harpoonAI;
import data.scripts.ai.armaa_grenadeAI;
import data.scripts.ai.armaa_dispersalMortarAI;
import data.scripts.ai.armaa_curvyLaserAI;
import data.scripts.ai.armaa_armorPodAI;
import data.scripts.world.ARMAAWorldGen;
import data.scripts.campaign.notifications.armaa_notificationShower;
import data.hullmods.cataphract;

import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent;


import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Skills;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import data.scripts.campaign.armaa_wingmanPromotion;
import data.scripts.campaign.armaa_skyMindBattleResultListener;
import data.scripts.campaign.armaa_drugsAreBad;
import data.scripts.campaign.armaa_lootCleaner;
import data.scripts.campaign.armaa_hyperSpaceImmunity;
import data.scripts.campaign.armaa_mrcReprisalListener;
import data.scripts.campaign.intel.armaa_squadManagerIntel;
import data.scripts.fleets.ArmaaFleetManager;
import com.fs.starfarer.api.campaign.SectorEntityToken;

import java.util.*;
import lunalib.lunaSettings.LunaSettings;

//import exerelin.campaign.SectorManager;

public class MechaModPlugin extends BaseModPlugin {


	public static final String spike_ID = "armaa_spike_rod";
	public static final String harpoon_ID = "armaa_harpoon_rod";
	public static final String grenade_ID = "armaa_grenade_shot";
	public static final String mortar_ID = "armaa_mortar_shot";
	public static final String laser_ID = "armaa_curvylaser_mirv";
	public static final String pod_ID = "armaa_armorPod_missile";	
	public static List<String> KARMA_IMMUNE = new ArrayList<>();
	
	//Wingcom schiz
	public static List<String> squadNames = new ArrayList<>();
	public static List<String> squadChatter = new ArrayList<>();
	public static List<String> squadChatter_soldier = new ArrayList<>();
	public static List<String> squadChatter_villain = new ArrayList<>();
	
	public static List<String> squadChatter_alpha = new ArrayList<>();
	public static List<String> squadChatter_beta = new ArrayList<>();
	public static List<String> squadChatter_gamma = new ArrayList<>();
		
	public static List<String> combatChatter = new ArrayList<>();
	public static List<String> introChatter = new ArrayList<>();
	public static Map<String,List<String>> introChatter_special = new HashMap<>();
	public static boolean chatterEnabled = true;
	public static List<String> intersquadChatter_statement = new ArrayList<>();
	public static List<String> intersquadChatter_response = new ArrayList<>();	
	private static final Set<String> SHIPS_WITH_DIALOG = new HashSet<>();
    static
	{
		SHIPS_WITH_DIALOG.add("expsp_asdf1_frig");
		SHIPS_WITH_DIALOG.add("tahlan_Nightingale");
		SHIPS_WITH_DIALOG.add("guardian");	
    }
	
	public static List<String> varrifle_weapons = new ArrayList<>();	
	public static Map<String,String> SPECIAL_WING_DIALG = new HashMap<>();
	public static Map<String,Float> MISSILE_REFIT_MALUS = new HashMap<>();
	public static Map<String,Float> HULLMOD_REFIT_MALUS = new HashMap<>();		
	public static Map<String,Float> GROUND_SUPPORT_CUSTOM = new HashMap<>();	
	
	private armaa_wingmanPromotion script;
	private armaa_drugsAreBad copiumScript;
	private armaa_hyperSpaceImmunity hyperScript;
	private armaa_mrcReprisalListener reprisalScript;	
	private armaa_skyMindBattleResultListener resultListener;
	private armaa_notificationShower notificationScript;
   
    @Override
    public void onNewGame() {
        boolean haveNexerelin = Global.getSettings().getModManager().isModEnabled("nexerelin");
            new ARMAAWorldGen().generate(Global.getSector());
    }

 @Override
    public PluginPick<MissileAIPlugin> pickMissileAI(MissileAPI missile, ShipAPI launchingShip)    {
        switch (missile.getProjectileSpecId()) {
            case spike_ID:
                return new PluginPick<MissileAIPlugin>(new armaa_spikeAI(missile, launchingShip), CampaignPlugin.PickPriority.MOD_SPECIFIC);
            case harpoon_ID:
                return new PluginPick<MissileAIPlugin>(new armaa_harpoonAI(missile, launchingShip), CampaignPlugin.PickPriority.MOD_SPECIFIC);
            case grenade_ID:
                return new PluginPick<MissileAIPlugin>(new armaa_grenadeAI(missile, launchingShip), CampaignPlugin.PickPriority.MOD_SPECIFIC);
            case mortar_ID:
                return new PluginPick<MissileAIPlugin>(new armaa_dispersalMortarAI(missile, launchingShip), CampaignPlugin.PickPriority.MOD_SPECIFIC);
            case laser_ID:
                return new PluginPick<MissileAIPlugin>(new armaa_curvyLaserAI(missile, launchingShip), CampaignPlugin.PickPriority.MOD_SPECIFIC);
            case pod_ID:
                return new PluginPick<MissileAIPlugin>(new armaa_armorPodAI(missile, launchingShip), CampaignPlugin.PickPriority.MOD_SPECIFIC);
        }
        return null;
    }    


    @Override
	public void onApplicationLoad() throws ClassNotFoundException {  
            
        try {
            Global.getSettings().getScriptClassLoader().loadClass("org.lazywizard.lazylib.ModUtils");
        } catch (ClassNotFoundException ex) {
            String message = System.lineSeparator()
                    + System.lineSeparator() + "LazyLib is required to run at least one of the mods you have installed."
                    + System.lineSeparator() + System.lineSeparator()
                    + "You can download LazyLib at http://fractalsoftworks.com/forum/index.php?topic=5444"
                    + System.lineSeparator();
            throw new ClassNotFoundException(message);
        }
        
        try {
            Global.getSettings().getScriptClassLoader().loadClass("org.magiclib.util.MagicUIInternal");
        } catch (ClassNotFoundException ex) {
            String message = System.lineSeparator()
                    + System.lineSeparator() + "MagicLib is required to run at least one of the mods you have installed."
                    + System.lineSeparator() + System.lineSeparator()
                    + "You can download MagicLib at http://fractalsoftworks.com/forum/index.php?topic=13718.0"
                    + System.lineSeparator();
            throw new ClassNotFoundException(message);
        }
        
        try {  
            Global.getSettings().getScriptClassLoader().loadClass("org.dark.shaders.util.ShaderLib");  
            ShaderLib.init();  
            LightData.readLightDataCSV("data/lights/armaa_bling.csv"); 
            TextureData.readTextureDataCSV("data/lights/armaa_texture.csv"); 
        } catch (ClassNotFoundException ex) {
        }
		
       //modSettings loader:
	   
		KARMA_IMMUNE = MagicSettings.getList("armaarmatura", "missile_resist_karma");
		
		squadNames = MagicSettings.getList("armaarmatura", "strikecraft_squad_names");
		squadChatter = MagicSettings.getList("armaarmatura", "strikecraft_squad_chatter");
		squadChatter_soldier = MagicSettings.getList("armaarmatura", "strikecraft_squad_chatter_soldier");
		squadChatter_villain = MagicSettings.getList("armaarmatura", "strikecraft_squad_chatter_villain");
		
		//ai
		squadChatter_alpha = MagicSettings.getList("armaarmatura", "strikecraft_squad_chatter_alpha");
		squadChatter_beta = MagicSettings.getList("armaarmatura", "strikecraft_squad_chatter_beta");
		squadChatter_gamma = MagicSettings.getList("armaarmatura", "strikecraft_squad_chatter_gamma");		
		//
		
		introChatter = MagicSettings.getList("armaarmatura","strikecraft_squad_chatter_intro");
		if (Global.getSettings().getModManager().isModEnabled("lunalib"))
		{
			chatterEnabled = LunaSettings.getBoolean("armaa", "armaa_enableWingcom");
		}
		else
			chatterEnabled = MagicSettings.getBoolean("armaarmatura","wingcom_chatter");		
		
		introChatter_special.put("$gaTJ_completed",MagicSettings.getList("armaarmatura","$gaTJ_completed"));
		introChatter_special.put("$atrocities",MagicSettings.getList("armaarmatura","$atrocities"));
		introChatter_special.put("$metBaird",MagicSettings.getList("armaarmatura","$metBaird"));

		combatChatter = MagicSettings.getList("armaarmatura","strikecraft_squad_chatter_combat");
		
		intersquadChatter_statement = MagicSettings.getList("armaarmatura","strikecraft_squad_chatter_intersquad_statement");
		intersquadChatter_response = MagicSettings.getList("armaarmatura","strikecraft_squad_chatter_intersquad_response");
		
		SPECIAL_WING_DIALG = MagicSettings.getStringMap("armaarmatura","special_wing_lines");
		MISSILE_REFIT_MALUS = MagicSettings.getFloatMap("armaarmatura","missile_refit_custom");
	    HULLMOD_REFIT_MALUS = MagicSettings.getFloatMap("armaarmatura","hullmod_refit_custom");
	    GROUND_SUPPORT_CUSTOM = MagicSettings.getFloatMap("armaarmatura","ground_support_custom");

		cataphract.GROUND_BONUS.putAll(GROUND_SUPPORT_CUSTOM);		

		varrifle_weapons = MagicSettings.getList("armaarmatura","variablerifle_whitelist");
    }

    @Override
    public void beforeGameSave() 
	{
		//TODO: Why do I do this twice?
		Global.getSector().removeTransientScript(resultListener);
        Global.getSector().removeListener(resultListener);
		Global.getSector().removeTransientScript(script);
        Global.getSector().removeListener(script);
		Global.getSector().removeTransientScript(copiumScript);
        Global.getSector().removeListener(copiumScript);
		Global.getSector().removeTransientScript(hyperScript);
        Global.getSector().removeListener(hyperScript);
		Global.getSector().removeListener(reprisalScript);
		Global.getSector().removeTransientScript(notificationScript);	
    }
	
    @Override
    public void afterGameSave() 
	{
        Global.getSector().addTransientScript(script = new armaa_wingmanPromotion());
        Global.getSector().addTransientScript(resultListener = new armaa_skyMindBattleResultListener());
        Global.getSector().addTransientScript(copiumScript = new armaa_drugsAreBad());
		Global.getSector().addTransientScript(hyperScript = new armaa_hyperSpaceImmunity());
		Global.getSector().addTransientScript(reprisalScript = new armaa_mrcReprisalListener());
		Global.getSector().addTransientScript(notificationScript = new armaa_notificationShower());		
    }

    @Override
    public void onGameLoad(boolean newGame) 
	{
		boolean haveAnime = Global.getSettings().getModManager().isModEnabled("zzarmaa_anime");		
		boolean haveTahlan = Global.getSettings().getModManager().isModEnabled("tahlan");
		boolean havePAGSM = Global.getSettings().getModManager().isModEnabled("PAGSM");						

		Global.getSector().removeTransientScript(resultListener);;
		Global.getSector().removeTransientScript(script);
		Global.getSector().removeTransientScript(copiumScript);
		Global.getSector().removeTransientScript(hyperScript);
		Global.getSector().removeTransientScript(reprisalScript);
		Global.getSector().removeTransientScript(notificationScript);
		
		boolean hiredDawn = false;
		
		for(ShipHullSpecAPI spec :Global.getSettings().getAllShipHullSpecs())
		{
			if(spec.getHullSize() == HullSize.FIGHTER)
				continue;
			if(spec.hasTag("armaa_strikecraftize"))
				spec.addBuiltInMod("strikeCraft");
		}

        MarketAPI exsedol = Global.getSector().getEconomy().getMarket("exsedol_station_market");
		if(exsedol != null && !exsedol.hasSubmarket("armaa_mrcMarket"))
		{
			exsedol.addSubmarket("armaa_mrcMarket");
		}
		for(OfficerDataAPI officer: Global.getSector().getPlayerFleet().getFleetData().getOfficersCopy())
		{
			if(officer.getPerson().getId().equals("armaa_dawn"))
				hiredDawn = true;
		}
		
		if(hiredDawn)
		for (IntelInfoPlugin curr : Global.getSector().getIntelManager().getIntel(ContactIntel.class)) 
		{
			ContactIntel intel = (ContactIntel) curr;
			if (intel.isEnding() || intel.isEnded() || intel.getState() == ContactState.POTENTIAL) continue;
			
			int importance = intel.getPerson().getImportance().ordinal();
			float rel = intel.getPerson().getRelToPlayer().getRel();
			if(intel.getPerson().getId().equals("armaa_dawn"))
			{
				intel.loseContact(null);
			}
		}
		if (!Global.getSector().hasScript(ArmaaFleetManager.class))
		{
			Global.getSector().addScript(new ArmaaFleetManager());
		}
		Global.getSector().getListenerManager().addListener(new armaa_lootCleaner(), true);
		Global.getSector().getListenerManager().addListener(new armaa_raid(), true);
		
		Global.getSector().addTransientScript(script = new armaa_wingmanPromotion());
		Global.getSector().addTransientScript(resultListener = new armaa_skyMindBattleResultListener());
        Global.getSector().addTransientScript(copiumScript = new armaa_drugsAreBad());
		Global.getSector().addTransientScript(hyperScript = new armaa_hyperSpaceImmunity());
		Global.getSector().addTransientScript(reprisalScript = new armaa_mrcReprisalListener());		
		Global.getSector().addTransientScript(notificationScript = new armaa_notificationShower());
		Global.getSector().getPlayerPerson().getStats().setSkillLevel("armaa_cataphract", 1);
		
		if(Global.getSector().getImportantPeople().getPerson("armaa_dawn") != null)
		{
			PersonAPI secretary = Global.getSector().getImportantPeople().getPerson("armaa_dawn");
			if(haveAnime)
				secretary.setPortraitSprite("graphics/armaa/portraits/armaa_dawn2.png");
			else
				secretary.setPortraitSprite("graphics/armaa/portraits/armaa_dawn.png");				
			
		}

		if(Global.getSector().getImportantPeople().getPerson("armaa_seraph") != null)
		{
			PersonAPI secretary = Global.getSector().getImportantPeople().getPerson("armaa_seraph");
			if(haveAnime)
				secretary.setPortraitSprite("graphics/armaa/portraits/armaa_seraph_anim.png");
			else
				secretary.setPortraitSprite("graphics/armaa/portraits/armaa_seraph.png");				
			
		}
		
		if(Global.getSector().getImportantPeople().getPerson("armaa_kade") == null)
		{			   
			PersonAPI kade = Global.getFactory().createPerson();
			kade.addTag(Tags.CONTACT_UNDERWORLD);	
			kade.setFaction("pirates");
			kade.setGender(FullName.Gender.MALE);
			kade.getName().setFirst("Kade");
			kade.getName().setLast("Lennox");
			kade.setId("armaa_kade");
			//kade.getName().setLast("Pha");
			kade.setImportanceAndVoice(PersonImportance.HIGH, new Random());
			kade.setPortraitSprite("graphics/armaa/portraits/armaa_kade.png");			
			Global.getSector().getImportantPeople().addPerson(kade);					
		}
		PersonAPI kade = Global.getSector().getImportantPeople().getPerson("armaa_kade");		
		if(!kade.hasTag(Tags.CONTACT_TRADE))
		{
			kade.addTag(Tags.CONTACT_TRADE);
			kade.addTag(Tags.CONTACT_UNDERWORLD);	
			kade.setImportanceAndVoice(PersonImportance.MEDIUM, new Random());
		}
		
		if(Global.getSector().getImportantPeople().getPerson("armaa_ironking") == null)
		{			   
            PersonAPI ironKing = Global.getFactory().createPerson();
			ironKing.setFaction("domain");
			ironKing.setGender(FullName.Gender.FEMALE);
			ironKing.setPostId(Ranks.POST_ADMINISTRATOR);
			ironKing.setRankId(Ranks.CITIZEN);
            ironKing.getName().setFirst("IRON");
            ironKing.getName().setLast("KING");
			ironKing.setFaction("armaarmatura");
			ironKing.setImportanceAndVoice(PersonImportance.VERY_HIGH, new Random());
			ironKing.setPortraitSprite("graphics/armaa/portraits/armaa_ironking.png");
            ironKing.getStats().setSkillLevel(Skills.INDUSTRIAL_PLANNING, 1);
            ironKing.getStats().setSkillLevel(Skills.HYPERCOGNITION, 1);
			if(!ironKing.getId().equals("armaa_ironking"))
				ironKing.setId("armaa_ironking");
			ironKing.setAICoreId(Commodities.ALPHA_CORE);
			Global.getSector().getImportantPeople().addPerson(ironKing);
			ironKing.getMemoryWithoutUpdate().set(MemFlags.SUSPECTED_AI, true);					
		}	
		ShipHullSpecAPI spec = Global.getSettings().getHullSpec("armaa_legioMech");		
		spec.setShipSystemId("armaa_fullerAuto");	
		
		if(haveTahlan)
		{
			
			FactionAPI MRC = Global.getSector().getFaction("armaarmatura_pirates");
			if(!MRC.knowsShip(("tahlan_Castella_pirates")))
			{
				MRC.addKnownShip("tahlan_landsknecht",false);
				MRC.addKnownShip("tahlan_Tempest_P",false);			
				MRC.addKnownShip("tahlan_Bungalow",false);
				MRC.addKnownShip("tahlan_Kodai",false);
			}
			List<String> tahlanIds = new ArrayList<>();			
			tahlanIds.add("armaa_legioMech");
			tahlanIds.add("armaa_legioMech_default_D");	
			
			tahlanIds.add("armaa_legioMech_leftShoulder");
			tahlanIds.add("armaa_legioMech_rightShoulder");			
			for(String part : tahlanIds)
			{
				spec = Global.getSettings().getHullSpec(part);
				if(part.equals("armaa_legioMech"))
				{
					spec.setShipSystemId("armaa_fullerAuto");
					ShipVariantAPI variant = Global.getSettings().getVariant(part+"_standard");
					variant.getWing(0).setId("tahlan_miasma_drone_wing"); 
					
					variant.refreshBuiltInWings();					
				}
				if(!spec.getBuiltInMods().contains("tahlan_daemonarmor"))
				{
					ShipVariantAPI variant = Global.getSettings().getVariant(part+"_standard");	
					if(spec.getHullSize() != HullSize.FIGHTER)
					{
						if(variant != null)
							variant.addPermaMod("tahlan_daemoncore");					
						spec.addBuiltInMod("tahlan_daemoncore");
					}
					spec.addBuiltInMod("tahlan_daemonarmor");	
					if(variant != null)
						variant.addPermaMod("tahlan_daemonarmor");		
				}
			}
		}
		//find a better way to do this. MagicSettings may not be ideal
        for (FactionAPI factionAPI : Global.getSector().getAllFactions()){
            String str = factionAPI.getId();
            String id = factionAPI.getId();
            if (str.equals("sindrian_diktat") && havePAGSM)
                str += "_pagsm";      
			if(MagicSettings.getList("armaarmatura",str).size() > 0)
				introChatter_special.put(id, MagicSettings.getList("armaarmatura", str));
        }
        for (String tmp : SHIPS_WITH_DIALOG)
        {
			introChatter_special.put(tmp,MagicSettings.getList("armaarmatura",tmp));
        }

		if(!Global.getSector().getIntelManager().hasIntelOfClass(armaa_squadManagerIntel.class))
		{
			armaa_squadManagerIntel squadManager = new armaa_squadManagerIntel();
		}
		/*
		if(!Global.getSector().getIntelManager().hasIntelOfClass(ArmaArmaturaDeal.class))
		{
			ArmaArmaturaDeal deal = new ArmaArmaturaDeal(null);
		}
		*/		
		boolean hasValk = false;
		boolean check = false;
		if(Global.getSector().getPersistentData().get("armaa_superStart_valkazard?") instanceof Boolean)
		{
			check =  (Boolean)Global.getSector().getPersistentData().get("armaa_superStart_valkazard?");
		}
		if(!check)
		{			
			for(FleetMemberAPI f: Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy())
			{
				if(f.getHullId().equals("armaa_valkazard"))
				{
					hasValk= true;
					Global.getSector().getPersistentData().put("armaa_superStart_valkazard?",true);
					Global.getSector().getMemoryWithoutUpdate().set("$hasValk", true);
					break;
				}
			}
			
			if(hasValk && Global.getSector().getEntityById("valkazard") != null)
			{
				Global.getSector().getEntityById("valkazard").setExpired(true);
				Global.getSector().getEntityById("valkazard").setContainingLocation(null);
			}
		}
    }
    @Override
    public void onNewGameAfterEconomyLoad() 
	{
		boolean haveAnime = Global.getSettings().getModManager().isModEnabled("zzarmaa_anime");
		boolean haveNex = Global.getSettings().getModManager().isModEnabled("nexerelin");			
		if(haveNex && Global.getSector().getPlayerMemoryWithoutUpdate().get("$nex_startingFactionId").equals("armaarmatura_pirates"))
		{
			FleetParamsV3 fparams = new FleetParamsV3(
				Global.getSector().getEntityById("armaa_research_station").getLocationInHyperspace(),
				"tritachyon",
				null,
				FleetTypes.PATROL_SMALL,
				20f, // combatPts
				0f, // freighterPts 
				0f, // tankerPts
				0f, // transportPts
				0f, // linerPts
				0f, // utilityPts
				.5f // qualityMod
				);
			CampaignFleetAPI fleet =FleetFactoryV3.createFleet(fparams);
			SectorEntityToken loc = Global.getSector().getEntityById("armaa_research_station");
			// Spawn fleet around player
			loc.getContainingLocation().spawnFleet(
					loc, 25, 25, fleet);
			fleet.setLocation(loc.getLocation().x,loc.getLocation().y);
			fleet.setId("armaa_valkDefender");
			fleet.getFleetData().addFleetMember("armaa_garegga_tt_carrier_standard");	
			fleet.getFleetData().addFleetMember("armaa_garegga_tt_carrier_standard");
			fleet.getFleetData().addFleetMember("armaa_garegga_tt_carrier_standard");			
			fleet.addAssignment(FleetAssignment.ORBIT_AGGRESSIVE, loc, 500f);						
		}
        MarketAPI market = Global.getSector().getEconomy().getMarket("armaa_meshanii_market");
        if (market != null) 
		{
			Global.getSector().getEconomy().getMarket("armaa_meshanii_market").getMemoryWithoutUpdate().set("$musicSetId","music_armaa_market_neutral");
			Global.getSector().getStarSystem("Gamlin").getMemoryWithoutUpdate().set("$musicSetId","music_armaa_system");			
            PersonAPI admin = Global.getFactory().createPerson();
			admin.addTag(Tags.CONTACT_SCIENCE);
			admin.addTag(Tags.CONTACT_MILITARY);
			admin.addTag(Tags.CONTACT_UNDERWORLD);	
			admin.setFaction("armaarmatura");
			admin.setGender(FullName.Gender.FEMALE);
			admin.setPostId(Ranks.POST_ADMINISTRATOR);
			admin.setRankId(Ranks.CITIZEN);
            admin.getName().setFirst("Sera");
            admin.getName().setLast("Pha");
			admin.setImportanceAndVoice(PersonImportance.HIGH, new Random());
			if(haveAnime)
				admin.setPortraitSprite("graphics/armaa/portraits/armaa_seraph_anim.png");			
			else
				admin.setPortraitSprite("graphics/armaa/portraits/armaa_seraph.png");
            admin.getStats().setSkillLevel(Skills.INDUSTRIAL_PLANNING, 1);
            admin.getStats().setSkillLevel(Skills.HYPERCOGNITION, 1);
			if(!admin.getId().equals("armaa_seraph"))
				admin.setId("armaa_seraph");
			admin.setAICoreId(Commodities.ALPHA_CORE);
			admin.getMemoryWithoutUpdate().set(MemFlags.SUSPECTED_AI, true);			
            market.setAdmin(admin);
            market.getCommDirectory().addPerson(admin, 0);
            market.addPerson(admin);
			if(!Global.getSector().getImportantPeople().containsPerson(admin))
			{			   
				admin.getMemoryWithoutUpdate().set(MemFlags.SUSPECTED_AI, true);
				OfficerManagerEvent event = new OfficerManagerEvent();
				PersonAPI secretary = event.createOfficer(Global.getSector().getFaction("armaarmatura"),1, true);
				secretary.setRankId(Ranks.PILOT);
				secretary.setPostId(Ranks.POST_MERCENARY);
				if(haveAnime)
					secretary.setPortraitSprite("graphics/armaa/portraits/armaa_dawn2.png");
				else
					secretary.setPortraitSprite("graphics/armaa/portraits/armaa_dawn.png");			
				secretary.setFaction("armaarmatura");
				secretary.setGender(FullName.Gender.FEMALE);
				secretary.addTag(Tags.CONTACT_MILITARY);				
				secretary.getName().setFirst("Dawn");
				secretary.getName().setLast("Given-to-god");
				secretary.setId("armaa_dawn");
				secretary.setImportanceAndVoice(PersonImportance.LOW, new Random());
				secretary.setVoice(Voices.FAITHFUL);
				secretary.setPersonality("aggressive");
				OfficerManagerEvent.AvailableOfficer f = new OfficerManagerEvent.AvailableOfficer(secretary,"armaa_meshanii_market",4000,1000);
				secretary.getMemoryWithoutUpdate().set("$ome_hireable", true);
				secretary.getMemoryWithoutUpdate().set("$ome_eventRef", event);
				secretary.getMemoryWithoutUpdate().set("$ome_hiringBonus", Misc.getWithDGS(f.hiringBonus));		
				secretary.getMemoryWithoutUpdate().set("$ome_salary", Misc.getWithDGS(f.salary));
				event.addAvailable(f);
				market.getCommDirectory().addPerson(secretary, 99);
				market.getCommDirectory().getEntryForPerson(secretary).setHidden(true);				
				Global.getSector().getImportantPeople().addPerson(admin);
				Global.getSector().getImportantPeople().addPerson(secretary);
				if(haveNex && Global.getSector().getPlayerMemoryWithoutUpdate().get("$nex_startingFactionId").equals("armaarmatura"))
				{
					//ContactIntel.addPotentialContact(1f, secretary, admin.getMarket(), null);					
				}
			}
			for(PersonAPI secretary:market.getPeopleCopy())
			{
				if(secretary.getId().equals("armaa_dawn"))
				{
					secretary.getStats().setSkillLevel("armaa_AcePilot", 2);
					OfficerManagerEvent event = new OfficerManagerEvent();
					OfficerManagerEvent.AvailableOfficer f = new OfficerManagerEvent.AvailableOfficer(secretary,"armaa_meshanii_market",4000,1000);
					secretary.getMemoryWithoutUpdate().set("$ome_hireable", true);
					secretary.getMemoryWithoutUpdate().set("$ome_eventRef", event);
					secretary.getMemoryWithoutUpdate().set("$ome_hiringBonus", Misc.getWithDGS(f.hiringBonus));		
					secretary.getMemoryWithoutUpdate().set("$ome_salary", Misc.getWithDGS(f.salary));	
					event.addAvailable(f);
					
				}
			}			
		}
			Global.getSector().getEconomy().getMarket("armaa_meshanii_market").getMemoryWithoutUpdate().set("$musicSetId","music_armaa_market_neutral");

	}		
}