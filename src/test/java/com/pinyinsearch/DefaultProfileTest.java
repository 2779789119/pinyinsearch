package com.pinyinsearch;

import com.pinyinsearch.api.PinyinSearch;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 开发文档 §8.1「默认 Profile」用例（纯 JUnit 表驱动，不依赖 MC 运行时）。
 *
 * <p><b>★ 两处与开发文档表格不一致、且以实测为准的行</b>（PinIn 1.6.0 实测，见 README「实测结论」）：</p>
 * <ul>
 *   <li>{@code 中国 | z国}：文档写 {@code ✗}，实测 {@code ✓}。
 *       原因：{@code z} 本来就是 {@code zhong} 的合法简拼首字母（和 {@code zsj} 能搜「钻石剑」是同一机制），
 *       <b>不是</b>模糊音 {@code zh -> z}。模糊音的对照组（{@code zong国}）实测确为 {@code ✗}。</li>
 *   <li>{@code 中国 | zh1国}：文档写 {@code ✓}，实测 {@code ✗}。
 *       原因：声调数字必须跟在<b>完整音节</b>后面（{@code zhong1} ✓、{@code zhen1} ✓），
 *       跟在裸声母后面（{@code zh1}）PinIn 不识别。</li>
 * </ul>
 */
class DefaultProfileTest {

    static Stream<Arguments> defaultProfileCases() {
        return Stream.of(
                // 文本, 查询, 期望, 覆盖点
                Arguments.of("铁砧", "tiezhen", true, "全拼"),
                Arguments.of("铁砧", "tz", true, "声母"),
                Arguments.of("铁砧", "tiezh", true, "简拼"),
                Arguments.of("铁砧", "zuanjian", false, "不误命中"),
                Arguments.of("钻石剑", "zuanshijian", true, "全拼"),
                Arguments.of("钻石剑", "zsj", true, "声母"),
                Arguments.of("钻石剑", "钻石", true, "原文包含"),
                Arguments.of("钻石剑", "Diamond Sword", false, "不误命中"),
                Arguments.of("中国", "zhongguo", true, "任意组合（PinIn README 示例）"),
                Arguments.of("中国", "中guo", true, "任意组合"),
                Arguments.of("中国", "zhong国", true, "任意组合"),
                Arguments.of("中国", "zh国", true, "任意组合"),
                Arguments.of("中国", "zhong1国", true, "声调"),
                Arguments.of("中国", "zh1国", false, "声调：裸声母后不接声调数字（实测，与文档表格不一致）"),
                Arguments.of("中国", "zhong4国", true, "声调按读音校验：中 是多音字（zhōng / zhòng），tone 4 命中另一个读音"),
                Arguments.of("铁砧", "tie3", true, "声调校验通过（铁 tiě）"),
                Arguments.of("铁砧", "tie1", false, "声调写错时不命中（铁是第三声）"),
                Arguments.of("中国", "zong国", false, "默认模糊音全关"),
                Arguments.of("中国", "z国", true, "z 是 zhong 的合法简拼首字母，与模糊音无关（实测，与文档表格不一致）"),
                Arguments.of("Ｄｉａｍｏｎｄ", "diamond", true, "全角归一化"),
                Arguments.of("Diamond Sword", "diamond", true, "英文子串（大小写不敏感，实测通过）"),
                Arguments.of("Diamond Sword", "word", true, "英文子串（任意位置）"),
                Arguments.of("Diamond Sword", "diamonds", false, "英文子串是「包含」而不是「前缀/模糊」"),
                Arguments.of("Diamond Sword", "DS", false, "英文词首字母不支持"),
                Arguments.of("铁砧", "", true, "空查询"),
                Arguments.of("铁砧", "  ", true, "全空白查询"),
                Arguments.of("铁砧", null, true, "null 查询（§4.1）"),
                Arguments.of(null, "tie", true, "null 文本（§4.1）"),
                Arguments.of(null, null, true, "双 null（§4.1）"),
                Arguments.of("铁砧", "!!!", false, "无匹配退化（不抛异常）"),
                Arguments.of("", "tie", false, "空文本 + 非空查询")
        );
    }

    @ParameterizedTest(name = "[{index}] 文本={0} 查询={1} → {2}（{3}）")
    @MethodSource("defaultProfileCases")
    void matches(String text, String query, boolean expected, String note) {
        assertEquals(expected, PinyinSearch.matches(text, query), note);
    }

    @ParameterizedTest(name = "[{index}] 文本={0} 查询={1} 不抛异常（{3}）")
    @MethodSource("defaultProfileCases")
    void neverThrows(String text, String query, boolean expected, String note) {
        assertDoesNotThrow(() -> PinyinSearch.matches(text, query), note);
    }

    @org.junit.jupiter.api.Test
    void normalizeIsStable() {
        assertEquals("diamond sword", PinyinSearch.normalize("Ｄｉａｍｏｎｄ Ｓｗｏｒｄ"));
        assertEquals("铁砧", PinyinSearch.normalize("  铁砧  "));
        assertEquals("abc", PinyinSearch.normalize("ABC"));
        assertEquals("", PinyinSearch.normalize(null));
        assertEquals("", PinyinSearch.normalize("　"), "表意空格应被去掉");
        assertEquals(PinyinSearch.normalize("中国"), PinyinSearch.normalize(PinyinSearch.normalize("中国")),
                "归一化必须幂等");
    }

    @org.junit.jupiter.api.Test
    void libraryIsAvailable() {
        assertTrue(PinyinSearch.isAvailable());
    }
}
