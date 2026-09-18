/* nRF52840 with SoftDevice S140 v7 (the T1000-E ships with 7.3.0).
 *
 * The SoftDevice occupies flash below 0x27000, so the application starts
 * there. It ends at 0xED000, where the Adafruit core keeps its LittleFS
 * filesystem and, above that, the bootloader.
 *
 * RAM below 0x20004000 is reserved for the SoftDevice. That floor depends on
 * the Bluetooth configuration (one connection, MTU 247); if the Bluefruit
 * setup grows, the SoftDevice will report the size it needs and this must
 * move up to match.
 */

SEARCH_DIR(.)
GROUP(-lgcc -lc -lnosys)

MEMORY
{
  FLASH (rx) : ORIGIN = 0x27000, LENGTH = 0xED000 - 0x27000
  RAM (rwx) : ORIGIN = 0x20004000, LENGTH = 0x20040000 - 0x20004000
}

/* Sections the SoftDevice runtime and flash-storage layer locate by their
 * start and stop symbols, placed in RAM after .data. */
SECTIONS
{
  . = ALIGN(4);
  .svc_data :
  {
    PROVIDE(__start_svc_data = .);
    KEEP(*(.svc_data))
    PROVIDE(__stop_svc_data = .);
  } > RAM

  .fs_data :
  {
    PROVIDE(__start_fs_data = .);
    KEEP(*(.fs_data))
    PROVIDE(__stop_fs_data = .);
  } > RAM
} INSERT AFTER .data;

INCLUDE "nrf52_common.ld"
