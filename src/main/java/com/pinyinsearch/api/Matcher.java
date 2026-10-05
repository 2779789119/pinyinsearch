package com.pinyinsearch.api;

import com.pinyinsearch.impl.Normalizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 针对一个<b>固定文本池</b>的匹配器：建好之后可以用任意 query 反复搜索。
 *
 * <p>通过 {@link PinyinSearch#matcher(List)} / {@link PinyinSearch#matcher(List, Profile)} 创建
 * （拼音实现），或用 {@link #literal(List)} 创建<b>纯原文包含</b>实现。</p>
 *
 * <h2>★ 为什么入参是 {@code List} 而不是 {@code Collection}</h2>
 * <p>{@link #searchIndices(CharSequence)} 返回的是<b>下标</b>，而下标只有在文本池是
 * <b>稳定有序列表</b>时才有意义：传 {@code HashSet} 之类无序集合时，迭代顺序不保证稳定，
 * 下标无法可靠映射回元素，两次调用之间甚至可能对不上。因此这里要求 {@code List}。</p>
 *
 * <h2>线程安全</h2>
 * <p><b>本接口的实现不保证线程安全，必须在客户端主线程使用。</b>
 * 底层 PinIn 的缓存会随查询惰性增长，并发访问不安全。</p>
 *
 * <p>例外：索引构建完成后是只读的，因此「异步构建 → 构建完成后交回主线程使用」是安全的用法
 * （构造 {@code Matcher} 的那一步即可放在异步线程）。</p>
 *
 * <h2>异常</h2>
 * <p>实现<b>不抛异常</b>：任何内部 {@link Throwable} 都会退化为「原文包含」语义。</p>
 */
public interface Matcher {

    /**
     * 返回命中文本池中的哪些项（<b>下标</b>），按文本池的迭代顺序<b>升序</b>排列，不去重（同一元素只会出现一次）。
     *
     * <p>下标语义依赖文本池是稳定有序列表，见类型注释。</p>
     *
     * <p>大文本池下 {@code List<Integer>} 有装箱开销，换来的是 API 简洁；
     * 性能敏感场景请自行用 {@link PinyinSearch#matches(CharSequence, CharSequence, Profile)} 遍历。</p>
     *
     * <p>行为保证：{@code query} 为 {@code null}／空串／全空白 → 返回<b>全部下标</b>
     * （与「空查询命中一切」的语义一致）；本方法不抛异常。</p>
     *
     * @param query 用户输入；{@code null} 视为空查询
     * @return 命中的下标列表，升序；从不为 {@code null}
     */
    List<Integer> searchIndices(CharSequence query);

    /**
     * 便捷方法：直接拿到命中的文本（保留文本池顺序与重复项，不去重）。
     *
     * <p>等价于对 {@link #searchIndices(CharSequence)} 的结果做一次下标映射。</p>
     *
     * @param query 用户输入；{@code null} 视为空查询
     * @return 命中的文本列表，顺序与 {@link #searchIndices(CharSequence)} 一致；从不为 {@code null}
     */
    List<String> search(CharSequence query);

    /**
     * <b>不依赖拼音的纯原文包含实现</b>（只做「归一化后大小写不敏感的子串包含」），接口形状与拼音版一致。
     *
     * <p>用途：调用方想在代码里<b>统一按 {@code Matcher} 抽象</b>处理搜索
     * （例如自己有「关闭拼音搜索」的开关，或某个文本池不值得建索引），可以直接换成这个实现，
     * 不必自己再写一套。</p>
     *
     * <p>⚠️ <b>它不能用来兜「本模组整体没装」的情况</b> —— 那种情况下连 {@code Matcher} 这个类都加载不了
     * （{@code NoClassDefFoundError}）。「没装则行为不变」的回退必须由调用方自己写。</p>
     *
     * @param pool 文本池；{@code null} 视为空列表；其中的 {@code null} 元素视为空串。
     *             实现内部会拷贝一份，之后对传入列表的修改不会影响本对象
     * @return 纯原文包含实现
     */
    static Matcher literal(List<String> pool) {
        return new Literal(pool);
    }

    /**
     * {@link Matcher#literal(List)} 的实现：只做归一化 + 大小写不敏感子串包含，不含任何拼音逻辑。
     *
     * <p>这是公开类型只是为了让它能被 {@code Matcher} 的静态方法引用；
     * 请通过 {@link Matcher#literal(List)} 获取实例，不要直接使用。</p>
     */
    final class Literal implements Matcher {
        private final List<String> pool;

        /**
         * @param pool 文本池；{@code null} 视为空列表
         */
        public Literal(List<String> pool) {
            this.pool = Collections.unmodifiableList(
                    new ArrayList<>(pool == null ? Collections.emptyList() : pool));
        }

        /** {@inheritDoc} */
        @Override
        public List<Integer> searchIndices(CharSequence query) {
            try {
                String q = Normalizer.normalize(query == null ? null : query.toString());
                List<Integer> ret = new ArrayList<>();
                if (q.isEmpty()) {
                    for (int i = 0; i < pool.size(); i++) {
                        ret.add(i);
                    }
                    return ret;
                }
                for (int i = 0; i < pool.size(); i++) {
                    String item = pool.get(i);
                    if (Normalizer.normalize(item).contains(q)) {
                        ret.add(i);
                    }
                }
                return ret;
            } catch (Throwable ignored) {
                return new ArrayList<>();
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
    }
}
