package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.listeners.AdvanceableListener;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import java.awt.Color;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicRender;

/**
 * Formation drive. While well out of position and moving to rejoin the fleet,
 * grants the zero-flux engine boost below a flux threshold plus a flat
 * distance-scaled top speed bonus.
 *
 * Wing range is cut while the zero-flux boost is actually paying out, which is
 * the anti-kite measure for the carrier case: the flux gate stops the ship
 * fighting with its guns at range, the wing cut stops it fighting with its
 * strikecraft.
 *
 * Split out of armaa_bellatorAegis: shares no state with the Aegis field.
 */
public class armaa_bellatorFormationDrive extends BaseHullMod {

    public static final String MOD_ID = "armaa_bellatorFormationDrive";

    // --- Tunables ----------------------------------------------------------------
    /**
     * Distance from the battle line beyond which the drive engages.
     */
    private static final float ENGAGE_RANGE = 1500f;
    /**
     * Dead band below ENGAGE_RANGE before the drive disengages. Prevents
     * strobing.
     */
    private static final float DISENGAGE_HYSTERESIS = 300f;
    /**
     * Distance past ENGAGE_RANGE over which the flat speed bonus ramps to max.
     */
    private static final float RAMP_WIDTH = 1500f;
    /**
     * Peak flat top-speed bonus, on top of the zero-flux boost.
     */
    private static final float MAX_SPEED_BONUS = 25f;
    /**
     * Flux level up to which the zero-flux boost is maintained. This is the
     * anti-kite gate: firing pushes flux past it and the boost drops out, so
     * the speed is available for repositioning but not for fighting at range.
     */
    private static final float ZERO_FLUX_MAX_LEVEL = 0.15f;
    /**
     * Wing range multiplier at the engage threshold.
     */
    private static final float WING_RANGE_MULT_NEAR = 0.75f;
    /**
     * Wing range multiplier at full ramp. Stacks with the Aegis field's own
     * cut.
     */
    private static final float WING_RANGE_MULT_FAR = 0.5f;
    /**
     * Seconds for the speed bonus to slew to its target. Smooths anchor jumps.
     */
    private static final float BONUS_SLEW_TIME = 1f;
    /**
     * Grace period before "turning away" cuts the drive. Survives
     * strafing/turning.
     */
    private static final float CLOSING_GRACE = 0.3f;
    /**
     * How often the anchor centroid is recomputed.
     */
    private static final float ANCHOR_REFRESH = 0.5f;
    /**
     * Below this speed, "closing" is judged by facing instead of velocity.
     */
    private static final float STATIONARY_SPEED = 20f;

    private static final String ZERO_FLUX_ID = "armaa_formationDrive_avionics";
    private static final String SPEED_ID = "armaa_formationDrive_speed";
    private static final String WING_RANGE_ID = "armaa_formationDrive_wingRange";

    // --- Anchor arrow ------------------------------------------------------------
    private static final String ARROW_SPRITE_PATH = "graphics/warroom/ship_arrow.png";
    /**
     * Rendered size of the arrow, in su.
     */
    private static final float ARROW_SIZE = 48f;
    /**
     * How far the arrow drifts toward the anchor over its life, in su/sec.
     */
    private static final float ARROW_DRIFT_SPEED = 260f;
    /**
     * Distance from the ship's center where the arrow spawns, in su.
     */
    private static final float ARROW_SPAWN_OFFSET = 60f;
    private static final float ARROW_FADE_IN = 0.1f;
    private static final float ARROW_FULL = 0.15f;
    private static final float ARROW_FADE_OUT = 0.45f;
    /**
     * Minimum seconds between arrow pulses, so state chatter can't spam them.
     */
    private static final float ARROW_COOLDOWN = 1.5f;
    private static final Color ARROW_COLOR_ENGAGED = new Color(190, 220, 255);
    private static final Color ARROW_COLOR_WARNING = new Color(255, 170, 80);

    /**
     * Flip on to get distance/anchor telemetry in the status panel plus a
     * marker particle.
     */
    private static final boolean DEBUG = false;

    private static final String STATUS_ICON = "graphics/icons/hullsys/fortress_shield.png";

    private static final Color ENGINE_COLOR = new Color(190, 220, 255);

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        if (Global.getCombatEngine() == null) {
            return;
        }
        ship.addListener(new armaa_AvionicsTracker(ship));
    }

    @Override
    public String getDescriptionParam(int index, HullSize hullSize) {
        if (index == 0) {
            return "" + (int) ENGAGE_RANGE;
        }
        if (index == 1) {
            return "" + (int) MAX_SPEED_BONUS;
        }
        if (index == 2) {
            return (int) (ZERO_FLUX_MAX_LEVEL * 100f) + "%";
        }
        if (index == 3) {
            return "" + (int) (ENGAGE_RANGE - DISENGAGE_HYSTERESIS);
        }
        if (index == 4) {
            return (int) ((1f - WING_RANGE_MULT_NEAR) * 100f) + "%";
        }
        if (index == 5) {
            return (int) ((1f - WING_RANGE_MULT_FAR) * 100f) + "%";
        }
        return null;
    }

    public static class armaa_AvionicsTracker implements AdvanceableListener {

        private final ShipAPI ship;

        private final IntervalUtil effectInterval = new IntervalUtil(0.5f, 0.5f);
        private final IntervalUtil anchorInterval = new IntervalUtil(ANCHOR_REFRESH, ANCHOR_REFRESH);

        private float engineEffectLevel = 0f;
        private boolean avionicsBoost = false;

        private Vector2f anchor = null;
        private float anchorDist = 0f;
        private float closingGrace = 0f;
        private float currentBonus = 0f;
        private float lastWingMult = 1f;
        private String statusReason = "NO FORMATION";

        // Arrow pulse state
        private String lastStatusReason = "NO FORMATION";
        private float arrowCooldown = 0f;

        public armaa_AvionicsTracker(ShipAPI ship) {
            this.ship = ship;
        }

        @Override
        public void advance(float amount) {
            if (amount <= 0f) {
                return; // paused
            }

            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine == null) {
                return;
            }

            MutableShipStatsAPI stats = ship.getMutableStats();

            if (ship.isHulk() || !ship.isAlive()) {
                clearModifiers(stats);
                return;
            }

            arrowCooldown = Math.max(0f, arrowCooldown - amount);

            // --- Anchor: centroid of the friendly battle line ---------------------
            anchorInterval.advance(amount);
            if (anchorInterval.intervalElapsed() || anchor == null) {
                anchor = findAnchor(engine, ship);
            }

            boolean shouldBoost = false;
            if (anchor == null) {
                anchorDist = 0f;
                closingGrace = 0f;
                statusReason = "NO FORMATION";
            } else {
                anchorDist = MathUtils.getDistance(ship.getLocation(), anchor);

                // Hysteresis: engage past ENGAGE_RANGE, disengage only once well inside.
                float threshold = avionicsBoost ? (ENGAGE_RANGE - DISENGAGE_HYSTERESIS) : ENGAGE_RANGE;
                boolean outOfPosition = anchorDist > threshold;

                // Anti-yo-yo: drops the drive when moving flat away from the anchor.
                // Does NOT imply the ship is out of combat -- enemies are frequently
                // between this ship and the line, so "closing" and "charging" overlap.
                // The flux gate and wing cut are what keep this from being a kite tool.
                Vector2f toAnchor = Vector2f.sub(anchor, ship.getLocation(), new Vector2f());
                Vector2f dir = ship.getVelocity();
                if (dir.lengthSquared() < STATIONARY_SPEED * STATIONARY_SPEED) {
                    // Nearly stationary: judge by where the ship is pointed, otherwise
                    // it can never accelerate into the boost from a standstill.
                    dir = Misc.getUnitVectorAtDegreeAngle(ship.getFacing());
                }
                boolean closing = Vector2f.dot(toAnchor, dir) > 0f;

                if (closing) {
                    closingGrace = CLOSING_GRACE;
                } else {
                    closingGrace = Math.max(0f, closingGrace - amount);
                }

                shouldBoost = outOfPosition && closingGrace > 0f;

                if (shouldBoost) {
                    statusReason = null;
                } else if (!outOfPosition) {
                    statusReason = "IN POSITION";
                } else {
                    statusReason = "TURNING AWAY";
                }
            }

            // --- Anchor arrow pulse -------------------------------------------------
            // Fires on state transitions only, not continuously: the useful moments are
            // the drive engaging, and the drive cutting because the ship turned off.
            String reasonNow = statusReason == null ? "ENGAGED" : statusReason;
            if (!reasonNow.equals(lastStatusReason)) {
                if ("ENGAGED".equals(reasonNow)) {
                    pulseAnchorArrow(engine, Global.getSettings().getColor("textFriendColor"));
                } else if ("TURNING AWAY".equals(reasonNow)) {
                    pulseAnchorArrow(engine, Global.getSettings().getColor("textEnemyColor"));
                }
                lastStatusReason = reasonNow;
            }

            // --- Zero-flux boost toggle -------------------------------------------
            // Raises the zero-flux threshold rather than locking it on, so the boost
            // falls away on its own once the ship opens fire.
            if (shouldBoost != avionicsBoost) {
                avionicsBoost = shouldBoost;
                if (avionicsBoost) {
                    stats.getZeroFluxMinimumFluxLevel().modifyFlat(ZERO_FLUX_ID, ZERO_FLUX_MAX_LEVEL);
                } else {
                    stats.getZeroFluxMinimumFluxLevel().unmodify(ZERO_FLUX_ID);
                }
            }

            // --- Distance-scaled flat speed bonus, slewed ---------------------------
            float targetBonus = 0f;
            if (avionicsBoost) {
                float over = anchorDist - ENGAGE_RANGE;
                float t = MathUtils.clamp(over / RAMP_WIDTH, 0f, 1f);
                targetBonus = MAX_SPEED_BONUS * t;
            }

            float slew = (MAX_SPEED_BONUS / BONUS_SLEW_TIME) * amount;
            if (currentBonus < targetBonus) {
                currentBonus = Math.min(targetBonus, currentBonus + slew);
            } else if (currentBonus > targetBonus) {
                currentBonus = Math.max(targetBonus, currentBonus - slew);
            }

            if (currentBonus > 0.01f) {
                stats.getMaxSpeed().modifyFlat(SPEED_ID, currentBonus);
            } else {
                stats.getMaxSpeed().unmodify(SPEED_ID);
            }

            // --- Wing range cut ------------------------------------------------------
            // Only while the zero-flux boost is actually paying out, since that is the
            // bulk of the speed. Scales with the ramp: worse the further out and faster
            // the ship is. Set per frame so it inherits the bonus slew.
            boolean zeroFluxActive = avionicsBoost && ship.getFluxLevel() <= ZERO_FLUX_MAX_LEVEL;
            if (zeroFluxActive) {
                float frac = MAX_SPEED_BONUS > 0f ? (currentBonus / MAX_SPEED_BONUS) : 1f;
                lastWingMult = WING_RANGE_MULT_NEAR + (WING_RANGE_MULT_FAR - WING_RANGE_MULT_NEAR) * frac;
                stats.getFighterWingRange().modifyMult(WING_RANGE_ID, lastWingMult);
            } else {
                stats.getFighterWingRange().unmodify(WING_RANGE_ID);
                lastWingMult = 1f;
            }

            renderStatus(engine);

            // --- Engine VFX ---------------------------------------------------------
            if (avionicsBoost) {
                engineEffectLevel += amount;

                effectInterval.advance(amount);
                if (effectInterval.intervalElapsed()) {
                    // Alpha tracks how hard the drive is pushing.
                    float intensity = MAX_SPEED_BONUS > 0f ? (currentBonus / MAX_SPEED_BONUS) : 1f;
                    Color c = new Color(
                            ENGINE_COLOR.getRed(),
                            ENGINE_COLOR.getGreen(),
                            ENGINE_COLOR.getBlue(),
                            (int) (60f + 120f * intensity));

                    Vector2f v = ship.getVelocity();
                    ship.addAfterimage(
                            c,
                            0f, 0f,
                            -v.x, // cancel ship motion so the image stays put in world space
                            -v.y,
                            0f, // maxJitter
                            0f, // fadeIn
                            0.15f, // hold at full
                            1f, // fadeOut
                            false, // additive
                            true, // combineWithSpriteColor
                            false);        // aboveShip
                }
            } else {
                engineEffectLevel -= amount;
            }

            engineEffectLevel = MathUtils.clamp(engineEffectLevel, 0f, 1f);
            ship.getEngineController().fadeToOtherColor(this, ENGINE_COLOR, new Color(0, 0, 0, 0), engineEffectLevel, 0.33f);
        }

        private void clearModifiers(MutableShipStatsAPI stats) {
            if (avionicsBoost) {
                stats.getZeroFluxMinimumFluxLevel().unmodify(ZERO_FLUX_ID);
                avionicsBoost = false;
            }
            stats.getMaxSpeed().unmodify(SPEED_ID);
            stats.getFighterWingRange().unmodify(WING_RANGE_ID);
            currentBonus = 0f;
            lastWingMult = 1f;
        }

        // --- Anchor arrow ----------------------------------------------------------
        /**
         * One-shot arrow from the ship's center pointing at the fleet centroid,
         * which drifts that way as it fades. Player ship only -- this is a HUD
         * affordance, not a world effect.
         */
        private void pulseAnchorArrow(CombatEngineAPI engine, Color color) {
            if (anchor == null || arrowCooldown > 0f) {
                return;
            }
            if (ship != engine.getPlayerShip()) {
                return;
            }

            SpriteAPI sprite = getArrowSprite();
            if (sprite == null) {
                return;
            }

            arrowCooldown = ARROW_COOLDOWN;

            float angleToAnchor = Misc.getAngleInDegrees(ship.getLocation(), anchor);
            Vector2f unit = Misc.getUnitVectorAtDegreeAngle(angleToAnchor);

            // Offset is relative to the anchor entity. parentFacing is false, so it is
            // world-axis-aligned and does not spin with the hull.
            Vector2f offset = new Vector2f(unit);
            offset.scale(ARROW_SPAWN_OFFSET);

            Vector2f vel = new Vector2f(unit);
            vel.scale(ARROW_DRIFT_SPEED);

            MagicRender.objectspace(
                    sprite,
                    ship,
                    offset,
                    vel,
                    new Vector2f(ARROW_SIZE / 1.5f, ARROW_SIZE / 1.5f),
                    new Vector2f(0f, 0f), // growth
                    angleToAnchor + 180f, // sprite art points "up"; 0deg is east
                    0f, // spin
                    false, // parentFacing
                    color,
                    false, // additive
                    ARROW_FADE_IN,
                    ARROW_FULL,
                    ARROW_FADE_OUT,
                    true);
        }

        private static SpriteAPI arrowSprite = null;
        private static boolean arrowSpriteChecked = false;

        private static SpriteAPI getArrowSprite() {
            if (!arrowSpriteChecked) {
                arrowSpriteChecked = true;
                try {
                    Global.getSettings().loadTexture("graphics/warroom/ship_arrow.png");
                    arrowSprite = Global.getSettings().getSprite("graphics/warroom/ship_arrow.png");
                } catch (Exception e) {
                    Global.getLogger(armaa_bellatorFormationDrive.class)
                            .warn("Could not load formation drive arrow: " + ARROW_SPRITE_PATH, e);
                    arrowSprite = null;
                }
            }
            return arrowSprite;
        }

        // --- Status panel ----------------------------------------------------------
        private void renderStatus(CombatEngineAPI engine) {
            if (ship != engine.getPlayerShip()) {
                return;
            }

            if (avionicsBoost) {
                engine.maintainStatusForPlayerShip(
                        "armaa_formationDrive",
                        STATUS_ICON,
                        "FORMATION DRIVE",
                        "+" + (int) currentBonus + " top speed",
                        false);
            } else {
                engine.maintainStatusForPlayerShip(
                        "armaa_formationDrive",
                        STATUS_ICON,
                        "FORMATION DRIVE",
                        statusReason == null ? "STANDBY" : statusReason,
                        true);
            }

            // Gated on the mult, not on avionicsBoost: this drops away on its own
            // when flux crosses the threshold even though the drive is still engaged.
            if (lastWingMult < 1f) {
                engine.maintainStatusForPlayerShip(
                        "armaa_formationDrive_wings",
                        STATUS_ICON,
                        "FORMATION DRIVE",
                        "-" + (int) ((1f - lastWingMult) * 100f) + "% wing range",
                        true);
            }

            if (DEBUG) {
                String data = anchor == null
                        ? "no anchor"
                        : (int) anchorDist + "u to line / thr " + (int) (avionicsBoost ? ENGAGE_RANGE - DISENGAGE_HYSTERESIS : ENGAGE_RANGE);
                engine.maintainStatusForPlayerShip(
                        "armaa_formationDrive_dbg",
                        STATUS_ICON,
                        "ANCHOR",
                        data,
                        false);

                if (anchor != null) {
                    engine.addSmoothParticle(
                            new Vector2f(anchor),
                            new Vector2f(),
                            60f,
                            1f,
                            0.15f,
                            avionicsBoost ? Color.CYAN : Color.ORANGE);
                }
            }
        }

        // --- Anchor resolution -----------------------------------------------------
        /**
         * Deployment-weighted centroid of the friendly battle line. Falls back
         * to lighter hulls if no cruisers/capitals are on the field, so this
         * still works in small engagements.
         */
        private static Vector2f findAnchor(CombatEngineAPI engine, ShipAPI self) {
            Vector2f heavy = weightedCentroid(engine, self, true);
            if (heavy != null) {
                return heavy;
            }
            return weightedCentroid(engine, self, false);
        }

        private static Vector2f weightedCentroid(CombatEngineAPI engine, ShipAPI self, boolean heavyOnly) {
            float sumX = 0f, sumY = 0f, sumW = 0f;

            for (ShipAPI other : engine.getShips()) {
                if (other == self) {
                    continue;
                }
                if (other.getOwner() != self.getOwner()) {
                    continue;
                }
                if (other.isHulk() || !other.isAlive()) {
                    continue;
                }
                if (other.isFighter() || other.isDrone()) {
                    continue;
                }
                if (other.isShuttlePod()) {
                    continue;
                }

                float w = anchorWeight(other, heavyOnly);
                if (w <= 0f) {
                    continue;
                }

                sumX += other.getLocation().x * w;
                sumY += other.getLocation().y * w;
                sumW += w;
            }

            if (sumW <= 0f) {
                return null;
            }
            return new Vector2f(sumX / sumW, sumY / sumW);
        }

        private static float anchorWeight(ShipAPI s, boolean heavyOnly) {
            switch (s.getHullSize()) {
                case CAPITAL_SHIP:
                    return 3f;
                case CRUISER:
                    return 2f;
                case DESTROYER:
                    return heavyOnly ? 0f : 1f;
                case FRIGATE:
                    return heavyOnly ? 0f : 0.5f;
                default:
                    return 0f;
            }
        }
    }
}
