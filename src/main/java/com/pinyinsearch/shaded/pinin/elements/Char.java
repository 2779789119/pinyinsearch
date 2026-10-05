// VENDORED from PinIn 1.6.0 (https://github.com/Towdium/PinIn, MIT License).
// Modification: package relocated `me.towdium.pinin` -> `com.pinyinsearch.shaded.pinin`
// to avoid duplicate-class conflicts with JustEnoughCharacters. Logic untouched.
package com.pinyinsearch.shaded.pinin.elements;

import com.pinyinsearch.shaded.pinin.utils.IndexSet;

public class Char implements Element {
    protected char ch;
    public static final Pinyin[] NONE = new Pinyin[0];

    Pinyin[] pinyin;

    public Char(char ch, Pinyin[] pinyin) {
        this.ch = ch;
        this.pinyin = pinyin;
    }

    @Override
    public IndexSet match(String str, int start, boolean partial) {
        IndexSet ret = (str.charAt(start) == ch ? IndexSet.ONE : IndexSet.NONE).copy();
        for (Element p : pinyin) ret.merge(p.match(str, start, partial));
        return ret;
    }

    public char get() {
        return ch;
    }

    public Pinyin[] pinyins() {
        return pinyin;
    }

    public static class Dummy extends Char {
        public Dummy() {
            super('\0', NONE);
        }

        public void set(char ch) {
            this.ch = ch;
        }
    }
}
