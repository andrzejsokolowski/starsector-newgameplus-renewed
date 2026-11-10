package ngprenewed.feats;

import achievementsrenewed.api.AchievementListener;
import achievementsrenewed.api.AchievementSpec;
import ngprenewed.SaveState;

/**
 * Pays a feat's career points the moment its achievement is done. Added on every game load, never
 * saved. Feats.check catches anything this misses.
 */
public final class FeatPayer implements AchievementListener {

    @Override
    public void achievementDone(AchievementSpec spec) {
        Feat feat = Feats.forAchievement(spec.id);
        if (feat != null) Feats.pay(SaveState.get(), feat);
    }
}
