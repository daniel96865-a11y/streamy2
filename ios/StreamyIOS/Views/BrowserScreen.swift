import SwiftUI
import WebKit

struct BrowserScreen: View {
    @State private var address = "https://www.google.com"
    @State private var loadedURL = URL(string: "https://www.google.com")!

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                HStack {
                    TextField("Adresse", text: $address)
                        .textFieldStyle(.roundedBorder)
                        .textInputAutocapitalization(.never)
                        .keyboardType(.URL)
                        .onSubmit { navigate() }
                    Button {
                        navigate()
                    } label: {
                        Image(systemName: "arrow.right.circle.fill")
                            .font(.title2)
                    }
                }
                .padding()

                StreamyWebView(url: loadedURL)
            }
            .navigationTitle("Browser")
            .navigationBarTitleDisplayMode(.inline)
        }
    }

    private func navigate() {
        var value = address.trimmingCharacters(in: .whitespacesAndNewlines)
        if !value.contains("://") { value = "https://" + value }
        if let url = URL(string: value) {
            loadedURL = url
            address = value
        }
    }
}

private struct StreamyWebView: UIViewRepresentable {
    let url: URL

    func makeUIView(context: Context) -> WKWebView {
        let configuration = WKWebViewConfiguration()
        configuration.allowsInlineMediaPlayback = true
        configuration.mediaTypesRequiringUserActionForPlayback = []
        let view = WKWebView(frame: .zero, configuration: configuration)
        view.allowsBackForwardNavigationGestures = true
        return view
    }

    func updateUIView(_ webView: WKWebView, context: Context) {
        if webView.url != url {
            webView.load(URLRequest(url: url))
        }
    }
}
