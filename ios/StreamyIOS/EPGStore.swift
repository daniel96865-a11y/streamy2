import Foundation
import Combine

@MainActor
final class EPGStore: ObservableObject {
    @Published private(set) var programsByChannel: [String: [EPGProgram]] = [:]
    @Published private(set) var isLoading = false
    @Published var lastError: String?
    @Published private(set) var lastRefresh: Date?

    private let lastRefreshKey = "ios.epg.lastRefresh"

    init() {
        loadCache()
    }

    func refresh(from url: URL) async {
        isLoading = true
        lastError = nil
        defer { isLoading = false }
        do {
            let programs = try await XMLTVParser.load(url: url)
            programsByChannel = Self.group(programs)
            lastRefresh = Date()
            UserDefaults.standard.set(lastRefresh, forKey: lastRefreshKey)
            saveCache(programs)
        } catch {
            lastError = error.localizedDescription
        }
    }

    func now(for channel: Channel, date: Date = Date()) -> EPGProgram? {
        let key = channel.epgID ?? channel.id
        return programsByChannel[key]?.first { $0.start <= date && $0.stop > date }
    }

    func next(for channel: Channel, date: Date = Date()) -> EPGProgram? {
        let key = channel.epgID ?? channel.id
        return programsByChannel[key]?.first { $0.start > date }
    }

    func programs(for channel: Channel) -> [EPGProgram] {
        programsByChannel[channel.epgID ?? channel.id] ?? []
    }

    private func loadCache() {
        lastRefresh = UserDefaults.standard.object(forKey: lastRefreshKey) as? Date
        guard let data = try? Data(contentsOf: cacheURL),
              let programs = try? JSONDecoder().decode([EPGProgram].self, from: data) else { return }
        programsByChannel = Self.group(programs)
    }

    private func saveCache(_ programs: [EPGProgram]) {
        guard let data = try? JSONEncoder().encode(programs) else { return }
        try? FileManager.default.createDirectory(
            at: cacheURL.deletingLastPathComponent(),
            withIntermediateDirectories: true
        )
        try? data.write(to: cacheURL, options: .atomic)
    }

    private var cacheURL: URL {
        let base = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first!
        return base.appendingPathComponent("Streamy", isDirectory: true)
            .appendingPathComponent("epg-cache.json")
    }

    private static func group(_ programs: [EPGProgram]) -> [String: [EPGProgram]] {
        Dictionary(grouping: programs, by: \.channelID)
            .mapValues { $0.sorted { $0.start < $1.start } }
    }
}
