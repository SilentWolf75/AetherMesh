import Crypto
import Foundation

/// Session/counter pair that makes each authenticated remote config unique.
public struct ControlAuthIdentity: Equatable, Sendable {
    public let sessionId: UInt64
    public let counter: UInt32

    public init(sessionId: UInt64, counter: UInt32) {
        self.sessionId = sessionId
        self.counter = counter
    }
}

/// Remote-config authentication, byte-compatible with Android `ControlAuth` and
/// firmware `packetauth::buildConfigCanonical` / `verifyConfig`.
public enum ControlAuth {
    public static let domainV2 = Data("AMCFG2".utf8)
    public static let domainV3 = Data("AMCFG3".utf8)
    /// Fixed 8-byte control-key salt ("AMCTRL1" + NUL), same as firmware.
    static let controlKeySalt = Data("AMCTRL1".utf8) + Data([0])

    /// v3 control key: PBKDF2-HMAC-SHA256(password, "AMCTRL1\0", 120k).
    public static func controlKey(password: String) throws -> SymmetricKey {
        try ChatKeyDerivation.derive(passcode: password, salt: controlKeySalt)
    }

    /// 16-byte tag. `authProtocol` 3 uses the derived key (preferred); 2 keys HMAC
    /// with the raw password (legacy, kept only for old firmware).
    public static func sign(
        senderId: UInt32,
        recipientId: UInt32,
        identity: ControlAuthIdentity,
        config: Aethermesh_NodeConfig,
        password: String,
        authProtocol: Int = 3
    ) throws -> Data {
        let key = authProtocol >= 3
            ? try controlKey(password: password)
            : SymmetricKey(data: Data(password.utf8))
        let domain = authProtocol >= 3 ? domainV3 : domainV2
        let message = canonical(senderId: senderId, recipientId: recipientId, identity: identity, config: config, domain: domain)
        return Data(HMAC<SHA256>.authenticationCode(for: message, using: key)).prefix(16)
    }

    public static func canonical(
        senderId: UInt32,
        recipientId: UInt32,
        identity: ControlAuthIdentity,
        config: Aethermesh_NodeConfig,
        domain: Data = domainV2
    ) -> Data {
        var out = Data()
        out.append(domain)
        out.appendLE(senderId)
        out.appendLE(recipientId)
        out.appendLE(identity.sessionId)
        out.appendLE(identity.counter)
        let name = Data(config.nodeName.prefixUTF8(maxBytes: 16).utf8)
        out.append(UInt8(name.count))
        out.append(name)
        out.appendLE(config.loraSf)
        out.appendLE(config.loraBw.bitPattern)
        out.appendLE(UInt32(bitPattern: config.loraTxPower))
        out.appendLE(config.region)
        out.appendLE(config.nodeRole)
        out.appendLE(config.telemetryInterval)
        out.appendLE(config.screenTimeoutSecs)
        out.append(config.powerSaveMode ? 1 : 0)
        out.appendLE(config.positionPrecision)
        out.appendLE(config.gpsMode)
        out.append(config.fixedPosition ? 1 : 0)
        out.appendLE(config.fixedLatitude.bitPattern)
        out.appendLE(config.fixedLongitude.bitPattern)
        out.appendLE(UInt32(bitPattern: config.fixedAltitude))
        out.append(config.applyNameOnly ? 1 : 0)
        out.appendLE(config.meshHopLimit)
        out.appendLE(config.rebroadcastTxdelayX100)
        out.append(config.requestReport ? 1 : 0)
        out.appendLE(config.applyMask)
        let shortName = Data(config.nodeShortName.prefixUTF8(maxBytes: 4).utf8)
        out.append(UInt8(shortName.count))
        out.append(shortName)
        out.appendLE(config.gpsDutyIntervalSecs)
        return out
    }
}

extension Data {
    mutating func appendLE<T: FixedWidthInteger>(_ value: T) {
        var little = value.littleEndian
        Swift.withUnsafeBytes(of: &little) { append(contentsOf: $0) }
    }
}
