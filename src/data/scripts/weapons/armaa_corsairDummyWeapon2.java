package data.scripts.weapons;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.Global;
import java.awt.Color;
import data.scripts.util.armaa_utils;


public class armaa_corsairDummyWeapon2 implements EveryFrameWeaponEffectPlugin {

    private boolean runOnce = false, runOnce2 = false;
	private WeaponAPI trueWeapon;
	private float originalArmPos = 0f;
	private float increment = 0f;
	private final float TORSO_OFFSET = 1, LEFT_ARM_OFFSET = -10;	
    public void init(WeaponAPI weapon)
    {
        runOnce = true;
		//need to grab another weapon so some effects are properly rendered, like lighting from glib
        for (WeaponAPI w : weapon.getShip().getAllWeapons()) 
		{
            switch (w.getSlot().getId()) 
			{
                case "TRUE_GUN2":
                    if(trueWeapon==null) 
					{
                        trueWeapon = w;
                    }
                    break;
            }
        }
    }
    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) 
	{
		if(!runOnce)
			init(weapon);
		if(trueWeapon == null)
			return;
		ShipAPI ship = weapon.getShip();
		if(ship.getHullSpec().getHullId().contains("legio"))
		{
			if(!ship.getHullSpec().getShipSystemId().equals("tahlan_weaponsoverdrive"))
				ship.getHullSpec().setShipSystemId("tahlan_weaponsoverdrive");
		}
		weapon.setCurrAngle(trueWeapon.getCurrAngle());
		for(ShipAPI m : weapon.getShip().getChildModulesCopy())
		{
			m.ensureClonedStationSlotSpec();

			if( m.getStationSlot() != null)
			{
				if( m.getStationSlot().getId().equals("WS0004"))
				{
					float global = weapon.getShip().getFacing();
					float aim = MathUtils.getShortestRotation(global, trueWeapon.getCurrAngle());
					m.getShield().forceFacing(weapon.getShip().getFacing()-90f+increment);
					m.setFacing(global - 1 * (TORSO_OFFSET * 0.5f - aim * 0.3f - LEFT_ARM_OFFSET * 0.25f));					
					if(m.getHullLevel() <= 0.50f || weapon.getShip().getHullLevel() <= 0.50f || weapon.getShip().getFluxLevel() > 0.80f)
					{					
						m.getShield().toggleOn();
					//m.getShield().getLocation().set(weapon.getShip().getShieldCenterEvenIfNoShield());
					//m.getShield().setCenter(weapon.getShip().getShieldCenterEvenIfNoShield().x,weapon.getShip().getShieldCenterEvenIfNoShield().y);
					}
					else
					{
						m.getShield().toggleOff();
					}
					increment+=1+5*(1-m.getHullLevel());
				}
				if(armaa_utils.estimateIncomingDamage(m) >= 200f && (m.getFluxLevel() > 0.90f))
				{
					m.useSystem();
				}				
			}			
		}

    }
}
