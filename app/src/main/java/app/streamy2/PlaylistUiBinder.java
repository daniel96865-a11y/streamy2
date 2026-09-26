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
    static final String REFRESH_TAG = "streamyPlaylistRefreshButton";
    private static final String REFRESH_LABEL = "Playlist aktualisieren";
    private static final String REFRESH_BUSY_LABEL = "Playlist wird aktualisiert…";

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
        bindRefreshButton(main, label, prefs);
    }

    /** Visible "Playlist aktualisieren" button right below the active playlist label. */
    private static void bindRefreshButton(MainActivity main, TextView label, Prefs prefs) {
        if (!(label.getParent() instanceof ViewGroup)) return;
        ViewGroup parent = (ViewGroup) label.getParent();
        View existing = parent.findViewWithTag(REFRESH_TAG);
        TextView button;
        if (existing instanceof TextView) {
            button = (TextView) existing;
        } else {
            button = new TextView(main);
            button.setTag(REFRESH_TAG);
            button.setId(View.generateViewId());
            button.setText(REFRESH_LABEL);
            button.setTextSize(14f);
            button.setGravity(Gravity.CENTER_VERTICAL);
            button.setBackgroundResource(R.drawable.bg_btn_sec);
            button.setFocusable(true);
            button.setFocusableInTouchMode(false);
            button.setClickable(true);
            button.setContentDescription("Aktive Playlist jetzt vom Server neu laden");
            button.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_refresh, 0, 0, 0);
            button.setCompoundDrawablePadding(dp(main, 10));
            int horizontal = dp(main, 14);
            button.setPadding(horizontal, 0, horizontal, 0);
            button.setOnClickListener(v -> main.refreshPlaylist());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(main, 48));
            params.topMargin = dp(main, 8);
            parent.addView(button, Math.max(0, parent.indexOfChild(label) + 1), params);
        }
        int accent = AccentTheme.accent(main);
        button.setTextColor(accent);
        button.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(accent));
        boolean has = prefs.hasXtream();
        button.setVisibility(has ? View.VISIBLE : View.GONE);
        boolean busy = main.isPlaylistRefreshing();
        button.setText(busy ? REFRESH_BUSY_LABEL : REFRESH_LABEL);
        button.setEnabled(!busy);

        // D-pad order: label -> refresh -> add -> name field.
        View add = parent.findViewWithTag(ADD_TAG);
        if (has) {
            label.setNextFocusDownId(button.getId());
            if (add != null) {
                button.setNextFocusDownId(add.getId());
                add.setNextFocusUpId(button.getId());
            }
            button.setNextFocusUpId(label.getId());
        } else if (add != null) {
            label.setNextFocusDownId(add.getId());
            add.setNextFocusUpId(label.getId());
        }
    }

    /** Called by MainActivity while a manual playlist refresh runs. */
    static void setRefreshBusy(MainActivity main, boolean busy) {
        TextView label = main.findViewById(R.id.activeLabel);
        if (label == null || !(label.getParent() instanceof ViewGroup)) return;
        View existing = ((ViewGroup) label.getParent()).findViewWithTag(REFRESH_TAG);
        if (!(existing instanceof TextView)) return;
        TextView button = (TextView) existing;
        button.setText(busy ? REFRESH_BUSY_LABEL : REFRESH_LABEL);
        button.setEnabled(!busy);
        button.setAlpha(busy ? 0.7f : 1.0f);
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
        addButton.setTextColor(AccentTheme.accent(main));
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
            String prefix = ids.get(i).equals(active) ? "✓ " : "    ";
            entries.add(prefix + names.get(i));
        }
        entries.add("＋ Neue Playlist hinzufügen");

        AlertDialog manager = new AlertDialog.Builder(main)
                .setTitle("Wiedergabelisten (" + ids.size() + ")")
                .setItems(entries.toArray(new String[0]), (dialog, which) -> {
                    if (which < ids.size()) {
                        showEntry(main, prefs, ids.get(which));
                    } else {
                        add(main, prefs);
                    }
                })
                .setNegativeButton("Schließen", null)
                .show();
        Tv.styleDialog(manager);
    }

    /** Per-playlist actions: use it, rename it or delete just this one. */
    private static void showEntry(MainActivity main, Prefs prefs, String id) {
        final boolean isActive = id.equals(prefs.activeProfileId());
        String name = prefs.profileDisplayName(id);
        final boolean canRefresh = isActive && prefs.profileHasAccount(id);
        final List<String> actions = new ArrayList<>();
        actions.add(isActive ? "✓ Wird verwendet" : "▶ Diese Playlist verwenden");
        if (canRefresh) actions.add(REFRESH_LABEL);
        actions.add("✎ Umbenennen");
        actions.add("🗑 Löschen");
        final int refreshIndex = canRefresh ? 1 : -1;
        final int offset = canRefresh ? 1 : 0;
        final int accent = AccentTheme.accent(main);
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<String>(
                main, android.R.layout.select_dialog_item, android.R.id.text1, actions) {
            @Override
            public View getView(int position, View convertView, ViewGroup group) {
                View row = super.getView(position, convertView, group);
                TextView text = row.findViewById(android.R.id.text1);
                if (text != null) {
                    if (position == refreshIndex) {
                        text.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_refresh, 0, 0, 0);
                        text.setCompoundDrawablePadding(dp(main, 12));
                        text.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(accent));
                    } else {
                        text.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
                    }
                }
                return row;
            }
        };
        AlertDialog entry = new AlertDialog.Builder(main)
                .setTitle(name)
                .setAdapter(adapter, (dialog, which) -> {
                    if (which == 0) activate(main, prefs, id);
                    else if (which == refreshIndex) main.refreshPlaylist();
                    else if (which == 1 + offset) rename(main, prefs, id);
                    else confirmDelete(main, prefs, id);
                })
                .setNegativeButton("Zurück", (dialog, which) -> showManager(main, new Prefs(main)))
                .show();
        Tv.styleDialog(entry);
    }

    static void activate(MainActivity main, Prefs prefs, String id) {
        if (id.equals(prefs.activeProfileId())) return;
        if (prefs.setActiveProfile(id)) {
            resetAppState();
            main.recreate();
        }
    }

    private static void rename(MainActivity main, Prefs prefs, String id) {
        final EditText input = new EditText(main);
        input.setSingleLine(true);
        input.setText(prefs.profileDisplayName(id));
        input.setSelectAllOnFocus(true);
        input.setTextColor(main.getColor(R.color.fg));
        AlertDialog dialog = new AlertDialog.Builder(main)
                .setTitle("Playlist umbenennen")
                .setView(input)
                .setPositiveButton("Speichern", (d, which) -> {
                    String label = input.getText() == null ? "" : input.getText().toString();
                    if (prefs.renameProfile(id, label)) {
                        bindLabel(main, new Prefs(main));
                        showManager(main, new Prefs(main));
                    }
                })
                .setNegativeButton("Abbrechen", null)
                .show();
        Tv.styleDialog(dialog);
        input.requestFocus();
    }

    private static void add(MainActivity main, Prefs prefs) {
        // Reuse an already empty active entry instead of piling up empty playlists.
        if (prefs.profileHasAccount(prefs.activeProfileId()) || prefs.profileCount() == 0) {
            prefs.createProfile(null);
        }
        main.getSharedPreferences(PREFS, 0).edit().putBoolean(OPEN_SETTINGS, true).apply();
        resetAppState();
        main.recreate();
    }

    private static void confirmDelete(MainActivity main, Prefs prefs, String id) {
        String name = prefs.profileDisplayName(id);
        final boolean wasActive = id.equals(prefs.activeProfileId());
        AlertDialog confirm = new AlertDialog.Builder(main)
                .setTitle("Playlist löschen")
                .setMessage("„" + name + "“ wirklich von diesem Gerät löschen? Andere Playlists bleiben erhalten.")
                .setPositiveButton("Löschen", (dialog, which) -> {
                    File cache = prefs.catalogCacheFile(main.getCacheDir(), id);
                    if (cache != null && cache.isFile()) cache.delete();
                    prefs.deleteProfile(id);
                    if (wasActive) {
                        resetAppState();
                        main.recreate();
                    } else {
                        bindLabel(main, new Prefs(main));
                        showManager(main, new Prefs(main));
                    }
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
