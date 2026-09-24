import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var state: AppState
    @EnvironmentObject private var playlists: PlaylistStore
    @EnvironmentObject private var prefs: PrefsStore
    @EnvironmentObject private var epg: EPGStore

    @StateObject private var sync = DeviceSyncService()
    @State private var showAdd = false
    @State private var syncMessage = ""

    var body: some View {
        NavigationStack {
            Form {
                Section("Wiedergabelisten") {
                    if !playlists.sources.isEmpty {
                        Picker("Aktive Liste", selection: $playlists.selectedSourceID) {
                            ForEach(playlists.sources) { source in
                                Text(source.name).tag(Optional(source.id))
                            }
                        }
                        .onChange(of: playlists.selectedSourceID) { _ in
                            Task { await state.reload() }
                        }
                    }

                    ForEach(playlists.sources) { source in
                        NavigationLink {
                            PlaylistEditView(
                                source: source,
                                credentials: playlists.credentials(for: source)
                            )
                        } label: {
                            HStack {
                                VStack(alignment: .leading) {
                                    Text(source.name)
                                    Text(source.kind == .xtream ? "Xtream Codes" : "M3U")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                                Spacer()
                                if playlists.selectedSourceID == source.id {
                                    Image(systemName: "checkmark.circle.fill")
                                        .foregroundStyle(.green)
                                }
                            }
                        }
                    }
                    .onDelete { offsets in
                        playlists.delete(at: offsets)
                        Task { await state.reload() }
                    }

                    Button {
                        showAdd = true
                    } label: {
                        Label("Wiedergabeliste hinzufügen", systemImage: "plus.circle")
                    }

                    Button {
                        Task { await state.reload() }
                    } label: {
                        Label("Inhalte aktualisieren", systemImage: "arrow.clockwise")
                    }
                }

                if let account = state.accountInfo {
                    Section("Xtream-Zugang") {
                        LabeledContent("Status", value: account.status)
                        LabeledContent("Ablaufdatum", value: account.expiryLabel)
                        if let active = account.activeConnections {
                            LabeledContent("Aktive Verbindungen", value: String(active))
                        }
                        if let maximum = account.maxConnections {
                            LabeledContent("Max. Verbindungen", value: String(maximum))
                        }
                    }
                }

                Section("Player & Wiedergabe") {
                    Picker("Player", selection: $prefs.playerPreference) {
                        ForEach(PlayerPreference.allCases) { option in
                            Text(option.label).tag(option)
                        }
                    }

                    Picker("Audio / Surround", selection: $prefs.audioMode) {
                        ForEach(AudioMode.allCases) { option in
                            Text(option.label).tag(option)
                        }
                    }

                    Picker("Puffer", selection: $prefs.bufferMode) {
                        ForEach(BufferMode.allCases) { option in
                            Text(option.label).tag(option)
                        }
                    }

                    Picker("Stream-Format", selection: $prefs.streamFormat) {
                        ForEach(StreamFormat.allCases) { option in
                            Text(option.label).tag(option)
                        }
                    }
                    .onChange(of: prefs.streamFormat) { _ in
                        Task { await state.reload() }
                    }

                    Picker("Bildmodus", selection: $prefs.resizeMode) {
                        ForEach(VideoResizeMode.allCases) { option in
                            Text(option.label).tag(option)
                        }
                    }

                    Toggle("Letzten Sender merken", isOn: $prefs.rememberLastChannel)

                    Text("Auto nutzt den Apple-Player und fällt bei nicht unterstützten Formaten auf VLC zurück. Puffer, Audio-Ausgabe, HLS/TS und Bildmodus entsprechen den Streamy-Player-Einstellungen.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                Section("Live TV & EPG") {
                    Picker("EPG aktualisieren", selection: $prefs.epgRefreshHours) {
                        Text("Alle 6 Stunden").tag(6)
                        Text("Alle 12 Stunden").tag(12)
                        Text("Alle 24 Stunden").tag(24)
                    }

                    Button {
                        refreshEPG()
                    } label: {
                        HStack {
                            Label("EPG jetzt aktualisieren", systemImage: "calendar.badge.clock")
                            Spacer()
                            if epg.isLoading { ProgressView() }
                        }
                    }

                    if let date = epg.lastRefresh {
                        Text("Zuletzt: \(date.formatted(date: .abbreviated, time: .shortened))")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    if let error = epg.lastError {
                        Text(error).font(.caption).foregroundStyle(.red)
                    }
                }

                Section("Design & Oberfläche") {
                    Picker("Akzentfarbe", selection: $prefs.accentHex) {
                        Text("Blau").tag("#5B9DFF")
                        Text("Grün").tag("#34C759")
                        Text("Orange").tag("#FF9500")
                        Text("Pink").tag("#FF2D55")
                        Text("Lila").tag("#AF52DE")
                    }
                    Toggle("Oberen Bereich ausblenden", isOn: $prefs.hideTopBar)
                }

                Section("Geräte-Synchronisierung") {
                    Text(sync.statusText)
                        .font(.caption)
                        .foregroundStyle(.secondary)

                    ForEach(sync.peers, id: \.self) { peer in
                        Button("Mit \(peer.displayName) verbinden") {
                            sync.connect(peer)
                        }
                    }

                    if !sync.connectedPeers.isEmpty, let source = playlists.selectedSource {
                        Button {
                            let credentials = playlists.credentials(for: source)
                            do {
                                try sync.send(PlaylistTransfer(
                                    source: source,
                                    username: credentials?.username,
                                    password: credentials?.password
                                ))
                                syncMessage = "Gesendet"
                            } catch {
                                syncMessage = error.localizedDescription
                            }
                        } label: {
                            Label("Aktive Wiedergabeliste senden", systemImage: "iphone.and.arrow.forward")
                        }
                    }

                    if !syncMessage.isEmpty {
                        Text(syncMessage).font(.caption)
                    }
                }

                Section("Updates") {
                    LabeledContent("iOS-Version", value: "0.1.0")
                    Text("Auf iPhone und iPad werden neue Builds über TestFlight bzw. später den App Store verteilt. Die Android-APK-Aktualisierung wird auf iOS nicht verwendet.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Einstellungen")
            .sheet(isPresented: $showAdd) {
                PlaylistSetupView {
                    Task { await state.reload() }
                }
                .environmentObject(playlists)
            }
            .onAppear { sync.start() }
            .onDisappear { sync.stop() }
            .onChange(of: sync.receivedTransfer?.source.id) { _ in
                if let transfer = sync.receivedTransfer {
                    playlists.importTransfer(transfer)
                    sync.receivedTransfer = nil
                    Task { await state.reload() }
                }
            }
        }
    }

    private func refreshEPG() {
        guard let source = playlists.selectedSource else { return }
        if let raw = source.epgURL, let url = URL(string: raw) {
            Task { await epg.refresh(from: url) }
            return
        }
        if let api = XtreamAPI(source: source, credentials: playlists.credentials(for: source)),
           let url = api.xmltvURL() {
            Task { await epg.refresh(from: url) }
        }
    }
}
