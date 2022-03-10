package data.hullmods;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.Global;

import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.impl.campaign.ids.*;

import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.characters.*;

import com.fs.starfarer.api.combat.WeaponAPI.WeaponSize;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.entities.AnchoredEntity;

import com.fs.starfarer.api.combat.EmpArcEntityAPI;

import data.scripts.util.MagicUI;
import data.scripts.util.MagicRender;

import data.scripts.ai.armaa_combat_docking_AI;

import org.lazywizard.lazylib.combat.CombatUtils;
import org.lazywizard.lazylib.CollisionUtils;
import org.lazywizard.lazylib.combat.DefenseUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.*;
import java.awt.Color;

import data.scripts.util.armaa_utils;
//Needed to access MagicSettings stuff
import data.scripts.MechaModPlugin;

import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.combat.listeners.AdvanceableListener;
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.combat.listeners.DamageListener;
import com.fs.starfarer.api.graphics.SpriteAPI;
import data.scripts.util.MagicRender;

//import org.apache.log4j.Logger;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
public class armaa_strikeCraft extends BaseHullMod {

	private Color COLOR = new Color(0, 106, 0, 50);
	private float DEGRADE_INCREASE_PERCENT = 100f;
	public float CORONA_EFFECT_REDUCTION = 0.00001f;
	
	public MagicUI ui;
	private static final float RETREAT_AREA_SIZE = 2050f;
	private IntervalUtil interval = new IntervalUtil(25f,25f);
	private IntervalUtil warningInterval = new IntervalUtil(1f,1f);
	private IntervalUtil textInterval = new IntervalUtil(.5f,.5f);
	
	private Color TEXT_COLOR = new Color(55,155,255,255);
	private final Color CORE_COLOR = new Color(200, 200, 200);
	private final Color FRINGE_COLOR = new Color(255, 10, 10);	
	private final float Repair = 100, CR = 20; //Hullmod in game description.
	private float HPPercent = 0.80f;
	private float BaseTimer = .30f;
    private float CRmarker = 0.4f;
	
	private float MISSILE_DAMAGE_THRESHOLD = 750f;
	private boolean hasLanded;
	public final ArrayList<String> landingLines_Good = new ArrayList<>();
	public final ArrayList<String> landingLines_Fair = new ArrayList<>();
	public final ArrayList<String> landingLines_Critical = new ArrayList<>();
	public final ArrayList<String> landingLines_notPossible = new ArrayList<>();
	
	{
		//If CR is low, but otherwise no major damage
        landingLines_Good.add("\"Welcome aboard, $hisorher.\"");
		landingLines_Good.add("\"Looks like you got away pretty clean!\"");
		landingLines_Good.add("\"Hopefully, you bag a few more kills today.\"");
		landingLines_Good.add("\"All conditions good. Fuselage solid.\"");

		//If taken heavy damage, but CR isn't too bad
		landingLines_Critical.add("\"Glad you made it back alive, $hisorher.\"");
		landingLines_Critical.add("\"Looks like this may be a tough battle, $hisorher.\"");
		landingLines_Critical.add("\"Be careful flying while severely damaged. Even anti-air fire could bring you down.\"");
		
		
		//If heavy damage and low CR
	
		//Tried to find a carrier but could not
		landingLines_notPossible.add("\"Dammit, nowhere to land!\"");
		landingLines_notPossible.add("\"Could really use somewhere to land right now..\"");
		landingLines_notPossible.add("\"Zilch on available carriers. Aborting refit!\"");
		landingLines_notPossible.add("\"There's nowhere for me to resupply.\"");
	}
		
	//Logger log = Logger.getLogger(armaa_strikeCraft.class.getName());

	public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) 
	{
		stats.getZeroFluxMinimumFluxLevel().modifyFlat(id, -1f); // -1 means boost never takes effect; 2 means always on
		stats.getCRLossPerSecondPercent().modifyPercent(id, DEGRADE_INCREASE_PERCENT);
		stats.getDynamic().getStat(Stats.CORONA_EFFECT_MULT).modifyMult("armaa_carrierStorage", CORONA_EFFECT_REDUCTION);
	}
	
	@Override
	public void applyEffectsAfterShipCreation(ShipAPI ship, java.lang.String id)
	{
		//These listeners are used to get around some of the quirks of using Fighter HullSize
		
		ship.addListener(new StrikeCraftDeathMod(ship));
		ship.addListener(new StrikeCraftDamageListener(ship));
	}
	
	@Override
	public void advanceInCampaign(FleetMemberAPI member, float amount)
	{
		if(member.getFleetData() == null)
			return;
		FleetDataAPI fleet = member.getFleetData();
		for(FleetMemberAPI ship: fleet.getMembersListCopy())
		{
			if(ship.isFrigate())
				continue;
			
			if(ship.getNumFlightDecks() < 1)
				continue;
			
			for(int i = 0; i < ship.getNumFlightDecks();i++)
			{
				return;
			}
		}
		member.getStats().getDynamic().getStat(Stats.CORONA_EFFECT_MULT).unmodify("armaa_carrierStorage");
	}

	
	
	public boolean canRetreat(ShipAPI ship)
	{
		float mapWidth = Global.getCombatEngine().getMapWidth() / 2f, mapHeight = Global.getCombatEngine().getMapHeight() / 2f;
		Vector2f rawLL = new Vector2f(-mapWidth, -mapHeight),rawUR = new Vector2f(mapWidth, mapHeight);
		CombatEngineAPI engine = Global.getCombatEngine();
		BattleCreationContext context = engine.getContext();
		FleetGoal objective = context.getPlayerGoal();
		boolean check = false;
		Vector2f location = ship.getLocation();
		// if player is escaping or I am an enemy
		if(objective == FleetGoal.ESCAPE || ship.getOwner() == 1)
		{
			//I can retreat at the top of the map
			if(location.getY() > rawUR.getY() - RETREAT_AREA_SIZE)
			{
				check = true;
			}
		}
		//if player is escaping and I am an enemy, or if I am player
		if(objective == FleetGoal.ESCAPE && ship.getOwner() == 1 || ship.getOwner() == 0)
		{
			//I can retreat at the bottom of the map
			if(location.getY() < RETREAT_AREA_SIZE+rawLL.getY())
			{
				check = true;
			}
		}
		return check;
	}
	
	//determines when AI pilots will return to carrier
	public void getPilotPersonality(ShipAPI ship)
	{
		String personality = "steady";
		if (ship.getCaptain() != null) 
		{
			PersonAPI pilot = ship.getCaptain();
			personality = pilot.getPersonalityAPI().getId().toString();
		}
		float dangerLevel = .45f;
		switch (personality) 
		{
			case "steady":
					// do nothing, we will use the default
				break;
			case "reckless":
					// we have a death wish
					dangerLevel = .45f;
				break;
			case "aggressive":
					dangerLevel = .45f;
				break;
			case "timid":
					dangerLevel = .45f;
				break;
			case "cautious":
					dangerLevel = .45f;
				break;
			default:
				break;
		}
		
		Global.getCombatEngine().getCustomData().put("armaa_strikecraftPilot"+ship.getId(),dangerLevel);
		
	}


	//Since fighters don't normally malfunction we have to simulate it
	public void checkMalfChance(ShipAPI ship)
	{
		float cr = ship.getCurrentCR();

		if(cr < .20f && cr >= 0f )
		{
			if(cr == 0f)
			{
				ship.setDefenseDisabled(true);
				ship.setShipSystemDisabled(true);
			}

			else
			{
				ship.setDefenseDisabled(false);
				ship.setShipSystemDisabled(false);
			}

			ship.getMutableStats().getEngineMalfunctionChance().modifyFlat(ship.getId(), 0.03f*(1f + (1f-cr) ) );
			ship.getMutableStats().getWeaponMalfunctionChance().modifyFlat(ship.getId(), 0.03f*(1f + (1f-cr) ) );
		}

		else
		{
			ship.getMutableStats().getEngineMalfunctionChance().unmodify();
			ship.getMutableStats().getWeaponMalfunctionChance().unmodify();
		}
	}
	
	public void drawHud(ShipAPI ship)
	{
		MagicUI.drawHUDStatusBar(
			ship, 
			(ship.getHitpoints()/ship.getMaxHitpoints()),
			null,
			null,
			1,
			"HULL",
			">",
			true
		);

		MagicUI.drawHUDStatusBar(
		 ship, (ship.getCurrFlux()/ship.getMaxFlux()),
		 null,
		 null,
		(ship.getHardFluxLevel()),
		 "FLUX",
		 ">",
		 false
		);
	}

	public Color getColorCondition(ShipAPI ship)
	{
		return Global.getSettings().getColor("textFriendColor");
	}

	public String getDescriptionParam(int index, HullSize hullSize) 
	{

		if (index == 0) return "" + "activating autopilot"; 

		return null;
	}
	
	public void giveCrWarning(ShipAPI ship)
	{
		if(ship.getOwner() == 0 && !ship.isAlly() && Global.getCombatEngine().getPlayerShip() == ship)
			if(!(Global.getCombatEngine().getCustomData().get("armaa_strikecraftCRWarning"+ship.getId()) instanceof Boolean))
			{
				Global.getCombatEngine().getCombatUI().addMessage(1,Global.getSettings().getColor("textFriendColor"),ship.getName() + "'s ",Color.yellow, "performance is degrading due to combat stresses.");
				Global.getSoundPlayer().playUISound("cr_playership_warning", .8f, 1f);
				Global.getCombatEngine().getCustomData().put("armaa_strikecraftCRWarning"+ship.getId(),true);
			}
	}
	
	private final Color HL=Global.getSettings().getColor("hColor");
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
		Color[] arr3 ={Misc.getHighlightColor(),Misc.getHighlightColor(),E};	
		
		tooltip.addSectionHeading("Details", Alignment.MID, 10);  
		tooltip.addPara("%s " + "Can %s.", pad, Misc.getHighlightColor(), "-", "maneuver over asteroids and other vessels, unless flamed out");
		tooltip.addPara("%s " + "Most weapons %s.", padS, Misc.getHighlightColor(), "-", "fire over allied ships");
		tooltip.addPara("%s " + "No %s.", padS, Misc.getHighlightColor(), "-", "zero-flux speed bonus");
		tooltip.addPara("%s " + "Combat Readiness decreases %s faster.", padS, arr2, "-", (int) DEGRADE_INCREASE_PERCENT + "%");
		tooltip.addPara("%s " + "Docking replenishes %s CR and %s armor/hull/ammo.", padS, Misc.getHighlightColor(), "-",(int)CR + "%",(int)Repair+"%");
		tooltip.addPara("%s " + "Incapable of %s.", padS, Misc.getHighlightColor(), "-","capturing objectives");

		tooltip.addSectionHeading("Point Defense Vulnerability",Alignment.MID,10);
        TooltipMakerAPI pdWarning = tooltip.beginImageWithText("graphics/armaa/icons/hullsys/armaa_pdWarning.png", 64);
		pdWarning.addPara("%s " + "Incurs additional damage from all weapons employed by captains with the %s skill. The visual indicator to the left will appear over such vessels.", padS, Misc.getHighlightColor(), "-","Point Defense");
		pdWarning.addPara("%s " + "This malus applies to any other abilities that increase damage to fighters, as well.", padS, Misc.getHighlightColor(), "-","Point Defense");		tooltip.addImageWithText(10f);
		
		tooltip.addSectionHeading("Carrier Bonuses",Alignment.MID,10);
		if(ship.getMutableStats().getDynamic().getStat(Stats.CORONA_EFFECT_MULT).getModifiedValue() != ship.getMutableStats().getDynamic().getStat(Stats.CORONA_EFFECT_MULT).base)
			tooltip.addPara("%s " + "Friendly Carriers present for storage during travel: Receives %s from hyperspace storms and coronas outside of combat.",pad,arr,"-","no damage");
		
        //title
        tooltip.addSectionHeading("Refit Penalties", Alignment.MID, 10);        
        
        if(ship!=null && ship.getVariant()!=null)
		{
			float adjustedRate  = 0f;
			String weaponName = "";
			getRefitRate(ship);
			Map<String,Float> MALUSES = (Map)Global.getCombatEngine().getCustomData().get("armaa_strikecraftMalus_"+ship.getId());
		
            if(MALUSES == null || MALUSES.isEmpty())
			{
                tooltip.addPara(
                        "No refit penalty."
                        ,10
                        ,F
                );

            } 
			else 
			{
				float total = (float)Global.getCombatEngine().getCustomData().get("armaa_strikecraftTotalMalus"+ship.getId());
				tooltip.addPara("%s " + "Refitting at carriers is %s slower.", pad, arr2, "-", (int)(total*100) + "%");
				tooltip.addSectionHeading("Modifiers", Alignment.MID, 10);        
				tooltip.addPara("", 0f);
				for(Map.Entry<String,Float> entry : MALUSES.entrySet())
				{
					//effect applied
					float value = entry.getValue();				;
					String weapon = entry.getKey();
					tooltip.addPara("%s " + "%s: +%s.", padS, arr3, "-",weapon,(int)(value*100) + "%");
					
				}
            }
        }
        
    }
	
	private void getRefitRate(ShipAPI target)
	{
		float totalRate = 0f;
		String wepName = "";
		List<WeaponAPI> weapons = target.getAllWeapons();
		Map<String,Float> MALUSES = new HashMap<>();
		for(WeaponAPI w : weapons)
		{
			float adjustedRate  = 0f;
			if(MechaModPlugin.MISSILE_REFIT_MALUS.get(w.getId()) != null)
			{
				//If not null, there is a custom refit value for this weapon
				adjustedRate = MechaModPlugin.MISSILE_REFIT_MALUS.get(w.getId());
				totalRate+= adjustedRate;
				if (MALUSES.containsKey(w.getDisplayName()))
				{
					float prev = MALUSES.get(w.getDisplayName());
					MALUSES.put(w.getDisplayName(),prev+adjustedRate);
				} 
				
				else 
					MALUSES.put(w.getDisplayName(),adjustedRate);
			}
			
			else if(w.getType() == WeaponType.MISSILE)
			{
				float damage =	w.getDerivedStats().getDamagePerShot();
				if(damage > MISSILE_DAMAGE_THRESHOLD)
				{
					float penalty = damage/MISSILE_DAMAGE_THRESHOLD;
					
					if(penalty < 1)
						continue;
					
					penalty = penalty/10f;
					float newRate = (float)Math.min(.5f,penalty);
					wepName = w.getDisplayName();
					adjustedRate = newRate;
					totalRate+= adjustedRate;
					
					if (MALUSES.containsKey(wepName))
					{
						float prev = MALUSES.get(wepName);
						MALUSES.put(wepName,prev+adjustedRate);
					} 
					
					else 
						MALUSES.put(wepName,adjustedRate);
					//Global.getCombatEngine().getCustomData().put("armaa_strikecraftMissileMalus"+target.getId(),adjustedRate);
					//Global.getCombatEngine().getCustomData().put("armaa_strikecraftWepName"+target.getId(),wepName);
				}
			}	
		}
		
        for (HullModSpecAPI spec : Global.getSettings().getAllHullModSpecs())
        {
			float adjustedRate  = 0f;
			//dont bother if its not in the map
			if(MechaModPlugin.HULLMOD_REFIT_MALUS.get(spec.getId()) == null)
				continue;
			
			//dont bother if its not on the target ship
			if(!target.getVariant().getHullMods().contains(spec.getId()))
				continue;
			
			String hullmod = spec.getId();
			String name = spec.getDisplayName();
			//If not null, there is a custom refit value for this weapon
			adjustedRate = MechaModPlugin.HULLMOD_REFIT_MALUS.get(hullmod);
			totalRate+= adjustedRate;
			if(MALUSES.containsKey(name))
			{
				float prev = MALUSES.get(name);
				MALUSES.put(name,prev+adjustedRate);
			} 
					
			else 
				MALUSES.put(name,adjustedRate);
        }
			Global.getCombatEngine().getCustomData().put("armaa_strikecraftTotalMalus"+target.getId(),totalRate);
			Global.getCombatEngine().getCustomData().put("armaa_strikecraftMalus"+"_"+target.getId(),MALUSES);
	}
	
	//Check if any of our weapons are out of ammo.
    private boolean needsReload(ShipAPI target)
    {
		boolean check = false;
		getRefitRate(target);
		List<WeaponAPI> weapons = target.getAllWeapons();
		for(WeaponAPI w : weapons)		
		{
			//So long as we have at least one weapon with some ammo left, don't return yet
			if(w.getId().equals("armaa_leynosBoostKnuckle") && Global.getCombatEngine().getPlayerShip() != target)
			{
				return false;
			}
			
			//code to make sure AI strikecraft don't return if they fire their landing beacon
			if(w.getId().equals("armaa_landingBeacon"))
			{
				if(w.getAmmo() < 1 && target == Global.getCombatEngine().getPlayerShip())
				{
					check = true;
					return check;
				}
				
				else continue;
			}
			
			if(w.usesAmmo() && (w.getAmmo() >= 1 && w.getAmmoPerSecond() == 0) && !(w.getSlot().isDecorative() && !w.getId().equals("armaa_landingBeacon") ))
			{
					check = false;
					
			}

			else if(w.usesAmmo() && w.getAmmo() < 1 && !(w.getSlot().isDecorative()))
			{
				if(w.getAmmoPerSecond() == 0)
				{
					check = true;
				}
			}
			
		}
		return check;
    }
	
	//Check if there are any ships that we can land at
	private boolean canRefit(ShipAPI ship)
	{
		boolean canRefit = false;
		
		for (ShipAPI carrier : CombatUtils.getShipsWithinRange(ship.getLocation(), 10000.0F)) 
		{
			if(carrier.getOwner() != ship.getOwner() || carrier.isFighter() || carrier.isFrigate())
				continue;
			if(carrier.isHulk())
				continue;
			if(carrier.getNumFighterBays() > 0)
			{

				canRefit = true;
			}				
		}
		
		return canRefit;
	}

	private ShipAPI getNearestCarrier(ShipAPI ship)
	{
		ShipAPI potCarrier = null;
		float distance = 99999f;
		for (ShipAPI carrier : CombatUtils.getShipsWithinRange(ship.getLocation(), 10000.0F)) 
		{
			if(carrier.getOwner() != ship.getOwner() || carrier.isFighter() || carrier.isFrigate() || carrier == ship)
				continue;
			if(carrier.isHulk())
				continue;
			if(carrier.getNumFighterBays() > 0)
			{
				if(MathUtils.getDistance(ship, carrier) < distance)
				{
					distance = MathUtils.getDistance(ship, carrier);
					potCarrier = carrier; 
				}
			}				
		}
		
		return potCarrier;
	}	
	
	private void checkWaypoint(ShipAPI ship)
	{
		boolean ally = false;
		if(ship.isAlly())
			ally = true;
		if(ship.isRetreating())
			return;
		CombatFleetManagerAPI cfm = Global.getCombatEngine().getFleetManager(ship.getOwner()); 
		CombatTaskManagerAPI ctm = cfm.getTaskManager(ally);
		
		if(Global.getCombatEngine().getCustomData().get("armaa_strikecraft_hasWaypoint"+ship.getId()) instanceof Boolean)
		{
			if((Boolean)Global.getCombatEngine().getCustomData().get("armaa_strikecraft_hasWaypoint"+ship.getId()) == true)
			{
				if(Global.getCombatEngine().getCustomData().get("armaa_strikecraft_refit_timer"+ship.getId()) instanceof Float)
				{
					float f = (Float)Global.getCombatEngine().getCustomData().get("armaa_strikecraft_refit_timer"+ship.getId());
					Global.getCombatEngine().getCustomData().put("armaa_strikecraft_refit_timer"+ship.getId(),f+0.1f);
					
					if(f  > 150f)
					{
						if(ctm.getAssignmentFor(ship) != null && ctm.getAssignmentFor(ship).getType() == CombatAssignmentType.RALLY_CIVILIAN)
						ctm.removeAssignment(ctm.getAssignmentFor(ship));
						ctm.orderSearchAndDestroy(cfm.getDeployedFleetMember(ship), false); 	
						Global.getCombatEngine().getCustomData().put("armaa_strikecraft_refit_timer"+ship.getId(),0f);
						Global.getCombatEngine().getCustomData().put("armaa_strikecraft_hasWaypoint"+ship.getId(),false);
					}
				}
				
				else
					Global.getCombatEngine().getCustomData().put("armaa_strikecraft_refit_timer"+ship.getId(),0f);
			}
		}
	}
	
	private void retreatToRefit(ShipAPI ship)
	{
		boolean ally = false;
		CombatFleetManagerAPI cfm = Global.getCombatEngine().getFleetManager(ship.getOwner()); 

		if(ship.isRetreating())
			return;
			
		if(Global.getCombatEngine().getCustomData().get("armaa_strikecraft_hasWaypoint"+ship.getId()) instanceof Boolean)
		{
			if((Boolean)Global.getCombatEngine().getCustomData().get("armaa_strikecraft_hasWaypoint"+ship.getId()) == true)
			{
				return;
			}
		}
		
		if(ship.isAlly())
			ally = true;
			
		ShipAPI carrier = getNearestCarrier(ship);

		CombatTaskManagerAPI ctm = cfm.getTaskManager(ally);
		
		DeployedFleetMemberAPI dfm = cfm.getDeployedFleetMember(ship);
		if(Global.getCombatEngine().isEntityInPlay(carrier) == false)
			return;
		
		//if we have an assignment to escort something
		if(ctm.getAssignmentFor(ship) != null && ctm.getAssignmentFor(ship).getType() == CombatAssignmentType.RALLY_CIVILIAN)
		{
			return;
		}

		ship.setHullSize(HullSize.FRIGATE);
		ship.resetDefaultAI();
		if(ship.getHullSize() == HullSize.FRIGATE)
		{
			CombatFleetManagerAPI.AssignmentInfo assign = ctm.createAssignment(CombatAssignmentType.RALLY_CIVILIAN,cfm.createWaypoint(carrier.getLocation(),ally),false);	
			ctm.giveAssignment(dfm,assign,false);	
			Global.getCombatEngine().getCustomData().put("armaa_strikecraft_hasWaypoint"+ship.getId(),true);		
		}
	}

	public boolean checkRefitStatus(ShipAPI ship)
	{
		boolean player = ship == Global.getCombatEngine().getPlayerShip();		
		boolean needsrefit = false;
		boolean ally = ship.isAlly();
		CombatFleetManagerAPI cfm = Global.getCombatEngine().getFleetManager(ship.getOwner()); 
		CombatTaskManagerAPI ctm = cfm.getTaskManager(ally);
		
		if((player && !Global.getCombatEngine().isUIAutopilotOn() && ship.getShipAI() == null))
		{
			ship.setHullSize(HullSize.FRIGATE);
			ship.resetDefaultAI();
		}
		
		if (player && !ship.isFinishedLanding()) 
			Global.getCombatEngine().maintainStatusForPlayerShip("AceSystem1", "graphics/ui/icons/icon_repair_refit.png","/ - WARNING - /", "REFIT / REARM NEEDED( PRESS " + Global.getSettings().getControlStringForEnumName("C2_TOGGLE_AUTOPILOT") + " )" ,true);
		
		if((!player && canRefit(ship)) || (player && !Global.getCombatEngine().isUIAutopilotOn()))
		{
			if(!ship.isFinishedLanding())
			{
				needsrefit  = true;
				retreatToRefit(ship);
			}
			
			armaa_combat_docking_AI DockingAI = new armaa_combat_docking_AI(ship);
			if(ship.controlsLocked() && !ship.getShipAI().needsRefit())
			{
				if(ship.isFinishedLanding())
				{
					ship.setAnimatedLaunch();
				}										
				if(ctm.getAssignmentFor(ship) != null && ctm.getAssignmentFor(ship).getType() == CombatAssignmentType.RALLY_CIVILIAN)
					ctm.removeAssignment(ctm.getAssignmentFor(ship));
				
				ship.setShipAI(DockingAI);
			}
			if(!ship.isFinishedLanding() && getNearestCarrier(ship) != null && MathUtils.getDistance(ship, getNearestCarrier(ship)) < 1500f)
			{
				if(ctm.getAssignmentFor(ship) != null && ctm.getAssignmentFor(ship).getType() == CombatAssignmentType.RALLY_CIVILIAN)
					ctm.removeAssignment(ctm.getAssignmentFor(ship));					
				//Do init -once-
					Global.getCombatEngine().getCustomData().put("armaa_strikecraftisLanding"+ship.getId(),true);
					ship.setShipAI(DockingAI);
					DockingAI.init();
			}
			else
			{
				if(Global.getCombatEngine().getCustomData().get("armaa_strikecraftLanded"+ship.getId()) instanceof Boolean)
				{
					boolean hasLanded = (boolean)Global.getCombatEngine().getCustomData().get("armaa_strikecraftLanded"+ship.getId());
					if(player && !hasLanded && ship.isFinishedLanding())
					{
						Random rand = new Random();
						String txt =  landingLines_Critical.get(rand.nextInt(landingLines_Critical.size()));
						if(ship.getHullLevel() > 0.45f)
							txt =  landingLines_Good.get(rand.nextInt(landingLines_Good.size()));
						Vector2f texLoc;
						if(txt.contains("$hisorher"))
						{
							FullName.Gender gender = ship.getCaptain().getGender();
							String title = "";
							switch(gender)
							{
								case FEMALE:
								title = "ma'am";
								case MALE:
								title = "sir";
								case ANY:
								title = "sir";
								break;
							}
								String tempTxt = txt;
								txt = tempTxt.replace("$hisorher",title);								
						}
						if(DockingAI.getCarrier() != null)
							texLoc = DockingAI.getCarrier().getLocation();
						else
							texLoc = ship.getLocation();
						if(Math.random() <= 35f)
						{
							ship.getFluxTracker().showOverloadFloatyIfNeeded(txt, Color.white, 2f, true);
							Global.getSoundPlayer().playSound("ui_noise_static", 1f, 1f, texLoc,new Vector2f());
						}
						Global.getCombatEngine().getCustomData().put("armaa_strikecraftLanded"+ship.getId(),true);
					}
				}
				Global.getCombatEngine().getCustomData().put("armaa_strikecraftRefitTime"+ship.getId(),DockingAI.getCarrierRefitRate());
			}
		}
		return needsrefit;
	}	
    @Override
    public void advanceInCombat(ShipAPI ship, float amount)
    {
		if(Global.getCombatEngine().getPlayerShip() == ship)
		{
			warningInterval.advance(amount);
			for(ShipAPI pdShip :CombatUtils.getShipsWithinRange(ship.getLocation(),2000f))
			{
				if(!pdShip.isAlive() || pdShip.isStationModule())
					continue;
				if(pdShip.getOwner() == ship.getOwner())
					continue;
				
				if(pdShip.getCaptain() == null || pdShip.getCaptain().isDefault())
					continue;
				
				if(pdShip.getCaptain().getStats().getSkillLevel("point_defense") > 0)
				{
					if(warningInterval.intervalElapsed())
					{
						Vector2f loc = new Vector2f(pdShip.getLocation().x,pdShip.getLocation().y+pdShip.getCollisionRadius()+20f);
						if(ship.getShipTarget() != null)
							if(ship.getShipTarget() == pdShip)
								loc = new Vector2f(pdShip.getLocation().x+pdShip.getCollisionRadius(),pdShip.getLocation().y+pdShip.getCollisionRadius()+80f);
						SpriteAPI warningSprite = Global.getSettings().getSprite("misc", "armaa_pdWarning");
						MagicRender.battlespace(
									warningSprite,
									loc,
									pdShip.getVelocity(),
									new Vector2f(64,64),
									new Vector2f(),
									0f,
									0f,
									new Color(217,102,37,200), 
									false,
									0.1f,
									0.3f,
									0.1f
							);
					}
				}
			}
		}
		
		if(Global.getCombatEngine() != null)
		{
			if(!(Global.getCombatEngine().getCustomData().get("armaa_strikecraftLanded"+ship.getId()) instanceof Boolean))
				Global.getCombatEngine().getCustomData().put("armaa_strikecraftLanded"+ship.getId(),false);
		
			if(Global.getCombatEngine().getCustomData().get("armaa_strikecraftPilot"+ship.getId()) instanceof Float == false)
			{
				getPilotPersonality(ship);
			}
			else
				HPPercent = (float)Global.getCombatEngine().getCustomData().get("armaa_strikecraftPilot"+ship.getId());
		}

		if(ship.getCollisionClass() == CollisionClass.SHIP)
			ship.setCollisionClass(CollisionClass.FIGHTER);

		CombatUIAPI ui = Global.getCombatEngine().getCombatUI();
		
		checkMalfChance(ship);
		
		if(ship.getCurrentCR() <= 0.4f && Global.getCombatEngine().getCombatUI() != null && ship.getOriginalOwner() != -1)
		{
			giveCrWarning(ship);
		}
			
		float CurrentHull = ship.getHitpoints();
		float MaxHull = ship.getMaxHitpoints();
		float CurrentCR = ship.getCurrentCR();
		boolean needsrefit = false;
		if (((CurrentHull <= MaxHull * HPPercent) || (CurrentCR < CRmarker) || (needsReload(ship))) && canRefit(ship))
		{					
			needsrefit = checkRefitStatus(ship);
		}
				
		if(ship.getFluxTracker().isOverloaded())
		{
			textInterval.advance(amount);
			float shipRadius = armaa_utils.effectiveRadius(ship);
			if(textInterval.intervalElapsed())
			{
				//if(Math.random() < 0.05f)
				//{
					for (int i = 0; i < 1; i++) 
					{
						
						Vector2f targetPoint = MathUtils.getRandomPointInCircle(ship.getLocation(), (shipRadius * 0.75f + 15f) * 1f);
						Vector2f anchorPoint = MathUtils.getRandomPointInCircle(ship.getLocation(), shipRadius);
						AnchoredEntity anchor = new AnchoredEntity(ship, anchorPoint);
						float thickness = (float) Math.sqrt(((shipRadius * 0.025f + 5f) * 1f) * MathUtils.getRandomNumberInRange(0.75f, 1.25f)) * 3f;
						Color coreColor = new Color(TEXT_COLOR.getRed(), TEXT_COLOR.getGreen(), TEXT_COLOR.getBlue(), 255);
						Color overloadColor = new Color(ship.getOverloadColor().getRed(),ship.getOverloadColor().getGreen(), ship.getOverloadColor().getBlue(), 100);
						EmpArcEntityAPI arc = Global.getCombatEngine().spawnEmpArcPierceShields(ship, targetPoint, anchor, anchor, DamageType.ENERGY,
							0f, 0f, shipRadius, null, thickness, ship.getOverloadColor(), coreColor);
					}
						if(!Global.getCombatEngine().hasAttachedFloaty(ship))
							Global.getCombatEngine().addFloatingTextAlways(new Vector2f(ship.getLocation().x,ship.getLocation().y+ship.getCollisionRadius()), "Overloaded!", 20f, TEXT_COLOR, ship, 1f, 1f, 1f, 1f, 1f, .5f); 
				//}
			}
		}

		if(ui != null)
		{
			if(canRetreat(ship) || ship.getTravelDrive().isOn() || needsrefit)
			{
				ship.setHullSize(HullSize.FRIGATE);
				SpriteAPI repairSprite = Global.getSettings().getSprite("ui", "icon_repair_refit");
				if(needsrefit && !Global.getCombatEngine().isPaused() && ship.getOwner() == 0)
				{
					MagicRender.objectspace(
								repairSprite,
								ship,
								new Vector2f(0f,ship.getCollisionRadius()),
								new Vector2f(),
								new Vector2f(24,24),
								new Vector2f(),
								0f,
								0f,
								false,
								new Color(0,200,0,200), 
								true,
								0f,
								amount,
								0f,
								true
						);
				}
			}

			else
			{
				drawHud(ship);
				ship.setHullSize(HullSize.FIGHTER);
			}
		}
		
		if(ship.getOriginalOwner() == -1 || Global.getCombatEngine().isCombatOver() || Global.getCombatEngine().isPaused())
			ship.setHullSize(HullSize.FRIGATE);
		
		checkWaypoint(ship);
    }
	
	public static class StrikeCraftDamageListener implements DamageListener {
		ShipAPI ship;
		
		public StrikeCraftDamageListener(ShipAPI ship) 
		{
			this.ship = ship;
		}

		public void reportDamageApplied(Object source, CombatEntityAPI target, ApplyDamageResultAPI result) 
		{
			float totalDamage = result.getDamageToHull() + result.getTotalDamageToArmor();
			if(source instanceof WeaponAPI)
			{
				WeaponAPI wep = (WeaponAPI)source;
				ShipAPI ship = (ShipAPI) target;
				
				//We dont have the armor to tank beams like larger ships, back off if we start to take too much hull/armor damage
				if(wep.isBeam() && totalDamage > 20)
				{
					ShipwideAIFlags flags = ship.getAIFlags();
					flags.setFlag(ShipwideAIFlags.AIFlags.BACK_OFF,1f);
					flags.setFlag(ShipwideAIFlags.AIFlags.DO_NOT_PURSUE,1f);
				}
			}
			
			//Damage floaties since they aren't created for fighter hullsize
			if(ship.getHullSize() == HullSize.FIGHTER && Global.getCombatEngine().getPlayerShip() == ship)
			{
				float shipRadius = armaa_utils.effectiveRadius(ship);
				Vector2f anchorPoint = MathUtils.getRandomPointInCircle(ship.getLocation(), shipRadius);
				Global.getCombatEngine().addFloatingDamageText(anchorPoint, result.getTotalDamageToArmor(), Color.yellow, ship, (CombatEntityAPI)source);
				anchorPoint.setY(anchorPoint.getY()-20);
				Global.getCombatEngine().addFloatingDamageText(anchorPoint, result.getDamageToHull(), Color.red, ship, (CombatEntityAPI)source);
				anchorPoint.setY(anchorPoint.getY()-20);
				Global.getCombatEngine().addFloatingDamageText(anchorPoint, result.getDamageToShields(), new Color(125,125,200,255), ship, (CombatEntityAPI)source);					
			}
		}
	}
	
	//This listener ensures we die properly
	public static class StrikeCraftDeathMod implements DamageTakenModifier, AdvanceableListener 
	{
		protected ShipAPI ship;
		public StrikeCraftDeathMod(ShipAPI ship) {
			this.ship = ship;
		}
		
		public void advance(float amount) 
		{

		}

		public String modifyDamageTaken(Object param,CombatEntityAPI target, DamageAPI damage, Vector2f point, boolean shieldHit) 
		{
			
			if(!(target instanceof ShipAPI))
				return null;
			
			if(shieldHit)
				return null;
			float multiplier = 1f;
			switch(damage.getType())
			{
				case KINETIC:
				multiplier = 0.5f;
				break;
				
				case HIGH_EXPLOSIVE:
				multiplier = 2f;
				break;
				
				case FRAGMENTATION:
				multiplier = 0.25f;
			}
			ShipAPI s = (ShipAPI) target;
			float damageVal = damage.getDamage();
			float armor = DefenseUtils.getArmorValue(s,point);
			if(damageVal*(damageVal/(armor+Math.max(damageVal*0.05,damageVal))) > s.getHitpoints() || s.getHitpoints() <= 0)
				s.setHullSize(HullSize.FRIGATE);

			String id = "strikecraft_death";
			return id;
		}
	}
}

