import Foundation
import MultipeerConnectivity
import UIKit

struct PlaylistTransfer: Codable {
    let source: PlaylistSource
    let username: String?
    let password: String?
}

final class DeviceSyncService: NSObject, ObservableObject {
    @Published private(set) var peers: [MCPeerID] = []
    @Published private(set) var connectedPeers: [MCPeerID] = []
    @Published var receivedTransfer: PlaylistTransfer?
    @Published var statusText = "Bereit"

    private let serviceType = "streamy-sync"
    private let peerID = MCPeerID(displayName: String(UIDevice.current.name.prefix(60)))
    private lazy var session = MCSession(peer: peerID, securityIdentity: nil, encryptionPreference: .required)
    private var advertiser: MCNearbyServiceAdvertiser!
    private var browser: MCNearbyServiceBrowser!

    override init() {
        super.init()
        session.delegate = self
        advertiser = MCNearbyServiceAdvertiser(peer: peerID, discoveryInfo: nil, serviceType: serviceType)
        browser = MCNearbyServiceBrowser(peer: peerID, serviceType: serviceType)
        advertiser.delegate = self
        browser.delegate = self
    }

    func start() {
        advertiser.startAdvertisingPeer()
        browser.startBrowsingForPeers()
        statusText = "Suche nach Streamy-Geräten …"
    }

    func stop() {
        advertiser.stopAdvertisingPeer()
        browser.stopBrowsingForPeers()
    }

    func connect(_ peer: MCPeerID) {
        statusText = "Verbinde mit (peer.displayName) …"
        browser.invitePeer(peer, to: session, withContext: nil, timeout: 20)
    }

    func send(_ transfer: PlaylistTransfer) throws {
        let data = try JSONEncoder().encode(transfer)
        guard !session.connectedPeers.isEmpty else {
            throw NSError(domain: "StreamySync", code: 1, userInfo: [NSLocalizedDescriptionKey: "Kein Gerät verbunden."])
        }
        try session.send(data, toPeers: session.connectedPeers, with: .reliable)
        DispatchQueue.main.async { self.statusText = "Wiedergabeliste gesendet" }
    }
}

extension DeviceSyncService: MCNearbyServiceAdvertiserDelegate {
    func advertiser(_ advertiser: MCNearbyServiceAdvertiser, didReceiveInvitationFromPeer peerID: MCPeerID, withContext context: Data?, invitationHandler: @escaping (Bool, MCSession?) -> Void) {
        DispatchQueue.main.async {
            self.statusText = "Verbindungsanfrage von (peerID.displayName)"
        }
        invitationHandler(true, session)
    }

    func advertiser(_ advertiser: MCNearbyServiceAdvertiser, didNotStartAdvertisingPeer error: Error) {
        DispatchQueue.main.async { self.statusText = error.localizedDescription }
    }
}

extension DeviceSyncService: MCNearbyServiceBrowserDelegate {
    func browser(_ browser: MCNearbyServiceBrowser, foundPeer peerID: MCPeerID, withDiscoveryInfo info: [String : String]?) {
        DispatchQueue.main.async {
            if !self.peers.contains(peerID) { self.peers.append(peerID) }
        }
    }

    func browser(_ browser: MCNearbyServiceBrowser, lostPeer peerID: MCPeerID) {
        DispatchQueue.main.async { self.peers.removeAll { $0 == peerID } }
    }

    func browser(_ browser: MCNearbyServiceBrowser, didNotStartBrowsingForPeers error: Error) {
        DispatchQueue.main.async { self.statusText = error.localizedDescription }
    }
}

extension DeviceSyncService: MCSessionDelegate {
    func session(_ session: MCSession, peer peerID: MCPeerID, didChange state: MCSessionState) {
        DispatchQueue.main.async {
            self.connectedPeers = session.connectedPeers
            switch state {
            case .connected: self.statusText = "Verbunden mit (peerID.displayName)"
            case .connecting: self.statusText = "Verbinde mit (peerID.displayName) …"
            case .notConnected: self.statusText = "Nicht verbunden"
            @unknown default: self.statusText = "Unbekannter Status"
            }
        }
    }

    func session(_ session: MCSession, didReceive data: Data, fromPeer peerID: MCPeerID) {
        guard let transfer = try? JSONDecoder().decode(PlaylistTransfer.self, from: data) else { return }
        DispatchQueue.main.async {
            self.receivedTransfer = transfer
            self.statusText = "Wiedergabeliste von (peerID.displayName) empfangen"
        }
    }

    func session(_ session: MCSession, didReceive stream: InputStream, withName streamName: String, fromPeer peerID: MCPeerID) {}
    func session(_ session: MCSession, didStartReceivingResourceWithName resourceName: String, fromPeer peerID: MCPeerID, with progress: Progress) {}
    func session(_ session: MCSession, didFinishReceivingResourceWithName resourceName: String, fromPeer peerID: MCPeerID, at localURL: URL?, withError error: Error?) {}
}
