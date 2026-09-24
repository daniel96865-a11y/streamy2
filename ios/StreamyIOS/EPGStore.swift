import Foundation

@MainActor
final class EPGStore: ObservableObject {
    @Published private(set) var programsByChannel: [String: [EPGProgram]] = [:]
    @Published private(set) var isLoading = false
    @Published var lastError: String?
    @Published private(set) var lastRefresh: Date?

    func refresh(from url: URL) async {
        isLoading = true
        lastError = nil
        defer { isLoading = false }
        do {
            let programs = try await XMLTVParser.load(url: url)
            programsByChannel = Dictionary(grouping: programs, by: \.channelID)
                .mapValues { $0.sorted { $0.start < $1.start } }
            lastRefresh = Date()
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
}
