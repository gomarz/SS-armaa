package data.scripts.util;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import org.magiclib.util.MagicRender;
import org.magiclib.util.MagicTargeting;
import org.magiclib.util.MagicLensFlare;
import java.awt.Color;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.FaderUtil;
import com.fs.starfarer.api.graphics.SpriteAPI;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Collections;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import org.lazywizard.lazylib.combat.CombatUtils;
import com.fs.starfarer.api.loading.DamagingExplosionSpec;
import com.fs.starfarer.api.input.InputEventAPI;
import java.util.Random;

import com.fs.starfarer.api.characters.*;

import data.scripts.MechaModPlugin;
import org.apache.log4j.Logger;

public class armaa_pilotTracker extends BaseEveryFrameCombatPlugin
{
	private ShipAPI fighter;
	private String name;
	private ShipAPI ship;
	private PersonAPI captain;
	private int pilotNo;
	private IntervalUtil interval = new IntervalUtil(10f, 10f);
	private IntervalUtil openingDialogue = new IntervalUtil(3f, 15f);
	private IntervalUtil combatDialogue = new IntervalUtil(5f, 40f);
	private boolean runOnce = false;
	private boolean hasChattered = false;
	private boolean hasSquadChattered = false;
	private List<String> introChatter = new ArrayList<String>();
	private HashSet<String> alreadyUsed = new HashSet<String>();
	private List<String> combatChatter = new ArrayList<String>();
	private List<String> squadChatter_s = new ArrayList<String>();
	private List<String> squadChatter_r = new ArrayList<String>();
	private Logger logger;
    public armaa_pilotTracker(ShipAPI fighter, String name,int pilotNo) 
	{	
        this.fighter = fighter;
		this.name  = name;
		this.pilotNo = pilotNo;
		this.ship = fighter.getWing().getSourceShip();
		this.captain = fighter.getWing().getSourceShip().getCaptain();
		//logger = Logger.getLogger(this.getClass());		
		int side = ship.getOwner() == 0 ? 1 : 0;
		introChatter.addAll(MechaModPlugin.introChatter);
		
		//get fac specific intro lines
		//can add other stuff while iterating here in future
		if(Global.getCombatEngine().getFleetManager(side).getFleetCommander() != null)
		{
			String faction = Global.getCombatEngine().getFleetManager(side).getFleetCommander().getFaction().getId();
			if(MechaModPlugin.introChatter_special.containsKey(faction))
			{
				//logger.info(faction);
				introChatter.addAll(MechaModPlugin.introChatter_special.get(faction));
			}
			//Collections.shuffle(introChatter);
		}
				
		for(FleetMemberAPI member:Global.getCombatEngine().getFleetManager(side).getDeployedCopy())
		{
			if(MechaModPlugin.introChatter_special.containsKey(member.getHullId()))
			{
				introChatter.addAll(MechaModPlugin.introChatter_special.get(member.getHullId()));				
			}
			
			if(MechaModPlugin.introChatter_special.containsKey(member.getCaptain().getId()))
			{
				introChatter.addAll(MechaModPlugin.introChatter_special.get(member.getCaptain().getId()));				
			}
		}
		combatChatter.addAll(MechaModPlugin.combatChatter);
		squadChatter_s.addAll(MechaModPlugin.intersquadChatter_statement);
		squadChatter_r.addAll(MechaModPlugin.intersquadChatter_response);
				
		//logger.info(Global.getSector().getMemory().getKeys());	
    }

    @Override
	public void advance(float amount, List<InputEventAPI> events)
	{
		if(Global.getCombatEngine().isPaused())
			return;
		//Global.getSector().getPlayerPerson().getMemory().getKeys();
		if(!introChatter.isEmpty() && MechaModPlugin.chatterEnabled)
		{
			if(Global.getCombatEngine().getCustomData().get("armaa_wingCommander_squadron_hasChattered_"+captain.getId()) instanceof Boolean)
				hasChattered = (Boolean)Global.getCombatEngine().getCustomData().get("armaa_wingCommander_squadron_hasChattered_"+captain.getId());

			//String squadName = (String)Global.getSector().getPersistentData().get("armaa_wingCommander_squadronName_"+ship.getCaptain().getId());
			if(!Global.getCombatEngine().isPaused())
			{
				interval.advance(amount);
				if(!runOnce && !hasChattered && fighter.getWing() != null && fighter.getWing().getLeader() == fighter)
				{
					openingDialogue.advance(amount);
					if(openingDialogue.intervalElapsed())
					{
						if(Math.random() >= .70f)
						{
							if(Global.getCombatEngine().getCustomData().containsKey("armaa_alreadyUsedLines"))
								alreadyUsed = (HashSet)Global.getCombatEngine().getCustomData().get("armaa_alreadyUsedLines");
							String squadName = (String)Global.getSector().getPersistentData().get("armaa_wingCommander_squadronName_"+captain.getId());
							Random rand = new Random();
							int size = rand.nextInt(introChatter.size());
							String chatter = introChatter.get(size);
							if(!alreadyUsed.contains(chatter))
							{
								alreadyUsed.add(chatter);
								Global.getCombatEngine().getCustomData().put("armaa_alreadyUsedLines",alreadyUsed);
								Color color = fighter.isAlly() ? Misc.getHighlightColor() : Misc.getBasePlayerColor();
								Color textColor = Global.getSettings().getColor("standardTextColor");

								if(chatter.contains("$"))
								{
									String chatter2 = processSpecialStrings(fighter,ship,chatter);
									Global.getCombatEngine().getCombatUI().addMessage(1,fighter,color,squadName,Color.white,":",textColor,chatter2);
								}
								else
									Global.getCombatEngine().getCombatUI().addMessage(1,fighter,color,squadName,Color.white,":",textColor,chatter);
							}
						}
						runOnce = true;
						Global.getCombatEngine().getCustomData().put("armaa_wingCommander_squadron_hasChattered_"+captain.getId(),true);
						
					}
				}
			}
		}
		
		combatDialogue.advance(amount);
		float prob = 1f;
		if(fighter.getWing() != null)
			prob = (float)(fighter.getWing().getWingMembers().size());
		//Global.getCombatEngine().maintainStatusForPlayerShip("Probability", "graphics/ui/icons/icon_repair_refit.png", "Chance of Dialogue" +": ", String.valueOf(.50f/prob
		//),false);
		if(Global.getCombatEngine().getCustomData().get("armaa_wingCommander_squadron_hasSquadChattered_"+captain.getId()) instanceof Boolean)
			hasSquadChattered = (Boolean)Global.getCombatEngine().getCustomData().get("armaa_wingCommander_squadron_hasSquadChattered_"+captain.getId());		
		if(combatDialogue.intervalElapsed() && MechaModPlugin.chatterEnabled)
		{
			Color color = fighter.isAlly() ? Misc.getHighlightColor() : Misc.getBasePlayerColor();
			Color textColor = Global.getSettings().getColor("standardTextColor");
			if(fighter.getOwner() == 1)
				color = Misc.getNegativeHighlightColor();
			boolean isFiring = false;
			float baseProb = .2f;
			if(MagicRender.screenCheck(0.2f, fighter.getLocation()))
			{
				baseProb*=3;
				
			}
			for(WeaponAPI w :fighter.getAllWeapons())
				if(w.isFiring())
				{
					isFiring = true;
					break;
				}
			if(isFiring || fighter.getShipTarget() != null)
			{
				if(Math.random() <= (baseProb/prob))
				{
					Random rand = new Random();
					int size = rand.nextInt(combatChatter.size());
					String chatter = combatChatter.get(size);
					String squadName = (String)Global.getSector().getPersistentData().get("armaa_wingCommander_squadronName_"+captain.getId());
					
					if(chatter.contains("$"))
					{
						String chatter2 = processSpecialStrings(fighter,ship,chatter);
						chatter = chatter2;
					}
					if(!MagicRender.screenCheck(0.2f, fighter.getLocation()) )
					{						
						Global.getCombatEngine().getCombatUI().addMessage(1,fighter,color,name+", "+squadName,Color.white,":",textColor,chatter);
					}
					
					else
				Global.getCombatEngine().addFloatingTextAlways(fighter.getLocation(), chatter, 30f, Color.white, fighter, 0f, 0f, 1f, 3f, 2f, 0f); 	
				}
			}

			else if(Math.random() <= .15f/prob && !hasSquadChattered)
			{
				int count = (Integer)Global.getSector().getPersistentData().get("armaa_wingCommander_squadSize_"+captain.getId());
				for(int j = 0; j < count; j++)
				{
					boolean wasAssigned = false;
					Object obj = Global.getSector().getPersistentData().get("armaa_wingCommander_wingman_"+j+"_"+captain.getId());
					if(obj instanceof PersonAPI && fighter.getWing() != null && fighter.getWing().getSpec().getVariant().getHullSpec().getMinCrew() > 0)
					{
						Object bool = Global.getCombatEngine().getCustomData().get("armaa_wingCommander_wingman_"+j+"_wasAssigned_"+captain.getId());
						if(bool instanceof Boolean)
						{
							wasAssigned = (Boolean)Global.getCombatEngine().getCustomData().get("armaa_wingCommander_wingman_"+j+"_wasAssigned_"+captain.getId());
						}
						PersonAPI pilot = (PersonAPI)Global.getSector().getPersistentData().get("armaa_wingCommander_wingman_"+j+"_"+captain.getId());
						if(!wasAssigned)
							continue;
						if(pilot == fighter.getCaptain())
							continue;
						String otherguy = (String)Global.getSector().getPersistentData().get("armaa_wingCommander_wingman_"+j+"_"+"callsign_"+captain.getId());
						String squadName = (String)Global.getSector().getPersistentData().get("armaa_wingCommander_squadronName_"+captain.getId());
						
						Random rand = new Random();
						int size = rand.nextInt(squadChatter_s.size());
						String chatter = squadChatter_s.get(size);
						
						if(chatter.contains("$"))
						{
							if(chatter.contains("$squadmember"))
							{
								String chatter2 = chatter.replace("$squadmember",otherguy);
								chatter = chatter2;
							}
							
							String chatter2 = processSpecialStrings(fighter,ship,chatter);
							chatter = chatter2;
						}
				
						Global.getCombatEngine().getCombatUI().addMessage(1,fighter,color,name+", "+squadName,Color.white,":",textColor,chatter);
												
						size = rand.nextInt(squadChatter_r.size());
						chatter = squadChatter_r.get(size);

						if(chatter.contains("$"))
						{
							if(chatter.contains("$squadmember"))
							{
								String chatter2 = chatter.replace("$squadmember",name);
								chatter = chatter2;
							}
							
							String chatter2 = processSpecialStrings(fighter,ship,chatter);
							chatter = chatter2;
						}

						Global.getCombatEngine().getCombatUI().addMessage(1,fighter,color,otherguy+", "+squadName,Color.white,":",textColor,chatter);
						Global.getCombatEngine().getCustomData().put("armaa_wingCommander_squadron_hasSquadChattered_"+captain.getId(),true);
						break;						
					}
				}
			}
		}
		
		if(Global.getCombatEngine() == null || fighter== null || fighter.getHitpoints() <= 0 || !Global.getCombatEngine().isEntityInPlay(fighter) || Global.getCombatEngine().isCombatOver()) 
		{
			Global.getCombatEngine().getCustomData().put("armaa_wingCommander_wingman_"+pilotNo+"_wasAssigned_"+captain.getId(),false);
			Global.getCombatEngine().removePlugin(this);
		}	
		
		if(MagicRender.screenCheck(0.2f, fighter.getLocation()) && !fighter.isLiftingOff() && fighter.isAlive())
		{
			Color floatyColor = fighter.getOwner() == 1 ? Misc.getHighlightColor() : Misc.getBasePlayerColor();
			Vector2f loc = new Vector2f(fighter.getLocation());
			loc.setY(loc.getY()+35);
			if(!Global.getCombatEngine().hasAttachedFloaty(fighter))
				Global.getCombatEngine().addFloatingTextAlways(loc, name, 15f, floatyColor, fighter, 0f, 0f, 5f, amount, amount, 0f); 	
		}
	}
	
	private String processSpecialStrings(ShipAPI fighter, ShipAPI ship,String chatter)
	{
		int side = fighter.getOwner() == 0 ? 1 : 0;
		
		if(Global.getCombatEngine().getFleetManager(side) == null)
			return "*static*";			
		if(Global.getCombatEngine().getFleetManager(side).getFleetCommander() == null)
			return "*fzzt*";	
		if(Global.getCombatEngine().getFleetManager(side).getFleetCommander().getFaction() == null)
			return "*bzzt*";
		
		if(ship == null || fighter == null)
			return "*crackle*";

		String chatter2 = chatter;
		
		PersonAPI eneCommander = Global.getCombatEngine().getFleetManager(side).getFleetCommander();
		
		if(chatter2.contains("$factionnamearticle"))
		{
			String faction = "the enemy";
			//just nullcheck everything lel
			if(eneCommander.getFaction().isPlayerFaction())
			{
				if(Misc.getFactionMarkets(Global.getSector().getPlayerFaction()).isEmpty())
				{
					faction = "the Savior of Galatia";
				}
			}
			
			else
			faction = Global.getCombatEngine().getFleetManager(side).getFleetCommander().getFaction().getDisplayNameWithArticle();
			
			chatter2 = chatter.replace("$factionnamearticle",faction);
			chatter = chatter2;
		}
		
		if(chatter2.contains("$factionname"))
		{
			String faction = "hostiles";
			if(eneCommander.getFaction().isPlayerFaction())
			{
				if(Misc.getFactionMarkets(Global.getSector().getPlayerFaction()).isEmpty())
				{
					faction = "unaffiliated";
				}
			}
			
			else
			faction = Global.getCombatEngine().getFleetManager(side).getFleetCommander().getFaction().getDisplayName();
			chatter2 = chatter.replace("$factionname",faction);
			chatter = chatter2;
		}
		
		if(chatter2.contains("$squadronname"))
		{
			String squadName = (String)Global.getSector().getPersistentData().get("armaa_wingCommander_squadronName_"+captain.getId());
			chatter2 = chatter.replace("$squadronname",squadName);
			chatter = chatter2;
		}
				
		if(chatter2.contains("$squadleader"))
		{
			String squadLeader = "the Command Ship";
			if(ship != null && ship.getName() != null)
				squadLeader = ship.getName();
			
			chatter2 = chatter.replace("$squadleader",squadLeader);
			chatter = chatter2;
		}


		return chatter2;
	}
	

}