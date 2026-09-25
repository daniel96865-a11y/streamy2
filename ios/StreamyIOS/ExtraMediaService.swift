import Foundation

actor ExtraMediaService {
    static let shared = ExtraMediaService()
    static let filmCategoryID = "extra_media_films"
    static let seriesCategoryID = "extra_media_series"

    private let seeds = [
        "https://megakino19.com",
        "https://megakino18.com",
        "https://megakino15.com",
        "https://megakino14.com",
        "https://megakino12.com",
        "https://megakino5.org",
        "https://megakino4.com",
        "https://megakino2.com",
        "https://megakino1.com"
    ]

    private let session: URLSession
    private var base: URL?
    private var tokenAt: Date?
    private var seriesPagesByKey: [String: [URL]] = [:]

    private init() {
        let config = URLSessionConfiguration.default
        config.httpCookieAcceptPolicy = .always
        config.httpCookieStorage = HTTPCookieStorage.shared
        config.timeoutIntervalForRequest = 18
        config.timeoutIntervalForResource = 30
        session = URLSession(configuration: config)
    }

    func cachedCatalog() -> (movies: [Movie], series: [Series]) {
        guard let data = try? Data(contentsOf: cacheURL),
              let cache = try? JSONDecoder().decode(MediaCache.self, from: data) else {
            return ([], [])
        }
        seriesPagesByKey = cache.seriesPages.reduce(into: [:]) { partial, entry in
            partial[entry.key] = entry.urls.compactMap(URL.init(string:))
        }
        return (
            cache.movies.compactMap { $0.movie },
            cache.series.compactMap { $0.series }
        )
    }

    func loadFast() async -> (movies: [Movie], series: [Series]) {
        if let fresh = try? await loadCatalog(filmPages: 2, seriesPages: 2), !fresh.movies.isEmpty || !fresh.series.isEmpty {
            saveCache(movies: fresh.movies, series: fresh.series)
            return fresh
        }
        return cachedCatalog()
    }

    func refreshAll() async -> (movies: [Movie], series: [Series]) {
        if let fresh = try? await loadCatalog(filmPages: 5, seriesPages: 8), !fresh.movies.isEmpty || !fresh.series.isEmpty {
            saveCache(movies: fresh.movies, series: fresh.series)
            return fresh
        }
        return cachedCatalog()
    }

    func resolveMovie(_ movie: Movie) async throws -> URL {
        if isDirectStream(movie.streamURL) { return movie.streamURL }
        let html = try await get(movie.streamURL)
        let embeds = collectEmbeds(in: html, relativeTo: movie.streamURL)
        for embed in ranked(embeds) {
            if let stream = try? await resolveEmbed(embed) {
                return stream
            }
        }
        throw URLError(.cannotParseResponse)
    }

    func episodes(for series: Series) async throws -> [Episode] {
        let key = showKey(series.name)
        var pages = seriesPagesByKey[key] ?? []
        if pages.isEmpty, let page = pageURL(fromSeriesID: series.id) {
            pages = [page]
        }

        var output: [Episode] = []
        for page in pages {
            let html = try await get(page)
            let season = seasonNumber(from: title(in: html).isEmpty ? series.name : title(in: html))
            let options = selectOptions(in: html)
            var index = 0
            for option in options {
                index += 1
                let number = episodeNumber(option.value) ?? episodeNumber(option.title) ?? index
                let candidates = httpOptions(in: html, selectID: option.value)
                let first = candidates.first ?? absoluteURL(option.value, base: page)
                guard let url = first else { continue }
                let cleanTitle = isGenericEpisodeTitle(option.title) ? "Folge \(number)" : clean(option.title)
                output.append(Episode(
                    id: "mediaextra-episode:\(page.absoluteString)|\(option.value)",
                    season: max(1, season),
                    episode: number,
                    title: cleanTitle,
                    streamURL: url,
                    plot: nil
                ))
            }
        }

        return output.sorted {
            $0.season == $1.season ? $0.episode < $1.episode : $0.season < $1.season
        }
    }

    func resolveEpisode(_ episode: Episode) async throws -> URL {
        if isDirectStream(episode.streamURL) { return episode.streamURL }
        if let stream = try? await resolveEmbed(episode.streamURL) {
            return stream
        }

        if episode.id.hasPrefix("mediaextra-episode:") {
            let raw = String(episode.id.dropFirst("mediaextra-episode:".count))
            if let separator = raw.firstIndex(of: "|"),
               let page = URL(string: String(raw[..<separator])) {
                let selectID = String(raw[raw.index(after: separator)...])
                let html = try await get(page)
                for candidate in ranked(httpOptions(in: html, selectID: selectID)) {
                    if let stream = try? await resolveEmbed(candidate) {
                        return stream
                    }
                }
            }
        }
        throw URLError(.cannotParseResponse)
    }

    static func isExtra(_ movie: Movie) -> Bool {
        movie.id.hasPrefix("mediaextra:")
    }

    static func isExtra(_ series: Series) -> Bool {
        series.id.hasPrefix("mediaextra-series:")
    }

    static func isExtra(_ episode: Episode) -> Bool {
        episode.id.hasPrefix("mediaextra-episode:")
    }

    private func loadCatalog(filmPages: Int, seriesPages: Int) async throws -> (movies: [Movie], series: [Series]) {
        let host = try await resolveBase()
        try await ensureToken(on: host)

        var movieCards: [MediaCard] = []
        movieCards.append(contentsOf: parseCards(try await get(host.appendingPathComponent("films/")), forceSeries: false))
        if filmPages > 1 {
            for page in 2...filmPages {
                if let html = try? await get(host.appendingPathComponent("films/page/\(page)/")) {
                    movieCards.append(contentsOf: parseCards(html, forceSeries: false))
                }
            }
        }
        if let html = try? await get(host.appendingPathComponent("kinofilme/")) {
            movieCards.insert(contentsOf: parseCards(html, forceSeries: false), at: 0)
        }
        if let html = try? await get(host) {
            movieCards.insert(contentsOf: parseCards(html, forceSeries: false).filter { !$0.isSeries }, at: 0)
        }

        var serialCards: [MediaCard] = []
        serialCards.append(contentsOf: parseCards(try await get(host.appendingPathComponent("serials/")), forceSeries: true))
        if seriesPages > 1 {
            for page in 2...seriesPages {
                if let html = try? await get(host.appendingPathComponent("serials/page/\(page)/")) {
                    serialCards.append(contentsOf: parseCards(html, forceSeries: true))
                }
            }
        }

        movieCards = dedupe(movieCards)
        serialCards = dedupe(serialCards)

        let movies = movieCards.compactMap { card -> Movie? in
            guard let page = card.page else { return nil }
            return Movie(
                id: "mediaextra:" + page.absoluteString,
                name: card.name,
                categoryID: Self.filmCategoryID,
                posterURL: card.poster,
                streamURL: page,
                plot: card.plot,
                year: card.year,
                rating: nil
            )
        }

        let groups = Dictionary(grouping: serialCards, by: { showKey($0.name) })
        var groupedSeries: [Series] = []
        var pageMap: [String: [URL]] = [:]

        for (key, values) in groups {
            let sorted = values.sorted { seasonNumber(from: $0.name) < seasonNumber(from: $1.name) }
            guard let latest = sorted.last, let latestPage = latest.page else { continue }
            let cleanName = showTitle(latest.name)
            pageMap[key] = sorted.compactMap(\.page)
            groupedSeries.append(Series(
                id: "mediaextra-series:" + latestPage.absoluteString,
                name: cleanName,
                categoryID: Self.seriesCategoryID,
                posterURL: latest.poster,
                plot: latest.plot,
                rating: nil
            ))
        }

        groupedSeries.sort {
            $0.name.localizedCompare($1.name) == .orderedAscending
        }
        seriesPagesByKey = pageMap
        return (movies, groupedSeries)
    }

    private func resolveBase() async throws -> URL {
        if let base { return base }

        var fallback = URL(string: seeds[0])!
        for raw in seeds {
            guard let candidate = URL(string: raw) else { continue }
            fallback = candidate
            do {
                _ = try? await get(candidate.appendingPathComponent("index.php"), query: [URLQueryItem(name: "yg", value: "token")])
                let html = try await get(candidate)
                if html.localizedCaseInsensitiveContains("poster grid-item") {
                    base = candidate
                    return candidate
                }
            } catch {
                continue
            }
        }
        base = fallback
        throw URLError(.cannotFindHost)
    }

    private func ensureToken(on host: URL) async throws {
        if let tokenAt, Date().timeIntervalSince(tokenAt) < 480 { return }
        _ = try await get(host.appendingPathComponent("index.php"), query: [URLQueryItem(name: "yg", value: "token")])
        tokenAt = Date()
    }

    private func get(_ url: URL, query: [URLQueryItem] = []) async throws -> String {
        var target = url
        if !query.isEmpty {
            var components = URLComponents(url: url, resolvingAgainstBaseURL: false)
            components?.queryItems = query
            if let resolved = components?.url { target = resolved }
        }

        var request = URLRequest(url: target)
        request.timeoutInterval = 18
        request.setValue("Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 Version/18.0 Mobile/15E148 Safari/604.1", forHTTPHeaderField: "User-Agent")
        request.setValue("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8", forHTTPHeaderField: "Accept")
        request.setValue("de-DE,de;q=0.9,en;q=0.8", forHTTPHeaderField: "Accept-Language")
        if let base {
            request.setValue(base.absoluteString + "/", forHTTPHeaderField: "Referer")
        }

        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse, (200...399).contains(http.statusCode) else {
            throw URLError(.badServerResponse)
        }
        if let text = String(data: data, encoding: .utf8) { return text }
        if let text = String(data: data, encoding: .isoLatin1) { return text }
        throw URLError(.cannotDecodeContentData)
    }

    private func parseCards(_ html: String, forceSeries: Bool) -> [MediaCard] {
        let anchorPattern = #"(?is)<a\b[^>]*href="([^"]+)"[^>]*>[\s\S]*?poster\s+grid-item[\s\S]*?</a>"#
        let matches = regexMatches(anchorPattern, in: html)
        var result: [MediaCard] = []

        for match in matches {
            guard match.count >= 2 else { continue }
            let block = match[0]
            let href = match[1]
            if href.contains("/cast/") || href.contains("/genre/") { continue }
            let name = first(#"(?is)poster__title[^>]*>\s*([^<]+)"#, in: block).map(clean) ?? ""
            guard !name.isEmpty else { continue }

            let posterRaw = first(#"(?is)data-src="([^"]+)""#, in: block)
            let subtitle = first(#"(?is)poster__text[^>]*>\s*([^<]+)"#, in: block).map(clean)
            let year = first(#"(19|20)\d{2}"#, in: block, wholeMatch: true)
            let page = absoluteURL(href, base: base)
            let poster = posterRaw.flatMap { absoluteURL($0, base: base) }
            let isSeries = forceSeries || href.contains("/serials/")

            result.append(MediaCard(
                name: name,
                page: page,
                poster: poster,
                plot: subtitle,
                year: year,
                isSeries: isSeries
            ))
        }
        return result
    }

    private func collectEmbeds(in html: String, relativeTo page: URL) -> [URL] {
        var output: [URL] = []
        for pattern in [
            #"(?is)<iframe[^>]+(?:data-src|src)="([^"]+)""#,
            #"(?is)<option[^>]+value="([^"]+)""#
        ] {
            for match in regexMatches(pattern, in: html) where match.count > 1 {
                if let url = absoluteURL(match[1], base: page), !isYouTube(url) {
                    if !output.contains(url) { output.append(url) }
                }
            }
        }
        return output
    }

    private func selectOptions(in html: String) -> [(value: String, title: String)] {
        guard let body = first(#"(?is)<select[^>]*class="[^"]*se-select[^"]*"[^>]*>([\s\S]*?)</select>"#, in: html) else {
            return []
        }
        return regexMatches(#"(?is)<option[^>]+value="([^"]+)"[^>]*>([^<]*)"#, in: body)
            .compactMap { match in
                guard match.count >= 3 else { return nil }
                let value = match[1]
                guard !value.isEmpty, !value.hasPrefix("#"), !value.hasPrefix("http") else { return nil }
                return (value, clean(match[2]))
            }
    }

    private func httpOptions(in html: String, selectID: String) -> [URL] {
        let escaped = NSRegularExpression.escapedPattern(for: selectID)
        guard let body = first(#"(?is)<select[^>]*id=""# + escaped + #""[^>]*>([\s\S]*?)</select>"#, in: html) else {
            return []
        }
        return regexMatches(#"(?is)<option[^>]+value="([^"]+)""#, in: body)
            .compactMap { $0.count > 1 ? URL(string: $0[1]) : nil }
    }

    private func resolveEmbed(_ url: URL) async throws -> URL {
        if isDirectStream(url) { return url }
        let raw = url.absoluteString.lowercased()
        if raw.contains("youtube") || raw.contains("youtu.be") {
            throw URLError(.unsupportedURL)
        }
        if raw.contains("gxplayer") || raw.contains("watch.gx") || raw.contains("/watch?v=") {
            if let gx = try await resolveGX(url) { return gx }
        }
        if isVoe(raw), let voe = try await resolveVoe(url) {
            return voe
        }
        if let gx = try await resolveGX(url) { return gx }
        throw URLError(.cannotParseResponse)
    }

    private func resolveGX(_ url: URL) async throws -> URL? {
        let html = try await get(url)
        var uid = first(#""uid"\s*:\s*"([^"]+)""#, in: html) ?? ""
        var md5 = first(#""md5"\s*:\s*"([^"]+)""#, in: html) ?? ""
        var id = first(#""id"\s*:\s*"([^"]+)""#, in: html) ?? ""
        var status = first(#""status"\s*:\s*"([^"]+)""#, in: html) ?? ""

        if uid.isEmpty || md5.isEmpty,
           let jsonText = first(#"(?is)var\s+video\s*=\s*(\{.*?\})\s*;"#, in: html),
           let data = jsonText.data(using: .utf8),
           let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
            uid = object["uid"] as? String ?? uid
            md5 = object["md5"] as? String ?? md5
            id = object["id"] as? String ?? id
            status = object["status"] as? String ?? status
        }

        guard !uid.isEmpty, !md5.isEmpty,
              let scheme = url.scheme, let host = url.host else { return nil }
        let origin = scheme + "://" + host
        if !id.isEmpty {
            return URL(string: origin + "/m3u8/\(uid)/\(md5)/master.txt?s=1&id=\(id)&cache=\(status)")
        }
        return URL(string: origin + "/alternative_stream/\(uid)/\(md5)/master.m3u8")
    }

    private func resolveVoe(_ original: URL) async throws -> URL? {
        let aliases = [
            "https://johnfullwonder.com",
            "https://johnbeyondnation.com",
            "https://tracylocalschool.com",
            "https://eugenemakedraw.com",
            "https://jilliandescribecompany.com",
            "https://voe.sx"
        ]

        var candidates: [URL] = []
        if let components = URLComponents(url: original, resolvingAgainstBaseURL: false) {
            let path = components.path + (components.query.map { "?" + $0 } ?? "")
            for alias in aliases {
                if let url = URL(string: alias + path) { candidates.append(url) }
            }
        }
        candidates.append(original)

        for candidate in candidates {
            guard let html = try? await followVoe(candidate, depth: 0),
                  !html.isEmpty,
                  !html.contains("DDoS-Guard") else { continue }

            if let raw = first(#"(?i)"(?:hls|source|mp4|file)"\s*:\s*"(https?:[^"\\]+)""#, in: html)?
                .replacingOccurrences(of: "\\/", with: "/"),
               let url = URL(string: raw) {
                return url
            }

            if let raw = first(#"(?i)https?:[^"'\s<>]+\.m3u8[^"'\s<>]*"#, in: html, wholeMatch: true)?
                .replacingOccurrences(of: "\\/", with: "/"),
               let url = URL(string: raw) {
                return url
            }

            if let packed = first(#"(?is)<script\s+type="application/json">(.*?)</script>"#, in: html),
               let object = decryptVoe(packed) {
                for key in ["source", "hls", "file"] {
                    if let raw = object[key] as? String,
                       let url = URL(string: raw.replacingOccurrences(of: "\\/", with: "/")) {
                        return url
                    }
                }
            }
        }
        return nil
    }

    private func followVoe(_ url: URL, depth: Int) async throws -> String {
        guard depth <= 6 else { return "" }
        let html = try await get(url)
        if html.contains("application/json") && html.count > 2000 { return html }
        if let redirect = first(#"(?is)(?:window\.)?location(?:\.href)?\s*=\s*['"](https?://[^'"]+)['"]"#, in: html),
           let next = URL(string: redirect),
           next != url {
            return try await followVoe(next, depth: depth + 1)
        }
        return html
    }

    private func decryptVoe(_ raw: String) -> [String: Any]? {
        var value = rot13(raw.trimmingCharacters(in: .whitespacesAndNewlines))
        for token in ["@$", "^^", "~@", "%?", "*~", "!!", "#&"] {
            value = value.replacingOccurrences(of: token, with: "_")
        }
        value = value.replacingOccurrences(of: "_", with: "")
        guard let firstData = Data(base64Encoded: value, options: .ignoreUnknownCharacters),
              let firstString = String(data: firstData, encoding: .utf8) else { return nil }

        var shifted = ""
        for scalar in firstString.unicodeScalars {
            if let next = UnicodeScalar(max(0, Int(scalar.value) - 3)) {
                shifted.unicodeScalars.append(next)
            } else {
                shifted.unicodeScalars.append(scalar)
            }
        }
        let reversed = String(shifted.reversed())

        guard let finalData = Data(base64Encoded: reversed, options: .ignoreUnknownCharacters),
              let object = try? JSONSerialization.jsonObject(with: finalData) as? [String: Any] else { return nil }
        return object
    }

    private func rot13(_ value: String) -> String {
        var output = ""
        for scalar in value.unicodeScalars {
            let v = scalar.value
            if v >= 65 && v <= 90,
               let rotated = UnicodeScalar(((v - 65 + 13) % 26) + 65) {
                output.unicodeScalars.append(rotated)
            } else if v >= 97 && v <= 122,
                      let rotated = UnicodeScalar(((v - 97 + 13) % 26) + 97) {
                output.unicodeScalars.append(rotated)
            } else {
                output.unicodeScalars.append(scalar)
            }
        }
        return output
    }

    private func ranked(_ urls: [URL]) -> [URL] {
        urls.sorted { rank($0) < rank($1) }
    }

    private func rank(_ url: URL) -> Int {
        let raw = url.absoluteString.lowercased()
        if raw.contains("gxplayer") || raw.contains("watch.gx") { return 0 }
        if isVoe(raw) { return 1 }
        return 2
    }

    private func isVoe(_ raw: String) -> Bool {
        [
            "voe.sx", "voe.", "voe-network", "johnfullwonder", "johnbeyondnation",
            "jilliandescribecompany", "mikaylaarealike", "christopheruntilpoint",
            "walterprettytheir", "crystaltreatmenteast", "lauradaydo", "lancewhosedifficult",
            "dianaavoidthey", "jefferycontrolmodel", "charlestoughrace", "richardquestionbuilding",
            "jessicayeahcatch", "juliewomanwish", "rebeccapracticeloss", "stevenfamilyedge",
            "nathanfromsubject", "donaldlineargroup", "tracylocalschool", "eugenemakedraw",
            "cloudwindow-route"
        ].contains { raw.contains($0) }
    }

    private func isDirectStream(_ url: URL) -> Bool {
        let raw = url.absoluteString.lowercased()
        return raw.contains(".m3u8") || raw.contains(".mp4") || raw.contains("/hls/") ||
            raw.contains("/alternative_stream/") || raw.contains("master.txt") || raw.contains("/m3u8/")
    }

    private func isYouTube(_ url: URL) -> Bool {
        let raw = url.absoluteString.lowercased()
        return raw.contains("youtube") || raw.contains("youtu.be")
    }

    private func dedupe(_ cards: [MediaCard]) -> [MediaCard] {
        var seen = Set<String>()
        return cards.filter {
            guard let page = $0.page else { return false }
            return seen.insert(page.absoluteString).inserted
        }
    }

    private func pageURL(fromSeriesID id: String) -> URL? {
        guard id.hasPrefix("mediaextra-series:") else { return nil }
        return URL(string: String(id.dropFirst("mediaextra-series:".count)))
    }

    private func showTitle(_ value: String) -> String {
        value
            .replacingOccurrences(of: #"(?i)\s*[-–:]\s*(?:staffel\s*)?\d+(?:\s*staffel)?\s*$"#, with: "", options: .regularExpression)
            .replacingOccurrences(of: #"(?i)\s*[-–:]\s*s(?:eason)?\s*\d+\s*$"#, with: "", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func showKey(_ value: String) -> String {
        showTitle(value).folding(options: [.caseInsensitive, .diacriticInsensitive], locale: Locale(identifier: "de_DE"))
    }

    private func seasonNumber(from value: String) -> Int {
        for pattern in [
            #"(?i)staffel\s*(\d+)"#,
            #"(?i)(?:^|\s|-)\s*(\d+)\s*staffel"#,
            #"(?i)\bs(?:eason)?\s*(\d+)\b"#
        ] {
            if let raw = first(pattern, in: value), let number = Int(raw) { return max(1, number) }
        }
        return 1
    }

    private func episodeNumber(_ value: String) -> Int? {
        guard let raw = first(#"(\d+)"#, in: value) else { return nil }
        return Int(raw)
    }

    private func isGenericEpisodeTitle(_ value: String) -> Bool {
        value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
            value.range(of: #"(?i)^\s*(?:folge|episode|ep\.?|e)\s*\d+\s*$"#, options: .regularExpression) != nil
    }

    private func title(in html: String) -> String {
        first(#"(?is)<h1[^>]*itemprop="name"[^>]*>([^<]+)"#, in: html).map(clean)
            ?? first(#"(?is)<h1[^>]*>([^<]+)"#, in: html).map(clean)
            ?? ""
    }

    private func absoluteURL(_ raw: String, base: URL?) -> URL? {
        let value = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if value.isEmpty || value.hasPrefix("about:") { return nil }
        if value.hasPrefix("//") { return URL(string: "https:" + value) }
        if let direct = URL(string: value), direct.scheme != nil { return direct }
        guard let base else { return nil }
        return URL(string: value, relativeTo: base)?.absoluteURL
    }

    private func clean(_ raw: String) -> String {
        raw
            .replacingOccurrences(of: "<[^>]+>", with: " ", options: .regularExpression)
            .replacingOccurrences(of: "&amp;", with: "&")
            .replacingOccurrences(of: "&quot;", with: "\"")
            .replacingOccurrences(of: "&#39;", with: "'")
            .replacingOccurrences(of: "&nbsp;", with: " ")
            .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func first(_ pattern: String, in text: String, wholeMatch: Bool = false) -> String? {
        guard let regex = try? NSRegularExpression(pattern: pattern, options: []),
              let match = regex.firstMatch(in: text, range: NSRange(text.startIndex..., in: text)) else { return nil }
        let index = wholeMatch ? 0 : 1
        guard match.numberOfRanges > index,
              let range = Range(match.range(at: index), in: text) else { return nil }
        return String(text[range])
    }

    private func regexMatches(_ pattern: String, in text: String) -> [[String]] {
        guard let regex = try? NSRegularExpression(pattern: pattern, options: []) else { return [] }
        return regex.matches(in: text, range: NSRange(text.startIndex..., in: text)).map { match in
            (0..<match.numberOfRanges).map { index in
                guard let range = Range(match.range(at: index), in: text) else { return "" }
                return String(text[range])
            }
        }
    }

    private func saveCache(movies: [Movie], series: [Series]) {
        let pages = seriesPagesByKey.map { SeriesPagesCache(key: $0.key, urls: $0.value.map(\.absoluteString)) }
        let cache = MediaCache(
            movies: movies.map { CachedMovie($0) },
            series: series.map { CachedSeries($0) },
            seriesPages: pages
        )
        guard let data = try? JSONEncoder().encode(cache) else { return }
        try? FileManager.default.createDirectory(
            at: cacheURL.deletingLastPathComponent(),
            withIntermediateDirectories: true
        )
        try? data.write(to: cacheURL, options: .atomic)
    }

    private var cacheURL: URL {
        let root = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first!
        return root.appendingPathComponent("Streamy", isDirectory: true)
            .appendingPathComponent("media-extra.json")
    }
}

private struct MediaCard {
    let name: String
    let page: URL?
    let poster: URL?
    let plot: String?
    let year: String?
    let isSeries: Bool
}

private struct MediaCache: Codable {
    let movies: [CachedMovie]
    let series: [CachedSeries]
    let seriesPages: [SeriesPagesCache]
}

private struct SeriesPagesCache: Codable {
    let key: String
    let urls: [String]
}

private struct CachedMovie: Codable {
    let id: String
    let name: String
    let categoryID: String?
    let posterURL: String?
    let streamURL: String
    let plot: String?
    let year: String?
    let rating: String?

    init(_ movie: Movie) {
        id = movie.id
        name = movie.name
        categoryID = movie.categoryID
        posterURL = movie.posterURL?.absoluteString
        streamURL = movie.streamURL.absoluteString
        plot = movie.plot
        year = movie.year
        rating = movie.rating
    }

    var movie: Movie? {
        guard let url = URL(string: streamURL) else { return nil }
        return Movie(
            id: id,
            name: name,
            categoryID: categoryID,
            posterURL: posterURL.flatMap(URL.init(string:)),
            streamURL: url,
            plot: plot,
            year: year,
            rating: rating
        )
    }
}

private struct CachedSeries: Codable {
    let id: String
    let name: String
    let categoryID: String?
    let posterURL: String?
    let plot: String?
    let rating: String?

    init(_ series: Series) {
        id = series.id
        name = series.name
        categoryID = series.categoryID
        posterURL = series.posterURL?.absoluteString
        plot = series.plot
        rating = series.rating
    }

    var series: Series? {
        Series(
            id: id,
            name: name,
            categoryID: categoryID,
            posterURL: posterURL.flatMap(URL.init(string:)),
            plot: plot,
            rating: rating
        )
    }
}
