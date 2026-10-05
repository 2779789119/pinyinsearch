package com.pinyinsearch;

import com.pinyinsearch.api.Matcher;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link Matcher#literal(List)}：不依赖拼音的纯原文包含实现，接口形状与拼音版一致。
 *
 * <p>典型用途：调用方想在代码里统一按 {@code Matcher} 抽象处理搜索
 * （例如自己有「关闭拼音搜索」的开关），可以直接换成这个实现。</p>
 */
class LiteralMatcherTest {

    private static final List<String> POOL = Arrays.asList(
            "铁砧", "钻石剑", "Diamond Sword", null, "", "钻石");

    @Test
    void containsInsteadOfPinyin() {
        Matcher matcher = Matcher.literal(POOL);
        assertEquals(List.of(1, 5), matcher.searchIndices("钻石"), "只做原文包含");
        assertEquals(Collections.emptyList(), matcher.searchIndices("zsj"), "literal 不做拼音");
        assertEquals(Collections.emptyList(), matcher.searchIndices("tiezhen"), "literal 不做拼音");
    }

    @Test
    void isCaseInsensitiveAndNormalized() {
        Matcher matcher = Matcher.literal(POOL);
        assertEquals(List.of(2), matcher.searchIndices("diamond"), "大小写不敏感");
        assertEquals(List.of(2), matcher.searchIndices("ＤＩＡＭＯＮＤ"), "全角 + 大写同样归一化");
        assertEquals(List.of(2), matcher.searchIndices("  sword  "), "首尾空白被去掉");
    }

    @Test
    void nullAndEmptySemanticsMatchPinyinVersion() {
        Matcher matcher = Matcher.literal(POOL);
        assertEquals(POOL.size(), matcher.searchIndices(null).size());
        assertEquals(POOL.size(), matcher.searchIndices("").size());
        assertEquals(List.of(0, 1, 2, 3, 4, 5), matcher.searchIndices(" "));
    }

    @Test
    void poolIsCopiedAndNullSafe() {
        List<String> mutable = new ArrayList<>(List.of("钻石剑"));
        Matcher matcher = Matcher.literal(mutable);
        mutable.clear();
        assertEquals(List.of("钻石剑"), matcher.search("钻石"));

        assertEquals(Collections.emptyList(), Matcher.literal(null).search("钻石"));
        assertEquals(Collections.emptyList(), Matcher.literal(null).searchIndices("钻石"));
    }
}
