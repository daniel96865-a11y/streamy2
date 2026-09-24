import Foundation

final class XMLTVParser: NSObject, XMLParserDelegate {
    private(set) var programs: [EPGProgram] = []
    private var currentChannel = ""
    private var currentStart: Date?
    private var currentStop: Date?
    private var currentTitle = ""
    private var currentSubtitle = ""
    private var currentDescription = ""
    private var currentElement = ""
    private var buffer = ""

    static func load(url: URL) async throws -> [EPGProgram] {
        let (data, _) = try await URLSession.shared.data(from: url)
        let parser = XMLTVParser()
        let xml = XMLParser(data: data)
        xml.delegate = parser
        guard xml.parse() else {
            throw xml.parserError ?? URLError(.cannotParseResponse)
        }
        return parser.programs
    }

    func parser(_ parser: XMLParser, didStartElement elementName: String, namespaceURI: String?, qualifiedName qName: String?, attributes: [String : String] = [:]) {
        currentElement = elementName
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
                    id: "(currentChannel)-(Int(start.timeIntervalSince1970))",
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
        let value = raw.split(separator: " ").first.map(String.init) ?? raw
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyyMMddHHmmss"
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        if let date = formatter.date(from: value) { return date }
        formatter.dateFormat = "yyyyMMddHHmm"
        return formatter.date(from: value)
    }
}
