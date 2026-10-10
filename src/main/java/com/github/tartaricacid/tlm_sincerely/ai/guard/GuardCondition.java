package com.github.tartaricacid.tlm_sincerely.ai.guard;

/**
 * 守护名单里"这个目标什么时候才需要守"的条件（纯逻辑）。
 *
 * <p>血量都按"点"算（2 点 = 1 颗心，玩家 20 点满血）。为了不让目标的血量在门槛上下晃时守护对象来回切，
 * 条件带"滞回"：血量降到门槛以下才开始守，守起来之后要回升到 {@link #recoveryMargin()} 以上才算好了。
 *
 * @param threshold {@link Kind#HP_BELOW} 是血量点数，{@link Kind#HP_PERCENT_BELOW} 是百分比（1~99）；
 *                  {@link Kind#ALWAYS} 不用它
 */
public record GuardCondition(Kind kind, double threshold) {
    public enum Kind {
        /** 一直守。 */
        ALWAYS,
        /** 目标血量低于 threshold 点才守。 */
        HP_BELOW,
        /** 目标血量低于 threshold% 才守。 */
        HP_PERCENT_BELOW
    }

    public static final GuardCondition ALWAYS = new GuardCondition(Kind.ALWAYS, 0.0D);

    /** 血量点数条件的滞回宽度（点）：守起来之后回升到 门槛 + 它 才算好了。 */
    public static final double HP_MARGIN = 4.0D;
    /** 血量百分比条件的滞回宽度（百分点）。 */
    public static final double PERCENT_MARGIN = 15.0D;

    public GuardCondition {
        threshold = switch (kind) {
            case ALWAYS -> 0.0D;
            case HP_BELOW -> Math.max(1.0D, Math.min(1000.0D, threshold));
            case HP_PERCENT_BELOW -> Math.max(1.0D, Math.min(99.0D, threshold));
        };
    }

    public static GuardCondition hpBelow(double points) {
        return new GuardCondition(Kind.HP_BELOW, points);
    }

    public static GuardCondition hpPercentBelow(double percent) {
        return new GuardCondition(Kind.HP_PERCENT_BELOW, percent);
    }

    /** 守起来之后，要回升到多少才算"好了"（相对门槛的宽度）。 */
    public double recoveryMargin() {
        return switch (kind) {
            case ALWAYS -> 0.0D;
            case HP_BELOW -> HP_MARGIN;
            case HP_PERCENT_BELOW -> PERCENT_MARGIN;
        };
    }

    /**
     * 此刻这个条件成不成立。
     *
     * @param alreadyTriggered 上一次判定时它就成立（已经在守了）：此时要回升到门槛 + 滞回宽度以上才不再成立
     */
    public boolean holds(double hp, double maxHp, boolean alreadyTriggered) {
        double limit = threshold + (alreadyTriggered ? recoveryMargin() : 0.0D);
        return switch (kind) {
            case ALWAYS -> true;
            case HP_BELOW -> hp < limit;
            case HP_PERCENT_BELOW -> (maxHp > 0.0D ? hp / maxHp * 100.0D : 100.0D) < limit;
        };
    }
}
