import Foundation
import UIKit

actor ExtraLiveService {
    static let shared = ExtraLiveService()

    static let germanCategoryID = "extra_live"
    static let polishCategoryID = "extra_live_pl"

    private let catalogHosts: [(host: String, path: String, catalogID: String)] = [
        ("https://kool.to", "/mediahubmx-catalog.json", "iptv"),
        ("https://vavoo.to", "/mediahubmx-catalog.json", "iptv"),
        ("https://kool.to", "/vto-cluster/mediahubmx-catalog.json", "vto-iptv"),
        ("https://vavoo.to", "/vto-cluster/mediahubmx-catalog.json", "vto-iptv"),
        ("https://www.vavoo.to", "/mediahubmx-catalog.json", "iptv")
    ]

    private let resolveHosts = ["https://vavoo.to", "https://kool.to"]
    private let pingURL = URL(string: "https://www.vavoo.tv/api/app/ping")!

    private var signatureValue: String?
    private var signatureDate: Date?
    private var activeHost = "https://kool.to"
    private let clientID = "s2-ios-" + UUID().uuidString
    private let clientStartedAt = Int(Date().timeIntervalSince1970 * 1000)

    private init() {}

    func cachedChannels() -> [Channel] {
        guard let data = try? Data(contentsOf: cacheURL),
              let rows = try? JSONDecoder().decode([CachedChannel].self, from: data) else {
            return []
        }
        return rows.compactMap { row in
            guard let streamURL = URL(string: row.url) else { return nil }
            return Channel(
                id: row.id,
                name: row.name,
                categoryID: row.categoryID,
                logoURL: row.logo.flatMap(URL.init(string:)),
                streamURL: streamURL,
                epgID: nil,
                streamID: nil,
                hasCatchup: false,
                catchupDays: 0
            )
        }
    }

    func fetchFast() async -> [Channel] {
        var output: [Channel] = []
        if let german = try? await fetchCountry(
            country: "Germany",
            language: "de",
            region: "DE",
            categoryID: Self.germanCategoryID,
            maxPages: 1,
            preferredOnly: true
        ) {
            output.append(contentsOf: german)
        }

        if let polish = try? await fetchCountry(
            country: "Poland",
            language: "pl",
            region: "PL",
            categoryID: Self.polishCategoryID,
            maxPages: 1,
            preferredOnly: true
        ) {
            output.append(contentsOf: polish)
        }

        if !output.isEmpty {
            saveCache(output)
            return output
        }
        return cachedChannels()
    }

    func refreshAll() async -> [Channel] {
        async let german = fetchCountry(
            country: "Germany",
            language: "de",
            region: "DE",
            categoryID: Self.germanCategoryID,
            maxPages: 20,
            preferredOnly: false
        )
        async let polish = fetchCountry(
            country: "Poland",
            language: "pl",
            region: "PL",
            categoryID: Self.polishCategoryID,
            maxPages: 20,
            preferredOnly: false
        )

        var output: [Channel] = []
        if let list = try? await german { output.append(contentsOf: list) }
        if let list = try? await polish { output.append(contentsOf: list) }

        if !output.isEmpty {
            saveCache(output)
            return output
        }
        return cachedChannels()
    }

    func resolve(_ channel: Channel) async throws -> URL {
        let original = channel.streamURL
        let raw = original.absoluteString

        guard Self.needsResolve(raw) else { return original }

        let language = channel.categoryID == Self.polishCategoryID ? "pl" : "de"
        let region = channel.categoryID == Self.polishCategoryID ? "PL" : "DE"

        let orderedHosts: [String]
        if raw.contains("kool.to") {
            orderedHosts = ["https://kool.to", "https://vavoo.to"]
        } else {
            orderedHosts = resolveHosts
        }

        var lastError: Error = URLError(.cannotParseResponse)
        for host in orderedHosts {
            do {
                var sig = try await signature()
                var response = try await resolveResponse(
                    host: host,
                    url: raw,
                    language: language,
                    region: region,
                    signature: sig
                )

                if response.statusCode == 401 || response.statusCode == 403 {
                    signatureValue = nil
                    signatureDate = nil
                    sig = try await signature()
                    response = try await resolveResponse(
                        host: host,
                        url: raw,
                        language: language,
                        region: region,
                        signature: sig
                    )
                }

                guard (200...299).contains(response.statusCode) else {
                    lastError = URLError(.badServerResponse)
                    continue
                }

                if let resolved = Self.extractPlayableURL(from: response.data) {
                    activeHost = host
                    return resolved
                }
            } catch {
                lastError = error
            }
        }
        throw lastError
    }

    static func isExtra(_ channel: Channel) -> Bool {
        channel.categoryID == germanCategoryID || channel.categoryID == polishCategoryID
    }

    static func categories(for channels: [Channel]) -> [Category] {
        var result: [Category] = []
        if channels.contains(where: { $0.categoryID == germanCategoryID }) {
            result.append(Category(id: germanCategoryID, name: "Live Extra Deutschland"))
        }
        if channels.contains(where: { $0.categoryID == polishCategoryID }) {
            result.append(Category(id: polishCategoryID, name: "Live Extra Polen"))
        }
        return result
    }

    private func fetchCountry(
        country: String,
        language: String,
        region: String,
        categoryID: String,
        maxPages: Int,
        preferredOnly: Bool
    ) async throws -> [Channel] {
        var hosts = catalogHosts
        if preferredOnly {
            hosts = [
                (activeHost, "/mediahubmx-catalog.json", "iptv"),
                (activeHost == "https://kool.to" ? "https://vavoo.to" : "https://kool.to", "/mediahubmx-catalog.json", "iptv")
            ]
        }

        var lastError: Error = URLError(.cannotLoadFromNetwork)
        for target in hosts {
            do {
                let channels = try await fetchPages(
                    host: target.host,
                    path: target.path,
                    catalogID: target.catalogID,
                    country: country,
                    language: language,
                    region: region,
                    categoryID: categoryID,
                    maxPages: maxPages
                )
                if !channels.isEmpty {
                    activeHost = target.host
                    return channels
                }
            } catch {
                lastError = error
            }
        }
        throw lastError
    }

    private func fetchPages(
        host: String,
        path: String,
        catalogID: String,
        country: String,
        language: String,
        region: String,
        categoryID: String,
        maxPages: Int
    ) async throws -> [Channel] {
        var cursor = 0
        var page = 0
        var seen = Set<String>()
        var result: [Channel] = []

        while page < max(1, maxPages) {
            let payload: [String: Any] = [
                "language": language,
                "region": region,
                "catalogId": catalogID,
                "id": catalogID,
                "adult": false,
                "search": "",
                "sort": "name",
                "filter": ["group": country],
                "cursor": cursor,
                "clientVersion": "3.0.2"
            ]

            let data = try await postJSON(
                url: URL(string: host + path)!,
                json: payload,
                authenticated: true
            )

            guard let object = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let items = object["items"] as? [[String: Any]],
                  !items.isEmpty else {
                break
            }

            for item in items {
                guard let rawURL = item["url"] as? String,
                      !rawURL.isEmpty,
                      let streamURL = URL(string: rawURL),
                      let name = item["name"] as? String,
                      !name.isEmpty,
                      seen.insert(rawURL).inserted else { continue }

                let ids = item["ids"] as? [String: Any]
                let sourceID = (ids?["id"] as? String) ?? rawURL
                let logo = item["logo"] as? String

                result.append(Channel(
                    id: categoryID + ":" + sourceID,
                    name: Self.clean(name),
                    categoryID: categoryID,
                    logoURL: logo.flatMap(URL.init(string:)),
                    streamURL: streamURL,
                    epgID: nil,
                    streamID: nil,
                    hasCatchup: false,
                    catchupDays: 0
                ))
            }

            guard let next = object["nextCursor"] as? Int, next >= 0, next != cursor else {
                break
            }
            cursor = next
            page += 1
        }
        return result
    }

    private func signature() async throws -> String {
        if let value = signatureValue,
           let date = signatureDate,
           Date().timeIntervalSince(date) < 480 {
            return value
        }

        let deviceName = await MainActor.run { UIDevice.current.name }
        let systemVersion = await MainActor.run { UIDevice.current.systemVersion }
        let now = Int(Date().timeIntervalSince1970 * 1000)

        let payload: [String: Any] = [
            "token": "",
            "reason": "app-focus",
            "locale": "de",
            "theme": "dark",
            "metadata": [
                "device": [
                    "type": "Handset",
                    "brand": "Apple",
                    "model": deviceName,
                    "name": "streamy",
                    "uniqueId": clientID
                ],
                "os": [
                    "name": "ios",
                    "version": systemVersion,
                    "abis": ["arm64"],
                    "host": "ios"
                ],
                "app": [
                    "platform": "ios",
                    "version": "3.1.21",
                    "buildId": "289515000",
                    "engine": "hbc85",
                    "installer": "appstore",
                    "signatures": []
                ],
                "version": [
                    "package": "tv.vavoo.app",
                    "binary": "3.1.21",
                    "js": "3.1.21"
                ]
            ],
            "appFocusTime": 0,
            "playerActive": false,
            "playDuration": 0,
            "devMode": false,
            "hasAddon": true,
            "castConnected": false,
            "package": "tv.vavoo.app",
            "version": "3.1.21",
            "process": "app",
            "firstAppStart": clientStartedAt,
            "lastAppStart": now,
            "ipLocation": NSNull(),
            "adblockEnabled": true,
            "proxy": [
                "supported": ["ss"],
                "engine": "Mu",
                "enabled": false,
                "autoServer": true
            ],
            "iap": ["supported": false]
        ]

        let data = try await postJSON(url: pingURL, json: payload, authenticated: false)
        guard let object = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let sig = (object["addonSig"] as? String) ?? (object["signed"] as? String),
              !sig.isEmpty else {
            throw URLError(.userAuthenticationRequired)
        }

        signatureValue = sig
        signatureDate = Date()
        return sig
    }

    private func resolveResponse(
        host: String,
        url: String,
        language: String,
        region: String,
        signature: String
    ) async throws -> (data: Data, statusCode: Int) {
        let payload: [String: Any] = [
            "language": language,
            "region": region,
            "url": url,
            "clientVersion": "3.0.2"
        ]

        let body = try JSONSerialization.data(withJSONObject: payload)
        var request = URLRequest(url: URL(string: host + "/mediahubmx-resolve.json")!)
        request.httpMethod = "POST"
        request.timeoutInterval = 14
        request.httpBody = body
        request.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
        request.setValue("*/*", forHTTPHeaderField: "Accept")
        request.setValue("MediaHubMX/2", forHTTPHeaderField: "User-Agent")
        request.setValue(signature, forHTTPHeaderField: "mediahubmx-signature")

        let (data, response) = try await URLSession.shared.data(for: request)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        return (data, status)
    }

    private func postJSON(url: URL, json: [String: Any], authenticated: Bool) async throws -> Data {
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.timeoutInterval = 12
        request.httpBody = try JSONSerialization.data(withJSONObject: json)
        request.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")

        if authenticated {
            request.setValue("MediaHubMX/2", forHTTPHeaderField: "User-Agent")
            request.setValue("https://vavoo.to", forHTTPHeaderField: "Origin")
            request.setValue("https://vavoo.to/", forHTTPHeaderField: "Referer")
            let sig = try await signature()
            request.setValue(sig, forHTTPHeaderField: "mediahubmx-signature")
        } else {
            request.setValue("okhttp/4.11.0", forHTTPHeaderField: "User-Agent")
        }

        let (data, response) = try await URLSession.shared.data(for: request)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard (200...299).contains(status) else { throw URLError(.badServerResponse) }
        return data
    }

    private func saveCache(_ channels: [Channel]) {
        let rows = channels.map {
            CachedChannel(
                id: $0.id,
                name: $0.name,
                url: $0.streamURL.absoluteString,
                categoryID: $0.categoryID ?? Self.germanCategoryID,
                logo: $0.logoURL?.absoluteString
            )
        }
        guard let data = try? JSONEncoder().encode(rows) else { return }
        try? FileManager.default.createDirectory(
            at: cacheURL.deletingLastPathComponent(),
            withIntermediateDirectories: true
        )
        try? data.write(to: cacheURL, options: .atomic)
    }

    private var cacheURL: URL {
        let base = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first!
        return base
            .appendingPathComponent("Streamy", isDirectory: true)
            .appendingPathComponent("extra-live.json")
    }

    private static func needsResolve(_ raw: String) -> Bool {
        raw.contains("vavoo-iptv") || raw.contains("kool-iptv") || raw.contains("vavoo.to") || raw.contains("kool.to")
    }

    private static func extractPlayableURL(from data: Data) -> URL? {
        if let text = String(data: data, encoding: .utf8) {
            let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
            if trimmed.hasPrefix("http"), let token = trimmed.split(whereSeparator: { $0.isWhitespace }).first {
                return URL(string: String(token))
            }
        }

        guard let root = try? JSONSerialization.jsonObject(with: data) else { return nil }

        func from(_ value: Any) -> URL? {
            if let string = value as? String, string.hasPrefix("http") {
                return URL(string: string)
            }
            if let object = value as? [String: Any] {
                for key in ["url", "stream", "src"] {
                    if let result = object[key].flatMap(from) { return result }
                }
                for key in ["streams", "urls"] {
                    if let array = object[key] as? [Any] {
                        for item in array {
                            if let result = from(item) { return result }
                        }
                    }
                }
            }
            if let array = value as? [Any] {
                for item in array {
                    if let result = from(item) { return result }
                }
            }
            return nil
        }

        return from(root)
    }

    private static func clean(_ value: String) -> String {
        value
            .replacingOccurrences(of: "&amp;", with: "&")
            .replacingOccurrences(of: "&quot;", with: "\"")
            .replacingOccurrences(of: "&#39;", with: "'")
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }
}

private struct CachedChannel: Codable {
    let id: String
    let name: String
    let url: String
    let categoryID: String
    let logo: String?
}
