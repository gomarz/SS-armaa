package data.scripts.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript.State;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript.StatusData;
import java.awt.Color;
import java.util.Iterator;

public class armaa_tshellStats extends BaseShipSystemScript {
   public static final float MAX_TIME_MULT = 8.0F;
   public static final float MIN_TIME_MULT = 0.1F;
   public static final float DAM_MULT = 0.1F;
   public static final Color JITTER_COLOR = new Color(255, 0, 0, 55);
   public static final Color JITTER_UNDER_COLOR = new Color(245, 188, 44, 155);

   public void apply(MutableShipStatsAPI var1, String var2, State var3, float var4) {
      ShipAPI var5 = null;
      boolean var6 = false;
      if (var1.getEntity() instanceof ShipAPI) {
         var5 = (ShipAPI)var1.getEntity();
         var6 = var5 == Global.getCombatEngine().getPlayerShip();
         var2 = var2 + "_" + var5.getId();
         float var7 = var4;
         float var8 = 0.0F;
         float var9 = 10.0F;
         if (var3 == State.IN) {
            Iterator var10 = var5.getAllWeapons().iterator();

            while(var10.hasNext()) {
               WeaponAPI var11 = (WeaponAPI)var10.next();
               if (var11.getCooldownRemaining() > 0.0F) {
                  var11.setRemainingCooldownTo(0.0F);
               }
            }

            var7 = var4 / (1.0F / var5.getSystem().getChargeUpDur());
            if (var7 > 1.0F) {
               var7 = 1.0F;
            }

            var8 = var7 * var9;
         } else if (var3 == State.ACTIVE) {
            var7 = 1.0F;
            var8 = var9;
         } else if (var3 == State.OUT) {
            var8 = var4 * var9;
         }

         var7 = (float)Math.sqrt((double)var7);
         var4 *= var4;
         var5.setJitter(this, JITTER_COLOR, var7, 3, 0.0F, 0.0F + var8);
         var5.setJitterUnder(this, JITTER_UNDER_COLOR, var7, 25, 0.0F, 7.0F + var8);
         float var12 = 1.0F + 7.0F * var4;
         var1.getTimeMult().modifyMult(var2, var12);
         if (var6) {
            Global.getCombatEngine().getTimeMult().modifyMult(var2, 1.0F / var12);
         } else {
            Global.getCombatEngine().getTimeMult().unmodify(var2);
         }

         var5.getEngineController().fadeToOtherColor(this, JITTER_COLOR, new Color(0, 0, 0, 0), var4, 0.5F);
         var5.getEngineController().extendFlame(this, -0.25F, -0.25F, -0.25F);
      }
   }

   public void unapply(MutableShipStatsAPI var1, String var2) {
      ShipAPI var3 = null;
      boolean var4 = false;
      if (var1.getEntity() instanceof ShipAPI) {
         var3 = (ShipAPI)var1.getEntity();
         var4 = var3 == Global.getCombatEngine().getPlayerShip();
         var2 = var2 + "_" + var3.getId();
         Global.getCombatEngine().getTimeMult().unmodify(var2);
         var1.getTimeMult().unmodify(var2);
      }
   }

   public StatusData getStatusData(int var1, State var2, float var3) {
      float var4 = 1.0F + 7.0F * var3;
      return var1 == 0 ? new StatusData("time flow altered", false) : null;
   }
}
