// VENDORED from PinIn 1.6.0 (https://github.com/Towdium/PinIn, MIT License).
// Modification: package relocated `me.towdium.pinin` -> `com.pinyinsearch.shaded.pinin`
// to avoid duplicate-class conflicts with JustEnoughCharacters. Logic untouched.
package com.pinyinsearch.shaded.pinin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.BiConsumer;

@FunctionalInterface
public interface DictLoader {
    void load(BiConsumer<Character, String[]> feed);

    class Default implements DictLoader {
        @Override
        public void load(BiConsumer<Character, String[]> feed) {
            InputStream is = PinIn.class.getResourceAsStream("data.txt");
            InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
            BufferedReader br = new BufferedReader(isr);
            try {
                String line;
                while ((line = br.readLine()) != null) {
                    char ch = line.charAt(0);
                    String[] records = line.substring(3).split(", ");
                    feed.accept(ch, records);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
