package data.scripts.world.systems;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.procgen.DefenderDataOverride;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator;
import com.fs.starfarer.api.impl.campaign.procgen.themes.SalvageSpecialAssigner;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial;
import com.fs.starfarer.api.campaign.OrbitAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.jetbrains.annotations.Nullable;

public class armaa_nekki {

    public static String MECH_ID = "valkazard";


    //Mini-function for generating derelicts
    public static SectorEntityToken addDerelict(StarSystemAPI system, String variantId, OrbitAPI orbit,
            ShipRecoverySpecial.ShipCondition condition, boolean recoverable,
            @Nullable DefenderDataOverride defenders) {

        DerelictShipEntityPlugin.DerelictShipData params = new DerelictShipEntityPlugin.DerelictShipData(new ShipRecoverySpecial.PerShipData(variantId, condition), false);
        SectorEntityToken ship = BaseThemeGenerator.addSalvageEntity(system, Entities.WRECK, "derelict", params);
        //SectorEntityToken debris = BaseThemeGenerator.addDebrisField(BaseThemeGenerator.StarSystemData, ship, 250f);
        WeightedRandomPicker<String> graveships = new WeightedRandomPicker<>();
        graveships.add(Factions.TRITACHYON);
        graveships.add(Factions.REMNANTS);
        graveships.add(Factions.HEGEMONY);
        ship.setDiscoverable(true);
        ship.setId("valkazard");
        ship.setOrbit(orbit);
        ship.setName("Derelict Cataphract");
        if (recoverable) {
            SalvageSpecialAssigner.ShipRecoverySpecialCreator creator = new SalvageSpecialAssigner.ShipRecoverySpecialCreator(null, 0, 0, false, null, null);
            Misc.setSalvageSpecial(ship, creator.createSpecial(ship, null));
        }
        if (defenders == null) {

        }
        return ship;
    }
}
