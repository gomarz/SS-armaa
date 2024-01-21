package data.scripts.shipsystems.ai;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.FluxTrackerAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAIScript;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.util.IntervalUtil;
import java.util.Iterator;
import java.util.List;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

public class armaa_siegeAI implements ShipSystemAIScript {
   private ShipAPI ship;
   private CombatEngineAPI engine;
   private ShipwideAIFlags flags;
   private ShipSystemAPI system;
   private IntervalUtil tracker = new IntervalUtil(0.5F, 1.0F);

   public void init(ShipAPI var1, ShipSystemAPI var2, ShipwideAIFlags var3, CombatEngineAPI var4) {
      this.ship = var1;
      this.flags = var3;
      this.engine = var4;
      this.system = var1.getSystem();
   }

   public void advance(float var1, Vector2f var2, Vector2f var3, ShipAPI var4) {
      this.tracker.advance(var1);
      if (this.engine != null) {
         if (!this.engine.isPaused()) {
            if (this.tracker.intervalElapsed()) {
               float var5 = 0.0F;
               if (!this.ship.areAnyEnemiesInRange()) {
                  var5 -= 9999.0F;
               }

               if (this.flags.hasFlag(AIFlags.BACKING_OFF)) {
                  var5 -= 5.0F;
               }

               if (this.flags.hasFlag(AIFlags.RUN_QUICKLY)) {
                  var5 -= 5.0F;
               }

               if (this.flags.hasFlag(AIFlags.NEEDS_HELP)) {
                  var5 -= 2.0F;
               }

               if (this.flags.hasFlag(AIFlags.HAS_INCOMING_DAMAGE)) {
                  var5 += 2.0F;
               }

               float var6 = 1.55F;
               Iterator var7 = this.ship.getAllWeapons().iterator();

               while(true) {
                  WeaponAPI var8;
                  do {
                     if (!var7.hasNext()) {
                        FluxTrackerAPI var12 = this.ship.getFluxTracker();
                        if (var12.getFluxLevel() >= 0.6F) {
                           var5 -= 9999.0F;
                        }

                        if (var5 >= 2.0F && !this.system.isActive()) {
                           this.ship.useSystem();
                        } else if (var5 <= 1.0F && this.system.isActive()) {
                           this.ship.useSystem();
                           return;
                        }

                        return;
                     }

                     var8 = (WeaponAPI)var7.next();
                  } while(var8.isDecorative());

                  if (this.system.isActive()) {
                     var6 = 0.0F;
                  }

                  List var9 = CombatUtils.getShipsWithinRange(this.ship.getLocation(), var8.getRange() * 1.55F);
                  Iterator var10 = var9.iterator();

                  while(var10.hasNext()) {
                     ShipAPI var11 = (ShipAPI)var10.next();
                     if (var11.getOwner() != this.ship.getOwner() && !var11.isHulk() && !var11.isFighter() && var11.isAlive() && !var11.isAlly()) {
                        if (var11.isDestroyer()) {
                           var5 += 2.0F;
                        }

                        if (var11.isCruiser()) {
                           var5 += 2.0F;
                        }

                        if (var11.isCapital()) {
                           var5 += 2.0F;
                        }

                        if (MathUtils.getDistance(var11, this.ship.getLocation()) <= 1250.0F) {
                           this.flags.setFlag(AIFlags.BACK_OFF, 1.0F);
                        }
                     }
                  }

                  if (var9.isEmpty()) {
                     var5 -= 5.0F;
                  }
               }
            }
         }
      }
   }
}
