package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import java.util.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.util.Misc;
import org.magiclib.util.MagicIncompatibleHullmods;

public class armaa_acsutilityscript extends BaseHullMod {

    public static final ArrayList<String> DATA_PREFIXES = new ArrayList<String>();
    public static final ArrayList<String> MODS = new ArrayList<String>();

    static {
        DATA_PREFIXES.add("alpha_core_acs_check_");
        DATA_PREFIXES.add("beta_core_acs_check_");
        DATA_PREFIXES.add("gamma_core_acs_check_");

        MODS.add("armaa_automatedCognitionShell");
    }
    public static final String ITEM = Commodities.ALPHA_CORE;
    public static final HashMap<String, String> itemMap = new HashMap<String, String>();

    static {
        for (String s : DATA_PREFIXES) {
            if (s.contains("alpha")) {
                itemMap.put(s, ITEM);
            } else if (s.contains("beta")) {
                itemMap.put(s, Commodities.BETA_CORE);
            } else {
                itemMap.put(s, Commodities.GAMMA_CORE);
            }
        }
    }
    @Override
    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) 
    {
      
    }
    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id)
    {
        final Map<String, Object> data = Global.getSector().getPersistentData();
        FleetMemberAPI member = ship.getMutableStats().getFleetMember();
        if(member == null)
            return;
        for (String DATA_PREFIX : DATA_PREFIXES) {
            for (String MOD : MODS) {
                if (data.containsKey(DATA_PREFIX + member.getId()) && !member.getVariant().hasHullMod(MOD)) {
                    //Log.info("Refunding " + " " + itemMap.get(DATA_PREFIX));
                    data.remove(DATA_PREFIX + member.getId());
                    if (member.getCaptain() != null && member.getCaptain().getId().contains("armaa_automata")) {
                        Misc.setUnremovable(member.getCaptain(), false);
                        member.setCaptain(null);
                        ship.setCaptain(null);
                    }
                    member.getVariant().removePermaMod("armaa_acsutilityscript");


                }
                else if (data.containsKey(DATA_PREFIX + member.getId()) && !member.getCaptain().getId().contains("armaa_automata")) {
                    member.getVariant().removeMod("armaa_automatedCognitionShell");
                    member.getVariant().removePermaMod("armaa_acsutilityscript");
                }
            }
        }  
    }   
    @Override
    public void advanceInCampaign(FleetMemberAPI member, float amount) 
    {

    }
}
