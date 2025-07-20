ENTRY(_start)

MEMORY {
  mrom (rx): ORIGIN = 0x20000000, LENGTH = 4K
  sram (rwx): ORIGIN = 0x0f000000, LENGTH = 8K
}
SECTIONS {
  /* _pmem_start and _entry_offset are defined in LDFLAGS */
  . = _pmem_start + _entry_offset;
  .text : {
    *(entry)
    *(.text*)
  } > mrom AT> mrom
  _etext = .;
  .rodata : {
    *(.rodata*)
  } > mrom AT> mrom
  _erodata = .;
  _lma_data_start = LOADADDR (.data);
  .data : {
    _data_start = .;
    *(.data)
  }  > sram AT> mrom
  . = . ;
  _data_end = . ;
  _rom_bss_start = LOADADDR (.bss);
  .bss :  {
	  _bss_start = .;
    *(.bss*)
    *(.sbss*)
    *(.scommon)
  } > sram AT> mrom
  . = . ;
  _bss_end = .;
  _heap_start = ALIGN(0x400);
  _stack_top = ALIGN(0x1000);
  . = _stack_top + 0x1000;
  _stack_pointer = .;
  end = .;
  _end = .;
}
