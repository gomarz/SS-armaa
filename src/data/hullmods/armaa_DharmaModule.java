package data.hullmods;

import com.fs.starfarer.api.combat.*;
import org.magiclib.util.MagicIncompatibleHullmods;

import java.util.HashSet;
import java.util.Set;

//Nabbed this from Alfonzo, madman extraordinaire
//Original code by DR, mad god of the modiverse
//Further modified by Nia, also a bit mad
//Even further modified by shoi
public class armaa_DharmaModule extends BaseHullMod {

    private static final Set<String> BLOCKED_FRONT = new HashSet<>();
    private static final Set<String> BLOCKED_OTHER = new HashSet<>();
    private static final Set<String> BLOCKED_OTHER_PLAYER_ONLY = new HashSet<>();

    static {
        /* No shields on my modules */
        BLOCKED_FRONT.add("frontemitter");
        BLOCKED_FRONT.add("frontshield");
        BLOCKED_FRONT.add("adaptiveshields");

        /* Modules don't move on their own */
        BLOCKED_OTHER.add("auxiliarythrusters");
        BLOCKED_OTHER.add("unstable_injector");

        /* Module's can't provide ECM/Nav */
        BLOCKED_OTHER.add("ecm");
        BLOCKED_OTHER.add("nav_relay");

        /* Logistics mods partially or completely don't apply on modules */
        BLOCKED_OTHER.add("operations_center");
        BLOCKED_OTHER.add("recovery_shuttles");
        BLOCKED_OTHER.add("additional_berthing");
        BLOCKED_OTHER.add("augmentedengines");
        BLOCKED_OTHER.add("auxiliary_fuel_tanks");
        BLOCKED_OTHER.add("efficiency_overhaul");
        BLOCKED_OTHER.add("expanded_cargo_holds");
        BLOCKED_OTHER.add("hiressensors");
        //BLOCKED_OTHER.add("insulatedengine"); // Niche use
        BLOCKED_OTHER.add("militarized_subsystems");
        //BLOCKED_OTHER.add("solar_shielding"); // Niche use
        BLOCKED_OTHER.add("surveying_equipment");

        /* Crew penalty doesn't reflect in campaign */
        BLOCKED_OTHER_PLAYER_ONLY.add("converted_hangar");
        BLOCKED_OTHER_PLAYER_ONLY.add("expanded_deck_crew");
        BLOCKED_OTHER_PLAYER_ONLY.add("TSC_converted_hangar");
    }

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
		
        switch (ship.getHullSpec().getBaseHullId()) {
            case "armaa_altagrave_leftleg":
            case "armaa_altagrave_rightleg":
                for (String tmp : BLOCKED_FRONT) {
                    if (ship.getVariant().getHullMods().contains(tmp)) {
                        MagicIncompatibleHullmods.removeHullmodWithWarning(ship.getVariant(), tmp, "armaa_dharmamodule");
                    }
                }
                break;
            default:
                break;
        }
        switch (ship.getHullSpec().getBaseHullId()) {
            case "armaa_altagrave_leftleg":
            case "armaa_altagrave_rightleg":
                for (String tmp : BLOCKED_OTHER) {
                    if (ship.getVariant().getHullMods().contains(tmp)) {
                        MagicIncompatibleHullmods.removeHullmodWithWarning(ship.getVariant(), tmp, "armaa_dharmamodule");
                    }
                }
                break;
            default:
                break;
        }
        switch (ship.getHullSpec().getBaseHullId()) {
            case "armaa_altagrave_leftleg":
            case "armaa_altagrave_rightleg":
                for (String tmp : BLOCKED_OTHER_PLAYER_ONLY) {
                    if (ship.getVariant().getHullMods().contains(tmp)) {
                        MagicIncompatibleHullmods.removeHullmodWithWarning(ship.getVariant(), tmp, "armaa_dharmamodule");
                    }
                }
                break;
            default:
                break;
        }
    }
}
