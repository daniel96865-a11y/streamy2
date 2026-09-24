import SwiftUI

struct RootView: View {
    @EnvironmentObject private var state: AppState
    @EnvironmentObject private var playlists: PlaylistStore
    @EnvironmentObject private var prefs: PrefsStore
    @State private var selectedTab = 0

    var body: some View {
        Group {
            if playlists.sources.isEmpty {
                PlaylistSetupView {
                    Task { await state.reload() }
                }
            } else {
                TabView(selection: $selectedTab) {
                    LiveView()
                        .tabItem { Label("Live", systemImage: "tv") }
                        .tag(0)
                    MoviesView()
                        .tabItem { Label("Filme", systemImage: "film") }
                        .tag(1)
                    SeriesView()
                        .tabItem { Label("Serien", systemImage: "rectangle.stack") }
                        .tag(2)
                    BrowserScreen()
                        .tabItem { Label("Browser", systemImage: "globe") }
                        .tag(3)
                    SettingsView()
                        .tabItem { Label("Einstellungen", systemImage: "gearshape") }
                        .tag(4)
                }
                .tint(Color(hex: prefs.accentHex))
            }
        }
        .background(Color.black)
        .fullScreenCover(item: $state.playbackItem) { item in
            PlayerScreen(item: item, prefs: prefs)
        }
    }
}
