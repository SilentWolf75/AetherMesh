#pragma once
#include <stdint.h>
#include <stddef.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <math.h>
#include <algorithm>
using std::min;
using std::max;
#define HEX 16
extern uint32_t simulatedMillis;
inline uint32_t millis() { return simulatedMillis; }
inline uint32_t micros() { return simulatedMillis * 1000u; }
inline long random(long low, long high) { return low + (high - low) / 2; }
inline long random(long high) { return random(0, high); }
struct HostSerial {
    template<typename... T> void print(T...) {}
    template<typename... T> void println(T...) {}
    template<typename... T> void printf(T...) {}
};
extern HostSerial Serial;
