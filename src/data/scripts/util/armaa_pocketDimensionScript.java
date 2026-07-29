/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package data.scripts.util;

    // use this to create the pocket dimension 
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import data.scripts.weapons.armaa_mknuckleGuidance;
import java.awt.Color;
import java.util.List;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

    public class armaa_pocketDimensionScript extends BaseEveryFrameCombatPlugin {

        private final Vector2f location;
        private final IntervalUtil animInterval;
        private final IntervalUtil pulseInterval = new IntervalUtil(2f,2f);
        private final String weaponId;
        private final ShipAPI source;
        private final WeaponAPI weapon;
        private static final float MUZZLE_FLASH_DURATION = 1f;
        private static final float MUZZLE_FLASH_SIZE = 50.0f;

        public armaa_pocketDimensionScript(String weaponId, Vector2f location, ShipAPI source, WeaponAPI weapon) {
            this.location = location;
            this.weaponId = weaponId;
            this.animInterval = new IntervalUtil(12f, 12f);
            this.source = source;
            this.weapon = weapon;
            for(DamagingProjectileAPI proj : CombatUtils.getProjectilesWithinRange(location, 10000))
            {
                if(proj.getSource() == source && proj.getWeapon() == weapon)
                    Global.getCombatEngine().removeEntity(proj);
            }
        }

        @Override
        public void advance(float amount, List<InputEventAPI> events) {
            if (Global.getCombatEngine().isPaused()) {
                return;
            }
            float level = animInterval.getElapsed() / animInterval.getIntervalDuration();
            float spin = 0f;
            animInterval.advance(amount);
            pulseInterval.advance(amount);
            org.magiclib.util.MagicRender.singleframe(
                    Global.getSettings().getSprite("misc", "armaa_atmo_cloud"),
                    new Vector2f(location),
                    new Vector2f(175f * level * 2, 175f * level * 2),
                    -spin*2,
                    new Color(200f / 255f, 0f, 125f / 255f, (50f / 255f) * level),
                    true);
            org.magiclib.util.MagicRender.singleframe(
                    Global.getSettings().getSprite("misc", "armaa_atmo_cloud"),
                    new Vector2f(location),
                    new Vector2f(150f * level * 2, 150f * level * 2),
                    spin*2,
                    new Color(25f / 255f, 0f, 59f / 255f, (150f / 255f) * level),
                    true);
            org.magiclib.util.MagicRender.singleframe(
                    Global.getSettings().getSprite("misc", "armaa_atmo_cloud"),
                    new Vector2f(location),
                    new Vector2f(100f * level * 3, 100f * level * 3),
                    -spin*2,
                    new Color(0, 0, 0, 255f / 255f * level),
                    false);
            spin += 5 * level;
            Color MUZZLE_FLASH_COLOR = new Color(255, 55, 200, 155);
            Color MUZZLE_FLASH_COLOR_ALT = new Color(255, 255, 255, 100);
            if (Math.random() > 0.75) {
                Global.getCombatEngine().spawnExplosion(location, new Vector2f(), MUZZLE_FLASH_COLOR_ALT, MUZZLE_FLASH_SIZE * 0.20f * level * 2, MUZZLE_FLASH_DURATION);
            } else {
                Global.getCombatEngine().spawnExplosion(location, new Vector2f(), MUZZLE_FLASH_COLOR, MUZZLE_FLASH_SIZE * 1f * level * 2, MUZZLE_FLASH_DURATION);
            }
            org.magiclib.util.MagicRender.singleframe(Global.getSettings().getSprite("gates", "starfield"), location, new Vector2f(100f * level, 100f * level), -spin*25*level, new Color(1f, 1f, 1f, 1f * level), false,CombatEngineLayers.ABOVE_PARTICLES);
            org.magiclib.util.MagicRender.singleframe(Global.getSettings().getSprite("gates", "glow_whirl1"), location, new Vector2f(100f * level, 100f * level), -spin*50*level, new Color(1f, 1f, 1f, 1f * level), true,CombatEngineLayers.ABOVE_PARTICLES);
            org.magiclib.util.MagicRender.singleframe(Global.getSettings().getSprite("gates", "glow_whirl2"), location, new Vector2f(100f * level, 100f * level), spin*50*level,new Color(1f, 1f, 1f, 1f * level), true,CombatEngineLayers.ABOVE_PARTICLES);
            if(pulseInterval.intervalElapsed())
            org.magiclib.util.MagicRender.battlespace(
                    Global.getSettings().getSprite("misc", "armaa_sfxpulse"),
                    location,
                    new Vector2f(),
                    new Vector2f(0 * 2, 0*2),
                    new Vector2f(400f, 400f),
                    5f,
                    5f,
                    new Color(25, 0, 50, 100),
                    false,
                    .1f,
                    .1f,
                    1f
            );            
            if (animInterval.intervalElapsed()) {
                Global.getCombatEngine().spawnExplosion(location, new Vector2f(), MUZZLE_FLASH_COLOR_ALT, MUZZLE_FLASH_SIZE * 0.20f * level * 4, MUZZLE_FLASH_DURATION);
                DamagingProjectileAPI newProj = (DamagingProjectileAPI) Global.getCombatEngine().spawnProjectile(
                        source,
                        weapon,
                        weaponId,
                        location,
                        0f,
                        new Vector2f()
                );
                for (int x = 0; x < 15; x++) 
                {
                    float damage = 1000f;
                    Global.getCombatEngine().addNebulaParticle(location,
                            new Vector2f(MathUtils.getRandomNumberInRange(-100, 100), 0),
                            30f * (0.75f + (float) Math.random() * 0.5f),
                            5f + 3f * Misc.getHitGlowSize(100f, newProj.getDamage().getBaseDamage(), DamageType.KINETIC, damage * 2, damage / 2, damage / 2, 0f) / 100f,
                            0f,
                            0f,
                            1f,
                            new Color(200, 100, 200, 150));
                }                
                Global.getCombatEngine().addPlugin(new armaa_mknuckleGuidance(newProj, source));
                Global.getCombatEngine().removePlugin(this);
            }
        }
    }