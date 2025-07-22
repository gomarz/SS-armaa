package data.hullmods;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import org.lazywizard.lazylib.MathUtils;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import data.scripts.ai.armaa_combat_docking_AI_fighter;
import data.scripts.ai.armaa_combat_retreat_AI_fighter;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;
import java.util.*;
import java.awt.Color;
import java.util.Random;
import com.fs.starfarer.api.ui.Alignment;
import data.scripts.util.armaa_utils;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import data.scripts.MechaModPlugin;
import org.lwjgl.input.Keyboard;

import org.magiclib.util.MagicIncompatibleHullmods;

import com.fs.starfarer.api.util.IntervalUtil;

public class armaa_skyMindSuiteBeta extends BaseHullMod {
	
	public static final String DATA_PREFIX = "beta_core_skymind_check_";
	public static final String coreType = "BETA CORE";
	private Color COLOR = new Color(0, 106, 0, 50);
	private static final float RETREAT_AREA_SIZE = 2000f;
	private boolean hasLanded;
	public static final String ITEM = Commodities.BETA_CORE;
	private Vector2f landingLoc = new Vector2f();
    private static final Color JITTER_UNDER_COLOR = new Color(50,125,50,50);
    private static final float MAX_TIME_MULT = 1.1f;
    private static final Map<HullSize, Float> ENGAGEMENT_REDUCTION = new HashMap<>();
	private static final int BOMBER_COST_MOD = 10000;
	private static final float FIGHTER_REPLACEMENT_TIME_MULT = .70f;
	private static final float FIGHTER_RATE = 1.25f;
	private static final float CREW_LOSS_MULT = 0.25f;
	private IntervalUtil tracker = new IntervalUtil(0.5f, 1.0f);
	private IntervalUtil tracker2 = new IntervalUtil(0.05f, 0.05f);
	private final List<String> squadChatter_beta =  MechaModPlugin.squadChatter_beta;
    private static final Set<String> BLOCKED_HULLMODS = new HashSet<>();
	
    static
	{
		BLOCKED_HULLMODS.add("armaa_skyMindGamma");
		BLOCKED_HULLMODS.add("armaa_skyMindAlpha");	
    }
	
    static
	{
        ENGAGEMENT_REDUCTION.put(HullSize.FIGHTER, 0.5f);
        ENGAGEMENT_REDUCTION.put(HullSize.FRIGATE, 0.5f);
        ENGAGEMENT_REDUCTION.put(HullSize.DESTROYER, 0.4f);
        ENGAGEMENT_REDUCTION.put(HullSize.CRUISER, 0.3f);
        ENGAGEMENT_REDUCTION.put(HullSize.CAPITAL_SHIP, 0.2f);
	}
	
	public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) 
	{
		int numFighters = 0;
		int extraCrew = 0;	
	}
	
	//@Override
	public void applyEffectsAfterShipCreation(ShipAPI ship, java.lang.String id)
	{
        for (String tmp : BLOCKED_HULLMODS)
        {
			if(ship.getVariant().getHullMods().contains(tmp))
			{
   				MagicIncompatibleHullmods.removeHullmodWithWarning(ship.getVariant(), tmp, "armaa_skyMindBeta");
			}
        }		
	}
	
	@Override
	public boolean affectsOPCosts() {
		return false;
	}
	
	@Override	
	public boolean isApplicableToShip(ShipAPI ship) 
	{
        final CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
		boolean hasItem = true;
        if (playerFleet != null)
		{
			if(Global.getSector().getPlayerFleet().getCargo().getCommodityQuantity(ITEM) > 0)
				hasItem = true;
			else
				hasItem = false;
        }

		return hasItem && ((!ship.isStationModule() && ship.getVariant().hasHullMod("strikeCraft") && ship.getHullSpec().getFighterBays() == 0 ) || ship.getMutableStats().getNumFighterBays().getModifiedValue() > 0);	
	}

	public String getUnapplicableReason(ShipAPI ship) 
	{
        if (ship == null) 
			return "Can not be assigned";
		
        final CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        if (playerFleet != null)
		{
			final String coreKey = "armaa_skyMind_"+ITEM+"_"+ship.getFleetMemberId();
			final Map<String, Object> data = Global.getSector().getPersistentData();
			if(Global.getSector().getPlayerFleet().getCargo().getCommodityQuantity(ITEM) <= 0 && !data.containsKey(coreKey))
				return "Installation requires 1 BETA Core";
        }
		
		return "Only installable on strikecraft or carriers larger than frigates";
	}

	private final Color HL=Global.getSettings().getColor("hColor");	
	private final Color TT = Global.getSettings().getColor("buttonBgDark");
	private final Color F = Global.getSettings().getColor("textFriendColor");
	private final Color E = Global.getSettings().getColor("textEnemyColor");
	private final Color def = Global.getSettings().getColor("standardTextColor");
	@Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) 
	{
		float pad = 10f;
		float padS = 2f;
		Color[] arr ={Misc.getHighlightColor(),F};
		Color[] arrB ={Misc.getHighlightColor(),F,F};
		Color[] arr2 ={Misc.getHighlightColor(),E};
        //title

		tooltip.addSectionHeading("Details" ,Alignment.MID, 10);
		tooltip.addPara("%s " + "Assigning an AI Core to this vessel %s.", pad,arr, "\u2022", "will grant it's wing all the skills the core possesses");
		tooltip.addPara("%s " + "Only applicable with %s fighters.", pad, arrB, "\u2022","automated");
		tooltip.addPara("%s " + "If this ship is destroyed in combat, the assigned core will be %s.", pad, arr2, "\u2022","permanently lost");
		
		if(ship == null)
			return;

                tooltip.addSectionHeading("=== AI CORE INFO ===",Alignment.MID, 10);

		FighterWingSpecAPI wing = ship.getVariant().getWing(0);
		int wingSize = getWingSize(ship);
		
        if(ship!=null && ship.getVariant()!=null)
		{	
			//boolean crewedWing = false;
            if( wing == null){
                tooltip.addPara(
                        "No wing assigned."
                        ,10
                        ,Misc.getHighlightColor()
                );

            }
				
			else 
			{
				boolean expand = Keyboard.isKeyDown(Keyboard.getKeyIndex("F1"));		
				
                String captain = ship.getCaptain().getNameString();
				boolean hasSpecialString = false;
				String str = "";
				ArrayList<String> squadChatterCopy = new ArrayList<String>();
				for(String s : MechaModPlugin.squadChatter) {
					squadChatterCopy.add(s);
				}

					Random rand = new Random();

					int size = rand.nextInt(squadChatter_beta.size());
					String chatter = squadChatter_beta.get(size);;
					PersonAPI pilot = ship.getCaptain();
					
					final String coreKey = "armaa_skyMind_"+ITEM+"_"+ship.getFleetMemberId();
					final Map<String, Object> data = Global.getSector().getPersistentData();
					if(data.containsKey(coreKey))
					{
						pilot = (PersonAPI)data.get(coreKey);
					}
					
					else
						return;
					tooltip.addSectionHeading(pilot.getId().toUpperCase(),Alignment.MID, 10);
					
					String personality = coreType;
					
					tooltip.addSectionHeading("Persona: " + personality,HL,TT, Alignment.MID, 0f);
					tooltip.beginImageWithText(pilot.getPortraitSprite(), 88).addPara(chatter, 3, def,chatter);
					tooltip.addImageWithText(8);
					tooltip.addRelationshipBar(ship.getCaptain(),5f);
            }
        }    
    }
	
    public static void removePlayerCommodity(final String id) {
        final CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        if (playerFleet == null) {
            return;
        }
        final List<CargoStackAPI> playerCargoStacks = playerFleet.getCargo().getStacksCopy();
        for (final CargoStackAPI cargoStack : playerCargoStacks) {
            if (cargoStack.isCommodityStack() && cargoStack.getCommodityId().equals(id)) {
                cargoStack.subtract(1);
                if (cargoStack.getSize() <= 0) {
                    playerFleet.getCargo().removeStack(cargoStack);
                }
                return;
            }
        }
    }
	
	@Override
	public void advanceInCampaign(FleetMemberAPI member, float amount)
	{
		final String coreKey = "armaa_skyMind_"+ITEM+"_"+member.getId();
        final Map<String, Object> data = Global.getSector().getPersistentData();
		if(!data.containsKey(coreKey))
		{
			// Apparently this can be the case
			if (Misc.getAICoreOfficerPlugin(ITEM) == null) {
				return;
			}
			
			
			PersonAPI core = Misc.getAICoreOfficerPlugin(ITEM).createPerson(ITEM, member.getCaptain().getFaction().getId(), Misc.random);
			core.setAICoreId(ITEM);
			data.put(coreKey,core);
		}
        if (data.containsKey(DATA_PREFIX + member.getId())) {
            return;
        }

		if(!member.getVariant().hasHullMod("armaa_aicoreutilityscript"))
		{
			removePlayerCommodity(ITEM);
			data.put(DATA_PREFIX + member.getId(),"_");	
			member.getVariant().addPermaMod("armaa_aicoreutilityscript");			
		}
	}
    @Override
    public void advanceInCombat(ShipAPI ship, float amount)
    {
		if(ship.getLaunchBaysCopy().isEmpty())
			return;
		//if you inherently have fighterbays, it's probably ok to use default fighter behavior
		if(ship.isStationModule())
			return;
		if((ship.getHullSpec().getFighterBays() > 0 && !ship.isFrigate() && !ship.isFighter()) || ship.getHullSpec().hasTag("strikecraft_with_bays"))
			return;
		
		FighterLaunchBayAPI bay = ship.getLaunchBaysCopy().get(0);
		if(ship.isLanding())
		{
			ShipAPI defaultCarrier = getCarrier(ship);
			if(defaultCarrier != null)
			Global.getCombatEngine().getCustomData().put("armaa_wingCommander_landingLocation_default"+ship.getId(),defaultCarrier);
		}
		//Global.getCombatEngine().addFloatingText(ship.getLocation(), "asdf", 10f, Color.white, ship, 0f, 0f); 
		if(bay.getWing() != null)
		{
			if(!bay.getWing().getWingMembers().isEmpty())
				for(int i = 0; i < bay.getWing().getWingMembers().size();i++)
				{
					ShipAPI fighter =  bay.getWing().getWingMembers().get(i);
					if(fighter == null || fighter.isHulk())
						continue;
					if(fighter != null)
					{
						Object b = Global.getCombatEngine().getCustomData().get("armaa_wingCommander_landingLocation_"+fighter.getId()+"_set");
						boolean done = b instanceof Boolean ? (Boolean)b : false;
						if(fighter.isLiftingOff() && !done)
						{
							float mapWidth = Global.getCombatEngine().getMapWidth() / 2f, mapHeight = Global.getCombatEngine().getMapHeight() / 2f;
							Vector2f rawLL = new Vector2f(-mapWidth, -mapHeight),rawUR = new Vector2f(mapWidth, mapHeight);
							Vector2f landingLoc = null;
							
							//set launch point to the motherships by default
							ShipAPI potentialLaunchPoint = (ShipAPI)Global.getCombatEngine().getCustomData().get("armaa_wingCommander_landingLocation_default"+ship.getId());
							
							//Is there a carrier this index remembers landing at?
							if(Global.getCombatEngine().getCustomData().get("armaa_wingCommander_landingLocation_"+ship.getId()+"_"+i) instanceof ShipAPI)
							{
								//if so, use as launch point
								potentialLaunchPoint = (ShipAPI)Global.getCombatEngine().getCustomData().get("armaa_wingCommander_landingLocation_"+ship.getId()+"_"+i);
								
							}
							
							if( potentialLaunchPoint != null && (!potentialLaunchPoint.isAlive() || potentialLaunchPoint.isHulk()))
								potentialLaunchPoint = null;
							if(potentialLaunchPoint == null)
							{
								potentialLaunchPoint = getCarrier(fighter);								
							}
							if(Global.getCombatEngine().isEntityInPlay(potentialLaunchPoint) && potentialLaunchPoint.isAlive())
							{
								for(FighterLaunchBayAPI wep:potentialLaunchPoint.getLaunchBaysCopy())
								{
									if(wep.getWeaponSlot() != null)
									{
										WeaponSlotAPI w = wep.getWeaponSlot();
										landingLoc = new Vector2f(potentialLaunchPoint.getLocation().x+w.getLocation().y, potentialLaunchPoint.getLocation().y+w.getLocation().x);
										if(Math.random() <= .50f)
											break;
									}
								}
							}
							
							//if we are here, we still have nowhere to land
							if(landingLoc == null)
							{
								if(potentialLaunchPoint != null && potentialLaunchPoint.getLocation() != null && potentialLaunchPoint.isAlive())
									landingLoc = potentialLaunchPoint.getLocation();
								
								else
								{	
									if(fighter.getOwner() == 0)
									{
										if(ship.getLocation().getY() > rawLL.getY() - RETREAT_AREA_SIZE) 
											landingLoc = new Vector2f((ship.getLocation().getX()), ship.getLocation().getY()-2000);
										else
											landingLoc = new Vector2f((rawLL.x+rawUR.x)/2, rawLL.y);
									}
								
									else
										landingLoc = new Vector2f((rawLL.x+rawUR.x)/2, rawUR.y);
								}
							}
								armaa_utils.setLocation(fighter, landingLoc);
								Global.getCombatEngine().getCustomData().put("armaa_wingCommander_landingLocation_"+fighter.getId()+"_set",true);

						}
					}
				}
			tracker.advance(amount);
			if(tracker.intervalElapsed())
			{
				if(!bay.getWing().getReturning().isEmpty())
				{
					doLanding(bay,ship);
				}
			}			
		}
	}		
	
	public int getWingSize(ShipAPI ship)
	{
		FighterWingSpecAPI wing = ship.getVariant().getWing(0);
		int wingSize = 0;
		boolean crewedWing = false;
		if(wing != null)
		{
			for( int i = 0; i < ship.getVariant().getWings().size(); i++)
			{
				FighterWingSpecAPI w = ship.getVariant().getWing(i);
					if(w == null)
						continue;
				crewedWing = w.getVariant().getHullSpec().getMinCrew() == 0;
				
				if(crewedWing)
					wingSize +=w.getNumFighters();
			}
		}
		
		for(String slot: ship.getVariant().getModuleSlots())
		{
			if(!ship.getVariant().getModuleVariant(slot).hasHullMod("armaa_wingCommander"))
				continue;
			for( int i = 0; i < ship.getVariant().getModuleVariant(slot).getWings().size(); i++)
			{
				FighterWingSpecAPI w = ship.getVariant().getModuleVariant(slot).getWing(i);
					if(w == null)
						continue;
				crewedWing = w.getVariant().getHullSpec().getMinCrew() == 0;
				
				if(crewedWing)
					wingSize +=w.getNumFighters();
			}
		}

		return wingSize;
	}
	
	public int getWingSize(ShipVariantAPI ship)
	{
		FighterWingSpecAPI wing = ship.getWing(0);
		int wingSize = 0;
		boolean crewedWing = false;
		if(wing != null)
		{
			for( int i = 0; i < ship.getWings().size(); i++)
			{
				FighterWingSpecAPI w = ship.getWing(i);
					if(w == null)
						continue;
				crewedWing = w.getVariant().getHullSpec().getMinCrew() > 0;
				
				if(crewedWing)
					wingSize +=w.getNumFighters();
			}
		}

		return wingSize;
	}	

	@Override
	public void applyEffectsToFighterSpawnedByShip(ShipAPI fighter, ShipAPI ship, java.lang.String id) 
	{
		if(fighter.getHullSpec().getMinCrew() > 0)
			return;
		
	    final String coreKey = "armaa_skyMind_"+ITEM+"_"+ship.getFleetMemberId();		
        final Map<String, Object> data = Global.getSector().getPersistentData();
		if(data.containsKey(coreKey))
		{
			PersonAPI core = (PersonAPI) data.get(coreKey);
			fighter.setCaptain(core);
		}		
    }
	
	public String getDescriptionParam(int index, HullSize hullSize) 
	{
		return null;
	}

	public ShipAPI getCarrier(ShipAPI ship)
	{
		ShipAPI potentialCarrier = null;
		boolean isFakeFighter = ship.getVariant().getHullSize() == HullSize.FRIGATE ? true : false;
		float distance = 99999f;
		for (ShipAPI carrier : CombatUtils.getShipsWithinRange(ship.getLocation(), 10000f)) 
		{
			if(carrier.getOwner() != ship.getOwner())
				continue;
			if(!carrier.isAlive() || carrier.isHulk())
				continue;
			
			if((carrier.isFighter() && !carrier.getHullSpec().hasTag("strikecraft_with_bays")) || carrier.getHullSpec().getFighterBays() < 1)
				continue;
			
			//If this is a normal fighter we want to get the nearest carrier to the source ship
			if(!isFakeFighter && ship.getWing() != null && ship.getWing().getSourceShip() != null)
			{
				ShipAPI source = ship.getWing().getSourceShip();
				if(distance > MathUtils.getDistance(carrier,source))
				{
					distance = MathUtils.getDistance(carrier,source);
					potentialCarrier = carrier;
					continue;
				}
				
			}
			
			else
			if(( !carrier.isFrigate() || carrier.isStationModule()))
			{
				if(carrier.hasLaunchBays())
				{
					//carriersExist = true;
					potentialCarrier = carrier;
					return potentialCarrier;
				}
			}
		}
		
		return potentialCarrier;
		
	}
	
	public void doLanding(FighterLaunchBayAPI bay, ShipAPI ship)
	{
		for(int i = 0; i < bay.getWing().getReturning().size();i++)
		{
			ShipAPI fighter =  bay.getWing().getReturning().get(i).fighter;
			if(fighter == null)
				continue;
			if(fighter != null)
			{			
				if(bay.getWing().isReturning(fighter))
				{
					boolean carriersExist = false;
					ShipAPI posCarrier = getCarrier(fighter);
					if(posCarrier != null)
					{
						armaa_combat_docking_AI_fighter DockingAI = new armaa_combat_docking_AI_fighter(fighter);
						if(fighter.getShipAI() != DockingAI);
						{
							fighter.setShipAI(DockingAI);
							DockingAI.init();
						}
					}
					
					else
					{
						boolean alreadyHasAI = false;
						if(fighter.getCustomData().get("armaa_wingCommander_fighterRetreat_"+fighter.getId()) instanceof Boolean)
							alreadyHasAI = (Boolean)fighter.getCustomData().get("armaa_wingCommander_fighterRetreat_"+fighter.getId());
						if(!alreadyHasAI)
						{
							armaa_combat_retreat_AI_fighter RetreatAI = new armaa_combat_retreat_AI_fighter(fighter);
							if(fighter.getShipAI() != RetreatAI);
							{
								fighter.setShipAI(RetreatAI);
								fighter.getCustomData().put("armaa_wingCommander_fighterRetreat_"+fighter.getId(),true);
								//DockingAI.init();
							}
						}						
					
					}
				}
					
				if(fighter.isLanding())
				{
					ShipAPI carrierLandingOn;
					for (ShipAPI carrier : CombatUtils.getShipsWithinRange(fighter.getLocation(), 100f)) 
					{
						if(carrier.getOwner() != fighter.getOwner())
							continue;
						if(carrier.isFighter())
							continue;
						if(( !carrier.isFrigate() || carrier.isStationModule() ))
						{
							if(carrier.hasLaunchBays())
							{
								Global.getCombatEngine().getCustomData().put("armaa_wingCommander_landingLocation_"+ship.getId()+"_"+i,carrier);
								break;
							}
						}
					}
				}
				
				if(fighter.isFinishedLanding())
				{
					 bay.land(fighter);
					 Global.getCombatEngine().removeEntity(fighter);
				}						

			}
		}
	}
}