package data.scripts.weapons;
import com.fs.starfarer.api.AnimationAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.magiclib.util.MagicAnim;
import org.magiclib.util.MagicRender;
import org.magiclib.util.MagicUI;
import java.awt.Color;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.Global;
import data.scripts.ai.armaa_hangar_drone_AI;
import com.fs.starfarer.api.combat.listeners.AdvanceableListener;
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.combat.listeners.DamageListener;
import com.fs.starfarer.api.combat.*;
import java.util.ArrayList;
import java.util.List;
import data.scripts.util.armaa_utils;



public class armaa_hangerCoreEffect implements EveryFrameWeaponEffectPlugin, OnFireEffectPlugin
{
    private List<ShipAPI> alreadyRegisteredBits = new ArrayList<ShipAPI>();


    public void init(ShipAPI ship)
    {

    }

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) 
	{
		ShipAPI ship  = weapon.getShip();
		List<ShipAPI> cloneList = new ArrayList<>(alreadyRegisteredBits);
		if(cloneList.size() > 0)
			weapon.setAmmo(0);
		for (ShipAPI drone : cloneList) {
			if (!engine.isEntityInPlay(drone) || !drone.isAlive()) 
			{
				alreadyRegisteredBits.remove(drone);
			}
			
			else
			{
				if(!ship.isAlive() || ship.getHitpoints() <= 0 || !Global.getCombatEngine().isEntityInPlay(ship))
				{
					drone.setHitpoints(0f);
					Global.getCombatEngine().removeEntity(drone);
				}
				drone.getSpriteAPI().setColor(new Color(0f,0f,0f,0f));
				drone.setFacing(ship.getFacing());
				drone.setCollisionClass(CollisionClass.NONE);
				drone.getVelocity().set(ship.getVelocity());
				armaa_utils.setLocation(drone,ship.getShieldCenterEvenIfNoShield());
				if(drone.getSystem().getAmmo() > 0)
				{
					drone.useSystem();
					
				}
				drone.setShipAI(null);
			}
		}		
		
    }

	public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) 
	{
		engine.getFleetManager(weapon.getShip().getOwner()).setSuppressDeploymentMessages(true); 
		ShipAPI bit2  = engine.getFleetManager(weapon.getShip().getOwner()).spawnShipOrWing("eis_havoc_wing_dummy",weapon.getShip().getLocation(),weapon.getShip().getFacing());
		bit2.setAnimatedLaunch();
			//bit2.cloneVariant
		if (!alreadyRegisteredBits.contains(bit2) && engine.isEntityInPlay(bit2) && bit2.isAlive()) 
		{
			alreadyRegisteredBits.add(bit2);
		}
		engine.getFleetManager(weapon.getShip().getOwner()).setSuppressDeploymentMessages(false); 

	}

}
