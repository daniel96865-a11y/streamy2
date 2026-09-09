package app.streamy2;

import android.Manifest;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInstaller;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.media3.common.C;
import androidx.media3.ui.DefaultTimeBar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import app.streamy2.ChannelAdapter;
import app.streamy2.MainActivity;
import app.streamy2.Models;
import app.streamy2.Theme;
import app.streamy2.Updates;
import com.google.android.material.appbar.AppBarLayout;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import org.json.JSONObject;

/* loaded from: classes.dex */
public class MainActivity extends AppCompatActivity implements ChannelAdapter.Listener {
    private LinearLayout accentRow;
    private TextView activeLabel;
    private ChannelAdapter adapter;
    private XtreamApi api;
    private AppBarLayout appBar;
    private TextView appVersion;
    private BrowserController browser;
    private TextView btnPlayDetail;
    private View btnTop;
    private TextView bufHigh;
    private TextView bufLow;
    private TextView bufMax;
    private TextView bufNorm;
    private TextView chipCat;
    private TextView chipSort;
    private View chips;
    private TextView detailCast;
    private RecyclerView detailEps;
    private TextView detailHint;
    private Models.Media detailMedia;
    private TextView detailMeta;
    private View detailPane;
    private TextView detailPlot;
    private ImageView detailPoster;
    private TextView detailTitle;
    private TextView empty;
    private PickAdapter epAdapter;
    private TextView epg12;
    private TextView epg24;
    private TextView epg6;
    private TextView epgStatus;
    private TextView fmtHls;
    private TextView fmtTs;
    private EpgGuide guide;
    private EditText inEpgUrl;
    private EditText inName;
    private EditText inPass;
    private EditText inUrl;
    private EditText inUser;
    private long kinoSearchGen;
    private RecyclerView list;
    private ProgressBar loading;
    private boolean lockSearchFocus;
    private View mainPane;
    private View pairPane;
    private boolean passVisible;
    private Updates.Info pendingUpdate;
    private PickAdapter pickAdapter;
    private RecyclerView pickList;
    private View pickerPane;
    private TextView pickerTitle;
    private TextView playerLiveAuto;
    private TextView playerLiveExo;
    private TextView playerLiveVlc;
    private TextView playerVavooAuto;
    private TextView playerVavooExo;
    private TextView playerVavooVlc;
    private Prefs prefs;
    private TextView resizeFit;
    private TextView resizeZoom;
    private long resumeAt;
    private EditText search;
    private Models.Media seriesOpen;
    private View settingsPane;
    private TextView status;
    private TextView tabBrowser;
    private TextView tabKino;
    private TextView tabLive;
    private TextView tabMovies;
    private TextView tabSeries;
    private TextView tabVavoo;
    private View topChrome;
    private View updateBanner;
    private TextView updateText;
    private volatile boolean vavooBusy;
    private static final ExecutorService IO = Executors.newFixedThreadPool(6);
    private static final ExecutorService EPG = Executors.newSingleThreadExecutor(new ThreadFactory() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda111
        @Override // java.util.concurrent.ThreadFactory
        public final Thread newThread(Runnable runnable) {
            return MainActivity.lambda$static$0(runnable);
        }
    });
    private static final Handler UI = new Handler(Looper.getMainLooper());
    private final Set<String> epgAsked = new HashSet();
    private Models.Catalog catalog = new Models.Catalog();
    private int tab = 0;
    private String catId = "all";
    private int sort = 0;
    private String query = "";
    private final Set<String> plotFetch = new HashSet();
    private long updateDownloadId = -1;
    private final Runnable searchRun = new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda35
        @Override // java.lang.Runnable
        public final void run() {
            MainActivity.this.renderList();
        }
    };
    private final BroadcastReceiver downloadDone = new BroadcastReceiver() { // from class: app.streamy2.MainActivity.1
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            long longExtra = intent.getLongExtra("extra_download_id", -1L);
            if (longExtra != MainActivity.this.updateDownloadId || longExtra < 0) {
                return;
            }
            Uri uriForDownloadedFile = ((DownloadManager) MainActivity.this.getSystemService("download")).getUriForDownloadedFile(longExtra);
            if (uriForDownloadedFile == null) {
                Toast.makeText(MainActivity.this, "Download unvollständig", 1).show();
            } else {
                MainActivity.this.installDownloaded(uriForDownloadedFile);
            }
        }
    };
    private final Runnable epgLater = new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda36
        @Override // java.lang.Runnable
        public final void run() {
            MainActivity.this.lambda$new$106();
        }
    };

    static /* synthetic */ Thread lambda$static$0(Runnable runnable) {
        Thread thread = new Thread(runnable, "epg");
        thread.setPriority(1);
        thread.setDaemon(true);
        return thread;
    }

    @Override // androidx.fragment.app.FragmentActivity, androidx.activity.ComponentActivity, androidx.core.app.ComponentActivity, android.app.Activity
    protected void onCreate(Bundle bundle) {
        View view;
        super.onCreate(bundle);
        setContentView(R.layout.activity_main);
        this.prefs = new Prefs(this);
        if (App.guide == null) {
            App.guide = new EpgGuide();
        }
        this.guide = App.guide;
        this.mainPane = findViewById(R.id.mainPane);
        this.settingsPane = findViewById(R.id.settingsPane);
        this.chips = findViewById(R.id.chips);
        this.detailPane = findViewById(R.id.detailPane);
        this.tabLive = (TextView) findViewById(R.id.tabLive);
        this.tabMovies = (TextView) findViewById(R.id.tabMovies);
        this.tabSeries = (TextView) findViewById(R.id.tabSeries);
        this.tabVavoo = (TextView) findViewById(R.id.tabVavoo);
        this.tabKino = (TextView) findViewById(R.id.tabKino);
        this.tabBrowser = (TextView) findViewById(R.id.tabBrowser);
        this.chipCat = (TextView) findViewById(R.id.chipCat);
        this.chipSort = (TextView) findViewById(R.id.chipSort);
        this.empty = (TextView) findViewById(R.id.empty);
        this.status = (TextView) findViewById(R.id.status);
        this.activeLabel = (TextView) findViewById(R.id.activeLabel);
        this.fmtHls = (TextView) findViewById(R.id.fmtHls);
        this.fmtTs = (TextView) findViewById(R.id.fmtTs);
        this.epgStatus = (TextView) findViewById(R.id.epgStatus);
        this.epg6 = (TextView) findViewById(R.id.epg6);
        this.epg12 = (TextView) findViewById(R.id.epg12);
        this.epg24 = (TextView) findViewById(R.id.epg24);
        this.resizeFit = (TextView) findViewById(R.id.resizeFit);
        this.resizeZoom = (TextView) findViewById(R.id.resizeZoom);
        this.playerLiveAuto = (TextView) findViewById(R.id.playerLiveAuto);
        this.playerLiveExo = (TextView) findViewById(R.id.playerLiveExo);
        this.playerLiveVlc = (TextView) findViewById(R.id.playerLiveVlc);
        this.playerVavooAuto = (TextView) findViewById(R.id.playerVavooAuto);
        this.playerVavooExo = (TextView) findViewById(R.id.playerVavooExo);
        this.playerVavooVlc = (TextView) findViewById(R.id.playerVavooVlc);
        this.bufLow = (TextView) findViewById(R.id.bufLow);
        this.bufNorm = (TextView) findViewById(R.id.bufNorm);
        this.bufHigh = (TextView) findViewById(R.id.bufHigh);
        this.bufMax = (TextView) findViewById(R.id.bufMax);
        this.inEpgUrl = (EditText) findViewById(R.id.inEpgUrl);
        this.accentRow = (LinearLayout) findViewById(R.id.accentRow);
        this.search = (EditText) findViewById(R.id.search);
        this.inName = (EditText) findViewById(R.id.inName);
        this.inUrl = (EditText) findViewById(R.id.inUrl);
        this.inUser = (EditText) findViewById(R.id.inUser);
        this.inPass = (EditText) findViewById(R.id.inPass);
        this.loading = (ProgressBar) findViewById(R.id.loading);
        this.list = (RecyclerView) findViewById(R.id.list);
        this.pickerPane = findViewById(R.id.pickerPane);
        this.pickerTitle = (TextView) findViewById(R.id.pickerTitle);
        this.pickList = (RecyclerView) findViewById(R.id.pickList);
        this.updateBanner = findViewById(R.id.updateBanner);
        this.updateText = (TextView) findViewById(R.id.updateText);
        this.appVersion = (TextView) findViewById(R.id.appVersion);
        this.appBar = (AppBarLayout) findViewById(R.id.appBar);
        this.topChrome = findViewById(R.id.topChrome);
        this.btnTop = findViewById(R.id.btnTop);
        this.browser = new BrowserController(this);
        boolean z = true;
        if (Tv.isTv(this) && (view = this.topChrome) != null && (view.getLayoutParams() instanceof AppBarLayout.LayoutParams)) {
            AppBarLayout.LayoutParams layoutParams = (AppBarLayout.LayoutParams) this.topChrome.getLayoutParams();
            layoutParams.setScrollFlags(0);
            this.topChrome.setLayoutParams(layoutParams);
            AppBarLayout appBarLayout = this.appBar;
            if (appBarLayout != null) {
                appBarLayout.setExpanded(true, false);
            }
        }
        this.adapter = new ChannelAdapter(this);
        this.list.setLayoutManager(new LinearLayoutManager(this));
        this.list.setAdapter(this.adapter);
        this.list.setHasFixedSize(true);
        this.list.setItemViewCacheSize(24);
        this.list.setItemAnimator(null);
        this.list.setDescendantFocusability(262144);
        this.list.setFocusable(true);
        this.list.setNestedScrollingEnabled(!Tv.isTv(this));
        this.list.getRecycledViewPool().setMaxRecycledViews(0, 18);
        if (Tv.isTv(this)) {
            this.list.getViewTreeObserver().addOnGlobalFocusChangeListener(new ViewTreeObserver.OnGlobalFocusChangeListener() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda68
                @Override // android.view.ViewTreeObserver.OnGlobalFocusChangeListener
                public final void onGlobalFocusChanged(View view2, View view3) {
                    MainActivity.this.lambda$onCreate$1(view2, view3);
                }
            });
        }
        this.pickAdapter = new PickAdapter();
        this.pickList.setLayoutManager(new LinearLayoutManager(this));
        this.pickList.setAdapter(this.pickAdapter);
        this.pickList.setDescendantFocusability(262144);
        this.list.addOnScrollListener(new RecyclerView.OnScrollListener() { // from class: app.streamy2.MainActivity.2
            @Override // androidx.recyclerview.widget.RecyclerView.OnScrollListener
            public void onScrollStateChanged(RecyclerView recyclerView, int i) {
                ImageView imageView;
                Images.setScrolling(i != 0);
                if (i == 0) {
                    MainActivity.this.loadVisibleEpg();
                    int childCount = recyclerView.getChildCount();
                    for (int i2 = 0; i2 < childCount; i2++) {
                        View childAt = recyclerView.getChildAt(i2);
                        if (childAt != null && (imageView = (ImageView) childAt.findViewById(R.id.logo)) != null) {
                            Images.resume(imageView);
                        }
                    }
                }
            }

            @Override // androidx.recyclerview.widget.RecyclerView.OnScrollListener
            public void onScrolled(RecyclerView recyclerView, int i, int i2) {
                LinearLayoutManager linearLayoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                int findFirstVisibleItemPosition = linearLayoutManager == null ? 0 : linearLayoutManager.findFirstVisibleItemPosition();
                boolean z2 = findFirstVisibleItemPosition > 4;
                if (MainActivity.this.btnTop != null) {
                    MainActivity.this.btnTop.setVisibility(z2 ? 0 : 8);
                }
                if (Tv.isTv(MainActivity.this) || MainActivity.this.appBar == null) {
                    return;
                }
                if (findFirstVisibleItemPosition <= 0) {
                    MainActivity.this.appBar.setExpanded(true, true);
                } else if (i2 > 8) {
                    MainActivity.this.appBar.setExpanded(false, true);
                } else if (i2 < -8) {
                    MainActivity.this.appBar.setExpanded(true, true);
                }
            }
        });
        this.tabLive.setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda80
            @Override // android.view.View.OnClickListener
            public final void onClick(View view2) {
                MainActivity.this.lambda$onCreate$2(view2);
            }
        });
        this.tabMovies.setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda92
            @Override // android.view.View.OnClickListener
            public final void onClick(View view2) {
                MainActivity.this.lambda$onCreate$3(view2);
            }
        });
        this.tabSeries.setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda104
            @Override // android.view.View.OnClickListener
            public final void onClick(View view2) {
                MainActivity.this.lambda$onCreate$4(view2);
            }
        });
        TextView textView = this.tabVavoo;
        if (textView != null) {
            textView.setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda110
                @Override // android.view.View.OnClickListener
                public final void onClick(View view2) {
                    MainActivity.this.lambda$onCreate$5(view2);
                }
            });
        }
        TextView textView2 = this.tabKino;
        if (textView2 != null) {
            textView2.setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda112
                @Override // android.view.View.OnClickListener
                public final void onClick(View view2) {
                    MainActivity.this.lambda$onCreate$6(view2);
                }
            });
        }
        this.tabBrowser.setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda113
            @Override // android.view.View.OnClickListener
            public final void onClick(View view2) {
                MainActivity.this.lambda$onCreate$7(view2);
            }
        });
        View view2 = this.btnTop;
        if (view2 != null) {
            view2.setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda114
                @Override // android.view.View.OnClickListener
                public final void onClick(View view3) {
                    MainActivity.this.lambda$onCreate$8(view3);
                }
            });
        }
        findViewById(R.id.btnSettings).setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda115
            @Override // android.view.View.OnClickListener
            public final void onClick(View view3) {
                MainActivity.this.lambda$onCreate$9(view3);
            }
        });
        findViewById(R.id.btnCloseSettings).setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda116
            @Override // android.view.View.OnClickListener
            public final void onClick(View view3) {
                MainActivity.this.lambda$onCreate$10(view3);
            }
        });
        this.chipCat.setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda69
            @Override // android.view.View.OnClickListener
            public final void onClick(View view3) {
                MainActivity.this.lambda$onCreate$11(view3);
            }
        });
        this.chipSort.setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda70
            @Override // android.view.View.OnClickListener
            public final void onClick(View view3) {
                MainActivity.this.lambda$onCreate$12(view3);
            }
        });
        findViewById(R.id.btnTest).setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda71
            @Override // android.view.View.OnClickListener
            public final void onClick(View view3) {
                MainActivity.this.lambda$onCreate$13(view3);
            }
        });
        findViewById(R.id.btnSave).setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda72
            @Override // android.view.View.OnClickListener
            public final void onClick(View view3) {
                MainActivity.this.lambda$onCreate$14(view3);
            }
        });
        findViewById(R.id.btnDemo).setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda73
            @Override // android.view.View.OnClickListener
            public final void onClick(View view3) {
                MainActivity.this.lambda$onCreate$15(view3);
            }
        });
        View findViewById = findViewById(R.id.btnSendTv);
        if (findViewById != null) {
            findViewById.setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda74
                @Override // android.view.View.OnClickListener
                public final void onClick(View view3) {
                    MainActivity.this.lambda$onCreate$16(view3);
                }
            });
        }
        View findViewById2 = findViewById(R.id.btnGetPin);
        if (findViewById2 != null) {
            findViewById2.setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda75
                @Override // android.view.View.OnClickListener
                public final void onClick(View view3) {
                    MainActivity.this.lambda$onCreate$17(view3);
                }
            });
        }
        this.pairPane = findViewById(R.id.pairPane);
        View findViewById3 = findViewById(R.id.btnPairClose);
        if (findViewById3 != null) {
            findViewById3.setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda76
                @Override // android.view.View.OnClickListener
                public final void onClick(View view3) {
                    MainActivity.this.lambda$onCreate$18(view3);
                }
            });
        }
        View findViewById4 = findViewById(R.id.btnPairGo);
        if (findViewById4 != null) {
            findViewById4.setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda77
                @Override // android.view.View.OnClickListener
                public final void onClick(View view3) {
                    MainActivity.this.lambda$onCreate$19(view3);
                }
            });
        }
        this.fmtHls.setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda79
            @Override // android.view.View.OnClickListener
            public final void onClick(View view3) {
                MainActivity.this.lambda$onCreate$20(view3);
            }
        });
        this.fmtTs.setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda81
            @Override // android.view.View.OnClickListener
            public final void onClick(View view3) {
                MainActivity.this.lambda$onCreate$21(view3);
            }
        });
        this.resizeFit.setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda82
            @Override // android.view.View.OnClickListener
            public final void onClick(View view3) {
                MainActivity.this.lambda$onCreate$22(view3);
            }
        });
        this.resizeZoom.setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda83
            @Override // android.view.View.OnClickListener
            public final void onClick(View view3) {
                MainActivity.this.lambda$onCreate$23(view3);
            }
        });
        this.playerLiveAuto.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view3) { MainActivity.this.setPlayerLiveEngine("auto"); }
        });
        this.playerLiveExo.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view3) { MainActivity.this.setPlayerLiveEngine("exo"); }
        });
        this.playerLiveVlc.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view3) { MainActivity.this.setPlayerLiveEngine("vlc"); }
        });
        this.playerVavooAuto.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view3) { MainActivity.this.setPlayerVavooEngine("auto"); }
        });
        this.playerVavooExo.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view3) { MainActivity.this.setPlayerVavooEngine("exo"); }
        });
        this.playerVavooVlc.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view3) { MainActivity.this.setPlayerVavooEngine("vlc"); }
        });
        this.bufLow.setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda87
            @Override // android.view.View.OnClickListener
            public final void onClick(View view3) {
                MainActivity.this.lambda$onCreate$27(view3);
            }
        });
        this.bufNorm.setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda88
            @Override // android.view.View.OnClickListener
            public final void onClick(View view3) {
                MainActivity.this.lambda$onCreate$28(view3);
            }
        });
        this.bufHigh.setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda90
            @Override // android.view.View.OnClickListener
            public final void onClick(View view3) {
                MainActivity.this.lambda$onCreate$29(view3);
            }
        });
        this.bufMax.setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda91
            @Override // android.view.View.OnClickListener
            public final void onClick(View view3) {
                MainActivity.this.lambda$onCreate$30(view3);
            }
        });
        this.epg6.setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda93
            @Override // android.view.View.OnClickListener
            public final void onClick(View view3) {
                MainActivity.this.lambda$onCreate$31(view3);
            }
        });
        this.epg12.setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda94
            @Override // android.view.View.OnClickListener
            public final void onClick(View view3) {
                MainActivity.this.lambda$onCreate$32(view3);
            }
        });
        this.epg24.setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda95
            @Override // android.view.View.OnClickListener
            public final void onClick(View view3) {
                MainActivity.this.lambda$onCreate$33(view3);
            }
        });
        findViewById(R.id.btnRefreshEpg).setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda96
            @Override // android.view.View.OnClickListener
            public final void onClick(View view3) {
                MainActivity.this.lambda$onCreate$34(view3);
            }
        });
        bindFold(R.id.headAccount, R.id.bodyAccount, R.id.chevAccount);
        bindFold(R.id.headPlay, R.id.bodyPlay, R.id.chevPlay);
        bindFold(R.id.headEpg, R.id.bodyEpg, R.id.chevEpg);
        bindFold(R.id.headLook, R.id.bodyLook, R.id.chevLook);
        buildAccentRow();
        this.search.addTextChangedListener(new TextWatcher() { // from class: app.streamy2.MainActivity.3
            @Override // android.text.TextWatcher
            public void afterTextChanged(Editable editable) {
            }

            @Override // android.text.TextWatcher
            public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {
            }

            @Override // android.text.TextWatcher
            public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                MainActivity.this.query = charSequence == null ? "" : charSequence.toString();
                if (MainActivity.this.search != null) {
                    MainActivity.this.search.removeCallbacks(MainActivity.this.searchRun);
                    MainActivity.this.search.postDelayed(MainActivity.this.searchRun, 140L);
                } else {
                    MainActivity.this.renderList();
                }
            }
        });
        this.search.setOnFocusChangeListener(new View.OnFocusChangeListener() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda97
            @Override // android.view.View.OnFocusChangeListener
            public final void onFocusChange(View view3, boolean z2) {
                MainActivity.this.lambda$onCreate$35(view3, z2);
            }
        });
        TextView textView3 = (TextView) findViewById(R.id.btnShowPass);
        if (textView3 != null) {
            textView3.setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda98
                @Override // android.view.View.OnClickListener
                public final void onClick(View view3) {
                    MainActivity.this.lambda$onCreate$36(view3);
                }
            });
        }
        this.inName.setText(this.prefs.name());
        this.inUrl.setText(this.prefs.url());
        this.inUser.setText(this.prefs.user());
        this.inPass.setText(this.prefs.pass());
        this.inEpgUrl.setText(this.prefs.epgUrl());
        applyTheme();
        refreshActive();
        updateEpgStatus();
        Tv.focusTree(this.mainPane);
        Tv.focusTree(this.settingsPane);
        TextView textView4 = (TextView) findViewById(R.id.btnClosePicker);
        if (textView4 != null) {
            textView4.setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda99
                @Override // android.view.View.OnClickListener
                public final void onClick(View view3) {
                    MainActivity.this.lambda$onCreate$37(view3);
                }
            });
        }
        Tv.phoneX(textView4);
        View view3 = this.pickerPane;
        if ((view3 instanceof ViewGroup) && ((ViewGroup) view3).getChildCount() > 0) {
            View view4 = this.pickerPane;
            Tv.phoneSheet(view4, ((ViewGroup) view4).getChildAt(0));
        }
        this.pickerPane.setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda101
            @Override // android.view.View.OnClickListener
            public final void onClick(View view5) {
                MainActivity.this.lambda$onCreate$38(view5);
            }
        });
        TextView textView5 = (TextView) findViewById(R.id.btnCloseDetail);
        if (textView5 != null) {
            textView5.setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda102
                @Override // android.view.View.OnClickListener
                public final void onClick(View view5) {
                    MainActivity.this.lambda$onCreate$39(view5);
                }
            });
        }
        Tv.phoneX(textView5);
        View view5 = this.detailPane;
        if (view5 != null) {
            view5.setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda103
                @Override // android.view.View.OnClickListener
                public final void onClick(View view6) {
                    MainActivity.this.lambda$onCreate$40(view6);
                }
            });
            View findViewById5 = findViewById(R.id.detailCard);
            if (findViewById5 != null) {
                findViewById5.setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda105
                    @Override // android.view.View.OnClickListener
                    public final void onClick(View view6) {
                        MainActivity.this.lambda$onCreate$41(view6);
                    }
                });
                Tv.phoneSheet(this.detailPane, findViewById5);
                Tv.focusTree(findViewById5);
            }
        }
        this.detailTitle = (TextView) findViewById(R.id.detailTitle);
        this.detailMeta = (TextView) findViewById(R.id.detailMeta);
        this.detailCast = (TextView) findViewById(R.id.detailCast);
        this.detailPlot = (TextView) findViewById(R.id.detailPlot);
        this.detailHint = (TextView) findViewById(R.id.detailHint);
        this.btnPlayDetail = (TextView) findViewById(R.id.btnPlayDetail);
        this.detailPoster = (ImageView) findViewById(R.id.detailPoster);
        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.detailEps);
        this.detailEps = recyclerView;
        if (recyclerView != null) {
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
            PickAdapter pickAdapter = new PickAdapter();
            this.epAdapter = pickAdapter;
            this.detailEps.setAdapter(pickAdapter);
        }
        TextView textView6 = this.btnPlayDetail;
        if (textView6 != null) {
            textView6.setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda106
                @Override // android.view.View.OnClickListener
                public final void onClick(View view6) {
                    MainActivity.this.lambda$onCreate$42(view6);
                }
            });
        }
        findViewById(R.id.btnUpdateNow).setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda107
            @Override // android.view.View.OnClickListener
            public final void onClick(View view6) {
                MainActivity.this.lambda$onCreate$43(view6);
            }
        });
        findViewById(R.id.btnCheckUpdate).setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda108
            @Override // android.view.View.OnClickListener
            public final void onClick(View view6) {
                MainActivity.this.lambda$onCreate$44(view6);
            }
        });
        View findViewById6 = findViewById(R.id.btnOpenApk);
        if (findViewById6 != null) {
            findViewById6.setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda109
                @Override // android.view.View.OnClickListener
                public final void onClick(View view6) {
                    MainActivity.this.lambda$onCreate$45(view6);
                }
            });
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(this.downloadDone, new IntentFilter("android.intent.action.DOWNLOAD_COMPLETE"), 2);
        } else {
            registerReceiver(this.downloadDone, new IntentFilter("android.intent.action.DOWNLOAD_COMPLETE"));
        }
        TextView textView7 = this.appVersion;
        if (textView7 != null) {
            textView7.setText("Version 3.07  (127)");
        }
        TextView textView8 = (TextView) findViewById(R.id.pickerHint);
        if (textView8 != null) {
            if (Tv.isTv(this)) {
                textView8.setText("Blauer Rand = Position  ·  OK wählen  ·  Zurück schließt");
            } else {
                textView8.setVisibility(8);
            }
        }
        if (this.prefs.hasXtream()) {
            loadXtream(false);
        } else {
            Models.Catalog build = DemoCatalog.build();
            this.catalog = build;
            App.live = build.live;
            renderList();
            loadVavoo();
            loadKino();
            prefetchEpg();
        }
        requestNotifyPermission();
        checkUpdate(false);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(z) { // from class: app.streamy2.MainActivity.4
            @Override // androidx.activity.OnBackPressedCallback
            public void handleOnBackPressed() {
                if (MainActivity.this.pairPane != null && MainActivity.this.pairPane.getVisibility() == 0) {
                    MainActivity.this.hidePair();
                    return;
                }
                if (MainActivity.this.browser != null && MainActivity.this.browser.visible() && MainActivity.this.browser.onBack()) {
                    return;
                }
                if (MainActivity.this.detailPane != null && MainActivity.this.detailPane.getVisibility() == 0) {
                    MainActivity.this.hideDetail();
                    return;
                }
                if (MainActivity.this.pickerPane != null && MainActivity.this.pickerPane.getVisibility() == 0) {
                    MainActivity.this.hidePicker();
                    return;
                }
                if (MainActivity.this.settingsPane.getVisibility() == 0) {
                    MainActivity.this.showSettings(false);
                    return;
                }
                if (MainActivity.this.seriesOpen != null) {
                    MainActivity.this.seriesOpen = null;
                    MainActivity.this.renderList();
                } else {
                    if (SystemClock.uptimeMillis() - MainActivity.this.resumeAt < 1200) {
                        return;
                    }
                    MainActivity.this.moveTaskToBack(true);
                }
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$1(View view, View view2) {
        syncTvChrome(view2);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$2(View view) {
        setTab(0);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$3(View view) {
        setTab(1);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$4(View view) {
        setTab(2);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$5(View view) {
        setTab(4);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$6(View view) {
        setTab(5);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$7(View view) {
        setTab(3);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$8(View view) {
        RecyclerView recyclerView = this.list;
        if (recyclerView != null) {
            recyclerView.scrollToPosition(0);
        }
        AppBarLayout appBarLayout = this.appBar;
        if (appBarLayout != null) {
            appBarLayout.setExpanded(true, true);
        }
        this.btnTop.setVisibility(8);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$9(View view) {
        showSettings(true);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$10(View view) {
        showSettings(false);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$11(View view) {
        pickCategory();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$12(View view) {
        pickSort();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$13(View view) {
        connect(false);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$14(View view) {
        connect(true);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$15(View view) {
        this.prefs.clearAccount();
        this.api = null;
        App.api = null;
        synchronized (this.epgAsked) {
            this.epgAsked.clear();
        }
        this.guide.clear();
        Models.Catalog build = DemoCatalog.build();
        this.catalog = build;
        App.live = build.live;
        setStatus("Ohne Playlist · Vavoo und Megakino bleiben", false);
        updateEpgStatus();
        showSettings(false);
        renderList();
        loadVavoo();
        loadKino();
        setTab(4);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$16(View view) {
        startPairHost();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$17(View view) {
        startPairClient();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$18(View view) {
        hidePair();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$19(View view) {
        loadPairPin();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$20(View view) {
        setFmt("hls");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$21(View view) {
        setFmt("ts");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$22(View view) {
        setResize("fit");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$23(View view) {
        setResize("zoom");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$24(View view) {
        setPlayerLiveEngine("auto");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$25(View view) {
        setPlayerLiveEngine("exo");
    }


    private void openMuxTest(boolean vlc) {
        try {
            if (vlc) {
                PlayerActivity.openTestVlc(this);
            } else {
                PlayerActivity.openTestExo(this);
            }
        } catch (Throwable t) {
            Toast.makeText(this, "Teststream fehlgeschlagen", 0).show();
        }
    }

    private void showPlaybackDiag() {
        try {
            LocalHls.start();
            String hls = LocalHls.isReady() ? ("bereit · Port " + LocalHls.getPort()) : "nicht bereit";
            boolean lib = VlcFactory.isAvailable();
            String msg = "Engine Live: " + this.prefs.playerLive() + " · Vavoo: " + this.prefs.playerVavoo() + " · Sonst: " + this.prefs.player()
                    + "\nlibVLC: " + (lib ? "ja" : "nein")
                    + "\nLocalHls: " + hls
                    + "\nHost: —"
                    + "\nExo-Fehler: —"
                    + "\nVLC-Fehler: " + ((VlcFactory.lastError == null || VlcFactory.lastError.isEmpty()) ? "—" : VlcFactory.lastError);
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Wiedergabe-Diagnose")
                    .setMessage(msg)
                    .setPositiveButton("OK", null)
                    .show();
        } catch (Throwable t) {
            Toast.makeText(this, "Diagnose fehlgeschlagen", 0).show();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$26(View view) {
        setPlayerLiveEngine("vlc");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$27(View view) {
        setBuffer("low");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$28(View view) {
        setBuffer("normal");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$29(View view) {
        setBuffer("high");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$30(View view) {
        setBuffer("max");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$31(View view) {
        setEpgInterval(6);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$32(View view) {
        setEpgInterval(12);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$33(View view) {
        setEpgInterval(24);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$34(View view) {
        refreshXmltv(true);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$35(View view, boolean z) {
        this.lockSearchFocus = z;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$36(View view) {
        this.passVisible = !this.passVisible;
        int selectionEnd = this.inPass.getSelectionEnd();
        this.inPass.setInputType(this.passVisible ? 145 : 129);
        this.inPass.setTypeface(Typeface.DEFAULT);
        if (selectionEnd >= 0) {
            EditText editText = this.inPass;
            editText.setSelection(Math.min(selectionEnd, editText.length()));
        }
        ((TextView) view).setText(this.passVisible ? "Verbergen" : "Anzeigen");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$37(View view) {
        hidePicker();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$38(View view) {
        hidePicker();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$39(View view) {
        hideDetail();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$40(View view) {
        if (Tv.isTv(this) || this.detailMedia == null) {
            return;
        }
        playDetail();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$41(View view) {
        if (Tv.isTv(this) || this.detailMedia == null) {
            return;
        }
        RecyclerView recyclerView = this.detailEps;
        if (recyclerView == null || recyclerView.getVisibility() != 0) {
            playDetail();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$42(View view) {
        playDetail();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$43(View view) {
        installUpdate();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$44(View view) {
        checkUpdate(true);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$45(View view) {
        openApkInBrowser();
    }

    @Override // androidx.appcompat.app.AppCompatActivity, androidx.core.app.ComponentActivity, android.app.Activity, android.view.Window.Callback
    public boolean dispatchKeyEvent(KeyEvent keyEvent) {
        RecyclerView recyclerView;
        BrowserController browserController;
        View view;
        View view2;
        View view3;
        BrowserController browserController2;
        View view4;
        RecyclerView recyclerView2;
        RecyclerView recyclerView3;
        BrowserController browserController3 = this.browser;
        if (browserController3 != null && browserController3.handleKey(keyEvent)) {
            return true;
        }
        if (keyEvent.getAction() == 0 && (view4 = this.detailPane) != null && view4.getVisibility() == 0) {
            int keyCode = keyEvent.getKeyCode();
            if (keyCode == 126 || keyCode == 85) {
                playDetail();
                return true;
            }
            if (keyCode == 23 || keyCode == 66 || keyCode == 160) {
                View currentFocus = getCurrentFocus();
                if (currentFocus != null && currentFocus.getId() == R.id.btnCloseDetail) {
                    hideDetail();
                    return true;
                }
                if (currentFocus != null && (recyclerView2 = this.detailEps) != null && recyclerView2.getVisibility() == 0 && (currentFocus == (recyclerView3 = this.detailEps) || recyclerView3.findFocus() == currentFocus)) {
                    return super.dispatchKeyEvent(keyEvent);
                }
                playDetail();
                return true;
            }
        }
        if (keyEvent.getAction() == 0 && Tv.isTv(this) && this.list != null && (((view2 = this.detailPane) == null || view2.getVisibility() != 0) && (view3 = this.settingsPane) != null && view3.getVisibility() != 0 && ((browserController2 = this.browser) == null || !browserController2.visible()))) {
            int keyCode2 = keyEvent.getKeyCode();
            View findFocus = this.list.findFocus();
            int childAdapterPosition = findFocus == null ? -1 : this.list.getChildAdapterPosition(findFocus);
            if (childAdapterPosition == -1 && findFocus != null && (findFocus.getParent() instanceof View)) {
                childAdapterPosition = this.list.getChildAdapterPosition((View) findFocus.getParent());
            }
            ChannelAdapter channelAdapter = this.adapter;
            Object item = channelAdapter != null ? channelAdapter.getItem(childAdapterPosition) : null;
            if (item instanceof Models.Media) {
                Models.Media media = (Models.Media) item;
                if (keyCode2 == 23 || keyCode2 == 66 || keyCode2 == 160 || keyCode2 == 165 || keyCode2 == 172) {
                    onMedia(media);
                    return true;
                }
                if (keyCode2 == 126 || keyCode2 == 85) {
                    onPlayMedia(media);
                    return true;
                }
            }
        }
        if (keyEvent.getAction() == 0 && keyEvent.getKeyCode() == 19 && (recyclerView = this.list) != null && recyclerView.hasFocus() && (((browserController = this.browser) == null || !browserController.visible()) && (view = this.settingsPane) != null && view.getVisibility() != 0)) {
            View findFocus2 = this.list.findFocus();
            if ((findFocus2 != null ? this.list.getChildAdapterPosition(findFocus2) : -1) == 0) {
                setTvChrome(true);
                TextView textView = this.chipCat;
                if (textView != null && textView.getVisibility() == 0) {
                    this.chipCat.requestFocus();
                    return true;
                }
                TextView textView2 = this.tabLive;
                if (textView2 != null) {
                    textView2.requestFocus();
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(keyEvent);
    }

    private void syncTvChrome(View view) {
        if (!Tv.isTv(this) || this.list == null) {
            return;
        }
        View view2 = this.settingsPane;
        if (view2 != null && view2.getVisibility() == 0) {
            setTvChrome(true);
            return;
        }
        BrowserController browserController = this.browser;
        if (browserController != null && browserController.visible()) {
            setTvChrome(true);
            return;
        }
        if (view == null) {
            return;
        }
        if (!isUnder(this.list, view)) {
            setTvChrome(true);
            return;
        }
        int childAdapterPosition = this.list.getChildAdapterPosition(view);
        if (childAdapterPosition == -1 && (view.getParent() instanceof View)) {
            childAdapterPosition = this.list.getChildAdapterPosition((View) view.getParent());
        }
        setTvChrome(childAdapterPosition <= 0);
    }

    private void setTvChrome(boolean z) {
        View view;
        if (!Tv.isTv(this) || (view = this.topChrome) == null) {
            return;
        }
        int i = z ? 0 : 8;
        if (view.getVisibility() == i) {
            return;
        }
        this.topChrome.setVisibility(i);
        AppBarLayout appBarLayout = this.appBar;
        if (appBarLayout != null) {
            appBarLayout.setExpanded(true, false);
        }
    }

    private static boolean isUnder(View view, View view2) {
        while (view2 != null) {
            if (view2 == view) {
                return true;
            }
            if (!(view2.getParent() instanceof View)) {
                return false;
            }
            view2 = (View) view2.getParent();
        }
        return false;
    }

    @Override // androidx.fragment.app.FragmentActivity, android.app.Activity
    protected void onResume() {
        RecyclerView recyclerView;
        super.onResume();
        this.resumeAt = SystemClock.uptimeMillis();
        try {
            BrowserController browserController = this.browser;
            if (browserController != null && browserController.visible()) {
                this.browser.resume();
            }
            if (this.adapter == null || this.tab != 0 || (recyclerView = this.list) == null) {
                return;
            }
            recyclerView.post(new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda40
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$onResume$46();
                }
            });
        } catch (Throwable unused) {
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onResume$46() {
        try {
            this.adapter.notifyEpg();
        } catch (Throwable unused) {
        }
    }

    @Override // androidx.fragment.app.FragmentActivity, android.app.Activity
    protected void onPause() {
        BrowserController browserController = this.browser;
        if (browserController != null) {
            browserController.pause();
        }
        super.onPause();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showSettings(boolean z) {
        hideKeyboard();
        if (!z) {
            this.prefs.setEpgUrl(text(this.inEpgUrl));
        }
        this.settingsPane.setVisibility(z ? 0 : 8);
        this.mainPane.setVisibility(z ? 8 : 0);
        if (z) {
            boolean z2 = !this.prefs.hasXtream();
            setFold(R.id.bodyAccount, R.id.chevAccount, z2);
            View findViewById = findViewById(z2 ? R.id.inName : R.id.headAccount);
            if (findViewById == null) {
                findViewById = findViewById(R.id.btnCloseSettings);
            }
            if (findViewById != null) {
                Objects.requireNonNull(findViewById);
                findViewById.post(new MainActivity$$ExternalSyntheticLambda60(findViewById));
            }
        } else if (this.list != null) {
            focusFirstRow();
        }
        if (z) {
            refreshActive();
            updateEpgStatus();
        } else {
            renderList();
        }
    }

    private void hideKeyboard() {
        View currentFocus = getCurrentFocus();
        if (currentFocus == null) {
            currentFocus = this.settingsPane;
        }
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService("input_method");
        if (inputMethodManager == null || currentFocus == null) {
            return;
        }
        inputMethodManager.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
    }

    private void startPairHost() {
        String text = text(this.inUrl);
        String text2 = text(this.inUser);
        String text3 = text(this.inPass);
        if (text.isEmpty()) {
            text = this.prefs.url();
        }
        if (text2.isEmpty()) {
            text2 = this.prefs.user();
        }
        if (text3.isEmpty()) {
            text3 = this.prefs.pass();
        }
        if (text.isEmpty() || text2.isEmpty() || text3.isEmpty()) {
            Toast.makeText(this, "Zuerst Playlist speichern", 1).show();
            return;
        }
        String text4 = text(this.inName);
        if (text4.isEmpty()) {
            text4 = this.prefs.name();
        }
        String startHost = Pairing.startHost(Pairing.pack(text4, text, text2, text3, text(this.inEpgUrl)));
        TextView textView = (TextView) findViewById(R.id.pairPin);
        TextView textView2 = (TextView) findViewById(R.id.pairTitle);
        TextView textView3 = (TextView) findViewById(R.id.pairHint);
        EditText editText = (EditText) findViewById(R.id.inPin);
        View findViewById = findViewById(R.id.btnPairGo);
        if (textView != null) {
            textView.setVisibility(0);
            textView.setText(startHost);
        }
        if (editText != null) {
            editText.setVisibility(8);
        }
        if (findViewById != null) {
            findViewById.setVisibility(8);
        }
        if (textView2 != null) {
            textView2.setText("PIN auf dem TV eingeben");
        }
        if (textView3 != null) {
            textView3.setText("Am TV: Einstellungen → PIN vom Handy eingeben.\nBeide Geräte im gleichen WLAN.");
        }
        View view = this.pairPane;
        if (view != null) {
            view.setVisibility(0);
        }
        getWindow().addFlags(128);
        View findViewById2 = findViewById(R.id.btnPairClose);
        if (findViewById2 != null) {
            Objects.requireNonNull(findViewById2);
            findViewById2.post(new MainActivity$$ExternalSyntheticLambda60(findViewById2));
        }
    }

    private void startPairClient() {
        Pairing.stop();
        TextView textView = (TextView) findViewById(R.id.pairPin);
        TextView textView2 = (TextView) findViewById(R.id.pairTitle);
        TextView textView3 = (TextView) findViewById(R.id.pairHint);
        final EditText editText = (EditText) findViewById(R.id.inPin);
        View findViewById = findViewById(R.id.btnPairGo);
        if (textView != null) {
            textView.setVisibility(8);
        }
        if (editText != null) {
            editText.setVisibility(0);
            editText.setText("");
        }
        if (findViewById != null) {
            findViewById.setVisibility(0);
        }
        if (textView2 != null) {
            textView2.setText("PIN vom Handy");
        }
        if (textView3 != null) {
            textView3.setText("PIN mit den Zahlentasten eingeben, dann Playlist holen.\nGleiches WLAN wie das Handy.");
        }
        View view = this.pairPane;
        if (view != null) {
            view.setVisibility(0);
        }
        if (editText != null) {
            Objects.requireNonNull(editText);
            editText.post(new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda14
                @Override // java.lang.Runnable
                public final void run() {
                    editText.requestFocus();
                }
            });
        }
    }

    private void loadPairPin() {
        EditText editText = (EditText) findViewById(R.id.inPin);
        final String trim = editText == null ? "" : editText.getText().toString().trim();
        if (trim.length() != 6) {
            Toast.makeText(this, "6-stelligen PIN eingeben", 0).show();
            return;
        }
        TextView textView = (TextView) findViewById(R.id.pairHint);
        if (textView != null) {
            textView.setText("Suche Handy…");
        }
        IO.execute(new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda44
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$loadPairPin$48(trim);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$loadPairPin$48(String str) {
        final String fetch = Pairing.fetch(str, 14000);
        UI.post(new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda58
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$loadPairPin$47(fetch);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* renamed from: applyPairJson, reason: merged with bridge method [inline-methods] */
    public void lambda$loadPairPin$47(String str) {
        if (str == null || str.length() < 8) {
            TextView textView = (TextView) findViewById(R.id.pairHint);
            if (textView != null) {
                textView.setText("Handy nicht gefunden. Gleiches WLAN? PIN noch offen?");
            }
            Toast.makeText(this, "Keine Playlist empfangen", 1).show();
            return;
        }
        try {
            JSONObject jSONObject = new JSONObject(str);
            String optString = jSONObject.optString("url", "");
            String optString2 = jSONObject.optString("user", "");
            String optString3 = jSONObject.optString("pass", "");
            if (optString.isEmpty() || optString2.isEmpty()) {
                throw new Exception("leer");
            }
            EditText editText = this.inName;
            if (editText != null) {
                editText.setText(jSONObject.optString("name", ""));
            }
            EditText editText2 = this.inUrl;
            if (editText2 != null) {
                editText2.setText(optString);
            }
            EditText editText3 = this.inUser;
            if (editText3 != null) {
                editText3.setText(optString2);
            }
            EditText editText4 = this.inPass;
            if (editText4 != null) {
                editText4.setText(optString3);
            }
            EditText editText5 = this.inEpgUrl;
            if (editText5 != null) {
                editText5.setText(jSONObject.optString("epg", ""));
            }
            hidePair();
            Toast.makeText(this, "Playlist empfangen", 0).show();
            connect(true);
        } catch (Exception unused) {
            Toast.makeText(this, "PIN ungültig", 1).show();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void hidePair() {
        Pairing.stop();
        View view = this.pairPane;
        if (view != null) {
            view.setVisibility(8);
        }
        getWindow().clearFlags(128);
    }

    private void refreshActive() {
        Theme.Accent accent = Theme.get(this.prefs.accent());
        if (this.prefs.hasXtream()) {
            String name = this.prefs.name();
            if (name == null || name.isEmpty()) {
                name = this.prefs.user();
            }
            this.activeLabel.setText("Aktive Playlist: " + name + "  ·  Verbunden");
            this.activeLabel.setTextColor(accent.color);
            TextView textView = (TextView) findViewById(R.id.accHint);
            if (textView != null) {
                textView.setText(name);
                return;
            }
            return;
        }
        this.activeLabel.setText("Aktive Playlist: Streamy Demo");
        this.activeLabel.setTextColor(getColor(R.color.muted));
        TextView textView2 = (TextView) findViewById(R.id.accHint);
        if (textView2 != null) {
            textView2.setText("Nicht verbunden");
        }
    }

    private void setFmt(String str) {
        this.prefs.setFormat(str);
        boolean equals = "ts".equals(str);
        Theme.Accent accent = Theme.get(this.prefs.accent());
        paintChip(this.fmtHls, !equals, accent);
        paintChip(this.fmtTs, equals, accent);
    }

    private void setResize(String str) {
        this.prefs.setResize(str);
        paintResize();
    }

    private void setPlayerEngine(String str) {
        this.prefs.setPlayer(str);
        paintPlayer();
    }

    private void setPlayerLiveEngine(String str) {
        this.prefs.setPlayerLive(str);
        // Keep legacy player in sync for VOD/other until user sets it elsewhere
        this.prefs.setPlayer(str);
        paintPlayer();
    }

    private void setPlayerVavooEngine(String str) {
        this.prefs.setPlayerVavoo(str);
        paintPlayer();
    }

    private void setBuffer(String str) {
        this.prefs.setBuffer(str);
        paintBuffer();
    }

    private void paintPlayer() {
        Theme.Accent accent = Theme.get(this.prefs.accent());
        String live = this.prefs.playerLive();
        paintChip(this.playerLiveAuto, "auto".equals(live), accent);
        paintChip(this.playerLiveExo, "exo".equals(live), accent);
        paintChip(this.playerLiveVlc, "vlc".equals(live), accent);
        String vavoo = this.prefs.playerVavoo();
        paintChip(this.playerVavooAuto, "auto".equals(vavoo), accent);
        paintChip(this.playerVavooExo, "exo".equals(vavoo), accent);
        paintChip(this.playerVavooVlc, "vlc".equals(vavoo), accent);
    }

    /** Non-auto engine string for PlayerActivity, or null for Auto. */
    private String forceEngineFor(boolean vavoo) {
        String pref = vavoo ? this.prefs.playerVavoo() : this.prefs.playerLive();
        if ("vlc".equals(pref) || "exo".equals(pref)) {
            return pref;
        }
        return null;
    }

    private void paintBuffer() {
        Theme.Accent accent = Theme.get(this.prefs.accent());
        String buffer = this.prefs.buffer();
        paintChip(this.bufLow, "low".equals(buffer), accent);
        paintChip(this.bufNorm, "normal".equals(buffer), accent);
        paintChip(this.bufHigh, "high".equals(buffer), accent);
        paintChip(this.bufMax, "max".equals(buffer), accent);
    }

    private void paintResize() {
        Theme.Accent accent = Theme.get(this.prefs.accent());
        boolean equals = "zoom".equals(this.prefs.resize());
        paintChip(this.resizeFit, !equals, accent);
        paintChip(this.resizeZoom, equals, accent);
    }

    private void setEpgInterval(int i) {
        this.prefs.setEpgIntervalHours(i);
        paintEpgInterval();
    }

    private void paintEpgInterval() {
        Theme.Accent accent = Theme.get(this.prefs.accent());
        int epgIntervalHours = this.prefs.epgIntervalHours();
        paintChip(this.epg6, epgIntervalHours == 6, accent);
        paintChip(this.epg12, epgIntervalHours == 12, accent);
        paintChip(this.epg24, epgIntervalHours == 24, accent);
    }

    private void bindFold(int i, final int i2, final int i3) {
        final View findViewById = findViewById(i);
        final View findViewById2 = findViewById(i2);
        if (findViewById == null || findViewById2 == null) {
            return;
        }
        findViewById.setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda53
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$bindFold$49(findViewById2, i2, i3, findViewById, view);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$bindFold$49(View view, int i, int i2, View view2, View view3) {
        boolean z = view.getVisibility() != 0;
        setFold(i, i2, z);
        if (z) {
            View firstFocusable = firstFocusable(view);
            if (firstFocusable != null) {
                Objects.requireNonNull(firstFocusable);
                firstFocusable.post(new MainActivity$$ExternalSyntheticLambda60(firstFocusable));
                return;
            } else {
                view2.requestFocus();
                return;
            }
        }
        view2.requestFocus();
    }

    private void setFold(int i, int i2, boolean z) {
        View findViewById = findViewById(i);
        TextView textView = (TextView) findViewById(i2);
        if (findViewById != null) {
            findViewById.setVisibility(z ? 0 : 8);
        }
        if (textView != null) {
            textView.setText(z ? "▲" : "▼");
        }
        relinkFolds();
    }

    private void relinkFolds() {
        View findViewById = findViewById(R.id.bodyAccount);
        View findViewById2 = findViewById(R.id.bodyPlay);
        View findViewById3 = findViewById(R.id.bodyEpg);
        View findViewById4 = findViewById(R.id.bodyLook);
        View findViewById5 = findViewById(R.id.headAccount);
        View findViewById6 = findViewById(R.id.headPlay);
        View findViewById7 = findViewById(R.id.headEpg);
        View findViewById8 = findViewById(R.id.headLook);
        if (findViewById5 != null) {
            findViewById5.setNextFocusDownId(open(findViewById) ? R.id.inName : R.id.headPlay);
        }
        if (findViewById6 != null) {
            findViewById6.setNextFocusDownId(open(findViewById2) ? R.id.fmtHls : R.id.headEpg);
            findViewById6.setNextFocusUpId(R.id.headAccount);
        }
        if (findViewById7 != null) {
            findViewById7.setNextFocusDownId(open(findViewById3) ? R.id.epg6 : R.id.headLook);
            findViewById7.setNextFocusUpId(R.id.headPlay);
        }
        if (findViewById8 != null) {
            findViewById8.setNextFocusDownId(open(findViewById4) ? R.id.accentRow : R.id.btnCheckUpdate);
            findViewById8.setNextFocusUpId(R.id.headEpg);
        }
    }

    private static boolean open(View view) {
        return view != null && view.getVisibility() == 0;
    }

    private View firstFocusable(View view) {
        if (view == null) {
            return null;
        }
        if (view.isFocusable() && view.getVisibility() == 0 && ((view instanceof EditText) || view.isClickable())) {
            return view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View firstFocusable = firstFocusable(viewGroup.getChildAt(i));
                if (firstFocusable != null) {
                    return firstFocusable;
                }
            }
        }
        return null;
    }

    private void paintChip(TextView textView, boolean z, Theme.Accent accent) {
        if (textView == null) {
            return;
        }
        textView.setBackgroundResource(z ? R.drawable.bg_btn : R.drawable.bg_btn_sec);
        textView.setTextColor(z ? accent.onColor : getColor(R.color.fg));
    }

    private void setTab(int i) {
        this.tab = i;
        this.seriesOpen = null;
        this.catId = i == 4 ? "vavoo" : "all";
        paintTabs();
        if (i == 3) {
            showBrowser(true);
        } else {
            showBrowser(false);
            if (i == 5) {
                loadKino();
            }
            if (i == 4) {
                loadVavoo();
            }
            renderList();
            RecyclerView recyclerView = this.list;
            if (recyclerView != null) {
                recyclerView.scrollToPosition(0);
            }
        }
        AppBarLayout appBarLayout = this.appBar;
        if (appBarLayout != null) {
            appBarLayout.setExpanded(true, true);
        }
        setTvChrome(true);
        View view = this.btnTop;
        if (view != null) {
            view.setVisibility(8);
        }
    }

    private void showBrowser(boolean z) {
        EditText editText = this.search;
        if (editText != null) {
            editText.setVisibility(z ? 8 : 0);
        }
        View view = this.chips;
        if (view != null) {
            view.setVisibility(z ? 8 : 0);
        }
        RecyclerView recyclerView = this.list;
        if (recyclerView != null) {
            recyclerView.setVisibility(z ? 8 : 0);
        }
        TextView textView = this.empty;
        if (textView != null && z) {
            textView.setVisibility(8);
        }
        View view2 = this.btnTop;
        if (view2 != null && z) {
            view2.setVisibility(8);
        }
        View findViewById = findViewById(R.id.topChrome);
        if (findViewById != null && (findViewById.getLayoutParams() instanceof AppBarLayout.LayoutParams)) {
            AppBarLayout.LayoutParams layoutParams = (AppBarLayout.LayoutParams) findViewById.getLayoutParams();
            if (z || Tv.isTv(this)) {
                layoutParams.setScrollFlags(0);
                AppBarLayout appBarLayout = this.appBar;
                if (appBarLayout != null) {
                    appBarLayout.setExpanded(true, false);
                }
            } else {
                layoutParams.setScrollFlags(21);
            }
            findViewById.setLayoutParams(layoutParams);
        }
        BrowserController browserController = this.browser;
        if (browserController != null) {
            if (z) {
                browserController.show();
            } else {
                browserController.hide();
            }
        }
    }

    private void paintTabs() {
        colorTab(this.tabLive, this.tab == 0);
        colorTab(this.tabMovies, this.tab == 1);
        colorTab(this.tabSeries, this.tab == 2);
        colorTab(this.tabVavoo, this.tab == 4);
        colorTab(this.tabKino, this.tab == 5);
        colorTab(this.tabBrowser, this.tab == 3);
        View view = this.chips;
        if (view == null) {
            return;
        }
        if (this.tab == 3) {
            view.setVisibility(8);
            TextView textView = this.chipSort;
            if (textView != null) {
                textView.setVisibility(8);
                return;
            }
            return;
        }
        view.setVisibility(this.seriesOpen == null ? 0 : 8);
        TextView textView2 = this.chipSort;
        if (textView2 != null) {
            textView2.setVisibility(0);
        }
        TextView textView3 = this.chipCat;
        if (textView3 != null) {
            textView3.setVisibility(this.tab == 4 ? 8 : 0);
        }
    }

    private void colorTab(TextView textView, boolean z) {
        if (textView == null) {
            return;
        }
        textView.setTextColor(z ? Theme.get(this.prefs.accent()).color : getColor(R.color.muted));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void renderList() {
        String str;
        if (this.tab == 3) {
            return;
        }
        try {
            int i = 0;
            this.chips.setVisibility(0);
            if (this.seriesOpen != null) {
                ArrayList arrayList = new ArrayList();
                if (this.seriesOpen.episodes != null) {
                    for (Models.Episode episode : this.seriesOpen.episodes) {
                        if (episode != null) {
                            Models.Media media = new Models.Media();
                            media.id = "ep:" + episode.id;
                            media.name = episode.title == null ? "" : episode.title;
                            media.genre = "Staffel " + episode.season + " · Folge " + episode.episode;
                            media.streamUrl = episode.streamUrl;
                            media.poster = this.seriesOpen.poster;
                            arrayList.add(media);
                        }
                    }
                }
                this.adapter.setMedia(arrayList);
                this.chipCat.setText(this.seriesOpen.name);
                TextView textView = this.empty;
                if (!arrayList.isEmpty()) {
                    i = 8;
                }
                textView.setVisibility(i);
                this.empty.setText("Keine Folgen.");
                focusFirstRow();
                return;
            }
            int i2 = this.tab;
            str = "Nichts gefunden.";
            if (i2 == 0 || i2 == 4) {
                if (i2 == 4) {
                    this.catId = "vavoo";
                }
                List<Models.Channel> filterLive = filterLive();
                this.adapter.setChannels(filterLive);
                TextView textView2 = this.empty;
                if (!filterLive.isEmpty()) {
                    i = 8;
                }
                textView2.setVisibility(i);
                this.empty.setText(this.tab == 4 ? "Vavoo wird geladen…" : "Nichts gefunden.");
                this.chipCat.setText(this.tab == 4 ? "Vavoo Deutschland" : catName(this.catalog.liveCats, this.catId));
                this.chipSort.setText(sortLabel());
                focusFirstRow();
                return;
            }
            if (i2 == 1) {
                List<Models.Media> filterMedia = filterMedia(this.catalog.vod, this.catalog.vodCats);
                this.adapter.setMedia(filterMedia);
                TextView textView3 = this.empty;
                if (!filterMedia.isEmpty()) {
                    i = 8;
                }
                textView3.setVisibility(i);
                this.empty.setText("Nichts gefunden.");
                this.chipCat.setText(catName(this.catalog.vodCats, this.catId));
                this.chipSort.setText(sortLabel());
                focusFirstRow();
                return;
            }
            if (i2 == 2) {
                List<Models.Media> filterMedia2 = filterMedia(this.catalog.series, this.catalog.seriesCats);
                this.adapter.setMedia(filterMedia2);
                TextView textView4 = this.empty;
                if (!filterMedia2.isEmpty()) {
                    i = 8;
                }
                textView4.setVisibility(i);
                this.empty.setText("Nichts gefunden.");
                this.chipCat.setText(catName(this.catalog.seriesCats, this.catId));
                this.chipSort.setText(sortLabel());
                focusFirstRow();
                return;
            }
            if (i2 == 5) {
                List<Models.Media> filterKino = filterKino();
                this.adapter.setMedia(filterKino);
                TextView textView5 = this.empty;
                if (!filterKino.isEmpty()) {
                    i = 8;
                }
                textView5.setVisibility(i);
                TextView textView6 = this.empty;
                if (!MegaKino.loaded) {
                    str = "Megakino wird geladen…";
                }
                textView6.setText(str);
                this.chipCat.setText(kinoCatLabel());
                this.chipSort.setText(sortLabel());
                focusFirstRow();
                String str2 = this.query;
                if (str2 == null || str2.trim().length() < 2) {
                    return;
                }
                searchKinoRemote();
            }
        } catch (Exception unused) {
        }
    }

    private void focusFirstRow() {
        if (this.list == null) {
            return;
        }
        EditText editText = this.search;
        if (editText == null || !(editText.hasFocus() || this.lockSearchFocus)) {
            this.list.post(new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda118
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$focusFirstRow$50();
                }
            });
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$focusFirstRow$50() {
        View childAt;
        EditText editText = this.search;
        if ((editText == null || !(editText.hasFocus() || this.lockSearchFocus)) && this.list.getChildCount() > 0 && (childAt = this.list.getChildAt(0)) != null && !childAt.hasFocus()) {
            childAt.requestFocus();
        }
    }

    private List<Models.Channel> filterLive() {
        String str = this.query;
        String lowerCase = str == null ? "" : str.trim().toLowerCase(Locale.GERMAN);
        ArrayList arrayList = new ArrayList();
        Models.Catalog catalog = this.catalog;
        if (catalog != null && catalog.live != null) {
            for (Models.Channel channel : this.catalog.live) {
                if (channel != null) {
                    String str2 = channel.name == null ? "" : channel.name;
                    if ("all".equals(this.catId) || (channel.categoryId != null && this.catId.equals(channel.categoryId))) {
                        if (lowerCase.isEmpty() || str2.toLowerCase(Locale.GERMAN).contains(lowerCase)) {
                            arrayList.add(channel);
                        }
                    }
                }
            }
            try {
                if (this.sort == 1) {
                    arrayList.sort(Comparator.comparing(new Function() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda16
                        @Override // java.util.function.Function
                        public final Object apply(Object obj) {
                            String lowerCase2;
                            lowerCase2 = (((Models.Channel) obj).name == null ? "" : ((Models.Channel) obj).name).toLowerCase(Locale.GERMAN);
                            return lowerCase2;
                        }
                    }));
                }
                if (this.sort == 2) {
                    arrayList.sort(new Comparator() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda17
                        @Override // java.util.Comparator
                        public final int compare(Object obj, Object obj2) {
                            int compareTo;
                            compareTo = (((Models.Channel) obj2).name == null ? "" : ((Models.Channel) obj2).name).toLowerCase(Locale.GERMAN).compareTo((((Models.Channel) obj).name != null ? ((Models.Channel) obj).name : "").toLowerCase(Locale.GERMAN));
                            return compareTo;
                        }
                    });
                }
                if (this.sort == 3) {
                    arrayList.sort(Comparator.comparingInt(new ToIntFunction() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda18
                        @Override // java.util.function.ToIntFunction
                        public final int applyAsInt(Object obj) {
                            int i;
                            i = ((Models.Channel) obj).number;
                            return i;
                        }
                    }));
                }
            } catch (Exception unused) {
            }
        }
        return arrayList;
    }

    private List<Models.Media> filterMedia(List<Models.Media> list, List<Models.Category> list2) {
        String str = this.query;
        String lowerCase = str == null ? "" : str.trim().toLowerCase(Locale.GERMAN);
        ArrayList arrayList = new ArrayList();
        if (list == null) {
            return arrayList;
        }
        for (Models.Media media : list) {
            if (media != null) {
                String str2 = media.name == null ? "" : media.name;
                if ("all".equals(this.catId) || (media.categoryId != null && this.catId.equals(media.categoryId))) {
                    if (lowerCase.isEmpty() || str2.toLowerCase(Locale.GERMAN).contains(lowerCase)) {
                        arrayList.add(media);
                    }
                }
            }
        }
        try {
            if (this.sort == 1) {
                arrayList.sort(Comparator.comparing(new Function() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda55
                    @Override // java.util.function.Function
                    public final Object apply(Object obj) {
                        String lowerCase2;
                        lowerCase2 = (((Models.Media) obj).name == null ? "" : ((Models.Media) obj).name).toLowerCase(Locale.GERMAN);
                        return lowerCase2;
                    }
                }));
            }
            if (this.sort == 2) {
                arrayList.sort(new Comparator() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda57
                    @Override // java.util.Comparator
                    public final int compare(Object obj, Object obj2) {
                        int compareTo;
                        compareTo = (((Models.Media) obj2).name == null ? "" : ((Models.Media) obj2).name).toLowerCase(Locale.GERMAN).compareTo((((Models.Media) obj).name != null ? ((Models.Media) obj).name : "").toLowerCase(Locale.GERMAN));
                        return compareTo;
                    }
                });
            }
        } catch (Exception unused) {
        }
        return arrayList;
    }

    private String catName(List<Models.Category> list, String str) {
        if (!"all".equals(str) && list != null) {
            for (Models.Category category : list) {
                if (category != null && category.id != null && category.id.equals(str)) {
                    return category.name;
                }
            }
        }
        return "Alle Kategorien";
    }

    private String sortLabel() {
        int i = this.sort;
        if (i == 1) {
            return "A–Z";
        }
        if (i == 2) {
            return "Z–A";
        }
        if (i == 3) {
            return "Sender-Nr.";
        }
        return "Reihenfolge der Quelle";
    }

    private void pickCategory() {
        List<Models.Category> list;
        int i = this.tab;
        if (i == 4) {
            return;
        }
        int i2 = 0;
        if (i == 5) {
            String[] strArr = {"Alle", "Filme", "Serien"};
            final String[] strArr2 = {"all", "mk-films", "mk-serials"};
            int i3 = 0;
            while (i2 < 3) {
                if (strArr2[i2].equals(this.catId)) {
                    i3 = i2;
                }
                i2++;
            }
            showPicker("Kategorien", Arrays.asList(strArr), i3, new PickAdapter.OnPick() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda0
                @Override // app.streamy2.MainActivity.PickAdapter.OnPick
                public final void pick(int i4) {
                    MainActivity.this.lambda$pickCategory$56(strArr2, i4);
                }
            });
            return;
        }
        if (i == 0) {
            list = this.catalog.liveCats;
        } else if (i == 1) {
            list = this.catalog.vodCats;
        } else if (i != 2) {
            return;
        } else {
            list = this.catalog.seriesCats;
        }
        ArrayList arrayList = new ArrayList();
        final ArrayList arrayList2 = new ArrayList();
        arrayList.add("Alle Kategorien");
        arrayList2.add("all");
        int i4 = 0;
        while (i2 < list.size()) {
            arrayList.add(list.get(i2).name);
            arrayList2.add(list.get(i2).id);
            if (list.get(i2).id.equals(this.catId)) {
                i4 = i2 + 1;
            }
            i2++;
        }
        showPicker("Kategorien", arrayList, i4, new PickAdapter.OnPick() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda34
            @Override // app.streamy2.MainActivity.PickAdapter.OnPick
            public final void pick(int i5) {
                MainActivity.this.lambda$pickCategory$57(arrayList2, i5);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$pickCategory$56(String[] strArr, int i) {
        this.catId = strArr[i];
        renderList();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$pickCategory$57(List list, int i) {
        this.catId = (String) list.get(i);
        renderList();
    }

    private void pickSort() {
        showPicker("Sortierung", Arrays.asList("Reihenfolge der Quelle", "A–Z", "Z–A", "Sender-Nr."), this.sort, new PickAdapter.OnPick() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda22
            @Override // app.streamy2.MainActivity.PickAdapter.OnPick
            public final void pick(int i) {
                MainActivity.this.lambda$pickSort$58(i);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$pickSort$58(int i) {
        this.sort = i;
        renderList();
    }

    private void showPicker(String str, List<String> list, int i, final PickAdapter.OnPick onPick) {
        this.pickerTitle.setText(str);
        this.pickAdapter.set(list, i, new PickAdapter.OnPick() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda28
            @Override // app.streamy2.MainActivity.PickAdapter.OnPick
            public final void pick(int i2) {
                MainActivity.this.lambda$showPicker$59(onPick, i2);
            }
        });
        this.pickerPane.setVisibility(0);
        final int max = Math.max(0, i);
        this.pickList.post(new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda29
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$showPicker$61(max);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showPicker$59(PickAdapter.OnPick onPick, int i) {
        hidePicker();
        onPick.pick(i);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showPicker$61(final int i) {
        this.pickList.scrollToPosition(i);
        this.pickList.post(new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda27
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$showPicker$60(i);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$showPicker$60(int i) {
        RecyclerView.ViewHolder findViewHolderForAdapterPosition = this.pickList.findViewHolderForAdapterPosition(i);
        if (findViewHolderForAdapterPosition != null) {
            findViewHolderForAdapterPosition.itemView.requestFocus();
        } else {
            this.pickList.requestFocus();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void hidePicker() {
        View view = this.pickerPane;
        if (view != null) {
            view.setVisibility(8);
        }
        TextView textView = this.chipCat;
        if (textView != null) {
            textView.requestFocus();
        }
    }


    private void requestNotifyPermission() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                if (checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 4401);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void checkUpdate(final boolean z) {
        if (z) {
            try { Toast.makeText(this, "Suche Update…", 0).show(); } catch (Throwable ignored) {}
        }
        IO.execute(new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda122
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$checkUpdate$63(z);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$checkUpdate$63(final boolean z) {
        final Updates.Info fetch = Updates.fetch();
        UI.post(new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda15
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$checkUpdate$62(fetch, z);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* renamed from: applyUpdate, reason: merged with bridge method [inline-methods] */
    public void lambda$checkUpdate$62(Updates.Info info, boolean z) {
        if (info == null) {
            if (z) {
                Toast.makeText(this, "Update-Check fehlgeschlagen — Feed nicht erreichbar", 1).show();
            }
            return;
        }
        this.pendingUpdate = info;
        boolean z2 = (info == null || info.versionCode <= BuildConfig.VERSION_CODE || info.apkUrl == null || info.apkUrl.isEmpty()) ? false : true;
        if (this.updateBanner != null) {
            if (z2 && info.versionCode != this.prefs.skippedUpdate()) {
                this.updateBanner.setVisibility(0);
                this.updateText.setText("Version " + info.versionName + " verfügbar");
                Tv.focusTree(this.updateBanner);
            } else {
                this.updateBanner.setVisibility(8);
            }
        }
        if (z) {
            if (z2) {
                Toast.makeText(this, "Update " + info.versionName + " — Download startet", 0).show();
                installUpdate();
            } else {
                Toast.makeText(this, "Du bist auf dem neuesten Stand (" + BuildConfig.VERSION_NAME + ")", 0).show();
            }
        }
    }

    private void installUpdate() {
        Updates.Info info = this.pendingUpdate;
        if (info == null || info.apkUrl == null || this.pendingUpdate.apkUrl.isEmpty()) {
            Toast.makeText(this, "Kein Download-Link.", 0).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(this, "Unbekannte Apps erlauben, dann nochmal tippen.", 1).show();
            try {
                startActivity(new Intent("android.settings.MANAGE_UNKNOWN_APP_SOURCES", Uri.parse("package:" + getPackageName())));
                return;
            } catch (Exception unused) {
                return;
            }
        }
        IO.execute(new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda65
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$installUpdate$65();
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$installUpdate$65() {
        final String directApk = Updates.directApk(this.pendingUpdate.apkUrl);
        UI.post(new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda41
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$installUpdate$64(directApk);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* renamed from: enqueueUpdate, reason: merged with bridge method [inline-methods] */
    public void lambda$installUpdate$64(String str) {
        if (str == null || str.isEmpty()) {
            Toast.makeText(this, "Kein Download-Link.", 0).show();
            return;
        }
        try {
            DownloadManager downloadManager = (DownloadManager) getSystemService("download");
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(str));
            request.setTitle("Streamy 2 " + this.pendingUpdate.versionName);
            request.setDescription("Update wird geladen");
            request.setMimeType("application/vnd.android.package-archive");
            request.setNotificationVisibility(1);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(true);
            request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, "streamy2-update.apk");
            this.updateDownloadId = downloadManager.enqueue(request);
            Toast.makeText(this, "Download gestartet — oben in den Benachrichtigungen", 1).show();
        } catch (Exception unused) {
            openApkInBrowser();
        }
    }

    private void openApkInBrowser() {
        IO.execute(new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda30
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$openApkInBrowser$67();
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$openApkInBrowser$67() {
        Updates.Info info = this.pendingUpdate;
        if (info == null) {
            info = Updates.fetch();
        }
        final Updates.Info infoFinal = info;
        UI.post(new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda38
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$openApkInBrowser$66(infoFinal);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$openApkInBrowser$66(Updates.Info info) {
        if (info == null || info.apkUrl == null || info.apkUrl.isEmpty()) {
            Toast.makeText(this, "Kein APK-Link gefunden", 1).show();
            return;
        }
        try {
            Intent intent = new Intent("android.intent.action.VIEW", Uri.parse(info.apkUrl));
            intent.addFlags(268435456);
            startActivity(intent);
        } catch (Exception unused) {
            Toast.makeText(this, "Link: " + info.apkUrl, 1).show();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void installDownloaded(Uri uri) {
        try {
            Intent intent = new Intent("android.intent.action.VIEW");
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(268435457);
            Iterator<ResolveInfo> it = getPackageManager().queryIntentActivities(intent, 0).iterator();
            while (it.hasNext()) {
                grantUriPermission(it.next().activityInfo.packageName, uri, 1);
            }
            startActivity(intent);
        } catch (Exception e) {
            File file = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "streamy2-update.apk");
            if (file.exists()) {
                startApkInstall(file);
            } else {
                Toast.makeText(this, "Installieren nicht möglich: " + e.getMessage(), 1).show();
            }
        }
    }

    private void startApkInstall(File file) {
        // Primary: user-visible system installer via FileProvider
        try {
            installViaSystemInstaller(file);
            return;
        } catch (Exception primary) {
            try {
                installViaPackageInstaller(file);
            } catch (Exception secondary) {
                showInstallFailedDialog(primary.getMessage() != null ? primary.getMessage() : String.valueOf(secondary.getMessage()));
            }
        }
    }

    private void installViaSystemInstaller(File file) throws Exception {
        Uri uriForFile = FileProvider.getUriForFile(this, "app.streamy2.file", file);
        Intent intent2 = new Intent(Intent.ACTION_VIEW);
        intent2.setDataAndType(uriForFile, "application/vnd.android.package-archive");
        intent2.setClipData(ClipData.newRawUri("", uriForFile));
        intent2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        // Also support ACTION_INSTALL_PACKAGE where available
        try {
            Intent install = new Intent(Intent.ACTION_INSTALL_PACKAGE);
            install.setData(uriForFile);
            install.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
            install.putExtra(Intent.EXTRA_RETURN_RESULT, false);
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            Iterator<ResolveInfo> itInstall = getPackageManager().queryIntentActivities(install, 0).iterator();
            while (itInstall.hasNext()) {
                grantUriPermission(itInstall.next().activityInfo.packageName, uriForFile, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
            if (getPackageManager().queryIntentActivities(install, 0).size() > 0) {
                startActivity(install);
                return;
            }
        } catch (Exception ignored) {
        }
        Iterator<ResolveInfo> it = getPackageManager().queryIntentActivities(intent2, 0).iterator();
        while (it.hasNext()) {
            grantUriPermission(it.next().activityInfo.packageName, uriForFile, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        startActivity(intent2);
    }

    private void installViaPackageInstaller(File file) throws Exception {
        PackageInstaller packageInstaller = getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams sessionParams = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        sessionParams.setSize(file.length());
        int createSession = packageInstaller.createSession(sessionParams);
        PackageInstaller.Session openSession = packageInstaller.openSession(createSession);
        FileInputStream fileInputStream = new FileInputStream(file);
        OutputStream openWrite = openSession.openWrite("app.apk", 0L, file.length());
        byte[] bArr = new byte[65536];
        while (true) {
            int read = fileInputStream.read(bArr);
            if (read <= 0) {
                break;
            } else {
                openWrite.write(bArr, 0, read);
            }
        }
        openSession.fsync(openWrite);
        openWrite.close();
        fileInputStream.close();
        Intent intent = new Intent(this, (Class<?>) InstallReceiver.class);
        intent.setAction(InstallReceiver.ACTION);
        openSession.commit(PendingIntent.getBroadcast(this, createSession, intent, Build.VERSION.SDK_INT >= 31 ? 167772160 : C.BUFFER_FLAG_FIRST_SAMPLE).getIntentSender());
        openSession.close();
    }

    private void showInstallFailedDialog(String detail) {
        String msg = "Update konnte nicht installiert werden.\n\n"
                + "Falls die Signatur nicht übereinstimmt: Streamy 2 zuerst deinstallieren, dann die neue APK installieren.";
        if (detail != null && !detail.isEmpty()) {
            String lower = detail.toLowerCase(Locale.ROOT);
            if (lower.contains("signature") || lower.contains("incompatible") || lower.contains("update_incompatible")) {
                msg = "Die neue APK hat eine andere Signatur als die installierte App.\n\n"
                        + "Bitte Streamy 2 zuerst deinstallieren und danach die neue APK installieren.\n\n"
                        + detail;
            } else {
                msg = msg + "\n\n" + detail;
            }
        }
        try {
            new AlertDialog.Builder(this)
                    .setTitle("Update fehlgeschlagen")
                    .setMessage(msg)
                    .setPositiveButton("OK", null)
                    .show();
        } catch (Exception unused) {
            Toast.makeText(this, msg.replace('\n', ' '), Toast.LENGTH_LONG).show();
        }
    }

    @Override // androidx.appcompat.app.AppCompatActivity, androidx.fragment.app.FragmentActivity, android.app.Activity
    protected void onDestroy() {
        try {
            unregisterReceiver(this.downloadDone);
        } catch (Exception unused) {
        }
        BrowserController browserController = this.browser;
        if (browserController != null) {
            browserController.destroy();
        }
        Pairing.stop();
        super.onDestroy();
    }

    @Override // app.streamy2.ChannelAdapter.Listener
    public void onChannel(Models.Channel channel) {
        String str;
        if (channel.header) {
            return;
        }
        App.playing = channel;
        App.api = this.api;
        App.guide = this.guide;
        App.live = this.catalog.live;
        if (channel.epg != null && channel.epg.title != null) {
            str = channel.epg.title;
            if (channel.epg.start > 0 && channel.epg.end > 0) {
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat("HH:mm", Locale.GERMANY);
                str = str + "  ·  " + simpleDateFormat.format(new Date(channel.epg.start)) + "–" + simpleDateFormat.format(new Date(channel.epg.end));
            }
        } else {
            str = "";
        }
        String str2 = str;
        if (channel.vavooUrl != null && !channel.vavooUrl.isEmpty()) {
            try {
                String str3 = channel.vavooUrl;
                String str4 = channel.vavooUrl;
                String str5 = channel.name;
                if (str2.isEmpty()) {
                    str2 = "Vavoo";
                }
                PlayerActivity.open(this, str3, str4, str5, str2, true, forceEngineFor(true));
                return;
            } catch (Throwable unused) {
                Toast.makeText(this, "Player konnte nicht starten", 0).show();
                return;
            }
        }
        try {
            PlayerActivity.open(this, channel.hlsUrl, channel.tsUrl, channel.name, str2, true, forceEngineFor(false));
        } catch (Throwable unused2) {
            Toast.makeText(this, "Player konnte nicht starten", 0).show();
        }
    }

    @Override // app.streamy2.ChannelAdapter.Listener
    public void onMedia(Models.Media media) {
        if ("folder-live".equals(media.id)) {
            setTab(0);
            return;
        }
        if ("folder-vod".equals(media.id)) {
            setTab(1);
            return;
        }
        if ("folder-ser".equals(media.id)) {
            setTab(2);
            return;
        }
        if (media.id != null && media.id.startsWith("live:")) {
            PlayerActivity.open(this, media.streamUrl, media.plot, media.name, media.genre, true);
            return;
        }
        if (media.id != null && media.id.startsWith("ep:")) {
            PlayerActivity.open(this, media.streamUrl, null, media.name, media.genre, false);
            return;
        }
        View view = this.detailPane;
        if (view != null && view.getVisibility() == 0 && this.detailMedia != null && media != null && media.id != null && media.id.equals(this.detailMedia.id)) {
            playDetail();
        } else {
            openDetail(media);
        }
    }

    @Override // app.streamy2.ChannelAdapter.Listener
    public void onPlayMedia(Models.Media media) {
        if (media == null) {
            return;
        }
        if (media.series) {
            openDetail(media);
        } else if (MegaKino.owns(media)) {
            playMega(media, null);
        } else {
            PlayerActivity.open(this, media.streamUrl, null, media.name, join(media.genre, media.year), false);
        }
    }

    @Override // app.streamy2.ChannelAdapter.Listener
    public void onNeedPlot(final Models.Media media, final int i) {
        if (media == null || media.id == null) {
            return;
        }
        if (media.plot == null || media.plot.trim().isEmpty()) {
            if (MegaKino.owns(media)) {
                if (this.plotFetch.add(media.id)) {
                    IO.execute(new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda32
                        @Override // java.lang.Runnable
                        public final void run() {
                            MainActivity.this.lambda$onNeedPlot$69(media, i);
                        }
                    });
                }
            } else if (this.api != null && this.plotFetch.add(media.id)) {
                IO.execute(new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda33
                    @Override // java.lang.Runnable
                    public final void run() {
                        MainActivity.this.lambda$onNeedPlot$71(media, i);
                    }
                });
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onNeedPlot$69(final Models.Media media, final int i) {
        try {
            MegaKino.enrich(media);
        } catch (Throwable unused) {
        }
        UI.post(new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda49
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$onNeedPlot$68(media, i);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onNeedPlot$71(final Models.Media media, final int i) {
        try {
            this.api.enrich(media);
        } catch (Exception unused) {
        }
        UI.post(new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda2
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$onNeedPlot$70(media, i);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onNeedPlot$70(Models.Media media, int i) {
        if (media.plot == null || media.plot.trim().isEmpty()) {
            media.plot = "Keine Beschreibung.";
        }
        lambda$onNeedPlot$68(media, i);
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* renamed from: notifyPlot, reason: merged with bridge method [inline-methods] */
    public void lambda$onNeedPlot$68(Models.Media media, final int i) {
        if (media.plot == null || media.plot.trim().isEmpty()) {
            media.plot = "Keine Beschreibung.";
        }
        ChannelAdapter channelAdapter = this.adapter;
        if (channelAdapter == null || i < 0 || i >= channelAdapter.getItemCount()) {
            return;
        }
        RecyclerView recyclerView = this.list;
        if (recyclerView != null && recyclerView.isComputingLayout()) {
            this.list.post(new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda46
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$notifyPlot$72(i);
                }
            });
        } else {
            try {
                this.adapter.notifyItemChanged(i);
            } catch (Exception unused) {
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$notifyPlot$72(int i) {
        try {
            if (this.list.isComputingLayout()) {
                return;
            }
            this.adapter.notifyItemChanged(i);
        } catch (Exception unused) {
        }
    }

    private void openDetail(final Models.Media media) {
        if (media == null) {
            return;
        }
        this.detailMedia = media;
        bindDetail(media);
        View view = this.detailPane;
        if (view != null) {
            view.setVisibility(0);
        }
        Tv.focusTree(findViewById(R.id.detailCard));
        TextView textView = this.btnPlayDetail;
        if (textView != null) {
            textView.post(new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda120
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$openDetail$73();
                }
            });
        }
        if (MegaKino.owns(media)) {
            ProgressBar progressBar = this.loading;
            if (progressBar != null) {
                progressBar.setVisibility(0);
            }
            IO.execute(new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda121
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$openDetail$75(media);
                }
            });
            return;
        }
        if (this.api == null) {
            return;
        }
        this.loading.setVisibility(0);
        IO.execute(new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda1
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$openDetail$77(media);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$openDetail$73() {
        this.btnPlayDetail.setFocusable(true);
        this.btnPlayDetail.requestFocus();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$openDetail$75(final Models.Media media) {
        try {
            MegaKino.enrich(media);
        } catch (Throwable unused) {
        }
        UI.post(new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda61
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$openDetail$74(media);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$openDetail$74(Models.Media media) {
        ProgressBar progressBar = this.loading;
        if (progressBar != null) {
            progressBar.setVisibility(8);
        }
        if (this.detailMedia == media) {
            bindDetail(media);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$openDetail$77(final Models.Media media) {
        try {
            this.api.enrich(media);
        } catch (Exception unused) {
        }
        UI.post(new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda3
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$openDetail$76(media);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$openDetail$76(Models.Media media) {
        this.loading.setVisibility(8);
        if (this.detailMedia == media) {
            bindDetail(media);
        }
    }

    private void bindDetail(final Models.Media media) {
        TextView textView = this.detailTitle;
        if (textView != null) {
            textView.setText(media.name);
        }
        TextView textView2 = this.detailMeta;
        if (textView2 != null) {
            textView2.setText(detailLine(media));
        }
        if (this.detailCast != null) {
            String str = (media.director == null || media.director.isEmpty()) ? "" : "Regie: " + media.director;
            if (media.cast != null && !media.cast.isEmpty()) {
                str = (str.isEmpty() ? new StringBuilder("Mit: ") : new StringBuilder().append(str).append("\nMit: ")).append(media.cast).toString();
            }
            this.detailCast.setText(str);
            this.detailCast.setVisibility(str.isEmpty() ? 8 : 0);
        }
        if (this.detailPlot != null) {
            String trim = media.plot == null ? "" : media.plot.trim();
            TextView textView3 = this.detailPlot;
            if (trim.isEmpty()) {
                trim = "Beschreibung wird geladen…";
            }
            textView3.setText(trim);
        }
        if (this.detailHint != null) {
            if (Tv.isTv(this)) {
                this.detailHint.setVisibility(0);
                this.detailHint.setText("Nochmal OK startet  ·  Zurück schließt");
            } else {
                this.detailHint.setVisibility(0);
                this.detailHint.setText("Nochmal tippen oder Abspielen");
            }
        }
        ImageView imageView = this.detailPoster;
        if (imageView != null) {
            Images.load(imageView, media.poster);
        }
        TextView textView4 = this.btnPlayDetail;
        if (textView4 != null) {
            textView4.setText(media.series ? "Erste Folge" : "Abspielen");
        }
        if (this.detailEps == null || this.epAdapter == null) {
            return;
        }
        if (media.series && MegaKino.owns(media)) {
            final List<Models.Media> seasonsOf = MegaKino.seasonsOf(media);
            final List<Models.Episode> arrayList = media.episodes == null ? new ArrayList() : media.episodes;
            ArrayList arrayList2 = new ArrayList();
            final int size = seasonsOf.size() > 1 ? seasonsOf.size() : 0;
            for (int i = 0; i < size; i++) {
                Models.Media media2 = seasonsOf.get(i);
                arrayList2.add(((media2.id == null || !media2.id.equals(media.id)) ? "" : "● ") + "Staffel " + MegaKino.seasonOf(media2.name));
            }
            for (Models.Episode episode : arrayList) {
                arrayList2.add("Folge " + episode.episode + "  ·  " + (episode.title == null ? "" : episode.title));
            }
            this.epAdapter.set(arrayList2, -1, new PickAdapter.OnPick() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda45
                @Override // app.streamy2.MainActivity.PickAdapter.OnPick
                public final void pick(int i2) {
                    MainActivity.this.lambda$bindDetail$80(size, seasonsOf, media, arrayList, i2);
                }
            });
            this.detailEps.setVisibility(arrayList2.isEmpty() ? 8 : 0);
            return;
        }
        if (media.series && media.episodes != null && !media.episodes.isEmpty()) {
            ArrayList arrayList3 = new ArrayList();
            for (Models.Episode episode2 : media.episodes) {
                arrayList3.add("S" + episode2.season + " E" + episode2.episode + "  ·  " + episode2.title);
            }
            this.epAdapter.set(arrayList3, -1, new PickAdapter.OnPick() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda56
                @Override // app.streamy2.MainActivity.PickAdapter.OnPick
                public final void pick(int i2) {
                    MainActivity.this.lambda$bindDetail$81(media, i2);
                }
            });
            this.detailEps.setVisibility(0);
            return;
        }
        this.detailEps.setVisibility(8);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$bindDetail$80(int i, List list, Models.Media media, List list2, int i2) {
        if (i2 < i) {
            final Models.Media media2 = (Models.Media) list.get(i2);
            if (media2 == media) {
                return;
            }
            this.detailMedia = media2;
            if (media2.episodes == null || media2.episodes.isEmpty()) {
                IO.execute(new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda13
                    @Override // java.lang.Runnable
                    public final void run() {
                        MainActivity.this.lambda$bindDetail$79(media2);
                    }
                });
                return;
            } else {
                bindDetail(media2);
                return;
            }
        }
        int i3 = i2 - i;
        if (i3 < 0 || i3 >= list2.size()) {
            return;
        }
        playEpisode(media, (Models.Episode) list2.get(i3));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$bindDetail$79(final Models.Media media) {
        try {
            MegaKino.enrich(media);
        } catch (Throwable unused) {
        }
        UI.post(new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda119
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$bindDetail$78(media);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$bindDetail$78(Models.Media media) {
        if (this.detailMedia == media) {
            bindDetail(media);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$bindDetail$81(Models.Media media, int i) {
        if (i < 0 || i >= media.episodes.size()) {
            return;
        }
        playEpisode(media, media.episodes.get(i));
    }

    private static String detailLine(Models.Media media) {
        String join = join(media.year, media.genre);
        if (media.duration != null && !media.duration.isEmpty()) {
            join = join(join, formatDur(media.duration));
        }
        return (media.rating == null || media.rating.isEmpty()) ? join : join(join, "★ " + media.rating);
    }

    private static String formatDur(String str) {
        if (str == null) {
            return "";
        }
        String trim = str.trim();
        if (!trim.matches("\\d+")) {
            return trim;
        }
        try {
            int parseInt = Integer.parseInt(trim);
            if (parseInt <= 300) {
                return parseInt + " Min.";
            }
            return Math.round(parseInt / 60.0f) + " Min.";
        } catch (Exception unused) {
            return trim;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void hideDetail() {
        this.detailMedia = null;
        View view = this.detailPane;
        if (view != null) {
            view.setVisibility(8);
        }
    }

    private void playDetail() {
        final Models.Media media = this.detailMedia;
        if (media == null) {
            return;
        }
        if (media.series) {
            if (media.episodes != null && !media.episodes.isEmpty()) {
                playEpisode(media, media.episodes.get(0));
                return;
            } else if (MegaKino.owns(media)) {
                IO.execute(new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda50
                    @Override // java.lang.Runnable
                    public final void run() {
                        MainActivity.this.lambda$playDetail$83(media);
                    }
                });
                return;
            } else {
                Toast.makeText(this, "Keine Folgen geladen.", 0).show();
                return;
            }
        }
        if (MegaKino.owns(media)) {
            playMega(media, null);
        } else {
            PlayerActivity.open(this, media.streamUrl, null, media.name, join(media.genre, media.year), false);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$playDetail$83(final Models.Media media) {
        try {
            MegaKino.enrich(media);
        } catch (Throwable unused) {
        }
        UI.post(new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda54
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$playDetail$82(media);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$playDetail$82(Models.Media media) {
        if (media.episodes == null || media.episodes.isEmpty()) {
            playMega(media, null);
        } else {
            playEpisode(media, media.episodes.get(0));
        }
    }

    private void playEpisode(Models.Media media, Models.Episode episode) {
        if (episode == null) {
            return;
        }
        if (MegaKino.owns(media) || (episode.id != null && episode.id.startsWith("mkep:"))) {
            playMega(media, episode);
        } else {
            PlayerActivity.open(this, episode.streamUrl, null, media.name, "S" + episode.season + " E" + episode.episode + " · " + episode.title, false);
        }
    }

    private static String join(String str, String str2) {
        if (str == null) {
            str = "";
        }
        if (str2 == null) {
            str2 = "";
        }
        return (str.isEmpty() || str2.isEmpty()) ? str.isEmpty() ? str2 : str : str + " · " + str2;
    }

    private void connect(final boolean z) {
        final String text = text(this.inName);
        final String text2 = text(this.inUrl);
        final String text3 = text(this.inUser);
        final String text4 = text(this.inPass);
        if (text2.isEmpty() || text3.isEmpty() || text4.isEmpty()) {
            setStatus("Bitte alle Felder ausfüllen.", true);
        } else {
            setStatus("Verbinden…", false);
            IO.execute(new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda19
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$connect$88(text2, text3, text4, z, text);
                }
            });
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$connect$88(String str, String str2, String str3, boolean z, String str4) {
        try {
            XtreamApi xtreamApi = new XtreamApi(str, str2, str3, this.prefs.format());
            if (!xtreamApi.loginOk()) {
                throw new Exception("Login abgelehnt");
            }
            if (z) {
                Prefs prefs = this.prefs;
                if (str4.isEmpty()) {
                    str4 = str2;
                }
                prefs.saveAccount(str4, xtreamApi.base, str2, str3);
                this.api = xtreamApi;
                App.api = xtreamApi;
                final Models.Catalog loadLive = xtreamApi.loadLive();
                CatalogCache.write(CatalogCache.file(getCacheDir()), loadLive);
                Handler handler = UI;
                handler.post(new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda8
                    @Override // java.lang.Runnable
                    public final void run() {
                        MainActivity.this.lambda$connect$84(loadLive);
                    }
                });
                xtreamApi.loadLibrary(loadLive);
                handler.post(new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda9
                    @Override // java.lang.Runnable
                    public final void run() {
                        MainActivity.this.lambda$connect$85();
                    }
                });
                return;
            }
            UI.post(new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda10
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$connect$86();
                }
            });
        } catch (Exception unused) {
            UI.post(new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda12
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$connect$87();
                }
            });
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$connect$84(Models.Catalog catalog) {
        this.catalog = catalog;
        App.live = catalog.live;
        synchronized (this.epgAsked) {
            this.epgAsked.clear();
        }
        setStatus("Verbunden · " + catalog.live.size() + " Sender", false);
        refreshActive();
        showSettings(false);
        setTab(0);
        prefetchEpg();
        loadVavoo();
        loadKino();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$connect$85() {
        if (this.tab != 0) {
            renderList();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$connect$86() {
        setStatus("Verbindung ok", false);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$connect$87() {
        setStatus("Verbindung fehlgeschlagen. URL und Zugangsdaten prüfen.", true);
    }

    private List<Models.Media> filterKino() {
        String str = this.query;
        String lowerCase = str == null ? "" : str.trim().toLowerCase(Locale.GERMAN);
        List<Models.Media> all = MegaKino.all();
        ArrayList arrayList = new ArrayList();
        for (Models.Media media : all) {
            if (media != null && (!"mk-films".equals(this.catId) || !media.series)) {
                if (!"mk-serials".equals(this.catId) || media.series) {
                    if (!lowerCase.isEmpty()) {
                        if (!(media.name == null ? "" : media.name.toLowerCase(Locale.GERMAN)).contains(lowerCase)) {
                        }
                    }
                    arrayList.add(media);
                }
            }
        }
        int i = this.sort;
        if (i == 1) {
            arrayList.sort(Comparator.comparing(new Function() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda47
                @Override // java.util.function.Function
                public final Object apply(Object obj) {
                    String lowerCase2;
                    lowerCase2 = (((Models.Media) obj).name == null ? "" : ((Models.Media) obj).name).toLowerCase(Locale.GERMAN);
                    return lowerCase2;
                }
            }));
        } else if (i == 2) {
            arrayList.sort(new Comparator() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda48
                @Override // java.util.Comparator
                public final int compare(Object obj, Object obj2) {
                    int compareTo;
                    compareTo = (((Models.Media) obj2).name == null ? "" : ((Models.Media) obj2).name).toLowerCase(Locale.GERMAN).compareTo((((Models.Media) obj).name != null ? ((Models.Media) obj).name : "").toLowerCase(Locale.GERMAN));
                    return compareTo;
                }
            });
        }
        return arrayList;
    }

    private String kinoCatLabel() {
        return "mk-films".equals(this.catId) ? "Filme" : "mk-serials".equals(this.catId) ? "Serien" : "Alle";
    }

    private void searchKinoRemote() {
        String str = this.query;
        final String trim = str == null ? "" : str.trim();
        if (trim.length() < 2) {
            return;
        }
        final long j = this.kinoSearchGen + 1;
        this.kinoSearchGen = j;
        IO.execute(new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda66
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$searchKinoRemote$94(trim, j);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$searchKinoRemote$94(final String str, final long j) {
        List<Models.Media> list;
        try {
            list = MegaKino.search(str);
        } catch (Throwable unused) {
            list = null;
        }
        final List<Models.Media> list2 = list;
        UI.post(new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda5
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$searchKinoRemote$93(j, str, list2);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$searchKinoRemote$93(long j, String str, List list) {
        if (j == this.kinoSearchGen && this.tab == 5) {
            String str2 = this.query;
            if (!str.equals(str2 == null ? "" : str2.trim()) || list == null || list.isEmpty()) {
                return;
            }
            ArrayList arrayList = new ArrayList();
            Iterator it = list.iterator();
            while (it.hasNext()) {
                Models.Media media = (Models.Media) it.next();
                if (media != null && (!"mk-films".equals(this.catId) || !media.series)) {
                    if (!"mk-serials".equals(this.catId) || media.series) {
                        arrayList.add(media);
                    }
                }
            }
            int i = this.sort;
            if (i == 1) {
                arrayList.sort(Comparator.comparing(new Function() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda24
                    @Override // java.util.function.Function
                    public final Object apply(Object obj) {
                        String lowerCase;
                        lowerCase = (((Models.Media) obj).name == null ? "" : ((Models.Media) obj).name).toLowerCase(Locale.GERMAN);
                        return lowerCase;
                    }
                }));
            } else if (i == 2) {
                arrayList.sort(new Comparator() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda25
                    @Override // java.util.Comparator
                    public final int compare(Object obj, Object obj2) {
                        int compareTo;
                        compareTo = (((Models.Media) obj2).name == null ? "" : ((Models.Media) obj2).name).toLowerCase(Locale.GERMAN).compareTo((((Models.Media) obj).name != null ? ((Models.Media) obj).name : "").toLowerCase(Locale.GERMAN));
                        return compareTo;
                    }
                });
            }
            this.adapter.setMedia(arrayList);
            this.empty.setVisibility(arrayList.isEmpty() ? 0 : 8);
            this.empty.setText("Nichts gefunden.");
        }
    }

    private void loadKino() {
        if ((!MegaKino.loaded || MegaKino.all().isEmpty()) && !MegaKino.loading) {
            this.empty.setText("Megakino wird geladen…");
            this.empty.setVisibility(0);
            IO.execute(new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda59
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$loadKino$96();
                }
            });
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$loadKino$96() {
        try {
            MegaKino.load();
        } catch (Throwable unused) {
        }
        UI.post(new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda11
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$loadKino$95();
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$loadKino$95() {
        if (this.tab == 5) {
            renderList();
            if (MegaKino.all().isEmpty()) {
                String err = MegaKino.lastError != null && !MegaKino.lastError.isEmpty()
                        ? MegaKino.lastError
                        : "Megakino-Katalog leer. Später erneut versuchen.";
                this.empty.setVisibility(0);
                this.empty.setText(err);
            }
        }
    }

    private void playMega(final Models.Media media, final Models.Episode episode) {
        Toast.makeText(this, "Stream wird geladen…", 0).show();
        IO.execute(new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda31
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$playMega$98(episode, media);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$playMega$98(final Models.Episode episode, final Models.Media media) {
        String play = null;
        try {
            if (episode != null) {
                play = MegaKino.playEpisode(episode);
            } else {
                play = MegaKino.playUrl(media);
            }
        } catch (Throwable unused) {
            play = null;
        }
        final String str = play;
        UI.post(new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda6
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$playMega$97(str, episode, media);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$playMega$97(String str, Models.Episode episode, Models.Media media) {
        if (str == null || str.isEmpty()) {
            String err = (MegaKino.lastError != null && !MegaKino.lastError.isEmpty())
                    ? MegaKino.lastError
                    : "Megakino-Stream nicht erreichbar. Host/Player prüfen oder später erneut versuchen.";
            Toast.makeText(this, err, 1).show();
        } else {
            long durMs = PlayerActivity.parseDurationMs(media != null ? media.duration : null);
            PlayerActivity.open(this, str, null, media.name, episode == null ? join(media.genre, media.year) : "S" + episode.season + " E" + episode.episode + " · " + episode.title, false, null, durMs);
        }
    }

    private File vavooListCache() {
        return new File(getCacheDir(), "streamy2-vavoo-live-v4.json");
    }

    private void loadVavoo() {
        TextView textView;
        if (this.vavooBusy) {
            return;
        }
        this.vavooBusy = true;
        if (this.tab == 4 && (textView = this.empty) != null) {
            textView.setText("Vavoo wird geladen…");
            this.empty.setVisibility(0);
        }
        IO.execute(new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda4
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$loadVavoo$101();
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$loadVavoo$101() {
        Models.Catalog catalog = this.catalog;
        if (catalog == null) {
            catalog = DemoCatalog.build();
            this.catalog = catalog;
        }
        final Models.Catalog catalogFinal = catalog;
        try {
            Vavoo.merge(catalogFinal, vavooListCache());
            try {
                this.guide.apply(catalogFinal.live);
            } catch (Throwable unused) {
            }
            Handler handler = UI;
            handler.post(new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda42
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$loadVavoo$99();
                }
            });
            handler.post(new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda43
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$loadVavoo$100(catalogFinal);
                }
            });
        } catch (Throwable th) {
            UI.post(new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda43
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$loadVavoo$100(catalogFinal);
                }
            });
            throw th;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$loadVavoo$99() {
        loadVavooXmltv(false);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$loadVavoo$100(Models.Catalog catalog) {
        int i;
        TextView textView;
        this.vavooBusy = false;
        Models.Catalog catalog2 = this.catalog;
        if (catalog2 == catalog) {
            App.live = catalog2.live;
            int i2 = this.tab;
            if (i2 == 0 || i2 == 4) {
                renderList();
            }
        }
        if (this.tab == 4) {
            Models.Catalog catalog3 = this.catalog;
            if (catalog3 != null) {
                i = 0;
                for (Models.Channel channel : catalog3.live) {
                    if (channel != null && "vavoo".equals(channel.categoryId)) {
                        i++;
                    }
                }
            } else {
                i = 0;
            }
            if (i != 0 || (textView = this.empty) == null) {
                return;
            }
            textView.setText("Vavoo-Katalog leer. Tab erneut öffnen oder später versuchen." + (Vavoo.lastError != null && !Vavoo.lastError.isEmpty() ? ("\n" + Vavoo.lastError) : ""));
            this.empty.setVisibility(0);
        }
    }

    private void loadXtream(final boolean z) {
        final File file = CatalogCache.file(getCacheDir());
        if (!z) {
            Models.Catalog read = CatalogCache.read(file);
            if (read != null) {
                this.catalog = read;
                App.live = read.live;
                renderList();
                this.loading.setVisibility(8);
                loadVavoo();
                loadKino();
            } else {
                this.loading.setVisibility(0);
            }
        } else {
            this.loading.setVisibility(0);
        }
        IO.execute(new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda7
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$loadXtream$105(file, z);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$loadXtream$105(File file, final boolean z) {
        try {
            XtreamApi xtreamApi = new XtreamApi(this.prefs.url(), this.prefs.user(), this.prefs.pass(), this.prefs.format());
            this.api = xtreamApi;
            App.api = xtreamApi;
            final Models.Catalog loadLive = this.api.loadLive();
            CatalogCache.write(file, loadLive);
            Handler handler = UI;
            handler.post(new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda62
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$loadXtream$102(loadLive, z);
                }
            });
            this.api.loadLibrary(loadLive);
            handler.post(new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda63
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$loadXtream$103();
                }
            });
        } catch (Exception unused) {
            UI.post(new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda64
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$loadXtream$104();
                }
            });
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$loadXtream$102(Models.Catalog catalog, boolean z) {
        this.loading.setVisibility(8);
        this.catalog = catalog;
        App.live = catalog.live;
        synchronized (this.epgAsked) {
            this.epgAsked.clear();
        }
        if (z) {
            showSettings(false);
        }
        renderList();
        prefetchEpg();
        loadVavoo();
        loadKino();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$loadXtream$103() {
        if (this.tab != 0) {
            renderList();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$loadXtream$104() {
        this.loading.setVisibility(8);
        Models.Catalog catalog = this.catalog;
        if (catalog == null || catalog.live.isEmpty()) {
            Models.Catalog build = DemoCatalog.build();
            this.catalog = build;
            App.live = build.live;
            Toast.makeText(this, "Xtream nicht erreichbar — Demo geladen", 1).show();
            renderList();
            loadVavoo();
        }
    }

    private void prefetchEpg() {
        Handler handler = UI;
        handler.removeCallbacks(this.epgLater);
        handler.postDelayed(this.epgLater, 8000L);
        loadVisibleEpg();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$new$106() {
        refreshXmltv(false);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void loadVisibleEpg() {
        int i = this.tab;
        if (i == 0 || i == 4) {
            RecyclerView.LayoutManager layoutManager = this.list.getLayoutManager();
            if (layoutManager instanceof LinearLayoutManager) {
                LinearLayoutManager linearLayoutManager = (LinearLayoutManager) layoutManager;
                int findLastVisibleItemPosition = linearLayoutManager.findLastVisibleItemPosition() + 8;
                for (int max = Math.max(0, linearLayoutManager.findFirstVisibleItemPosition() - 4); max <= findLastVisibleItemPosition; max++) {
                    Object item = this.adapter.getItem(max);
                    if (item instanceof Models.Channel) {
                        askEpg((Models.Channel) item);
                    }
                }
            }
        }
    }

    private void askEpg(final Models.Channel channel) {
        if (channel == null || channel.header || channel.id == null) {
            return;
        }
        if (channel.epg == null || channel.epg.title == null || channel.epg.title.isEmpty()) {
            Models.Epg forChannel = this.guide.forChannel(channel);
            if (forChannel != null) {
                channel.epg = forChannel;
                this.adapter.patchEpg(channel.id, forChannel);
            } else if ((channel.vavooUrl == null || channel.vavooUrl.isEmpty()) && this.api != null) {
                synchronized (this.epgAsked) {
                    if (this.epgAsked.contains(channel.id)) {
                        return;
                    }
                    this.epgAsked.add(channel.id);
                    EPG.execute(new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda117
                        @Override // java.lang.Runnable
                        public final void run() {
                            MainActivity.this.lambda$askEpg$108(channel);
                        }
                    });
                }
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$askEpg$108(final Models.Channel channel) {
        final Models.Epg shortEpg = this.api.shortEpg(channel);
        if (shortEpg == null) {
            synchronized (this.epgAsked) {
                this.epgAsked.remove(channel.id);
            }
        } else {
            channel.epg = shortEpg;
            UI.post(new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda26
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$askEpg$107(channel, shortEpg);
                }
            });
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$askEpg$107(Models.Channel channel, Models.Epg epg) {
        this.adapter.patchEpg(channel.id, epg);
    }

    private File epgCache() {
        return new File(getCacheDir(), "streamy2-epg.xml");
    }

    private void refreshXmltv(final boolean z) {
        EditText editText = this.inEpgUrl;
        if (editText != null) {
            this.prefs.setEpgUrl(text(editText));
        }
        if (!z && this.guide.channelCount > 30) {
            IO.execute(new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda20
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$refreshXmltv$110();
                }
            });
            return;
        }
        if (!this.guide.loading || z) {
            if (!z) {
                long epgLast = this.prefs.epgLast();
                long epgIntervalHours = this.prefs.epgIntervalHours() * 3600000;
                final File epgCache = epgCache();
                final File vavooEpgCache = vavooEpgCache();
                if (epgLast > 0 && System.currentTimeMillis() - epgLast < epgIntervalHours && epgCache.exists() && epgCache.length() > 200) {
                    IO.execute(new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda21
                        @Override // java.lang.Runnable
                        public final void run() {
                            MainActivity.this.lambda$refreshXmltv$113(epgCache, vavooEpgCache);
                        }
                    });
                    return;
                }
            }
            EditText editText2 = this.inEpgUrl;
            String epgUrl = editText2 == null ? this.prefs.epgUrl() : text(editText2);
            if (epgUrl == null || epgUrl.isEmpty()) {
                XtreamApi xtreamApi = this.api;
                epgUrl = xtreamApi != null ? xtreamApi.xmltvUrl() : null;
            }
            final String epgUrlFinal = epgUrl;
            this.guide.loading = true;
            updateEpgStatus();
            IO.execute(new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda23
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$refreshXmltv$116(epgUrlFinal, z);
                }
            });
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$refreshXmltv$110() {
        try {
            EpgGuide epgGuide = this.guide;
            Models.Catalog catalog = this.catalog;
            final int apply = epgGuide.apply(catalog == null ? null : catalog.live);
            UI.post(new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda89
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$refreshXmltv$109(apply);
                }
            });
        } catch (Throwable unused) {
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$refreshXmltv$109(int i) {
        ChannelAdapter channelAdapter;
        if (i > 0 && (channelAdapter = this.adapter) != null) {
            channelAdapter.notifyEpg();
        }
        updateEpgStatus();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$refreshXmltv$113(File file, File file2) {
        try {
            this.guide.loadFile(file);
            if (file2.exists()) {
                this.guide.loadUrlMerge("https://epg.lat/files/de.xml.gz", file2, false);
            } else {
                this.guide.loadUrlMerge("https://epg.lat/files/de.xml.gz", file2, false);
            }
            EpgGuide epgGuide = this.guide;
            Models.Catalog catalog = this.catalog;
            final int apply = epgGuide.apply(catalog == null ? null : catalog.live);
            UI.post(new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda51
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$refreshXmltv$111(apply);
                }
            });
        } catch (Exception unused) {
            UI.post(new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda52
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$refreshXmltv$112();
                }
            });
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$refreshXmltv$111(int i) {
        ChannelAdapter channelAdapter;
        updateEpgStatus();
        if (i <= 0 || (channelAdapter = this.adapter) == null) {
            return;
        }
        channelAdapter.notifyEpg();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$refreshXmltv$112() {
        refreshXmltv(true);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$refreshXmltv$116(String str, final boolean z) {
        if (str != null) {
            try {
                if (!str.isEmpty()) {
                    try {
                        this.guide.loadUrl(str, epgCache(), z);
                    } catch (Throwable unused) {
                    }
                }
            } catch (Throwable th) {
                this.guide.loading = false;
                this.guide.error = th.getMessage();
                UI.post(new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda78
                    @Override // java.lang.Runnable
                    public final void run() {
                        MainActivity.this.lambda$refreshXmltv$115(z, th);
                    }
                });
                return;
            }
        }
        try {
            this.guide.loadUrlMerge("https://epg.lat/files/de.xml.gz", vavooEpgCache(), z);
        } catch (Throwable unused2) {
        }
        EpgGuide epgGuide = this.guide;
        Models.Catalog catalog = this.catalog;
        final int apply = epgGuide.apply(catalog == null ? null : catalog.live);
        this.prefs.setEpgLast(System.currentTimeMillis());
        this.guide.loading = false;
        UI.post(new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda67
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$refreshXmltv$114(z, apply);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$refreshXmltv$114(boolean z, int i) {
        updateEpgStatus();
        if (z) {
            Toast.makeText(this, i + " Sender mit EPG", 0).show();
        }
        ChannelAdapter channelAdapter = this.adapter;
        if (channelAdapter != null) {
            channelAdapter.notifyEpg();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$refreshXmltv$115(boolean z, Throwable th) {
        updateEpgStatus();
        if (z) {
            Toast.makeText(this, "EPG: " + th.getMessage(), 1).show();
        }
    }

    private void loadVavooXmltv(final boolean z) {
        IO.execute(new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda100
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$loadVavooXmltv$118(z);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$loadVavooXmltv$118(boolean z) {
        try {
            String[] strArr = Vavoo.EPG_URLS;
            int length = strArr.length;
            List<Models.Channel> list = null;
            int i = 0;
            Exception e = null;
            boolean loaded = false;
            while (i < length) {
                String url = strArr[i];
                File cache = vavooEpgCacheFor(url, i);
                try {
                    this.guide.loadUrlMerge(url, cache, z);

                    e = null;
                    loaded = true;
                    break;
                } catch (Exception e2) {
                    e = e2;
                    i++;
                }
            }
            if (!loaded && e != null && this.guide.channelCount == 0) {
                throw e;
            }
            EpgGuide epgGuide = this.guide;
            Models.Catalog catalog = this.catalog;
            if (catalog != null) {
                list = catalog.live;
            }
            final int apply = epgGuide.apply(list);
            this.prefs.setEpgLast(System.currentTimeMillis());
            UI.post(new Runnable() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda37
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$loadVavooXmltv$117(apply);
                }
            });
        } catch (Throwable th) {
            try {
                this.guide.error = th.getMessage();
                EpgGuide epgGuide2 = this.guide;
                Models.Catalog catalog2 = this.catalog;
                final int apply2 = epgGuide2 == null ? 0 : epgGuide2.apply(catalog2 == null ? null : catalog2.live);
                UI.post(new Runnable() {
                    @Override public void run() {
                        MainActivity.this.lambda$loadVavooXmltv$117(apply2);
                    }
                });
            } catch (Throwable unused) {
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$loadVavooXmltv$117(int i) {
        updateEpgStatus();
        ChannelAdapter channelAdapter = this.adapter;
        if (channelAdapter != null) {
            channelAdapter.notifyEpg();
        }
        try {
            loadVisibleEpg();
        } catch (Throwable unused) {
        }
    }

    private File vavooEpgCache() {
        return new File(getCacheDir(), "streamy2-vavoo-epg.xml");
    }

    private File vavooEpgCacheFor(String url, int index) {
        if (index <= 0) {
            return vavooEpgCache();
        }
        String safe = "u" + index;
        try {
            if (url != null) {
                int h = url.hashCode();
                safe = "u" + Integer.toHexString(h);
            }
        } catch (Throwable unused) {
        }
        return new File(getCacheDir(), "streamy2-vavoo-epg-" + safe + ".xml");
    }

    private void updateEpgStatus() {
        String str;
        String str2;
        if (this.epgStatus == null) {
            return;
        }
        if (this.guide.loading) {
            this.epgStatus.setText("EPG wird geladen…");
            return;
        }
        if (this.guide.error != null && this.guide.channelCount == 0) {
            this.epgStatus.setText("Fehler: " + this.guide.error);
            return;
        }
        if (this.guide.channelCount > 0) {
            if (this.prefs.epgLast() > 0) {
                str2 = new SimpleDateFormat("dd.MM. HH:mm", Locale.GERMANY).format(new Date(this.prefs.epgLast()));
            } else {
                str2 = "jetzt";
            }
            String msg = "Zuletzt: " + str2 + "  ·  " + this.guide.channelCount + " Sender, " + this.guide.programmeCount + " Programme";
            if (this.guide.error != null && !this.guide.error.isEmpty()) {
                msg = msg + "  ·  Warnung: " + this.guide.error;
            }
            this.epgStatus.setText(msg);
            return;
        }
        TextView textView = this.epgStatus;
        if (this.guide.error != null && !this.guide.error.isEmpty()) {
            str = "Fehler: " + this.guide.error;
        } else if (this.prefs.hasXtream()) {
            str = "Noch nicht geladen. „EPG jetzt aktualisieren“ tippen.";
        } else {
            str = "Demo-EPG ist lokal. Vavoo lädt XMLTV automatisch.";
        }
        textView.setText(str);
    }

    private void applyTheme() {
        Theme.Accent accent = Theme.get(this.prefs.accent());
        tintPrimary(findViewById(R.id.btnSave), accent);
        tintPrimary(findViewById(R.id.btnSendTv), accent);
        tintPrimary(findViewById(R.id.btnPairGo), accent);
        tintPrimary(findViewById(R.id.btnRefreshEpg), accent);
        View findViewById = findViewById(R.id.brandMark);
        if (findViewById != null && findViewById.getBackground() != null) {
            findViewById.getBackground().mutate().setTint(accent.color);
        }
        paintTabs();
        setFmt(this.prefs.format());
        paintResize();
        paintPlayer();
        paintBuffer();
        View btnTestExo = findViewById(R.id.btnTestExo);
        if (btnTestExo != null) {
            btnTestExo.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { MainActivity.this.openMuxTest(false); }
            });
        }
        View btnTestVlc = findViewById(R.id.btnTestVlc);
        if (btnTestVlc != null) {
            btnTestVlc.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { MainActivity.this.openMuxTest(true); }
            });
        }
        View btnPlaybackDiag = findViewById(R.id.btnPlaybackDiag);
        if (btnPlaybackDiag != null) {
            btnPlaybackDiag.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { MainActivity.this.showPlaybackDiag(); }
            });
        }

        paintEpgInterval();
        paintAccentDots();
        refreshActive();
    }

    private void tintPrimary(View view, Theme.Accent accent) {
        if (view == null) {
            return;
        }
        if (view.getBackground() != null) {
            view.getBackground().mutate().setTint(accent.color);
        }
        if (view instanceof TextView) {
            ((TextView) view).setTextColor(accent.onColor);
        }
    }

    private void buildAccentRow() {
        LinearLayout linearLayout = this.accentRow;
        if (linearLayout == null) {
            return;
        }
        linearLayout.removeAllViews();
        int dp = dp(36);
        int dp2 = dp(10);
        for (final Theme.Accent accent : Theme.ALL) {
            View view = new View(this);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(dp, dp);
            layoutParams.setMarginEnd(dp2);
            view.setLayoutParams(layoutParams);
            view.setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.MainActivity$$ExternalSyntheticLambda39
                @Override // android.view.View.OnClickListener
                public final void onClick(View view2) {
                    MainActivity.this.lambda$buildAccentRow$119(accent, view2);
                }
            });
            this.accentRow.addView(view);
        }
        paintAccentDots();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$buildAccentRow$119(Theme.Accent accent, View view) {
        this.prefs.setAccent(accent.id);
        applyTheme();
    }

    private void paintAccentDots() {
        if (this.accentRow == null) {
            return;
        }
        String accent = this.prefs.accent();
        for (int i = 0; i < this.accentRow.getChildCount() && i < Theme.ALL.length; i++) {
            View childAt = this.accentRow.getChildAt(i);
            Theme.Accent accent2 = Theme.ALL[i];
            GradientDrawable gradientDrawable = new GradientDrawable();
            gradientDrawable.setShape(1);
            gradientDrawable.setColor(accent2.color);
            if (accent2.id.equals(accent)) {
                gradientDrawable.setStroke(dp(3), -1);
            } else {
                gradientDrawable.setStroke(dp(2), DefaultTimeBar.DEFAULT_UNPLAYED_COLOR);
            }
            childAt.setBackground(gradientDrawable);
        }
    }

    private int dp(int i) {
        return Math.round(i * getResources().getDisplayMetrics().density);
    }

    private void setStatus(String str, boolean z) {
        this.status.setText(str);
        this.status.setTextColor(z ? getColor(R.color.danger) : Theme.get(this.prefs.accent()).color);
    }

    private static String text(EditText editText) {
        if (editText == null || editText.getText() == null) {
            return "";
        }
        return editText.getText().toString().trim();
    }

    static final class PickAdapter extends RecyclerView.Adapter<PickAdapter.VH> {
        private final List<String> items = new ArrayList();
        private OnPick onPick;
        private int selected;

        interface OnPick {
            void pick(int i);
        }

        PickAdapter() {
        }

        void set(List<String> list, int i, OnPick onPick) {
            this.items.clear();
            if (list != null) {
                this.items.addAll(list);
            }
            this.selected = i;
            this.onPick = onPick;
            notifyDataSetChanged();
        }

        @Override // androidx.recyclerview.widget.RecyclerView.Adapter
        public VH onCreateViewHolder(ViewGroup viewGroup, int i) {
            return new VH((TextView) LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.item_pick, viewGroup, false));
        }

        @Override // androidx.recyclerview.widget.RecyclerView.Adapter
        public void onBindViewHolder(final VH vh, int i) {
            int color;
            vh.name.setText(this.items.get(i));
            TextView textView = vh.name;
            if (i == this.selected) {
                color = vh.name.getResources().getColor(R.color.fg);
            } else {
                color = vh.name.getResources().getColor(R.color.muted);
            }
            textView.setTextColor(color);
            vh.itemView.setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.MainActivity$PickAdapter$$ExternalSyntheticLambda0
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    MainActivity.PickAdapter.this.lambda$onBindViewHolder$0(vh, view);
                }
            });
        }

        /* JADX INFO: Access modifiers changed from: private */
        public /* synthetic */ void lambda$onBindViewHolder$0(VH vh, View view) {
            OnPick onPick = this.onPick;
            if (onPick != null) {
                onPick.pick(vh.getBindingAdapterPosition());
            }
        }

        @Override // androidx.recyclerview.widget.RecyclerView.Adapter
        public int getItemCount() {
            return this.items.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final TextView name;

            VH(TextView textView) {
                super(textView);
                this.name = textView;
            }
        }
    }
}
