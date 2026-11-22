package com.gbalocalizer.vn.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.gbalocalizer.vn.R;
import com.gbalocalizer.vn.core.ScriptItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ScriptAdapter extends RecyclerView.Adapter<ScriptAdapter.Holder> {

    public interface OnItemTapListener {
        void onTap(ScriptItem item);
    }

    private final List<ScriptItem> data = new ArrayList<>();
    private final OnItemTapListener listener;

    public ScriptAdapter(OnItemTapListener listener) {
        this.listener = listener;
    }

    public void submitList(List<ScriptItem> items) {
        data.clear();
        data.addAll(items);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_script_card, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        ScriptItem item = data.get(position);
        h.tvMeta.setText(String.format(Locale.US, "#%d  0x%X", item.index, item.romOffset));
        h.tvBytes.setText(item.originalByteLength + "B");
        h.tvEn.setText(item.sourceText);

        if (item.isTranslated()) {
            h.tvVi.setText(item.translatedText);
            h.tvVi.setTextColor(ContextCompat.getColor(h.itemView.getContext(), R.color.accent_emerald));
        } else {
            h.tvVi.setText("(Chua dich - cham de nhap)");
            h.tvVi.setTextColor(ContextCompat.getColor(h.itemView.getContext(), R.color.text_muted));
        }

        h.itemView.setOnClickListener(v -> listener.onTap(item));
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView tvMeta;
        final TextView tvBytes;
        final TextView tvEn;
        final TextView tvVi;

        Holder(@NonNull View v) {
            super(v);
            tvMeta = v.findViewById(R.id.tvCardMeta);
            tvBytes = v.findViewById(R.id.tvCardBytes);
            tvEn = v.findViewById(R.id.tvCardEn);
            tvVi = v.findViewById(R.id.tvCardVi);
        }
    }
}
