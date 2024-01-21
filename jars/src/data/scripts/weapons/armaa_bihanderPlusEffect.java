package data.scripts.weapons;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.AnimationAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.magiclib.util.MagicAnim;
import java.awt.Color;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.util.Misc;

/**
 *
 * @author Tartiflette
 */
public class armaa_bihanderPlusEffect implements EveryFrameWeaponEffectPlugin {

    private boolean runOnce = false, lockNloaded = false;
    private ShipSystemAPI system;
    private ShipAPI ship;
    private SpriteAPI sprite;
    private AnimationAPI anim, aGlow, aGlow2;
    private WeaponAPI head, armL, armR, pauldronL, pauldronR, torso, wGlow,cannon;
        public float TURRET_OFFSET = 30f;

    private float delay = 0.1f;
    private float timer = 0;
    private float SPINUP = 0.02f;
    private float SPINDOWN = 10f;
    private final IntervalUtil interval = new IntervalUtil(0.015f, 0.015f);
    private float level = 0f;
    private int limbInit = 0;

    private float overlap = 0, heat = 0;
    private final float TORSO_OFFSET = -45, LEFT_ARM_OFFSET = -75, RIGHT_ARM_OFFSET = -25, MAX_OVERLAP = 10;

    public void init()
    {
        runOnce = true;
	ship.setCollisionClass(CollisionClass.FIGHTER);
        for (WeaponAPI w : ship.getAllWeapons()) {
            switch (w.getSlot().getId()) {
                case "B_TORSO":
                    if(torso==null) {
                        torso = w;
                        limbInit++;
                    }
                    break;
                case "C_ARML":
                    if(armL==null) {
                        armL = w;
                        limbInit++;
                    }
                    break;
                case "C_ARMR":
                    if(armR==null) {
                        armR = w;
                        limbInit++;
                    }
                    break;
                case "D_PAULDRONL":
                    if(pauldronL == null) {
                        pauldronL = w;
                        limbInit++;
                    }
                    break;
                case "D_PAULDRONR":
                    if(pauldronR == null) {
                        pauldronR = w;
                        limbInit++;
                    }
                    break;
                case "F_GLOW":
                    if(wGlow == null) {
                        wGlow = w;
                        aGlow = w.getAnimation();
                        limbInit++;
                    }
                    break;
                case "E_HEAD":
                    if(head == null) {
                        head = w;
                        limbInit++;
                    }
                    break;
                case "WS0001":
                    if(cannon == null) {
                        cannon = w;
                        limbInit++;
                    }
                    break;
            }
        }

    }
    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {

        ship = weapon.getShip();
        sprite = ship.getSpriteAPI();
        system = ship.getSystem();
        anim = weapon.getAnimation();

        init();


        if(!runOnce || limbInit != 8)
        {
           return;
        }

            if (ship.getEngineController().isAccelerating()) {
                if (overlap > (MAX_OVERLAP - 0.1f)) {
                    overlap = MAX_OVERLAP;
                } else {
                    overlap = Math.min(MAX_OVERLAP, overlap + ((MAX_OVERLAP - overlap) * amount * 5));
                }
            } else if (ship.getEngineController().isDecelerating() || ship.getEngineController().isAcceleratingBackwards()) {
                if (overlap < -(MAX_OVERLAP - 0.1f)) {
                    overlap = -MAX_OVERLAP;
                } else {
                    overlap = Math.max(-MAX_OVERLAP, overlap + ((-MAX_OVERLAP + overlap) * amount * 5));
                }
            } else {
                if (Math.abs(overlap) < 0.1f) {
                    overlap = 0;
                } else {
                    overlap -= (overlap / 2) * amount * 3;
                }
            }

            float sineA = 0, sinceB = 0;
            float global = ship.getFacing();
            float aim = MathUtils.getShortestRotation(global, weapon.getCurrAngle());

                torso.setCurrAngle(global + sineA * TORSO_OFFSET + aim * 0.3f);

                armR.setCurrAngle(weapon.getCurrAngle() + RIGHT_ARM_OFFSET);

                pauldronR.setCurrAngle(global + sineA * TORSO_OFFSET * 0.5f + aim * 0.75f + RIGHT_ARM_OFFSET * 0.5f);

	    if(ship.getHitpoints()/ship.getMaxHitpoints() <= .50)
            ship.syncWeaponDecalsWithArmorDamage();

            Vector2f origin = new Vector2f(weapon.getLocation());
            Vector2f offset = new Vector2f(TURRET_OFFSET, -15f);
            VectorUtils.rotate(offset, weapon.getCurrAngle(), offset);
            Vector2f.add(offset, origin, origin);
            float shipFacing = weapon.getCurrAngle();
            Vector2f shipVelocity = ship.getVelocity();
            if (armL != null) {
                armL.setCurrAngle(
                        global
                                +
                                ((aim + LEFT_ARM_OFFSET) * sinceB)
                                +
                                ((overlap + aim * 0.25f) * (1 - sinceB))
                );
            }

            if (pauldronL != null)
                pauldronL.setCurrAngle(torso.getCurrAngle() + MathUtils.getShortestRotation(torso.getCurrAngle(), armL.getCurrAngle()) * 0.6f);
            if (wGlow != null)
                wGlow.setCurrAngle(weapon.getCurrAngle());

    }
}
