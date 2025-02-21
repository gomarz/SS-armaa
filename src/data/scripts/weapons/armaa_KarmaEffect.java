package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.opengl.Display;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.impl.campaign.ids.Skills;
import java.awt.*;

import static org.lwjgl.opengl.GL11.*;

public class armaa_KarmaEffect implements EveryFrameWeaponEffectPlugin {
    private static final float[] COLOR_NORMAL = {255f / 255f, 100f / 255f, 20f / 255f};
    private static final float MAX_JITTER_DISTANCE = 0.2f;
    private static final float MAX_OPACITY = 1f;

    private static float EFFECT_RANGE = 600f;
   //public static final String SPRITE_PATH = "graphics/fx/eis_revengeance.png";
    public static final float ROTATION_SPEED = 5f;
    public static final Color COLOR = new Color(100, 155, 225, 166);
    
    //private final boolean basedonwhat = Global.getSettings().getBoolean("VengeanceSFX");
    //private boolean loaded = false;
    private float rotation = 0f;
    private float opacity = 0f;
    private SpriteAPI sprite = Global.getSettings().getSprite("misc", "wormhole_ring");

    private IntervalUtil interval = new IntervalUtil(0.05f,0.1f);

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (engine == null || !engine.isUIShowingHUD() || engine.isUIShowingDialog() || engine.getCombatUI().isShowingCommandUI()) {
            return;
        }
        
        ShipAPI ship = weapon.getShip();
        if (ship == null) {
            return;
        }

        boolean player = ship == engine.getPlayerShip();
		
        //--------------------
        // Revengeance section
        //--------------------

        /*if (sprite == null) {
            // Load sprite if it hasn't been loaded yet - not needed if you add it to settings.json
            if (!loaded) {
                try {
                    Global.getSettings().loadTexture(SPRITE_PATH);
                } catch (IOException ex) {
                    throw new RuntimeException("Failed to load sprite '" + SPRITE_PATH + "'!", ex);
                }

                loaded = true;
            }
            
        }*/

        if (ship.getSystem().isActive() || !player || ship.isHulk() || ship.isPiece() || !ship.isAlive() || ship.getSystem().isOutOfAmmo()) {
            opacity = Math.max(0f,opacity-2f*amount);
        } else {
            opacity = Math.min(1f,opacity+4f*amount);
        }

        final Vector2f loc = ship.getLocation();
        final ViewportAPI view = Global.getCombatEngine().getViewport();
        if (view.isNearViewport(loc, EFFECT_RANGE)) {
            glPushAttrib(GL_ENABLE_BIT);
            glMatrixMode(GL_PROJECTION);
            glPushMatrix();
            glViewport(0, 0, Display.getWidth(), Display.getHeight());
            glOrtho(0.0, Display.getWidth(), 0.0, Display.getHeight(), -1.0, 1.0);
            glEnable(GL_TEXTURE_2D);
            glEnable(GL_BLEND);
            float scale = Global.getSettings().getScreenScaleMult();
            float adjustedRange = ship.getMutableStats().getSystemRangeBonus().computeEffective(EFFECT_RANGE);
            if (ship.isFrigate() || ship.isDestroyer()) {adjustedRange *= 0.75;}
            float radius = ((adjustedRange+ship.getCollisionRadius()) * 2f * scale / view.getViewMult());
            sprite.setSize(radius, radius);
            sprite.setColor(COLOR);
            sprite.setAdditiveBlend();
            sprite.setAlphaMult(0.4f*opacity);
            sprite.renderAtCenter(view.convertWorldXtoScreenX(loc.x) * scale, view.convertWorldYtoScreenY(loc.y) * scale);
            sprite.setAngle(rotation);
            glPopMatrix();
            glPopAttrib();
        }

        // We can just jump out here. Stops the rotation while paused and none of the other stuff needs to run while paused
        if (Global.getCombatEngine().isPaused()) {
            return;
        }

        // Spin it
        rotation += ROTATION_SPEED * amount;
        if (rotation > 360f){
            rotation -= 360f;
        }



    }
}