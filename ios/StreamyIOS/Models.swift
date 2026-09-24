import Foundation

enum PlaylistKind: String, Codable, CaseIterable, Identifiable {
    case xtream
    case m3u
    var id: String { rawValue }
}

struct PlaylistSource: Identifiable, Codable, Hashable {
    let id: UUID
    var name: String
    var kind: PlaylistKind
    var baseURL: String?
    var m3uURL: String?
    var epgURL: String?
    var createdAt: Date

    init(id: UUID = UUID(), name: String, kind: PlaylistKind, baseURL: String? = nil, m3uURL: String? = nil, epgURL: String? = nil, createdAt: Date = Date()) {
        self.id = id
        self.name = name
        self.kind = kind
        self.baseURL = baseURL
        self.m3uURL = m3uURL
        self.epgURL = epgURL
        self.createdAt = createdAt
    }
}

struct Category: Identifiable, Hashable {
    let id: String
    let name: String
}

struct Channel: Identifiable, Hashable {
    let id: String
    let name: String
    let categoryID: String?
    let logoURL: URL?
    let streamURL: URL
    let epgID: String?
    let streamID: Int?
    let hasCatchup: Bool
    let catchupDays: Int
}

struct Movie: Identifiable, Hashable {
    let id: String
    let name: String
    let categoryID: String?
    let posterURL: URL?
    let streamURL: URL
    let plot: String?
    let year: String?
    let rating: String?
}

struct Series: Identifiable, Hashable {
    let id: String
    let name: String
    let categoryID: String?
    let posterURL: URL?
    let plot: String?
    let rating: String?
}

struct Episode: Identifiable, Hashable {
    let id: String
    let season: Int
    let episode: Int
    let title: String
    let streamURL: URL
    let plot: String?
}

struct EPGProgram: Identifiable, Hashable {
    let id: String
    let channelID: String
    let start: Date
    let stop: Date
    let title: String
    let subtitle: String?
    let description: String?
}

struct PlaybackItem: Identifiable, Hashable {
    let id = UUID()
    let title: String
    let subtitle: String?
    let url: URL
    let logoURL: URL?
    let isLive: Bool
    let channel: Channel?
}

enum PlayerPreference: String, Codable, CaseIterable, Identifiable {
    case automatic
    case avPlayer
    case vlc
    var id: String { rawValue }

    var label: String {
        switch self {
        case .automatic: return "Automatisch"
        case .avPlayer: return "Apple Player"
        case .vlc: return "VLC"
        }
    }
}

enum VideoResizeMode: String, Codable, CaseIterable, Identifiable {
    case fit, fill, stretch
    var id: String { rawValue }

    var label: String {
        switch self {
        case .fit: return "Anpassen"
        case .fill: return "Zoom"
        case .stretch: return "Strecken"
        }
    }
}


enum BufferMode: String, Codable, CaseIterable, Identifiable {
    case low, normal, high, max
    var id: String { rawValue }

    var label: String {
        switch self {
        case .low: return "Kurz"
        case .normal: return "Normal"
        case .high: return "Stabil"
        case .max: return "Extra"
        }
    }

    var milliseconds: Int {
        switch self {
        case .low: return 1500
        case .normal: return 2500
        case .high: return 4000
        case .max: return 8000
        }
    }
}

enum AudioMode: String, Codable, CaseIterable, Identifiable {
    case automatic, surround, stereo
    var id: String { rawValue }

    var label: String {
        switch self {
        case .automatic: return "Automatisch"
        case .surround: return "Surround"
        case .stereo: return "Stereo"
        }
    }
}

enum StreamFormat: String, Codable, CaseIterable, Identifiable {
    case hls, ts
    var id: String { rawValue }
    var label: String { self == .hls ? "HLS" : "TS" }
    var fileExtension: String { self == .hls ? "m3u8" : "ts" }
}
