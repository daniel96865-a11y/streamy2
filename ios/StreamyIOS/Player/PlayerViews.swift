import AVKit
import SwiftUI
#if canImport(VLCKit)
import VLCKit
#endif

struct AVPlayerContainer: UIViewControllerRepresentable {
    let session: PlayerSession

    func makeUIViewController(context: Context) -> AVPlayerViewController {
        let controller = AVPlayerViewController()
        controller.player = session.avPlayer
        controller.allowsPictureInPicturePlayback = true
        controller.canStartPictureInPictureAutomaticallyFromInline = true
        controller.entersFullScreenWhenPlaybackBegins = false
        controller.exitsFullScreenWhenPlaybackEnds = false
        controller.videoGravity = session.videoGravity
        return controller
    }

    func updateUIViewController(_ controller: AVPlayerViewController, context: Context) {
        controller.player = session.avPlayer
        controller.videoGravity = session.videoGravity
    }
}

#if canImport(VLCKit)
struct VLCPlayerContainer: UIViewRepresentable {
    let session: PlayerSession

    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        view.backgroundColor = .black
        session.vlcPlayer.drawable = view
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        session.vlcPlayer.drawable = uiView
    }
}
#endif

struct HybridPlayerView: View {
    @ObservedObject var session: PlayerSession

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            if session.usingVLC {
                #if canImport(VLCKit)
                VLCPlayerContainer(session: session)
                    .ignoresSafeArea()
                #else
                AVPlayerContainer(session: session)
                    .ignoresSafeArea()
                #endif
            } else {
                AVPlayerContainer(session: session)
                    .ignoresSafeArea()
            }
        }
    }
}
