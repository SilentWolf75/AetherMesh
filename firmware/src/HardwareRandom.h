#pragma once

#include <stddef.h>
#include <stdint.h>

// Cryptographic randomness from the chip's hardware generator: identity seeds
// and the nonce of every sealed message come from here.
//
// On nRF52 the RNG belongs to the SoftDevice once Bluetooth is running. Nordic
// lists it as a restricted peripheral: the application must go through
// sd_rand_application_vector_get() rather than the registers. Reading NRF_RNG
// directly then is unsupported, so this picks the right path for the moment
// it is called — registers before Bluetooth starts (identity generation at
// boot), the SoftDevice API afterwards (sealing a message).
namespace hwrandom {

// Fills `out` with `len` random bytes. Returns false if the generator could
// not deliver them, in which case the caller must not use `out`: a predictable
// nonce or seed is worse than refusing to seal.
bool fill(uint8_t* out, size_t len);

}  // namespace hwrandom
