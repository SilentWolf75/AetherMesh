#ifndef TX_POWER_H
#define TX_POWER_H

#include <stddef.h>
#include <stdint.h>

/**
 * Transmit power as the user means it: dBm at the antenna connector.
 *
 * Boards with an external power amplifier (Heltec V4, RAK3401 1W) add
 * roughly 7-13 dB on top of whatever the radio chip is set to, and the gain
 * falls as the chip is driven harder. The settings, the app and the regional
 * limit all speak in antenna dBm, so this module converts that into the chip
 * setting for the board and reports what actually goes out.
 *
 * Gains are per chip dBm (index 0 = 0 dBm) from bench measurements of each
 * amplifier; where a board has two amplifier variants the higher gain is
 * used, so the output never exceeds what was asked for.
 */
namespace txpower {

struct Amplifier {
    const uint8_t* gainDb;  // gain at chip power 0, 1, 2, ... dBm
    uint8_t points;         // entries in gainDb; the chip is never set above points - 1
};

constexpr int8_t CHIP_MIN_DBM = -9;
constexpr int8_t CHIP_MAX_DBM = 22;

// Heltec V4: GC1109 or KCT8103L front end, whichever gains more at each step.
constexpr uint8_t HELTEC_V4_GAIN[] = {
    13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13,
    12, 12, 11, 11, 10, 9, 8, 7};

// RAK3401 with the RAK13302 1 W amplifier.
constexpr uint8_t RAK13302_GAIN[] = {
    7, 8, 8, 8, 8, 8, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 8};

constexpr Amplifier NO_AMPLIFIER = {nullptr, 0};
constexpr Amplifier HELTEC_V4_AMP = {HELTEC_V4_GAIN, sizeof(HELTEC_V4_GAIN)};
constexpr Amplifier RAK13302_AMP = {RAK13302_GAIN, sizeof(RAK13302_GAIN)};

inline bool hasAmplifier(const Amplifier& amp) { return amp.gainDb != nullptr && amp.points > 0; }

inline int8_t chipMaxFor(const Amplifier& amp) {
    return hasAmplifier(amp) ? (int8_t)(amp.points - 1) : CHIP_MAX_DBM;
}

inline int gainAt(const Amplifier& amp, int chipDbm) {
    if (!hasAmplifier(amp)) return 0;
    if (chipDbm < 0) chipDbm = 0;
    if (chipDbm >= amp.points) chipDbm = amp.points - 1;
    return amp.gainDb[chipDbm];
}

/** dBm at the antenna for a given chip setting. */
inline int outputFor(const Amplifier& amp, int chipDbm) {
    return chipDbm + gainAt(amp, chipDbm);
}

/** Highest antenna power this board can produce. */
inline int boardMaxDbm(const Amplifier& amp) {
    int best = outputFor(amp, CHIP_MIN_DBM);
    for (int c = CHIP_MIN_DBM; c <= chipMaxFor(amp); c++) {
        int out = outputFor(amp, c);
        if (out > best) best = out;
    }
    return best;
}

/**
 * Chip setting for a wanted antenna power: the strongest setting whose output
 * does not exceed it. Asking for less than the board can go gives its minimum.
 */
inline int8_t chipDbmFor(const Amplifier& amp, int wantedDbm) {
    int8_t chosen = CHIP_MIN_DBM;
    for (int c = CHIP_MIN_DBM; c <= chipMaxFor(amp); c++) {
        if (outputFor(amp, c) <= wantedDbm) chosen = (int8_t)c;
    }
    return chosen;
}

/** Regulatory ceiling for a region code (0 = US915, 1 = EU868). */
inline int regionMaxDbm(uint32_t region) {
    // US915: FCC part 15.247, 30 dBm conducted.
    // EU868: 869.4-869.65 MHz sub-band, 500 mW (27 dBm) ERP.
    return region == 1 ? 27 : 30;
}

/** What to transmit: the wanted power, limited by the board and the region. */
inline int effectiveDbm(const Amplifier& amp, int wantedDbm, uint32_t region) {
    int limit = boardMaxDbm(amp);
    int regional = regionMaxDbm(region);
    if (regional < limit) limit = regional;
    return wantedDbm > limit ? limit : wantedDbm;
}

/**
 * Converts a setting saved by firmware that treated it as the chip power into
 * the antenna power it actually produced, so an update keeps the same range.
 */
inline int migrateChipSetting(const Amplifier& amp, int savedChipDbm) {
    int chip = savedChipDbm;
    if (chip > chipMaxFor(amp)) chip = chipMaxFor(amp);
    if (chip < CHIP_MIN_DBM) chip = CHIP_MIN_DBM;
    return outputFor(amp, chip);
}

}  // namespace txpower

#endif  // TX_POWER_H
