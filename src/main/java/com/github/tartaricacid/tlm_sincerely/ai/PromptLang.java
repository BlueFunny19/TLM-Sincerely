package com.github.tartaricacid.tlm_sincerely.ai;

import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 提示词语言选择。本模组发给 LLM 的提示词有中、英两套：
 * <ul>
 *   <li>对话语言是中文（zh*）→ 用中文提示词（小模型对中文指令的遵守更好）；</li>
 *   <li>其余语言 → 回落到英文提示词，并在提示词末尾追加一句"请改用该语言回复"（见 {@link #replyDirective}）。</li>
 * </ul>
 * 对话语言取自主 Mod 为本次对话记录的 {@code chatLanguage}（即玩家客户端语言），
 * 没有记录时主 Mod 自己的兜底是 en_us，这里保持一致。
 */
public final class PromptLang {
    public static final String DEFAULT_LANGUAGE = "en_us";

    /** 语言标签形态：2~3 字母语言码 + 可选 2~4 字母地区码。能匹配的字符串不含空格/标点/换行，拼入提示词无法构成注入载荷。 */
    private static final Pattern LANG_TAG = Pattern.compile("^[a-z]{2,3}(_[a-z]{2,4})?$");

    private PromptLang() {
    }

    /** 该女仆当前对话所用的语言（已规整为小写下划线形式，如 zh_cn）。 */
    public static String of(EntityMaid maid) {
        if (maid == null) {
            return DEFAULT_LANGUAGE;
        }
        MaidAIChatManager manager = maid.getAiChatManager();
        if (manager == null) {
            return DEFAULT_LANGUAGE;
        }
        return normalize(manager.getChatLanguage());
    }

    /** 规整语言标签：转小写、"-" 换成 "_"；为空或形态不合法时回落到 en_us。 */
    public static String normalize(String language) {
        if (language == null) {
            return DEFAULT_LANGUAGE;
        }
        String lang = language.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return LANG_TAG.matcher(lang).matches() ? lang : DEFAULT_LANGUAGE;
    }

    public static boolean isChinese(String language) {
        return normalize(language).startsWith("zh");
    }

    public static boolean isEnglish(String language) {
        return normalize(language).startsWith("en");
    }

    public static String pick(String language, String zh, String en) {
        return isChinese(language) ? zh : en;
    }

    public static String pick(EntityMaid maid, String zh, String en) {
        return pick(of(maid), zh, en);
    }

    /**
     * 追加在提示词末尾的"改用某语言回复"一句。简体中文与英文返回空串（提示词本身已是那种语言），
     * 繁体中文提示"请用繁体中文"，其他语言用英文提示词并要求改用该语言。
     */
    public static String replyDirective(String language) {
        String lang = normalize(language);
        if (lang.startsWith("en")) {
            return "";
        }
        if (lang.startsWith("zh")) {
            return switch (lang) {
                case "zh_tw", "zh_hk", "zh_mo" -> "请始终使用繁体中文回复，不要使用简体中文。";
                default -> "";
            };
        }
        Locale locale = Locale.forLanguageTag(lang.replace('_', '-'));
        String name = locale.getDisplayLanguage(Locale.ENGLISH);
        if (name.isEmpty()) {
            return "";
        }
        String country = locale.getDisplayCountry(Locale.ENGLISH);
        return "Always reply to the player in " + name + (country.isEmpty() ? "" : " (" + country + ")")
                + ", even though the instructions above are written in English.";
    }
}
