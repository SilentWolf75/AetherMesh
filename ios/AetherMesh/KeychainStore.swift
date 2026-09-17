import AetherMeshKit
import Foundation
import Security

/// Chat keys and node passwords in the iOS Keychain, device-only (never synced
/// to iCloud or included in backups restored to another device).
final class KeychainStore: ChatKeyProvider {
    private let service = "com.silentwolf75.aethermesh"

    func chatKey(for chatIdentifier: String) -> String? {
        read(account: "chatkey:\(chatIdentifier)")
    }

    func setChatKey(_ key: String, for chatIdentifier: String) {
        let trimmed = key.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty {
            delete(account: "chatkey:\(chatIdentifier)")
        } else {
            save(trimmed, account: "chatkey:\(chatIdentifier)")
        }
    }

    func nodePassword(for peripheral: UUID) -> String? {
        read(account: "nodepassword:\(peripheral.uuidString)")
    }

    func setNodePassword(_ password: String?, for peripheral: UUID) {
        if let password, !password.isEmpty {
            save(password, account: "nodepassword:\(peripheral.uuidString)")
        } else {
            delete(account: "nodepassword:\(peripheral.uuidString)")
        }
    }

    private func baseQuery(account: String) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
    }

    private func read(account: String) -> String? {
        var query = baseQuery(account: account)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: AnyObject?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    private func save(_ value: String, account: String) {
        let data = Data(value.utf8)
        let attributes: [String: Any] = [
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        ]
        let status = SecItemUpdate(baseQuery(account: account) as CFDictionary, attributes as CFDictionary)
        if status == errSecItemNotFound {
            var insert = baseQuery(account: account)
            insert.merge(attributes) { _, new in new }
            SecItemAdd(insert as CFDictionary, nil)
        }
    }

    private func delete(account: String) {
        SecItemDelete(baseQuery(account: account) as CFDictionary)
    }
}
