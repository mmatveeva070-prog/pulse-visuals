package ru.pulse.gui;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import ru.pulse.cosmetic.CosmeticCatalog;

import java.util.ArrayList;
import java.util.List;

/**
 * Экран косметики на ванильных widget API 1.21.11:
 * вкладки All/Capes/Wings/Body/Pets/Hats, поиск, сетка карточек 85x51
 * (иконка item_N.png + имя + тип + статус EQUIP/ON), скролл колесом,
 * клик — экипировать/снять (лимит 5), кнопка закрытия.
 * UX-референс — pulse.gui.cosmetics.CosmeticsTab из бандля.
 */
public class CosmeticsScreen extends Screen {
    private static final String[] TABS = {"All", "Capes", "Wings", "Body", "Pets", "Hats"};
    private static final String[] TYPES = {"", "cape", "wings", "bodywear", "pet", "hat"};

    private static final int CARD_W = 85;
    private static final int CARD_H = 51;
    private static final int GAP = 6;
    private static final int COLUMNS = 4;
    private static final int TOP = 52;
    private static final int LEFT = 19;

    // Палитра (ARGB)
    private static final int COLOR_BG = 0xF0101018;
    private static final int COLOR_CARD = 0xFF1B1B26;
    private static final int COLOR_CARD_HOVER = 0xFF26262F;
    private static final int COLOR_CARD_SELECTED = 0xFF223355;
    private static final int COLOR_BORDER = 0xFF34343F;
    private static final int COLOR_BORDER_HOVER = 0xFF4A4A5A;
    private static final int COLOR_BORDER_SELECTED = 0xFF6EC8FF;
    private static final int COLOR_TEXT = 0xFFF2F2F6;
    private static final int COLOR_TEXT_DIM = 0xFF9A9AA8;
    private static final int COLOR_PILL = 0xFF33333F;
    private static final int COLOR_PILL_SELECTED = 0xFF3E9E68;
    private static final int COLOR_ACCENT = 0xFF6EC8FF;

    private final Screen parent;
    private int tab;
    private double scroll;
    private String search = "";
    private TextFieldWidget searchField;

    public CosmeticsScreen(Screen parent) {
        super(Text.literal("Pulse Cosmetics"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // Вкладки категорий
        int tabWidth = (this.width - LEFT * 2) / TABS.length;
        for (int i = 0; i < TABS.length; i++) {
            final int index = i;
            int x = LEFT + i * tabWidth;
            ButtonWidget button = ButtonWidget.builder(Text.literal(TABS[i]), b -> selectTab(index))
                .dimensions(x, 28, tabWidth - 2, 18)
                .build();
            this.addDrawableChild(button);
        }

        // Поиск
        this.searchField = this.addDrawableChild(new TextFieldWidget(
            this.textRenderer, LEFT, 8, 160, 16, Text.literal("Search")));
        this.searchField.setMaxLength(32);
        this.searchField.setPlaceholder(Text.literal("Search..."));
        this.searchField.setChangedListener(value -> {
            this.search = value == null ? "" : value.toLowerCase();
            this.scroll = 0;
        });
        if (!this.search.isEmpty()) {
            this.searchField.setText(this.search);
        }

        // Закрытие
        this.addDrawableChild(ButtonWidget.builder(Text.literal("x"), b -> this.close())
            .dimensions(this.width - 24, 6, 18, 18)
            .build());
    }

    private void selectTab(int index) {
        if (index >= 0 && index < TABS.length && index != this.tab) {
            this.tab = index;
            this.scroll = 0;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        // Заголовок и счётчик экипированных
        context.drawTextWithShadow(this.textRenderer, this.title, LEFT + 180, 12, COLOR_ACCENT);
        String count = CosmeticCatalog.selectedCount() + "/" + CosmeticCatalog.maxSelected() + " equipped";
        int countWidth = this.textRenderer.getWidth(count);
        context.fill(this.width - 28 - countWidth - 12, 10, this.width - 28, 24, 0x50223355);
        context.drawTextWithShadow(this.textRenderer, count, this.width - 28 - countWidth - 6, 14, COLOR_TEXT);

        // Виджеты (вкладки, поиск, закрытие)
        super.render(context, mouseX, mouseY, delta);

        // Сетка карточек
        List<Integer> items = this.filtered();
        int gridX = LEFT;
        int gridY = TOP;
        int gridW = this.width - LEFT * 2;
        int gridH = this.height - TOP - 10;
        this.renderCards(context, items, gridX, gridY, gridW, gridH, mouseX, mouseY);
    }

    private void renderCards(DrawContext context, List<Integer> items, int x, int y, int width, int height, int mouseX, int mouseY) {
        // Подложка
        context.fill(x - 3, y - 3, x + width + 3, y + height + 3, COLOR_BG);

        int rows = (items.size() + COLUMNS - 1) / COLUMNS;
        int contentHeight = Math.max(1, rows) * CARD_H + Math.max(0, rows - 1) * GAP;
        int maxScroll = Math.max(0, contentHeight - height);
        if (this.scroll < 0) {
            this.scroll = 0;
        }
        if (this.scroll > maxScroll) {
            this.scroll = maxScroll;
        }

        // Scissor-обрезка по области сетки
        context.enableScissor(x, y, x + width, y + height);
        try {
            for (int pos = 0; pos < items.size(); pos++) {
                int index = items.get(pos);
                int col = pos % COLUMNS;
                int row = pos / COLUMNS;
                int cardX = x + col * (CARD_W + GAP);
                int cardY = y + row * (CARD_H + GAP) - (int) this.scroll;
                if (cardY > y + height || cardY + CARD_H < y) {
                    continue;
                }

                this.renderCard(context, index, cardX, cardY,
                    mouseX >= cardX && mouseX < cardX + CARD_W && mouseY >= cardY && mouseY < cardY + CARD_H);
            }
        } finally {
            context.disableScissor();
        }

        // Полоса прокрутки
        if (contentHeight > height) {
            int barX = x + width - 3;
            context.fill(barX, y, barX + 2, y + height, 0x30343F4F);
            int thumbHeight = Math.max(12, (int) ((float) height / contentHeight * height));
            int thumbY = y + (int) ((float) this.scroll / maxScroll * (height - thumbHeight));
            context.fill(barX - 1, thumbY, barX + 2, thumbY + thumbHeight, 0xFF6EC8FF);
        }
    }

    private void renderCard(DrawContext context, int index, int x, int y, boolean hovered) {
        boolean selected = CosmeticCatalog.isSelected(index);
        int bg = selected ? COLOR_CARD_SELECTED : (hovered ? COLOR_CARD_HOVER : COLOR_CARD);
        int border = selected ? COLOR_BORDER_SELECTED : (hovered ? COLOR_BORDER_HOVER : COLOR_BORDER);

        // Тело карточки
        context.fill(x, y, x + CARD_W, y + CARD_H, bg);
        // Рамка (1px)
        context.fill(x, y, x + CARD_W, y + 1, border);
        context.fill(x, y + CARD_H - 1, x + CARD_W, y + CARD_H, border);
        context.fill(x, y, x + 1, y + CARD_H, border);
        context.fill(x + CARD_W - 1, y, x + CARD_W, y + CARD_H, border);

        // Иконка (динамическая регистрация item_N.png через TextureManager)
        int[] iconWidth = new int[1];
        int[] iconHeight = new int[1];
        Identifier icon = CosmeticCatalog.iconTexture(index, iconWidth, iconHeight);
        if (icon != null && iconWidth[0] > 0 && iconHeight[0] > 0) {
            int iconSize = 23;
            int iconX = x + 7;
            int iconY = y + 8;
            // Вся текстура, вписанная в квадрат iconSize (12-арг вариант с regionWidth/Height)
            context.drawTexture(RenderPipelines.GUI_TEXTURED, icon, iconX, iconY, 0.0F, 0.0F,
                iconSize, iconSize, iconWidth[0], iconHeight[0], iconWidth[0], iconHeight[0]);
        }

        // Имя (с обрезкой)
        String name = CosmeticCatalog.name(index);
        String clipped = this.textRenderer.trimToWidth(name, CARD_W - 46);
        context.drawTextWithShadow(this.textRenderer, clipped, x + 35, y + 9, COLOR_TEXT);

        // Тип
        String type = displayType(CosmeticCatalog.type(index));
        context.drawTextWithShadow(this.textRenderer, type, x + 35, y + 21, COLOR_TEXT_DIM);

        // Статус EQUIP/ON
        String action = selected ? "ON" : "EQUIP";
        int actionWidth = this.textRenderer.getWidth(action);
        context.fill(x + 7, y + 37, x + CARD_W - 7, y + 45, selected ? COLOR_PILL_SELECTED : COLOR_PILL);
        context.drawTextWithShadow(this.textRenderer, action, x + (CARD_W - actionWidth) / 2, y + 38, selected ? 0xFFFFFFFF : COLOR_TEXT_DIM);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int gridH = this.height - TOP - 10;
        int rows = (this.filtered().size() + COLUMNS - 1) / COLUMNS;
        int contentHeight = Math.max(1, rows) * CARD_H + Math.max(0, rows - 1) * GAP;
        if (contentHeight > gridH) {
            this.scroll -= verticalAmount * 24.0;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
        int hit = this.hitCard(click.x(), click.y());
        if (hit >= 0) {
            CosmeticCatalog.toggle(hit);
            return true;
        }
        return super.mouseClicked(click, doubleClick);
    }

    private int hitCard(double mouseX, double mouseY) {
        int gridX = LEFT;
        int gridY = TOP;
        int gridW = this.width - LEFT * 2;
        int gridH = this.height - TOP - 10;
        if (mouseX < gridX || mouseX >= gridX + gridW || mouseY < gridY || mouseY >= gridY + gridH) {
            return -1;
        }

        List<Integer> items = this.filtered();
        int localX = (int) (mouseX - gridX);
        int localY = (int) (mouseY - gridY + this.scroll);
        int col = localX / (CARD_W + GAP);
        int row = localY / (CARD_H + GAP);
        if (col < 0 || col >= COLUMNS) {
            return -1;
        }

        int inCellX = localX - col * (CARD_W + GAP);
        int inCellY = localY - row * (CARD_H + GAP);
        int pos = row * COLUMNS + col;
        return inCellX <= CARD_W && inCellY <= CARD_H && pos >= 0 && pos < items.size() ? items.get(pos) : -1;
    }

    private List<Integer> filtered() {
        String type = TYPES[this.tab];
        List<Integer> result = new ArrayList<>();

        for (int i = 0; i < CosmeticCatalog.size(); i++) {
            if (!type.isEmpty() && !type.equals(CosmeticCatalog.type(i))) {
                continue;
            }
            if (!this.search.isEmpty() && !CosmeticCatalog.name(i).toLowerCase().contains(this.search)) {
                continue;
            }
            result.add(i);
        }

        return result;
    }

    private static String displayType(String type) {
        if ("bodywear".equals(type)) {
            return "body";
        }
        return type;
    }

    @Override
    public void close() {
        ru.pulse.config.PulseConfig.get().save();
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
