import Foundation

final class XMLTVParser: NSObject, XMLParserDelegate {
    private(set) var programs: [EPGProgram] = []
    private var currentChannel = ""
    private var currentStart: Date?
    private var currentStop: Date?
    private var currentTitle = ""
    private var currentSubtitle = ""
    private var currentDescription = ""
    private var buffer = ""

    static func load(url: URL) async throws -> [EPGProgram] {
        let (data, _) = try await URLSession.shared.data(from: url)
        let parserDelegate = XMLTVParser()
        let parser = XMLParser(data: data)
        parser.delegate = parserDelegate
        guard parser.parse() else {
            throw parser.parserError ?? URLError(.cannotParseResponse)
        }
        return parserDelegate.programs
    }

    func parser(_ parser: XMLParser, didStartElement elementName: String, namespaceURI: String?, qualifiedName qName: String?, attributes: [String : String] = [:]) {
        buffer = ""
        if elementName == "programme" {
            currentChannel = attributes["channel"] ?? ""
            currentStart = Self.parseDate(attributes["start"])
            currentStop = Self.parseDate(attributes["stop"])
            currentTitle = ""
            currentSubtitle = ""
            currentDescription = ""
        }
    }

    func parser(_ parser: XMLParser, foundCharacters string: String) {
        buffer += string
    }

    func parser(_ parser: XMLParser, didEndElement elementName: String, namespaceURI: String?, qualifiedName qName: String?) {
        let value = buffer.trimmingCharacters(in: .whitespacesAndNewlines)
        switch elementName {
        case "title": currentTitle = value
        case "sub-title": currentSubtitle = value
        case "desc": currentDescription = value
        case "programme":
            if let start = currentStart, let stop = currentStop, !currentChannel.isEmpty {
                programs.append(EPGProgram(
                    id: "\(currentChannel)-\(Int(start.timeIntervalSince1970))",
                    channelID: currentChannel,
                    start: start,
                    stop: stop,
                    title: currentTitle.isEmpty ? "Sendung" : currentTitle,
                    subtitle: currentSubtitle.isEmpty ? nil : currentSubtitle,
                    description: currentDescription.isEmpty ? nil : currentDescription
                ))
            }
        default: break
        }
        buffer = ""
    }

    private static func parseDate(_ raw: String?) -> Date? {
        guard let raw else { return nil }
        let parts = raw.split(separator: " ")
        let value = parts.first.map(String.init) ?? raw
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = value.count >= 14 ? "yyyyMMddHHmmss" : "yyyyMMddHHmm"
        if parts.count > 1 {
            let zone = String(parts[1])
            if zone.count == 5 {
                let sign = zone.hasPrefix("-") ? -1 : 1
                let digits = zone.dropFirst()
                if let hours = Int(digits.prefix(2)), let minutes = Int(digits.suffix(2)) {
                    formatter.timeZone = TimeZone(secondsFromGMT: sign * (hours * 3600 + minutes * 60))
                }
            }
        } else {
            formatter.timeZone = TimeZone(secondsFromGMT: 0)
        }
        return formatter.date(from: value)
    }
}
