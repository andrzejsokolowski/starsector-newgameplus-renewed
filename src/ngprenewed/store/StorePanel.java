package ngprenewed.store;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomVisualDialogDelegate;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.util.Misc;
import ngprenewed.Career;
import ngprenewed.LedgerDialog;
import ngprenewed.SaveState;
import org.lwjgl.input.Keyboard;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * The store screen: tabs along the top, a list of items that scrolls with the mouse wheel, and a buy
 * button per item that asks for a second click to confirm.
 *
 * <p>Every button lives in one tooltip element that covers the whole panel and sits at its top left
 * corner. Buttons in an element placed at an offset draw correctly but never receive clicks (found in
 * Gambling Den 0.1.0). Text for each row is a separate, button-free element placed over its row.
 * The whole screen is rebuilt after every change, which keeps the layout code simple.</p>
 */
public class StorePanel implements CustomVisualDialogDelegate, CustomUIPanelPlugin {

    public static final float WIDTH = 1000f;
    public static final float HEIGHT = 660f;

    private static final float PAD = 20f;
    private static final float TAB_Y = 58f;
    private static final float TAB_H = 28f;
    private static final float LIST_Y = 100f;
    private static final float ROW_H = 72f;
    private static final float FOOT_H = 56f;
    private static final float BUY_W = 150f;
    private static final float BUY_H = 30f;
    private static final float ICON = 52f;

    private static final String LEAVE = "ngp_leave";
    private static final String SCROLL_UP = "ngp_up";
    private static final String SCROLL_DOWN = "ngp_down";

    private final LedgerDialog owner;
    private CustomPanelAPI panel;
    private DialogCallbacks callbacks;
    private PositionAPI position;

    private final List<UIComponentAPI> shown = new ArrayList<>();
    private String tab;
    private int scroll;
    private String pendingItem;
    private String lastPressed;
    private long lastPressedAt;
    private String message = "";
    private Color messageColor = Misc.getTextColor();

    public StorePanel(LedgerDialog owner) {
        this.owner = owner;
        List<String> tabs = StoreCatalog.tabs();
        tab = tabs.isEmpty() ? "" : tabs.get(0);
    }

    // --- CustomVisualDialogDelegate ------------------------------------------------------------

    @Override
    public void init(CustomPanelAPI panel, DialogCallbacks callbacks) {
        this.panel = panel;
        this.callbacks = callbacks;
        rebuild();
    }

    @Override
    public CustomUIPanelPlugin getCustomPanelPlugin() {
        return this;
    }

    @Override
    public float getNoiseAlpha() {
        return 0f;
    }

    @Override
    public void advance(float amount) {
    }

    @Override
    public void reportDismissed(int option) {
        owner.storeClosed();
    }

    // --- Layout ----------------------------------------------------------------------------------

    private int visibleRows() {
        return (int) Math.floor((HEIGHT - LIST_Y - FOOT_H) / ROW_H);
    }

    private void rebuild() {
        for (UIComponentAPI component : shown) panel.removeComponent(component);
        shown.clear();

        SaveState state = SaveState.get();
        if (state == null) return;
        List<StoreItem> items = StoreCatalog.itemsIn(tab);
        int maxScroll = Math.max(0, items.size() - visibleRows());
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        // One element over the whole panel holds every button and the header text.
        TooltipMakerAPI holder = panel.createUIElement(WIDTH, HEIGHT, false);
        holder.setActionListenerDelegate((inputEvent, data) -> buttonPressed(data));

        holder.setParaInsigniaLarge();
        LabelAPI title = holder.addPara("The Ledger", Misc.getBasePlayerColor(), 0f);
        title.getPosition().inTL(PAD, 16f);
        holder.setParaFontDefault();

        LabelAPI budget = holder.addPara("Points to spend: %s of %s     Career points: %s", 0f,
                Misc.getGrayColor(), Misc.getHighlightColor(),
                String.valueOf(state.budget()), String.valueOf(state.startingBudget()), String.valueOf(Career.total()));
        budget.setAlignment(Alignment.RMID);
        budget.getPosition().setSize(WIDTH - 2 * PAD - 200f, 20f);
        budget.getPosition().inTL(PAD + 200f, 22f);

        float tabX = PAD;
        float tabW = 150f;
        for (String name : StoreCatalog.tabs()) {
            boolean current = name.equals(tab);
            ButtonAPI tabButton = holder.addButton(name, "tab:" + name,
                    current ? Misc.getBrightPlayerColor() : Misc.getBasePlayerColor(),
                    current ? Misc.getDarkPlayerColor().brighter() : Misc.getDarkPlayerColor(),
                    Alignment.MID, CutStyle.TOP, tabW, TAB_H, 0f);
            tabButton.getPosition().inTL(tabX, TAB_Y);
            if (current) tabButton.highlight();
            tabX += tabW + 6f;
        }

        int end = Math.min(items.size(), scroll + visibleRows());
        for (int i = scroll; i < end; i++) {
            StoreItem item = items.get(i);
            float y = LIST_Y + (i - scroll) * ROW_H;
            addRowButton(holder, item, state, y);
        }

        if (scroll > 0) {
            ButtonAPI up = holder.addButton("Up", SCROLL_UP, 70f, 24f, 0f);
            up.getPosition().inTL(WIDTH - PAD - 150f, HEIGHT - FOOT_H + 16f);
        }
        if (scroll < maxScroll) {
            ButtonAPI down = holder.addButton("Down", SCROLL_DOWN, 70f, 24f, 0f);
            down.getPosition().inTL(WIDTH - PAD - 70f, HEIGHT - FOOT_H + 16f);
        }
        if (items.size() > visibleRows()) {
            LabelAPI range = holder.addPara((scroll + 1) + "-" + end + " of " + items.size(), Misc.getGrayColor(), 0f);
            range.setAlignment(Alignment.RMID);
            range.getPosition().setSize(150f, 20f);
            range.getPosition().inTL(WIDTH - PAD - 320f, HEIGHT - FOOT_H + 18f);
        }

        // Escape is handled in processInput, not as a button shortcut, so it can never fire twice.
        ButtonAPI leave = holder.addButton("Leave", LEAVE, 140f, 30f, 0f);
        leave.getPosition().inTL(PAD, HEIGHT - FOOT_H + 13f);

        LabelAPI note = holder.addPara(message, messageColor, 0f);
        note.getPosition().setSize(WIDTH - 2 * PAD - 500f, 20f);
        note.getPosition().inTL(PAD + 160f, HEIGHT - FOOT_H + 18f);

        panel.addUIElement(holder).inTL(0f, 0f);
        shown.add(holder);

        // Row text goes on top as button-free elements, one per row.
        for (int i = scroll; i < end; i++) {
            StoreItem item = items.get(i);
            float y = LIST_Y + (i - scroll) * ROW_H;
            TooltipMakerAPI row = panel.createUIElement(WIDTH - 2 * PAD - BUY_W - 140f, ROW_H - 6f, false);
            TooltipMakerAPI text = row.beginImageWithText(item.icon, ICON);
            text.addPara(item.name, Misc.getHighlightColor(), 0f);
            if (!item.description.isEmpty()) text.addPara(item.description, Misc.getGrayColor(), 3f);
            row.addImageWithText(0f);
            panel.addUIElement(row).inTL(PAD, y + 6f);
            shown.add(row);
        }

        if (items.isEmpty()) {
            TooltipMakerAPI empty = panel.createUIElement(WIDTH - 2 * PAD, 40f, false);
            empty.addPara("Nothing on offer here.", Misc.getGrayColor(), 0f);
            panel.addUIElement(empty).inTL(PAD, LIST_Y + 10f);
            shown.add(empty);
        }
    }

    private void addRowButton(TooltipMakerAPI holder, StoreItem item, SaveState state, float y) {
        int cost = StoreCatalog.cost(item);
        int left = Purchases.left(item, state);

        LabelAPI price = holder.addPara(cost + " pts", Misc.getHighlightColor(), 0f);
        price.setAlignment(Alignment.RMID);
        price.getPosition().setSize(110f, 20f);
        price.getPosition().inTL(WIDTH - PAD - BUY_W - 125f, y + 14f);
        if (left >= 0) {
            LabelAPI stock = holder.addPara(left + " left", Misc.getGrayColor(), 0f);
            stock.setAlignment(Alignment.RMID);
            stock.getPosition().setSize(110f, 20f);
            stock.getPosition().inTL(WIDTH - PAD - BUY_W - 125f, y + 36f);
        }

        String blocker = Purchases.blocker(item, state);
        boolean confirming = item.id.equals(pendingItem);
        String label = blocker != null ? blocker : (confirming ? "Confirm" : "Buy");
        ButtonAPI buy = holder.addButton(label, "buy:" + item.id,
                confirming ? Misc.getStoryBrightColor() : Misc.getButtonTextColor(),
                confirming ? Misc.getStoryDarkColor() : Misc.getDarkPlayerColor(),
                Alignment.MID, CutStyle.ALL, BUY_W, BUY_H, 0f);
        buy.getPosition().inTL(WIDTH - PAD - BUY_W, y + (ROW_H - BUY_H) / 2f);
        if (blocker != null) buy.setEnabled(false);
    }

    // --- Input -----------------------------------------------------------------------------------

    /**
     * Both the panel plugin and the tooltip's listener report button clicks, and which of them fires
     * is not certain, so both are wired up. A click reported twice within a moment is handled once:
     * otherwise a single click on Buy would also count as the confirming second click.
     */
    @Override
    public void buttonPressed(Object data) {
        if (!(data instanceof String)) return;
        String id = (String) data;
        long now = System.nanoTime();
        if (id.equals(lastPressed) && now - lastPressedAt < 150_000_000L) return;
        lastPressed = id;
        lastPressedAt = now;
        if (LEAVE.equals(id)) {
            callbacks.dismissDialog();
            return;
        }
        if (SCROLL_UP.equals(id)) {
            scroll -= visibleRows();
            pendingItem = null;
        } else if (SCROLL_DOWN.equals(id)) {
            scroll += visibleRows();
            pendingItem = null;
        } else if (id.startsWith("tab:")) {
            tab = id.substring(4);
            scroll = 0;
            pendingItem = null;
        } else if (id.startsWith("buy:")) {
            clickBuy(id.substring(4));
        }
        rebuild();
    }

    private void clickBuy(String itemId) {
        if (!itemId.equals(pendingItem)) {
            pendingItem = itemId;
            return;
        }
        pendingItem = null;
        SaveState state = SaveState.get();
        StoreItem item = find(itemId);
        if (state == null || item == null) return;
        if (Purchases.buy(item, state)) {
            message = goodsGoToBay(item) ? item.name + ": waiting in the delivery bay." : item.name + ": done.";
            messageColor = Misc.getPositiveHighlightColor();
            Global.getSoundPlayer().playUISound("ui_cargo_special_tech_drop", 1f, 1f);
        } else {
            message = "The Ledger declines.";
            messageColor = Misc.getNegativeHighlightColor();
        }
    }

    private static boolean goodsGoToBay(StoreItem item) {
        return "commodity".equals(item.kind) || "special_item".equals(item.kind);
    }

    private StoreItem find(String itemId) {
        for (String name : StoreCatalog.tabs()) {
            for (StoreItem item : StoreCatalog.itemsIn(name)) {
                if (item.id.equals(itemId)) return item;
            }
        }
        return null;
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
        for (InputEventAPI event : events) {
            if (event.isConsumed()) continue;
            if (event.isKeyDownEvent() && event.getEventValue() == Keyboard.KEY_ESCAPE) {
                event.consume();
                callbacks.dismissDialog();
                return;
            }
            if (event.isMouseScrollEvent() && inside(event.getX(), event.getY())) {
                int before = scroll;
                scroll += event.getEventValue() > 0 ? -1 : 1;
                event.consume();
                List<StoreItem> items = StoreCatalog.itemsIn(tab);
                int maxScroll = Math.max(0, items.size() - visibleRows());
                scroll = Math.max(0, Math.min(scroll, maxScroll));
                if (scroll != before) {
                    pendingItem = null;
                    rebuild();
                }
            }
        }
    }

    private boolean inside(float x, float y) {
        if (position == null) return true;
        return x >= position.getX() && x <= position.getX() + position.getWidth()
                && y >= position.getY() && y <= position.getY() + position.getHeight();
    }

    @Override
    public void positionChanged(PositionAPI position) {
        this.position = position;
    }

    @Override
    public void renderBelow(float alphaMult) {
    }

    @Override
    public void render(float alphaMult) {
    }
}
