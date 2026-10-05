package com.pinyinsearch;

import com.pinyinsearch.api.Matcher;
import com.pinyinsearch.api.PinyinSearch;
import com.pinyinsearch.api.Profile;
import com.pinyinsearch.impl.EngineCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 开发文档 §8.2 第 4 点：引擎实例缓存与 {@link Profile} 隔离。
 *
 * <ul>
 *   <li>同一个 {@code Profile} 连续走 {@code matches()} 与 {@code matcher()} <b>两条路径</b>，
 *       PinIn 只被构造一次；换一个新 {@code Profile} 才重新构造。</li>
 *   <li>两个不同 {@code Profile} 的 {@code Matcher} 交叉使用互不干扰
 *       （防「共享实例 + 就地 commit」式串扰）。</li>
 *   <li>缓存有容量上限，超出时淘汰最久未使用（§3.1 第 3 条）。</li>
 * </ul>
 */
class EngineCacheTest {

    @BeforeEach
    void resetCache() {
        EngineCache.clear();
    }

    @Test
    void bothPathsShareOneEnginePerProfile() {
        Profile profile = PinyinSearch.profile().allFuzzy(true).build();
        int before = EngineCache.constructionCount();

        // 路径 1：matches()
        assertTrue(PinyinSearch.matches("中国", "zong国", profile));
        assertEquals(before + 1, EngineCache.constructionCount(),
                "matches() 应该构造并缓存引擎（这条路径同样走缓存，容易漏测）");

        // 路径 2：matcher()（同一个 Profile）
        Matcher matcher = PinyinSearch.matcher(List.of("中国", "铁砧"), profile);
        assertTrue(matcher.search("zong国").contains("中国"));
        assertEquals(before + 1, EngineCache.constructionCount(), "同一个 Profile 不得重复构造引擎");

        // 反复调用也不得再构造
        for (int i = 0; i < 5; i++) {
            PinyinSearch.matches("中国", "tie", profile);
            matcher.search("tie");
        }
        assertEquals(before + 1, EngineCache.constructionCount(), "热路径上不得反复构造引擎");

        // 换一个 Profile 才会重新构造
        Profile other = profile.toBuilder().engine(Profile.Engine.TREE).build();
        assertTrue(PinyinSearch.matches("中国", "zong国", other));
        assertEquals(before + 2, EngineCache.constructionCount(), "新 Profile 才需要新引擎");
    }

    @Test
    void defaultProfileBuildsOnlyOneEngine() {
        int before = EngineCache.constructionCount();
        PinyinSearch.matches("铁砧", "tz");
        PinyinSearch.matches("钻石剑", "zsj");
        PinyinSearch.matcher(List.of("铁砧", "钻石剑")).search("tz");
        PinyinSearch.matches("中国", "zhongguo", PinyinSearch.defaultProfile());
        PinyinSearch.matches("中国", "zhongguo", PinyinSearch.profile().build());
        assertEquals(before + 1, EngineCache.constructionCount(),
                "所有「默认配置」的 Profile 必须命中同一个缓存键");
    }

    @Test
    void differentProfilesDoNotInterfere() {
        Profile fuzzy = PinyinSearch.profile().allFuzzy(true).build();
        Profile strict = PinyinSearch.profile().build();
        List<String> pool = List.of("中国", "铁砧");

        Matcher fuzzyMatcher = PinyinSearch.matcher(pool, fuzzy);
        Matcher strictMatcher = PinyinSearch.matcher(pool, strict);

        assertTrue(fuzzyMatcher.search("zong国").contains("中国"), "模糊音 Matcher 应命中 zong国");
        assertFalse(strictMatcher.search("zong国").contains("中国"), "关闭模糊音的 Matcher 不应命中 zong国");

        // 交叉、交替调用，确认没有「就地 commit」式串扰
        for (int i = 0; i < 3; i++) {
            assertFalse(strictMatcher.search("zong国").contains("中国"));
            assertTrue(fuzzyMatcher.search("zong国").contains("中国"));
            assertTrue(strictMatcher.search("zhong国").contains("中国"));
            assertTrue(fuzzyMatcher.search("zhong国").contains("中国"));
        }
    }

    @Test
    void cacheHasCapacityLimit() {
        int wanted = EngineCache.MAX_ENGINES + 6;
        // 用不同的模糊音组合造出 wanted 个互不相同的 Profile，并逐个走一遍匹配
        for (int i = 0; i < wanted; i++) {
            Profile p = PinyinSearch.profile()
                    .fuzzyZhZ((i & 1) != 0)
                    .fuzzyShS((i & 2) != 0)
                    .fuzzyChC((i & 4) != 0)
                    .fuzzyAngAn((i & 8) != 0)
                    .fuzzyIngIn((i & 16) != 0)
                    .fuzzyEngEn((i & 32) != 0)
                    .build();
            assertTrue(PinyinSearch.matches("中国", "zhongguo", p));
        }
        assertTrue(EngineCache.size() <= EngineCache.MAX_ENGINES,
                "缓存必须有容量上限，实测 size=" + EngineCache.size());
        assertTrue(EngineCache.size() > 0, "淘汰后仍应保留最近使用的引擎");
    }
}
