package ngprenewed.ships;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.DModManager;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.skills.FieldRepairsScript;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.loading.VariantSource;
import ngprenewed.SaveState;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The four ship items sold in the Shipyard tab, and what they do to a ship.
 *
 * <p>Extra S-mod slots and stat boosts are variant tags read by the permanent hull mod
 * {@link LedgerRefitHullMod}. Extra ordnance points are a second permanent hull mod whose OP cost is
 * negative. D-mod removal changes the ship at once and leaves nothing behind. Every change is also
 * written to the save ({@link SaveState#shipRecord}), and {@link #sync} puts back anything a refit or
 * another mod took off a ship in the player's fleet.</p>
 */
public final class ShipItems {

    public static final String SMOD_SLOT = "ngp_smod_slot";
    public static final String DMOD_REMOVAL = "ngp_dmod_removal";
    public static final String STAT_TUNING = "ngp_stat_tuning";
    public static final String EXTRA_OP = "ngp_extra_op";

    public static final String REFIT_MOD = "ngp_ledger_refit";
    public static final String OP_MOD = "ngp_ledger_rails";

    private static final String SMOD_TAG = "ngp_smod_";

    private static final Logger log = Global.getLogger(ShipItems.class);

    private ShipItems() {
    }

    public static boolean isShipItem(String itemId) {
        return SMOD_SLOT.equals(itemId) || DMOD_REMOVAL.equals(itemId) || STAT_TUNING.equals(itemId)
                || EXTRA_OP.equals(itemId);
    }

    // --- Reading a variant ------------------------------------------------------------------------

    public static int extraSModSlots(ShipVariantAPI variant) {
        int count = 0;
        for (String tag : variant.getTags()) {
            if (tag.startsWith(SMOD_TAG)) count++;
        }
        return count;
    }

    public static Set<Boost> boosts(ShipVariantAPI variant) {
        Set<Boost> result = EnumSet.noneOf(Boost.class);
        for (String tag : variant.getTags()) {
            Boost boost = Boost.fromTag(tag);
            if (boost != null) result.add(boost);
        }
        return result;
    }

    public static List<Boost> boostsLeft(FleetMemberAPI member) {
        Set<Boost> taken = boosts(member.getVariant());
        List<Boost> left = new ArrayList<>();
        for (Boost boost : Boost.values()) {
            if (!taken.contains(boost)) left.add(boost);
        }
        return left;
    }

    // --- Which ships can take an item ---------------------------------------------------------------

    public static boolean canTake(FleetMemberAPI member, String itemId) {
        if (member == null || member.isFighterWing() || member.getVariant() == null) return false;
        ShipVariantAPI variant = member.getVariant();
        switch (itemId) {
            case SMOD_SLOT:
                return true;
            case DMOD_REMOVAL:
                return hasRemovableDMods(variant);
            case STAT_TUNING:
                return !boostsLeft(member).isEmpty();
            case EXTRA_OP:
                return !variant.hasHullMod(OP_MOD);
            default:
                return false;
        }
    }

    public static List<FleetMemberAPI> eligible(String itemId) {
        List<FleetMemberAPI> result = new ArrayList<>();
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null) return result;
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            if (canTake(member, itemId)) result.add(member);
        }
        return result;
    }

    private static boolean hasRemovableDMods(ShipVariantAPI variant) {
        if (variant == null) return false;
        if (!removableDMods(variant).isEmpty()) return true;
        ShipHullSpecAPI base = FieldRepairsScript.getBaseNonDHullFor(variant);
        if (base != null && base != variant.getHullSpec() && variant.getHullSpec().isDHull()) return true;
        for (String slot : variant.getModuleSlots()) {
            if (hasRemovableDMods(variant.getModuleVariant(slot))) return true;
        }
        return false;
    }

    /** D-mods added to the ship, not the ones its hull was designed with. */
    private static List<String> removableDMods(ShipVariantAPI variant) {
        List<String> result = new ArrayList<>();
        for (String id : variant.getHullMods()) {
            HullModSpecAPI spec = DModManager.getMod(id);
            if (spec == null || !spec.hasTag(Tags.HULLMOD_DMOD)) continue;
            if (variant.getHullSpec().getBuiltInMods().contains(id)) continue;
            result.add(id);
        }
        return result;
    }

    // --- Changing a ship ----------------------------------------------------------------------------

    /**
     * Fits the item to the ship. For Stat tuning, {@code boost} names the stat.
     *
     * @return true if the ship changed
     */
    public static boolean apply(FleetMemberAPI member, String itemId, Boost boost) {
        if (!canTake(member, itemId)) return false;
        SaveState state = SaveState.get();
        ShipVariantAPI variant = ownVariant(member);
        Map<String, Object> record = state == null ? null : state.shipRecord(member.getId());
        switch (itemId) {
            case SMOD_SLOT: {
                int slots = extraSModSlots(variant) + 1;
                variant.addTag(SMOD_TAG + slots);
                variant.addPermaMod(REFIT_MOD, false);
                if (record != null) record.put("smodSlots", slots);
                break;
            }
            case DMOD_REMOVAL:
                restore(member, variant);
                break;
            case STAT_TUNING: {
                if (boost == null || boosts(variant).contains(boost)) return false;
                variant.addTag(boost.tag());
                variant.addPermaMod(REFIT_MOD, false);
                if (record != null) {
                    List<String> names = boostNames(record);
                    if (!names.contains(boost.name())) names.add(boost.name());
                }
                break;
            }
            case EXTRA_OP:
                variant.addPermaMod(OP_MOD, false);
                if (record != null) record.put("extraOp", Boolean.TRUE);
                break;
            default:
                return false;
        }
        member.updateStats();
        return true;
    }

    private static void restore(FleetMemberAPI member, ShipVariantAPI variant) {
        restoreVariant(variant);
        for (String slot : new ArrayList<>(variant.getModuleSlots())) {
            ShipVariantAPI module = variant.getModuleVariant(slot);
            if (module == null || !hasRemovableDMods(module)) continue;
            if (module.isStockVariant() || module.isGoalVariant() || module.getSource() != VariantSource.REFIT) {
                module = module.clone();
                module.setSource(VariantSource.REFIT);
                variant.setModuleVariant(slot, module);
            }
            restoreVariant(module);
        }
    }

    /** Takes off every added D-mod and gives the ship back its original hull, as a full restoration does. */
    private static void restoreVariant(ShipVariantAPI variant) {
        for (String id : removableDMods(variant)) DModManager.removeDMod(variant, id);
        FieldRepairsScript.restoreToNonDHull(variant);
    }

    /** A variant of this ship's own, so a change never touches the shared stock design. */
    private static ShipVariantAPI ownVariant(FleetMemberAPI member) {
        ShipVariantAPI variant = member.getVariant();
        if (variant.isStockVariant() || variant.isGoalVariant() || variant.getSource() != VariantSource.REFIT) {
            variant = variant.clone();
            variant.setSource(VariantSource.REFIT);
            member.setVariant(variant, false, true);
        }
        return variant;
    }

    @SuppressWarnings("unchecked")
    private static List<String> boostNames(Map<String, Object> record) {
        Object value = record.get("boosts");
        if (value instanceof List) return (List<String>) value;
        List<String> fresh = new ArrayList<>();
        record.put("boosts", fresh);
        return fresh;
    }

    // --- Keeping ships as recorded ------------------------------------------------------------------

    /** Puts back Ledger work a ship in the player's fleet has lost, for example to an autofit. */
    public static void sync(SaveState state) {
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null || !state.hasShipRecords()) return;
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            Map<String, Object> record = state.existingShipRecord(member.getId());
            if (record == null || member.getVariant() == null) continue;
            if (needsRepair(member.getVariant(), record)) {
                repair(ownVariant(member), record);
                member.updateStats();
                log.info("New Game Plus - Renewed: put back the Ledger's work on " + member.getShipName());
            }
        }
    }

    private static int recordedSlots(Map<String, Object> record) {
        Object value = record.get("smodSlots");
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private static boolean needsRepair(ShipVariantAPI variant, Map<String, Object> record) {
        int slots = recordedSlots(record);
        List<String> names = boostNames(record);
        boolean refit = slots > 0 || !names.isEmpty();
        if (refit && !variant.getPermaMods().contains(REFIT_MOD)) return true;
        if (extraSModSlots(variant) < slots) return true;
        Set<Boost> have = boosts(variant);
        for (String name : names) {
            Boost boost = boostByName(name);
            if (boost != null && !have.contains(boost)) return true;
        }
        return Boolean.TRUE.equals(record.get("extraOp")) && !variant.getPermaMods().contains(OP_MOD);
    }

    private static void repair(ShipVariantAPI variant, Map<String, Object> record) {
        int slots = recordedSlots(record);
        for (int i = 1; i <= slots; i++) variant.addTag(SMOD_TAG + i);
        for (String name : boostNames(record)) {
            Boost boost = boostByName(name);
            if (boost != null) variant.addTag(boost.tag());
        }
        if (slots > 0 || !boostNames(record).isEmpty()) variant.addPermaMod(REFIT_MOD, false);
        if (Boolean.TRUE.equals(record.get("extraOp"))) variant.addPermaMod(OP_MOD, false);
    }

    private static Boost boostByName(String name) {
        try {
            return Boost.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
