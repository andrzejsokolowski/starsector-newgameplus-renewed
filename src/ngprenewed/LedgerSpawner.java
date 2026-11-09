package ngprenewed;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

/**
 * Puts the Ledger station next to the player the first time the campaign is ready: a new game, or
 * the first load of an older save. Waits until no dialog is open, so start screens from other mods
 * (Nexerelin's starting options, for example) have placed the fleet first.
 */
public final class LedgerSpawner {

    public static final String ENTITY_TYPE = "ngp_ledger_station";

    private LedgerSpawner() {
    }

    public static void ensure(SaveState state) {
        SectorEntityToken existing = state.ledger();
        if (existing != null && existing.isAlive()) return;

        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        if (ui == null || ui.isShowingDialog() || ui.isShowingMenu()) return;
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null || fleet.getContainingLocation() == null) return;

        LocationAPI where = fleet.getContainingLocation();
        SectorEntityToken ledger = where.addCustomEntity("ngp_ledger_" + Misc.genUID(), null, ENTITY_TYPE, Factions.NEUTRAL);
        ledger.setDiscoverable(false);

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
        ui.addMessage("The Ledger has opened its doors near your fleet.", Misc.getHighlightColor());
    }
}
