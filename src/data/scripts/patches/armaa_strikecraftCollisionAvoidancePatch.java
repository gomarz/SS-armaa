package data.scripts.patches;

import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.ShipAPI;
import patchlib.api.context.MethodCallContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.patch.Patch;
import patchlib.api.patch.RedirectMethodCall;

/**
 * Vanilla ship AI (BasicShipAI, and OmniShieldControlAI for the player's autopilot shields) steers around
 * every nearby ship that isn't fighter-sized. FIGHTER collision never physically touches a ship, so allies
 * and strikecraft avoiding each other is wasted maneuvering. Each avoidShipCollisions() has exactly one
 * isFighter() call, the obstacle filter; answer true for that pair so it is skipped.
 *
 * Seen on 0.98a-RC8 (Windows). Inferred targets, not API-guaranteed, so re-check them after a game update:
 * both classes implement ShipAIPlugin, declare a private no-arg avoidShipCollisions(), and hold their ship in
 * a field named "ship". If the field is renamed, the patch passes the vanilla result through untouched.
 */
@Patch(target = @ClassMatch(subtypeName = "com.fs.starfarer.api.combat.ShipAIPlugin",
        packageName = "com.fs.starfarer.combat.ai",
        methodMatches = @MethodMatch(methodName = "avoidShipCollisions", parameterCount = 0)))
public class armaa_strikecraftCollisionAvoidancePatch {

    private static final String STRIKECRAFT = "strikeCraft";
    private static final String SHIP_FIELD = "ship";

    @RedirectMethodCall(target = @MethodMatch(methodName = "avoidShipCollisions", parameterCount = 0),
            call = @MethodMatch(methodName = "isFighter", parameterCount = 0))
    public static void obstacleIsFighter(MethodCallContext ctx) {
        boolean fighter = (Boolean) ctx.call();
        if (!fighter && ctx.hasField(SHIP_FIELD)) {
            Object other = ctx.getCallReceiver();
            Object self = ctx.getField(SHIP_FIELD).get();
            if (other instanceof ShipAPI && self instanceof ShipAPI) {
                ShipAPI obstacle = (ShipAPI) other;
                ShipAPI ship = (ShipAPI) self;
                if (obstacle.getOwner() == ship.getOwner() && (isFlyingStrikecraft(obstacle) || isFlyingStrikecraft(ship))) {
                    fighter = true;
                }
            }
        }
        ctx.setResult(fighter);
    }

    // armaa_strikeCraft drops back to SHIP collision when flamed out or wrecked; those still collide
    private static boolean isFlyingStrikecraft(ShipAPI ship) {
        return ship.getCollisionClass() == CollisionClass.FIGHTER
                && ship.getVariant() != null && ship.getVariant().hasHullMod(STRIKECRAFT);
    }
}
