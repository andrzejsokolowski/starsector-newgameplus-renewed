package ngprenewed.ships;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FleetMemberPickerListener;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.SpecialItemPlugin.RightClickActionHelper;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;
import org.lwjgl.input.Keyboard;

import java.util.List;
import java.util.Map;

/**
 * Opened from the cargo screen by right-clicking a ship item: pick a ship, pick a stat for Stat
 * tuning, and the item is used up. Closing the dialog at any point before that keeps the item.
 */
public class ShipItemDialog implements InteractionDialogPlugin {

    private static final Logger log = Global.getLogger(ShipItemDialog.class);

    private static final String CHOOSE = "ngp_choose";
    private static final String LEAVE = "ngp_leave";
    private static final String BOOST = "boost:";

    private final String itemId;
    private final RightClickActionHelper helper;
    private InteractionDialogAPI dialog;
    private FleetMemberAPI picked;
    private boolean used;

    public ShipItemDialog(String itemId, RightClickActionHelper helper) {
        this.itemId = itemId;
        this.helper = helper;
    }

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        dialog.getTextPanel().addPara(opening());
        showStart();
    }

    private String opening() {
        switch (itemId) {
            case ShipItems.SMOD_SLOT:
                return "A dockyard charter stamped with Legacy Station's seal. Any yard that reads it will find room for one more refit it should not be able to fit.";
            case ShipItems.DMOD_REMOVAL:
                return "A crate of parts machined to tolerances nobody has used since the Collapse. The manifest names a ship, but leaves the hull number blank.";
            case ShipItems.STAT_TUNING:
                return "A tuning kit and a note in a hand you almost recognise: one change, and make it count.";
            case ShipItems.EXTRA_OP:
                return "Mount rails and power couplings cut for a hull that does not exist yet. They will fit whichever one you choose.";
            default:
                return "Legacy Station's work, waiting for a ship.";
        }
    }

    private void showStart() {
        OptionPanelAPI options = dialog.getOptionPanel();
        options.clearOptions();
        if (ShipItems.eligible(itemId).isEmpty()) {
            dialog.getTextPanel().addPara("No ship in your fleet can take it.", Misc.getGrayColor());
        } else {
            options.addOption("Choose a ship", CHOOSE);
        }
        options.addOption("Put it away", LEAVE);
        options.setShortcut(LEAVE, Keyboard.KEY_ESCAPE, false, false, false, true);
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (!(optionData instanceof String)) return;
        String option = (String) optionData;
        if (optionText != null) dialog.addOptionSelectedText(optionData);
        if (LEAVE.equals(option)) {
            dialog.dismiss();
        } else if (CHOOSE.equals(option)) {
            pickShip();
        } else if (option.startsWith(BOOST) && picked != null && !used) {
            try {
                fit(Boost.valueOf(option.substring(BOOST.length())));
            } catch (IllegalArgumentException e) {
                showStart();
            }
        }
    }

    private void pickShip() {
        List<FleetMemberAPI> pool = ShipItems.eligible(itemId);
        if (pool.isEmpty()) {
            showStart();
            return;
        }
        int columns = 7;
        int rows = Math.max(1, Math.min(4, (pool.size() + columns - 1) / columns));
        dialog.showFleetMemberPickerDialog("Fit it to which ship?", "Fit", "Cancel", rows, columns, 58f,
                true, false, pool, new FleetMemberPickerListener() {
                    @Override
                    public void pickedFleetMembers(List<FleetMemberAPI> members) {
                        if (members == null || members.isEmpty()) return;
                        picked = members.get(0);
                        if (ShipItems.STAT_TUNING.equals(itemId)) {
                            showBoosts();
                        } else {
                            fit(null);
                        }
                    }

                    @Override
                    public void cancelledFleetMemberPicking() {
                    }
                });
    }

    private void showBoosts() {
        OptionPanelAPI options = dialog.getOptionPanel();
        options.clearOptions();
        dialog.getTextPanel().addPara("Which part of the " + picked.getShipName() + " gets the work?");
        for (Boost boost : ShipItems.boostsLeft(picked)) {
            options.addOption(boost.label + " " + boost.amount, BOOST + boost.name());
        }
        options.addOption("Choose a different ship", CHOOSE);
        options.addOption("Put it away", LEAVE);
        options.setShortcut(LEAVE, Keyboard.KEY_ESCAPE, false, false, false, true);
    }

    private void fit(Boost boost) {
        if (used || picked == null) return;
        TextPanelAPI text = dialog.getTextPanel();
        boolean done;
        try {
            done = ShipItems.apply(picked, itemId, boost);
        } catch (Exception e) {
            log.error("New Game Plus - Renewed: could not fit " + itemId + " to " + picked.getShipName(), e);
            done = false;
        }
        if (!done) {
            text.addPara("The " + picked.getShipName() + " cannot take it.", Misc.getNegativeHighlightColor());
            showStart();
            return;
        }
        used = true;
        consumeItem();
        Global.getSoundPlayer().playUISound("ui_cargo_special_tech_drop", 1f, 1f);
        text.addPara("By the next shift change the work is done, and the yard crew cannot remember doing it.");
        text.addPara(picked.getShipName() + ": " + result(boost), Misc.getPositiveHighlightColor());
        OptionPanelAPI options = dialog.getOptionPanel();
        options.clearOptions();
        options.addOption("Leave", LEAVE);
        options.setShortcut(LEAVE, Keyboard.KEY_ESCAPE, false, false, false, true);
    }

    private String result(Boost boost) {
        switch (itemId) {
            case ShipItems.SMOD_SLOT:
                return "+1 S-mod slot.";
            case ShipItems.DMOD_REMOVAL:
                return "every D-mod removed.";
            case ShipItems.STAT_TUNING:
                return boost == null ? "tuned." : boost.label + " " + boost.amount + ".";
            case ShipItems.EXTRA_OP:
                return "extra ordnance points.";
            default:
                return "done.";
        }
    }

    /** Takes one item from the stack that was right-clicked, or from the fleet's cargo. */
    private void consumeItem() {
        if (helper != null) {
            try {
                helper.removeFromClickedStackFirst(1);
                return;
            } catch (Exception e) {
                log.warn("New Game Plus - Renewed: could not take the item from the clicked stack; taking it from cargo.", e);
            }
        }
        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
        cargo.removeItems(CargoAPI.CargoItemType.SPECIAL, new SpecialItemData(itemId, null), 1);
    }

    @Override
    public void optionMousedOver(String optionText, Object optionData) {
    }

    @Override
    public void advance(float amount) {
    }

    @Override
    public void backFromEngagement(EngagementResultAPI battleResult) {
    }

    @Override
    public Object getContext() {
        return null;
    }

    @Override
    public Map<String, MemoryAPI> getMemoryMap() {
        return null;
    }
}
