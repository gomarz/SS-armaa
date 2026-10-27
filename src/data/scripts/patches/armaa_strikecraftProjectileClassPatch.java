package data.scripts.patches;

import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.loading.BeamWeaponSpecAPI;
import com.fs.starfarer.api.loading.ProjectileSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import patchlib.api.context.AfterContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.patch.After;
import patchlib.api.patch.Patch;

/**
 * WeaponAPI.getProjectileCollisionClass() picks the "fired by a fighter" class by hull size, but shots and beams
 * spawn with it whenever the firing ship has FIGHTER collision. A flying strikecraft has FIGHTER collision without
 * being fighter-sized, so its guns report a friendly-fire class they never spawn with, and every vanilla
 * friendly-fire check (autofire, the AI's manual weapon group, the aim-line warning) holds fire for nothing.
 * Report what actually spawns. Missiles always spawn with their own spec's class, so missile weapons are left
 * alone: a MISSILE_FF missile such as the Reaper still checks for allies.
 *
 * Only ships with the strikeCraft hullmod that currently have FIGHTER collision are affected; flamed-out or
 * wrecked strikecraft drop back to SHIP collision, and their shots really can hit allies.
 */
@Patch(target = @ClassMatch(subtype = WeaponAPI.class))
public class armaa_strikecraftProjectileClassPatch {

    private static final String STRIKECRAFT = "strikeCraft";

    @After(target = @MethodMatch(methodName = "getProjectileCollisionClass", parameterCount = 0))
    public static void afterProjectileCollisionClass(AfterContext ctx) {
        Object self = ctx.getSelf();
        if (!(self instanceof WeaponAPI)) {
            return;
        }
        WeaponAPI weapon = (WeaponAPI) self;
        ShipAPI ship = weapon.getShip();
        if (ship == null || ship.isFighter() || ship.getCollisionClass() != CollisionClass.FIGHTER
                || ship.getVariant() == null || !ship.getVariant().hasHullMod(STRIKECRAFT)) {
            return;
        }

        WeaponSpecAPI spec = weapon.getSpec();
        if (spec == null) {
            return;
        }
        CollisionClass spawned = null;
        if (spec instanceof BeamWeaponSpecAPI) {
            spawned = ((BeamWeaponSpecAPI) spec).getCollisionClassIfByFighter();
        } else if (spec.getProjectileSpec() instanceof ProjectileSpecAPI) {
            spawned = ((ProjectileSpecAPI) spec.getProjectileSpec()).getCollisionClassIfByFighter();
        }
        if (spawned != null) {
            ctx.setReturnValue(spawned);
        }
    }
}
