package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicRender;

/**
 *
 * @author bra20
 */
public class armaa_mknuckleProjectileEffect extends BaseEveryFrameCombatPlugin {

    private MissileAPI proj;
    private float angle;
    private IntervalUtil interval = new IntervalUtil(0.06f, 0.06f);
    private int index = 0;
    public static final Map<Integer, String> SPRITE_MAP = new HashMap<>();

    static {
        SPRITE_MAP.put(0,"graphics/armaa/ships/valkazard/geant/armaa_valkazard_geant_leftArm.png");
        SPRITE_MAP.put(1,"graphics/armaa/ships/valkazard/geant/armaa_valkazard_geant_arm_shot.png");
        SPRITE_MAP.put(2,"graphics/armaa/ships/valkazard/geant/armaa_valkazard_geant_rightArm.png");

    }

    public armaa_mknuckleProjectileEffect(DamagingProjectileAPI dpapi) {
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
        if(index > 2)
            index = 0;
        //proj.getSpriteAPI().setColor(new Color(0f, 0f, 0f, 0f));
        proj.setEmpResistance(99);
        if (proj.isFading() || proj.isFizzling() || proj.getHitpoints() <= 0) {
            Vector2f newVel = Misc.getUnitVectorAtDegreeAngle(proj.getFacing() + angle);
            DamagingProjectileAPI newProj = (DamagingProjectileAPI) Global.getCombatEngine().spawnProjectile(
                    proj.getSource(),
                    proj.getWeapon(),
                    "armaa_valkazard_geant_mknuckle_return",
                    proj.getLocation(),
                    proj.getFacing(),
                    newVel
            );
            if(newProj.getCustomData().get("armaa_mknuckleGuidanceScript") == null)
            {
                newProj.getCustomData().put("armaa_mknuckleGuidanceScript",true);
                Global.getCombatEngine().addPlugin(new armaa_mknuckleGuidance(newProj, proj.getSource()));
            }
            proj.getCustomData().remove("armaa_mknuckleScript");            
            Global.getCombatEngine().removeEntity(proj);
            Global.getCombatEngine().removePlugin(this);
            return;
        }
        SpriteAPI sprite = Global.getSettings().getSprite("misc","fist_"+index);
        MagicRender.singleframe(sprite, proj.getLocation(), new Vector2f(sprite.getWidth(), sprite.getHeight()), proj.getFacing()-90f, Color.white, false);
        interval.advance(amount);
        proj.getSpriteAPI().setColor(new Color(0,0,0,0));
        if (interval.intervalElapsed()) {
            MagicRender.battlespace(
                    sprite,
                    proj.getLocation(),
                    new Vector2f(),
                    new Vector2f(sprite.getWidth(), sprite.getHeight()),
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
            if (Math.random() < 0.15f && MathUtils.getDistance(proj, proj.getWeapon().getFirePoint(0)) < 1000f) {
                Global.getCombatEngine().spawnEmpArcVisual(proj.getWeapon().getFirePoint(0), proj.getSource(), proj.getLocation(), proj, 10f, new Color(255, 154, 60, 150), Color.black);
            }
            index++;
        }
    }
}
