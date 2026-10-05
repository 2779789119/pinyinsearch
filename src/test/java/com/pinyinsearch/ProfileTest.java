package com.pinyinsearch;

import com.pinyinsearch.api.PinyinSearch;
import com.pinyinsearch.api.Profile;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 开发文档 §8.1「自定义 Profile」用例 + {@link Profile} 值对象性质。
 */
class ProfileTest {

    @Test
    void defaultProfileValues() {
        Profile p = PinyinSearch.defaultProfile();
        assertEquals(Profile.Scheme.QUANPIN, p.scheme());
        assertEquals(Profile.Engine.LOOP, p.engine(), "默认引擎必须是 LOOP：等价性由构造保证");
        assertFalse(p.fuzzyZhZ());
        assertFalse(p.fuzzyShS());
        assertFalse(p.fuzzyChC());
        assertFalse(p.fuzzyAngAn());
        assertFalse(p.fuzzyIngIn());
        assertFalse(p.fuzzyEngEn());
        assertFalse(p.fuzzyUV());
        assertTrue(p.accelerate(), "accelerate 默认开");
    }

    @Test
    void defaultProfileIsSharedInstance() {
        assertSame(PinyinSearch.defaultProfile(), PinyinSearch.defaultProfile());
        assertEquals(PinyinSearch.defaultProfile(), PinyinSearch.profile().build(),
                "按默认值构建出的 Profile 必须与 defaultProfile 相等（否则缓存无法命中）");
    }

    @Test
    void allFuzzyFlipsBothDirections() {
        Profile fuzzy = PinyinSearch.profile().allFuzzy(true).build();
        for (String q : new String[]{"zong国", "z国"}) {
            assertTrue(PinyinSearch.matches("中国", q, fuzzy), "开模糊音后应命中：" + q);
        }
        assertFalse(PinyinSearch.matches("中国", "zong国"), "默认 Profile 下不应命中：zong国");
        assertTrue(PinyinSearch.matches("中国", "zhongguo", fuzzy), "开模糊音不改变既有命中");
    }

    @Test
    void shuangpinChangesQueryParsing() {
        Profile xiaohe = PinyinSearch.profile().scheme(Profile.Scheme.SHUANGPIN_XIAOHE).build();
        Profile ziranma = PinyinSearch.profile().scheme(Profile.Scheme.SHUANGPIN_ZIRANMA).build();
        assertTrue(PinyinSearch.matches("中国", "vsgo", xiaohe), "小鹤：中国 = vsgo");
        assertTrue(PinyinSearch.matches("中国", "vsgo", ziranma), "自然码：中国 = vsgo");
        assertTrue(PinyinSearch.matches("中国", "vs", xiaohe), "小鹤：声母也走双拼键位");
        assertFalse(PinyinSearch.matches("中国", "zhongguo", xiaohe), "双拼模式下全拼串不再命中");
    }

    @Test
    void zhuyinChangesQueryParsing() {
        Profile dachien = PinyinSearch.profile().scheme(Profile.Scheme.ZHUYIN_DACHEN).build();
        assertTrue(PinyinSearch.matches("中国", "5j/eji", dachien), "大千注音：中国 = 5j/eji");
        assertTrue(PinyinSearch.matches("中国", "5j/", dachien), "大千注音：声母也走注音键位");
        assertFalse(PinyinSearch.matches("中国", "zhongguo", dachien), "注音模式下全拼串不再命中");
    }

    @Test
    void accelerateOnlyAffectsPerformance() {
        Profile on = PinyinSearch.profile().accelerate(true).build();
        Profile off = PinyinSearch.profile().accelerate(false).build();
        List<String> texts = List.of("铁砧", "钻石剑", "中国", "Diamond Sword", "僵尸", "下界合金剑", "铁锭");
        List<String> queries = List.of("tz", "zsj", "zh国", "tiezhen", "zombie", "xjj", "zhongguo", "diamond", "");
        for (String t : texts) {
            for (String q : queries) {
                assertEquals(PinyinSearch.matches(t, q, off), PinyinSearch.matches(t, q, on),
                        "accelerate 只影响性能，不影响语义：" + t + " | " + q);
            }
        }
    }

    @Test
    void equalsAndHashCodeCoverEveryOption() {
        Profile base = PinyinSearch.profile().build();
        assertEquals(base, PinyinSearch.profile().build());
        assertEquals(base.hashCode(), PinyinSearch.profile().build().hashCode());

        List<Profile> variants = new ArrayList<>();
        variants.add(PinyinSearch.profile().scheme(Profile.Scheme.SHUANGPIN_XIAOHE).build());
        variants.add(PinyinSearch.profile().engine(Profile.Engine.TREE).build());
        variants.add(PinyinSearch.profile().fuzzyZhZ(true).build());
        variants.add(PinyinSearch.profile().fuzzyShS(true).build());
        variants.add(PinyinSearch.profile().fuzzyChC(true).build());
        variants.add(PinyinSearch.profile().fuzzyAngAn(true).build());
        variants.add(PinyinSearch.profile().fuzzyIngIn(true).build());
        variants.add(PinyinSearch.profile().fuzzyEngEn(true).build());
        variants.add(PinyinSearch.profile().fuzzyUV(true).build());
        variants.add(PinyinSearch.profile().accelerate(false).build());
        for (Profile v : variants) {
            assertNotEquals(base, v, "每个配置项都必须参与 equals：" + v);
        }
    }

    @Test
    void toBuilderRoundTrips() {
        Profile original = PinyinSearch.profile()
                .scheme(Profile.Scheme.SHUANGPIN_XIAOHE)
                .engine(Profile.Engine.TREE)
                .allFuzzy(true)
                .accelerate(false)
                .build();
        assertEquals(original, original.toBuilder().build());
        Profile derived = original.toBuilder().engine(Profile.Engine.LOOP).build();
        assertEquals(Profile.Engine.LOOP, derived.engine());
        assertEquals(Profile.Engine.TREE, original.engine(), "派生不得改动原对象（不可变）");
    }

    @Test
    void builderToleratesNullEnums() {
        Profile p = PinyinSearch.profile()
                .scheme(null)
                .engine(null)
                .build();
        assertEquals(Profile.Scheme.QUANPIN, p.scheme());
        assertEquals(Profile.Engine.LOOP, p.engine());
    }

    @Test
    void nullProfileFallsBackToDefault() {
        assertTrue(PinyinSearch.matches("铁砧", "tz", null), "null Profile 退化为默认 Profile，不抛异常");
        assertEquals(PinyinSearch.matcher(List.of("铁砧")).search("tz"),
                PinyinSearch.matcher(List.of("铁砧"), null).search("tz"));
    }
}
