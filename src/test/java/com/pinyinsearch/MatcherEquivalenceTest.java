package com.pinyinsearch;

import com.pinyinsearch.api.Matcher;
import com.pinyinsearch.api.PinyinSearch;
import com.pinyinsearch.api.Profile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 开发文档 §4.1「★ 硬性等价要求」+ §8.1 的 {@link Matcher} 验收：
 * 对同一 pool / query / Profile，
 * {@code matcher(pool, p).searchIndices(query)} 必须与「逐条 {@code matches} 为 true 的下标集合」完全相等。
 *
 * <p>（实测结论：PinIn 1.6.0 上 {@code LOOP / SIMPLE / TREE / CACHED} 四个引擎在本用例集上全部等价，
 * 但按开发文档要求，索引引擎仍属「显式选择、需自行验收」的优化。）</p>
 */
class MatcherEquivalenceTest {

    private static final List<String> POOL = Collections.unmodifiableList(Arrays.asList(
            "铁砧", "钻石剑", "中国", "Diamond Sword", "僵尸", "钻石", "铁锭",
            null, "", "下界合金剑", "铁砧", "钻石剑"));

    private static final List<String> QUERIES = Arrays.asList(
            "tz", "zsj", "zh国", "zhong1国", "zh1国", "zong国", "z国", "tiezhen",
            "diamond", "DS", "", " ", null, "!!!", "铁", "zi", "guo");

    @ParameterizedTest(name = "engine={0}：Matcher 与逐条 matches 等价")
    @EnumSource(Profile.Engine.class)
    void matcherIsEquivalentToPerItemMatches(Profile.Engine engine) {
        Profile profile = PinyinSearch.profile().engine(engine).build();
        Matcher matcher = PinyinSearch.matcher(POOL, profile);
        for (String query : QUERIES) {
            assertEquals(expected(profile, query), matcher.searchIndices(query),
                    "引擎 " + engine + " 与逐条 matches 不等价：query=" + query);
        }
    }

    @ParameterizedTest(name = "engine={0}：开模糊音后依然等价")
    @EnumSource(Profile.Engine.class)
    void matcherIsEquivalentWithFuzzyEnabled(Profile.Engine engine) {
        Profile profile = PinyinSearch.profile().allFuzzy(true).engine(engine).build();
        Matcher matcher = PinyinSearch.matcher(POOL, profile);
        for (String query : QUERIES) {
            assertEquals(expected(profile, query), matcher.searchIndices(query),
                    "模糊音 + 引擎 " + engine + " 不等价：query=" + query);
        }
    }

    @ParameterizedTest(name = "engine={0}：双拼/注音方案下依然等价")
    @EnumSource(Profile.Engine.class)
    void matcherIsEquivalentWithOtherSchemes(Profile.Engine engine) {
        for (Profile.Scheme scheme : Profile.Scheme.values()) {
            Profile profile = PinyinSearch.profile().scheme(scheme).engine(engine).build();
            Matcher matcher = PinyinSearch.matcher(POOL, profile);
            for (String query : Arrays.asList("vsgo", "5j/eji", "zhongguo", "zsj", "tiezhen")) {
                assertEquals(expected(profile, query), matcher.searchIndices(query),
                        "方案 " + scheme + " + 引擎 " + engine + " 不等价：query=" + query);
            }
        }
    }

    private static List<Integer> expected(Profile profile, String query) {
        List<Integer> ret = new ArrayList<>();
        for (int i = 0; i < POOL.size(); i++) {
            if (PinyinSearch.matches(POOL.get(i), query, profile)) {
                ret.add(i);
            }
        }
        return ret;
    }

    @Test
    void indicesAreAscending() {
        Matcher matcher = PinyinSearch.matcher(POOL);
        List<Integer> indices = matcher.searchIndices("z");
        List<Integer> sorted = new ArrayList<>(indices);
        Collections.sort(sorted);
        assertEquals(sorted, indices, "下标必须按 pool 迭代顺序升序");
    }

    @Test
    void emptyQueryReturnsEveryIndex() {
        Matcher matcher = PinyinSearch.matcher(POOL);
        for (String query : new String[]{null, "", " ", "　"}) {
            List<Integer> indices = matcher.searchIndices(query);
            assertEquals(POOL.size(), indices.size(), "空查询应返回全部下标（query=" + query + "）");
        }
    }

    @Test
    void searchKeepsPoolOrderAndDuplicates() {
        Matcher matcher = PinyinSearch.matcher(List.of("铁砧", "钻石剑", "铁砧", "钻石剑"));
        assertEquals(List.of("铁砧", "铁砧"), matcher.search("tiezhen"), "保留下标顺序与重复项，不去重");
        assertEquals(List.of("钻石剑", "钻石剑"), matcher.search("zsj"));
        assertNotNull(matcher.search(null));
        assertEquals(List.of("铁砧", "钻石剑", "铁砧", "钻石剑"), matcher.search(""));
    }

    @Test
    void nullItemAlwaysMatches() {
        // matches(null, query) == true（§4.1），因此 pool 里的 null 元素对任意 query 都命中，
        // 且结果里原样保留 null
        Matcher matcher = PinyinSearch.matcher(POOL);
        assertEquals(Arrays.asList("铁砧", null, "铁砧"), matcher.search("tiezhen"));
        assertEquals(Arrays.asList("钻石剑", null, "钻石剑"), matcher.search("zsj"));
        assertEquals(POOL, matcher.search(""));
    }

    @Test
    void poolIsCopiedAndNullSafe() {
        List<String> mutable = new ArrayList<>(Arrays.asList("铁砧", "钻石剑"));
        Matcher matcher = PinyinSearch.matcher(mutable);
        mutable.clear();
        assertEquals(List.of("铁砧"), matcher.search("tz"), "Matcher 内部必须拷贝 pool，不受外部修改影响");

        assertEquals(Collections.emptyList(), PinyinSearch.matcher(null).search("tz"), "null pool 视为空列表");
        assertEquals(Collections.emptyList(), PinyinSearch.matcher(null).searchIndices("tz"));
        assertEquals(Collections.emptyList(), PinyinSearch.matcher(Collections.<String>emptyList()).searchIndices("tz"));
    }

    @Test
    void matcherNeverThrows() {
        Matcher matcher = PinyinSearch.matcher(POOL);
        for (String query : QUERIES) {
            assertTrue(matcher.searchIndices(query) != null);
            assertTrue(matcher.search(query) != null);
        }
    }
}
