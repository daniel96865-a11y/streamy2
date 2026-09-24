import SwiftUI

@main
@MainActor
struct StreamyIOSApp: App {
    @StateObject private var playlists: PlaylistStore
    @StateObject private var prefs: PrefsStore
    @StateObject private var epg: EPGStore
    @StateObject private var state: AppState

    init() {
        let playlists = PlaylistStore()
        let prefs = PrefsStore()
        let epg = EPGStore()
        _playlists = StateObject(wrappedValue: playlists)
        _prefs = StateObject(wrappedValue: prefs)
        _epg = StateObject(wrappedValue: epg)
        _state = StateObject(wrappedValue: AppState(playlists: playlists, prefs: prefs, epg: epg))
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(state)
                .environmentObject(playlists)
                .environmentObject(prefs)
                .environmentObject(epg)
                .task {
                    await state.reload()
                }
        }
    }
}
