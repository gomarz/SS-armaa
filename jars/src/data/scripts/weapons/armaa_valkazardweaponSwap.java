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

public class armaa_valkazardweaponSwap extends BaseHullMod {
   public Map<Integer, String> LEFT_SELECTOR = new HashMap();
   public Map<Integer, String> RIGHT_SELECTOR;
   private final Map<String, Integer> SWITCH_TO_LEFT;
   private final Map<String, Integer> SWITCH_TO_RIGHT;
   private final Map<Integer, String> RIGHTSWITCH;
   private final Map<Integer, String> LEFTSWITCH;
   public Map<Integer, String> CORE_SELECTOR;
   private final Map<String, Integer> SWITCH_TO_CORE;
   private final Map<Integer, String> CORESWITCH;
   private final String leftslotID;
   private final String rightslotID;
   private final String coreslotID;

   public armaa_valkazardweaponSwap() {
      this.LEFT_SELECTOR.put(0, "armaa_valkazard_harpoon");
      this.LEFT_SELECTOR.put(1, "armaa_valkazard_shotgun_left");
      this.LEFT_SELECTOR.put(2, "armaa_valkazard_pulse_rifle_left");
      this.LEFT_SELECTOR.put(3, "armaa_valkazard_machinegun_left");
      this.LEFT_SELECTOR.put(4, "armaa_valkazard_rcl_left");
      this.LEFT_SELECTOR.put(5, "armaa_valkazard_blade");
      this.RIGHT_SELECTOR = new HashMap();
      this.RIGHT_SELECTOR.put(0, "armaa_valkazard_rcl");
      this.RIGHT_SELECTOR.put(1, "armaa_valkazard_chaingun");
      this.RIGHT_SELECTOR.put(2, "armaa_valkazard_pulse_rifle_right");
      this.RIGHT_SELECTOR.put(3, "armaa_valkazard_machinegun_right");
      this.RIGHT_SELECTOR.put(4, "armaa_valkazard_shotgun_right");
      this.SWITCH_TO_LEFT = new HashMap();
      this.SWITCH_TO_LEFT.put("armaa_valkazard_harpoon", 5);
      this.SWITCH_TO_LEFT.put("armaa_valkazard_blade", 4);
      this.SWITCH_TO_LEFT.put("armaa_valkazard_rcl_left", 3);
      this.SWITCH_TO_LEFT.put("armaa_valkazard_machinegun_left", 2);
      this.SWITCH_TO_LEFT.put("armaa_valkazard_pulse_rifle_left", 1);
      this.SWITCH_TO_LEFT.put("armaa_valkazard_shotgun_left", 0);
      this.SWITCH_TO_RIGHT = new HashMap();
      this.SWITCH_TO_RIGHT.put("armaa_valkazard_rcl", 4);
      this.SWITCH_TO_RIGHT.put("armaa_valkazard_shotgun_right", 3);
      this.SWITCH_TO_RIGHT.put("armaa_valkazard_machinegun_right", 2);
      this.SWITCH_TO_RIGHT.put("armaa_valkazard_pulse_rifle_right", 1);
      this.SWITCH_TO_RIGHT.put("armaa_valkazard_chaingun", 0);
      this.RIGHTSWITCH = new HashMap();
      this.RIGHTSWITCH.put(0, "armaa_selector_rcl");
      this.RIGHTSWITCH.put(1, "armaa_selector_chaingun");
      this.RIGHTSWITCH.put(2, "armaa_selector_pulse_rifle_right");
      this.RIGHTSWITCH.put(3, "armaa_selector_machinegun_right");
      this.RIGHTSWITCH.put(4, "armaa_selector_shotgun_right");
      this.LEFTSWITCH = new HashMap();
      this.LEFTSWITCH.put(0, "armaa_selector_harpoon");
      this.LEFTSWITCH.put(1, "armaa_selector_shotgun_left");
      this.LEFTSWITCH.put(2, "armaa_selector_pulse_rifle_left");
      this.LEFTSWITCH.put(3, "armaa_selector_machinegun_left");
      this.LEFTSWITCH.put(4, "armaa_selector_rcl_left");
      this.LEFTSWITCH.put(5, "armaa_selector_blade_VAL");
      this.CORE_SELECTOR = new HashMap();
      this.CORE_SELECTOR.put(0, "armaa_valkazard_torso");
      this.CORE_SELECTOR.put(1, "armaa_valkazard_torso_shield");
      this.CORE_SELECTOR.put(2, "armaa_valkazard_torso_boson");
      this.CORE_SELECTOR.put(3, "armaa_valkazard_torso_chaosburst");
      this.CORE_SELECTOR.put(4, "armaa_valkazard_torso_ac");
      this.SWITCH_TO_CORE = new HashMap();
      this.SWITCH_TO_CORE.put("armaa_valkazard_torso", 4);
      this.SWITCH_TO_CORE.put("armaa_valkazard_torso_ac", 3);
      this.SWITCH_TO_CORE.put("armaa_valkazard_torso_chaosburst", 2);
      this.SWITCH_TO_CORE.put("armaa_valkazard_torso_boson", 1);
      this.SWITCH_TO_CORE.put("armaa_valkazard_torso_shield", 0);
      this.CORESWITCH = new HashMap();
      this.CORESWITCH.put(0, "armaa_selector_blunderbuss");
      this.CORESWITCH.put(1, "armaa_selector_counter_shield");
      this.CORESWITCH.put(2, "armaa_selector_boson");
      this.CORESWITCH.put(3, "armaa_selector_chaos");
      this.CORESWITCH.put(4, "armaa_selector_ac20");
      this.leftslotID = "C_ARML";
      this.rightslotID = "A_GUN";
      this.coreslotID = "B_TORSO";
   }

   public void applyEffectsBeforeShipCreation(HullSize var1, MutableShipStatsAPI var2, String var3) {
      boolean var4 = true;
      boolean var5 = true;
      boolean var6 = true;
      int var7 = 0;

      int var8;
      for(var8 = 0; var8 < this.SWITCH_TO_LEFT.size(); ++var8) {
         if (var2.getVariant().getHullMods().contains(this.LEFTSWITCH.get(var8))) {
            var4 = false;
            ++var7;
         }
      }

      for(var8 = 0; var8 < this.SWITCH_TO_RIGHT.size(); ++var8) {
         if (var2.getVariant().getHullMods().contains(this.RIGHTSWITCH.get(var8))) {
            var5 = false;
            ++var7;
         }
      }

      for(var8 = 0; var8 < this.CORESWITCH.size(); ++var8) {
         if (var2.getVariant().getHullMods().contains(this.CORESWITCH.get(var8)) && var2.getVariant().getWeaponSpec("B_TORSO") != null) {
            var6 = false;
            ++var7;
         }
      }

      boolean var9;
      String var10;
      if (var4) {
         var9 = false;
         if (var2.getVariant().getWeaponSpec("C_ARML") != null && this.SWITCH_TO_LEFT.containsKey(var2.getVariant().getWeaponSpec("C_ARML").getWeaponId())) {
            var8 = (Integer)this.SWITCH_TO_LEFT.get(var2.getVariant().getWeaponSpec("C_ARML").getWeaponId());
         } else {
            var8 = MathUtils.getRandomNumberInRange(0, this.SWITCH_TO_LEFT.size() - 1);
            var9 = true;
         }

         var2.getVariant().addMod((String)this.LEFTSWITCH.get(var8));
         var2.getVariant().clearSlot("C_ARML");
         var10 = (String)this.LEFT_SELECTOR.get(var8);
         var2.getVariant().addWeapon("C_ARML", var10);
         if (var9) {
            var2.getVariant().autoGenerateWeaponGroups();
         }
      }

      if (var5) {
         var9 = false;
         if (var2.getVariant().getWeaponSpec("A_GUN") != null) {
            var8 = (Integer)this.SWITCH_TO_RIGHT.get(var2.getVariant().getWeaponSpec("A_GUN").getWeaponId());
         } else {
            var8 = MathUtils.getRandomNumberInRange(0, this.SWITCH_TO_RIGHT.size() - 1);
            var9 = true;
         }

         var2.getVariant().addMod((String)this.RIGHTSWITCH.get(var8));
         var2.getVariant().clearSlot("A_GUN");
         var10 = (String)this.RIGHT_SELECTOR.get(var8);
         var2.getVariant().addWeapon("A_GUN", var10);
         if (var9) {
            var2.getVariant().autoGenerateWeaponGroups();
         }
      }

      if (var6) {
         var9 = false;
         if (var2.getVariant().getWeaponSpec("B_TORSO") != null) {
            var8 = (Integer)this.SWITCH_TO_CORE.get(var2.getVariant().getWeaponSpec("B_TORSO").getWeaponId());
         } else {
            var8 = MathUtils.getRandomNumberInRange(0, this.CORESWITCH.size() - 1);
            var10 = "";
            Iterator var11 = var2.getVariant().getHullMods().iterator();

            while(var11.hasNext()) {
               String var12 = (String)var11.next();
               if (this.CORESWITCH.containsValue(var12)) {
                  var10 = var12;
               }
            }

            var2.getVariant().removeMod(var10);
            var9 = true;
         }

         var2.getVariant().addMod((String)this.CORESWITCH.get(var8));
         var2.getVariant().clearSlot("B_TORSO");
         var10 = (String)this.CORE_SELECTOR.get(var8);
         var2.getVariant().addWeapon("B_TORSO", var10);
         if (var9) {
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
            } while(!this.LEFT_SELECTOR.containsValue(var4.getWeaponSpecIfWeapon().getWeaponId()) && !this.RIGHT_SELECTOR.containsValue(var4.getWeaponSpecIfWeapon().getWeaponId()) && !this.CORE_SELECTOR.containsValue(var4.getWeaponSpecIfWeapon().getWeaponId()));

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
