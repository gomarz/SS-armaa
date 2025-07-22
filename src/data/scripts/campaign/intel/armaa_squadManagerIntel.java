package data.scripts.campaign.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.characters.FullName.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.impl.campaign.ids.Stats;

import data.scripts.campaign.intel.armaa_promoteWingman;

import org.apache.log4j.Logger;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class armaa_squadManagerIntel extends BaseIntelPlugin {
	public static Logger log = Global.getLogger(armaa_squadManagerIntel.class);
	
	public static final boolean DISPLAY_ONLY = false;
	public static final Tab BUTTON_FLEET = Tab.FLEET;
	public static final Tab BUTTON_PORTRAIT = Tab.PORTRAIT;
	public static final Tab BUTTON_HELP = Tab.HELP;
	public static final String BUTTON_INSURE_ALL = "btn_insureAll";
	
	protected transient Tab currentTab;
	protected PersonAPI member;
	protected int pilot;
	
	protected IntervalUtil updateInterval = new IntervalUtil(0.25f, 0.25f);
		
	public armaa_squadManagerIntel() {
		Global.getSector().getIntelManager().addIntel(this);
		Global.getSector().addScript(this);
		this.setImportant(true);
	}

	public List<FleetMemberAPI> getAllShips() {
		return Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy();
	}
	
	public float getCredits() {
		return 1;
	}

	@Override
	public String getSmallDescriptionTitle() {
		return "getName";
	}

	protected String getName() {
		if (listInfoParam != null && listInfoParam instanceof List)
			return "titleV2Expire";
		return "WINGCOM Management";
	}
	
	@Override
	public boolean hasSmallDescription() {
		return false;
	}

	@Override
	public boolean hasLargeDescription() { 
		return true;
	}
	
	@Override
	public boolean isPlayerVisible() {
		return true;
	}
	
	public static int TAB_BUTTON_HEIGHT = 20;
	public static int TAB_BUTTON_WIDTH = 180;
	public static int ENTRY_HEIGHT = 80;
	public static int ENTRY_WIDTH = 300;
	public static int IMAGE_WIDTH = 80;
	public static int MANAGE_BUTTON_WIDTH = 120;
	public static int IMAGE_DESC_GAP = 12;

	public TooltipMakerAPI createPersonImageForPanel(CustomPanelAPI panel, 
			PersonAPI person, float width, float height) 
	{
		TooltipMakerAPI image = panel.createUIElement(width, height, false);
		image.addImage(person.getPortraitSprite(), height, 0);
		return image;
	}
	public TooltipMakerAPI createFleetMemberImageForPanel(CustomPanelAPI panel, 
			FleetMemberAPI member, float width, float height) 
	{
		TooltipMakerAPI image = panel.createUIElement(width, height, false);
		
		//addSingleShipList(image, width, member, 0);
		return image;
	}
	
	/**
	 * Generates an image of a ship or officer on the left-hand side of the screen. 
	 * @param panel
	 * @param member Takes priority over {@code officer}.
	 * @param officer
	 * @return 
	 */
	protected TooltipMakerAPI generateImage(CustomPanelAPI panel, FleetMemberAPI member, OfficerDataAPI officer,PersonAPI person) 
	{
		if(member == null && officer == null)
		{
			return createPersonImageForPanel(panel, person, IMAGE_WIDTH, ENTRY_HEIGHT);
		}
		if (member != null) {
			return createFleetMemberImageForPanel(panel, member, IMAGE_WIDTH, ENTRY_HEIGHT);
		} else {
			return createPersonImageForPanel(panel, officer.getPerson(), IMAGE_WIDTH, ENTRY_HEIGHT);
		}
	}

	protected void createFleetView(CustomPanelAPI panel, TooltipMakerAPI info, float width) 
	{
		float pad = 3;
		float opad = 10;
		Color h = Misc.getHighlightColor();
		CampaignFleetAPI player = Global.getSector().getPlayerFleet();
		if (player == null) return;
		
		PersonAPI playerChar = player.getFleetData().getCommander();
		boolean addPlayer = false;
		if(Global.getSector().getPersistentData().get("armaa_wingCommander_squadronName_"+playerChar.getId()) instanceof String)
			addPlayer =true;
			
		List<PersonAPI> officers = new ArrayList();
		for (OfficerDataAPI member : player.getFleetData().getOfficersCopy()) 
		{
			if(Global.getSector().getPersistentData().get("armaa_wingCommander_squadronName_"+member.getPerson().getId()) instanceof String == false)
				continue;
			if(player.getFleetData().getMemberWithCaptain(member.getPerson()) == null)
				continue;
			
			FleetMemberAPI assignedShip = player.getFleetData().getMemberWithCaptain(member.getPerson());
			if(!assignedShip.getVariant().hasHullMod("armaa_wingCommander"))
				continue;
			if(Global.getSector().getPersistentData().get("armaa_wingCommander_squadronName_"+member.getPerson().getId()) instanceof String)
				officers.add(member.getPerson());
		}
		if(addPlayer)
			officers.add(0,playerChar);
		
		float heightPerItem = ENTRY_HEIGHT + opad;
		int numItems = officers.size();
		float itemPanelHeight = heightPerItem * numItems;
		CustomPanelAPI itemPanel = panel.createCustomPanel(ENTRY_WIDTH, itemPanelHeight, null);
		float yPos = opad;
		
		boolean noSquads = true;			
		for (PersonAPI member : officers) 
		{
			TooltipMakerAPI image = generateImage(itemPanel, null, null,member);
			itemPanel.addUIElement(image).inTL(4, yPos);
			
			TooltipMakerAPI entry = itemPanel.createUIElement(width - IMAGE_WIDTH - IMAGE_DESC_GAP,
					ENTRY_HEIGHT, true);
			entry.addPara("Name: " + member.getName().getFullName(), opad,h,member.getName().getFullName());
			entry.addPara("Rank: " + member.getRank(),pad, h,member.getRank());			
			boolean hasSquadron = Global.getSector().getPersistentData().get("armaa_wingCommander_squadronName_"+member.getId()) instanceof String ? true:false;

			if (hasSquadron) 
			{
				noSquads = false;
				String amount = (String)Global.getSector().getPersistentData().get("armaa_wingCommander_squadronName_"+member.getId());;
				entry.addPara("Commanding: " + amount, pad, h, amount);

				itemPanel.addUIElement(entry).rightOfTop(image, IMAGE_DESC_GAP);
				
				TooltipMakerAPI buttonHolder = itemPanel.createUIElement(MANAGE_BUTTON_WIDTH, 16, false);
				String name = "Manage";
				ButtonAPI manage = buttonHolder.addButton(name, member, 120, 16, 0);
				itemPanel.addUIElement(buttonHolder).inTL(4 + IMAGE_WIDTH + IMAGE_DESC_GAP + ENTRY_WIDTH, yPos+ENTRY_HEIGHT/4);
				
				yPos += ENTRY_HEIGHT + opad;				
			} else 
			{
				continue;	
			}
			//itemPanel.addUIElement(entry).inTL(4 + IMAGE_WIDTH + IMAGE_DESC_GAP, yPos);

		}
		if(noSquads)
		{
			TooltipMakerAPI entry = itemPanel.createUIElement(width - IMAGE_WIDTH - IMAGE_DESC_GAP,
					ENTRY_HEIGHT, true);
			TooltipMakerAPI image2 = generateImage(itemPanel, null, null,playerChar);
			itemPanel.addUIElement(image2).inTL(4, yPos);
			entry.addPara("No squadrons of any merit has been established.", pad, h);
			entry.addPara("Once officers have been %s, their squadrons can be managed from here.", pad, h,"assigned to a unit with WINGCOM installed");
			itemPanel.addUIElement(entry).rightOfTop(image2, IMAGE_DESC_GAP);

		}
		info.addCustom(itemPanel, 0);
		member = null;
	}
	
	protected void createSquadView(CustomPanelAPI panel, TooltipMakerAPI info, float width, PersonAPI member) {
		float pad = 3;
		float opad = 10;
		Color h = Misc.getHighlightColor();
		PersonAPI squadLeader = member;
		if (squadLeader == null || squadLeader.getId() == null) return;
		
		float heightPerItem = ENTRY_HEIGHT + ENTRY_HEIGHT/2 + opad;
		int numItems = 0;
		if(Global.getSector().getPersistentData().get("armaa_wingCommander_squadSize_"+squadLeader.getId()) != null)
		numItems = 	(int)Global.getSector().getPersistentData().get("armaa_wingCommander_squadSize_"+squadLeader.getId());
		float itemPanelHeight = heightPerItem * numItems;
		CustomPanelAPI itemPanel = panel.createCustomPanel(ENTRY_WIDTH, itemPanelHeight, null);
		float yPos = opad;
		boolean addPlayer = false;
		boolean noSquads = true;
		List<Object> squadParams = new ArrayList<>();
	    TooltipMakerAPI squadNameInputHolder = itemPanel.createUIElement(MANAGE_BUTTON_WIDTH, 16, false);
		TextFieldAPI squadName = info.addTextField(200f,pad);
		squadNameInputHolder.addButton("Rename Squadron" + numItems, squadParams, 160, 20, opad);
		squadParams.add(member);
		squadParams.add(squadName);
	    itemPanel.addUIElement(squadNameInputHolder).inTL(160*2-200/2 +opad, -32);
		MutableCharacterStatsAPI playerStats = Global.getSector().getPlayerFleet().getFleetData().getCommander().getStats();
		int maxElite = (int)Global.getSettings().getInt("officerMaxEliteSkills")+(int)playerStats.getDynamic().getMod(Stats.OFFICER_MAX_ELITE_SKILLS_MOD).computeEffective(0);
		int maxLevel = (int)playerStats.getDynamic().getMod(Stats.OFFICER_MAX_LEVEL_MOD).computeEffective(0)+(int)Misc.MAX_OFFICER_LEVEL;
		for( int i = 0; i < numItems; i++) 
		{
			PersonAPI pilot = (PersonAPI)Global.getSector().getPersistentData().get("armaa_wingCommander_wingman_"+i+"_"+squadLeader.getId());
			if(pilot == null)
				continue;
			
			TooltipMakerAPI image = generateImage(itemPanel, null, null,pilot);
			itemPanel.addUIElement(image).inTL(4, yPos);
			
			TooltipMakerAPI entry = itemPanel.createUIElement(width - IMAGE_WIDTH - IMAGE_DESC_GAP,
					heightPerItem-opad, true);
			entry.addPara("Name: "+pilot.getName().getFullName(),pad,h,pilot.getName().getFullName());

			String callsign = (String)Global.getSector().getPersistentData().get("armaa_wingCommander_wingman_"+i+"_"+"callsign_"+squadLeader.getId());
			//String text = getFormattedDateString(getString("entryDescAmount"), policy.date);
			entry.addPara("Callsign: "+ callsign, pad, h, callsign);
			
			String level = String.valueOf(pilot.getStats().getLevel());
			if(maxLevel < pilot.getStats().getLevel())
				maxLevel = pilot.getStats().getLevel();
			entry.addPara("Level: " + level + " / " + maxLevel,pad,h,level);
			entry.addRelationshipBar(pilot,5f);
			itemPanel.addUIElement(entry).rightOfTop(image, IMAGE_DESC_GAP);
			MutableCharacterStatsAPI stats = pilot.getStats();
			List<MutableCharacterStatsAPI.SkillLevelAPI> skillList = new ArrayList(stats.getSkillsCopy());
		
			TooltipMakerAPI skills = itemPanel.createUIElement(MANAGE_BUTTON_WIDTH+opad*2, heightPerItem-opad, true);
			skills.addPara("Skills: " +Misc.getNumEliteSkills(pilot)+ " / " + maxElite + " Elite",pad,h,""+Misc.getNumEliteSkills(pilot));
			for(MutableCharacterStatsAPI.SkillLevelAPI skill:skillList)
			{
				if(skill.getLevel() <= 0)
					continue;
				String skillStr = skill.getSkill().getName();
				if(skill.getLevel() >= 2)
					skillStr+="+";
				skills.addPara(skillStr,pad,Misc.getPositiveHighlightColor(),skillStr);				
			}
			itemPanel.addUIElement(skills).inTL(4 + IMAGE_WIDTH + IMAGE_DESC_GAP + ENTRY_WIDTH/2+opad, yPos);
			TooltipMakerAPI nameHolder = itemPanel.createUIElement(MANAGE_BUTTON_WIDTH, 16, false);
			TextFieldAPI nameChange = nameHolder.addTextField(200f,pad);
			itemPanel.addUIElement(nameHolder).inTL(4 + IMAGE_WIDTH + IMAGE_DESC_GAP + ENTRY_WIDTH+opad*2, yPos);
			
			List<Object> params = new ArrayList<>();
			params.add(member);
			params.add(i);
			params.add(nameChange);
			
			TooltipMakerAPI buttonHolder = itemPanel.createUIElement(MANAGE_BUTTON_WIDTH, 16, false);
			String name = "Change Callsign";
			ButtonAPI manage = buttonHolder.addButton(name, params, 120, 20, 0);
			itemPanel.addUIElement(buttonHolder).inTL(4 + IMAGE_WIDTH + IMAGE_DESC_GAP + ENTRY_WIDTH+(200f-120f)/2+opad*2, yPos+40f);
			
			List<Object> params2 = new ArrayList<>();
			params2.add(member);
			params.add(i);
			TooltipMakerAPI image2 = itemPanel.createUIElement(IMAGE_WIDTH, ENTRY_HEIGHT, false);
		    image2.addImage("graphics/portraits/portrait_generic_grayscale.png", ENTRY_HEIGHT+opad, 0);			
			//TooltipMakerAPI image2 = generateImage(itemPanel, null, null,pilot);
			//Why are you 10px higher than the other image

			itemPanel.addUIElement(image2).inTL(4+ IMAGE_WIDTH + IMAGE_DESC_GAP + ENTRY_WIDTH*2+(120f-IMAGE_WIDTH)/2+opad*2, yPos);
			TooltipMakerAPI buttonHolder2 = itemPanel.createUIElement(MANAGE_BUTTON_WIDTH, 16, false);
			String portrait = "Change Portrait";
			ButtonAPI appearance = buttonHolder2.addButton(portrait, i, 120, 20, 0);
			itemPanel.addUIElement(buttonHolder2).inTL((4 + IMAGE_WIDTH + IMAGE_DESC_GAP + ENTRY_WIDTH*2+opad)+opad*2, yPos+40f);

			if(pilot.getRelToPlayer().getRel() >= .70f)
			{
				boolean alreadyConsidered = false;
				if(Global.getSector().getIntelManager().getIntel(armaa_promoteWingman.class) != null)
				{
					int size = Global.getSector().getIntelManager().getIntel(armaa_promoteWingman.class).size();
					for(int j = 0; j < size; j++)
					{
						armaa_promoteWingman pastIntel = (armaa_promoteWingman)Global.getSector().getIntelManager().getIntel(armaa_promoteWingman.class).get(j);
						if(pastIntel.getCandidate().getId().equals(pilot.getId()));
							alreadyConsidered = true;
					}
				}
				if(!alreadyConsidered)
				{
					params = new ArrayList<>();
					params.add(member);
					params.add(i);
					params.add("promote");
					
					TooltipMakerAPI buttonHolder3 = itemPanel.createUIElement(MANAGE_BUTTON_WIDTH, 16, false);
					name = "Promote";
					ButtonAPI promote = buttonHolder3.addButton(name, params, 120, 20, 0);
					itemPanel.addUIElement(buttonHolder3).inTL(4 + IMAGE_WIDTH + IMAGE_DESC_GAP + ENTRY_WIDTH+(200f-120f)/2+opad*2, yPos+70f);
				}
			}
							
			yPos += heightPerItem;				
		//itemPanel.addUIElement(entry).inTL(4 + IMAGE_WIDTH + IMAGE_DESC_GAP, yPos);
		}
		info.addCustom(itemPanel, 0);
		//member = null;
	}

	protected void createPortraitView(CustomPanelAPI panel, TooltipMakerAPI info, float width, PersonAPI member, int squadMember) {
		float pad = 3;
		float opad = 10;
		Color h = Misc.getHighlightColor();
		PersonAPI squadLeader = member;
		if (squadLeader == null) 
		{
			
			
		}
		PersonAPI pilot = (PersonAPI)Global.getSector().getPersistentData().get("armaa_wingCommander_wingman_"+squadMember+"_"+squadLeader.getId());
		if(pilot == null)
		{	
			
		}
		
		float heightPerItem = ENTRY_HEIGHT + opad;
		
		List<String> portraits = new ArrayList<>();

			//the dreaded nested for loop
			for(Gender gender : Gender.values())
			{
				//Get all portraits from commissioned faction and player faction
				List<String> portraitList = new ArrayList<>();
				if(Misc.getCommissionFaction() != null)
				{				
					portraitList.addAll(Misc.getCommissionFaction().getPortraits(gender).getItems());
				}
				
				portraitList.addAll(Global.getSector().getPlayerFaction().getPortraits(gender).getItems());
				
				for(String portrait:portraitList)
				{
					if(!portraits.contains(portrait))
						portraits.add(portrait);
				}
			}
		
		int numItems = 	(int)portraits.size();
		int cols = Global.getSettings().getScreenWidth() <1920 ? 9:12;
		int rows = (int)Math.ceil(numItems / (float)cols);
		float itemPanelHeight = rows*ENTRY_HEIGHT+(opad*rows);
		CustomPanelAPI itemPanel = panel.createCustomPanel(ENTRY_WIDTH*cols, itemPanelHeight, null);
		float yPos = opad;
		int li = 0;
		for(int r = 0; r < rows; r++)
		{
			float xPos = opad;
			for( int i = 0; i < cols; i++) 
			{
						log.info(li);

				if(li > portraits.size()-1)
					break;				
				//TooltipMakerAPI image = generateImage(itemPanel, null, null,squadLeader);
				TooltipMakerAPI image = itemPanel.createUIElement(IMAGE_WIDTH, ENTRY_HEIGHT, false);
				image.addImage(portraits.get(li), ENTRY_HEIGHT, 0);
				itemPanel.addUIElement(image).inTL(4+xPos, yPos);
				
				TooltipMakerAPI entry = itemPanel.createUIElement(width - IMAGE_WIDTH - IMAGE_DESC_GAP,
						ENTRY_HEIGHT+opad*i, true);

				String portraitChange = portraits.get(li);
				li++;
				List<Object> params = new ArrayList<>();
				params.add(member);
				params.add(squadMember);
				params.add(portraitChange);
				
				TooltipMakerAPI buttonHolder = itemPanel.createUIElement(MANAGE_BUTTON_WIDTH, 16, false);
				String name = "Select";
				ButtonAPI manage = buttonHolder.addButton(name, params, 60, 16, 0);
				itemPanel.addUIElement(buttonHolder).inTL(4 + xPos+(IMAGE_WIDTH-60)/2, yPos+IMAGE_WIDTH-opad);
				xPos+= IMAGE_WIDTH + opad*2;
			}
			yPos += ENTRY_HEIGHT + opad;
		}
		info.addCustom(itemPanel, 0);
		member = null;
	}
		
	protected void createHelpView(TooltipMakerAPI info) {
		float opad = 15;
		float pad = 3;
		Color h = Misc.getHighlightColor();
		
		TooltipMakerAPI help = info.beginImageWithText("graphics/icons/skills/best_of_the_best.png", 52f);	
		help.setParaInsigniaLarge(); 
		help.addPara("About WINGCOM", opad);
		help.setParaFontDefault();
		help.setBulletedListMode(BaseIntelPlugin.BULLET);
		help.addPara("When an officer is assigned to a unit with WINGCOM, the crew drafted for the squadron can be reviewed here.", pad);
		help.addPara("Squadron Members are generally drafted from the fleets best pilots, often %s than their compatriots.", pad, h, "possessing greater skills");
		help.addPara("By engaging in many battles, these squad members can eventually be %s.", pad, h,"promoted to full fledged officers");
		help.setBulletedListMode(BaseIntelPlugin.BULLET);
		UIPanelAPI temp =info.addImageWithText(pad);
		unindent(info);		
		TooltipMakerAPI help2 =info.beginImageWithText("graphics/icons/skills/crew_training.png", 82f);	
		help2.setParaInsigniaLarge();
		help2.addPara("Squad Enhancement", opad);
		help2.setParaFontDefault();
		help2.setBulletedListMode(BaseIntelPlugin.BULLET);
		help2.addPara("Squadrons combat performance will fluctuate based on two simple factors.", pad);
		help2.addPara("Winning battles will generally increase the squadrons performance by a random amount.", pad, h);
		help2.addPara("Once a pilot reaches their level cap, there is a small chance they can exceed it by one.", pad, h);		
		help2.addPara("If the squadron leader is shot down or forced to retreat, performance will suffer.", pad);
		help2.setBulletedListMode(BaseIntelPlugin.BULLET);
		help2.setParaInsigniaLarge();
		help2.addPara("Promoting Pilots", opad);		
		help2.setParaFontDefault();
		help2.setBulletedListMode(BaseIntelPlugin.BULLET);
		help2.addPara("Squad members can be promoted once their relationship exceeds %s points.", pad,h,"70");
		help2.addPara("If a squad member is promoted, a new pilot will fill in their vacant position.",pad);
		help2.addPara("You can also simply %s for promotion.",pad,h,"pass them over");	
		help2.setBulletedListMode(BaseIntelPlugin.BULLET);			
		unindent(info);				
		temp =info.addImageWithText(pad);
	}
	
	public TooltipMakerAPI generateTabButton(CustomPanelAPI buttonRow, String nameId, Tab tab,
			Color base, Color bg, Color bright, TooltipMakerAPI rightOf) 
	{
		TooltipMakerAPI holder = buttonRow.createUIElement(TAB_BUTTON_WIDTH, 
				TAB_BUTTON_HEIGHT, false);
		
		ButtonAPI button = holder.addAreaCheckbox(nameId, tab, base, bg, bright,
				TAB_BUTTON_WIDTH, TAB_BUTTON_HEIGHT, 0);
		button.setChecked(tab == this.currentTab);
		
		if (rightOf != null) {
			buttonRow.addUIElement(holder).rightOfTop(rightOf, 4);
		} else {
			buttonRow.addUIElement(holder).inTL(0, 3);
		}
		
		return holder;
	}
	
	protected TooltipMakerAPI addTabButtons(TooltipMakerAPI tm, CustomPanelAPI panel, float width) {
		
		CustomPanelAPI row = panel.createCustomPanel(width, TAB_BUTTON_HEIGHT, null);
		CustomPanelAPI spacer = panel.createCustomPanel(width, TAB_BUTTON_HEIGHT, null);
		FactionAPI fc = getFactionForUIColors();
		Color base = fc.getBaseUIColor(), bg = fc.getDarkUIColor(), bright = fc.getBrightUIColor();
				
		TooltipMakerAPI btnHolder1 = generateTabButton(panel, "Squadrons", BUTTON_FLEET, 
				base, bg, bright, null);		
		//TooltipMakerAPI btnHolder2 = generateTabButton(panel, "IDK what to put here", BUTTON_CLAIMS, 
		//		base, bg, bright, btnHolder1);
		TooltipMakerAPI btnHolder3 = generateTabButton(panel, "Info", BUTTON_HELP, 
				base, bg, bright, btnHolder1);
		
		return btnHolder1;
	}
	
	@Override
	public void createLargeDescription(CustomPanelAPI panel, float width, float height) {
		float opad = 10;
		float pad = 3;
		Color h = Misc.getHighlightColor();
		
		if (currentTab == null) currentTab = Tab.FLEET;
				
		TooltipMakerAPI info = panel.createUIElement(width, height - TAB_BUTTON_HEIGHT - 4, true);
		FactionAPI faction = Global.getSector().getPlayerFaction();
		
		TooltipMakerAPI buttonHolder = addTabButtons(info, panel, width);
		
		info.addSectionHeading("Squadron Management", faction.getBaseUIColor(), 
			faction.getDarkUIColor(), com.fs.starfarer.api.ui.Alignment.MID, opad);
					
		switch (currentTab) {
			case FLEET:
				createFleetView(panel, info, width);
				break;
			case HELP:
				createHelpView(info);
				break;
			case SQUAD:
				String squadName = (String)Global.getSector().getPersistentData().get("armaa_wingCommander_squadronName_"+member.getId());
				info.addPara("Squadron Details: " +squadName, opad, h, squadName);
				createSquadView(panel,info,width,member);
				break;
			case PORTRAIT:
				info.addPara("Change Appearance",opad);
				createPortraitView(panel,info,width,member,pilot);
				break;
		}
		
		panel.addUIElement(info).belowLeft(buttonHolder, pad);
	}
	
	@Override
	public void createConfirmationPrompt(Object buttonId, TooltipMakerAPI prompt) {
		if (buttonId == BUTTON_INSURE_ALL) {
			
		}
		
		if (buttonId instanceof FleetMemberAPI) {
			
		}
	}
	
	@Override
	public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
		if (buttonId instanceof Tab) 
		{
			currentTab = (Tab)buttonId;
			ui.updateUIForItem(this);
			return;
		}
		if (buttonId == BUTTON_INSURE_ALL) 
		{
			ui.updateUIForItem(this);
			return;
		}
		if (buttonId instanceof PersonAPI) 
		{
			PersonAPI officer = (PersonAPI)buttonId;
			member = officer;
			currentTab = Tab.SQUAD;
			ui.updateUIForItem(this);
			return;
		}
		if (buttonId instanceof Integer && currentTab == Tab.SQUAD) 
		{
			pilot = (int)buttonId;
			currentTab = Tab.PORTRAIT;
			ui.updateUIForItem(this);
			return;
		}
		
		if(buttonId instanceof List)
		{ 
			List<Object> params = (ArrayList)buttonId;
			if(params.size() < 3)
			{
				PersonAPI squadLeader = (PersonAPI)params.get(0);
				TextFieldAPI newName = (TextFieldAPI)params.get(1);
				Global.getSector().getPersistentData().put("armaa_wingCommander_squadronName_"+squadLeader.getId(),newName.getText());				
			}
			else
			{
				PersonAPI member = (PersonAPI)params.get(0);
				int index = (int)params.get(1);
				if(currentTab == Tab.PORTRAIT)
				{
					PersonAPI pilot = (PersonAPI)Global.getSector().getPersistentData().get("armaa_wingCommander_wingman_"+index+"_"+member.getId());
					pilot.setPortraitSprite((String)params.get(2));
					Global.getSector().getPersistentData().put("armaa_wingCommander_wingman_"+index+"_"+member.getId(),pilot);
				}
				else if(String.valueOf(params.get(2)).equals("promote"))
				{
					CampaignFleetAPI player = Global.getSector().getPlayerFleet();
					FleetMemberAPI lead = player.getFleetData().getMemberWithCaptain(member);
					armaa_promoteWingman intel = new armaa_promoteWingman(null,lead,index);
					Global.getSector().getIntelManager().addIntel(intel);
					ui.updateIntelList(); 					
				}
				else
				{
					TextFieldAPI text =(TextFieldAPI)params.get(2);
					Global.getSector().getPersistentData().put("armaa_wingCommander_wingman_"+index+"_"+"callsign_"+member.getId(),text.getText());
				}
			}
			currentTab = Tab.SQUAD;
			ui.updateUIForItem(this);
			return;
		}
	}
	
	@Override
	public boolean doesButtonHaveConfirmDialog(Object buttonId) {
		if (buttonId == BUTTON_INSURE_ALL) return true;
		
		if (buttonId instanceof FleetMemberAPI) {

				return true;
		}
		
		return false;
	}
	
	@Override
	public String getConfirmText(Object buttonId) {
		return super.getConfirmText(buttonId);
	}
	
	@Override
	public String getCancelText(Object buttonId) {	
		return super.getCancelText(buttonId);
	}

	@Override
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		tags.add("Squadron Manager");
		return tags;
	}

	@Override
	public String getIcon() {
		return ("graphics/icons/markets/headquarters.png");
	}
	
	@Override
	public boolean isHidden() {
		return false;
	}

	@Override
	protected void notifyEnded() {
		Global.getSector().getIntelManager().removeIntel(this);
		Global.getSector().removeScript(this);
	}
	
	protected static String getString(String id) {
		return "GetString";
	}

	public static enum Tab {
		FLEET, PORTRAIT, HELP, SQUAD
	}
}
