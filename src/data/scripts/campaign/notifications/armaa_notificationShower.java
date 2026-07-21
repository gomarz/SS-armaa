package data.scripts.campaign.notifications;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.CargoAPI.CargoItemType;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lazywizard.lazylib.campaign.CampaignUtils;

import java.util.*;

public class armaa_notificationShower implements EveryFrameScript {

    private final IntervalUtil timer = new IntervalUtil(1f, 5f);
    private final Map<String, armaa_NotificationBase> notifications = new HashMap<>();
    private final List<armaa_NotificationBase> notificationQueue = new ArrayList<>();

    private static final List<String> validEntities = new ArrayList<>();
    private static final List<String> validKeys = new ArrayList<>();
    private static final List<String> validSingleKeys = new ArrayList<>();
    // The only notifications that do NOT require Dawn in the fleet
    // (this is why validSingleKeys is a separate list)
    private static final Set<String> DAWN_EXEMPT_ENTITIES = new HashSet<>();

    static {
        DAWN_EXEMPT_ENTITIES.add("valkHunter");
        DAWN_EXEMPT_ENTITIES.add("cassianReadyToChat");

        validEntities.add("station_galatia_academy");
        validEntities.add("mazalot");
        validEntities.add("volturn");
        validEntities.add("kantas_den");
        validKeys.add("$anh_inProgress");
        validKeys.add("$encounteredDweller");
        validKeys.add("$dawnOutpaced");
        validKeys.add("$dawnNoticedAI");
        validKeys.add("$pk_recovered");
        validKeys.add("$bffi_goMeetMenesYaribay");

        // ??? Why is this seperate?
        validSingleKeys.add("$armaa_engagedValkHunters");
        validSingleKeys.add("$armaa_cassianReadyToChat");
        
    }

    public void showNotificationOnce(String id) {
        armaa_NotificationBase notification = notifications.get(id);
        if (notification != null) {
            notification.shouldBeShownOnce = true;
        }
    }

    public void showNotificationRepeatable(String id) {
        armaa_NotificationBase notification = notifications.get(id);
        if (notification != null) {
            notification.shouldBeShownRepeatable = true;
        }
    }

    public void addNotification(final String entity) {
        String id = "armaa_" + entity + "_event_id";
        if (!notifications.containsKey(id)) {
            armaa_NotificationBase notif = new armaa_NotificationBase(id) {
                @Override
                public InteractionDialogPlugin create() {
                    return new armaa_approachingPlanetNotification(entity);
                }
            };
            notif.requiresDawn = !DAWN_EXEMPT_ENTITIES.contains(entity);
            notifications.put(id, notif);
        }
    }

    // transient: never serialized into the save, so the sweep runs once per game session
    private transient boolean recoverySweepDone = false;

    /**
     * Backwards compatibility for saves affected by the old bug. Any
     * $player.armaa_*_event_id flag that is still true was set by showNotification()
     * but never flipped back to false by its rules.csv rule -- meaning the dialog
     * never actually displayed, even if wasNotificationShown got stamped true.
     * Reset the persistent flag and re-queue it.
     */
    private void recoverUnshownNotifications() {
        recoverySweepDone = true;
        MemoryAPI mem = Global.getSector().getPlayerMemoryWithoutUpdate();
        for (String key : new ArrayList<>(mem.getKeys())) {
            if (!key.startsWith("$armaa_") || !key.endsWith("_event_id")) {
                continue;
            }
            if (!mem.getBoolean(key)) {
                continue; // rules.csv set it false -> genuinely shown, leave it alone
            }
            String id = key.substring(1); // strip "$" -> "armaa_..._event_id"
            String entity = id.substring("armaa_".length(), id.length() - "_event_id".length());

            Global.getSector().getPersistentData().put("$" + id + "wasNotificationShown", false);
            addNotification(entity); // no-ops if already in the map
            showNotificationOnce(id);
            Global.getLogger(armaa_notificationShower.class).info(
                    "Recovering notification " + id + " that was consumed without displaying");
        }
    }

    public boolean dawnIsPresent() {
        for (OfficerDataAPI officer : Global.getSector().getPlayerFleet().getFleetData().getOfficersCopy()) {
            if ("armaa_dawn".equals(officer.getPerson().getId())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void advance(float amount) {
        if (Global.getSector().isInNewGameAdvance()
                || Global.getSector().getCampaignUI().isShowingDialog()
                || Global.getSector().getCampaignUI().isShowingMenu()
                || Global.getCurrentState() == GameState.TITLE) {
            return;
        }

        // One-time per session: rescue notifications the old code ate
        if (!recoverySweepDone) {
            recoverUnshownNotifications();
        }

        timer.advance(amount);
        if (timer.intervalElapsed()) {
            CampaignFleetAPI flt = Global.getSector().getPlayerFleet();
            if (flt == null) {
                return;
            }

            List<SectorEntityToken> entities = new ArrayList<>();
            entities.addAll(CampaignUtils.getNearbyEntitiesWithTag(flt, 1000f, Tags.STATION));
            entities.addAll(CampaignUtils.getNearbyEntitiesWithTag(flt, 1000f, Tags.PLANET));
            entities.addAll(CampaignUtils.getNearbyEntitiesWithTag(flt, 1000f, Tags.CRYOSLEEPER));

            // Single-time event
            String valkHunters = (String)Global.getSector().getMemoryWithoutUpdate().get("$armaa_engagedValkHunters");
            if (valkHunters != null && notifications.get("armaa_valkHunter_event_id") == null
                    && valkHunters.equals("true")) {
                addNotification("valkHunter");
                showNotificationOnce("armaa_valkHunter_event_id");
            }
            if (notifications.get("armaa_cassianReadyToChat_event_id") == null
                    && Global.getSector().getMemoryWithoutUpdate().contains("$armaa_cassianReadyToChat")) {
                addNotification("cassianReadyToChat");
                showNotificationOnce("armaa_cassianReadyToChat_event_id");
            }
            // Dawn-triggered notifications
            if (dawnIsPresent()) {
                for (SectorEntityToken entity : entities) {
                    if (entity.hasTag(Tags.CRYOSLEEPER)) {
                        String id = "armaa_" + Tags.CRYOSLEEPER + "_event_id";
                        if (!notifications.containsKey(id)) {
                            addNotification(Tags.CRYOSLEEPER);
                            showNotificationOnce(id);
                        }
                    }

                    if (validEntities.contains(entity.getId())) {
                        String id = "armaa_" + entity.getId() + "_event_id";
                        if (!notifications.containsKey(id)) {
                            addNotification(entity.getId());
                            showNotificationOnce(id);
                        }
                    }
                }

                // Hullmod check
                if (!notifications.containsKey("armaa_shroudmod_event_id")) {
                    for (FleetMemberAPI member : flt.getFleetData().getMembersListCopy()) {
                        for (String modId : member.getVariant().getHullMods()) {
                            HullModSpecAPI spec = Global.getSettings().getHullModSpec(modId);
                            if (spec != null && spec.hasTag(Tags.SHROUDED)) {
                                addNotification("shroudmod");
                                showNotificationOnce("armaa_shroudmod_event_id");
                                break;
                            }
                        }
                    }
                }

                // Planetkiller check
                if (!notifications.containsKey("armaa_pk_event_id")) {
                    if (flt.getCargo().getQuantity(CargoItemType.SPECIAL, new SpecialItemData("planetkiller", null)) > 0) {
                        addNotification("pk");
                        showNotificationOnce("armaa_pk_event_id");
                    }
                }

                // Memory triggers
                for (String key : Global.getSector().getMemoryWithoutUpdate().getKeys()) {
                    if (validKeys.contains(key)) {
                        String id = "armaa_" + key + "_event_id";
                        if (!notifications.containsKey(id)) {
                            addNotification(key);
                            showNotificationOnce(id);
                        }
                    }
                }

                for (String key : Global.getSector().getPlayerMemoryWithoutUpdate().getKeys()) {
                    String id = "armaa_" + key + "_event_id";
                    if (validKeys.contains(key) && !notifications.containsKey(id)) {
                        addNotification(key);
                        showNotificationOnce(id);
                    } else if ("$dawnVisitedShrines".equals(key)) {
                        float num = Global.getSector().getPlayerMemoryWithoutUpdate().getFloat(key);
                        if (num > 4f && !notifications.containsKey("armaa_CoF_event_id")) {
                            addNotification("CoF");
                            showNotificationOnce("armaa_CoF_event_id");
                        }
                    }
                }
            }

            // Queue valid notifications
            for (armaa_NotificationBase notif : notifications.values()) {
                if ((notif.shouldBeShownOnce || notif.shouldBeShownRepeatable)
                        && !notificationQueue.contains(notif)) {
                    notificationQueue.add(notif);
                }
            }
        }

        // Confirmation watchdog: anything that fired showRuleDialog() but whose
        // $player.<id> flag is still true (rule never fired) gets re-queued after a timeout
        for (armaa_NotificationBase notif : notifications.values()) {
            notif.advanceConfirmation(amount);
        }

        // Show next notification if none is showing
        if (!Global.getSector().getCampaignUI().isShowingDialog() && !notificationQueue.isEmpty()) {
            armaa_NotificationBase next = notificationQueue.remove(0);
            // Re-verify gating at show time: a retried or recovered notification may
            // have been armed while Dawn was aboard but popped after she left.
            // shouldBeShownOnce is still true here, so the interval loop re-queues
            // it and it fires once she's back in the fleet. Nothing is consumed.
            if (next.requiresDawn && !dawnIsPresent()) {
                return;
            }
            next.showIfRequested();
        }
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }
}
