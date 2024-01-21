package data.scripts.weapons;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.FaderUtil;
import com.fs.starfarer.api.util.Misc;
import java.awt.Color;

public class armaa_ceylonLights implements EveryFrameWeaponEffectPlugin {
    private Color base = null;
    private FaderUtil Sussy = new FaderUtil(1f, 1f, 1f);
    private FaderUtil Pulsating = new FaderUtil(1f, 2f, 2f, true, true);
    
    public armaa_ceylonLights() {
        Sussy.fadeIn();
        Pulsating.fadeIn();
    }
    
    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        // Refit screen check
		ShipAPI ship = weapon.getShip();
		SpriteAPI sprite = weapon.getSprite();
		
		if(ship == null || sprite == null)
			return;
        if (weapon.getShip().getOwner() == -1 || !ship.isAlive()) {weapon.getSprite().setColor(new Color(0, 0, 0, 0));return;} //blinker off ig		
		if (engine.isPaused()) return;	
        weapon.getSprite().setAdditiveBlend();
        Sussy.advance(amount);
        Pulsating.advance(amount);
        
        if (base == null) {base = sprite.getColor();}
        if (ship.isAlive() && !ship.getFluxTracker().isOverloaded()) {
            if (ship.getFluxTracker().isVenting()) {
                Sussy.fadeOut();
            } else {
                Sussy.fadeIn();
            }
        }
        float alphaMult = Sussy.getBrightness() * (0.75f + Pulsating.getBrightness() * 0.25f);
        if (ship.getFluxTracker().isOverloaded()) {
            alphaMult = (float) Math.random() * Sussy.getBrightness();
        }
        Color color = Misc.scaleAlpha(base, alphaMult);
        sprite.setColor(color);
    }
}