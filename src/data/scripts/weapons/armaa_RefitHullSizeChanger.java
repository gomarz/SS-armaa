package data.scripts.weapons;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.*;
import com.fs.starfarer.api.combat.WeaponAPI;

import com.fs.starfarer.api.util.IntervalUtil;
import java.awt.Color;


public class armaa_RefitHullSizeChanger implements EveryFrameWeaponEffectPlugin {

    private boolean runOnce = false, lockNloaded = false;
    private WeaponAPI torso;
	public int frame = 7;
    private IntervalUtil interval = new IntervalUtil(0.08f, 0.08f);
	private IntervalUtil interval2;
	private Color ogColor;

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) 
	{
        ShipAPI ship = weapon.getShip();
		
		if(ship.getHullSize() != HullSize.FRIGATE)
		{
			ship.setHullSize(HullSize.FRIGATE);	
			ship.resetDefaultAI();
			runOnce = true;
			return;
		}
	
    }
}
