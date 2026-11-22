package com.gbalocalizer.vn.core;

import java.util.ArrayList;
import java.util.List;

public class ScriptItem {
    public int index;
    public int romOffset;
    public int originalByteLength;
    public boolean isMultiline;
    public String sourceText = "";
    public String translatedText = "";
    public final List<Integer> pointerAddresses = new ArrayList<>();

    public boolean isTranslated() {
        return translatedText != null && !translatedText.trim().isEmpty();
    }

    public boolean hasControlVars() {
        return sourceText != null && (sourceText.contains("{FD:") || sourceText.contains("{FC:"));
    }
}
