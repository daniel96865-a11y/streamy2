import SwiftUI
import WebKit

struct BrowserScreen: View {
    @EnvironmentObject private var state: AppState
    @StateObject private var model = BrowserModel()

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                HStack(spacing: 10) {
                    Button {
                        model.goBack()
                    } label: {
                        Image(systemName: "chevron.left")
                    }
                    .disabled(!model.canGoBack)

                    Button {
                        model.goForward()
                    } label: {
                        Image(systemName: "chevron.right")
                    }
                    .disabled(!model.canGoForward)

                    Button {
                        model.goHome()
                    } label: {
                        Image(systemName: "house")
                    }

                    TextField("Adresse oder Suche", text: $model.address)
                        .textFieldStyle(.roundedBorder)
                        .textInputAutocapitalization(.never)
                        .keyboardType(.webSearch)
                        .onSubmit { model.navigate() }

                    Button {
                        model.navigate()
                    } label: {
                        Image(systemName: "arrow.right.circle.fill")
                            .font(.title2)
                    }
                }
                .padding(.horizontal)
                .padding(.vertical, 8)

                if model.progress < 1 {
                    ProgressView(value: model.progress)
                        .progressViewStyle(.linear)
                }

                StreamyWebView(model: model) { url in
                    state.playbackItem = PlaybackItem(
                        title: url.host ?? "Browser",
                        subtitle: "Browser",
                        url: url,
                        logoURL: nil,
                        isLive: false,
                        channel: nil
                    )
                }
            }
            .navigationTitle("Browser")
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}

@MainActor
final class BrowserModel: ObservableObject {
    @Published var address = ""
    @Published var canGoBack = false
    @Published var canGoForward = false
    @Published var progress = 0.0

    fileprivate weak var webView: WKWebView?

    func navigate() {
        let trimmed = address.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            goHome()
            return
        }

        let target: String
        if trimmed.contains("://") {
            target = trimmed
        } else if trimmed.contains(".") && !trimmed.contains(" ") {
            target = "https://" + trimmed
        } else {
            let query = trimmed.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? trimmed
            target = "https://www.google.com/search?q=" + query
        }

        guard let url = URL(string: target) else { return }
        webView?.load(URLRequest(url: url))
    }

    func goHome() {
        address = ""
        webView?.loadHTMLString(
            """
            <!doctype html>
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>
            body{font-family:-apple-system,system-ui;background:#101114;color:#fff;margin:0;
                 min-height:100vh;display:grid;place-items:center;text-align:center}
            .box{max-width:420px;padding:32px} h1{font-size:30px;margin:0 0 8px}
            p{opacity:.68;line-height:1.45}
            </style>
            <div class="box"><h1>Streamy Browser</h1><p>Adresse oder Suche oben eingeben.</p></div>
            """,
            baseURL: nil
        )
    }

    func goBack() { webView?.goBack() }
    func goForward() { webView?.goForward() }

    fileprivate func sync(_ view: WKWebView) {
        canGoBack = view.canGoBack
        canGoForward = view.canGoForward
        progress = view.estimatedProgress
        if let raw = view.url?.absoluteString, !raw.hasPrefix("about:") {
            address = raw
        }
    }
}

private struct StreamyWebView: UIViewRepresentable {
    @ObservedObject var model: BrowserModel
    let onMedia: (URL) -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(model: model, onMedia: onMedia)
    }

    func makeUIView(context: Context) -> WKWebView {
        let configuration = WKWebViewConfiguration()
        configuration.allowsInlineMediaPlayback = true
        configuration.mediaTypesRequiringUserActionForPlayback = []
        configuration.websiteDataStore = .default()

        let controller = WKUserContentController()
        configuration.userContentController = controller
        installAdBlockRules(into: controller)

        let view = WKWebView(frame: .zero, configuration: configuration)
        view.allowsBackForwardNavigationGestures = true
        view.navigationDelegate = context.coordinator
        view.uiDelegate = context.coordinator
        context.coordinator.attach(view)
        model.webView = view
        model.goHome()
        return view
    }

    func updateUIView(_ webView: WKWebView, context: Context) {
        if model.webView !== webView {
            model.webView = webView
        }
    }

    private func installAdBlockRules(into controller: WKUserContentController) {
        let rules = """
        [
          {"trigger":{"url-filter":".*doubleclick\\.net.*"},"action":{"type":"block"}},
          {"trigger":{"url-filter":".*googlesyndication\\.com.*"},"action":{"type":"block"}},
          {"trigger":{"url-filter":".*googleadservices\\.com.*"},"action":{"type":"block"}},
          {"trigger":{"url-filter":".*adservice\\.google\\..*"},"action":{"type":"block"}},
          {"trigger":{"url-filter":".*popads\\.net.*"},"action":{"type":"block"}},
          {"trigger":{"url-filter":".*popcash\\.net.*"},"action":{"type":"block"}}
        ]
        """
        WKContentRuleListStore.default().compileContentRuleList(
            forIdentifier: "streamy-basic-adblock",
            encodedContentRuleList: rules
        ) { list, _ in
            if let list {
                controller.add(list)
            }
        }

        let css = """
        (function(){
          try {
            const s=document.createElement('style');
            s.textContent='#ad,#ads,.ad,.ads,.adsbygoogle,[id*="google_ads"],[class*="ad-banner"],[id*="ad-banner"],.advertisement,.ad-container,.adbox,.ad-wrapper{display:none!important}';
            document.documentElement.appendChild(s);
          } catch(e) {}
        })();
        """
        controller.addUserScript(WKUserScript(
            source: css,
            injectionTime: .atDocumentEnd,
            forMainFrameOnly: false
        ))
    }

    final class Coordinator: NSObject, WKNavigationDelegate, WKUIDelegate {
        private let model: BrowserModel
        private let onMedia: (URL) -> Void
        private var progressObservation: NSKeyValueObservation?

        init(model: BrowserModel, onMedia: @escaping (URL) -> Void) {
            self.model = model
            self.onMedia = onMedia
        }

        func attach(_ webView: WKWebView) {
            progressObservation = webView.observe(\.estimatedProgress, options: [.new]) { [weak self, weak webView] _, _ in
                guard let self, let webView else { return }
                Task { @MainActor in self.model.sync(webView) }
            }
        }

        func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
            model.sync(webView)
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            model.sync(webView)
        }

        func webView(
            _ webView: WKWebView,
            decidePolicyFor navigationAction: WKNavigationAction,
            decisionHandler: @escaping (WKNavigationActionPolicy) -> Void
        ) {
            guard let url = navigationAction.request.url else {
                decisionHandler(.cancel)
                return
            }

            let scheme = url.scheme?.lowercased() ?? ""
            if ["mailto", "tel", "market", "intent", "magnet"].contains(scheme) {
                decisionHandler(.cancel)
                return
            }

            if Self.isMedia(url) {
                onMedia(url)
                decisionHandler(.cancel)
                return
            }

            decisionHandler(.allow)
        }

        func webView(
            _ webView: WKWebView,
            createWebViewWith configuration: WKWebViewConfiguration,
            for navigationAction: WKNavigationAction,
            windowFeatures: WKWindowFeatures
        ) -> WKWebView? {
            if let url = navigationAction.request.url {
                webView.load(URLRequest(url: url))
            }
            return nil
        }

        private static func isMedia(_ url: URL) -> Bool {
            let lower = url.absoluteString.lowercased()
            let clean = lower.split(separator: "?", maxSplits: 1).first.map(String.init) ?? lower
            return [".m3u8", ".mp4", ".mkv", ".avi", ".webm", ".mpd"].contains {
                clean.hasSuffix($0)
            }
        }
    }
}
