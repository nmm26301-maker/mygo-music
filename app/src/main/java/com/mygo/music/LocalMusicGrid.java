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
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.Button;
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
        void play(Uri uri);
    }

    final GridView grid;
    final EditText filter;
    final Button clearFilter;
    private final TextView empty;
    private final List<Uri> tracks;
    private final List<String> names;
    private final Host host;
    private List<Song> visible = java.util.Collections.emptyList();
    private final SongAdapter songs = new SongAdapter();

    LocalMusicGrid(Context context, List<Uri> tracks, List<String> names, Host host) {
        super(context);
        this.tracks = tracks;
        this.names = names;
        this.host = host;
        setOrientation(VERTICAL);
        LinearLayout searchRow = new LinearLayout(context);
        searchRow.setBackground(background(39, 36, 44));
        LayoutParams searchParams = new LayoutParams(-1, dp(36));
        searchParams.bottomMargin = dp(6);
        addView(searchRow, searchParams);

        filter = new EditText(context);
        filter.setSingleLine(true);
        filter.setImeOptions(EditorInfo.IME_ACTION_DONE);
        filter.setTextSize(14);
        filter.setTextColor(Color.WHITE);
        filter.setHintTextColor(Color.rgb(170, 165, 175));
        filter.setHint("筛选本地音乐的歌名或歌手");
        filter.setPadding(dp(10), 0, dp(10), 0);
        filter.setBackgroundColor(Color.TRANSPARENT);
        searchRow.addView(filter, new LayoutParams(0, -1, 1));
        filter.setOnEditorActionListener((view, action, event) -> {
            if (action != EditorInfo.IME_ACTION_DONE) return false;
            hideFilterKeyboard();
            return true;
        });
        clearFilter = new Button(context);
        clearFilter.setText("清除");
        clearFilter.setContentDescription("清除本地音乐筛选");
        clearFilter.setTextSize(12);
        clearFilter.setTextColor(Color.WHITE);
        clearFilter.setPadding(0, 0, 0, 0);
        clearFilter.setBackgroundColor(Color.TRANSPARENT);
        clearFilter.setVisibility(GONE);
        searchRow.addView(clearFilter, new LayoutParams(dp(52), -1));
        clearFilter.setOnClickListener(view -> {
            filter.setText("");
            hideFilterKeyboard();
        });

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
        grid.addOnLayoutChangeListener((v, l, t, r, b, oldL, oldT, oldR, oldB) -> {
            if (cardHeight(b - t) != cardHeight(oldB - oldT)) songs.notifyDataSetChanged();
        });

        empty = new TextView(context);
        empty.setTextColor(Color.LTGRAY);
        empty.setTextSize(15);
        empty.setGravity(Gravity.CENTER);
        content.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        grid.setEmptyView(empty);
        grid.setAdapter(songs);
        grid.setOnItemClickListener((parent, view, position, id) -> {
            if (view instanceof SongCard) host.play(((SongCard) view).uri);
        });
        filter.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                clearFilter.setVisibility(s.length() > 0 ? VISIBLE : GONE);
                refresh();
                grid.setSelection(0);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void hideFilterKeyboard() {
        InputMethodManager input = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (input != null) input.hideSoftInputFromWindow(filter.getWindowToken(), 0);
        filter.clearFocus();
    }

    void refresh() {
        String query = filter.getText().toString().trim().toLowerCase(Locale.ROOT);
        String[] terms = query.isEmpty() ? new String[0] : query.split("\\s+");
        ArrayList<Song> matches = new ArrayList<>();
        for (int i = 0, count = Math.min(tracks.size(), names.size()); i < count; i++) {
            String title = names.get(i);
            if (matches(title.toLowerCase(Locale.ROOT), terms)) {
                matches.add(new Song(tracks.get(i), title));
            }
        }
        visible = matches;
        empty.setText(query.isEmpty() ? "暂无歌曲，先添加本地音乐或从音乐源播放" : "没有符合条件的歌曲");
        songs.notifyDataSetChanged();
    }

    private static boolean matches(String name, String[] terms) {
        for (String term : terms) if (!name.contains(term)) return false;
        return true;
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private int cardHeight(int viewport) {
        return viewport > 0 && viewport < dp(124) ? Math.min(viewport, dp(46)) : dp(118);
    }

    private GradientDrawable background(int red, int green, int blue) {
        GradientDrawable result = new GradientDrawable();
        result.setColor(Color.rgb(red, green, blue));
        result.setCornerRadius(dp(10));
        return result;
    }

    private static final class Song {
        final Uri uri;
        final String title;

        Song(Uri uri, String title) {
            this.uri = uri;
            this.title = title;
        }
    }

    private final class SongAdapter extends BaseAdapter {
        @Override public int getCount() { return visible.size(); }
        @Override public Song getItem(int position) { return visible.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            SongCard card = convertView instanceof SongCard ? (SongCard) convertView : new SongCard();
            Song song = getItem(position);
            card.uri = song.uri;
            card.resize(cardHeight(grid.getHeight()));
            card.title.setText(song.title);
            card.cover.setTag(null);
            card.cover.setImageResource(R.drawable.ic_launcher);
            host.artwork(card.cover, song.uri);
            return card;
        }
    }

    private final class SongCard extends LinearLayout {
        final TextView title;
        final ImageView cover;
        Uri uri;
        private int height;

        SongCard() {
            super(LocalMusicGrid.this.getContext());
            setBackground(background(37, 33, 42));

            cover = new ImageView(getContext());
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            addView(cover);

            title = new TextView(getContext());
            title.setTextColor(Color.WHITE);
            title.setTextSize(12);
            title.setMaxLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setGravity(Gravity.CENTER_VERTICAL);
            addView(title);
        }

        void resize(int height) {
            if (this.height == height) return;
            this.height = height;
            boolean compact = height < dp(118);
            setLayoutParams(new AbsListView.LayoutParams(-1, height));
            setOrientation(compact ? HORIZONTAL : VERTICAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(dp(6), dp(compact ? 3 : 6), dp(6), dp(compact ? 3 : 6));
            LayoutParams coverParams = compact ? new LayoutParams(dp(30), dp(30)) : new LayoutParams(-1, 0, 1);
            coverParams.rightMargin = compact ? dp(5) : 0;
            cover.setLayoutParams(coverParams);
            title.setLayoutParams(compact ? new LayoutParams(0, -1, 1) : new LayoutParams(-1, dp(34)));
        }
    }
}
