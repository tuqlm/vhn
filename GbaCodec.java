package com.gbalocalizer.vn.core;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class GbaCodec {

    public enum EncodingMode {
        AUTO("Tu dong nhan dien"),
        GEN3("Pokemon Gen 3 / ROM Hack"),
        ASCII("GBA ASCII chuan"),
        CUSTOM_TBL("Bang ma tuy chinh (.tbl)");

        public final String displayName;

        EncodingMode(String displayName) {
            this.displayName = displayName;
        }
    }

    public static class TableMap {
        public final Map<Integer, String> byteToChar = new HashMap<>();
        public final Map<String, Integer> charToByte = new LinkedHashMap<>();
        public int endByte = 0x00;
    }

    public static class DecodeOutcome {
        public final String text;
        public final int consumedBytes;
        public final int alphaCount;
        public final int vowelCount;

        public DecodeOutcome(String text, int consumedBytes, int alphaCount, int vowelCount) {
            this.text = text;
            this.consumedBytes = consumedBytes;
            this.alphaCount = alphaCount;
            this.vowelCount = vowelCount;
        }
    }

    private static final Map<Integer, String> GEN3_DEC = new HashMap<>();
    private static final Map<Character, Integer> GEN3_ENC_SINGLE = new HashMap<>();
    private static final Map<String, Integer> GEN3_ENC_MULTI = new LinkedHashMap<>();
    private static final Map<Integer, Integer> FC_ARG_BYTES = new HashMap<>();
    public static final Set<String> KNOWN_GEN3_CODES = new HashSet<>();

    private static final Pattern STRIP_TAGS = Pattern.compile("\\{[^}]+\\}|\\\\[nlp]|\\[[^\\]]+\\]");
    private static final Pattern SPLIT_WORDS = Pattern.compile("[^A-Za-z]+");
    private static final Pattern MIXED_CASE_NOISE = Pattern.compile("[a-z][A-Z]");
    private static final Pattern REPEAT_NOISE = Pattern.compile("(.)\\1\\1\\1");

    static {
        KNOWN_GEN3_CODES.add("BPRE");
        KNOWN_GEN3_CODES.add("BPGE");
        KNOWN_GEN3_CODES.add("AXVE");
        KNOWN_GEN3_CODES.add("AXPE");
        KNOWN_GEN3_CODES.add("BPEE");

        GEN3_DEC.put(0x00, " ");
        GEN3_DEC.put(0x1B, "\u00E9");
        GEN3_DEC.put(0x2D, "&");
        GEN3_DEC.put(0x2E, "+");
        GEN3_DEC.put(0x34, "[Lv]");
        GEN3_DEC.put(0x35, "=");
        GEN3_DEC.put(0x36, ";");
        GEN3_DEC.put(0x53, "[PK]");
        GEN3_DEC.put(0x54, "[MN]");
        GEN3_DEC.put(0x5B, "%");
        GEN3_DEC.put(0x5C, "(");
        GEN3_DEC.put(0x5D, ")");
        GEN3_DEC.put(0x85, "<");
        GEN3_DEC.put(0x86, ">");
        for (int d = 0; d < 10; d++) {
            GEN3_DEC.put(0xA1 + d, String.valueOf(d));
            GEN3_ENC_SINGLE.put((char) ('0' + d), 0xA1 + d);
        }
        GEN3_DEC.put(0xAB, "!");
        GEN3_DEC.put(0xAC, "?");
        GEN3_DEC.put(0xAD, ".");
        GEN3_DEC.put(0xAE, "-");
        GEN3_DEC.put(0xAF, ".");
        GEN3_DEC.put(0xB0, "...");
        GEN3_DEC.put(0xB1, "\"");
        GEN3_DEC.put(0xB2, "\"");
        GEN3_DEC.put(0xB3, "'");
        GEN3_DEC.put(0xB4, "'");
        GEN3_DEC.put(0xB5, "[M]");
        GEN3_DEC.put(0xB6, "[F]");
        GEN3_DEC.put(0xB7, "$");
        GEN3_DEC.put(0xB8, ",");
        GEN3_DEC.put(0xB9, "x");
        GEN3_DEC.put(0xBA, "/");
        GEN3_DEC.put(0xF0, ":");
        GEN3_DEC.put(0xFA, "\\l");
        GEN3_DEC.put(0xFB, "\\p");
        GEN3_DEC.put(0xFE, "\\n");
        GEN3_DEC.put(0xFF, "");

        for (int i = 0; i < 26; i++) {
            char up = (char) ('A' + i);
            char lo = (char) ('a' + i);
            GEN3_DEC.put(0xBB + i, String.valueOf(up));
            GEN3_DEC.put(0xD5 + i, String.valueOf(lo));
            GEN3_ENC_SINGLE.put(up, 0xBB + i);
            GEN3_ENC_SINGLE.put(lo, 0xD5 + i);
        }

        GEN3_ENC_SINGLE.put(' ', 0x00);
        GEN3_ENC_SINGLE.put('\u00E9', 0x1B);
        GEN3_ENC_SINGLE.put('&', 0x2D);
        GEN3_ENC_SINGLE.put('+', 0x2E);
        GEN3_ENC_SINGLE.put('=', 0x35);
        GEN3_ENC_SINGLE.put(';', 0x36);
        GEN3_ENC_SINGLE.put('%', 0x5B);
        GEN3_ENC_SINGLE.put('(', 0x5C);
        GEN3_ENC_SINGLE.put(')', 0x5D);
        GEN3_ENC_SINGLE.put('<', 0x85);
        GEN3_ENC_SINGLE.put('>', 0x86);
        GEN3_ENC_SINGLE.put('!', 0xAB);
        GEN3_ENC_SINGLE.put('?', 0xAC);
        GEN3_ENC_SINGLE.put('.', 0xAD);
        GEN3_ENC_SINGLE.put('-', 0xAE);
        GEN3_ENC_SINGLE.put('"', 0xB1);
        GEN3_ENC_SINGLE.put('\'', 0xB4);
        GEN3_ENC_SINGLE.put('$', 0xB7);
        GEN3_ENC_SINGLE.put(',', 0xB8);
        GEN3_ENC_SINGLE.put('/', 0xBA);
        GEN3_ENC_SINGLE.put(':', 0xF0);
        GEN3_ENC_SINGLE.put('_', 0xAE);
        GEN3_ENC_SINGLE.put('*', 0xB9);

        GEN3_ENC_MULTI.put("\\l", 0xFA);
        GEN3_ENC_MULTI.put("\\p", 0xFB);
        GEN3_ENC_MULTI.put("\\n", 0xFE);
        GEN3_ENC_MULTI.put("[Lv]", 0x34);
        GEN3_ENC_MULTI.put("[PK]", 0x53);
        GEN3_ENC_MULTI.put("[MN]", 0x54);
        GEN3_ENC_MULTI.put("[M]", 0xB5);
        GEN3_ENC_MULTI.put("[F]", 0xB6);
        GEN3_ENC_MULTI.put("...", 0xB0);

        int[] fcSpec = {
            0x01, 1, 0x02, 1, 0x03, 1, 0x04, 3, 0x05, 1, 0x06, 1, 0x07, 0,
            0x08, 1, 0x09, 0, 0x0A, 0, 0x0B, 2, 0x0C, 1, 0x0D, 1, 0x0E, 1,
            0x0F, 0, 0x10, 2, 0x11, 1, 0x12, 1, 0x13, 1, 0x14, 1, 0x15, 0,
            0x16, 0, 0x17, 0, 0x18, 0
        };
        for (int i = 0; i < fcSpec.length; i += 2) {
            FC_ARG_BYTES.put(fcSpec[i], fcSpec[i + 1]);
        }
    }

    public static boolean isGen3Alpha(int b) {
        return (b >= 0xBB && b <= 0xEE) || b == 0x1B;
    }

    public static boolean canStartGen3(int b) {
        return b != 0xFF && b != 0x00 && (GEN3_DEC.containsKey(b) || b == 0xFC || b == 0xFD);
    }

    private static boolean isVowel(char c) {
        return "aeiouyAEIOUY".indexOf(c) >= 0;
    }

    public static TableMap parseTbl(String raw) {
        TableMap map = new TableMap();
        boolean explicitEnd = false;
        for (String line : raw.split("\\r?\\n")) {
            if (line == null || line.isEmpty()) continue;
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#") || t.startsWith(";")) continue;
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String hex = line.substring(0, eq).trim();
            String val = line.substring(eq + 1);
            if (hex.length() > 2) continue;
            try {
                int code = Integer.parseInt(hex, 16) & 0xFF;
                if (val.equals("[END]") || val.equals("{END}") || val.equals("\\0")) {
                    map.endByte = code;
                    explicitEnd = true;
                } else {
                    if (val.equals("[NL]")) val = "\\n";
                    map.byteToChar.put(code, val);
                    if (!map.charToByte.containsKey(val)) {
                        map.charToByte.put(val, code);
                    }
                }
            } catch (NumberFormatException ignored) {
            }
        }
        if (!explicitEnd) {
            map.endByte = ("A".equals(map.byteToChar.get(0xBB))) ? 0xFF : 0x00;
        }
        return map;
    }

    public static DecodeOutcome decode(byte[] rom, int offset, int maxLen, EncodingMode mode, TableMap tbl) {
        if (mode == EncodingMode.GEN3) return decodeGen3(rom, offset, maxLen);
        if (mode == EncodingMode.ASCII) return decodeAscii(rom, offset, maxLen);
        return decodeTbl(rom, offset, maxLen, tbl);
    }

    private static DecodeOutcome decodeGen3(byte[] rom, int offset, int maxLen) {
        int i = offset;
        int end = Math.min(rom.length, offset + maxLen);
        StringBuilder sb = new StringBuilder();
        int alpha = 0;
        int vowels = 0;

        while (i < end) {
            int b = rom[i] & 0xFF;
            if (b == 0xFF) {
                return new DecodeOutcome(sb.toString(), i - offset + 1, alpha, vowels);
            } else if (b == 0xFC) {
                if (i + 1 >= end) return null;
                Integer argLen = FC_ARG_BYTES.get(rom[i + 1] & 0xFF);
                if (argLen == null || i + 2 + argLen > end) return null;
                sb.append("{FC:");
                for (int k = 1; k < 2 + argLen; k++) {
                    sb.append(String.format("%02X", rom[i + k] & 0xFF));
                }
                sb.append('}');
                i += 2 + argLen;
            } else if (b == 0xFD) {
                if (i + 1 >= end) return null;
                int v = rom[i + 1] & 0xFF;
                if (v > 0x20) return null;
                sb.append(String.format("{FD:%02X}", v));
                i += 2;
            } else {
                String ch = GEN3_DEC.get(b);
                if (ch == null) return null;
                sb.append(ch);
                if (b >= 0xBB && b <= 0xEE) {
                    alpha++;
                    if (isVowel(ch.charAt(0))) vowels++;
                }
                i++;
            }
        }
        return null;
    }

    private static DecodeOutcome decodeAscii(byte[] rom, int offset, int maxLen) {
        int i = offset;
        int end = Math.min(rom.length, offset + maxLen);
        StringBuilder sb = new StringBuilder();
        int alpha = 0;
        int vowels = 0;

        while (i < end) {
            int b = rom[i] & 0xFF;
            if (b == 0x00) {
                return new DecodeOutcome(sb.toString(), i - offset + 1, alpha, vowels);
            } else if (b == 0x0A) {
                sb.append("\\n");
                i++;
            } else if (b == 0x0D) {
                i++;
            } else if (b >= 0x20 && b <= 0x7E) {
                char c = (char) b;
                sb.append(c);
                if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) {
                    alpha++;
                    if (isVowel(c)) vowels++;
                }
                i++;
            } else {
                return null;
            }
        }
        return null;
    }

    private static DecodeOutcome decodeTbl(byte[] rom, int offset, int maxLen, TableMap tbl) {
        if (tbl == null) return null;
        int i = offset;
        int end = Math.min(rom.length, offset + maxLen);
        StringBuilder sb = new StringBuilder();
        int alpha = 0;
        int vowels = 0;

        while (i < end) {
            int b = rom[i] & 0xFF;
            if (b == tbl.endByte) {
                return new DecodeOutcome(sb.toString(), i - offset + 1, alpha, vowels);
            }
            String s = tbl.byteToChar.get(b);
            if (s == null) return null;
            sb.append(s);
            if (s.length() == 1) {
                char c = s.charAt(0);
                if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) {
                    alpha++;
                    if (isVowel(c)) vowels++;
                }
            }
            i++;
        }
        return null;
    }

    public static boolean isNaturalLanguage(DecodeOutcome dec) {
        if (dec == null || dec.consumedBytes < 5 || dec.alphaCount < 3 || dec.vowelCount < 1) {
            return false;
        }
        if (dec.text.contains("    ")) return false;
        String clean = STRIP_TAGS.matcher(dec.text).replaceAll(" ").trim();
        if (clean.length() < 4) return false;
        if (((double) dec.alphaCount / clean.length()) < 0.45) return false;
        if (dec.alphaCount >= 8 && ((double) dec.vowelCount / dec.alphaCount) < 0.18) return false;

        int totalWords = 0;
        int noisyWords = 0;
        for (String w : SPLIT_WORDS.split(clean)) {
            if (w.length() < 2) continue;
            totalWords++;
            int v = 0;
            for (int k = 0; k < w.length(); k++) {
                if (isVowel(w.charAt(k))) v++;
            }
            if (MIXED_CASE_NOISE.matcher(w).find() && !w.equals("PokeNav") && !w.equals("PokéNav")) {
                noisyWords++;
            } else if (REPEAT_NOISE.matcher(w.toLowerCase()).find()) {
                noisyWords++;
            } else if (w.length() >= 3 && v == 0) {
                noisyWords++;
            }
        }
        return totalWords > 0 && (noisyWords == 0 || noisyWords < totalWords * 0.4);
    }

    public static byte[] encode(String text, EncodingMode mode, TableMap tbl) {
        if (mode == EncodingMode.GEN3) return encodeGen3(text);
        if (mode == EncodingMode.CUSTOM_TBL && tbl != null) return encodeTbl(text, tbl);
        return encodeAscii(text);
    }

    private static byte[] encodeGen3(String text) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int i = 0;
        int n = text.length();
        boolean openQuote = false;
        while (i < n) {
            if (text.charAt(i) == '{') {
                int close = text.indexOf('}', i);
                if (close != -1) {
                    String tag = text.substring(i + 1, close);
                    if (tag.startsWith("FC:") || tag.startsWith("FD:")) {
                        byte[] raw = hexToBytes(tag.substring(3));
                        if (raw != null) {
                            bos.write(tag.startsWith("FC:") ? 0xFC : 0xFD);
                            bos.write(raw, 0, raw.length);
                            i = close + 1;
                            continue;
                        }
                    }
                }
            }
            boolean matchedMulti = false;
            for (Map.Entry<String, Integer> e : GEN3_ENC_MULTI.entrySet()) {
                if (text.startsWith(e.getKey(), i)) {
                    bos.write(e.getValue());
                    i += e.getKey().length();
                    matchedMulti = true;
                    break;
                }
            }
            if (matchedMulti) continue;

            char c = text.charAt(i);
            if (c == '"') {
                bos.write(openQuote ? 0xB2 : 0xB1);
                openQuote = !openQuote;
            } else {
                Integer code = GEN3_ENC_SINGLE.get(c);
                if (code != null) {
                    bos.write(code);
                } else if (c == '\n' || c == '\r' || c == '\t') {
                    bos.write(0x00);
                }
            }
            i++;
        }
        bos.write(0xFF);
        return bos.toByteArray();
    }

    private static byte[] encodeAscii(String text) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int i = 0;
        while (i < text.length()) {
            if (text.startsWith("\\n", i) || text.startsWith("\\l", i) || text.startsWith("\\p", i)) {
                bos.write(0x0A);
                i += 2;
                continue;
            }
            char c = text.charAt(i);
            if (c >= 0x20 && c <= 0x7E) bos.write(c);
            else if (c == '\n') bos.write(0x0A);
            i++;
        }
        bos.write(0x00);
        return bos.toByteArray();
    }

    private static byte[] encodeTbl(String text, TableMap tbl) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int i = 0;
        while (i < text.length()) {
            if (text.startsWith("\\n", i) && tbl.charToByte.containsKey("\\n")) {
                bos.write(tbl.charToByte.get("\\n"));
                i += 2;
                continue;
            }
            Integer code = tbl.charToByte.get(String.valueOf(text.charAt(i)));
            if (code != null) bos.write(code);
            i++;
        }
        bos.write(tbl.endByte);
        return bos.toByteArray();
    }

    private static byte[] hexToBytes(String hex) {
        if (hex == null || hex.isEmpty() || (hex.length() % 2) != 0) return null;
        byte[] out = new byte[hex.length() / 2];
        try {
            for (int i = 0; i < out.length; i++) {
                out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
            }
            return out;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
