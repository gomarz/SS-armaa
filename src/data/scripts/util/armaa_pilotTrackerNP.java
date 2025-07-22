package data.scripts.util;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import org.magiclib.util.MagicRender;
import java.awt.Color;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.util.Misc;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import com.fs.starfarer.api.input.InputEventAPI;
import java.util.Random;
import org.apache.log4j.Logger;
import com.fs.starfarer.api.characters.*;

import data.scripts.MechaModPlugin;


public class armaa_pilotTrackerNP extends BaseEveryFrameCombatPlugin
{
	private ShipAPI fighter;
	private String name;
	private ShipAPI ship;
	private PersonAPI captain;
	private int pilotNo;
	private IntervalUtil interval = new IntervalUtil(10f, 10f);
	private IntervalUtil openingDialogue = new IntervalUtil(1f, 10f);
	private IntervalUtil combatDialogue = new IntervalUtil(20f, 50f);
	private boolean runOnce = false;
	private boolean hasChattered = false;
	private List<String> introChatter = new ArrayList<String>();
	private List<String> combatChatter = new ArrayList<String>();
	private List<String> squadChatter_s = new ArrayList<String>();
	private List<String> squadChatter_r = new ArrayList<String>();
	private Logger logger;

    public armaa_pilotTrackerNP(ShipAPI fighter, String name,int pilotNo) 
	{	
        this.fighter = fighter;
		this.name  = name;
		this.pilotNo = pilotNo;
		this.ship = fighter.getWing().getSourceShip();
		this.captain = fighter.getWing().getSourceShip().getCaptain();
		//logger = Logger.getLogger(this.getClass());

		introChatter.addAll(MechaModPlugin.introChatter);
		combatChatter.addAll(MechaModPlugin.combatChatter);
		squadChatter_s.addAll(MechaModPlugin.intersquadChatter_statement);
		squadChatter_r.addAll(MechaModPlugin.intersquadChatter_response);
		
		//logger.info(Global.getSector().getMemory().getKeys());
		if(fighter.getOwner() == 1)
		{
			for(String key : MechaModPlugin.introChatter_special.keySet()) 
			{
				if(Global.getSector().getMemory().get(key) != null)
				{
					introChatter.addAll(MechaModPlugin.introChatter_special.get(key));
				}
			}
			Collections.shuffle(introChatter);
		}
    }

    @Override
	public void advance(float amount, List<InputEventAPI> events)
	{
		if(Global.getCombatEngine().isPaused())
			return;
		
		if(!introChatter.isEmpty() && MechaModPlugin.chatterEnabled)
		{
			if(Global.getCombatEngine().getCustomData().get("armaa_wingCommander_squadron_hasChattered_"+captain.getId()) instanceof Boolean)
				hasChattered = (Boolean)Global.getCombatEngine().getCustomData().get("armaa_wingCommander_squadron_hasChattered_"+captain.getId());

			//String squadName = (String)Global.getCombatEngine().getCustomData().get("armaa_wingCommander_squadronName_"+ship.getCaptain().getId());
			if(!Global.getCombatEngine().isPaused())
			{
				interval.advance(amount);
				if(!runOnce && !hasChattered && fighter.getWing().getLeader() == fighter)
				{
					openingDialogue.advance(amount);
					if(openingDialogue.intervalElapsed())
					{
						if(Math.random() <= .50f)
						{
							String squadName = ship.getName() + " Squadron";
							Random rand = new Random();
							int size = rand.nextInt(introChatter.size());
							String chatter = introChatter.get(size);
							Color color = fighter.isAlly() ? Misc.getHighlightColor() : Misc.getNegativeHighlightColor();
							Color textColor = Global.getSettings().getColor("standardTextColor");

							if(chatter.contains("$"))
							{
								String chatter2 = processSpecialStrings(fighter,ship,chatter);
								Global.getCombatEngine().getCombatUI().addMessage(1,fighter,color,squadName,Color.white,":",textColor,chatter2);
							}
							else
								Global.getCombatEngine().getCombatUI().addMessage(1,fighter,color,squadName,Color.white,":",textColor,chatter);
						}
						runOnce = true;
						Global.getCombatEngine().getCustomData().put("armaa_wingCommander_squadron_hasChattered_"+captain.getId(),true);
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
			Color floatyColor = fighter.getOwner() == 1 ? Misc.getHighlightColor() : Misc.getPositiveHighlightColor();
			Vector2f loc = new Vector2f(fighter.getLocation());
			loc.setY(loc.getY()+35);
			if(!Global.getCombatEngine().hasAttachedFloaty(fighter))
				Global.getCombatEngine().addFloatingTextAlways(loc, name, 10f, floatyColor, fighter, 0f, 0f, 5f, amount, amount, 0f); 	
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
				
				else
				{
					faction = Global.getCombatEngine().getFleetManager(side).getFleetCommander().getFaction().getDisplayNameWithArticle();
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
					faction = "the Savior of Galatia";
				}
				
				else
				{
					faction = Global.getCombatEngine().getFleetManager(side).getFleetCommander().getFaction().getDisplayNameWithArticle();
				}
			}
			
			else
			faction = Global.getCombatEngine().getFleetManager(side).getFleetCommander().getFaction().getDisplayName();
			chatter2 = chatter.replace("$factionname",faction);
			chatter = chatter2;
		}
		
		if(chatter2.contains("$squadronname"))
		{
			String squadName = " "+ship.getName() + " Squadron";
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