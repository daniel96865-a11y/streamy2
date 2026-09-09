package app.streamy2;

import android.app.UiModeManager;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
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
