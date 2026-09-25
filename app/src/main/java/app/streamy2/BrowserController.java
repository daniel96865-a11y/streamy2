package app.streamy2;

import android.app.Activity;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Message;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.view.ViewCompat;
import com.google.android.material.appbar.AppBarLayout;

/* loaded from: classes.dex */
public class BrowserController {
    public static final String HOME = "https://www.google.com/";
    private static final String UA_PHONE = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36";
    private static final String UA_TV = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    private final Activity act;
    private final TextView adHint;
    private AdBlock adblock;
    private final AppBarLayout appBar;
    private boolean chromeHidden;
    private final ImageView cursor;
    private WebChromeClient.CustomViewCallback customCb;
    private View customView;
    private float cx = 400.0f;
    private float cy = 360.0f;
    private final FrameLayout fullScreen;
    private boolean hinted;
    private final View pane;
    private boolean pressing;
    private final ProgressBar progress;
    private boolean started;
    /** True once the user explicitly navigated away from the empty start page this session. */
    private boolean userNavigated;
    private boolean tvCursor;
    private final EditText urlBar;
    private WebView web;
    private FrameLayout webHost;
    private boolean webReady;
    private final View webChrome;
    /** Last created controller — used by App.onTrimMemory. */
    private static volatile BrowserController activeInstance;

    private void applyScroll(int i, int i2) {
    }

    private void injectScrollHook(WebView webView) {
    }

    public BrowserController(Activity activity) {
        this.act = activity;
        this.pane = activity.findViewById(R.id.browserPane);
        this.webHost = (FrameLayout) activity.findViewById(R.id.webViewHost);
        this.web = null;
        this.webReady = false;
        this.urlBar = (EditText) activity.findViewById(R.id.webUrl);
        this.progress = (ProgressBar) activity.findViewById(R.id.webProgress);
        this.adHint = (TextView) activity.findViewById(R.id.webAdHint);
        this.fullScreen = (FrameLayout) activity.findViewById(R.id.webFullscreen);
        this.webChrome = activity.findViewById(R.id.webChrome);
        this.appBar = (AppBarLayout) activity.findViewById(R.id.appBar);
        this.cursor = (ImageView) activity.findViewById(R.id.tvCursor);
        this.adblock = null; // load on first browser open — not on cold start
        this.tvCursor = Tv.isTv(activity);
        activeInstance = this;
        setupChrome();
    }

    /** Create WebView + AdBlock only when Browser tab is opened (keeps cold start light). */
    private void ensureWeb() {
        if (this.webReady && this.web != null) {
            return;
        }
        if (this.webHost == null) {
            this.webHost = (FrameLayout) this.act.findViewById(R.id.webViewHost);
        }
        if (this.web == null && this.webHost != null) {
            WebView wv = new WebView(this.act);
            wv.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            wv.setFocusable(true);
            wv.setFocusableInTouchMode(true);
            this.webHost.addView(wv);
            this.web = wv;
        }
        if (this.adblock == null) {
            this.adblock = new AdBlock(this.act);
        }
        setupWeb();
        this.webReady = this.web != null;
    }

    private void setupWeb() {
        WebView webView = this.web;
        if (webView == null) {
            return;
        }
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(true);
        settings.setCacheMode(-1);
        settings.setUserAgentString(Tv.isTv(this.act) ? UA_TV : UA_PHONE);
        settings.setMixedContentMode(0);
        if (Build.VERSION.SDK_INT >= 26) {
            settings.setSafeBrowsingEnabled(false);
        }
        this.web.setBackgroundColor(ViewCompat.MEASURED_STATE_MASK);
        this.web.setLayerType(2, null);
        if (this.tvCursor) {
            this.web.setFocusable(false);
            this.web.setFocusableInTouchMode(false);
            ImageView imageView = this.cursor;
            if (imageView != null) {
                imageView.setClickable(false);
                this.cursor.setFocusable(false);
            }
        }
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(this.web, true);
        this.web.setWebViewClient(new AnonymousClass1());
        this.web.setWebChromeClient(new WebChromeClient() { // from class: app.streamy2.BrowserController.2
            @Override // android.webkit.WebChromeClient
            public void onProgressChanged(WebView webView2, int i) {
                if (BrowserController.this.progress == null) {
                    return;
                }
                BrowserController.this.progress.setProgress(i);
                BrowserController.this.progress.setVisibility(i >= 100 ? 8 : 0);
            }

            @Override // android.webkit.WebChromeClient
            public boolean onCreateWindow(WebView webView2, boolean z, boolean z2, Message message) {
                WebView.HitTestResult hitTestResult = webView2.getHitTestResult();
                String extra = hitTestResult == null ? null : hitTestResult.getExtra();
                if (!z2 || extra == null || !extra.startsWith("http") || BrowserController.this.handleUrl(extra)) {
                    return false;
                }
                webView2.loadUrl(extra);
                return false;
            }

            @Override // android.webkit.WebChromeClient
            public void onShowCustomView(View view, WebChromeClient.CustomViewCallback customViewCallback) {
                if (BrowserController.this.customView != null) {
                    customViewCallback.onCustomViewHidden();
                    return;
                }
                BrowserController.this.customView = view;
                BrowserController.this.customCb = customViewCallback;
                if (BrowserController.this.fullScreen != null) {
                    BrowserController.this.fullScreen.addView(view, new FrameLayout.LayoutParams(-1, -1));
                    BrowserController.this.fullScreen.setVisibility(0);
                    BrowserController.this.fullScreen.bringToFront();
                }
                BrowserController.this.act.getWindow().addFlags(128);
                BrowserController.this.hideSystemUi(true);
                if (BrowserController.this.tvCursor) {
                    BrowserController browserController = BrowserController.this;
                    browserController.attachCursor(browserController.fullScreen);
                }
            }

            @Override // android.webkit.WebChromeClient
            public void onHideCustomView() {
                BrowserController.this.hideFullscreen();
            }
        });
    }

    private void setupChrome() {
        View findViewById = this.act.findViewById(R.id.btnWebBack);
        View findViewById2 = this.act.findViewById(R.id.btnWebFwd);
        View findViewById3 = this.act.findViewById(R.id.btnWebHome);
        View findViewById4 = this.act.findViewById(R.id.btnWebGo);
        if (!this.tvCursor) {
            label(findViewById, "Zurück");
            label(findViewById2, "Vor");
            label(findViewById3, "Home");
        }
        if (findViewById != null) {
            findViewById.setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.BrowserController$$ExternalSyntheticLambda0
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    BrowserController.this.lambda$setup$0(view);
                }
            });
        }
        if (findViewById2 != null) {
            findViewById2.setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.BrowserController$$ExternalSyntheticLambda1
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    BrowserController.this.lambda$setup$1(view);
                }
            });
        }
        if (findViewById3 != null) {
            findViewById3.setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.BrowserController$$ExternalSyntheticLambda2
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    BrowserController.this.lambda$setup$2(view);
                }
            });
        }
        if (findViewById4 != null) {
            findViewById4.setOnClickListener(new View.OnClickListener() { // from class: app.streamy2.BrowserController$$ExternalSyntheticLambda3
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    BrowserController.this.lambda$setup$3(view);
                }
            });
        }
        EditText editText = this.urlBar;
        if (editText != null) {
            editText.setOnEditorActionListener(new TextView.OnEditorActionListener() { // from class: app.streamy2.BrowserController$$ExternalSyntheticLambda4
                @Override // android.widget.TextView.OnEditorActionListener
                public final boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                    boolean lambda$setup$4;
                    lambda$setup$4 = BrowserController.this.lambda$setup$4(textView, i, keyEvent);
                    return lambda$setup$4;
                }
            });
        }
    }

    /* renamed from: app.streamy2.BrowserController$1, reason: invalid class name */
    class AnonymousClass1 extends WebViewClient {
        AnonymousClass1() {
        }

        @Override // android.webkit.WebViewClient
        public boolean shouldOverrideUrlLoading(WebView webView, WebResourceRequest webResourceRequest) {
            return BrowserController.this.handleUrl(webResourceRequest.getUrl() == null ? "" : webResourceRequest.getUrl().toString());
        }

        @Override // android.webkit.WebViewClient
        public boolean shouldOverrideUrlLoading(WebView webView, String str) {
            return BrowserController.this.handleUrl(str);
        }

        @Override // android.webkit.WebViewClient
        public WebResourceResponse shouldInterceptRequest(WebView webView, WebResourceRequest webResourceRequest) {
            if (webResourceRequest == null || webResourceRequest.getUrl() == null) {
                return super.shouldInterceptRequest(webView, webResourceRequest);
            }
            AdBlock block = BrowserController.this.adblock;
            if (block == null) {
                return super.shouldInterceptRequest(webView, webResourceRequest);
            }
            WebResourceResponse intercept = block.intercept(webResourceRequest.getUrl().toString());
            if (intercept != null) {
                Activity activity = BrowserController.this.act;
                final BrowserController browserController = BrowserController.this;
                activity.runOnUiThread(new Runnable() { // from class: app.streamy2.BrowserController$1$$ExternalSyntheticLambda0
                    @Override // java.lang.Runnable
                    public final void run() {
                        BrowserController.this.updateAdHint();
                    }
                });
                return intercept;
            }
            return super.shouldInterceptRequest(webView, webResourceRequest);
        }

        @Override // android.webkit.WebViewClient
        public void onPageStarted(WebView webView, String str, Bitmap bitmap) {
            if (BrowserController.this.urlBar != null && str != null && !BrowserController.this.urlBar.hasFocus()) {
                BrowserController.this.urlBar.setText(str);
            }
            if (BrowserController.this.progress != null) {
                BrowserController.this.progress.setVisibility(0);
            }
        }

        @Override // android.webkit.WebViewClient
        public void onPageFinished(WebView webView, String str) {
            if (BrowserController.this.urlBar != null && str != null && !BrowserController.this.urlBar.hasFocus()) {
                BrowserController.this.urlBar.setText(BrowserController.pretty(str));
            }
            if (BrowserController.this.progress != null) {
                BrowserController.this.progress.setVisibility(8);
            }
            BrowserController.this.injectHideAds(webView);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$setup$0(View view) {
        ensureWeb();
        if (this.web == null) {
            return;
        }
        if (this.web.canGoBack()) {
            this.web.goBack();
        } else {
            this.web.loadUrl(HOME);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$setup$1(View view) {
        ensureWeb();
        if (this.web != null && this.web.canGoForward()) {
            this.web.goForward();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$setup$2(View view) {
        ensureWeb();
        if (this.web != null) {
            this.web.loadUrl(HOME);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$setup$3(View view) {
        goToBar();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ boolean lambda$setup$4(TextView textView, int i, KeyEvent keyEvent) {
        goToBar();
        return true;
    }

    private void label(View view, String str) {
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            textView.setText(str);
            textView.setTextSize(12.0f);
            ViewGroup.LayoutParams layoutParams = textView.getLayoutParams();
            if (layoutParams != null) {
                layoutParams.width = -2;
                textView.setLayoutParams(layoutParams);
            }
            textView.setPadding(dp(10), 0, dp(10), 0);
        }
    }

    private void goToBar() {
        ensureWeb();
        EditText editText = this.urlBar;
        if (editText == null || this.web == null) {
            return;
        }
        String trim = editText.getText() == null ? "" : this.urlBar.getText().toString().trim();
        if (trim.isEmpty()) {
            this.web.loadUrl(HOME);
            return;
        }
        if (!trim.contains("://")) {
            trim = (!trim.contains(".") || trim.contains(" ")) ? "https://www.google.com/search?q=" + Uri.encode(trim) : "https://" + trim;
        }
        if (FeatureAccess.isRestrictedUrl(trim) && !FeatureAccess.isUnlocked(this.act)) {
            Toast.makeText(this.act, "Freigabecode erforderlich", Toast.LENGTH_SHORT).show();
            forceBlankHome();
            return;
        }
        this.userNavigated = true;
        this.web.loadUrl(trim);
        this.web.requestFocus();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean handleUrl(String str) {
        String str2;
        if (str == null || str.isEmpty()) {
            return true;
        }
        String lowerCase = str.toLowerCase();
        if (FeatureAccess.isRestrictedUrl(lowerCase) && !FeatureAccess.isUnlocked(this.act)) {
            Toast.makeText(this.act, "Freigabecode erforderlich", Toast.LENGTH_SHORT).show();
            return true;
        }
        if (lowerCase.startsWith("about:") || lowerCase.startsWith("javascript:")) {
            return false;
        }
        if (lowerCase.startsWith("intent:") || lowerCase.startsWith("market:") || lowerCase.startsWith("magnet:") || lowerCase.startsWith("mailto:") || lowerCase.startsWith("tel:")) {
            return true;
        }
        if (!isMedia(lowerCase)) {
            return false;
        }
        try {
            str2 = Uri.parse(str).getHost();
        } catch (Exception unused) {
            str2 = "";
        }
        PlayerActivity.open(this.act, str, null, "Browser", str2 == null ? "" : str2, false);
        return true;
    }

    private static boolean isMedia(String str) {
        int indexOf = str.indexOf(63);
        if (indexOf >= 0) {
            str = str.substring(0, indexOf);
        }
        return str.endsWith(".m3u8") || str.endsWith(".mp4") || str.endsWith(".mkv") || str.endsWith(".avi") || str.endsWith(".webm") || str.endsWith(".mpd");
    }

    private void setChromeHidden(boolean z) {
        this.chromeHidden = false;
        AppBarLayout appBarLayout = this.appBar;
        if (appBarLayout != null) {
            appBarLayout.setExpanded(true, false);
        }
        View view = this.webChrome;
        if (view != null) {
            view.setVisibility(0);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void injectHideAds(WebView webView) {
        webView.evaluateJavascript("(function(){try{var s=document.createElement('style');s.textContent='#ad,#ads,.ad,.ads,.adsbygoogle,[id*=\"google_ads\"],[class*=\"ad-banner\"],[id*=\"ad-banner\"],.advertisement,.ad-container,.adbox,.ad-wrapper,iframe[src*=\"doubleclick\"],iframe[src*=\"googlesyndication\"]{display:none!important;}';document.documentElement.appendChild(s);}catch(e){}})();", null);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updateAdHint() {
        if (this.adHint == null) {
            return;
        }
        int blockedCount = this.adblock == null ? 0 : this.adblock.blockedCount();
        if (blockedCount <= 0) {
            this.adHint.setVisibility(8);
        } else {
            this.adHint.setText(blockedCount + " Werbung blockiert");
            this.adHint.setVisibility(0);
        }
    }


    /** Wipe cookies / cache / history so Browser opens as a clean empty tab. */
    private void clearBrowserSlate() {
        try {
            CookieManager cm = CookieManager.getInstance();
            cm.removeAllCookies(null);
            cm.flush();
        } catch (Exception ignored) {
            Quiet.ignored("BrowserController", ignored);
        }
        try {
            WebStorage.getInstance().deleteAllData();
        } catch (Exception ignored) {
            Quiet.ignored("BrowserController", ignored);
        }
        WebView webView = this.web;
        if (webView != null) {
            webView.stopLoading();
            webView.clearCache(true);
            webView.clearFormData();
            webView.clearHistory();
        }
    }


    private String currentUrl() {
        try {
            return this.web != null ? this.web.getUrl() : null;
        } catch (Exception unused) {
            return null;
        }
    }

    private void forceBlankHome() {
        if (this.web != null) {
            this.web.stopLoading();
            this.web.loadUrl(HOME);
        }
        if (this.urlBar != null) {
            this.urlBar.setText("");
        }
    }

    public void show() {
        ensureWeb();
        restoreWebLayer();
        if (this.web != null) {
            try {
                this.web.onResume();
            } catch (Throwable ignored) {
                Quiet.ignored("BrowserController", ignored);
            }
        }
        View view = this.pane;
        if (view != null) {
            view.setVisibility(0);
        }
        setChromeHidden(false);
        if (!this.started) {
            this.started = true;
            clearBrowserSlate();
            forceBlankHome();
        } else {
            if (this.web != null) {
                this.web.onResume();
            }
            String url = currentUrl();
            boolean restrictedMedia = ExtraMediaSource.isRestrictedUrl(url);
            // Blank until user navigated this session; always clear restricted-media pollution
            if (!this.userNavigated || restrictedMedia) {
                forceBlankHome();
            }
        }
        if (this.tvCursor) {
            View view2 = this.pane;
            attachCursor(view2 instanceof ViewGroup ? (ViewGroup) view2 : null);
            showCursor(true);
            this.pane.post(new BrowserController$$ExternalSyntheticLambda5(this));
            if (this.hinted) {
                return;
            }
            this.hinted = true;
            Toast.makeText(this.act, "Pfeiltasten = Cursor  ·  OK = klicken", 1).show();
            return;
        }
        this.web.requestFocus();
    }

    public void hide() {
        hideFullscreen();
        showCursor(false);
        WebView webView = this.web;
        if (webView != null) {
            try {
                webView.onPause();
            } catch (Throwable ignored) {
                Quiet.ignored("BrowserController", ignored);
            }
            try {
                webView.clearCache(false);
            } catch (Throwable ignored) {
                Quiet.ignored("BrowserController", ignored);
            }
            // Free DOM when user never navigated this session
            if (!this.userNavigated) {
                try {
                    webView.stopLoading();
                    webView.loadUrl("about:blank");
                } catch (Throwable ignored) {
                    Quiet.ignored("BrowserController", ignored);
                }
            }
        }
        View view = this.pane;
        if (view != null) {
            view.setVisibility(8);
        }
    }

    /** Called from App.onTrimMemory — pause / blank / destroy aggressively when asked. */
    public static void trimForMemory(boolean aggressive) {
        BrowserController c = activeInstance;
        if (c == null) {
            return;
        }
        try {
            WebView webView = c.web;
            if (webView == null) {
                return;
            }
            webView.onPause();
            webView.stopLoading();
            try {
                webView.clearCache(false);
            } catch (Throwable ignored) {
                Quiet.ignored("BrowserController", ignored);
            }
            if (!c.userNavigated || aggressive) {
                try {
                    webView.loadUrl("about:blank");
                } catch (Throwable ignored) {
                    Quiet.ignored("BrowserController", ignored);
                }
            }
            if (aggressive && !c.visible()) {
                try {
                    webView.clearHistory();
                    webView.clearFormData();
                } catch (Throwable ignored) {
                    Quiet.ignored("BrowserController", ignored);
                }
                // Free hardware layer surface while hidden
                try {
                    webView.setLayerType(View.LAYER_TYPE_NONE, null);
                } catch (Throwable ignored) {
                    Quiet.ignored("BrowserController", ignored);
                }
            }
        } catch (Throwable ignored) {
            Quiet.ignored("BrowserController", ignored);
        }
    }

    public void pause() {
        hideFullscreen();
        WebView webView = this.web;
        if (webView != null) {
            webView.onPause();
        }
    }

    public void resume() {
        WebView webView;
        if (!visible() || (webView = this.web) == null) {
            return;
        }
        restoreWebLayer();
        webView.onResume();
    }

    private void restoreWebLayer() {
        WebView webView = this.web;
        if (webView == null) {
            return;
        }
        try {
            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        } catch (Throwable ignored) {
            try {
                webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            } catch (Throwable ignored2) {
                Quiet.ignored("BrowserController", ignored2);
            }
        }
    }

    public boolean visible() {
        View view = this.pane;
        return view != null && view.getVisibility() == 0;
    }

    public boolean onBack() {
        if (this.customView != null) {
            hideFullscreen();
            return true;
        }
        WebView webView = this.web;
        if (webView == null || !webView.canGoBack()) {
            return false;
        }
        this.web.goBack();
        return true;
    }

    public void destroy() {
        hideFullscreen();
        WebView webView = this.web;
        if (webView != null) {
            webView.stopLoading();
            this.web.loadUrl("about:blank");
            this.web.destroy();
        }
        if (activeInstance == this) {
            activeInstance = null;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void hideFullscreen() {
        FrameLayout frameLayout;
        View view = this.customView;
        if (view != null && (frameLayout = this.fullScreen) != null) {
            frameLayout.removeView(view);
            this.fullScreen.setVisibility(8);
        }
        this.customView = null;
        WebChromeClient.CustomViewCallback customViewCallback = this.customCb;
        if (customViewCallback != null) {
            try {
                customViewCallback.onCustomViewHidden();
            } catch (Exception unused) {
                Quiet.ignored("BrowserController", unused);
            }
            this.customCb = null;
        }
        if (this.tvCursor) {
            View view2 = this.pane;
            if (view2 instanceof ViewGroup) {
                attachCursor((ViewGroup) view2);
            }
        }
        this.act.getWindow().clearFlags(128);
        hideSystemUi(false);
    }

    public boolean handleKey(KeyEvent keyEvent) {
        int keyCode;
        if (!this.tvCursor || !visible() || this.cursor == null) {
            return false;
        }
        EditText editText = this.urlBar;
        if ((editText != null && editText.hasFocus()) || (keyCode = keyEvent.getKeyCode()) == 4) {
            return false;
        }
        boolean z = keyEvent.getAction() == 0;
        boolean z2 = keyEvent.getAction() == 1;
        if (keyCode == 23 || keyCode == 66 || keyCode == 160 || keyCode == 96) {
            if (z && keyEvent.getRepeatCount() == 0) {
                fireClick(true);
            } else if (z2) {
                fireClick(false);
            }
            return true;
        }
        if (!z) {
            return false;
        }
        float min = (Math.min(keyEvent.getRepeatCount(), 14) * 12.0f) + 26.0f;
        if (keyCode == 21) {
            move(-min, 0.0f);
            return true;
        }
        if (keyCode == 22) {
            move(min, 0.0f);
            return true;
        }
        if (keyCode == 19) {
            move(0.0f, -min);
            return true;
        }
        if (keyCode == 20) {
            move(0.0f, min);
            return true;
        }
        if (keyCode == 93 || keyCode == 167) {
            scrollWeb((int) (this.web != null ? this.web.getHeight() * 0.7f : 400.0f));
            return true;
        }
        if (keyCode != 92 && keyCode != 166) {
            return false;
        }
        scrollWeb((int) (-(this.web != null ? this.web.getHeight() * 0.7f : 400.0f)));
        return true;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void attachCursor(ViewGroup viewGroup) {
        ImageView imageView = this.cursor;
        if (imageView == null || viewGroup == null) {
            return;
        }
        ViewGroup viewGroup2 = imageView.getParent() instanceof ViewGroup ? (ViewGroup) this.cursor.getParent() : null;
        if (viewGroup2 == viewGroup) {
            this.cursor.bringToFront();
            return;
        }
        if (viewGroup2 != null) {
            viewGroup2.removeView(this.cursor);
        }
        viewGroup.addView(this.cursor, new FrameLayout.LayoutParams(this.cursor.getLayoutParams() != null ? this.cursor.getLayoutParams().width : dp(56), this.cursor.getLayoutParams() != null ? this.cursor.getLayoutParams().height : dp(56)));
        this.cursor.bringToFront();
        viewGroup.post(new BrowserController$$ExternalSyntheticLambda5(this));
    }

    private void showCursor(boolean z) {
        ImageView imageView = this.cursor;
        if (imageView == null || !this.tvCursor) {
            return;
        }
        imageView.setVisibility(z ? 0 : 8);
        if (z) {
            this.cursor.bringToFront();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void resetCursor() {
        ImageView imageView = this.cursor;
        View view = imageView == null ? null : (View) imageView.getParent();
        if (view == null || view.getWidth() <= 0) {
            return;
        }
        this.cx = view.getWidth() / 2.0f;
        this.cy = view.getHeight() / 2.0f;
        applyCursor();
        showCursor(true);
    }

    private void move(float f, float f2) {
        View view = (View) this.cursor.getParent();
        if (view == null) {
            return;
        }
        int width = view.getWidth();
        int height = view.getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        float f3 = this.cx + f;
        float f4 = this.cy + f2;
        if (f2 > 0.0f) {
            float f5 = height - 72.0f;
            if (f4 > f5) {
                scrollWeb((int) Math.max(48.0f, f2 * 5.0f));
                f4 = f5;
                this.cx = Math.max(16.0f, Math.min(width - 16, f3));
                this.cy = Math.max(16.0f, Math.min(height - 16, f4));
                applyCursor();
            }
        }
        if (f2 < 0.0f && f4 < 72.0f) {
            scrollWeb((int) (-Math.max(48.0f, (-f2) * 5.0f)));
            f4 = 72.0f;
        }
        this.cx = Math.max(16.0f, Math.min(width - 16, f3));
        this.cy = Math.max(16.0f, Math.min(height - 16, f4));
        applyCursor();
    }

    private void applyCursor() {
        ImageView imageView = this.cursor;
        if (imageView == null) {
            return;
        }
        float width = imageView.getWidth() > 0 ? this.cursor.getWidth() : dp(56);
        int height = this.cursor.getHeight() > 0 ? this.cursor.getHeight() : dp(56);
        this.cursor.setX(this.cx - (width / 2.0f));
        this.cursor.setY(this.cy - (height / 2.0f));
        this.cursor.bringToFront();
        this.cursor.setScaleX(this.pressing ? 0.82f : 1.0f);
        this.cursor.setScaleY(this.pressing ? 0.82f : 1.0f);
    }

    /* JADX WARN: Multi-variable type inference failed */
    private void fireClick(boolean z) {
        if (this.cursor == null) {
            return;
        }
        this.pressing = z;
        applyCursor();
        View view = (View) this.cursor.getParent();
        if (view == null) {
            return;
        }
        int[] r1 = new int[2];
        view.getLocationOnScreen(r1);
        View decorView = this.act.getWindow().getDecorView();
        int[] r2 = new int[2];
        decorView.getLocationOnScreen(r2);
        float f = (r2[0] + this.cx) - r1[0];
        float f2 = (r2[1] + this.cy) - r1[1];
        this.cursor.setVisibility(4);
        long uptimeMillis = SystemClock.uptimeMillis();
        MotionEvent obtain = MotionEvent.obtain(uptimeMillis, uptimeMillis, !z ? 1 : 0, f, f2, 0);
        obtain.setSource(4098);
        decorView.dispatchTouchEvent(obtain);
        obtain.recycle();
        if (z) {
            return;
        }
        showCursor(true);
        applyCursor();
    }

    private void scrollWeb(int i) {
        WebView webView = this.web;
        if (webView == null || this.customView != null) {
            return;
        }
        webView.evaluateJavascript("window.scrollBy(0," + i + ")", null);
    }

    private int dp(int i) {
        return Math.round(i * this.act.getResources().getDisplayMetrics().density);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void hideSystemUi(boolean z) {
        View decorView = this.act.getWindow().getDecorView();
        if (z) {
            decorView.setSystemUiVisibility(4102);
        } else {
            decorView.setSystemUiVisibility(0);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String pretty(String str) {
        if (str == null) {
            return "";
        }
        return str.startsWith("https://") ? str.substring(8) : str.startsWith("http://") ? str.substring(7) : str;
    }
}
