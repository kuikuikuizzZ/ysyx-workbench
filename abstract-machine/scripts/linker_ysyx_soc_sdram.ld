ENTRY(_start)

MEMORY {
  mrom (rx): ORIGIN = 0x20000000, LENGTH = 4K
  flash (rx): ORIGIN = 0x30000000, LENGTH = 0x01000000
  sram (rwx): ORIGIN = 0x0f000000, LENGTH = 8K
  sdram (rwx): ORIGIN = 0xa0000000, LENGTH = 32M
  psram (rwx): ORIGIN = 0x80000000, LENGTH = 4M
}
SECTIONS {
  /* _pmem_start and _entry_offset are defined in LDFLAGS */
  /* . = _pmem_start + _entry_offset;  */
  .entry : {
    _entry_start = .;
    KEEP(*(.entry))
  } > flash
  .first_boost : {
    _first_boost_start = .;
    KEEP(*(.first_boost))
  } > flash

  _lma_sec_boost_start = LOADADDR (.sec_boost);
  .sec_boost : {
    _sec_boost_start = .;
    KEEP(*(.sec_boost))
    KEEP(*(.bootutils))
  } > sdram AT> flash
  _sec_boost_end = .;
  . = .;
  . = ALIGN(4);
  _lma_code_start = LOADADDR (.text);
  .text : {
    _text_start = .;
    KEEP(*(.text._trm_init))
    *(.text*)
  } > sdram AT> flash

  _etext = .;
  . = ALIGN(4);
  .rodata  : {
    *(.rodata*)
  } > sdram AT> flash
  . = . ;
  _erodata = .;
  _lma_data_start = LOADADDR (.data);
  . = ALIGN(4);
  .data : {
    _data_start = .;
    *(.data)
  }  > sdram AT> flash
  . = . ;
  _data_end = . ;
  .bss :  {
	  _bss_start = .;
    *(.bss*)
    *(.sbss*)
    *(.scommon)
  } > sdram AT> flash
  _bss_end = .;
  /* _heap_start = ALIGN(0x100000);  */
  /* _stack_top =  ALIGN(0x100000) ; */
  /* . = _stack_top + 0x100000;      */ 
  . = 0x80000000;                  
  _heap_start = ALIGN(0x10000);
  _stack_top =  ALIGN(0x10000) ;
  . = _stack_top + 0x20000;
  _stack_pointer = .;
  end = .;
  _end = .;
}
