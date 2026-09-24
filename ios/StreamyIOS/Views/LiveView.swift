import SwiftUI

struct LiveView: View {
    @EnvironmentObject private var state: AppState
    @EnvironmentObject private var epg: EPGStore
    @State private var categoryID: String?
    @State private var favoritesOnly = false
    @State private var search = ""
    @State private var epgChannel: Channel?

    private var filtered: [Channel] {
        state.channels.filter { channel in
            let matchesCategory = categoryID == nil || channel.categoryID == categoryID
            let matchesFavorite = !favoritesOnly || state.isFavorite(channel)
            let matchesSearch = search.isEmpty || channel.name.localizedCaseInsensitiveContains(search)
            return matchesCategory && matchesFavorite && matchesSearch
        }
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                if let last = state.lastChannel {
                    Button {
                        state.play(channel: last)
                    } label: {
                        HStack {
                            Image(systemName: "play.circle.fill")
                            Text("Weiter: \(last.name)")
                                .lineLimit(1)
                            Spacer()
                            if let now = epg.now(for: last) {
                                Text(now.title)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                    .lineLimit(1)
                            }
                        }
                        .padding(.horizontal)
                        .padding(.vertical, 9)
                    }
                    .buttonStyle(.plain)
                    .background(.thinMaterial)
                }

                categoryBar

                if state.isLoading && state.channels.isEmpty {
                    Spacer()
                    ProgressView("Sender werden geladen …")
                    Spacer()
                } else if filtered.isEmpty {
                    ContentUnavailableView(
                        favoritesOnly ? "Noch keine Favoriten" : "Keine Sender gefunden",
                        systemImage: favoritesOnly ? "star" : "tv",
                        description: Text(favoritesOnly ? "Halte einen Sender gedrückt und füge ihn zu den Favoriten hinzu." : "Passe Suche oder Kategorie an.")
                    )
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
                                    HStack(spacing: 6) {
                                        Text(channel.name)
                                            .font(.headline)
                                            .foregroundStyle(.primary)
                                        if state.isFavorite(channel) {
                                            Image(systemName: "star.fill")
                                                .font(.caption)
                                                .foregroundStyle(.yellow)
                                        }
                                    }
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
                        .contextMenu {
                            Button {
                                state.toggleFavorite(channel)
                            } label: {
                                Label(
                                    state.isFavorite(channel) ? "Aus Favoriten entfernen" : "Zu Favoriten",
                                    systemImage: state.isFavorite(channel) ? "star.slash" : "star"
                                )
                            }
                            if channel.hasCatchup {
                                Button {
                                    epgChannel = channel
                                } label: {
                                    Label("Programm / Catch-up", systemImage: "clock.arrow.circlepath")
                                }
                            }
                        }
                        .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                            Button {
                                state.toggleFavorite(channel)
                            } label: {
                                Label("Favorit", systemImage: state.isFavorite(channel) ? "star.slash" : "star")
                            }
                            .tint(.yellow)
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
                Button("Alle") {
                    categoryID = nil
                    favoritesOnly = false
                }
                .buttonStyle(.borderedProminent)
                .tint(categoryID == nil && !favoritesOnly ? .accentColor : .gray.opacity(0.35))

                Button {
                    categoryID = nil
                    favoritesOnly = true
                } label: {
                    Label("Favoriten", systemImage: "star.fill")
                }
                .buttonStyle(.bordered)
                .tint(favoritesOnly ? .yellow : .secondary)

                ForEach(state.liveCategories) { category in
                    Button(category.name) {
                        categoryID = category.id
                        favoritesOnly = false
                    }
                    .buttonStyle(.bordered)
                    .tint(categoryID == category.id && !favoritesOnly ? .accentColor : .secondary)
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
