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
  . = ALIGN(4);
  .rodata  : {
    *(.rodata*)
  } > mrom AT> mrom
  _erodata = .;
  _lma_data_start = LOADADDR (.data);
  . = ALIGN(4);
  .data : {
    _data_start = .;
    *(.data)
  }  > sram AT> mrom
  . = . ;
  _data_end = . ;
  .bss :  {
	  _bss_start = .;
    *(.bss*)
    *(.sbss*)
    *(.scommon)
  } > sram AT> mrom
  . = ALIGN(4); 
  _bss_end = .;
  _heap_start = ALIGN(0x400);
  _stack_top = ALIGN(0x1000);
  . = _stack_top + 0x1000;
  _stack_pointer = .;
  end = .;
  _end = .;
}
