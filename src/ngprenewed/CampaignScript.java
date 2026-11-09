package ngprenewed;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.util.IntervalUtil;
import ngprenewed.ships.ShipItems;
import ngprenewed.store.Purchases;

/**
 * Runs once a second of real time: pays level points, places the Ledger when it is missing, and puts
 * back bought perks and ship work that something took away.
 * Runs while paused so levels gained inside a dialog are noticed. Added on every load, never saved.
 */
public final class CampaignScript implements EveryFrameScript {

    private final IntervalUtil interval = new IntervalUtil(1f, 1f);

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return true;
    }

    @Override
    public void advance(float amount) {
        interval.advance(amount);
        if (!interval.intervalElapsed()) return;
        SaveState state = SaveState.get();
        if (state == null || Global.getSector().getPlayerFleet() == null) return;
        try {
            LevelTracker.payPendingLevels(state);
            LedgerSpawner.ensure(state);
            Purchases.keepPerks(state);
            ShipItems.sync(state);
        } catch (Exception e) {
            Global.getLogger(CampaignScript.class).error("New Game Plus - Renewed: campaign check failed.", e);
        }
    }
}
