import SwiftUI

struct MoviesView: View {
    @EnvironmentObject private var state: AppState
    @State private var categoryID: String?
    @State private var search = ""

    private var filtered: [Movie] {
        state.movies.filter {
            (categoryID == nil || $0.categoryID == categoryID) &&
            (search.isEmpty || $0.name.localizedCaseInsensitiveContains(search))
        }
    }

    private let columns = [GridItem(.adaptive(minimum: 135), spacing: 14)]

    var body: some View {
        NavigationStack {
            ScrollView {
                categoryBar
                LazyVGrid(columns: columns, spacing: 18) {
                    ForEach(filtered) { movie in
                        Button {
                            state.play(movie: movie)
                        } label: {
                            VStack(alignment: .leading, spacing: 7) {
                                AsyncImage(url: movie.posterURL) { image in
                                    image.resizable().scaledToFill()
                                } placeholder: {
                                    Rectangle().fill(.secondary.opacity(0.16))
                                        .overlay(Image(systemName: "film"))
                                }
                                .frame(height: 195)
                                .clipShape(RoundedRectangle(cornerRadius: 12))

                                Text(movie.name)
                                    .font(.headline)
                                    .foregroundStyle(.primary)
                                    .lineLimit(2)
                                if let year = movie.year {
                                    Text(year).font(.caption).foregroundStyle(.secondary)
                                }
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding()
            }
            .navigationTitle("Filme")
            .searchable(text: $search, prompt: "Film suchen")
            .refreshable { await state.reload() }
        }
    }

    private var categoryBar: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack {
                Button("Alle") { categoryID = nil }
                ForEach(state.vodCategories) { category in
                    Button(category.name) { categoryID = category.id }
                }
            }
            .buttonStyle(.bordered)
            .padding(.horizontal)
        }
    }
}
