import Foundation

struct XtreamAPI {
    let baseURL: URL
    let username: String
    let password: String

    init?(source: PlaylistSource, credentials: (username: String, password: String)?) {
        guard source.kind == .xtream,
              let raw = source.baseURL,
              let base = URL(string: raw),
              let credentials else { return nil }
        baseURL = base
        username = credentials.username
        password = credentials.password
    }

    func categories(kind: String) async throws -> [Category] {
        let action: String
        switch kind {
        case "vod": action = "get_vod_categories"
        case "series": action = "get_series_categories"
        default: action = "get_live_categories"
        }
        let values: [CategoryDTO] = try await request(action: action)
        return values.map { Category(id: $0.categoryID, name: $0.categoryName) }
    }

    func liveChannels(categoryID: String? = nil) async throws -> [Channel] {
        var extra: [URLQueryItem] = []
        if let categoryID { extra.append(URLQueryItem(name: "category_id", value: categoryID)) }
        let values: [LiveDTO] = try await request(action: "get_live_streams", extra: extra)
        return values.compactMap { dto in
            guard let url = streamURL(section: "live", streamID: dto.streamID, extension: "m3u8") else { return nil }
            return Channel(
                id: String(dto.streamID),
                name: dto.name,
                categoryID: dto.categoryID,
                logoURL: dto.streamIcon.flatMap(URL.init(string:)),
                streamURL: url,
                epgID: dto.epgChannelID,
                streamID: dto.streamID,
                hasCatchup: dto.tvArchive == 1,
                catchupDays: Int(dto.tvArchiveDuration ?? "0") ?? 0
            )
        }
    }

    func movies(categoryID: String? = nil) async throws -> [Movie] {
        var extra: [URLQueryItem] = []
        if let categoryID { extra.append(URLQueryItem(name: "category_id", value: categoryID)) }
        let values: [VodDTO] = try await request(action: "get_vod_streams", extra: extra)
        return values.compactMap { dto in
            guard let url = streamURL(section: "movie", streamID: dto.streamID, extension: dto.containerExtension ?? "mp4") else { return nil }
            return Movie(
                id: String(dto.streamID),
                name: dto.name,
                categoryID: dto.categoryID,
                posterURL: dto.streamIcon.flatMap(URL.init(string:)),
                streamURL: url,
                plot: dto.plot,
                year: dto.year,
                rating: dto.rating
            )
        }
    }

    func series(categoryID: String? = nil) async throws -> [Series] {
        var extra: [URLQueryItem] = []
        if let categoryID { extra.append(URLQueryItem(name: "category_id", value: categoryID)) }
        let values: [SeriesDTO] = try await request(action: "get_series", extra: extra)
        return values.map {
            Series(
                id: String($0.seriesID),
                name: $0.name,
                categoryID: $0.categoryID,
                posterURL: $0.cover.flatMap(URL.init(string:)),
                plot: $0.plot,
                rating: $0.rating
            )
        }
    }

    func episodes(seriesID: String) async throws -> [Episode] {
        let response: SeriesInfoDTO = try await request(
            action: "get_series_info",
            extra: [URLQueryItem(name: "series_id", value: seriesID)]
        )
        var output: [Episode] = []
        for (seasonKey, list) in response.episodes {
            let seasonNumber = Int(seasonKey) ?? 0
            for dto in list {
                let ext = dto.containerExtension ?? "mp4"
                guard let url = streamURL(section: "series", streamID: dto.id, extension: ext) else { continue }
                output.append(Episode(
                    id: String(dto.id),
                    season: Int(dto.season ?? "") ?? seasonNumber,
                    episode: dto.episodeNum ?? 0,
                    title: dto.title ?? "Episode (dto.episodeNum ?? 0)",
                    streamURL: url,
                    plot: dto.info?.plot
                ))
            }
        }
        return output.sorted {
            $0.season == $1.season ? $0.episode < $1.episode : $0.season < $1.season
        }
    }

    func xmltvURL() -> URL? {
        var components = URLComponents(url: baseURL.appendingPathComponent("xmltv.php"), resolvingAgainstBaseURL: false)
        components?.queryItems = [
            URLQueryItem(name: "username", value: username),
            URLQueryItem(name: "password", value: password)
        ]
        return components?.url
    }

    func catchupURL(channel: Channel, program: EPGProgram) -> URL? {
        guard channel.hasCatchup, let streamID = channel.streamID else { return nil }
        let duration = max(1, Int(program.stop.timeIntervalSince(program.start) / 60))
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd:HH-mm"
        let start = formatter.string(from: program.start)
        var components = URLComponents(url: baseURL.appendingPathComponent("timeshift.php"), resolvingAgainstBaseURL: false)
        components?.queryItems = [
            URLQueryItem(name: "username", value: username),
            URLQueryItem(name: "password", value: password),
            URLQueryItem(name: "duration", value: String(duration)),
            URLQueryItem(name: "start", value: start),
            URLQueryItem(name: "stream", value: String(streamID))
        ]
        return components?.url
    }

    private func streamURL(section: String, streamID: Int, extension ext: String) -> URL? {
        baseURL
            .appendingPathComponent(section)
            .appendingPathComponent(username)
            .appendingPathComponent(password)
            .appendingPathComponent("(streamID).(ext)")
    }

    private func request<T: Decodable>(action: String, extra: [URLQueryItem] = []) async throws -> T {
        var components = URLComponents(url: baseURL.appendingPathComponent("player_api.php"), resolvingAgainstBaseURL: false)
        components?.queryItems = [
            URLQueryItem(name: "username", value: username),
            URLQueryItem(name: "password", value: password),
            URLQueryItem(name: "action", value: action)
        ] + extra
        guard let url = components?.url else { throw URLError(.badURL) }
        var request = URLRequest(url: url)
        request.timeoutInterval = 30
        request.setValue("Streamy-iOS/0.1", forHTTPHeaderField: "User-Agent")
        let (data, response) = try await URLSession.shared.data(for: request)
        if let http = response as? HTTPURLResponse, !(200...299).contains(http.statusCode) {
            throw URLError(.badServerResponse)
        }
        return try JSONDecoder().decode(T.self, from: data)
    }
}

private struct CategoryDTO: Decodable {
    let categoryID: String
    let categoryName: String
    enum CodingKeys: String, CodingKey {
        case categoryID = "category_id"
        case categoryName = "category_name"
    }
}

private struct LiveDTO: Decodable {
    let streamID: Int
    let name: String
    let categoryID: String?
    let streamIcon: String?
    let epgChannelID: String?
    let tvArchive: Int?
    let tvArchiveDuration: String?

    enum CodingKeys: String, CodingKey {
        case streamID = "stream_id"
        case name
        case categoryID = "category_id"
        case streamIcon = "stream_icon"
        case epgChannelID = "epg_channel_id"
        case tvArchive = "tv_archive"
        case tvArchiveDuration = "tv_archive_duration"
    }
}

private struct VodDTO: Decodable {
    let streamID: Int
    let name: String
    let categoryID: String?
    let streamIcon: String?
    let containerExtension: String?
    let plot: String?
    let year: String?
    let rating: String?

    enum CodingKeys: String, CodingKey {
        case streamID = "stream_id"
        case name
        case categoryID = "category_id"
        case streamIcon = "stream_icon"
        case containerExtension = "container_extension"
        case plot, year, rating
    }
}

private struct SeriesDTO: Decodable {
    let seriesID: Int
    let name: String
    let categoryID: String?
    let cover: String?
    let plot: String?
    let rating: String?

    enum CodingKeys: String, CodingKey {
        case seriesID = "series_id"
        case name
        case categoryID = "category_id"
        case cover, plot, rating
    }
}

private struct SeriesInfoDTO: Decodable {
    let episodes: [String: [EpisodeDTO]]
}

private struct EpisodeDTO: Decodable {
    let id: Int
    let episodeNum: Int?
    let title: String?
    let containerExtension: String?
    let season: String?
    let info: EpisodeInfoDTO?

    enum CodingKeys: String, CodingKey {
        case id
        case episodeNum = "episode_num"
        case title
        case containerExtension = "container_extension"
        case season, info
    }
}

private struct EpisodeInfoDTO: Decodable {
    let plot: String?
}
