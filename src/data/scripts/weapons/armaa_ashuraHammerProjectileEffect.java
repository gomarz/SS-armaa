package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import static data.scripts.util.armaa_guardualBladeParticleEffect.spawnAcrossWidth;
import java.awt.Color;
import java.util.List;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicRender;

/**
 *
 * @author bra20
 */
public class armaa_ashuraHammerProjectileEffect extends BaseEveryFrameCombatPlugin {

    private MissileAPI proj;
    private float angle;
    private IntervalUtil interval = new IntervalUtil(0.1f, 0.1f);

    public armaa_ashuraHammerProjectileEffect(DamagingProjectileAPI dpapi) {
        this.proj = (MissileAPI) dpapi;
        this.angle = proj.getFacing();
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (Global.getCombatEngine().isPaused()) {
            return;
        }
        if (Global.getCombatEngine().isEntityInPlay(proj) == false) {
            Global.getCombatEngine().removePlugin(this);
        }
        //proj.getSpriteAPI().setColor(new Color(0f, 0f, 0f, 0f));
        proj.setEmpResistance(99);
        if (proj.isFading() || proj.isFizzling()) {
            Vector2f newVel = Misc.getUnitVectorAtDegreeAngle(proj.getFacing()+angle);
            DamagingProjectileAPI newProj = (DamagingProjectileAPI) Global.getCombatEngine().spawnProjectile(
                    proj.getSource(),
                    proj.getWeapon(),
                    proj.getWeapon().getId() + "_return",
                    proj.getLocation(),
                    proj.getFacing(),
                    newVel
            );
            Global.getCombatEngine().addPlugin(new armaa_ashuraHammerGuidance(newProj, proj.getSource()));
            Global.getCombatEngine().removeEntity(proj);
            Global.getCombatEngine().removePlugin(this);
        }
       // MagicRender.singleframe(Global.getSettings().getSprite(proj.getWeaponSpec().getHardpointSpriteName()), proj.getLocation(), new Vector2f(proj.getSpriteAPI().getWidth(), proj.getSpriteAPI().getHeight()), angle + 2f, Color.white, false);
        interval.advance(amount);
        if (interval.intervalElapsed()) 
        {
            org.magiclib.util.MagicRender.battlespace(
                    Global.getSettings().getSprite(proj.getWeaponSpec().getHardpointSpriteName()),
                    proj.getLocation(),
                    new Vector2f(),
                    new Vector2f(proj.getSpriteAPI().getWidth(), proj.getSpriteAPI().getHeight()),
                    new Vector2f(0f, 0f),
                    proj.getFacing()-90f,
                    0f,
                    new Color(155, 155, 155, 200),
                    true,
                    0f,
                    0f,
                    0f,
                    0f,
                    0f,
                    0.1f,
                    0.15f,
                    0.25f,
                    CombatEngineLayers.BELOW_SHIPS_LAYER
            );
            // spawnAcrossWidth(proj);
            if(Math.random() < 0.15f)
                Global.getCombatEngine().spawnEmpArcVisual(proj.getWeapon().getFirePoint(0), proj.getSource(), proj.getLocation(), proj, 10f, new Color(255,154,60,150), Color.black);        }
        angle += 1f;
    }
}    
