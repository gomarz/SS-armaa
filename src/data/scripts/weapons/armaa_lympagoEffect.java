package data.scripts.weapons;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

public class armaa_lympagoEffect implements EveryFrameWeaponEffectPlugin {

    private boolean runOnce = false;
    private ShipAPI ship;
    private SpriteAPI sprite;
    private WeaponAPI head, armL, armR, pauldronL, pauldronR, gun;
    private static final Vector2f ZERO = new Vector2f();
    public float TURRET_OFFSET = 30f;
    private int limbInit = 0;
    private float overlap = 0;
    private final float TORSO_OFFSET = -45, LEFT_ARM_OFFSET = -75, RIGHT_ARM_OFFSET = -25, MAX_OVERLAP = 10;

    public void init()
    {
        runOnce = true;
        for (WeaponAPI w : ship.getAllWeapons()) {
            switch (w.getSlot().getId()) {
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
                case "E_HEAD":
                    if(head == null) {
                        head = w;
                        limbInit++;
                    }
                    break;
                case "A_GUN":
                    if(gun == null) {
                        gun = w;
                        limbInit++;
                    }
                    break;
            }
        }

    }
    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        ship = weapon.getShip();
		if(!Global.getCombatEngine().isEntityInPlay(ship))
			return;		
        sprite = ship.getSpriteAPI();
        init();

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
		float aim = MathUtils.getShortestRotation(global, gun.getCurrAngle());

		if (armR != null)
			armR.setCurrAngle(gun.getCurrAngle() + RIGHT_ARM_OFFSET);

		if (pauldronR != null)
			pauldronR.setCurrAngle(global + sineA * TORSO_OFFSET * 0.5f + aim * 0.75f + RIGHT_ARM_OFFSET * 0.5f);
	    
            ship.syncWeaponDecalsWithArmorDamage();

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
                pauldronL.setCurrAngle(ship.getFacing() + MathUtils.getShortestRotation(ship.getFacing(), armL.getCurrAngle()) * 0.6f);

            }
        }
