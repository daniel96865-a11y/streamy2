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
        playerPreference = PlayerPreference(rawValue: defaults.string(forKey: "playerPreference") ?? "") ?? .automatic
        resizeMode = VideoResizeMode(rawValue: defaults.string(forKey: "resizeMode") ?? "") ?? .fit
        accentHex = defaults.string(forKey: "accentHex") ?? "#5B9DFF"
        hideTopBar = defaults.bool(forKey: "hideTopBar")
        rememberLastChannel = defaults.object(forKey: "rememberLastChannel") as? Bool ?? true
        epgRefreshHours = defaults.object(forKey: "epgRefreshHours") as? Int ?? 12
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
