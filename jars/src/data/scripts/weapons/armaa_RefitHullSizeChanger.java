package data.scripts.weapons;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.AnimationAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.*;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.CombatEngineLayers;

import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.magiclib.util.MagicAnim;
import org.magiclib.util.MagicRender;
import java.awt.Color;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
//import org.lazywizard.lazylib.CombatUtils;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.Global;
import org.magiclib.util.MagicRender;


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
