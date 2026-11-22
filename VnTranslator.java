package com.gbalocalizer.vn.core;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.common.model.RemoteModelManager;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.TranslateRemoteModel;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VnTranslator {

    private static final Pattern CONTROL_TOKEN = Pattern.compile(
            "(\\{FC:[0-9A-Fa-f]+\\}|\\{FD:[0-9A-Fa-f]+\\}|\\[Lv\\]|\\[PK\\]|\\[MN\\]|\\[M\\]|\\[F\\]|\\\\p|%[0-9\\.]*[sdxufc])"
    );
    private static final Pattern MULTI_SPACE = Pattern.compile(" {2,}");
    private static final Pattern SPACE_BEFORE_PUNCT = Pattern.compile(" ([.,!?;:])");
    private static final Pattern STRIP_FC = Pattern.compile("\\{FC:[0-9A-Fa-f]+\\}");
    private static final Pattern STRIP_FD = Pattern.compile("\\{FD:[0-9A-Fa-f]+\\}");

    private Translator mlKitClient;

    private synchronized Translator getMlKitClient() {
        if (mlKitClient == null) {
            TranslatorOptions opts = new TranslatorOptions.Builder()
                    .setSourceLanguage(TranslateLanguage.ENGLISH)
                    .setTargetLanguage(TranslateLanguage.VIETNAMESE)
                    .build();
            mlKitClient = Translation.getClient(opts);
        }
        return mlKitClient;
    }

    public boolean isOfflinePackReady() throws Exception {
        RemoteModelManager mgr = RemoteModelManager.getInstance();
        TranslateRemoteModel model = new TranslateRemoteModel.Builder(TranslateLanguage.VIETNAMESE).build();
        Boolean ok = Tasks.await(mgr.isModelDownloaded(model));
        return ok != null && ok;
    }

    public void downloadOfflinePack() throws Exception {
        DownloadConditions cond = new DownloadConditions.Builder().build();
        Tasks.await(getMlKitClient().downloadModelIfNeeded(cond));
    }

    public String translateOffline(String source) throws Exception {
        String exact = VnDictionary.lookupExact(source);
        if (exact != null) return exact;

        MaskedText masked = maskTokens(source);
        if (masked.clean.isEmpty()) return source;

        String rawVi = Tasks.await(getMlKitClient().translate(masked.clean));
        String restored = unmaskTokens(rawVi, masked.tokens);
        return VnDictionary.polishTerminology(restored, source);
    }

    public String translateOnlineGoogle(String source) throws Exception {
        String exact = VnDictionary.lookupExact(source);
        if (exact != null) return exact;

        MaskedText masked = maskTokens(source);
        if (masked.clean.isEmpty()) return source;

        String urlStr = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=vi&dt=t&q="
                + URLEncoder.encode(masked.clean, "UTF-8");
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");

        if (conn.getResponseCode() != 200) {
            int code = conn.getResponseCode();
            conn.disconnect();
            throw new Exception("HTTP " + code);
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        } finally {
            conn.disconnect();
        }

        JSONArray root = new JSONArray(sb.toString());
        JSONArray segs = root.getJSONArray(0);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < segs.length(); i++) {
            JSONArray s = segs.getJSONArray(i);
            if (!s.isNull(0)) out.append(s.getString(0));
        }

        String restored = unmaskTokens(out.toString(), masked.tokens);
        return VnDictionary.polishTerminology(restored, source);
    }

    public synchronized void shutdown() {
        if (mlKitClient != null) {
            mlKitClient.close();
            mlKitClient = null;
        }
    }

    private static class MaskedText {
        String clean;
        final List<String> tokens = new ArrayList<>();
    }

    private static MaskedText maskTokens(String input) {
        MaskedText res = new MaskedText();
        String flat = input.replace("\\n", " ").replace("\\l", " ");
        Matcher m = CONTROL_TOKEN.matcher(flat);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String tok = m.group(0);
            int idx = res.tokens.size();
            res.tokens.add(tok);
            String ph;
            if (tok.equals("{FD:01}")) ph = " PlayerName" + idx + " ";
            else if (tok.equals("{FD:06}")) ph = " RivalName" + idx + " ";
            else ph = " [[K" + idx + "]] ";
            m.appendReplacement(sb, Matcher.quoteReplacement(ph));
        }
        m.appendTail(sb);
        res.clean = MULTI_SPACE.matcher(sb.toString()).replaceAll(" ").trim();
        return res;
    }

    private static String unmaskTokens(String text, List<String> tokens) {
        String out = text;
        for (int i = 0; i < tokens.size(); i++) {
            String orig = tokens.get(i);
            Pattern p;
            if (orig.equals("{FD:01}")) p = Pattern.compile("\\s*PlayerName" + i + "\\s*", Pattern.CASE_INSENSITIVE);
            else if (orig.equals("{FD:06}")) p = Pattern.compile("\\s*RivalName" + i + "\\s*", Pattern.CASE_INSENSITIVE);
            else p = Pattern.compile("\\s*\\[\\[\\s*K\\s*" + i + "\\s*\\]\\]\\s*", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(out);
            if (m.find()) {
                String rep = (orig.equals("\\p") || orig.startsWith("{FC:")) ? orig : (" " + orig + " ");
                out = m.replaceFirst(Matcher.quoteReplacement(rep));
            }
        }
        out = MULTI_SPACE.matcher(out).replaceAll(" ");
        out = out.replace(" \\p", "\\p").replace("\\p ", "\\p");
        return SPACE_BEFORE_PUNCT.matcher(out).replaceAll("$1").trim();
    }

    public static String formatForGba(String vi, boolean stripAccents, boolean multiline, int maxCols, GbaCodec.EncodingMode mode) {
        String s = stripAccents ? stripDiacritics(vi) : vi;
        if (multiline) {
            s = wrapText(s, maxCols, mode == GbaCodec.EncodingMode.GEN3);
        }
        return s;
    }

    public static String stripDiacritics(String input) {
        String s = input.replace("\u0110", "D").replace("\u0111", "d")
                .replace("\u201C", "\"").replace("\u201D", "\"")
                .replace("\u2018", "'").replace("\u2019", "'")
                .replace("\u2013", "-").replace("\u2014", "-")
                .replace("\u2026", "...");
        String nfd = Normalizer.normalize(s, Normalizer.Form.NFD);
        StringBuilder sb = new StringBuilder(nfd.length());
        for (int i = 0; i < nfd.length(); i++) {
            char c = nfd.charAt(i);
            if (Character.getType(c) != Character.NON_SPACING_MARK && c >= 32 && c <= 126) {
                sb.append(c);
            }
        }
        return MULTI_SPACE.matcher(sb.toString()).replaceAll(" ").trim();
    }

    private static int visibleLen(String s) {
        String t = STRIP_FC.matcher(s).replaceAll("");
        t = STRIP_FD.matcher(t).replaceAll("XXXXXX");
        t = t.replace("[Lv]", "Lv").replace("[PK]", "P").replace("[MN]", "M");
        return t.length();
    }

    private static String wrapText(String text, int maxCols, boolean gen3Scroll) {
        String[] pages = text.split("\\\\p", -1);
        StringBuilder out = new StringBuilder();
        for (int p = 0; p < pages.length; p++) {
            if (p > 0) out.append("\\p");
            String clean = MULTI_SPACE.matcher(pages[p].replace("\\n", " ").replace("\\l", " ")).replaceAll(" ").trim();
            if (clean.isEmpty()) continue;

            List<String> lines = new ArrayList<>();
            StringBuilder cur = new StringBuilder();
            for (String w : clean.split(" ")) {
                if (cur.length() == 0) {
                    cur.append(w);
                } else if (visibleLen(cur + " " + w) <= maxCols) {
                    cur.append(' ').append(w);
                } else {
                    lines.add(cur.toString());
                    cur.setLength(0);
                    cur.append(w);
                }
            }
            if (cur.length() > 0) lines.add(cur.toString());

            for (int l = 0; l < lines.size(); l++) {
                if (l == 1) out.append("\\n");
                else if (l >= 2) out.append(gen3Scroll ? "\\l" : "\\n");
                out.append(lines.get(l));
            }
        }
        return out.toString();
    }
}
