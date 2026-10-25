package dev.ad.soap;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Cards {
    static final int WIDTH = 112;
    static final int POSTER = 168;
    private static final int DIM_TEXT = 0xFFB6BBC4;

    private Cards() {}

    static final class Card {
        final String image;
        final String title;
        final String badge;
        final String corner;
        final String caption;
        final Runnable onClick;
        final Runnable onLongClick;

        Card(String image, String title, String badge, String corner, String caption, Runnable onClick, Runnable onLongClick) {
            this.image = image;
            this.title = title;
            this.badge = badge;
            this.corner = corner;
            this.caption = caption;
            this.onClick = onClick;
            this.onLongClick = onLongClick;
        }
    }

    static int dp(Context context, int v) {
        return Math.round(v * context.getResources().getDisplayMetrics().density);
    }

    static View view(Context context, Card card, Runnable onFocus) {
        LinearLayout view = new LinearLayout(context);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setFocusable(true);
        view.setPadding(dp(context, 5), dp(context, 5), dp(context, 5), dp(context, 5));
        LinearLayout.LayoutParams margin = new LinearLayout.LayoutParams(-2, -2);
        margin.setMarginEnd(dp(context, 3));
        view.setLayoutParams(margin);

        FrameLayout box = new FrameLayout(context);
        box.setClipToOutline(true);
        box.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(context, 10));
            }
        });
        ImageView poster = new ImageView(context);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setBackgroundColor(0xFF22262D);
        box.addView(poster, new FrameLayout.LayoutParams(-1, -1));
        Images.load(poster, card.image);
        if (card.badge != null) box.addView(pill(context, card.badge, 0xB0000000), at(context, Gravity.TOP | Gravity.START));
        if (card.corner != null) box.addView(pill(context, card.corner, 0xFFD32F2F), at(context, Gravity.TOP | Gravity.END));
        if (card.caption != null) {
            FrameLayout.LayoutParams low = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
            low.setMargins(dp(context, 4), 0, 0, dp(context, 4));
            box.addView(pill(context, card.caption, 0xB0000000), low);
        }
        view.addView(box, new LinearLayout.LayoutParams(dp(context, WIDTH), dp(context, POSTER)));

        TextView title = new TextView(context);
        title.setText(card.title);
        title.setTextSize(12);
        title.setTextColor(DIM_TEXT);
        title.setPadding(dp(context, 2), dp(context, 4), dp(context, 2), 0);
        title.setLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        view.addView(title, new LinearLayout.LayoutParams(dp(context, WIDTH), dp(context, 38)));

        view.setOnClickListener(v -> card.onClick.run());
        if (card.onLongClick != null) {
            view.setOnLongClickListener(v -> {
                card.onLongClick.run();
                return true;
            });
        }
        view.setOnFocusChangeListener((v, focused) -> {
            v.animate().scaleX(focused ? 1.06f : 1f).scaleY(focused ? 1.06f : 1f)
                .translationZ(focused ? dp(context, 8) : 0).setDuration(120).start();
            title.setTextColor(focused ? 0xFFFFFFFF : DIM_TEXT);
            title.setTypeface(focused ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            box.setForeground(focused ? ring(context) : null);
            if (focused && onFocus != null) onFocus.run();
        });
        return view;
    }

    static TextView button(Context context, String text) {
        TextView button = new TextView(context);
        button.setText(text);
        button.setTextSize(16);
        button.setGravity(Gravity.CENTER);
        button.setFocusable(true);
        button.setClickable(true);
        button.setPadding(dp(context, 22), dp(context, 10), dp(context, 22), dp(context, 10));
        button.setTextColor(new ColorStateList(new int[][] {{-android.R.attr.state_enabled}, {}}, new int[] {0xFF6E7681, 0xFFFFFFFF}));
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[] {android.R.attr.state_focused}, buttonShape(context, 0xFF2B4C7E, 0xFF8CB4E8));
        background.addState(new int[] {}, buttonShape(context, 0xFF1F2733, 0x33FFFFFF));
        button.setBackground(background);
        return button;
    }

    private static GradientDrawable buttonShape(Context context, int fill, int stroke) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(context, 22));
        shape.setStroke(dp(context, 2), stroke);
        return shape;
    }

    private static GradientDrawable ring(Context context) {
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(context, 10));
        shape.setStroke(dp(context, 3), 0xFFFFFFFF);
        return shape;
    }

    private static TextView pill(Context context, String text, int color) {
        TextView badge = new TextView(context);
        badge.setText(text);
        badge.setTextSize(11);
        badge.setTextColor(0xFFFFFFFF);
        badge.setPadding(dp(context, 5), dp(context, 1), dp(context, 5), dp(context, 1));
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(context, 8));
        badge.setBackground(shape);
        return badge;
    }

    private static FrameLayout.LayoutParams at(Context context, int gravity) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(-2, -2, gravity);
        p.setMargins(dp(context, 4), dp(context, 4), dp(context, 4), 0);
        return p;
    }
}
