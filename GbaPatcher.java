package com.gbalocalizer.vn.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GbaPatcher {

    public static class BuildReport {
        public int writtenInPlace;
        public int writtenRepointed;
        public int skippedUntranslated;
        public int failedNoSpace;
        public int outputSizeBytes;
    }

    public static byte[] buildPatchedRom(
            byte[] sourceRom,
            List<ScriptItem> items,
            GbaCodec.EncodingMode mode,
            GbaCodec.TableMap customTbl,
            BuildReport report
    ) {
        byte[] rom = new byte[sourceRom.length];
        System.arraycopy(sourceRom, 0, rom, 0, sourceRom.length);

        List<GbaScanner.FreeRegion> freeRegions = GbaScanner.findFreeRegions(rom, (byte) 0xFF, 4096, 64);
        if (GbaScanner.sumBytes(freeRegions) < 65536) {
            freeRegions.addAll(GbaScanner.findFreeRegions(rom, (byte) 0x00, 8192, 64));
        }

        byte filler = (mode == GbaCodec.EncodingMode.GEN3)
                ? (byte) 0xFF
                : (mode == GbaCodec.EncodingMode.CUSTOM_TBL && customTbl != null ? (byte) customTbl.endByte : (byte) 0x00);

        for (ScriptItem item : items) {
            if (!item.isTranslated()) {
                report.skippedUntranslated++;
                continue;
            }
            byte[] encoded = GbaCodec.encode(item.translatedText.trim(), mode, customTbl);
            if (encoded.length <= item.originalByteLength) {
                System.arraycopy(encoded, 0, rom, item.romOffset, encoded.length);
                for (int k = encoded.length; k < item.originalByteLength; k++) {
                    rom[item.romOffset + k] = filler;
                }
                report.writtenInPlace++;
            } else {
                int newOff = reserveAligned(freeRegions, encoded.length);
                if (newOff < 0) {
                    report.failedNoSpace++;
                    continue;
                }
                System.arraycopy(encoded, 0, rom, newOff, encoded.length);

                int oldAddr = 0x08000000 + item.romOffset;
                int newAddr = 0x08000000 + newOff;
                byte o0 = (byte) (oldAddr & 0xFF);
                byte o1 = (byte) ((oldAddr >>> 8) & 0xFF);
                byte o2 = (byte) ((oldAddr >>> 16) & 0xFF);
                byte o3 = (byte) ((oldAddr >>> 24) & 0xFF);

                byte n0 = (byte) (newAddr & 0xFF);
                byte n1 = (byte) ((newAddr >>> 8) & 0xFF);
                byte n2 = (byte) ((newAddr >>> 16) & 0xFF);
                byte n3 = (byte) ((newAddr >>> 24) & 0xFF);

                for (int ptr : item.pointerAddresses) {
                    if (ptr >= 0 && ptr + 4 <= rom.length) {
                        if (rom[ptr] == o0 && rom[ptr + 1] == o1 && rom[ptr + 2] == o2 && rom[ptr + 3] == o3) {
                            rom[ptr] = n0;
                            rom[ptr + 1] = n1;
                            rom[ptr + 2] = n2;
                            rom[ptr + 3] = n3;
                        }
                    }
                }
                report.writtenRepointed++;
            }
        }

        report.outputSizeBytes = rom.length;
        return rom;
    }

    private static int reserveAligned(List<GbaScanner.FreeRegion> regions, int bytesNeeded) {
        for (GbaScanner.FreeRegion r : regions) {
            int cur = r.start;
            int rem = cur % 4;
            if (rem != 0) cur += (4 - rem);
            if (cur + bytesNeeded <= r.end) {
                r.start = cur + bytesNeeded;
                return cur;
            }
        }
        return -1;
    }

    public static String serializeProjectJson(String romName, GbaCodec.EncodingMode mode, List<ScriptItem> items) throws Exception {
        JSONObject root = new JSONObject();
        root.put("rom", romName);
        root.put("encoding", mode.name());
        root.put("count", items.size());

        JSONArray arr = new JSONArray();
        for (ScriptItem item : items) {
            JSONObject obj = new JSONObject();
            obj.put("id", item.index);
            obj.put("offset", "0x" + Integer.toHexString(item.romOffset));
            obj.put("len", item.originalByteLength);
            obj.put("en", item.sourceText);
            obj.put("vi", item.translatedText);
            arr.put(obj);
        }
        root.put("entries", arr);
        return root.toString(2);
    }

    public static int mergeProjectJson(String jsonStr, List<ScriptItem> targets) throws Exception {
        JSONObject root = new JSONObject(jsonStr);
        JSONArray arr = root.getJSONArray("entries");
        Map<Integer, String> byOffset = new HashMap<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            String vi = obj.optString("vi", "");
            if (!vi.isEmpty()) {
                String off = obj.getString("offset").trim();
                int parsed = (off.startsWith("0x") || off.startsWith("0X"))
                        ? Integer.parseInt(off.substring(2), 16)
                        : Integer.parseInt(off);
                byOffset.put(parsed, vi);
            }
        }
        int imported = 0;
        for (ScriptItem item : targets) {
            String vi = byOffset.get(item.romOffset);
            if (vi != null && !vi.isEmpty()) {
                item.translatedText = vi;
                imported++;
            }
        }
        return imported;
    }
}
