// VENDORED from PinIn 1.6.0 (https://github.com/Towdium/PinIn, MIT License).
// Modification: package relocated `me.towdium.pinin` -> `com.pinyinsearch.shaded.pinin`
// to avoid duplicate-class conflicts with JustEnoughCharacters. Logic untouched.
package com.pinyinsearch.shaded.pinin.searchers;

import com.pinyinsearch.shaded.pinin.PinIn;
import com.pinyinsearch.shaded.pinin.utils.Accelerator;

import java.util.List;

public interface Searcher<T> {
    void put(String name, T identifier);

    List<T> search(String name);

    PinIn context();

    enum Logic {
        BEGIN {
            @Override
            public boolean test(Accelerator a, int offset, int start) {
                return a.begins(offset, start);
            }

            @Override
            public boolean test(PinIn p, String s1, String s2) {
                return p.begins(s1, s2);
            }

            @Override
            public boolean raw(String s1, String s2) {
                return s1.startsWith(s2);
            }
        },

        CONTAIN {
            @Override
            public boolean test(Accelerator a, int offset, int start) {
                return a.contains(offset, start);
            }

            @Override
            public boolean test(PinIn p, String s1, String s2) {
                return p.contains(s1, s2);
            }

            @Override
            public boolean raw(String s1, String s2) {
                return s1.contains(s2);
            }
        },

        EQUAL {
            @Override
            public boolean test(Accelerator a, int offset, int start) {
                return a.matches(offset, start);
            }

            @Override
            public boolean test(PinIn p, String s1, String s2) {
                return p.matches(s1, s2);
            }

            @Override
            public boolean raw(String s1, String s2) {
                return s1.equals(s2);
            }
        };

        public boolean test(Accelerator a, int offset, int start) {
            return false;
        }

        public boolean test(PinIn p, String s1, String s2) {
            return false;
        }

        public boolean raw(String s1, String s2) {
            return false;
        }
    }
}
