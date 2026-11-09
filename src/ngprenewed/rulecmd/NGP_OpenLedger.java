package ngprenewed.rulecmd;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import ngprenewed.LedgerDialog;

import java.util.List;
import java.util.Map;

/** rules.csv: hands the Ledger's interaction dialog over to {@link LedgerDialog}. */
public class NGP_OpenLedger extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params,
                           Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;
        LedgerDialog plugin = new LedgerDialog(memoryMap);
        dialog.setPlugin(plugin);
        plugin.init(dialog);
        return true;
    }
}
