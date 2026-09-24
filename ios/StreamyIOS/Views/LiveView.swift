import SwiftUI

struct LiveView: View {
    @EnvironmentObject private var state: AppState
    @EnvironmentObject private var epg: EPGStore
    @State private var categoryID: String?
    @State private var search = ""
    @State private var epgChannel: Channel?

    private var filtered: [Channel] {
        state.channels.filter { channel in
            let matchesCategory = categoryID == nil || channel.categoryID == categoryID
            let matchesSearch = search.isEmpty || channel.name.localizedCaseInsensitiveContains(search)
            return matchesCategory && matchesSearch
        }
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                categoryBar

                if state.isLoading && state.channels.isEmpty {
                    Spacer()
                    ProgressView("Sender werden geladen …")
                    Spacer()
                } else {
                    List(filtered) { channel in
                        Button {
                            state.play(channel: channel)
                        } label: {
                            HStack(spacing: 12) {
                                AsyncImage(url: channel.logoURL) { image in
                                    image.resizable().scaledToFit()
                                } placeholder: {
                                    RoundedRectangle(cornerRadius: 8)
                                        .fill(.secondary.opacity(0.18))
                                        .overlay(Image(systemName: "tv"))
                                }
                                .frame(width: 54, height: 42)

                                VStack(alignment: .leading, spacing: 4) {
                                    Text(channel.name)
                                        .font(.headline)
                                        .foregroundStyle(.primary)
                                    if let now = epg.now(for: channel) {
                                        Text(now.title)
                                            .font(.subheadline)
                                            .foregroundStyle(.secondary)
                                            .lineLimit(1)
                                    } else {
                                        Text("Keine EPG-Daten")
                                            .font(.caption)
                                            .foregroundStyle(.secondary)
                                    }
                                }
                                Spacer()
                                if channel.hasCatchup {
                                    Button {
                                        epgChannel = channel
                                    } label: {
                                        Image(systemName: "clock.arrow.circlepath")
                                    }
                                    .buttonStyle(.borderless)
                                }
                                Image(systemName: "play.fill")
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                    .listStyle(.plain)
                    .refreshable { await state.reload() }
                }
            }
            .navigationTitle("Live TV")
            .searchable(text: $search, prompt: "Sender suchen")
            .sheet(item: $epgChannel) { channel in
                EPGChannelView(channel: channel)
            }
            .overlay(alignment: .bottom) {
                if let error = state.errorMessage {
                    Text(error)
                        .font(.caption)
                        .padding(10)
                        .background(.ultraThinMaterial, in: Capsule())
                        .padding()
                }
            }
        }
    }

    private var categoryBar: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack {
                Button("Alle") { categoryID = nil }
                    .buttonStyle(.borderedProminent)
                    .tint(categoryID == nil ? .accentColor : .gray.opacity(0.35))
                ForEach(state.liveCategories) { category in
                    Button(category.name) { categoryID = category.id }
                        .buttonStyle(.bordered)
                        .tint(categoryID == category.id ? .accentColor : .secondary)
                }
            }
            .padding(.horizontal)
            .padding(.vertical, 8)
        }
    }
}

private struct EPGChannelView: View {
    @EnvironmentObject private var state: AppState
    @EnvironmentObject private var epg: EPGStore
    @Environment(\.dismiss) private var dismiss
    let channel: Channel

    var body: some View {
        NavigationStack {
            List(epg.programs(for: channel)) { program in
                Button {
                    state.catchup(channel: channel, program: program)
                    dismiss()
                } label: {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(program.title).font(.headline)
                        Text(program.start.formatted(date: .omitted, time: .shortened) + " – " + program.stop.formatted(date: .omitted, time: .shortened))
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        if let description = program.description {
                            Text(description).font(.caption).lineLimit(2)
                        }
                    }
                }
                .disabled(!channel.hasCatchup || program.start > Date())
            }
            .navigationTitle(channel.name)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Schließen") { dismiss() }
                }
            }
        }
    }
}
