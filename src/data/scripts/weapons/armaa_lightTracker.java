package data.scripts.weapons;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.AnimationAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
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
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.util.FaderUtil;
import org.magiclib.util.MagicRender;


public class armaa_lightTracker implements EveryFrameWeaponEffectPlugin {

    private boolean runOnce = false;
    private WeaponAPI head;
    private Color base = null;
    private FaderUtil Sussy = new FaderUtil(1f, 1f, 1f);
    private FaderUtil Pulsating = new FaderUtil(1f, 2f, 2f, true, true);
	private IntervalUtil timer = new IntervalUtil(2f,2f);
	private int toggle = 0;
    
    public armaa_lightTracker() {
        Sussy.fadeIn();
        Pulsating.fadeIn();
    }

    public void init(WeaponAPI weapon)
    {
        runOnce = true;
		//need to grab another weapon so some effects are properly rendered, like lighting from glib
        for (WeaponAPI w : weapon.getShip().getAllWeapons()) 
		{
            switch (w.getSlot().getId()){ 
                case "WS0006":
                    if(head==null) 
                        head = w;
			}
                    
		}
	}
    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) 
	{
		if(!runOnce)
			init(weapon);
		ShipAPI ship = weapon.getShip();
		weapon.setCurrAngle(head.getCurrAngle());
        // Refit screen check
		SpriteAPI sprite = weapon.getSprite();
		
		if(ship == null || sprite == null)
			return;
        if (weapon.getShip().getOwner() == -1 || !ship.isAlive()) {weapon.getSprite().setColor(new Color(0, 0, 0, 0));return;} //blinker off ig		
		if (engine.isPaused()) return;	
        weapon.getSprite().setAdditiveBlend();
        Sussy.advance(amount);
        Pulsating.advance(amount);
		timer.advance(amount);
        
        if (base == null) {base = sprite.getColor();}
        if (ship.isAlive() && !ship.getFluxTracker().isOverloaded()) {
            if (ship.getFluxTracker().isVenting()) {
				//toggle = 0;
                Sussy.fadeOut();
            } 
			/*else 
			{
				if(timer.intervalElapsed())
					toggle = toggle == 0 ? 1:0;
				if(toggle == 0)
				{
					Sussy.fadeOut();
				}
				else
					Sussy.fadeIn();
            }*/
			else
				Sussy.fadeIn();
        }
        float alphaMult = Sussy.getBrightness() * (0.75f + Pulsating.getBrightness() * 0.25f);
        if (ship.getFluxTracker().isOverloaded()) {
			//toggle = 1;
			Sussy.fadeIn();
            alphaMult = (float) Math.random() * Sussy.getBrightness();
        }
        Color color = Misc.scaleAlpha(base, alphaMult);
        sprite.setColor(color);
    }
}
