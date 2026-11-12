package dev.ad.soap;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

final class CardGrid {
    private static final int FIRST_ROWS = 14;
    private static final int MORE_ROWS = 6;

    private final Context context;
    private final int columns;
    private final ScrollView scroll;
    private final LinearLayout rows;
    private List<Cards.Card> cards = new ArrayList<>();
    private int shown;
    private int focusIndex;

    CardGrid(Context context) {
        this.context = context;
        int card = Cards.width(context);
        int width = context.getResources().getDisplayMetrics().widthPixels;
        columns = Math.max(1, (width - Cards.dp(context, 8)) / card);
        int side = Math.max(0, (width - columns * card) / 2);
        rows = new LinearLayout(context);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(side, Cards.dp(context, 12), side, Cards.dp(context, 12));
        scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setFocusable(false);
        scroll.addView(rows);
    }

    View view() {
        return scroll;
    }

    void show(List<Cards.Card> list) {
        if (!rows.hasFocus() || list.isEmpty()) {
            clear();
            cards = list;
            more(FIRST_ROWS);
            return;
        }
        // Old rows go only after focus moves to the new ones: losing focus lands it on the Home tab, which switches to it.
        int target = target(list, cards.get(focusIndex).title);
        int oldRows = rows.getChildCount();
        cards = list;
        shown = 0;
        more(Math.max(Math.max(FIRST_ROWS, oldRows), target / columns + 2));
        ((LinearLayout) rows.getChildAt(oldRows + target / columns)).getChildAt(target % columns).requestFocus();
        rows.removeViews(0, oldRows);
    }

    private int target(List<Cards.Card> list, String title) {
        for (int i = 0; i < list.size(); i++) if (list.get(i).title.equals(title)) return i;
        return Math.min(focusIndex, list.size() - 1);
    }

    void clear() {
        cards = new ArrayList<>();
        shown = 0;
        rows.removeAllViews();
        scroll.scrollTo(0, 0);
    }

    void section(String title, List<Cards.Card> list) {
        TextView header = new TextView(context);
        header.setText(title);
        header.setTextSize(18);
        header.setTextColor(0xFFFFFFFF);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setPadding(Cards.dp(context, 8), Cards.dp(context, 8), 0, Cards.dp(context, 4));
        rows.addView(header);
        for (int i = 0; i < list.size(); i += columns) {
            LinearLayout row = new LinearLayout(context);
            row.setClipChildren(false);
            for (Cards.Card card : list.subList(i, Math.min(list.size(), i + columns))) row.addView(Cards.view(context, card, null));
            rows.addView(row);
        }
    }

    void message(String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(18);
        view.setTextColor(0xFF8B919A);
        view.setGravity(Gravity.CENTER);
        view.setPadding(0, Cards.dp(context, 80), 0, 0);
        rows.addView(view, new LinearLayout.LayoutParams(-1, -2));
    }

    private void more(int count) {
        int end = Math.min(cards.size(), shown + count * columns);
        while (shown < end) {
            LinearLayout row = new LinearLayout(context);
            row.setClipChildren(false);
            for (int i = 0; i < columns && shown < cards.size(); i++, shown++) {
                int index = shown;
                row.addView(Cards.view(context, cards.get(index), () -> focused(index)));
            }
            rows.addView(row);
        }
    }

    private void focused(int index) {
        focusIndex = index;
        if (shown < cards.size() && index >= shown - 2 * columns) more(MORE_ROWS);
    }
}
