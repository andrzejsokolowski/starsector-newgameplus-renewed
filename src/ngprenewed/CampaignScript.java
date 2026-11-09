package ngprenewed;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.util.IntervalUtil;

/**
 * Runs once a second of real time: pays level points and places the Ledger when it is missing.
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
        } catch (Exception e) {
            Global.getLogger(CampaignScript.class).error("New Game Plus - Renewed: campaign check failed.", e);
        }
    }
}
