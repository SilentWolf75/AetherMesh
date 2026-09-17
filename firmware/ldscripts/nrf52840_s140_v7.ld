/* SoftDevice S140 v7.x (factory T1000-E = 7.3.0). App starts at 0x27000;
 * LittleFS stays at the Adafruit default above 0xED000. */

SEARCH_DIR(.)
GROUP(-lgcc -lc -lnosys)

MEMORY
{
  FLASH (rx) : ORIGIN = 0x27000, LENGTH = 0xED000 - 0x27000
  /* SoftDevice RAM floor for Bluefruit 1-link / MTU 247 — re-measure if
   * Bluefruit config grows. */
  RAM (rwx) : ORIGIN = 0x20004000, LENGTH = 0x20040000 - 0x20004000
}

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
