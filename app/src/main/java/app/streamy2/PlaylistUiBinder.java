package app.streamy2;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Binds the separated playlist profiles to the existing settings screen. */
final class PlaylistUiBinder {
    private static final String PREFS = "streamy2";
    private static final String OPEN_SETTINGS = "profileOpenSettings";
    private static final String ADD_TAG = "streamyPlaylistAddButton";

    private PlaylistUiBinder() {
    }

    static void bind(Activity activity) {
        if (!(activity instanceof MainActivity)) return;
        MainActivity main = (MainActivity) activity;
        Prefs prefs = new Prefs(main);
        bindLabel(main, prefs);

        SharedPreferences raw = main.getSharedPreferences(PREFS, 0);
        if (raw.getBoolean(OPEN_SETTINGS, false)) {
            raw.edit().remove(OPEN_SETTINGS).apply();
            View settings = main.findViewById(R.id.btnSettings);
            if (settings != null) {
                settings.post(() -> {
                    settings.performClick();
                    bindLabel(main, new Prefs(main));
                    EditText name = main.findViewById(R.id.inName);
                    if (name != null) name.postDelayed(name::requestFocus, 120L);
                });
            }
        }
    }

    private static void bindLabel(MainActivity main, Prefs prefs) {
        TextView label = main.findViewById(R.id.activeLabel);
        if (label == null) return;
        label.setClickable(true);
        label.setFocusable(true);
        label.setFocusableInTouchMode(false);
        label.setBackgroundResource(R.drawable.bg_chip);
        int horizontal = dp(main, 12);
        label.setPadding(horizontal, 0, horizontal, 0);
        label.setMinimumHeight(dp(main, 44));
        label.setContentDescription("Wiedergabelisten verwalten");
        label.setOnClickListener(v -> showManager(main, new Prefs(main)));
        bindVisibleAddButton(main, label);
    }

    private static void bindVisibleAddButton(MainActivity main, TextView label) {
        if (!(label.getParent() instanceof ViewGroup)) return;
        ViewGroup parent = (ViewGroup) label.getParent();
        View existing = parent.findViewWithTag(ADD_TAG);
        if (existing instanceof TextView) return;

        TextView addButton = new TextView(main);
        addButton.setTag(ADD_TAG);
        addButton.setId(View.generateViewId());
        addButton.setText("＋ Wiedergabeliste hinzufügen");
        addButton.setTextSize(14f);
        addButton.setTextColor(main.getColor(R.color.accent));
        addButton.setGravity(Gravity.CENTER_VERTICAL);
        addButton.setBackgroundResource(R.drawable.bg_btn_sec);
        addButton.setFocusable(true);
        addButton.setFocusableInTouchMode(false);
        addButton.setClickable(true);
        addButton.setContentDescription("Neue Wiedergabeliste hinzufügen");
        int horizontal = dp(main, 14);
        addButton.setPadding(horizontal, 0, horizontal, 0);
        addButton.setOnClickListener(v -> add(main, new Prefs(main)));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(main, 48));
        params.topMargin = dp(main, 8);
        int index = parent.indexOfChild(label);
        parent.addView(addButton, Math.max(0, index + 1), params);

        label.setNextFocusDownId(addButton.getId());
        EditText name = main.findViewById(R.id.inName);
        if (name != null) addButton.setNextFocusDownId(name.getId());
    }

    private static void showManager(MainActivity main, Prefs prefs) {
        final List<String> ids = prefs.profileIds();
        final List<String> names = prefs.profileNames();
        final String active = prefs.activeProfileId();
        ArrayList<String> entries = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            String prefix = ids.get(i).equals(active) ? "✓ " : "";
            entries.add(prefix + names.get(i));
        }
        entries.add("＋ Neue Playlist hinzufügen");
        entries.add("🗑 Aktive Playlist löschen");

        AlertDialog manager = new AlertDialog.Builder(main)
                .setTitle("Wiedergabelisten")
                .setItems(entries.toArray(new String[0]), (dialog, which) -> {
                    if (which < ids.size()) {
                        activate(main, prefs, ids.get(which));
                    } else if (which == ids.size()) {
                        add(main, prefs);
                    } else {
                        confirmDelete(main, prefs);
                    }
                })
                .setNegativeButton("Abbrechen", null)
                .show();
        Tv.styleDialog(manager);
    }

    private static void activate(MainActivity main, Prefs prefs, String id) {
        if (id.equals(prefs.activeProfileId())) return;
        if (prefs.setActiveProfile(id)) {
            resetAppState();
            main.recreate();
        }
    }

    private static void add(MainActivity main, Prefs prefs) {
        prefs.createProfile(null);
        main.getSharedPreferences(PREFS, 0).edit().putBoolean(OPEN_SETTINGS, true).apply();
        resetAppState();
        main.recreate();
    }

    private static void confirmDelete(MainActivity main, Prefs prefs) {
        String name = prefs.activeProfileName();
        AlertDialog confirm = new AlertDialog.Builder(main)
                .setTitle("Playlist löschen")
                .setMessage("„" + name + "“ wirklich von diesem Gerät löschen?")
                .setPositiveButton("Löschen", (dialog, which) -> {
                    File cache = prefs.catalogCacheFile(main.getCacheDir());
                    if (cache != null && cache.isFile()) cache.delete();
                    prefs.deleteActiveProfile();
                    resetAppState();
                    main.recreate();
                })
                .setNegativeButton("Abbrechen", null)
                .show();
        Tv.styleDialog(confirm);
    }

    private static void resetAppState() {
        App.api = null;
        App.live = null;
        App.playing = null;
        App.guide = new EpgGuide();
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
