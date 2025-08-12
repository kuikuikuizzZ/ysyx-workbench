ENTRY(_start)

MEMORY {
  mrom (rx): ORIGIN = 0x20000000, LENGTH = 4K
  flash (rx): ORIGIN = 0x30000000, LENGTH = 0x10000000
  sram (rwx): ORIGIN = 0x0f000000, LENGTH = 8K
  psram (rwx): ORIGIN = 0x80000000, LENGTH = 32M
  sdram (rwx): ORIGIN = 0xa0000000, LENGTH = 128M
}
SECTIONS {
  /* _pmem_start and _entry_offset are defined in LDFLAGS */
  /* . = _pmem_start + _entry_offset;  */
  .entry : {
    _entry_start = .;
    KEEP(*(.entry))
  } > flash
  .first_boost : ALIGN(4){
    _first_boost_start = .;
    KEEP(*(.first_boost))
  } > flash
  _lma_sec_boost_start = LOADADDR (.sec_boost);
  .sec_boost : ALIGN(4) {
    _sec_boost_start = .;
    KEEP(*(.sec_boost))
    KEEP(*(.bootutils))
  } > sram AT> flash
  _sec_boost_end = .;
  _lma_code_start = LOADADDR (.text);
  .text : ALIGN(4){
    _text_start = .;
    KEEP(*(.text._trm_init))
    *(.text*)
  } > sdram AT> flash
  _etext = .;
  _lma_rodata_start = LOADADDR (.rodata);
  .rodata : ALIGN(4) {
    *(.rodata*)
  } > sdram AT> flash
  _erodata = .;
  _lma_data_start = LOADADDR (.data);
  .data : ALIGN(4){
    _data_start = .;
    *(.data)
  }  > sdram AT> flash
  _data_end = . ;
  .bss :  {
    . = ALIGN(4);
	  _bss_start = .;
    *(.bss*)
    *(.sbss*)
    *(.scommon)
  } > sdram AT> flash
  _bss_end = .;
  _heap_start = ALIGN(0x100000);
  _stack_top =  ALIGN(0x100000) ;
  . = _stack_top + 0x200000;
  _stack_pointer = .;
  end = .;
  _end = .;
}

