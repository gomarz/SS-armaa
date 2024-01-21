package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lwjgl.opengl.Display;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.impl.campaign.ids.Skills;
import java.awt.*;
import com.fs.starfarer.api.util.Misc;

import java.util.*;
import java.util.List;
import com.fs.starfarer.api.combat.MissileAPI;

import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lazywizard.lazylib.VectorUtils;

import com.fs.starfarer.api.combat.CombatEngineLayers;
import org.magiclib.util.MagicRender;

import static org.lwjgl.opengl.GL11.*;

public class armaa_AWACS implements EveryFrameWeaponEffectPlugin {
    private static final float[] COLOR_NORMAL = {255f / 255f, 100f / 255f, 20f / 255f};
    private static final float MAX_JITTER_DISTANCE = 0.2f;
    private static final float MAX_OPACITY = 1f;
    public static final float RANGE_BOOST = 100f;
	public static final float SPEED_BOOST = 15f;
    private static final float DAMAGE_MALUS = .80f;
    private static float EFFECT_RANGE = 2000f;
    public static final float ROTATION_SPEED = 5f;
    public static final Color COLOR = new Color(15, 215, 55, 200);
	private static final String AWACS_ID = "AWACS_ID";	   

    private float rotation = 0f;
    private float sfxOpacity = 0f;
	private int MAX_DRONES = 3;
    private SpriteAPI sprite = Global.getSettings().getSprite("ceylon", "armaa_ceylonrad");
	private List<ShipAPI> targetList = new ArrayList<ShipAPI>();
	private List<ShipAPI> enemyList = new ArrayList<ShipAPI>();
	private List<DeployedFleetMemberAPI> deployedShips = new ArrayList<DeployedFleetMemberAPI>();
    private static final EnumSet<WeaponAPI.WeaponType> WEAPON_TYPES = EnumSet.of(WeaponAPI.WeaponType.MISSILE,WeaponAPI.WeaponType.BALLISTIC,WeaponAPI.WeaponType.ENERGY);
    private IntervalUtil interval = new IntervalUtil(5f,5f);
	private boolean init = false;
	private boolean activated = false;
	public static float effectLevel = 0.25f;
	
	public static float getEffectLevel()
	{
		return effectLevel;
	}

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) 
	{
        if (engine == null || engine.isPaused()) {
            return;
        }
        ShipAPI ship = weapon.getShip();
        if (ship == null) {
            return;
        }

		int side = ship.getOwner() == 0 ? 1 : 0;
				interval.advance(amount);
		if(side == 0)
		{
			if(!init)
			{
				deployedShips.addAll(Global.getCombatEngine().getFleetManager(side).getAllEverDeployedCopy());
				init = true;
			}
			else
			{
				if(deployedShips.size() != Global.getCombatEngine().getFleetManager(side).getAllEverDeployedCopy().size() && interval.intervalElapsed())
				{
					deployedShips.clear();
					deployedShips.addAll(Global.getCombatEngine().getFleetManager(side).getAllEverDeployedCopy());
					ShipAPI eShip = Global.getCombatEngine().getFleetManager(side).getAllEverDeployedCopy().get(Global.getCombatEngine().getFleetManager(side).getAllEverDeployedCopy().size()-1).getShip();
					String name = eShip.getName();
					if(!eShip.isFighter())
					{
						String str = "\"" + eShip.getHullSpec().getHullNameWithDashClass()  + " approaching the AO from " + VectorUtils.getAngle(ship.getLocation(),eShip.getLocation()) + " degrees.\"";
						if(!MagicRender.screenCheck(0.2f, ship.getParentStation().getLocation()))
							Global.getCombatEngine().getCombatUI().addMessage(1,ship,Misc.getBasePlayerColor(),ship.getParentStation().getName(),Color.white,":",Color.white,str);
						else
							Global.getCombatEngine().addFloatingTextAlways(ship.getParentStation().getLocation(), str, 30f, Color.white, ship, 0f, 0f, 1f, 3f, 2f, 0f); 	
							
					}
				}

			}
		}
        if(ship.isHulk() || !ship.isAlive())
			return;
		if(ship.getParentStation() == null)
			return;
		
		float activeDrones = 0f;
		activeDrones = (float)ship.getDeployedDrones().size()/(float)MAX_DRONES;
        effectLevel = Math.max(0.25f,activeDrones);

        boolean player = ship == engine.getPlayerShip();
	
        if (ship.getSystem().isActive() || !player || ship.isHulk() || ship.isPiece() || !ship.isAlive() || ship.getSystem().isOutOfAmmo()) {
            //opacity = Math.max(0f,opacity-2f*amount);
        } else {
            //opacity = Math.min(1f,opacity+4f*amount);
        }		
        final ViewportAPI view = Global.getCombatEngine().getViewport();

		            float scale = Global.getSettings().getScreenScaleMult();

		//float radius = adjustedRange;
		float adjustedRange = ship.getParentStation().getMutableStats().getSystemRangeBonus().computeEffective(EFFECT_RANGE);
		if (ship.getParentStation().isFrigate() || ship.isDestroyer()) {adjustedRange *= 0.75;}
		final Vector2f loc = ship.getParentStation().getLocation();
		float radius = (adjustedRange*effectLevel)*2f;
        if (view.isNearViewport(loc, adjustedRange*effectLevel)) {		
		MagicRender.singleframe(
					sprite,
					new Vector2f(loc.getX(),loc.getY()),
					new Vector2f(radius,radius),
					rotation,
					ship.getOwner() == 0 ? COLOR : new Color(215,21,16,186),
					false,
					CombatEngineLayers.BELOW_SHIPS_LAYER
			);
		}

        // Spin it
        rotation += ROTATION_SPEED * amount;
        if (rotation > 360f){
            rotation -= 360f;
        }

        for (ShipAPI target : CombatUtils.getShipsWithinRange(ship.getLocation(), adjustedRange*effectLevel)) 
		{
			if (target.getOwner() == ship.getOwner() && !targetList.contains(target))
			{
				targetList.add(target);
			}
			
			else if (target.getOwner() != ship.getOwner() && !target.isHulk() && !enemyList.contains(target))
			{
				enemyList.add(target);
			}
        }		
        List<ShipAPI> purgeList = new ArrayList<ShipAPI>();
        for (ShipAPI target : targetList) 
		{
            if (MathUtils.getDistance(target.getLocation(), ship.getLocation()) <= adjustedRange*effectLevel) 
			{
				if(!ship.getParentStation().getVariant().hasHullMod("fourteenth"))
				{
					target.getMutableStats().getEnergyWeaponRangeBonus().modifyFlat(AWACS_ID, RANGE_BOOST);
					target.getMutableStats().getBallisticWeaponRangeBonus().modifyFlat(AWACS_ID, RANGE_BOOST);
				}
					
				else
				{
					target.getMutableStats().getMaxSpeed().modifyPercent(AWACS_ID,SPEED_BOOST); 
				}
				
				if(ship.getSystem() != null && ship.getSystem().isActive())
				{
					///target.getMutableStats().getAutofireAimAccuracy().modifyFlat(AWACS_ID, AUTOAIM_BONUS * 0.01f);
					///target.getMutableStats().getProjectileSpeedMult().modifyPercent(AWACS_ID, SPEED_BOOST);
				}

                //target.setWeaponGlow(0.7f,COLOR, WEAPON_TYPES);

            } 
			else 
			{
                target.getMutableStats().getEnergyWeaponRangeBonus().unmodify(AWACS_ID);
                target.getMutableStats().getBallisticWeaponRangeBonus().unmodify(AWACS_ID);
                target.getMutableStats().getAutofireAimAccuracy().unmodify(AWACS_ID);
                target.getMutableStats().getProjectileSpeedMult().unmodify(AWACS_ID);
                target.getMutableStats().getMaxSpeed().unmodify(AWACS_ID);
                //target.setWeaponGlow(0f,COLOR, WEAPON_TYPES);
                purgeList.add(target);
            }

        }
		if(ship.getParentStation().getSystem() != null && ship.getParentStation().getSystem().isActive())
		{
			
			if(ship.getParentStation().getVariant().hasHullMod("fourteenth") && !activated)
			{
				for (MissileAPI m : CombatUtils.getMissilesWithinRange(ship.getLocation(), adjustedRange*effectLevel)) 
				{
					if (m.getOwner() != ship.getOwner())
					{
						m.getVelocity().setX(m.getVelocity().x/2);
						m.getVelocity().setY(m.getVelocity().y/2);
					}
				}
			}
			
			for (ShipAPI target : enemyList) 
			{
				if (target.isAlive() && !target.isHulk() && MathUtils.getDistance(target.getLocation(), ship.getLocation()) <= adjustedRange*effectLevel) 
				{
			
					if(!ship.getParentStation().getVariant().hasHullMod("fourteenth"))
					{
						if(!target.isFighter() && !activated && !Global.getCombatEngine().hasAttachedFloaty(target))
						{
							Global.getCombatEngine().addFloatingTextAlways(target.getLocation(), "Damage Reduced!", 30f, Color.orange, target, 0f, 0f, 1f, 3f, 2f, 0f); 	
							
						}
						target.getMutableStats().getEnergyWeaponDamageMult().modifyMult(AWACS_ID, DAMAGE_MALUS);
						target.getMutableStats().getBallisticWeaponDamageMult().modifyMult(AWACS_ID, DAMAGE_MALUS);
						target.getMutableStats().getMissileWeaponDamageMult().modifyMult(AWACS_ID, DAMAGE_MALUS);					
					}
					//target.setWeaponGlow(0.7f,COLOR, WEAPON_TYPES);
				} 
				
				else 
				{
					target.getMutableStats().getEnergyWeaponDamageMult().unmodify(AWACS_ID);
					target.getMutableStats().getBallisticWeaponDamageMult().unmodify(AWACS_ID);
					target.getMutableStats().getMissileWeaponDamageMult().unmodify(AWACS_ID);

					purgeList.add(target);
				}
			}
			activated = true;
		}
		
		if(!ship.getParentStation().getSystem().isActive())
			activated = false;
		
        for (ShipAPI purge : purgeList) {
			targetList.remove(purge);
        }

    }
}