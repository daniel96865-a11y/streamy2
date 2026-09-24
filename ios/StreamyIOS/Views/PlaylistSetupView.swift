import SwiftUI

struct PlaylistSetupView: View {
    @EnvironmentObject private var playlists: PlaylistStore
    @Environment(\.dismiss) private var dismiss

    @State private var kind: PlaylistKind = .xtream
    @State private var name = ""
    @State private var baseURL = ""
    @State private var username = ""
    @State private var password = ""
    @State private var m3uURL = ""
    @State private var epgURL = ""
    @State private var error = ""

    var onDone: () -> Void = {}

    var body: some View {
        NavigationStack {
            Form {
                Section("Wiedergabeliste") {
                    Picker("Typ", selection: $kind) {
                        Text("Xtream Codes").tag(PlaylistKind.xtream)
                        Text("M3U").tag(PlaylistKind.m3u)
                    }
                    .pickerStyle(.segmented)

                    TextField("Name", text: $name)

                    if kind == .xtream {
                        TextField("Server, z. B. http://server:8080", text: $baseURL)
                            .textInputAutocapitalization(.never)
                            .keyboardType(.URL)
                        TextField("Benutzername", text: $username)
                            .textInputAutocapitalization(.never)
                        SecureField("Passwort", text: $password)
                    } else {
                        TextField("M3U-URL", text: $m3uURL)
                            .textInputAutocapitalization(.never)
                            .keyboardType(.URL)
                    }

                    TextField("EPG/XMLTV-URL (optional)", text: $epgURL)
                        .textInputAutocapitalization(.never)
                        .keyboardType(.URL)
                }

                if !error.isEmpty {
                    Section {
                        Text(error).foregroundStyle(.red)
                    }
                }

                Section {
                    Button("Wiedergabeliste hinzufügen") {
                        add()
                    }
                    .frame(maxWidth: .infinity)
                    .disabled(!isValid)
                }
            }
            .navigationTitle("Streamy einrichten")
            .navigationBarTitleDisplayMode(.inline)
        }
    }

    private var isValid: Bool {
        if name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return false }
        switch kind {
        case .xtream:
            return URL(string: baseURL) != nil && !username.isEmpty && !password.isEmpty
        case .m3u:
            return URL(string: m3uURL) != nil
        }
    }

    private func add() {
        let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        switch kind {
        case .xtream:
            playlists.addXtream(
                name: trimmedName,
                baseURL: baseURL,
                username: username,
                password: password,
                epgURL: epgURL.isEmpty ? nil : epgURL
            )
        case .m3u:
            playlists.addM3U(
                name: trimmedName,
                m3uURL: m3uURL,
                epgURL: epgURL.isEmpty ? nil : epgURL
            )
        }
        onDone()
        dismiss()
    }
}
