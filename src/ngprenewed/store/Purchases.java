package ngprenewed.store;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.plugins.LevelupPlugin;
import ngprenewed.Career;
import ngprenewed.LevelTracker;
import ngprenewed.SaveState;
import org.apache.log4j.Logger;

/** Checks and carries out one purchase. */
public final class Purchases {

    private static final Logger log = Global.getLogger(Purchases.class);

    private Purchases() {
    }

    /** Why an item cannot be bought right now, or null if it can. */
    public static String blocker(StoreItem item, SaveState state) {
        if (item.limited() && state.boughtCount(item.id) >= item.stock) return "Sold out";
        if (StoreCatalog.cost(item) > state.budget()) return "Not enough points";
        if ("levels".equals(item.kind)) {
            LevelupPlugin plugin = Global.getSettings().getLevelupPlugin();
            if (Global.getSector().getPlayerStats().getLevel() >= plugin.getMaxLevel()) return "Already at the top";
        }
        return null;
    }

    /** How many more one game can buy, or -1 for no limit. */
    public static int left(StoreItem item, SaveState state) {
        if (!item.limited()) return -1;
        return Math.max(0, item.stock - state.boughtCount(item.id));
    }

    /**
     * Buys the item: takes the points, records the purchase and hands over the goods. Story points,
     * skill points, levels and credits apply at once. Cargo goes to the delivery bay, which the
     * player empties through a loot screen.
     *
     * @return true if the purchase went through
     */
    public static boolean buy(StoreItem item, SaveState state) {
        if (blocker(item, state) != null) return false;
        int cost = StoreCatalog.cost(item);
        try {
            deliver(item, state);
        } catch (Exception e) {
            log.error("New Game Plus - Renewed: could not deliver " + item.id + "; no points were taken.", e);
            return false;
        }
        state.spend(cost);
        state.recordPurchase(item.id);
        return true;
    }

    private static void deliver(StoreItem item, SaveState state) {
        MutableCharacterStatsAPI stats = Global.getSector().getPlayerStats();
        CargoAPI bay = state.bay();
        switch (item.kind) {
            case "story_points":
                stats.addStoryPoints(item.amount);
                break;
            case "skill_points":
                stats.addPoints(item.amount);
                break;
            case "levels":
                addLevels(item.amount, state);
                break;
            case "credits":
                Global.getSector().getPlayerFleet().getCargo().getCredits().add(item.amount);
                break;
            case "commodity":
                bay.addCommodity(item.target, item.amount);
                bay.sort();
                break;
            case "special_item":
                bay.addSpecial(new SpecialItemData(item.target, null), item.amount);
                bay.sort();
                break;
            default:
                throw new IllegalStateException("unknown kind " + item.kind);
        }
    }

    /**
     * Raises the player by whole levels. Levels bought here never pay career points: any level
     * already earned is paid first, then the bought levels are marked as paid.
     */
    private static void addLevels(int levels, SaveState state) {
        LevelTracker.payPendingLevels(state);
        MutableCharacterStatsAPI stats = Global.getSector().getPlayerStats();
        LevelupPlugin plugin = Global.getSettings().getLevelupPlugin();
        for (int i = 0; i < levels; i++) {
            int level = stats.getLevel();
            if (level >= plugin.getMaxLevel()) break;
            // getXPForLevel is the total needed to reach that level.
            long needed = plugin.getXPForLevel(level + 1) - stats.getXP();
            if (needed > 0) stats.addXP(needed, null, false, false, true);
            stats.levelUpIfNeeded();
            if (stats.getLevel() == level) break; // a level cap from another mod; stop rather than loop
        }
        Career.setLevelsPaid(state.saveId(), stats.getLevel());
    }
}
