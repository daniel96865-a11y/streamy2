import SwiftUI

struct SeriesView: View {
    @EnvironmentObject private var state: AppState
    @State private var categoryID: String?
    @State private var search = ""

    private var filtered: [Series] {
        state.series.filter {
            (categoryID == nil || $0.categoryID == categoryID) &&
            (search.isEmpty || $0.name.localizedCaseInsensitiveContains(search))
        }
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack {
                        Button("Alle") { categoryID = nil }
                        ForEach(state.seriesCategories) { category in
                            Button(category.name) { categoryID = category.id }
                        }
                    }
                    .buttonStyle(.bordered)
                    .padding(.horizontal)
                    .padding(.vertical, 8)
                }

                List(filtered) { series in
                    NavigationLink {
                        SeriesDetailView(series: series)
                    } label: {
                        HStack(spacing: 12) {
                            AsyncImage(url: series.posterURL) { image in
                                image.resizable().scaledToFill()
                            } placeholder: {
                                Rectangle().fill(.secondary.opacity(0.15))
                            }
                            .frame(width: 70, height: 100)
                            .clipShape(RoundedRectangle(cornerRadius: 9))

                            VStack(alignment: .leading, spacing: 5) {
                                Text(series.name).font(.headline)
                                if let plot = series.plot {
                                    Text(plot)
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                        .lineLimit(3)
                                }
                            }
                        }
                    }
                }
                .listStyle(.plain)
            }
            .navigationTitle("Serien")
            .searchable(text: $search, prompt: "Serie suchen")
            .refreshable { await state.reload() }
        }
    }
}

private struct SeriesDetailView: View {
    @EnvironmentObject private var state: AppState
    let series: Series

    @State private var episodes: [Episode] = []
    @State private var loading = true
    @State private var error: String?

    var body: some View {
        List {
            if let plot = series.plot {
                Section {
                    Text(plot)
                }
            }

            if loading {
                ProgressView("Episoden werden geladen …")
            } else if let error {
                Text(error).foregroundStyle(.red)
            } else {
                ForEach(episodes) { episode in
                    Button {
                        state.play(episode: episode, series: series)
                    } label: {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("S(episode.season) E(episode.episode) · (episode.title)")
                                .font(.headline)
                            if let plot = episode.plot {
                                Text(plot).font(.caption).foregroundStyle(.secondary).lineLimit(2)
                            }
                        }
                    }
                }
            }
        }
        .navigationTitle(series.name)
        .task {
            do {
                episodes = try await state.episodes(for: series)
            } catch {
                self.error = error.localizedDescription
            }
            loading = false
        }
    }
}
