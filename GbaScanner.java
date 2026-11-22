package com.gbalocalizer.vn.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GbaScanner {

    public static class RomMetadata {
        public String title = "UNKNOWN";
        public String gameCode = "????";
        public int totalBytes;
        public int freeFF;
        public int free00;
        public boolean isGen3Header;
    }

    public static class ScanOutput {
        public GbaCodec.EncodingMode detectedMode;
        public final List<ScriptItem> items = new ArrayList<>();
        public int pointerTargetCount;
    }

    public static class FreeRegion {
        public int start;
        public int end;

        public FreeRegion(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    public static RomMetadata analyzeRom(byte[] rom) {
        RomMetadata meta = new RomMetadata();
        meta.totalBytes = rom.length;
        if (rom.length >= 0xB2) {
            meta.title = asciiSlice(rom, 0xA0, 12);
            meta.gameCode = asciiSlice(rom, 0xAC, 4);
        }
        meta.isGen3Header = GbaCodec.KNOWN_GEN3_CODES.contains(meta.gameCode);
        meta.freeFF = sumBytes(findFreeRegions(rom, (byte) 0xFF, 4096, 64));
        meta.free00 = sumBytes(findFreeRegions(rom, (byte) 0x00, 4096, 64));
        return meta;
    }

    private static String asciiSlice(byte[] data, int start, int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len && start + i < data.length; i++) {
            int b = data[start + i] & 0xFF;
            if (b >= 32 && b <= 126) sb.append((char) b);
        }
        return sb.toString().trim();
    }

    public static List<FreeRegion> findFreeRegions(byte[] rom, byte fill, int minSize, int padMargin) {
        List<FreeRegion> list = new ArrayList<>();
        int n = rom.length;
        int i = 0;
        while (i < n) {
            if (rom[i] == fill) {
                int j = i;
                while (j < n && rom[j] == fill) j++;
                if (j - i >= minSize) {
                    int s = i + padMargin;
                    int rem = s % 4;
                    if (rem != 0) s += (4 - rem);
                    int e = j - padMargin;
                    if (e > s) list.add(new FreeRegion(s, e));
                }
                i = j;
            } else {
                i++;
            }
        }
        list.sort((a, b) -> Integer.compare(b.end - b.start, a.end - a.start));
        return list;
    }

    public static int sumBytes(List<FreeRegion> regions) {
        int sum = 0;
        for (FreeRegion r : regions) sum += (r.end - r.start);
        return sum;
    }

    public static ScanOutput scanStrings(
            byte[] rom,
            GbaCodec.EncodingMode requestedMode,
            boolean includeSingleWords,
            GbaCodec.TableMap customTbl
    ) {
        Map<Integer, List<Integer>> ptrIndex = new HashMap<>(65536);
        int maxTarget = rom.length - 2;
        for (int i = 3; i < rom.length; i++) {
            int hi = rom[i] & 0xFF;
            if (hi == 0x08 || hi == 0x09) {
                int p = i - 3;
                int target = (rom[p] & 0xFF)
                        | ((rom[p + 1] & 0xFF) << 8)
                        | ((rom[p + 2] & 0xFF) << 16)
                        | ((hi - 8) << 24);
                if (target >= 0x100 && target < maxTarget) {
                    List<Integer> list = ptrIndex.get(target);
                    if (list == null) {
                        list = new ArrayList<>(2);
                        ptrIndex.put(target, list);
                    }
                    list.add(p);
                }
            }
        }

        List<Integer> offsets = new ArrayList<>(ptrIndex.keySet());
        Collections.sort(offsets);

        GbaCodec.EncodingMode mode = requestedMode;
        if (mode == GbaCodec.EncodingMode.AUTO) {
            RomMetadata meta = analyzeRom(rom);
            if (meta.isGen3Header) {
                mode = GbaCodec.EncodingMode.GEN3;
            } else {
                int g3 = sampleScore(rom, offsets, GbaCodec.EncodingMode.GEN3);
                int asc = sampleScore(rom, offsets, GbaCodec.EncodingMode.ASCII);
                mode = (g3 >= asc && g3 >= 40) ? GbaCodec.EncodingMode.GEN3 : GbaCodec.EncodingMode.ASCII;
            }
        }

        ScanOutput output = new ScanOutput();
        output.detectedMode = mode;
        output.pointerTargetCount = offsets.size();

        List<ScriptItem> candidates = new ArrayList<>();
        for (int off : offsets) {
            int b0 = rom[off] & 0xFF;
            if (mode == GbaCodec.EncodingMode.GEN3) {
                if (!GbaCodec.canStartGen3(b0)) continue;
                if (off >= 2) {
                    int p1 = rom[off - 1] & 0xFF;
                    int p2 = rom[off - 2] & 0xFF;
                    if (GbaCodec.isGen3Alpha(p1) && (GbaCodec.isGen3Alpha(p2) || p2 == 0x00)) continue;
                }
            } else if (mode == GbaCodec.EncodingMode.ASCII) {
                if (b0 < 0x21 || b0 > 0x7E) continue;
                if (off >= 1) {
                    int p1 = rom[off - 1] & 0xFF;
                    if ((p1 >= 'A' && p1 <= 'Z') || (p1 >= 'a' && p1 <= 'z')) continue;
                }
            } else if (mode == GbaCodec.EncodingMode.CUSTOM_TBL) {
                if (customTbl == null || b0 == customTbl.endByte || !customTbl.byteToChar.containsKey(b0)) continue;
            }

            GbaCodec.DecodeOutcome dec = GbaCodec.decode(rom, off, 2000, mode, customTbl);
            if (!GbaCodec.isNaturalLanguage(dec)) continue;

            boolean multiWord = dec.text.contains(" ") || dec.text.contains("\\n")
                    || dec.text.contains("\\l") || dec.text.contains("\\p");
            if (!includeSingleWords && !multiWord) continue;

            ScriptItem item = new ScriptItem();
            item.romOffset = off;
            item.originalByteLength = dec.consumedBytes;
            item.isMultiline = dec.text.contains("\\n") || dec.text.contains("\\l") || dec.text.contains("\\p");
            item.sourceText = dec.text;
            item.pointerAddresses.addAll(ptrIndex.get(off));
            candidates.add(item);
        }

        int occupiedUntil = -1;
        int seq = 1;
        for (ScriptItem item : candidates) {
            if (item.romOffset < occupiedUntil) continue;
            item.index = seq++;
            output.items.add(item);
            occupiedUntil = item.romOffset + item.originalByteLength;
        }
        return output;
    }

    private static int sampleScore(byte[] rom, List<Integer> offsets, GbaCodec.EncodingMode mode) {
        int hits = 0;
        int step = Math.max(1, offsets.size() / 4000);
        for (int i = 0; i < offsets.size(); i += step) {
            GbaCodec.DecodeOutcome dec = GbaCodec.decode(rom, offsets.get(i), 300, mode, null);
            if (dec != null && dec.consumedBytes >= 10 && dec.text.contains(" ") && GbaCodec.isNaturalLanguage(dec)) {
                hits++;
            }
        }
        return hits;
    }
}
