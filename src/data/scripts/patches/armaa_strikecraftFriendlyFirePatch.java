package data.scripts.patches;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.util.vector.Vector2f;
import patchlib.api.context.AfterContext;
import patchlib.api.context.BeforeContext;
import patchlib.api.context.ExceptContext;
import patchlib.api.context.HookContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.match.StaticType;
import patchlib.api.patch.After;
import patchlib.api.patch.Before;
import patchlib.api.patch.Except;
import patchlib.api.patch.Patch;

/**
 * Patches vanilla's friendly-fire danger score itself, so every caller agrees: autofire, the AI's
 * manually fired weapon group, the player's red aim-line warning, dash systems, and mods using
 * SettingsAPI.getFriendlyFireDanger. The score treats every non-fighter-sized ally as an obstacle, but
 * friendly shots, beams and missiles all pass through a ship with FIGHTER collision, so flying strikecraft
 * are left out of it. Strikecraft as shooters are handled by armaa_strikecraftProjectileClassPatch, which
 * keeps missiles that can hit allies under this check.
 *
 * The danger function is a static (ShipAPI shooter, target, Vector2f from, Vector2f to, float, float, float)
 * method in com.fs.starfarer.combat.ai. Two other static 7-arg float methods share that package and arity, and
 * the one telling parameter type is obfuscated, so the matcher hits all three and each handler checks the
 * arguments at runtime: only the danger function has a Vector2f at both index 2 and 3.
 *
 * Seen on 0.98a-RC8 (Windows); these names change between builds, the package and shapes should not:
 *   danger: com.fs.starfarer.combat.ai.OO0O#super(Ship, E.B, Vector2f, Vector2f, float, float, float),
 *           profiler label "Computing FF danger"
 *   others: OO0O#super(Ship, Vector2f, float, float, float, Ship, boolean)    (enemy threat in an arc)
 *           OO0O#super(Ship, Vector2f, float, float, float, boolean, boolean) (friendlies in an arc)
 *
 * Masking sets the shared ShipAPI drone flag on friendly strikecraft for the length of one call. Another mod's
 * hook on this function ordered between our @Before and @After would see them as drones.
 */
@Patch(target = @ClassMatch(packageName = "com.fs.starfarer.combat.ai",
        methodMatches = @MethodMatch(parameterCount = 7, returnTypeName = "float", staticType = StaticType.STATIC)))
public class armaa_strikecraftFriendlyFirePatch {

    private static final String STRIKECRAFT = "strikeCraft";
    private static final String MASKED_KEY = "armaa_maskedStrikecraft";

    @Before(target = @MethodMatch(parameterCount = 7, returnTypeName = "float", staticType = StaticType.STATIC))
    public static void beforeDanger(BeforeContext ctx) {
        if (!isDangerCall(ctx)) {
            return;
        }
        ShipAPI shooter = (ShipAPI) ctx.getArg(0);

        // The danger loop skips drones with FIGHTER collision, so flag friendly strikecraft as drones for this call only
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null) {
            return;
        }
        List<ShipAPI> masked = null;
        for (ShipAPI ship : engine.getShips()) {
            if (ship == shooter || ship.isDrone() || ship.getOwner() != shooter.getOwner()
                    || ship.getCollisionClass() != CollisionClass.FIGHTER || !isStrikecraft(ship)) {
                continue;
            }
            if (masked == null) {
                masked = new ArrayList<>();
            }
            ship.setDrone(true);
            masked.add(ship);
        }
        if (masked != null) {
            ctx.setLocal(MASKED_KEY, masked);
        }
    }

    @After(target = @MethodMatch(parameterCount = 7, returnTypeName = "float", staticType = StaticType.STATIC))
    public static void afterDanger(AfterContext ctx) {
        restore(ctx);
    }

    @Except(target = @MethodMatch(parameterCount = 7, returnTypeName = "float", staticType = StaticType.STATIC))
    public static void exceptDanger(ExceptContext ctx) {
        restore(ctx);
    }

    private static void restore(HookContext ctx) {
        List<ShipAPI> masked = ctx.getLocal(MASKED_KEY);
        if (masked == null) {
            return;
        }
        ctx.setLocal(MASKED_KEY, null);
        for (ShipAPI ship : masked) {
            ship.setDrone(false);
        }
    }

    private static boolean isDangerCall(HookContext ctx) {
        return ctx.getArg(0) instanceof ShipAPI && ctx.getArg(2) instanceof Vector2f && ctx.getArg(3) instanceof Vector2f;
    }

    private static boolean isStrikecraft(ShipAPI ship) {
        return ship.getVariant() != null && ship.getVariant().hasHullMod(STRIKECRAFT);
    }
}
