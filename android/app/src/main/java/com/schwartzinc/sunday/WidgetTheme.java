package com.schwartzinc.sunday;

import android.graphics.Color;

public final class WidgetTheme {
    public final int bg;
    public final int bgRes;
    public final int cardBg;
    public final int textPrimary;
    public final int textSecondary;
    public final int border;
    public final int accent;
    public final int action;

    private WidgetTheme(int bg, int bgRes, int cardBg, int textPrimary, int textSecondary, int border, int accent, int action) {
        this.bg = bg;
        this.bgRes = bgRes;
        this.cardBg = cardBg;
        this.textPrimary = textPrimary;
        this.textSecondary = textSecondary;
        this.border = border;
        this.accent = accent;
        this.action = action;
    }

    public static WidgetTheme get(String theme) {
        if (theme == null) theme = "dark";
        switch (theme) {
            case "light":
                return new WidgetTheme(
                    Color.parseColor("#f8f9fb"),
                    R.drawable.widget_bg_light,
                    Color.parseColor("#ffffff"),
                    Color.parseColor("#2c3e50"),
                    Color.parseColor("#64748b"),
                    Color.parseColor("#e8ecf0"),
                    Color.parseColor("#6366f1"),
                    Color.parseColor("#059669")
                );
            case "slate":
                return new WidgetTheme(
                    Color.parseColor("#1e2126"),
                    R.drawable.widget_bg_slate,
                    Color.parseColor("#282c34"),
                    Color.parseColor("#c9d1d9"),
                    Color.parseColor("#8b949e"),
                    Color.parseColor("#363b44"),
                    Color.parseColor("#9bc0eb"),
                    Color.parseColor("#4a8a6f")
                );
            case "paper":
                return new WidgetTheme(
                    Color.parseColor("#f7f5f2"),
                    R.drawable.widget_bg_paper,
                    Color.parseColor("#ffffff"),
                    Color.parseColor("#3a3733"),
                    Color.parseColor("#726c63"),
                    Color.parseColor("#e7e2da"),
                    Color.parseColor("#6b6256"),
                    Color.parseColor("#5c7a5c")
                );
            case "dark":
            default:
                return new WidgetTheme(
                    Color.parseColor("#1a1a1a"),
                    R.drawable.widget_bg_dark,
                    Color.parseColor("#2d2d2d"),
                    Color.parseColor("#e0e0e0"),
                    Color.parseColor("#888888"),
                    Color.parseColor("#3d3d3d"),
                    Color.parseColor("#ff6b2b"),
                    Color.parseColor("#4CAF50")
                );
        }
    }
}
