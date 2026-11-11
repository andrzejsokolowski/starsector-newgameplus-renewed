package ngprenewed.store;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomVisualDialogDelegate;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import ngprenewed.Career;
import ngprenewed.LedgerDialog;
import ngprenewed.LedgerSpawner;
import ngprenewed.SaveState;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The store screen (0.7.5 rebuild, after Andrzej's "the shop sucks"): tabs along the top, the chosen tab's
 * items in a list that scrolls smoothly with the mouse wheel, and a buy button per item that asks for a
 * second click to confirm. It takes most of the screen.
 *
 * <p>Built the way Achievements - Renewed's screen is: every tab's list is a container panel made once,
 * holding a scrolling TooltipMakerAPI with a content panel of labels. Hidden lists are moved off screen (a
 * scrolling TooltipMakerAPI cannot be taken out of a panel again). Backgrounds, icons, tabs and buttons are
 * drawn and hit-tested by this panel from the mouse state, reading back each frame where the scrolled
 * content really sits. No ButtonAPI is used: in 0.7.4 the game drew the tab buttons but never reported
 * their clicks.</p>
 */
public final class StorePanel extends BaseCustomUIPanelPlugin {

    private static final float PAD = 20f, TITLE_H = 34f, TAB_H = 32f, TAB_GAP = 6f, TAB_W_MAX = 200f;
    private static final float FOOT_H = 38f, LEAVE_W = 150f, SCROLLBAR_W = 22f, HIDDEN_X = -20000f;
    private static final float ROW_H = 84f, ROW_GAP = 6f, ROW_X = 12f, ICON = 60f, TEXT_GAP = 16f;
    private static final float BUY_W = 180f, BUY_H = 36f, PRICE_W = 120f;

    private static final Color BG = new Color(10, 13, 19);
    private static final Color PANEL = new Color(20, 25, 34);
    private static final Color ROW_FILL = new Color(24, 30, 40);
    private static final Color BUTTON = new Color(32, 40, 50);
    private static final Color BUTTON_HOVER = new Color(60, 67, 78);
    private static final Color BUTTON_ON = new Color(98, 76, 38);
    private static final Color BUTTON_OFF = new Color(26, 29, 34);
    private static final Color CONFIRM = new Color(70, 110, 60);
    private static final Color GOLD = new Color(235, 185, 100);
    private static final Color GREEN = new Color(120, 210, 130);
    private static final Color RED = new Color(230, 110, 100);
    private static final Color DIM = new Color(150, 157, 173);
    private static final Color FAINT = new Color(105, 110, 121);
    private static final Color TEXT = new Color(220, 224, 232);

    private static final class Row {
        final StoreItem item;
        final float y;
        LabelAPI price, stock, button;
        String blocker;
        boolean hover;

        Row(StoreItem item, float y) {
            this.item = item;
            this.y = y;
        }
    }

    private static final class Tab {
        final String name;
        final float x, w;
        final List<Row> rows = new ArrayList<>();
        CustomPanelAPI container, content;
        float contentLeft, contentTop;
        boolean hover;

        Tab(String name, float x, float w) {
            this.name = name;
            this.x = x;
            this.w = w;
        }
    }

    private final LedgerDialog owner;
    private final float panelW, panelH, tabsTop, listTop, listW, listH, footTop;
    private final List<Tab> tabs = new ArrayList<>();
    private final Map<String, SpriteAPI> sprites = new HashMap<>();
    private CustomPanelAPI panel;
    private CustomVisualDialogDelegate.DialogCallbacks callbacks;
    private PositionAPI position;
    private LabelAPI budgetLabel, messageLabel;
    private int tab;
    private String pendingItem;
    private boolean leaveHover, dismissed, wasLeftDown;

    public StorePanel(LedgerDialog owner) {
        this.owner = owner;
        float screenW = Global.getSettings().getScreenWidth();
        float screenH = Global.getSettings().getScreenHeight();
        panelW = Math.max(Math.min(1500f, screenW * 0.86f), Math.min(900f, screenW - 10f));
        panelH = Math.max(Math.min(1000f, screenH * 0.88f), Math.min(600f, screenH - 10f));
        tabsTop = PAD + TITLE_H + 8f;
        listTop = tabsTop + TAB_H + 10f;
        footTop = panelH - PAD - FOOT_H;
        listW = panelW - PAD * 2f;
        listH = footTop - 10f - listTop;
    }

    public float width() {
        return panelW;
    }

    public float height() {
        return panelH;
    }

    /** What InteractionDialogAPI.showCustomVisualDialog needs. A class of its own, so advance runs once. */
    public CustomVisualDialogDelegate delegate() {
        final StorePanel store = this;
        return new CustomVisualDialogDelegate() {
            @Override
            public void init(CustomPanelAPI panel, DialogCallbacks callbacks) {
                store.init(panel, callbacks);
            }

            @Override
            public CustomUIPanelPlugin getCustomPanelPlugin() {
                return store;
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
        };
    }

    // ---- building ----

    private void init(CustomPanelAPI panel, CustomVisualDialogDelegate.DialogCallbacks callbacks) {
        this.panel = panel;
        this.callbacks = callbacks;
        // The click that chose "Browse the catalogue" may still be held down.
        wasLeftDown = Mouse.isCreated() && Mouse.isButtonDown(0);

        label(panel, LedgerSpawner.NAME, GOLD, PAD, PAD + 2f, 420f, 26f, Fonts.ORBITRON_20AA, Alignment.LMID);
        budgetLabel = label(panel, "", TEXT, panelW - PAD - 640f, PAD + 6f, 640f, 20f, Fonts.DEFAULT_SMALL, Alignment.RMID);

        List<String> names = StoreCatalog.tabs();
        int n = Math.max(1, names.size());
        float tabW = Math.min(TAB_W_MAX, (listW - TAB_GAP * (n - 1)) / n);
        float x = PAD;
        for (String name : names) {
            Tab t = new Tab(name, x, tabW);
            label(panel, name, TEXT, x, tabsTop + (TAB_H - 18f) / 2f, tabW, 18f, Fonts.DEFAULT_SMALL, Alignment.MID);
            build(t);
            tabs.add(t);
            x += tabW + TAB_GAP;
        }

        label(panel, "Leave", Color.WHITE, PAD, footTop + (FOOT_H - 18f) / 2f, LEAVE_W, 18f, Fonts.DEFAULT_SMALL, Alignment.MID);
        messageLabel = label(panel, "", TEXT, PAD + LEAVE_W + 20f, footTop + (FOOT_H - 18f) / 2f,
                listW - LEAVE_W - 40f, 18f, Fonts.DEFAULT_SMALL, Alignment.LMID);

        showTab(0);
        refresh();
    }

    private float contentW() {
        return listW - SCROLLBAR_W;
    }

    private float buyX() {
        return contentW() - 12f - BUY_W;
    }

    private void build(Tab t) {
        float y = ROW_GAP;
        for (StoreItem item : StoreCatalog.itemsIn(t.name)) {
            t.rows.add(new Row(item, y));
            y += ROW_H + ROW_GAP;
        }
        float contentH = Math.max(listH, y + ROW_GAP);

        t.container = panel.createCustomPanel(listW, listH, null);
        panel.addComponent(t.container).inTL(HIDDEN_X, listTop);
        t.contentLeft = PAD;
        t.contentTop = listTop;

        TooltipMakerAPI scroller = t.container.createUIElement(listW, listH, true);
        t.content = t.container.createCustomPanel(listW, contentH, null);
        float textX = ROW_X + ICON + TEXT_GAP;
        float priceX = buyX() - TEXT_GAP - PRICE_W;
        float textW = priceX - TEXT_GAP - textX;
        for (Row r : t.rows) {
            label(t.content, r.item.name, GOLD, textX, r.y + 14f, textW, 24f, Fonts.INSIGNIA_LARGE, Alignment.LMID);
            if (!r.item.description.isEmpty()) {
                wrapped(t.content, r.item.description, DIM, Fonts.DEFAULT_SMALL, textX, r.y + 42f, textW);
            }
            boolean limited = r.item.limited();
            r.price = label(t.content, "", GOLD, priceX, r.y + (limited ? 22f : 33f), PRICE_W, 18f,
                    Fonts.DEFAULT_SMALL, Alignment.RMID);
            if (limited) {
                r.stock = label(t.content, "", DIM, priceX, r.y + 44f, PRICE_W, 18f, Fonts.DEFAULT_SMALL, Alignment.RMID);
            }
            r.button = label(t.content, "", Color.WHITE, buyX(), r.y + (ROW_H - 18f) / 2f, BUY_W, 18f,
                    Fonts.DEFAULT_SMALL, Alignment.MID);
        }
        if (t.rows.isEmpty()) {
            label(t.content, "Nothing on offer here.", DIM, ROW_X, 16f, contentW() - 24f, 18f, Fonts.DEFAULT_SMALL, Alignment.LMID);
        }
        scroller.addCustom(t.content, 0f);
        scroller.setHeightSoFar(contentH);
        t.container.addUIElement(scroller).inTL(0f, 0f);
    }

    private LabelAPI label(CustomPanelAPI parent, String text, Color color, float x, float y, float w, float h,
                           String font, Alignment align) {
        LabelAPI label = Global.getSettings().createLabel(text, font);
        label.setColor(color);
        label.setAlignment(align);
        parent.addComponent((UIComponentAPI) label).inTL(x, y).setSize(w, h);
        return label;
    }

    private void wrapped(CustomPanelAPI parent, String text, Color color, String font, float x, float y, float w) {
        LabelAPI label = Global.getSettings().createLabel(text, font);
        label.setColor(color);
        label.setAlignment(Alignment.TL);
        parent.addComponent((UIComponentAPI) label);
        label.autoSizeToWidth(w);
        label.getPosition().inTL(x, y);
    }

    /** Budget, prices, stock and every button's text, after any change. */
    private void refresh() {
        SaveState state = SaveState.get();
        if (state != null) {
            String budget = String.valueOf(state.budget());
            String career = String.valueOf(Career.total());
            budgetLabel.setText("Points to spend: " + budget + "      Career points: " + career);
            budgetLabel.setHighlight(budget, career);
            budgetLabel.setHighlightColors(GOLD, GOLD);
        }
        for (Tab t : tabs) {
            for (Row r : t.rows) {
                r.blocker = state == null ? "Closed" : Purchases.blocker(r.item, state);
                r.price.setText(StoreCatalog.cost(r.item) + " pts");
                if (r.stock != null && state != null) r.stock.setText(Purchases.left(r.item, state) + " left");
                boolean confirming = r.item.id.equals(pendingItem);
                r.button.setText(r.blocker != null ? r.blocker : confirming ? "Confirm" : "Buy");
                r.button.setColor(r.blocker != null ? FAINT : Color.WHITE);
            }
        }
    }

    private void showTab(int next) {
        if (tabs.isEmpty()) return;
        tab = next;
        for (int i = 0; i < tabs.size(); i++) {
            tabs.get(i).container.getPosition().inTL(i == tab ? PAD : HIDDEN_X, listTop);
        }
    }

    private void message(String text, Color color) {
        messageLabel.setText(text);
        messageLabel.setColor(color);
    }

    // ---- input ----

    private void close() {
        if (dismissed) return;
        dismissed = true;
        if (callbacks != null) callbacks.dismissDialog();
    }

    private void clickBuy(Row r) {
        if (!r.item.id.equals(pendingItem)) {
            pendingItem = r.item.id;
            message("Click again to confirm: " + r.item.name + ".", DIM);
            return;
        }
        pendingItem = null;
        SaveState state = SaveState.get();
        if (state != null && Purchases.buy(r.item, state)) {
            message(goodsGoToBay(r.item) ? r.item.name + ": waiting in the delivery bay." : r.item.name + ": done.", GREEN);
            Global.getSoundPlayer().playUISound("ui_cargo_special_tech_drop", 1f, 1f);
        } else {
            message("Purchase failed.", RED);
        }
    }

    private static boolean goodsGoToBay(StoreItem item) {
        return "commodity".equals(item.kind) || "special_item".equals(item.kind) || "weapon_pack".equals(item.kind);
    }

    /** Reads where the shown list's scrolled content sits now, in this panel's top-down coordinates. */
    private void updateContent() {
        if (position == null || tabs.isEmpty()) return;
        Tab t = tabs.get(tab);
        PositionAPI p = t.content == null ? null : t.content.getPosition();
        if (p == null) return;
        t.contentLeft = p.getX() - position.getX();
        t.contentTop = (position.getY() + panelH) - (p.getY() + p.getHeight());
    }

    private static boolean inBox(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    @Override
    public void advance(float amount) {
        updateContent();
        if (dismissed || position == null || !Mouse.isCreated()) return;
        float scale = Global.getSettings().getScreenScaleMult();
        if (scale <= 0f) return;
        float mx = Mouse.getX() / scale - position.getX();
        float my = position.getY() + panelH - Mouse.getY() / scale;
        boolean leftDown = Mouse.isButtonDown(0);
        boolean clicked = leftDown && !wasLeftDown;
        wasLeftDown = leftDown;

        leaveHover = inBox(mx, my, PAD, footTop, LEAVE_W, FOOT_H);
        for (Tab t : tabs) t.hover = inBox(mx, my, t.x, tabsTop, t.w, TAB_H);
        Tab shown = tabs.isEmpty() ? null : tabs.get(tab);
        boolean inList = inBox(mx, my, PAD, listTop, listW, listH);
        if (shown != null) {
            for (Row r : shown.rows) {
                r.hover = inList && inBox(mx, my, shown.contentLeft + buyX(),
                        shown.contentTop + r.y + (ROW_H - BUY_H) / 2f, BUY_W, BUY_H);
            }
        }
        if (!clicked) return;

        if (leaveHover) {
            close();
            return;
        }
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i).hover && i != tab) {
                Global.getSoundPlayer().playUISound("ui_button_pressed", 1f, 0.6f);
                pendingItem = null;
                message("", TEXT);
                showTab(i);
                refresh();
                return;
            }
        }
        if (shown == null) return;
        for (Row r : shown.rows) {
            if (r.hover && r.blocker == null) {
                Global.getSoundPlayer().playUISound("ui_button_pressed", 1f, 0.6f);
                clickBuy(r);
                refresh();
                return;
            }
        }
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
        if (events == null || dismissed) return;
        for (InputEventAPI e : events) {
            if (!e.isConsumed() && e.isKeyDownEvent() && e.getEventValue() == Keyboard.KEY_ESCAPE) {
                e.consume();
                close();
                return;
            }
        }
    }

    @Override
    public void positionChanged(PositionAPI p) {
        position = p;
    }

    // ---- drawing ----

    private float x(float n) {
        return position.getX() + n;
    }

    private float y(float topDown) {
        return position.getY() + panelH - topDown;
    }

    private void rect(float left, float top, float w, float h, Color c, float alpha) {
        GLDraw.quad(x(left), y(top + h), w, h, c, alpha);
    }

    private void frame(float left, float top, float w, float h, Color c, float thickness, float alpha) {
        GLDraw.frame(x(left), y(top + h), w, h, c, thickness, alpha);
    }

    private void button(float left, float top, float w, float h, Color fill, Color edge, float alpha) {
        GLDraw.bevelledPanel(x(left), y(top + h), w, h, fill, 2f, alpha);
        frame(left, top, w, h, edge, 1f, alpha * 0.65f);
    }

    private void clip(float left, float top, float w, float h) {
        float scale = Global.getSettings().getScreenScaleMult();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor((int) (x(left) * scale), (int) (y(top + h) * scale),
                (int) Math.ceil(w * scale), (int) Math.ceil(h * scale));
    }

    private SpriteAPI sprite(String path) {
        if (sprites.containsKey(path)) return sprites.get(path);
        SpriteAPI s = load(path);
        sprites.put(path, s);
        return s;
    }

    private static SpriteAPI load(String path) {
        if (path == null || path.isEmpty()) return null;
        String clean = path.startsWith("/") ? path.substring(1) : path;
        try {
            SpriteAPI s = Global.getSettings().getSprite(clean);
            if (s != null && s.getTextureId() != 0) return s;
        } catch (Throwable ignored) {
            // Not loaded yet.
        }
        try {
            Global.getSettings().loadTexture(clean);
            SpriteAPI s = Global.getSettings().getSprite(clean);
            if (s != null && s.getTextureId() != 0) return s;
        } catch (Throwable ignored) {
            // No such picture.
        }
        return null;
    }

    /** The picture fitted inside a square, keeping its shape (weapon sprites are tall). */
    private void drawIcon(String path, float left, float top, float size, float alpha) {
        SpriteAPI s = sprite(path);
        if (s == null) return;
        float w = s.getWidth(), h = s.getHeight();
        if (w <= 0f || h <= 0f) return;
        float k = Math.min(size / w, size / h);
        float dw = w * k, dh = h * k;
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        s.setSize(dw, dh);
        s.setColor(Color.WHITE);
        s.setAlphaMult(alpha);
        s.render(x(left + (size - dw) / 2f), y(top + (size + dh) / 2f));
        s.setSize(w, h);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    @Override
    public void renderBelow(float alpha) {
        if (position == null) return;
        updateContent();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT
                | GL11.GL_TEXTURE_BIT | GL11.GL_LINE_BIT | GL11.GL_SCISSOR_BIT);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            rect(0f, 0f, panelW, panelH, BG, alpha);
            frame(0f, 0f, panelW, panelH, GOLD, 2f, alpha * 0.6f);
            rect(PAD, PAD + TITLE_H, listW, 1f, GOLD, alpha * 0.45f);

            for (int i = 0; i < tabs.size(); i++) {
                Tab t = tabs.get(i);
                boolean on = i == tab;
                button(t.x, tabsTop, t.w, TAB_H, on ? BUTTON_ON : t.hover ? BUTTON_HOVER : BUTTON, on ? GOLD : DIM, alpha);
            }

            rect(PAD, listTop, listW, listH, PANEL, alpha * 0.6f);
            frame(PAD, listTop, listW, listH, DIM, 1f, alpha * 0.25f);
            if (!tabs.isEmpty()) drawList(tabs.get(tab), alpha);

            button(PAD, footTop, LEAVE_W, FOOT_H, leaveHover ? BUTTON_HOVER : BUTTON, DIM, alpha);
        } finally {
            GL11.glPopAttrib();
        }
    }

    private void drawList(Tab t, float alpha) {
        clip(PAD, listTop, listW, listH);
        float left = t.contentLeft, w = contentW();
        for (Row r : t.rows) {
            float top = t.contentTop + r.y;
            if (top + ROW_H < listTop || top > listTop + listH) continue;
            rect(left, top, w, ROW_H, ROW_FILL, alpha);
            rect(left, top, 3f, ROW_H, r.blocker == null ? GOLD : FAINT, alpha * 0.7f);

            float iconX = left + ROW_X, iconY = top + (ROW_H - ICON) / 2f;
            rect(iconX, iconY, ICON, ICON, BG, alpha * 0.7f);
            drawIcon(r.item.icon, iconX + 4f, iconY + 4f, ICON - 8f, alpha);
            frame(iconX, iconY, ICON, ICON, GOLD, 1f, alpha * 0.5f);

            boolean confirming = r.item.id.equals(pendingItem);
            Color fill = r.blocker != null ? BUTTON_OFF : confirming ? CONFIRM : r.hover ? BUTTON_HOVER : BUTTON;
            button(left + buyX(), top + (ROW_H - BUY_H) / 2f, BUY_W, BUY_H, fill,
                    confirming ? GREEN : r.blocker != null ? FAINT : DIM, alpha);
        }
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }
}
