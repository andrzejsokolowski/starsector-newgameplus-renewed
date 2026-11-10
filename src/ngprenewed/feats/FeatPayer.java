package ngprenewed.feats;

import achievementsrenewed.api.AchievementListener;
import achievementsrenewed.api.AchievementSpec;
import ngprenewed.SaveState;

/**
 * Pays an achievement's career points the moment it is done. Added on every game load, never saved.
 * Rewards.check catches anything this misses.
 */
public final class FeatPayer implements AchievementListener {

    @Override
    public void achievementDone(AchievementSpec spec) {
        Rewards.payNow(SaveState.get(), spec);
    }
}
