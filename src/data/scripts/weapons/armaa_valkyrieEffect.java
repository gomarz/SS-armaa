package data.scripts.weapons;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.Global;
import java.awt.Color;
import java.util.List;
import org.lazywizard.lazylib.MathUtils;
import com.fs.starfarer.api.util.IntervalUtil;

public class armaa_valkyrieEffect implements EveryFrameWeaponEffectPlugin  {
	
	private IntervalUtil interval = new IntervalUtil(0.08f, 5f);	
    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) 
	{

        ShipAPI ship = weapon.getShip();
		
		List<ShipAPI> children = ship.getChildModulesCopy();
		interval.advance(amount);
		if(children != null)
			for(ShipAPI m: children)	
			{
				if(m.getStationSlot() == null)
					continue;
				if(m.controlsLocked() == false)
				{
					m.setControlsLocked(true);
					m.setShipAI(null);

				}
				else
				{
					for(WeaponAPI wep : m.getAllWeapons())
					{
						if(wep.getSlot().getId().equals("TRUE_GUN2"))
							wep.setCurrAngle(m.getFacing()+90f);
					}
				}
				if(ship.areAnyEnemiesInRange() && interval.intervalElapsed() && Math.random() > 0.70f)
				{
					float variance = MathUtils.getRandomNumberInRange(-0.2f,0.2f);				
					Global.getSoundPlayer().playSound("disabled_small_crit", 1f+variance, 1f, m.getLocation(), m.getVelocity());						
					engine.addNebulaSmokeParticle(
					m.getLocation(), 
					m.getVelocity(), 
					 40f * (0.75f + (float) Math.random() * 0.5f),
					3f + 1f * (float)Math.random()*2f,
					0f,
					0f,
					1f,
					Color.white);
					m.resetDefaultAI();					
					if(m.isRetreating())
						m.setRetreating(false, false);
					m.setControlsLocked(false);
					m.setStationSlot(null);
					new Color(155,155,155,200);
					
				}				
		
				m.ensureClonedStationSlotSpec();
				if(m.getAllWings() == null || m.getAllWings().size() == 0)
					continue;
				for(ShipAPI fighter: m.getAllWings().get(0).getWingMembers())
				{
					m.getAllWings().get(0).orderReturn(fighter);
				}
			}

	}
}
