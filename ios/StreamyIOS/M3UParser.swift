import Foundation

enum M3UParser {
    static func load(url: URL) async throws -> [Channel] {
        let (data, _) = try await URLSession.shared.data(from: url)
        guard let text = String(data: data, encoding: .utf8) ?? String(data: data, encoding: .isoLatin1) else {
            throw URLError(.cannotDecodeContentData)
        }
        return parse(text)
    }

    static func parse(_ text: String) -> [Channel] {
        let lines = text.components(separatedBy: .newlines)
        var output: [Channel] = []
        var metadata: String?

        for raw in lines {
            let line = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !line.isEmpty else { continue }
            if line.hasPrefix("#EXTINF") {
                metadata = line
                continue
            }
            guard !line.hasPrefix("#"), let meta = metadata, let url = URL(string: line) else { continue }
            let name = meta.split(separator: ",", maxSplits: 1).last.map(String.init)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? "Sender"
            let epgID = attribute("tvg-id", in: meta)
            let logo = attribute("tvg-logo", in: meta).flatMap(URL.init(string:))
            let group = attribute("group-title", in: meta)
            let id = epgID?.isEmpty == false ? epgID! : UUID().uuidString
            output.append(Channel(
                id: id,
                name: name,
                categoryID: group,
                logoURL: logo,
                streamURL: url,
                epgID: epgID,
                streamID: nil,
                hasCatchup: false,
                catchupDays: 0
            ))
            metadata = nil
        }
        return output
    }

    private static func attribute(_ key: String, in line: String) -> String? {
        let token = key + "=""
        guard let start = line.range(of: token)?.upperBound,
              let end = line[start...].firstIndex(of: """) else { return nil }
        return String(line[start..<end])
    }
}
