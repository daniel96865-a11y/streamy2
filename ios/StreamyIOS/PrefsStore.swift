import Foundation
import SwiftUI

@MainActor
final class PrefsStore: ObservableObject {
    @Published var playerPreference: PlayerPreference {
        didSet { defaults.set(playerPreference.rawValue, forKey: "playerPreference") }
    }
    @Published var resizeMode: VideoResizeMode {
        didSet { defaults.set(resizeMode.rawValue, forKey: "resizeMode") }
    }
    @Published var bufferMode: BufferMode {
        didSet { defaults.set(bufferMode.rawValue, forKey: "bufferMode") }
    }
    @Published var audioMode: AudioMode {
        didSet { defaults.set(audioMode.rawValue, forKey: "audioMode") }
    }
    @Published var streamFormat: StreamFormat {
        didSet { defaults.set(streamFormat.rawValue, forKey: "streamFormat") }
    }
    @Published var extraLiveEnabled: Bool {
        didSet { defaults.set(extraLiveEnabled, forKey: "extraLiveEnabled") }
    }
    @Published var extraMediaEnabled: Bool {
        didSet { defaults.set(extraMediaEnabled, forKey: "extraMediaEnabled") }
    }
    @Published var accentHex: String {
        didSet { defaults.set(accentHex, forKey: "accentHex") }
    }
    @Published var hideTopBar: Bool {
        didSet { defaults.set(hideTopBar, forKey: "hideTopBar") }
    }
    @Published var rememberLastChannel: Bool {
        didSet { defaults.set(rememberLastChannel, forKey: "rememberLastChannel") }
    }
    @Published var epgRefreshHours: Int {
        didSet { defaults.set(epgRefreshHours, forKey: "epgRefreshHours") }
    }

    private let defaults = UserDefaults.standard

    init() {
        let stored = UserDefaults.standard
        playerPreference = PlayerPreference(rawValue: stored.string(forKey: "playerPreference") ?? "") ?? .automatic
        resizeMode = VideoResizeMode(rawValue: stored.string(forKey: "resizeMode") ?? "") ?? .fit
        bufferMode = BufferMode(rawValue: stored.string(forKey: "bufferMode") ?? "") ?? .normal
        audioMode = AudioMode(rawValue: stored.string(forKey: "audioMode") ?? "") ?? .automatic
        streamFormat = StreamFormat(rawValue: stored.string(forKey: "streamFormat") ?? "") ?? .hls
        extraLiveEnabled = stored.object(forKey: "extraLiveEnabled") as? Bool ?? true
        extraMediaEnabled = stored.object(forKey: "extraMediaEnabled") as? Bool ?? true
        accentHex = stored.string(forKey: "accentHex") ?? "#5B9DFF"
        hideTopBar = stored.bool(forKey: "hideTopBar")
        rememberLastChannel = stored.object(forKey: "rememberLastChannel") as? Bool ?? true
        epgRefreshHours = stored.object(forKey: "epgRefreshHours") as? Int ?? 12
    }
}

extension Color {
    init(hex: String) {
        let value = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var number: UInt64 = 0
        Scanner(string: value).scanHexInt64(&number)
        let r, g, b: Double
        if value.count == 6 {
            r = Double((number >> 16) & 0xff) / 255
            g = Double((number >> 8) & 0xff) / 255
            b = Double(number & 0xff) / 255
        } else {
            r = 0.36; g = 0.62; b = 1.0
        }
        self = Color(red: r, green: g, blue: b)
    }
}
