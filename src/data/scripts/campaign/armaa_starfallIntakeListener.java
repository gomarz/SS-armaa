package data.scripts.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;

/**
 * Sets $armaa_sfo_intakeDestroyed the moment the intake is wiped out, wherever
 * that happens - en route, at the exercise, or on the way to the installation.
 *
 * A named class rather than an anonymous one, so it holds no reference to the
 * mission and saves and loads with the fleet.
 *
 * Only destruction counts. The intake also despawns normally (reaching
 * Fikenhild on the Deny/Solo paths, the auto-despawn script, player far away),
 * so any other despawn reason must not set the flag.
 */
public class armaa_starfallIntakeListener implements FleetEventListener {

    public static final String DESTROYED_KEY = "$armaa_sfo_intakeDestroyed";

    @Override
    public void reportFleetDespawnedToListener(CampaignFleetAPI fleet,
            FleetDespawnReason reason, Object param) {
        if (reason == FleetDespawnReason.DESTROYED_BY_BATTLE
                || reason == FleetDespawnReason.NO_MEMBERS) {
            Global.getSector().getMemoryWithoutUpdate().set(DESTROYED_KEY, true);
            if (param instanceof BattleAPI) {
                noteKiller(fleet, (BattleAPI) param);
            }
        }
        fleet.removeEventListener(this);
    }

    @Override
    public void reportBattleOccurred(CampaignFleetAPI fleet,
            CampaignFleetAPI primaryWinner, BattleAPI battle) {
        // A fleet with no ships left after a battle is dead even if the despawn
        // callback hasn't run yet.
        if (fleet.getFleetData().getMembersListCopy().isEmpty()) {
            Global.getSector().getMemoryWithoutUpdate().set(DESTROYED_KEY, true);
            noteKiller(fleet, battle);
        }
    }

    /**
     * Flag set when the player was in the battle on the opposite side from the
     * intake, which is the only way the player can be the one who destroyed it.
     */
    public static final String KILLED_BY_PLAYER_KEY = "$armaa_sfo_intakeKilledByPlayer";

    private void noteKiller(CampaignFleetAPI fleet, BattleAPI battle) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (battle == null || player == null || !battle.isPlayerInvolved()) {
            return;
        }
        if (!battle.onSameSide(fleet, player)) {
            Global.getSector().getMemoryWithoutUpdate().set(KILLED_BY_PLAYER_KEY, true);
        }
    }
}
