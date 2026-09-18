#ifndef AIRTIME_WINDOW_H
#define AIRTIME_WINDOW_H

#include <stdint.h>

/**
 * Airtime used over a sliding window, kept in BUCKETS fixed slots of
 * BUCKET_MS each (so the window is BUCKETS * BUCKET_MS long and memory is
 * constant). Used for the regulatory transmit duty cycle (an hour) and for
 * how busy the channel is (a minute).
 */
template <uint16_t BUCKETS, uint32_t BUCKET_MS>
class AirtimeWindow {
public:
    static constexpr uint32_t WINDOW_MS = (uint32_t)BUCKETS * BUCKET_MS;

    void add(uint32_t nowMs, uint32_t airtimeMs) {
        advance(nowMs);
        uint32_t& slot = ms_[current_];
        slot = (slot + airtimeMs < slot) ? UINT32_MAX : slot + airtimeMs;
    }

    uint32_t totalMs(uint32_t nowMs) {
        advance(nowMs);
        uint64_t sum = 0;
        for (uint16_t i = 0; i < BUCKETS; i++) sum += ms_[i];
        return sum > UINT32_MAX ? UINT32_MAX : (uint32_t)sum;
    }

    /** Share of the window spent on air, in percent (0-100). */
    uint8_t percent(uint32_t nowMs) {
        uint64_t pct = (uint64_t)totalMs(nowMs) * 100u / WINDOW_MS;
        return pct > 100u ? 100u : (uint8_t)pct;
    }

    /** Whether adding airtimeMs would keep the window at or under limitPercent. */
    bool fits(uint32_t nowMs, uint32_t airtimeMs, uint8_t limitPercent) {
        if (limitPercent >= 100) return true;
        uint64_t allowed = (uint64_t)WINDOW_MS * limitPercent / 100u;
        return (uint64_t)totalMs(nowMs) + airtimeMs <= allowed;
    }

private:
    // Moves on by elapsed time rather than by nowMs / BUCKET_MS, so the
    // millis() wrap after 49.7 days does not look like a jump.
    void advance(uint32_t nowMs) {
        if (!started_) {
            started_ = true;
            lastMs_ = nowMs;
            return;
        }
        uint32_t elapsed = nowMs - lastMs_;  // unsigned: correct across the wrap
        lastMs_ = nowMs;
        if (elapsed >= WINDOW_MS) {
            for (uint16_t i = 0; i < BUCKETS; i++) ms_[i] = 0;
            intoBucket_ = 0;
            return;
        }
        intoBucket_ += elapsed;
        while (intoBucket_ >= BUCKET_MS) {
            intoBucket_ -= BUCKET_MS;
            current_ = (uint16_t)((current_ + 1) % BUCKETS);
            ms_[current_] = 0;
        }
    }

    uint32_t ms_[BUCKETS] = {};
    uint16_t current_ = 0;
    uint32_t lastMs_ = 0;
    uint32_t intoBucket_ = 0;
    bool started_ = false;
};

#endif  // AIRTIME_WINDOW_H
