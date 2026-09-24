import Foundation

@MainActor
final class PlaylistStore: ObservableObject {
    @Published private(set) var sources: [PlaylistSource] = []
    @Published var selectedSourceID: UUID? {
        didSet {
            UserDefaults.standard.set(selectedSourceID?.uuidString, forKey: "selectedSourceID")
        }
    }

    private let storageKey = "playlistSources"

    init() {
        load()
    }

    var selectedSource: PlaylistSource? {
        sources.first { $0.id == selectedSourceID } ?? sources.first
    }

    func addXtream(name: String, baseURL: String, username: String, password: String, epgURL: String? = nil) {
        let source = PlaylistSource(name: name, kind: .xtream, baseURL: normalize(baseURL), epgURL: epgURL)
        KeychainStore.save(username, for: "xtream.(source.id.uuidString).username")
        KeychainStore.save(password, for: "xtream.(source.id.uuidString).password")
        sources.append(source)
        selectedSourceID = source.id
        save()
    }

    func addM3U(name: String, m3uURL: String, epgURL: String? = nil) {
        let source = PlaylistSource(name: name, kind: .m3u, m3uURL: m3uURL, epgURL: epgURL)
        sources.append(source)
        selectedSourceID = source.id
        save()
    }

    func update(_ source: PlaylistSource, username: String? = nil, password: String? = nil) {
        guard let index = sources.firstIndex(where: { $0.id == source.id }) else { return }
        sources[index] = source
        if let username { KeychainStore.save(username, for: "xtream.(source.id.uuidString).username") }
        if let password { KeychainStore.save(password, for: "xtream.(source.id.uuidString).password") }
        save()
    }

    func delete(at offsets: IndexSet) {
        for index in offsets {
            let source = sources[index]
            KeychainStore.delete("xtream.(source.id.uuidString).username")
            KeychainStore.delete("xtream.(source.id.uuidString).password")
            if selectedSourceID == source.id { selectedSourceID = nil }
        }
        sources.remove(atOffsets: offsets)
        if selectedSourceID == nil { selectedSourceID = sources.first?.id }
        save()
    }

    func credentials(for source: PlaylistSource) -> (username: String, password: String)? {
        guard source.kind == .xtream,
              let username = KeychainStore.load("xtream.(source.id.uuidString).username"),
              let password = KeychainStore.load("xtream.(source.id.uuidString).password") else { return nil }
        return (username, password)
    }

    private func normalize(_ raw: String) -> String {
        var value = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        while value.hasSuffix("/") { value.removeLast() }
        return value
    }

    private func save() {
        if let data = try? JSONEncoder().encode(sources) {
            UserDefaults.standard.set(data, forKey: storageKey)
        }
    }

    private func load() {
        if let data = UserDefaults.standard.data(forKey: storageKey),
           let decoded = try? JSONDecoder().decode([PlaylistSource].self, from: data) {
            sources = decoded
        }
        if let raw = UserDefaults.standard.string(forKey: "selectedSourceID") {
            selectedSourceID = UUID(uuidString: raw)
        }
        if selectedSourceID == nil { selectedSourceID = sources.first?.id }
    }
}
