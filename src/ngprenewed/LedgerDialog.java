package ngprenewed;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CoreInteractionListener;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.util.Misc;
import ngprenewed.store.StorePanel;
import org.lwjgl.input.Keyboard;

import java.awt.Color;
import java.util.Map;

/** The Ledger's dialog: a short greeting, then the store, the delivery bay and the way out. */
public class LedgerDialog implements InteractionDialogPlugin {

    private enum Option { BROWSE, COLLECT, LEAVE }

    private final Map<String, MemoryAPI> memoryMap;
    private InteractionDialogAPI dialog;

    public LedgerDialog(Map<String, MemoryAPI> memoryMap) {
        this.memoryMap = memoryMap;
    }

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        SectorEntityToken target = dialog.getInteractionTarget();
        if (target != null && target.getCustomInteractionDialogImageVisual() != null) {
            dialog.getVisualPanel().showImageVisual(target.getCustomInteractionDialogImageVisual());
        }
        TextPanelAPI text = dialog.getTextPanel();
        text.addPara("The station answers before you hail it. A calm voice reads out your name, then a list "
                + "of other names, other ships, other lives. Every one of them is yours. Every one of them is "
                + "owed something.");
        showBudget();
        showOptions();
    }

    private void showBudget() {
        SaveState state = SaveState.get();
        if (state == null) return;
        Color h = Misc.getHighlightColor();
        dialog.getTextPanel().addPara("Points to spend: %s.", h, String.valueOf(state.budget()));
    }

    private void showOptions() {
        OptionPanelAPI options = dialog.getOptionPanel();
        options.clearOptions();
        options.addOption("Browse the catalogue", Option.BROWSE);
        SaveState state = SaveState.get();
        if (state != null && !state.bay().isEmpty()) {
            options.addOption("Collect deliveries", Option.COLLECT);
        }
        options.addOption("Leave", Option.LEAVE);
        options.setShortcut(Option.LEAVE, Keyboard.KEY_ESCAPE, false, false, false, true);
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (optionText != null) dialog.addOptionSelectedText(optionData);
        if (optionData == Option.BROWSE) {
            StorePanel store = new StorePanel(this);
            dialog.showCustomVisualDialog(store.width(), store.height(), store.delegate());
        } else if (optionData == Option.COLLECT) {
            collect();
        } else if (optionData == Option.LEAVE) {
            dialog.dismiss();
        }
    }

    /**
     * Called by the store when it closes. Goods bought go straight to a loot screen, opened on the
     * next frame so it never starts while the store panel is still closing.
     */
    public void storeClosed() {
        showBudget();
        SaveState state = SaveState.get();
        if (state != null && !state.bay().isEmpty()) {
            collectNextFrame = true;
        } else {
            showOptions();
        }
    }

    private boolean collectNextFrame;

    private void collect() {
        SaveState state = SaveState.get();
        if (state == null) return;
        CargoAPI bay = state.bay();
        dialog.getVisualPanel().showLoot("Deliveries", bay, false, false, false, new CoreInteractionListener() {
            @Override
            public void coreUIDismissed() {
                bay.sort();
                if (!bay.isEmpty()) {
                    dialog.getTextPanel().addPara("The rest stays in the bay until you come back for it.");
                }
                SectorEntityToken target = dialog.getInteractionTarget();
                if (target != null && target.getCustomInteractionDialogImageVisual() != null) {
                    dialog.getVisualPanel().showImageVisual(target.getCustomInteractionDialogImageVisual());
                }
                showOptions();
            }
        });
    }

    public InteractionDialogAPI getDialog() {
        return dialog;
    }

    @Override
    public void optionMousedOver(String optionText, Object optionData) {
    }

    @Override
    public void advance(float amount) {
        if (collectNextFrame) {
            collectNextFrame = false;
            collect();
        }
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
        return memoryMap;
    }
}
