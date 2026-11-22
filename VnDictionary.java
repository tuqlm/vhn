package com.gbalocalizer.vn.core;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VnDictionary {

    private static final Map<String, String> EXACT_VI = new HashMap<>();

    static {
        EXACT_VI.put("yes", "Có");
        EXACT_VI.put("no", "Không");
        EXACT_VI.put("new game", "Chơi mới");
        EXACT_VI.put("continue", "Tiếp tục");
        EXACT_VI.put("options", "Tùy chọn");
        EXACT_VI.put("option", "Tùy chọn");
        EXACT_VI.put("save", "Lưu game");
        EXACT_VI.put("save the game?", "Bạn có muốn lưu game không?");
        EXACT_VI.put("exit", "Thoát");
        EXACT_VI.put("cancel", "Hủy");
        EXACT_VI.put("back", "Quay lại");
        EXACT_VI.put("bag", "Túi đồ");
        EXACT_VI.put("party", "Đội hình");
        EXACT_VI.put("pokemon", "Pokemon");
        EXACT_VI.put("pokedex", "Pokedex");
        EXACT_VI.put("pokegear", "Pokegear");
        EXACT_VI.put("pokenav", "Pokenav");
        EXACT_VI.put("trainer card", "Thẻ Huấn luyện viên");
        EXACT_VI.put("a critical hit!", "Một đòn chí mạng!");
        EXACT_VI.put("it's super effective!", "Chiêu thức cực kỳ hiệu quả!");
        EXACT_VI.put("it's not very effective...", "Chiêu thức không hiệu quả lắm...");
        EXACT_VI.put("but it failed!", "Nhưng chiêu đã trượt!");
        EXACT_VI.put("got away safely!", "Đã chạy thoát an toàn!");
        EXACT_VI.put("can't escape!", "Không thể chạy trốn!");
        EXACT_VI.put("your coin case is full.", "Hộp xu của bạn đã đầy.");
        EXACT_VI.put("thanks, here you go.", "Cảm ơn, của bạn đây.");
        EXACT_VI.put("welcome! looking to buy coins?", "Chào mừng! Bạn muốn mua xu không?");
        EXACT_VI.put("come back when you're ready to buy.", "Hãy quay lại khi bạn sẵn sàng mua.");
        EXACT_VI.put("play which pinball game?", "Bạn muốn chơi bàn pinball nào?");
        EXACT_VI.put("give it a good shake?", "Lắc mạnh một cái chứ?");
    }

    public static String lookupExact(String en) {
        if (en == null) return null;
        String key = en.replace("\\n", " ").replace("\\l", " ")
                .replaceAll("\\s+", " ").trim().toLowerCase(Locale.US);
        return EXACT_VI.get(key);
    }

    public static String polishTerminology(String vi, String en) {
        if (vi == null || vi.isEmpty()) return vi;
        String out = vi;
        String enL = en == null ? "" : en.toLowerCase(Locale.US);

        out = swap(out, "phòng tập thể dục", "Gym");
        out = swap(out, "phòng gym", "Gym");
        out = swap(out, "nhà thi đấu thể dục", "Gym");

        if (enL.contains("badge")) out = swap(out, "huy chương", "Huy hiệu");
        if (enL.contains("party")) {
            out = swap(out, "bữa tiệc", "đội hình");
            out = swap(out, "bên tham gia", "đội hình");
        }
        if (enL.contains("coin case")) {
            out = swap(out, "trường hợp tiền xu", "Hộp xu");
            out = swap(out, "vỏ đồng xu", "Hộp xu");
            out = swap(out, "hộp đựng xu", "Hộp xu");
        }
        if (enL.contains("faint")) {
            out = swap(out, "bị ngất xỉu", "bị hạ gục");
            out = swap(out, "đã ngất xỉu", "đã bị hạ gục");
            out = swap(out, "ngất đi", "bị hạ gục");
        }
        if (enL.contains("critical hit")) {
            out = swap(out, "cú đánh chí mạng", "đòn chí mạng");
            out = swap(out, "một cú đánh quan trọng", "một đòn chí mạng");
        }
        if (enL.contains("super effective")) out = swap(out, "siêu hiệu quả", "cực kỳ hiệu quả");
        if (enL.contains("save")) {
            out = swap(out, "tiết kiệm trò chơi", "lưu game");
            out = swap(out, "tiết kiệm", "lưu");
        }
        if (enL.contains("coin")) {
            out = swap(out, "tiền xu", "xu");
            out = swap(out, "đồng xu", "xu");
        }
        if (enL.contains("potion")) {
            out = swap(out, "độc dược", "Potion");
            out = swap(out, "thuốc độc", "Potion");
        }
        if (enL.contains("exp")) out = swap(out, "điểm kinh nghiệm", "điểm EXP");

        return out.replaceAll(" {2,}", " ").trim();
    }

    private static String swap(String src, String target, String repl) {
        Pattern p = Pattern.compile(Pattern.quote(target), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        return p.matcher(src).replaceAll(Matcher.quoteReplacement(repl));
    }
}
