package npc.common

import chisel3._
import chisel3.util._
object Constants extends MemoryOpConstants with 
    PrivilegedConstants with
    ScalarOpConstants with
    NPCProcConstants with
    RISCVConstants with
    AXI4BurstTypes with
    ExceptionCodesConstants
{
}