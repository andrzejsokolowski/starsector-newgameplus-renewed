package ngprenewed.ships;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoTransferHandlerAPI;
import com.fs.starfarer.api.campaign.impl.items.BaseSpecialItemPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/** The four ship items in cargo. Right-click opens {@link ShipItemDialog} to pick the ship. */
public class ShipItemPlugin extends BaseSpecialItemPlugin {

    @Override
    public boolean hasRightClickAction() {
        return true;
    }

    /** The dialog removes the item itself, and only once it has been fitted. */
    @Override
    public boolean shouldRemoveOnRightClickAction() {
        return false;
    }

    @Override
    public void performRightClickAction(RightClickActionHelper helper) {
        if (Global.getSector() == null || Global.getSector().getPlayerFleet() == null) return;
        Global.getSector().getCampaignUI().showInteractionDialogFromCargo(
                new ShipItemDialog(getId(), helper), Global.getSector().getPlayerFleet(), () -> {
                });
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, CargoTransferHandlerAPI transferHandler,
                              Object stackSource) {
        super.createTooltip(tooltip, expanded, transferHandler, stackSource, true);
        tooltip.addPara("Right-click to fit it to a ship.", Misc.getPositiveHighlightColor(), 10f);
    }
}
