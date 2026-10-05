// VENDORED from PinIn 1.6.0 (https://github.com/Towdium/PinIn, MIT License).
// Modification: package relocated `me.towdium.pinin` -> `com.pinyinsearch.shaded.pinin`
// to avoid duplicate-class conflicts with JustEnoughCharacters. Logic untouched.
package com.pinyinsearch.shaded.pinin.elements;

import com.pinyinsearch.shaded.pinin.utils.IndexSet;

/**
 * Author: Towdium
 * Date: 21/04/19
 */
public interface Element {
    IndexSet match(String str, int start, boolean partial);
}
