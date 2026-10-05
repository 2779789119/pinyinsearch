package com.pinyinsearch.impl;

import java.util.Locale;

/**
 * 归一化：小写 + 全角转半角 + 去首尾空白。
 *
 * <p>这是调用方最容易踩的坑，因此由库统一吃掉：{@code PinyinSearch.matches} /
 * {@code Matcher} 内部都会先归一化再匹配。</p>
 *
 * <p><b>行为稳定性承诺</b>：公开 API {@code PinyinSearch.normalize} 的输入输出行为在 1.x 内保持不变
 * —— 一旦调用方拿它建过索引，行为变化会让调用方的索引失效。因此本类任何改动都必须先评估该承诺。</p>
 *
 * <p>非公开 API（{@code impl} 包不对外承诺兼容性）。</p>
 */
public final class Normalizer {

    private Normalizer() {
    }

    /**
     * @param text 待归一化文本；{@code null} → 返回 {@code ""}（不抛异常）
     * @return 归一化后的文本，恒不为 {@code null}
     */
    public static String normalize(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '\uFF01' && c <= '\uFF5E') {
                // 全角 ASCII（！…～）→ 半角
                c -= 0xFEE0;
            } else if (c == '\u3000') {
                // 表意空格 → 普通空格，交给后面的 strip 处理
                c = ' ';
            }
            sb.append(c);
        }
        // strip() 处理 Unicode 空白（比 trim() 更全面）
        return sb.toString().strip().toLowerCase(Locale.ROOT);
    }
}
