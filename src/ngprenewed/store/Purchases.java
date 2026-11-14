package ngprenewed.store;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.plugins.LevelupPlugin;
import ngprenewed.Career;
import ngprenewed.LevelTracker;
import ngprenewed.SaveState;
import ngprenewed.compat.PeerlessBridge;
import ngprenewed.compat.PeerlessRenewedBridge;
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
        if ("perk".equals(item.kind) && Global.getSector().getPlayerStats().getSkillLevel(item.target) > 0) {
            return "Already yours";
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
            case "weapon_pack":
                for (String weaponId : StoreCatalog.packWeapons(item.target)) bay.addWeapons(weaponId, item.amount);
                bay.sort();
                break;
            case "perk":
                grantPerk(item.target);
                break;
            case "peerless_point":
                // Peerless Skills - Renewed (0.9.2) when it is on, else the original Peerless Skills.
                if (Global.getSettings().getModManager().isModEnabled(PeerlessRenewedBridge.MOD_ID)) {
                    PeerlessRenewedBridge.addPoint();
                    break;
                }
                // A new key per purchase, so each one pays; Peerless Skills refuses a key it has seen.
                String key = "$ngp_renewed_" + state.saveId() + "_" + item.id + "_" + (state.boughtCount(item.id) + 1);
                if (!PeerlessBridge.awardPoint(key)) throw new IllegalStateException("Peerless Skills refused " + key);
                break;
            default:
                throw new IllegalStateException("unknown kind " + item.kind);
        }
    }

    /** A perk is a hidden skill on the player, so it lasts for this game and applies to every ship. */
    static void grantPerk(String skillId) {
        Global.getSector().getPlayerStats().setSkillLevel(skillId, 1f);
        Global.getSector().getPlayerStats().refreshCharacterStatsEffects();
        Global.getSector().getPlayerFleet().getFleetData().setSyncNeeded();
        Global.getSector().getPlayerFleet().getFleetData().syncIfNeeded();
    }

    /** Gives back any perk this game bought that the player no longer has, for example after a skill reset. */
    public static void keepPerks(SaveState state) {
        for (String tab : StoreCatalog.tabs()) {
            for (StoreItem item : StoreCatalog.itemsIn(tab)) {
                if (!"perk".equals(item.kind) || state.boughtCount(item.id) <= 0) continue;
                if (Global.getSector().getPlayerStats().getSkillLevel(item.target) > 0) continue;
                grantPerk(item.target);
                log.info("New Game Plus - Renewed: gave back the perk " + item.id);
            }
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
