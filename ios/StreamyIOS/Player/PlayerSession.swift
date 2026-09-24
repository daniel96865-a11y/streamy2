import AVFoundation
import Combine
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
    private let bufferMode: BufferMode
    private let audioMode: AudioMode
    private var statusObserver: NSKeyValueObservation?

    #if canImport(VLCKit)
    let vlcPlayer = VLCMediaPlayer()
    #endif

    init(item: PlaybackItem, prefs: PrefsStore) {
        self.item = item
        self.preference = prefs.playerPreference
        self.resizeMode = prefs.resizeMode
        self.bufferMode = prefs.bufferMode
        self.audioMode = prefs.audioMode
        self.avPlayer = AVPlayer(url: item.url)
        configureAudioSession()
        configureAVPlayerBuffer()
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
        let newItem = AVPlayerItem(url: item.url)
        newItem.preferredForwardBufferDuration = TimeInterval(bufferMode.milliseconds) / 1000.0
        avPlayer.replaceCurrentItem(with: newItem)
        configureAVPlayerBuffer()
        observeAV()
        play()
    }

    func refreshAudioOptions() {
        guard !usingVLC,
              let currentItem = avPlayer.currentItem,
              let group = currentItem.asset.mediaSelectionGroup(forMediaCharacteristic: .audible) else {
            audioOptions = []
            return
        }
        audioOptions = group.options.enumerated().map { index, option in
            let name = option.displayName.trimmingCharacters(in: .whitespacesAndNewlines)
            return name.isEmpty ? "Spur \(index + 1)" : name
        }
        if let selected = currentItem.currentMediaSelection.selectedMediaOption(in: group),
           let index = group.options.firstIndex(of: selected) {
            selectedAudioIndex = index
        } else if let first = group.options.first {
            currentItem.select(first, in: group)
            selectedAudioIndex = 0
        }
    }

    func selectAudio(index: Int) {
        guard !usingVLC, let currentItem = avPlayer.currentItem,
              let group = currentItem.asset.mediaSelectionGroup(forMediaCharacteristic: .audible),
              group.options.indices.contains(index) else { return }
        currentItem.select(group.options[index], in: group)
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

    private func configureAVPlayerBuffer() {
        avPlayer.automaticallyWaitsToMinimizeStalling = bufferMode != .low
        avPlayer.currentItem?.preferredForwardBufferDuration =
            TimeInterval(bufferMode.milliseconds) / 1000.0
    }

    private func configureAudioSession() {
        let session = AVAudioSession.sharedInstance()
        do {
            try session.setCategory(.playback, mode: .moviePlayback, options: [.allowAirPlay])
            try session.setActive(true)
            switch audioMode {
            case .stereo:
                if session.maximumOutputNumberOfChannels >= 2 {
                    try session.setPreferredOutputNumberOfChannels(2)
                }
            case .surround:
                let maxChannels = session.maximumOutputNumberOfChannels
                if maxChannels > 2 {
                    try session.setPreferredOutputNumberOfChannels(maxChannels)
                }
            case .automatic:
                break
            }
        } catch {
            // Audio route capabilities vary by device/AirPlay/HDMI. Playback must
            // continue even when a preferred channel count cannot be applied.
        }
    }

    private func observeAV() {
        guard let currentItem = avPlayer.currentItem else { return }
        statusObserver = currentItem.observe(\.status, options: [.new, .initial]) { [weak self] observed, _ in
            Task { @MainActor in
                guard let self else { return }
                if observed.status == .failed && self.preference == .automatic {
                    self.switchToVLC(reason: observed.error?.localizedDescription)
                } else if observed.status == .readyToPlay {
                    self.refreshAudioOptions()
                }
            }
        }
    }

    private func switchToVLC(reason: String?) {
        #if canImport(VLCKit)
        avPlayer.pause()
        usingVLC = true
        guard let media = VLCMedia(url: item.url) else {
            usingVLC = false
            errorMessage = reason ?? "VLC konnte den Stream nicht öffnen."
            return
        }
        media.addOption(":network-caching=\(bufferMode.milliseconds)")
        if audioMode == .stereo {
            media.addOption(":stereo-mode=stereo")
        }
        vlcPlayer.media = media
        if let reason { errorMessage = "Apple Player: \(reason) · VLC-Fallback aktiv" }
        vlcPlayer.play()
        isPlaying = true
        #else
        usingVLC = false
        errorMessage = reason ?? "VLC ist in diesem Build nicht verfügbar."
        #endif
    }
}
