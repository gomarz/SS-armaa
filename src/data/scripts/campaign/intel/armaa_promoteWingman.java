package data.scripts.campaign.intel;

import java.awt.Color;
import java.util.Set;

import org.lwjgl.input.Keyboard;
import  com.fs.starfarer.api.impl.campaign.intel.*;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.StoryPointActionDelegate;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.characters.SkillSpecAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Sounds;
import com.fs.starfarer.api.impl.campaign.rulecmd.SetStoryOption.BaseOptionStoryPointActionDelegate;
import com.fs.starfarer.api.impl.campaign.rulecmd.SetStoryOption.StoryOptionParams;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import org.lazywizard.lazylib.MathUtils;

//sun.security.x509.X509CertImpl test;
public class armaa_promoteWingman extends BaseIntelPlugin {
	public static String BUTTON_PROMOTE = "button_promote";
	public static String BUTTON_DECLINE = "button_decline";
	public static float DURATION = 120f;
	
	protected PersonAPI person;
	protected int wingmanNo;
	protected PersonAPI captain;

	public armaa_promoteWingman(TextPanelAPI text, FleetMemberAPI member, int squadMember) {
		
		if (Global.getSector().getPlayerFleet().getCargo().getCrew() <= 0) {
			endImmediately();
			return;
		}
		captain = member.getCaptain();
		int wingSize = 0;
		
		//Random random = Misc.random;
		wingmanNo = squadMember;
		person = (PersonAPI)Global.getSector().getPersistentData().get("armaa_wingCommander_wingman_"+squadMember+"_"+captain.getId());
		if(person.getRelToPlayer().getRel() < .70f)
		{
			endImmediately();
			return;			
		}

		if (text != null) {
			text.addPara(getDescText());
		}
		
		setImportant(true);
	}
	
	public boolean shouldRemoveIntel() {
		if (Global.getSector().getPlayerFleet().getCargo().getCrew() <= 0) {
			return true;
		}
		
		float days = getDaysSincePlayerVisible();
		return isEnded() || days >= DURATION;
	}
	
	public String getName() {
		return "Officer Promotion Candidate";
	}
	
	public String getSmallDescriptionTitle() {
		return getName();
	}

	@Override
	public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
		Color c = getTitleColor(mode);
		info.addPara(getName(), c, 0f);
		addBulletPoints(info, mode);
	}
	
	protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode) {
		Color h = Misc.getHighlightColor();
		Color g = Misc.getGrayColor();
		Color tc = getBulletColorForMode(mode);
		
		float pad = 3f;
		float opad = 10f;
		
		float initPad = pad;
		if (mode == ListInfoMode.IN_DESC) initPad = opad;

		String pName = Misc.lcFirst(Misc.getPersonalityName(person));
		
		bullet(info);
		MutableCharacterStatsAPI stats = person.getStats();
		for (String skillId : Global.getSettings().getSortedSkillIds()) {
			int level = (int) stats.getSkillLevel(skillId);
			if (level > 0) {
				SkillSpecAPI spec = Global.getSettings().getSkillSpec(skillId);
				String skillName = spec.getName();
				if (level > 1) {
					skillName += " (Elite)";
				}
				info.addPara("Skill: " + skillName, initPad, tc, h, skillName);
				initPad = 0f;
			}
		}
		info.addPara("Personality: %s", initPad, tc, h, pName);
		unindent(info);
	}
	
	public String getDescText() {
		String themselves = "himself";
		if (person.isFemale()) themselves = "herself";
		return "A pilot has distinguished " + themselves + " recently and is worthy of consideration for " +
				 "the command of a ship. You are free to pass them over for promotion, though this may affect their performance in future engagements.";
	}

	@Override
	public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
		String pName = Misc.getPersonalityName(person);
		
		Color h = Misc.getHighlightColor();
		Color g = Misc.getGrayColor();
		Color tc = Misc.getTextColor();
		float pad = 3f;
		float opad = 10f;
		
		info.addImage(person.getPortraitSprite(), width, 128, opad);
		info.addPara("%s, subordinate of %s",opad,tc,h,person.getNameString(),captain.getNameString());
		info.addPara(getDescText(), tc, opad);
		
		addBulletPoints(info, ListInfoMode.IN_DESC);
		info.addPara(person.getPersonalityAPI().getDescription(), opad);
		
		float days = DURATION - getDaysSincePlayerVisible();
		info.addPara("This opportunity will be available for %s more " + getDaysString(days) + ".", 
				opad, tc, h, getDays(days));
		
		int max = Misc.getMaxOfficers(Global.getSector().getPlayerFleet());
		int curr = Misc.getNumNonMercOfficers(Global.getSector().getPlayerFleet());
		
		Color hNum = h;
		if (curr > max) hNum = Misc.getNegativeHighlightColor();
		LabelAPI label = info.addPara("Officers already under your command: %s %s %s", opad, tc, h, 
				"" + curr, "/", "" + max);
		label.setHighlightColors(hNum, h, h);
		
		
		Color color = Misc.getStoryOptionColor();
		Color dark = Misc.getStoryDarkColor();
		
		ButtonAPI button = addGenericButton(info, width, color, dark, "Promote to ship command", BUTTON_PROMOTE);
		button.setShortcut(Keyboard.KEY_T, true);

		ButtonAPI button2 = addGenericButton(info, width, color, dark, "Pass over for promotion", BUTTON_DECLINE);
		button2.setShortcut(Keyboard.KEY_F, true);
		
		if (curr >= max) {
			button.setEnabled(false);
			//info.addPara("Maximum number of officers reached.", tc, opad);
		}
		
	}
	
	public void storyActionConfirmed(Object buttonId, IntelUIAPI ui)
	{
		if (buttonId == BUTTON_PROMOTE) {
			endImmediately();
			ui.recreateIntelUI();
		}
		
		if (buttonId == BUTTON_DECLINE) {
			endImmediately();
			ui.recreateIntelUI();
		}
	}
		
	public StoryPointActionDelegate getButtonStoryPointActionDelegate(Object buttonId) {
		if (buttonId == BUTTON_PROMOTE) {
			String squadName = (String)Global.getSector().getPersistentData().get("armaa_wingCommander_squadronName_"+captain.getId()); 		
			StoryOptionParams params = new StoryOptionParams(null, 1, "promoteCrewMember", 
											Sounds.STORY_POINT_SPEND_LEADERSHIP, 
											"Promoted a strikecraft pilot from " + squadName + " to ship command");
			return new BaseOptionStoryPointActionDelegate(null, params) {
				@Override
				public void confirm() {
					CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
					playerFleet.getCargo().removeCrew(1);
					
					playerFleet.getFleetData().addOfficer(person);
					person.setPostId(Ranks.POST_OFFICER);
					//remove them from squad roster
					Global.getSector().getPersistentData().put("armaa_wingCommander_wingman_"+wingmanNo+"_"+captain.getId(),null);
					Global.getSector().getPersistentData().put("armaa_wingCommander_wingman_"+wingmanNo+"_"+"callsign_"+captain.getId(),null);
				}
				
				@Override
				public String getTitle() {
					//return "Promoting junior officer to ship command";
					return null;
				}

				@Override
				public void createDescription(TooltipMakerAPI info) {
					info.setParaInsigniaLarge();
					super.createDescription(info);
				}
			};
		}
		if (buttonId == BUTTON_DECLINE) {
			StoryOptionParams params = new StoryOptionParams(null, 0, "passOverCrewMember", 
											Sounds.STORY_POINT_SPEND_LEADERSHIP, 
											"Promoted promising junior officer to ship command");
			return new BaseOptionStoryPointActionDelegate(null, params) {
				@Override
				public void confirm() 
				{
					person.getRelToPlayer().setRel(person.getRelToPlayer().getRel()-MathUtils.getRandomNumberInRange(.0f,.2f));
					person.addTag("armaa_doNotAutoConsiderAgain");
					Global.getSector().getPersistentData().put("armaa_wingCommander_wingman_"+wingmanNo+"_"+captain.getId(),person); 
					
				}
				
				@Override
				public String getTitle() {
					//return "Promoting junior officer to ship command";
					return null;
				}

				@Override
				public void createDescription(TooltipMakerAPI info) {
					info.setParaInsigniaLarge();
					super.createDescription(info);
				}
			};
		}
		return null;
	}


	@Override
	public String getIcon() {
		return person.getPortraitSprite();
	}
	
	@Override
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		tags.add("Squadron Manager");
		return tags;
	}

	@Override
	public String getCommMessageSound() {
		return super.getCommMessageSound();
		//return getSoundMajorPosting();
	}
	
	public PersonAPI getCandidate() {
		return person;
		//return getSoundMajorPosting();
	}
	
	
}



