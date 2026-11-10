package ngprenewed;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

/**
 * Puts Legacy Station (internally still "the Ledger": ids stay for old saves) next to the player the first time the campaign is ready: a new game, or
 * the first load of an older save. Waits until no dialog is open, so start screens from other mods
 * (Nexerelin's starting options, for example) have placed the fleet first.
 */
public final class LedgerSpawner {

    public static final String ENTITY_TYPE = "ngp_ledger_station";
    /** The name players see (Andrzej, 2026-11-10: not "the Ledger"). */
    public static final String NAME = "Legacy Station";
    /** descriptions.csv line the hover tooltip reads; without one the game shows no tooltip at all. */
    public static final String DESCRIPTION_ID = "ngp_legacy_station";
    /** Vanilla memory key: keep the system's music playing while the player talks to the station. */
    private static final String KEEP_MUSIC = "$playLocationMusicDuringEnc";

    private LedgerSpawner() {
    }

    public static void ensure(SaveState state) {
        SectorEntityToken existing = state.ledger();
        if (existing != null && existing.isAlive()) {
            fixUp(existing);
            return;
        }

        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        if (ui == null || ui.isShowingDialog() || ui.isShowingMenu()) return;
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null || fleet.getContainingLocation() == null) return;

        LocationAPI where = fleet.getContainingLocation();
        SectorEntityToken ledger = where.addCustomEntity("ngp_ledger_" + Misc.genUID(), null, ENTITY_TYPE, Factions.NEUTRAL);
        ledger.setDiscoverable(false);
        fixUp(ledger);

        Vector2f at = fleet.getLocation();
        if (where instanceof StarSystemAPI && !where.isHyperspace() && ((StarSystemAPI) where).getCenter() != null) {
            SectorEntityToken center = ((StarSystemAPI) where).getCenter();
            float distance = Math.max(Misc.getDistance(center.getLocation(), at), center.getRadius() + 600f);
            // A little ahead of the fleet along its orbit, so it does not sit on top of whatever the
            // fleet was parked next to.
            float angle = Misc.getAngleInDegrees(center.getLocation(), at) + (float) Math.toDegrees(350f / distance);
            ledger.setCircularOrbitPointingDown(center, angle, distance, Math.max(60f, distance / 10f));
        } else {
            ledger.setFixedLocation(at.x + 300f, at.y + 200f);
        }
        state.setLedger(ledger);

        LedgerIntel intel = new LedgerIntel(ledger);
        Global.getSector().getIntelManager().addIntel(intel, true);
        ui.addMessage(NAME + " has opened its doors near your fleet.", Misc.getHighlightColor());
    }

    /**
     * Without has_interaction_dialog the game draws the station but never lets the player click it
     * (0.7.3 and older). Stations made before 0.7.4 also keep their old name, and before 0.7.5 they had no
     * description (so no hover tooltip) and switched the music off for the encounter. All of it is put
     * right here, on new and old stations alike.
     */
    private static void fixUp(SectorEntityToken station) {
        if (!station.hasTag(Tags.HAS_INTERACTION_DIALOG)) station.addTag(Tags.HAS_INTERACTION_DIALOG);
        if (!NAME.equals(station.getName())) station.setName(NAME);
        if (!DESCRIPTION_ID.equals(station.getCustomDescriptionId())) station.setCustomDescriptionId(DESCRIPTION_ID);
        if (!station.getMemoryWithoutUpdate().getBoolean(KEEP_MUSIC)) station.getMemoryWithoutUpdate().set(KEEP_MUSIC, true);
    }
}
