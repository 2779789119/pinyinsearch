package com.pinyinsearch.impl;

import com.pinyinsearch.api.Matcher;
import com.pinyinsearch.api.PinyinSearch;
import com.pinyinsearch.api.Profile;
import com.pinyinsearch.shaded.pinin.searchers.CachedSearcher;
import com.pinyinsearch.shaded.pinin.searchers.Searcher;
import com.pinyinsearch.shaded.pinin.searchers.SimpleSearcher;
import com.pinyinsearch.shaded.pinin.searchers.TreeSearcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

/**
 * {@link Matcher} 的拼音实现，支持 {@link Profile.Engine#LOOP}（默认）与三种索引引擎。
 *
 * <h2>等价性</h2>
 * <p>{@link Profile.Engine#LOOP} 就是「逐条调用 {@link PinyinSearch#matches}
 * 的同一个实现」，因此它与逐条 {@code matches} 的等价性<b>由构造保证</b>。</p>
 * <p>{@code SIMPLE / TREE / CACHED} 走 PinIn 的索引匹配，其与即时匹配是否逐条等价
 * <b>未经验证</b>，需要调用方自行验收。</p>
 *
 * <p>非公开 API（{@code impl} 包不对外承诺兼容性）。</p>
 */
public final class PininMatcher implements Matcher {

    private final List<String> pool;
    private final Profile profile;
    /** {@link Profile.Engine#LOOP} 时为 {@code null}。 */
    private final Searcher<Integer> index;
    /** {@code pool} 中 {@code null} 元素的下标：{@code matches(null, query)} 恒为 {@code true}，索引路径需要补上。 */
    private final int[] alwaysMatch;

    /**
     * @param pool    文本池；{@code null} 视为空列表
     * @param profile 非 {@code null} 的配置
     */
    public PininMatcher(List<String> pool, Profile profile) {
        this.pool = Collections.unmodifiableList(
                new ArrayList<>(pool == null ? Collections.emptyList() : pool));
        this.profile = profile;
        List<Integer> always = new ArrayList<>();
        this.index = buildIndex(this.pool, profile, always);
        this.alwaysMatch = new int[always.size()];
        for (int i = 0; i < always.size(); i++) {
            this.alwaysMatch[i] = always.get(i);
        }
    }

    private static Searcher<Integer> buildIndex(List<String> pool, Profile profile, List<Integer> always) {
        Searcher<Integer> searcher;
        switch (profile.engine()) {
            case TREE:
                searcher = new TreeSearcher<>(Searcher.Logic.CONTAIN, EngineCache.engine(profile));
                break;
            case CACHED:
                searcher = new CachedSearcher<>(Searcher.Logic.CONTAIN, EngineCache.engine(profile));
                break;
            case SIMPLE:
                searcher = new SimpleSearcher<>(Searcher.Logic.CONTAIN, EngineCache.engine(profile));
                break;
            case LOOP:
            default:
                return null;
        }
        for (int i = 0; i < pool.size(); i++) {
            String item = pool.get(i);
            if (item == null) {
                // matches(null, query) 恒为 true：索引里没法表达，单独记下来在结果里补
                always.add(i);
                continue;
            }
            String name = Normalizer.normalize(item);
            if (name.isEmpty()) {
                // 空串只在空查询时命中，而空查询在上层已短路，无需入索引
                continue;
            }
            searcher.put(name, i);
        }
        return searcher;
    }

    /** {@inheritDoc} */
    @Override
    public List<Integer> searchIndices(CharSequence query) {
        try {
            String q = Normalizer.normalize(query == null ? null : query.toString());
            if (q.isEmpty()) {
                return allIndices();
            }
            if (index == null) {
                List<Integer> ret = new ArrayList<>();
                for (int i = 0; i < pool.size(); i++) {
                    if (PinyinSearch.matches(pool.get(i), q, profile)) {
                        ret.add(i);
                    }
                }
                return ret;
            }
            TreeSet<Integer> ret = new TreeSet<>(index.search(q));
            for (int i : alwaysMatch) {
                ret.add(i);
            }
            return new ArrayList<>(ret);
        } catch (Throwable t) {
            return literalFallback(query);
        }
    }

    /** {@inheritDoc} */
    @Override
    public List<String> search(CharSequence query) {
        List<Integer> indices = searchIndices(query);
        List<String> ret = new ArrayList<>(indices.size());
        for (Integer i : indices) {
            ret.add(pool.get(i));
        }
        return ret;
    }

    private List<Integer> allIndices() {
        List<Integer> ret = new ArrayList<>(pool.size());
        for (int i = 0; i < pool.size(); i++) {
            ret.add(i);
        }
        return ret;
    }

    /** 引擎异常时的兜底：与 {@code PinyinSearch.matches} 的兜底保持同一套语义。 */
    private List<Integer> literalFallback(CharSequence query) {
        List<Integer> ret = new ArrayList<>();
        try {
            String q = Normalizer.normalize(query == null ? null : query.toString());
            if (q.isEmpty()) {
                return allIndices();
            }
            for (int i = 0; i < pool.size(); i++) {
                String item = pool.get(i);
                if (item == null || Normalizer.normalize(item).contains(q)) {
                    ret.add(i);
                }
            }
        } catch (Throwable ignored) {
            return new ArrayList<>();
        }
        return ret;
    }
}
