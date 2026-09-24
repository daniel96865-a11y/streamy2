import SwiftUI

struct PlaylistEditView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var playlists: PlaylistStore
    @EnvironmentObject private var state: AppState

    @State private var source: PlaylistSource
    @State private var username: String
    @State private var password: String

    init(source: PlaylistSource, credentials: (username: String, password: String)?) {
        _source = State(initialValue: source)
        _username = State(initialValue: credentials?.username ?? "")
        _password = State(initialValue: credentials?.password ?? "")
    }

    var body: some View {
        Form {
            Section("Wiedergabeliste") {
                TextField("Name", text: $source.name)

                if source.kind == .xtream {
                    TextField(
                        "Server",
                        text: Binding(
                            get: { source.baseURL ?? "" },
                            set: { source.baseURL = $0 }
                        )
                    )
                    .textInputAutocapitalization(.never)
                    .keyboardType(.URL)

                    TextField("Benutzername", text: $username)
                        .textInputAutocapitalization(.never)
                    SecureField("Passwort", text: $password)
                } else {
                    TextField(
                        "M3U-URL",
                        text: Binding(
                            get: { source.m3uURL ?? "" },
                            set: { source.m3uURL = $0 }
                        )
                    )
                    .textInputAutocapitalization(.never)
                    .keyboardType(.URL)
                }

                TextField(
                    "EPG/XMLTV-URL",
                    text: Binding(
                        get: { source.epgURL ?? "" },
                        set: { source.epgURL = $0.isEmpty ? nil : $0 }
                    )
                )
                .textInputAutocapitalization(.never)
                .keyboardType(.URL)
            }

            Section {
                Button("Speichern") {
                    normalize()
                    playlists.update(
                        source,
                        username: source.kind == .xtream ? username : nil,
                        password: source.kind == .xtream ? password : nil
                    )
                    Task { await state.reload() }
                    dismiss()
                }
                .disabled(!isValid)
            }
        }
        .navigationTitle("Wiedergabeliste")
        .navigationBarTitleDisplayMode(.inline)
    }

    private var isValid: Bool {
        guard !source.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return false }
        if source.kind == .xtream {
            return URL(string: source.baseURL ?? "") != nil && !username.isEmpty && !password.isEmpty
        }
        return URL(string: source.m3uURL ?? "") != nil
    }

    private func normalize() {
        source.name = source.name.trimmingCharacters(in: .whitespacesAndNewlines)
        if var value = source.baseURL?.trimmingCharacters(in: .whitespacesAndNewlines) {
            while value.hasSuffix("/") { value.removeLast() }
            source.baseURL = value
        }
        source.m3uURL = source.m3uURL?.trimmingCharacters(in: .whitespacesAndNewlines)
        source.epgURL = source.epgURL?.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
