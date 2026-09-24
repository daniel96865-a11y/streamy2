import SwiftUI

struct PlayerScreen: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var prefs: PrefsStore
    @StateObject private var session: PlayerSession
    @State private var controlsVisible = true
    @State private var showingAudio = false

    init(item: PlaybackItem, prefs: PrefsStore) {
        _prefs = ObservedObject(wrappedValue: prefs)
        _session = StateObject(wrappedValue: PlayerSession(item: item, prefs: prefs))
    }

    var body: some View {
        ZStack {
            HybridPlayerView(session: session)
                .contentShape(Rectangle())
                .onTapGesture {
                    withAnimation { controlsVisible.toggle() }
                }

            if controlsVisible {
                VStack {
                    HStack(spacing: 12) {
                        Button {
                            dismiss()
                        } label: {
                            Image(systemName: "xmark")
                                .font(.headline)
                                .frame(width: 42, height: 42)
                                .background(.black.opacity(0.65), in: Circle())
                        }

                        VStack(alignment: .leading) {
                            Text(session.item.title).font(.headline)
                            if let subtitle = session.item.subtitle {
                                Text(subtitle).font(.caption).lineLimit(1)
                            }
                        }
                        .foregroundStyle(.white)

                        Spacer()

                        Menu {
                            Button("Automatisch / Apple Player") { session.forceAVPlayer() }
                            Button("VLC") { session.forceVLC() }
                            if !session.audioOptions.isEmpty {
                                Divider()
                                ForEach(Array(session.audioOptions.enumerated()), id: \.offset) { index, label in
                                    Button {
                                        session.selectAudio(index: index)
                                    } label: {
                                        if index == session.selectedAudioIndex {
                                            Label(label, systemImage: "checkmark")
                                        } else {
                                            Text(label)
                                        }
                                    }
                                }
                            }
                        } label: {
                            Image(systemName: "slider.horizontal.3")
                                .font(.headline)
                                .frame(width: 42, height: 42)
                                .background(.black.opacity(0.65), in: Circle())
                        }
                    }
                    .padding()

                    Spacer()

                    HStack(spacing: 28) {
                        Button {
                            session.toggle()
                        } label: {
                            Image(systemName: session.isPlaying ? "pause.fill" : "play.fill")
                                .font(.title2)
                                .frame(width: 58, height: 58)
                                .background(.black.opacity(0.7), in: Circle())
                        }
                        Text(session.usingVLC ? "VLC" : "Apple Player")
                            .font(.caption.bold())
                            .padding(.horizontal, 12)
                            .padding(.vertical, 8)
                            .background(.black.opacity(0.65), in: Capsule())
                    }
                    .foregroundStyle(.white)
                    .padding(.bottom, 30)
                }
                .transition(.opacity)
            }

            if let error = session.errorMessage {
                VStack {
                    Spacer()
                    Text(error)
                        .font(.caption)
                        .foregroundStyle(.white)
                        .padding(10)
                        .background(.red.opacity(0.8), in: Capsule())
                        .padding(.bottom, 100)
                }
            }
        }
        .background(Color.black)
        .statusBarHidden(true)
        .onAppear { session.play() }
        .onDisappear { session.stop() }
    }
}
