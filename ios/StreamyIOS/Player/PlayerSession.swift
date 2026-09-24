import AVFoundation
import AVKit
import Foundation
#if canImport(VLCKit)
import VLCKit
#endif

@MainActor
final class PlayerSession: ObservableObject {
    @Published private(set) var usingVLC = false
    @Published var errorMessage: String?
    @Published var isPlaying = false
    @Published var audioOptions: [String] = []
    @Published var selectedAudioIndex: Int = 0

    let item: PlaybackItem
    let avPlayer: AVPlayer
    private let preference: PlayerPreference
    private let resizeMode: VideoResizeMode
    private var statusObserver: NSKeyValueObservation?

    #if canImport(VLCKit)
    let vlcPlayer = VLCMediaPlayer()
    #endif

    init(item: PlaybackItem, prefs: PrefsStore) {
        self.item = item
        self.preference = prefs.playerPreference
        self.resizeMode = prefs.resizeMode
        self.avPlayer = AVPlayer(url: item.url)
        configure()
    }

    var videoGravity: AVLayerVideoGravity {
        switch resizeMode {
        case .fit: return .resizeAspect
        case .fill: return .resizeAspectFill
        case .stretch: return .resize
        }
    }

    func play() {
        if usingVLC {
            #if canImport(VLCKit)
            vlcPlayer.play()
            #endif
        } else {
            avPlayer.play()
        }
        isPlaying = true
    }

    func pause() {
        if usingVLC {
            #if canImport(VLCKit)
            vlcPlayer.pause()
            #endif
        } else {
            avPlayer.pause()
        }
        isPlaying = false
    }

    func toggle() {
        isPlaying ? pause() : play()
    }

    func stop() {
        avPlayer.pause()
        avPlayer.replaceCurrentItem(with: nil)
        #if canImport(VLCKit)
        vlcPlayer.stop()
        #endif
        isPlaying = false
    }

    func forceVLC() {
        switchToVLC(reason: nil)
    }

    func forceAVPlayer() {
        #if canImport(VLCKit)
        vlcPlayer.stop()
        #endif
        usingVLC = false
        avPlayer.replaceCurrentItem(with: AVPlayerItem(url: item.url))
        observeAV()
        play()
    }

    func refreshAudioOptions() {
        guard !usingVLC, let asset = avPlayer.currentItem?.asset,
              let group = asset.mediaSelectionGroup(forMediaCharacteristic: .audible) else {
            audioOptions = []
            return
        }
        audioOptions = group.options.enumerated().map { index, option in
            let name = option.displayName.trimmingCharacters(in: .whitespacesAndNewlines)
            return name.isEmpty ? "Spur (index + 1)" : name
        }
        if let selected = avPlayer.currentItem?.currentMediaSelection.selectedMediaOption(in: group),
           let index = group.options.firstIndex(of: selected) {
            selectedAudioIndex = index
        }
    }

    func selectAudio(index: Int) {
        guard !usingVLC, let item = avPlayer.currentItem,
              let group = item.asset.mediaSelectionGroup(forMediaCharacteristic: .audible),
              group.options.indices.contains(index) else { return }
        item.select(group.options[index], in: group)
        selectedAudioIndex = index
    }

    private func configure() {
        switch preference {
        case .vlc:
            switchToVLC(reason: nil)
        case .avPlayer:
            usingVLC = false
            observeAV()
        case .automatic:
            let ext = item.url.pathExtension.lowercased()
            if ["m3u8", "mp4", "m4v", "mov"].contains(ext) || item.isLive {
                usingVLC = false
                observeAV()
            } else {
                switchToVLC(reason: nil)
            }
        }
    }

    private func observeAV() {
        guard let currentItem = avPlayer.currentItem else { return }
        statusObserver = currentItem.observe(.status, options: [.new, .initial]) { [weak self] item, _ in
            Task { @MainActor in
                guard let self else { return }
                if item.status == .failed && self.preference == .automatic {
                    self.switchToVLC(reason: item.error?.localizedDescription)
                } else if item.status == .readyToPlay {
                    self.refreshAudioOptions()
                }
            }
        }
    }

    private func switchToVLC(reason: String?) {
        #if canImport(VLCKit)
        avPlayer.pause()
        usingVLC = true
        let media = VLCMedia(url: item.url)
        vlcPlayer.media = media
        if let reason { errorMessage = "Apple Player: (reason) · VLC-Fallback aktiv" }
        vlcPlayer.play()
        isPlaying = true
        #else
        usingVLC = false
        errorMessage = reason ?? "VLC ist in diesem Build nicht verfügbar."
        #endif
    }
}
