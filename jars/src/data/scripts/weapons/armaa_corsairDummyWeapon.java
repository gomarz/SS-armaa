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
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

public class armaa_corsairDummyWeapon implements EveryFrameWeaponEffectPlugin {

    private boolean runOnce = false, runOnce2 = false;
	private boolean quarterdamage,fiftydamage,seventydamage,destroyed = false;
	private WeaponAPI trueWeapon;
	private float originalArmPos = 0f;
	private final float TORSO_OFFSET = 1, RIGHT_ARM_OFFSET = -10;
	private IntervalUtil interval = new IntervalUtil(1f,1f);
	private float increment = 0f;
    public void init(WeaponAPI weapon)
    {
        runOnce = true;
		//need to grab another weapon so some effects are properly rendered, like lighting from glib
        for (WeaponAPI w : weapon.getShip().getAllWeapons()) 
		{
            switch (w.getSlot().getId()) 
			{
                case "TRUE_GUN":
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
		
		weapon.setCurrAngle(trueWeapon.getCurrAngle());
		
		for(ShipAPI m : weapon.getShip().getChildModulesCopy())
		{
			m.ensureClonedStationSlotSpec();

			if( m.getStationSlot() != null)
			{
				m.setHullSize(HullSize.FRIGATE);
				//String inner = "graphics/tahlan/fx/tahlan_shellshield.png";
				//String outer = "graphics/tahlan/fx/tahlan_tempshield_ring.png";

				//m.getShield().setRadius(m.getShieldRadiusEvenIfNoShield(),inner,outer);
				if( m.getStationSlot().getId().equals("WS0005"))
				{
					float global = weapon.getShip().getFacing();
					float aim = MathUtils.getShortestRotation(global, trueWeapon.getCurrAngle());
					m.setFacing(global + 1 * (TORSO_OFFSET * 0.5f + aim * 0.3f + RIGHT_ARM_OFFSET * 0.25f));
					m.getShield().forceFacing(weapon.getShip().getFacing()+90f+increment);					
					if(m.getHullLevel() <= 0.50f || weapon.getShip().getHullLevel() <= 0.50f || weapon.getShip().getFluxLevel() > 0.80f)
					{
						m.getShield().toggleOn();
					//m.getShield().getLocation().set(weapon.getShip().getShieldCenterEvenIfNoShield());
					}
					else
					{
						m.getShield().toggleOff();
					}
					increment+=1+5*(1-m.getHullLevel());
					if(armaa_utils.estimateIncomingDamage(m) >= 200f && (m.getFluxLevel() > 0.90f))
					{
						m.useSystem();
					}
				}
			}			
		}
		interval.advance(amount);
		if(interval.intervalElapsed())
		{
			weapon.getShip().syncWeaponDecalsWithArmorDamage();
		}
		/*
		if(weapon.getShip().getFleetMember() == null)
			return;
		if(weapon.getShip().getFleetMember().getFleetData().getFleet().getMemoryWithoutUpdate().get("$HVB_armaa_bounty_legiomech") != null)
		{
			//Global.getSoundPlayer().playCustomMusic(0, 5, "music_armaa_pirate_market_neutral", true); 
			if(!runOnce2)
			{
				Global.getCombatEngine().getCombatUI().addMessage(1,weapon.getShip(),Color.yellow,"Hel Corsair Pilot",Color.white,":",Color.white,"HC-1, arriving at the AO.  Captain " + Global.getSector().getPlayerPerson().getName().getFirst() + ". Let's have fun, shall we? This data will be vital.");
				Global.getSoundPlayer().playCustomMusic(0, 5, "music_armaa_legioMech_bounty", true); 				
				runOnce2 = true;
			}
			
			if(weapon.getShip().getHullLevel() <= .75f && !quarterdamage)
			{
				quarterdamage = true;
				Global.getCombatEngine().getCombatUI().addMessage(1,weapon.getShip(),Color.yellow,"Hel Corsair Pilot",Color.white,":",Color.white,"Oh, marvelous! ...but this is within expected parameters. Let's continue.");
			}
			if(weapon.getShip().getHullLevel() <= .50f && !fiftydamage)
			{
				fiftydamage = true;
				Global.getCombatEngine().getCombatUI().addMessage(1,weapon.getShip(),Color.yellow,"Hel Corsair Pilot",Color.white,":",Color.white,"..Hmm. Interesting.");
			}
			if(weapon.getShip().getHullLevel() <= .30f && !seventydamage)
			{
				seventydamage = true;
				Global.getCombatEngine().getCombatUI().addMessage(1,weapon.getShip(),Color.yellow,"Hel Corsair Pilot",Color.white,":",Color.white,"Did I miscalculate? \u2014 no, someone like you shouldn't exist.");
			}
			if(weapon.getShip().getHullLevel() <= 0f && !destroyed)
			{
				destroyed = true;
				Global.getCombatEngine().getCombatUI().addMessage(1,weapon.getShip(),Color.yellow,"Hel Corsair Pilot",Color.white,":",Color.white,"Mmm.. how am I going to explain this to the client?");
			}
*/			
		//}
	}
}
