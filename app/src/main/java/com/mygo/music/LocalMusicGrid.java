package com.mygo.music;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class LocalMusicGrid extends LinearLayout {
    interface Host {
        void artwork(ImageView view, Uri uri);
        void play(int index);
    }

    final GridView grid;
    final EditText filter;
    private final TextView empty;
    private final List<Uri> tracks;
    private final List<String> names;
    private final Host host;
    private final ArrayList<Integer> visible = new ArrayList<>();
    private final SongAdapter songs = new SongAdapter();

    LocalMusicGrid(Context context, List<Uri> tracks, List<String> names, Host host) {
        super(context);
        this.tracks = tracks;
        this.names = names;
        this.host = host;
        setOrientation(VERTICAL);
        filter = new EditText(context);
        filter.setSingleLine(true);
        filter.setTextSize(14);
        filter.setTextColor(Color.WHITE);
        filter.setHintTextColor(Color.rgb(170, 165, 175));
        filter.setHint("筛选本地音乐的歌名或歌手");
        filter.setPadding(dp(10), 0, dp(10), 0);
        filter.setBackground(background(39, 36, 44));
        LayoutParams filterParams = new LayoutParams(-1, dp(36));
        filterParams.bottomMargin = dp(6);
        addView(filter, filterParams);

        FrameLayout content = new FrameLayout(context);
        addView(content, new LayoutParams(-1, 0, 1));
        grid = new GridView(context);
        grid.setNumColumns(GridView.AUTO_FIT);
        grid.setColumnWidth(dp(112));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setHorizontalSpacing(dp(6));
        grid.setVerticalSpacing(dp(6));
        grid.setContentDescription("本地音乐列表");
        content.addView(grid, new FrameLayout.LayoutParams(-1, -1));

        empty = new TextView(context);
        empty.setTextColor(Color.LTGRAY);
        empty.setTextSize(15);
        empty.setGravity(Gravity.CENTER);
        content.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        grid.setEmptyView(empty);
        grid.setAdapter(songs);
        grid.setOnItemClickListener((parent, view, position, id) -> host.play(songs.getItem(position)));
        filter.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                refresh();
                grid.setSelection(0);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    void refresh() {
        String query = filter.getText().toString().trim().toLowerCase(Locale.ROOT);
        String[] terms = query.isEmpty() ? new String[0] : query.split("\\s+");
        visible.clear();
        for (int i = 0, count = Math.min(tracks.size(), names.size()); i < count; i++) {
            String name = names.get(i).toLowerCase(Locale.ROOT);
            if (matches(name, terms)) visible.add(i);
        }
        empty.setText(query.isEmpty() ? "暂无歌曲，先添加本地音乐或从音乐源播放" : "没有符合条件的歌曲");
        songs.notifyDataSetChanged();
    }

    private static boolean matches(String name, String[] terms) {
        for (String term : terms) if (!name.contains(term)) return false;
        return true;
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private GradientDrawable background(int red, int green, int blue) {
        GradientDrawable result = new GradientDrawable();
        result.setColor(Color.rgb(red, green, blue));
        result.setCornerRadius(dp(10));
        return result;
    }

    private final class SongAdapter extends BaseAdapter {
        @Override public int getCount() { return visible.size(); }
        @Override public Integer getItem(int position) { return visible.get(position); }
        @Override public long getItemId(int position) { return getItem(position); }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            SongCard card = convertView instanceof SongCard ? (SongCard) convertView : new SongCard();
            int index = getItem(position);
            card.title.setText(names.get(index));
            card.cover.setTag(null);
            card.cover.setImageResource(R.drawable.ic_launcher);
            host.artwork(card.cover, tracks.get(index));
            return card;
        }
    }

    private final class SongCard extends LinearLayout {
        final TextView title;
        final ImageView cover;

        SongCard() {
            super(LocalMusicGrid.this.getContext());
            setOrientation(VERTICAL);
            setLayoutParams(new AbsListView.LayoutParams(-1, dp(118)));
            setPadding(dp(6), dp(6), dp(6), dp(6));
            setBackground(background(37, 33, 42));

            cover = new ImageView(getContext());
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            addView(cover, new LayoutParams(-1, 0, 1));

            title = new TextView(getContext());
            title.setTextColor(Color.WHITE);
            title.setTextSize(12);
            title.setMaxLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setGravity(Gravity.CENTER_VERTICAL);
            addView(title, new LayoutParams(-1, dp(34)));
        }
    }
}
