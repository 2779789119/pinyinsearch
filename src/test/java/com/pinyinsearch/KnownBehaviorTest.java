package com.pinyinsearch;

import com.pinyinsearch.api.PinyinSearch;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 开发文档 §8.1 中标注「待实测」的项 —— 这里把<b>实测结论固定下来</b>，
 * 既是回归保护（PinIn 升级后行为若变了会被测出来），也是 README「已知限制」的依据。
 *
 * <p>实测环境：vendored PinIn 1.6.0（上游 master），1.20.1 Forge 项目内纯 JVM 运行。</p>
 */
@DisplayName("实测结论（写进 README 的「已知限制」）")
class KnownBehaviorTest {

    @Test
    @DisplayName("英文子串匹配可用：大小写不敏感，任意位置，但必须是「包含」")
    void englishSubstringWorks() {
        assertTrue(PinyinSearch.matches("Diamond Sword", "diamond"));
        assertTrue(PinyinSearch.matches("Diamond Sword", "word"));
        assertTrue(PinyinSearch.matches("Diamond Sword", "sword"));
        assertFalse(PinyinSearch.matches("Diamond Sword", "diamonds"), "不是前缀/模糊匹配");
    }

    @Test
    @DisplayName("英文词首字母不可用：Diamond Sword 搜不到 DS")
    void englishInitialsNotSupported() {
        assertFalse(PinyinSearch.matches("Diamond Sword", "DS"));
        assertFalse(PinyinSearch.matches("Diamond Sword", "ds"));
    }

    @Test
    @DisplayName("多音字：单字的每个读音都参与匹配，但没有词级读音控制")
    void polyphoneUsesEveryReadingOfTheChar() {
        // 重 有 zhòng / chóng 两个读音，两个都能搜到「重庆」
        assertTrue(PinyinSearch.matches("重庆", "chongqing"), "重的另一个读音 chóng 可命中");
        assertTrue(PinyinSearch.matches("重庆", "zhongqing"), "重的默认读音 zhòng 可命中");
        // 中 有 zhōng / zhòng，带声调时按读音校验
        assertTrue(PinyinSearch.matches("中国", "zhong1国"));
        assertTrue(PinyinSearch.matches("中国", "zhong4国"), "zhòng 是中的另一个读音");
        // 但词级覆盖（v1.1 的 readingOverride）在 v1.0 不提供：
        // 无法表达「只在『重庆』里把重读成 chong、在『重要』里读成 zhong」
    }

    @Test
    @DisplayName("简繁不能互搜（字典不含映射），但繁体文本本身可以拼音搜")
    void simplifiedTraditionalDoNotCrossMatch() {
        assertFalse(PinyinSearch.matches("钻石剑", "鑽"), "简体文本搜不到繁体字");
        assertFalse(PinyinSearch.matches("钻石剑", "鑽石劍"));
        assertFalse(PinyinSearch.matches("鑽石劍", "钻石"), "繁体文本搜不到简体字");
        assertTrue(PinyinSearch.matches("鑽石劍", "zuanshijian"), "但繁体文本本身支持拼音");
        assertTrue(PinyinSearch.matches("鑽石劍", "zsj"));
    }

    @Test
    @DisplayName("声调：数字跟在完整音节后生效，跟在裸声母后不生效")
    void toneDigitsNeedAFullSyllable() {
        assertTrue(PinyinSearch.matches("铁砧", "tie3"), "铁 = tiě，第三声");
        assertFalse(PinyinSearch.matches("铁砧", "tie1"), "声调写错则不命中（声调是被校验的）");
        assertTrue(PinyinSearch.matches("铁砧", "zhen1"), "砧 = zhēn，第一声");
        assertTrue(PinyinSearch.matches("中国", "zhong1国"));
        assertFalse(PinyinSearch.matches("中国", "zh1国"), "裸声母 zh 后不接声调数字");
    }

    @Test
    @DisplayName("纯数字 query 不会被误当成声调串去匹配任意文本")
    void pureNumberQueryDoesNotBlowUp() {
        assertFalse(PinyinSearch.matches("铁砧", "1"));
        assertFalse(PinyinSearch.matches("铁砧", "3"));
        assertTrue(PinyinSearch.matches("钻石剑1", "1"), "原文里的数字仍按原文包含处理");
    }

    @Test
    @DisplayName("z 是 zh 的合法简拼首字母，与模糊音无关（文档表格此处有误）")
    void bareInitialZIsNotFuzzySound() {
        assertTrue(PinyinSearch.matches("中国", "z国"), "z 是 zhong 的首字母简拼");
        assertTrue(PinyinSearch.matches("中国", "zh国"));
        assertTrue(PinyinSearch.matches("钻石剑", "zsj"));
        assertFalse(PinyinSearch.matches("中国", "zong国"), "真正的模糊音变体默认仍然关闭");
    }

    @Test
    @DisplayName("字形辅助码不存在（PinIn 明确不支持），且永远不会有")
    void glyphCodeIsNotSupported() {
        // 「砧」的字形码（如五笔 zhen）不属于拼音能力范围，这里用几个典型字形码串确认不命中
        assertFalse(PinyinSearch.matches("铁砧", "sqfh"));
        assertFalse(PinyinSearch.matches("铁砧", "tiezhan"), "不同读音的字形码不命中");
    }
}
