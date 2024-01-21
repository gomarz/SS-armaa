package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.lazywizard.lazylib.MathUtils;

public class armaa_leynosWeaponSwap extends BaseHullMod {
   public Map<Integer, String> LEFT_SELECTOR = new HashMap();
   public Map<Integer, String> RIGHT_SELECTOR;
   private final Map<String, Integer> SWITCH_TO_LEFT;
   private final Map<String, Integer> SWITCH_TO_RIGHT;
   private final Map<Integer, String> LEFTSWITCH;
   private final Map<Integer, String> RIGHTSWITCH;
   private final String leftslotID;
   private final String rightslotID;

   public armaa_leynosWeaponSwap() {
      this.LEFT_SELECTOR.put(0, "armaa_leynosBoostKnuckle");
      this.LEFT_SELECTOR.put(1, "armaa_leynos_ppc");
      this.LEFT_SELECTOR.put(2, "armaa_leynos_railgun");
      this.LEFT_SELECTOR.put(3, "armaa_leynosBazooka");
      this.RIGHT_SELECTOR = new HashMap();
      this.RIGHT_SELECTOR.put(0, "armaa_leynos_shotgun_right");
      this.RIGHT_SELECTOR.put(1, "armaa_aleste_rightArm");
      this.RIGHT_SELECTOR.put(2, "armaa_leynos_autocannon_right");
      this.RIGHT_SELECTOR.put(3, "armaa_leynos_crusher_right");
      this.SWITCH_TO_LEFT = new HashMap();
      this.SWITCH_TO_LEFT.put("armaa_leynosBazooka", 2);
      this.SWITCH_TO_LEFT.put("armaa_leynosBoostKnuckle", 3);
      this.SWITCH_TO_LEFT.put("armaa_leynos_railgun", 1);
      this.SWITCH_TO_LEFT.put("armaa_leynos_ppc", 0);
      this.SWITCH_TO_RIGHT = new HashMap();
      this.SWITCH_TO_RIGHT.put("armaa_leynos_autocannon_right", 1);
      this.SWITCH_TO_RIGHT.put("armaa_leynos_shotgun_right", 3);
      this.SWITCH_TO_RIGHT.put("armaa_aleste_rightArm", 0);
      this.SWITCH_TO_RIGHT.put("armaa_leynos_crusher_right", 2);
      this.LEFTSWITCH = new HashMap();
      this.LEFTSWITCH.put(0, "armaa_selector_fist");
      this.LEFTSWITCH.put(1, "armaa_selector_ppc");
      this.LEFTSWITCH.put(2, "armaa_selector_railgun");
      this.LEFTSWITCH.put(3, "armaa_selector_bazooka");
      this.RIGHTSWITCH = new HashMap();
      this.RIGHTSWITCH.put(0, "armaa_selector_shotgun");
      this.RIGHTSWITCH.put(1, "armaa_selector_lrifle");
      this.RIGHTSWITCH.put(2, "armaa_selector_ac");
      this.RIGHTSWITCH.put(3, "armaa_selector_crusher");
      this.leftslotID = "C_ARML";
      this.rightslotID = "A_GUN";
   }

   public void applyEffectsBeforeShipCreation(HullSize var1, MutableShipStatsAPI var2, String var3) {
      boolean var4 = true;
      boolean var5 = true;
      int var6 = 0;

      int var7;
      for(var7 = 0; var7 < this.SWITCH_TO_LEFT.size(); ++var7) {
         if (var2.getVariant().getHullMods().contains(this.LEFTSWITCH.get(var7))) {
            var4 = false;
            ++var6;
         }
      }

      for(var7 = 0; var7 < this.SWITCH_TO_RIGHT.size(); ++var7) {
         if (var2.getVariant().getHullMods().contains(this.RIGHTSWITCH.get(var7))) {
            var5 = false;
            ++var6;
         }
      }

      boolean var8;
      String var9;
      if (var4) {
         var8 = false;
         if (var2.getVariant().getWeaponSpec("C_ARML") != null) {
            var7 = (Integer)this.SWITCH_TO_LEFT.get(var2.getVariant().getWeaponSpec("C_ARML").getWeaponId());
         } else {
            var7 = MathUtils.getRandomNumberInRange(0, this.SWITCH_TO_LEFT.size() - 1);
            var8 = true;
         }

         var2.getVariant().addMod((String)this.LEFTSWITCH.get(var7));
         var2.getVariant().clearSlot("C_ARML");
         var9 = (String)this.LEFT_SELECTOR.get(var7);
         var2.getVariant().addWeapon("C_ARML", var9);
         if (var8) {
            var2.getVariant().autoGenerateWeaponGroups();
         }
      }

      if (var5) {
         var8 = false;
         if (var2.getVariant().getWeaponSpec("A_GUN") != null) {
            var7 = (Integer)this.SWITCH_TO_RIGHT.get(var2.getVariant().getWeaponSpec("A_GUN").getWeaponId());
         } else {
            var7 = MathUtils.getRandomNumberInRange(0, this.SWITCH_TO_RIGHT.size() - 1);
            var8 = true;
         }

         var2.getVariant().addMod((String)this.RIGHTSWITCH.get(var7));
         var2.getVariant().clearSlot("A_GUN");
         var9 = (String)this.RIGHT_SELECTOR.get(var7);
         var2.getVariant().addWeapon("A_GUN", var9);
         if (var8) {
            var2.getVariant().autoGenerateWeaponGroups();
         }
      }

   }

   public void applyEffectsAfterShipCreation(ShipAPI var1, String var2) {
      if (var1.getOriginalOwner() < 0 && Global.getSector() != null && Global.getSector().getPlayerFleet() != null && Global.getSector().getPlayerFleet().getCargo() != null && Global.getSector().getPlayerFleet().getCargo().getStacksCopy() != null && !Global.getSector().getPlayerFleet().getCargo().getStacksCopy().isEmpty()) {
         Iterator var3 = Global.getSector().getPlayerFleet().getCargo().getStacksCopy().iterator();

         while(true) {
            CargoStackAPI var4;
            do {
               do {
                  if (!var3.hasNext()) {
                     return;
                  }

                  var4 = (CargoStackAPI)var3.next();
               } while(!var4.isWeaponStack());
            } while(!this.LEFT_SELECTOR.containsValue(var4.getWeaponSpecIfWeapon().getWeaponId()) && !this.RIGHT_SELECTOR.containsValue(var4.getWeaponSpecIfWeapon().getWeaponId()));

            Global.getSector().getPlayerFleet().getCargo().removeStack(var4);
         }
      }
   }

   public String getDescriptionParam(int var1, HullSize var2) {
      if (var1 == 0) {
         return "A";
      } else if (var1 == 1) {
         return "B";
      } else if (var1 == 2) {
         return "C";
      } else {
         return var1 == 3 ? "D" : null;
      }
   }

   public boolean isApplicableToShip(ShipAPI var1) {
      return var1.getHullSpec().getHullId().startsWith("armaa_");
   }
}
