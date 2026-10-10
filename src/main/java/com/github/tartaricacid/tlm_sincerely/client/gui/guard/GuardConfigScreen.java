package com.github.tartaricacid.tlm_sincerely.client.gui.guard;

import com.github.tartaricacid.tlm_sincerely.ai.guard.GuardCondition;
import com.github.tartaricacid.tlm_sincerely.ai.guard.GuardConfigContainer;
import com.github.tartaricacid.tlm_sincerely.ai.guard.GuardData;
import com.github.tartaricacid.tlm_sincerely.ai.guard.GuardEditC2SPacket;
import com.github.tartaricacid.tlm_sincerely.ai.guard.GuardEntry;
import com.github.tartaricacid.tlm_sincerely.ai.guard.GuardRules;
import com.github.tartaricacid.tlm_sincerely.ai.guard.GuardTaskDataKeys;
import com.github.tartaricacid.tlm_sincerely.priority.autowork.network.AutoWorkNetworking;
import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.task.MaidTaskConfigGui;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 守护任务的配置界面：编辑守护名单。
 *
 * <p>三个视图共用右侧那块面板：<b>名单</b>（每项一行，点进去编辑；可添加目标、取消时限）、
 * <b>编辑</b>（条件、血量门槛、守护半径、优先级上下挪、删除）、<b>添加</b>（候选：主人与附近同主人的女仆）。
 * 全部用原版按钮搭成，没有自绘贴图，坐标都是相对面板左上角的固定值。
 *
 * <p>界面不直接改数据：每个操作都发一个 {@link GuardEditC2SPacket} 给服务端，服务端校验后改名单，
 * 名单再通过任务数据同步回来，界面发现数据变了就重建。编辑输入框正在输入时不重建（免得丢焦点和已输入的内容），
 * 输入停顿一小会儿后自动提交。
 */
public class GuardConfigScreen extends MaidTaskConfigGui<GuardConfigContainer> {
    /** 面板位置与大小：和自动工作配置页、主模组的默认任务配置页一致。 */
    private static final int PANEL_OFFSET_X = 80;
    private static final int PANEL_OFFSET_Y = 28;
    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_HEIGHT = 137;
    private static final ResourceLocation PANEL_TEXTURE =
            new ResourceLocation("touhou_little_maid", "textures/gui/maid_gui_main.png");

    private static final int TEXT_COLOR = 0xFF000000;
    private static final int MARGIN = 6;
    private static final int CONTENT_WIDTH = 164;
    private static final int ROW_TOP = 29;
    private static final int ROW_HEIGHT = 11;
    private static final int BUTTON_HEIGHT = 14;
    /** 输入停顿多久后自动提交（tick）。 */
    private static final int SUBMIT_DELAY_TICKS = 15;
    private static final double DEFAULT_HP_THRESHOLD = 20.0D;
    private static final double DEFAULT_PERCENT_THRESHOLD = 50.0D;

    private enum Mode {LIST, EDIT, ADD}

    /** 添加视图里的候选。 */
    private record Candidate(UUID id, String name) {
    }

    private final List<AbstractWidget> ownedWidgets = new ArrayList<>();
    private Mode mode = Mode.LIST;
    /** 编辑视图里正在编辑的目标。 */
    private UUID editingId;
    private GuardCondition.Kind editingKind = GuardCondition.Kind.ALWAYS;
    private EditBox thresholdBox;
    private EditBox distanceBox;
    private int submitCountdown = -1;

    private GuardData shownData;
    private boolean dirty = true;

    public GuardConfigScreen(GuardConfigContainer menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        shownData = currentData();
        rebuild();
    }

    // ===== 数据 =====

    private GuardData currentData() {
        GuardData data = maid == null ? null : maid.getData(GuardTaskDataKeys.GUARD_KEY);
        return data == null ? GuardData.EMPTY : data;
    }

    private GuardEntry editingEntry() {
        GuardRules rules = currentData().rules();
        int index = editingId == null ? -1 : rules.indexOf(editingId);
        return index < 0 ? null : rules.entries().get(index);
    }

    private void send(GuardEditC2SPacket packet) {
        AutoWorkNetworking.channel().sendToServer(packet);
    }

    // ===== 帧循环 =====

    @Override
    protected void containerTick() {
        super.containerTick();
        GuardData now = currentData();
        if (!now.equals(shownData)) {
            shownData = now;
            dirty = true;
        }
        if (submitCountdown > 0 && --submitCountdown == 0) {
            submitCountdown = -1;
            submitEdit();
        }
        if (dirty && !boxFocused()) {
            dirty = false;
            if (mode == Mode.EDIT && editingEntry() == null) {
                mode = Mode.LIST;
            }
            rebuild();
        }
    }

    private boolean boxFocused() {
        return (thresholdBox != null && thresholdBox.isFocused()) || (distanceBox != null && distanceBox.isFocused());
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode != GLFW.GLFW_KEY_ESCAPE) {
            for (EditBox box : new EditBox[]{thresholdBox, distanceBox}) {
                if (box != null && box.isFocused()) {
                    // 正在输入时，按键归输入框（否则按 E 会直接关掉界面）
                    return box.keyPressed(keyCode, scanCode, modifiers) || box.canConsumeInput();
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        super.renderBg(graphics, partialTicks, mouseX, mouseY);
        graphics.blit(PANEL_TEXTURE, panelX(), panelY(), PANEL_OFFSET_X, PANEL_OFFSET_Y,
                PANEL_WIDTH, PANEL_HEIGHT, 256, 256);
    }

    @Override
    protected void renderAddition(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.renderAddition(graphics, mouseX, mouseY, partialTicks);
        Font font = Minecraft.getInstance().font;
        switch (mode) {
            case LIST -> drawListTexts(graphics, font);
            case EDIT -> drawEditTexts(graphics, font);
            case ADD -> drawText(graphics, font, Component.translatable("gui.tlm_sincerely.guard.add.title"), MARGIN, 7);
        }
    }

    private void drawListTexts(GuiGraphics graphics, Font font) {
        drawText(graphics, font, Component.translatable("gui.tlm_sincerely.guard.title"), MARGIN, 7);
        GuardData data = currentData();
        Component time;
        if (data.isTemporary()) {
            long left = Math.max(0L, (data.expireAtGameTick() - currentGameTime()) / 20L);
            time = Component.translatable("gui.tlm_sincerely.guard.time.limited", String.valueOf(left));
        } else {
            time = Component.translatable("gui.tlm_sincerely.guard.time.forever");
        }
        drawText(graphics, font, time, MARGIN, 18);
        if (data.rules().isEmpty()) {
            drawText(graphics, font, Component.translatable("gui.tlm_sincerely.guard.empty"), MARGIN, 40);
        }
    }

    private void drawEditTexts(GuiGraphics graphics, Font font) {
        GuardEntry entry = editingEntry();
        String name = entry == null ? "" : entry.name();
        drawText(graphics, font, Component.translatable("gui.tlm_sincerely.guard.edit.title",
                font.plainSubstrByWidth(name, 110)), MARGIN, 7);
        drawText(graphics, font, Component.translatable(editingKind == GuardCondition.Kind.HP_PERCENT_BELOW
                ? "gui.tlm_sincerely.guard.edit.threshold_percent"
                : "gui.tlm_sincerely.guard.edit.threshold_points"), MARGIN, 43);
        drawText(graphics, font, Component.translatable("gui.tlm_sincerely.guard.edit.distance"), MARGIN, 61);
        drawText(graphics, font, Component.translatable("gui.tlm_sincerely.guard.edit.hint"), MARGIN, 118);
    }

    private void drawText(GuiGraphics graphics, Font font, Component text, int x, int y) {
        graphics.drawString(font, text, panelX() + x, panelY() + y, TEXT_COLOR, false);
    }

    // ===== 控件重建 =====

    private void rebuild() {
        for (AbstractWidget widget : ownedWidgets) {
            removeWidget(widget);
        }
        ownedWidgets.clear();
        thresholdBox = null;
        distanceBox = null;
        switch (mode) {
            case LIST -> buildList();
            case EDIT -> buildEdit();
            case ADD -> buildAdd();
        }
    }

    private <T extends AbstractWidget> T own(T widget) {
        addRenderableWidget(widget);
        ownedWidgets.add(widget);
        return widget;
    }

    private Button button(int x, int y, int width, int height, Component label, Button.OnPress onPress) {
        return own(Button.builder(label, onPress).bounds(panelX() + x, panelY() + y, width, height).build());
    }

    private void buildList() {
        Font font = Minecraft.getInstance().font;
        GuardData data = currentData();
        int index = 0;
        for (GuardEntry entry : data.rules().entries()) {
            Component label = Component.literal(font.plainSubstrByWidth(
                    "%d. %s · %s".formatted(index + 1, entry.name(), shortCondition(entry.condition())), CONTENT_WIDTH - 8));
            UUID id = entry.targetId();
            button(MARGIN, ROW_TOP + index * ROW_HEIGHT, CONTENT_WIDTH, ROW_HEIGHT, label, b -> openEdit(id));
            index++;
        }
        Button add = button(MARGIN, 120, 80, BUTTON_HEIGHT,
                Component.translatable("gui.tlm_sincerely.guard.add"), b -> {
                    mode = Mode.ADD;
                    rebuild();
                });
        add.active = !data.rules().isFull();
        if (data.isTemporary()) {
            button(90, 120, 80, BUTTON_HEIGHT, Component.translatable("gui.tlm_sincerely.guard.make_permanent"),
                    b -> send(GuardEditC2SPacket.simple(maid.getUUID(), GuardEditC2SPacket.Op.MAKE_PERMANENT, maid.getUUID())));
        }
    }

    private void openEdit(UUID id) {
        editingId = id;
        GuardEntry entry = editingEntry();
        editingKind = entry == null ? GuardCondition.Kind.ALWAYS : entry.condition().kind();
        mode = Mode.EDIT;
        rebuild();
    }

    private void buildEdit() {
        GuardEntry entry = editingEntry();
        if (entry == null) {
            mode = Mode.LIST;
            buildList();
            return;
        }
        Font font = Minecraft.getInstance().font;
        button(MARGIN, 20, CONTENT_WIDTH, BUTTON_HEIGHT, conditionLabel(editingKind), b -> cycleKind());

        thresholdBox = own(new EditBox(font, panelX() + 104, panelY() + 39, 56, BUTTON_HEIGHT,
                Component.translatable("gui.tlm_sincerely.guard.edit.threshold_points")));
        configureNumberBox(thresholdBox);
        thresholdBox.setValue(editingKind == GuardCondition.Kind.ALWAYS ? ""
                : format(entry.condition().kind() == editingKind ? entry.condition().threshold() : defaultThreshold(editingKind)));
        thresholdBox.setEditable(editingKind != GuardCondition.Kind.ALWAYS);
        thresholdBox.active = editingKind != GuardCondition.Kind.ALWAYS;
        thresholdBox.setResponder(text -> submitCountdown = SUBMIT_DELAY_TICKS);

        distanceBox = own(new EditBox(font, panelX() + 104, panelY() + 57, 56, BUTTON_HEIGHT,
                Component.translatable("gui.tlm_sincerely.guard.edit.distance")));
        configureNumberBox(distanceBox);
        distanceBox.setValue(format(entry.distance()));
        distanceBox.setResponder(text -> submitCountdown = SUBMIT_DELAY_TICKS);

        UUID id = entry.targetId();
        UUID maidId = maid.getUUID();
        button(MARGIN, 78, 80, BUTTON_HEIGHT, Component.translatable("gui.tlm_sincerely.guard.edit.up"),
                b -> send(GuardEditC2SPacket.simple(maidId, GuardEditC2SPacket.Op.MOVE_UP, id)));
        button(90, 78, 80, BUTTON_HEIGHT, Component.translatable("gui.tlm_sincerely.guard.edit.down"),
                b -> send(GuardEditC2SPacket.simple(maidId, GuardEditC2SPacket.Op.MOVE_DOWN, id)));
        button(MARGIN, 96, 80, BUTTON_HEIGHT, Component.translatable("gui.tlm_sincerely.guard.edit.delete"), b -> {
            send(GuardEditC2SPacket.simple(maidId, GuardEditC2SPacket.Op.REMOVE, id));
            backToList();
        });
        button(90, 96, 80, BUTTON_HEIGHT, Component.translatable("gui.tlm_sincerely.guard.back"), b -> {
            flushPendingEdit();
            backToList();
        });
    }

    private void buildAdd() {
        List<Candidate> candidates = candidates();
        if (candidates.isEmpty()) {
            // 文字在 renderAddition 里画不到"没有候选"，直接用一个不可点的按钮占位
            Button none = button(MARGIN, 20, CONTENT_WIDTH, ROW_HEIGHT,
                    Component.translatable("gui.tlm_sincerely.guard.add.none"), b -> { });
            none.active = false;
        }
        UUID maidId = maid.getUUID();
        int row = 0;
        for (Candidate candidate : candidates) {
            if (row >= 8) {
                break;
            }
            button(MARGIN, 20 + row * ROW_HEIGHT, CONTENT_WIDTH, ROW_HEIGHT, Component.literal(candidate.name()), b -> {
                send(GuardEditC2SPacket.simple(maidId, GuardEditC2SPacket.Op.ADD, candidate.id()));
                backToList();
            });
            row++;
        }
        button(MARGIN, 120, 80, BUTTON_HEIGHT, Component.translatable("gui.tlm_sincerely.guard.back"), b -> backToList());
    }

    private void backToList() {
        mode = Mode.LIST;
        editingId = null;
        submitCountdown = -1;
        rebuild();
    }

    // ===== 编辑提交 =====

    private void cycleKind() {
        GuardCondition.Kind[] kinds = GuardCondition.Kind.values();
        editingKind = kinds[(editingKind.ordinal() + 1) % kinds.length];
        // 换了条件种类：门槛用新种类的默认值（输入框里是旧种类的数，不能带过去），半径沿用输入框
        submitEdit(true);
        rebuild();
    }

    private void flushPendingEdit() {
        if (submitCountdown > 0) {
            submitCountdown = -1;
            submitEdit();
        }
    }

    private void submitEdit() {
        submitEdit(false);
    }

    private void submitEdit(boolean defaultThreshold) {
        if (mode != Mode.EDIT || editingId == null || distanceBox == null) {
            return;
        }
        Double distance = parse(distanceBox.getValue());
        if (distance == null) {
            distance = editingEntry() == null ? GuardEntry.DEFAULT_DISTANCE : editingEntry().distance();
        }
        double threshold = 0.0D;
        if (editingKind != GuardCondition.Kind.ALWAYS) {
            Double parsed = defaultThreshold || thresholdBox == null ? null : parse(thresholdBox.getValue());
            threshold = parsed == null ? defaultThreshold(editingKind) : parsed;
        }
        send(new GuardEditC2SPacket(maid.getUUID(), GuardEditC2SPacket.Op.SET, editingId,
                editingKind, threshold, distance));
    }

    // ===== 小工具 =====

    private static void configureNumberBox(EditBox box) {
        box.setMaxLength(6);
        box.setFilter(text -> text.matches("\\d{0,4}(\\.\\d?)?"));
    }

    private static Double parse(String text) {
        try {
            return text.isBlank() ? null : Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static double defaultThreshold(GuardCondition.Kind kind) {
        return kind == GuardCondition.Kind.HP_PERCENT_BELOW ? DEFAULT_PERCENT_THRESHOLD : DEFAULT_HP_THRESHOLD;
    }

    private static String format(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }

    private static String shortCondition(GuardCondition condition) {
        return switch (condition.kind()) {
            case ALWAYS -> Component.translatable("gui.tlm_sincerely.guard.short.always").getString();
            case HP_BELOW -> Component.translatable("gui.tlm_sincerely.guard.short.hp", format(condition.threshold())).getString();
            case HP_PERCENT_BELOW -> Component.translatable("gui.tlm_sincerely.guard.short.percent", format(condition.threshold())).getString();
        };
    }

    private static Component conditionLabel(GuardCondition.Kind kind) {
        return Component.translatable(switch (kind) {
            case ALWAYS -> "gui.tlm_sincerely.guard.edit.condition.always";
            case HP_BELOW -> "gui.tlm_sincerely.guard.edit.condition.hp";
            case HP_PERCENT_BELOW -> "gui.tlm_sincerely.guard.edit.condition.percent";
        });
    }

    /** 候选：主人（在线、同维度）与附近同一主人的其他女仆，去掉已经在名单里的。 */
    private List<Candidate> candidates() {
        List<Candidate> result = new ArrayList<>();
        UUID ownerId = maid.getOwnerUUID();
        Level level = maid.level();
        if (ownerId == null || level == null) {
            return result;
        }
        GuardRules rules = currentData().rules();
        Player owner = level.getPlayerByUUID(ownerId);
        if (owner != null && !rules.contains(ownerId)) {
            result.add(new Candidate(ownerId, owner.getName().getString()));
        }
        for (EntityMaid other : level.getEntitiesOfClass(EntityMaid.class, maid.getBoundingBox().inflate(64.0D),
                e -> e != maid && ownerId.equals(e.getOwnerUUID()) && !rules.contains(e.getUUID()))) {
            result.add(new Candidate(other.getUUID(), other.getName().getString()));
        }
        return result;
    }

    private long currentGameTime() {
        Level level = maid == null ? null : maid.level();
        return level == null ? 0L : level.getGameTime();
    }

    private int panelX() {
        return getGuiLeft() + PANEL_OFFSET_X;
    }

    private int panelY() {
        return getGuiTop() + PANEL_OFFSET_Y;
    }

    @Override
    public void onClose() {
        flushPendingEdit();
        super.onClose();
    }
}
