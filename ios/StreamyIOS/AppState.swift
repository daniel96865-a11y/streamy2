import Foundation
import Combine

@MainActor
final class AppState: ObservableObject {
    let playlists: PlaylistStore
    let prefs: PrefsStore
    let epg: EPGStore

    @Published var liveCategories: [Category] = []
    @Published var channels: [Channel] = []
    @Published var vodCategories: [Category] = []
    @Published var movies: [Movie] = []
    @Published var seriesCategories: [Category] = []
    @Published var series: [Series] = []
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var playbackItem: PlaybackItem?
    @Published var resolvingExtraLive = false
    @Published private(set) var favoriteChannelKeys: Set<String> = []
    @Published private(set) var lastChannelKey: String?
    @Published private(set) var accountInfo: XtreamAccountInfo?

    private let favoritesStorageKey = "ios.favoriteChannels"
    private let lastChannelStorageKey = "ios.lastChannel"

    init(playlists: PlaylistStore, prefs: PrefsStore, epg: EPGStore) {
        self.playlists = playlists
        self.prefs = prefs
        self.epg = epg
        favoriteChannelKeys = Set(UserDefaults.standard.stringArray(forKey: favoritesStorageKey) ?? [])
        lastChannelKey = UserDefaults.standard.string(forKey: lastChannelStorageKey)
    }

    var lastChannel: Channel? {
        guard let key = lastChannelKey else { return nil }
        return channels.first { channelKey($0) == key }
    }

    func reload() async {
        guard let source = playlists.selectedSource else {
            liveCategories = []
            channels = []
            vodCategories = []
            movies = []
            seriesCategories = []
            series = []
            accountInfo = nil
            return
        }

        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            if source.kind == .xtream {
                guard let api = XtreamAPI(source: source, credentials: playlists.credentials(for: source)) else {
                    throw URLError(.userAuthenticationRequired)
                }
                async let liveCats = api.categories(kind: "live")
                async let liveItems = api.liveChannels(streamExtension: prefs.streamFormat.fileExtension)
                async let movieCats = api.categories(kind: "vod")
                async let movieItems = api.movies()
                async let seriesCats = api.categories(kind: "series")
                async let seriesItems = api.series()

                liveCategories = try await liveCats
                channels = try await liveItems
                vodCategories = try await movieCats
                movies = try await movieItems
                seriesCategories = try await seriesCats
                series = try await seriesItems
                accountInfo = try? await api.accountInfo()

                let epgURL = source.epgURL.flatMap(URL.init(string:)) ?? api.xmltvURL()
                if let epgURL { Task { await epg.refresh(from: epgURL) } }
            } else if let raw = source.m3uURL, let url = URL(string: raw) {
                channels = try await M3UParser.load(url: url)
                let names = Set(channels.compactMap(\.categoryID)).sorted()
                liveCategories = names.map { Category(id: $0, name: $0) }
                vodCategories = []
                movies = []
                seriesCategories = []
                series = []
                accountInfo = nil
                if let epgRaw = source.epgURL, let epgURL = URL(string: epgRaw) {
                    Task { await epg.refresh(from: epgURL) }
                }
            }

            await mergeExtraLive()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func isFavorite(_ channel: Channel) -> Bool {
        favoriteChannelKeys.contains(channelKey(channel))
    }

    func toggleFavorite(_ channel: Channel) {
        let key = channelKey(channel)
        if favoriteChannelKeys.contains(key) {
            favoriteChannelKeys.remove(key)
        } else {
            favoriteChannelKeys.insert(key)
        }
        UserDefaults.standard.set(Array(favoriteChannelKeys).sorted(), forKey: favoritesStorageKey)
    }

    func play(channel: Channel) {
        if prefs.rememberLastChannel {
            let key = channelKey(channel)
            lastChannelKey = key
            UserDefaults.standard.set(key, forKey: lastChannelStorageKey)
        }

        if ExtraLiveService.isExtra(channel) {
            resolvingExtraLive = true
            Task {
                do {
                    let resolved = try await ExtraLiveService.shared.resolve(channel)
                    playbackItem = PlaybackItem(
                        title: channel.name,
                        subtitle: epg.now(for: channel)?.title,
                        url: resolved,
                        logoURL: channel.logoURL,
                        isLive: true,
                        channel: channel
                    )
                    errorMessage = nil
                } catch {
                    errorMessage = "Live Extra konnte nicht gestartet werden: \(error.localizedDescription)"
                }
                resolvingExtraLive = false
            }
            return
        }

        playbackItem = PlaybackItem(
            title: channel.name,
            subtitle: epg.now(for: channel)?.title,
            url: channel.streamURL,
            logoURL: channel.logoURL,
            isLive: true,
            channel: channel
        )
    }

    func play(movie: Movie) {
        playbackItem = PlaybackItem(
            title: movie.name,
            subtitle: movie.plot,
            url: movie.streamURL,
            logoURL: movie.posterURL,
            isLive: false,
            channel: nil
        )
    }

    func play(episode: Episode, series: Series) {
        playbackItem = PlaybackItem(
            title: series.name,
            subtitle: "S\(episode.season) E\(episode.episode) · \(episode.title)",
            url: episode.streamURL,
            logoURL: series.posterURL,
            isLive: false,
            channel: nil
        )
    }

    func catchup(channel: Channel, program: EPGProgram) {
        guard let source = playlists.selectedSource,
              let api = XtreamAPI(source: source, credentials: playlists.credentials(for: source)),
              let url = api.catchupURL(channel: channel, program: program) else { return }
        playbackItem = PlaybackItem(
            title: channel.name,
            subtitle: program.title,
            url: url,
            logoURL: channel.logoURL,
            isLive: false,
            channel: channel
        )
    }

    func episodes(for series: Series) async throws -> [Episode] {
        guard let source = playlists.selectedSource,
              let api = XtreamAPI(source: source, credentials: playlists.credentials(for: source)) else { return [] }
        return try await api.episodes(seriesID: series.id)
    }

    func refreshExtraLive() async {
        guard prefs.extraLiveEnabled else {
            removeExtraLive()
            return
        }
        let extra = await ExtraLiveService.shared.refreshAll()
        applyExtraLive(extra)
    }

    private func mergeExtraLive() async {
        guard prefs.extraLiveEnabled else {
            removeExtraLive()
            return
        }

        let cached = await ExtraLiveService.shared.cachedChannels()
        if !cached.isEmpty {
            applyExtraLive(cached)
        }

        let fresh = await ExtraLiveService.shared.fetchFast()
        if !fresh.isEmpty {
            applyExtraLive(fresh)
        }
    }

    private func applyExtraLive(_ extra: [Channel]) {
        let baseChannels = channels.filter { !ExtraLiveService.isExtra($0) }
        let unique = Dictionary(grouping: extra, by: \.id).compactMap { $0.value.first }
        channels = baseChannels + unique

        let baseCategories = liveCategories.filter {
            $0.id != ExtraLiveService.germanCategoryID &&
            $0.id != ExtraLiveService.polishCategoryID
        }
        liveCategories = ExtraLiveService.categories(for: unique) + baseCategories
    }

    private func removeExtraLive() {
        channels.removeAll { ExtraLiveService.isExtra($0) }
        liveCategories.removeAll {
            $0.id == ExtraLiveService.germanCategoryID ||
            $0.id == ExtraLiveService.polishCategoryID
        }
    }

    private func channelKey(_ channel: Channel) -> String {
        let sourceID = playlists.selectedSource?.id.uuidString ?? "unknown"
        return sourceID + "|" + channel.id
    }
}
