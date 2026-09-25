package app.streamy2;

import android.app.Dialog;
import android.app.UiModeManager;
import android.content.res.ColorStateList;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

/* loaded from: classes.dex */
public final class Tv {
    public static boolean isTv(Context context) {
        try {
            UiModeManager uiModeManager = (UiModeManager) context.getSystemService("uimode");
            if (uiModeManager != null) {
                return uiModeManager.getCurrentModeType() == 4;
            }
            return false;
        } catch (Exception unused) {
            return false;
        }
    }

    public static void focusTree(View view) {
        if (view == null || !isTv(view.getContext())) {
            return;
        }
        if (view.isClickable() || (view instanceof EditText)) {
            view.setFocusable(true);
            view.setFocusableInTouchMode(false);
        }
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                focusTree(viewGroup.getChildAt(i));
            }
        }
    }


    /**
     * TV only: make a standard AlertDialog usable with the remote. Buttons get the
     * app's focus drawable (visible outline) and list rows a selector drawn on top,
     * so the D-pad position is always visible. Call after dialog.show().
     */
    public static void styleDialog(Dialog dialog) {
        if (dialog == null || dialog.getWindow() == null || !isTv(dialog.getContext())) {
            return;
        }
        try {
            styleDialogTree(dialog.getWindow().getDecorView());
        } catch (Throwable ignored) {
            Quiet.ignored("Tv", ignored);
        }
    }

    private static void styleDialogTree(View view) {
        if (view instanceof Button) {
            Button button = (Button) view;
            button.setFocusable(true);
            button.setFocusableInTouchMode(false);
            button.setBackgroundResource(R.drawable.bg_btn_sec);
            int fg = button.getContext().getColor(R.color.fg);
            int muted = button.getContext().getColor(R.color.muted);
            button.setTextColor(new ColorStateList(
                    new int[][]{new int[]{-android.R.attr.state_enabled}, new int[]{}},
                    new int[]{muted, fg}));
            int h = dp(button, 16);
            button.setPadding(h, button.getPaddingTop(), h, button.getPaddingBottom());
            ViewGroup.LayoutParams lp = button.getLayoutParams();
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                ((ViewGroup.MarginLayoutParams) lp).setMarginStart(dp(button, 6));
                button.setLayoutParams(lp);
            }
            return;
        }
        if (view instanceof ListView) {
            ListView list = (ListView) view;
            list.setSelector(R.drawable.bg_player_dialog_item);
            list.setDrawSelectorOnTop(true);
            list.setFocusable(true);
            list.setFocusableInTouchMode(false);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                styleDialogTree(group.getChildAt(i));
            }
        }
    }

    public static void phoneX(TextView textView) {
        if (textView == null || isTv(textView.getContext())) {
            return;
        }
        textView.setText("✕");
        textView.setBackground(null);
        textView.setTextSize(20.0f);
        textView.setPadding(dp(textView, 10), dp(textView, 6), dp(textView, 10), dp(textView, 6));
        ViewGroup.LayoutParams layoutParams = textView.getLayoutParams();
        if (layoutParams != null) {
            layoutParams.width = -2;
            layoutParams.height = -2;
            textView.setLayoutParams(layoutParams);
        }
        textView.setMinWidth(dp(textView, 40));
        textView.setMinHeight(dp(textView, 40));
        textView.setGravity(17);
    }

    public static void phoneSheet(View view, View view2) {
        if (view == null || isTv(view.getContext())) {
            return;
        }
        if (view instanceof LinearLayout) {
            LinearLayout linearLayout = (LinearLayout) view;
            linearLayout.setGravity(80);
            linearLayout.setPadding(0, 0, 0, 0);
        }
        if (view2 != null) {
            ViewGroup.LayoutParams layoutParams = view2.getLayoutParams();
            if (layoutParams instanceof LinearLayout.LayoutParams) {
                LinearLayout.LayoutParams layoutParams2 = (LinearLayout.LayoutParams) layoutParams;
                layoutParams2.width = -1;
                layoutParams2.height = 0;
                layoutParams2.weight = 0.72f;
                layoutParams2.setMargins(0, 0, 0, 0);
                view2.setLayoutParams(layoutParams2);
            }
            view2.setPadding(dp(view2, 8), dp(view2, 4), dp(view2, 8), dp(view2, 8));
        }
    }

    private static int dp(View view, int i) {
        return Math.round(i * view.getResources().getDisplayMetrics().density);
    }

    private Tv() {
    }
}
