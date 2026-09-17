#ifndef BLE_SESSION_H
#define BLE_SESSION_H

#include <stdint.h>

/**
 * Binds phone trust (authentication, OTA ownership) to one BLE connection.
 *
 * BLEManager bumps a connection generation from the stack's connect and
 * disconnect callbacks and stamps every phone write with the generation it
 * arrived on. Trust is recorded against a generation, so it lapses the moment
 * the link changes, even when the main loop never observes the
 * disconnect/reconnect pair (a multi-second LoRa TX can hide both inside one
 * loop pass). The old design polled a connected flag from loop() and let a new
 * client inherit the previous client's authenticated session.
 *
 * Pure logic with no Arduino dependencies; covered by test/test_blesession.
 */
namespace blesession {

constexpr uint32_t NO_GENERATION = 0;

enum class PhonePacketRoute {
    Drop,           // queued on an earlier connection; never act on it
    AuthHandshake,  // unauthenticated: only the auth request flow may run
    OtaOnly,        // authenticated while an OTA is streaming
    Normal,         // authenticated, no OTA
};

enum class OtaPacketKind {
    Begin,
    Data,
    End,
    Abort,
    EnterDfu,
};

class Session {
public:
    bool isAuthenticated(uint32_t liveGeneration) const {
        return authGeneration_ != NO_GENERATION && authGeneration_ == liveGeneration;
    }

    /** Record authentication for the connection the auth packet arrived on. */
    void markAuthenticated(uint32_t packetGeneration) { authGeneration_ = packetGeneration; }

    void clearAuthentication() { authGeneration_ = NO_GENERATION; }

    /** Record which connection started the OTA stream. */
    void claimOta(uint32_t packetGeneration) { otaGeneration_ = packetGeneration; }

    void releaseOta() { otaGeneration_ = NO_GENERATION; }

    bool ownsOta(uint32_t generation) const {
        return otaGeneration_ != NO_GENERATION && otaGeneration_ == generation;
    }

    /**
     * True once per change of the live generation. loop() uses this to run
     * connect/disconnect side effects even when it missed the intermediate
     * connection state.
     */
    bool consumeGenerationChange(uint32_t liveGeneration) {
        if (liveGeneration == seenGeneration_) return false;
        seenGeneration_ = liveGeneration;
        return true;
    }

    /**
     * An OTA stream belongs to one connection. Abort when the link has changed
     * since BEGIN; the app restarts from BEGIN after a reconnect anyway.
     */
    bool otaOrphaned(bool otaActive, uint32_t liveGeneration) const {
        return otaActive && !ownsOta(liveGeneration);
    }

    PhonePacketRoute route(uint32_t packetGeneration, uint32_t liveGeneration, bool otaActive) const {
        if (packetGeneration == NO_GENERATION || packetGeneration != liveGeneration) {
            return PhonePacketRoute::Drop;
        }
        if (!isAuthenticated(packetGeneration)) return PhonePacketRoute::AuthHandshake;
        return otaActive ? PhonePacketRoute::OtaOnly : PhonePacketRoute::Normal;
    }

    /**
     * Whether an authenticated packet may drive the OTA state machine. BEGIN
     * and ENTER_DFU start fresh work; DATA/END/ABORT only steer the stream
     * owned by the same connection.
     */
    bool mayHandleOta(OtaPacketKind kind, bool otaActive, uint32_t packetGeneration) const {
        switch (kind) {
            case OtaPacketKind::Begin:
            case OtaPacketKind::EnterDfu:
                return true;
            case OtaPacketKind::Data:
            case OtaPacketKind::End:
            case OtaPacketKind::Abort:
                // No active stream: let the handler report "No OTA in progress".
                return !otaActive || ownsOta(packetGeneration);
        }
        return false;
    }

private:
    uint32_t authGeneration_ = NO_GENERATION;
    uint32_t otaGeneration_ = NO_GENERATION;
    uint32_t seenGeneration_ = NO_GENERATION;
};

/** A password change must never leave the node unclaimed. */
inline bool isAcceptableNewPassword(const char* password) {
    return password != nullptr && password[0] != '\0';
}

} // namespace blesession

#endif
