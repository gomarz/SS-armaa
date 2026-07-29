package data.hullmods;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.util.vector.Vector2f;
import java.awt.*;
import lunalib.lunaSettings.LunaSettings;
import org.magiclib.util.MagicRender;

/**
 *
 * @author Mayu || A Repair Kit system I made for UAF's unique Strikecraft I
 * then copied and stole mayu's homework kekekekekek - shoi
 */
public class armaa_repair_kit_subsystem extends BaseHullMod {

    // Emergency Repair Kit
    public static String KEYPRESS_KEY_ONE;
    public static String KEYPRESS_KEY_TWO; // R
    float jitterTimerDuration;
    float jitterTimer = 2f;
    boolean hasUsedRepairKit = false;
    private IntervalUtil cooldownTimer = new IntervalUtil(3f, 3f);

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        // Reset these variable shits in non-combat instance
        // What? Don't look at me like that! If it works then it WORKS!
        if (Global.getCurrentState() != GameState.COMBAT) {
            // Repair Kit
            hasUsedRepairKit = false;
            jitterTimer = 2f;
        }
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        final CombatEngineAPI engine = Global.getCombatEngine();
        boolean player = false;
        player = ship == Global.getCombatEngine().getPlayerShip();
        final float variance = MathUtils.getRandomNumberInRange(-0.3f, 0.6f);

        if (!ship.isAlive() || ship.isHulk()) {
            return;
        }

        boolean canYouFuckingUseItNow = true;
        float howManyRepairKitRemaining = 1f;
        String repairKitDescriptionActivation = "Only usable at below 70% hull level";
        Vector2f location = ship.getLocation();

        //////////////////////
        //// REPAIR KIT //////
        /////////////////////
        String statusKeyOne;
        String statusKeyTwo;
        KEYPRESS_KEY_ONE = "LMENU";
        KEYPRESS_KEY_TWO = "X";
        if (Global.getSettings().getModManager().isModEnabled("lunalib")) {
            KEYPRESS_KEY_TWO = LunaSettings.getString("armaa", "armaa_repairKitKey");
        }
        statusKeyTwo = KEYPRESS_KEY_TWO;
        if (KEYPRESS_KEY_ONE.equals("LMENU")) {
            statusKeyOne = "LEFT ALT";
        } else if (KEYPRESS_KEY_ONE.equals("LCONTROL")) {
            statusKeyOne = "LEFT CONTROL";
        } else {
            statusKeyOne = KEYPRESS_KEY_ONE;
        }

        final float percentageOfHPLeft = ship.getHitpoints() / ship.getMaxHitpoints();

        // 30% Recovery just change the 30 below to anything, this is an example and I fucking hate math
        // float redpotionDoYouUseitMotherfucker = Math.round(((float) 30 / 100 * ship.getMaxHitpoints()) + (ship.getHitpoints()));
        // 100% Recovery
        float fullHeal = Math.round(ship.getMaxHitpoints());

        if (hasUsedRepairKit == false) {
            if (percentageOfHPLeft < 0.7f) {

                repairKitDescriptionActivation = "Press " + statusKeyOne + " + " + statusKeyTwo + " to use the repair kit";
                canYouFuckingUseItNow = false;

                if (Keyboard.isKeyDown(Keyboard.getKeyIndex(KEYPRESS_KEY_ONE)) && Keyboard.isKeyDown(Keyboard.getKeyIndex(KEYPRESS_KEY_TWO)) || !player && percentageOfHPLeft < 0.25f) {
                    // Apply the repair kit
                    ship.setHitpoints(fullHeal);
                    // Play a sound
                    Global.getSoundPlayer().playSound("system_entropy", 1f + variance, 1f + variance, location, ship.getVelocity());
                    // Show the floaty text that it healed
                    // I wanted to do the green heal visuals that floats up with "+" symbol, but I'm too fucking retarded
                    Vector2f origin2 = new Vector2f(ship.getLocation());
                    Vector2f offset2 = new Vector2f(0, 0);
                    float speed = MathUtils.getRandomNumberInRange(80f, 120f);
                    Vector2f.add(offset2, origin2, origin2);
                    float size = MathUtils.getRandomNumberInRange(ship.getCollisionRadius() * 2, ship.getCollisionRadius() * 4);
                    float x = MathUtils.getRandomNumberInRange(-50, 50);
                    float y = MathUtils.getRandomNumberInRange(-50, 50);
                    Global.getCombatEngine().addNebulaParticle(origin2, ship.getVelocity(), size, 2f, 0.33f, 1f, 1f, new Color(0f, 1f, 0f, 0.9f));
                    
                    for (int i = 0; i < 10; i++) {
                    Vector2f burst = MathUtils.getPointOnCircumference(new Vector2f(), speed, MathUtils.getRandomNumberInRange(0f, 360f));
                    Vector2f inherited = new Vector2f(ship.getVelocity());
                    Vector2f velP = Vector2f.add(inherited, burst, null);            
                        engine.addHitParticle(ship.getLocation(),
                                velP,
                                8f, 1f, MathUtils.getRandomNumberInRange(0.6f, 1f), Color.green);
                    }
                    for (int i = 0; i < MathUtils.getRandomNumberInRange(5, 10); i++) {
                        MagicRender.battlespace(
                                Global.getSettings().getSprite("misc", "armaa_repair_symbol"),
                                new Vector2f(ship.getLocation().x, ship.getLocation().y),
                                new Vector2f(ship.getVelocity().x + MathUtils.getRandomNumberInRange(-100, 100), ship.getVelocity().y + MathUtils.getRandomNumberInRange(-100, 100)),
                                new Vector2f(ship.getCollisionRadius() / 4, ship.getCollisionRadius() / 4),
                                new Vector2f(40f, 40f),
                                0f,
                                0f,
                                new Color(0f, 1f, 0f, 0.8f),
                                true,
                                0f,
                                0f,
                                0f,
                                0f,
                                0f,
                                0.1f,
                                0.35f,
                                0.45f,
                                CombatEngineLayers.BELOW_SHIPS_LAYER
                        );
                    }
                    engine.addFloatingDamageText(new Vector2f(ship.getLocation().x - ship.getCollisionRadius(), ship.getLocation().y + ship.getCollisionRadius()), fullHeal, 0f, Color.green, ship, null);
                    // Set it to true
                    hasUsedRepairKit = true;
                }
            }
        }

        if (hasUsedRepairKit == true) {
            // Timer for the recovery visuals
            jitterTimerDuration = jitterTimer -= 1 * amount;
            if (jitterTimerDuration > 0f) {
                ship.setJitter(ship, new Color(49f/255f, 138f/255f, 90f/255f, (205f/255f)*(jitterTimer/jitterTimerDuration)), 1f, 3, 3.0f);
                ship.setJitterUnder(ship, new Color(13f/255f, 241f/255f, 112f/255f, (225f/255f)*((jitterTimer/jitterTimerDuration))), 3f, 5, 5.0f);
            }

            canYouFuckingUseItNow = true;
            repairKitDescriptionActivation = "The repair kit has been already used";
            // Deduct the number of use, in this case, since we only have one repair kit
            // Set it to zero!
            howManyRepairKitRemaining = 0f;
        }

        // Tell them how many repair kits they have
        if (player) {
            String ID = "mayu_repair_kit_subsystem";
            Global.getCombatEngine().maintainStatusForPlayerShip(
                    ID + "_repair_kit",
                    Global.getSettings().getSpriteName("tooltip", "uaf_lynx_emergency_repair_kit"),
                    "Emergency Repair Kit: " + Misc.getRoundedValue(howManyRepairKitRemaining) + " Remaining",
                    repairKitDescriptionActivation,
                    canYouFuckingUseItNow
            );
        }
    }
}
