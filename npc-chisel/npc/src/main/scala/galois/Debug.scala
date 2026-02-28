package npc.galois

import chisel3._
import chisel3.util._
import npc.common._
import npc._
import npc.devices._

class WBUDebugPort(implicit val conf: Config) extends Bundle {
    val wbCount = Output(UInt(conf.perfCountBits.W))
}



class DebugPort() (implicit val conf: Config)extends BlackBox with HasBlackBoxInline{ 
    val io = IO(new Bundle {
        val clock       = Input(Clock())
        val reset       = Input(Bool())   
        val halt        = Input(Bool())
        val pc          = Input(UInt(32.W))
        val pc_next     = Input(UInt(32.W))
        val retire_pc   = Input(UInt(32.W))
        val next_retire_pc = Input(UInt(32.W))
        val instA       = Input(UInt(32.W))
        val instB       = Input(UInt(32.W))
        val retire      = Flipped(new CommitDebug())
        val rm          = Flipped(new RegMapDebug())
        // val lsu_port = Flipped(new LSUDebugPort()) 
     })

     setInline("DebugPort.v",
     """
     import "DPI-C" function void dpi_port_OOO(input int halt, input int pcA, input int pcB, input int instA, input int instB, input int retireA_pc, input int retireB_pc);
     import "DPI-C" function void retire_OOO( input int pcA, input int pcB,
                input int instA, input int instB, 
                input int readyA, input int readyB, 
                input int retire_next_pc, input int retire_mem_val, 
                input int retire_mem_addr);
     import "DPI-C" function void rm_dpi(input int rm_assign_count);

    //  import "DPI-C" function void lsu_port(input enable,  input fcn, input int lsu_port_typ,input int addr, input int data);
     module DebugPort(
        input clock,
        input reset,
        input halt, 
        input [31:0] pc,
        input [31:0] pc_next,
        input [31:0] retire_pc,
        input [31:0] next_retire_pc,
        input [31:0] instA,
        input [31:0] instB,
        input [31:0] retire_pcA,
        input [31:0] retire_pcB,
        input [31:0] retire_instA,
        input [31:0] retire_instB,
        input retire_readyA,
        input retire_readyB,
        input [31:0] retire_next_pc,
        input retire_mem_val,
        input [31:0] retire_mem_addr,
        input [31:0] rm_assign_count
        // input [31:0] lsu_port_addr,
        // input [31:0] lsu_port_rdata,
        // input [31:0] lsu_port_wdata,
        // input [31:0] lsu_port_storeCount,
        // input [31:0] lsu_port_loadCount,
        // input lsu_port_mem_en,
        // input lsu_port_fcn,
        // input lsu_port_valid,
        // input [1:0]  lsu_port_typ
        );

        wire [31:0] expand_halt = {31'b0,halt};
        always @(*) begin
            dpi_port_OOO(expand_halt, pc, pc_next, instA, instB, retire_pc, next_retire_pc);
        end

        wire [31:0] expand_readyA = {31'b0,retire_readyA};
        wire [31:0] expand_readyB = {31'b0,retire_readyB};
        wire [31:0] expand_retire_mem_val = {31'b0,retire_mem_val};
        always @(*) begin
            retire_OOO(retire_pcA, retire_pcB, 
                retire_instA, retire_instB, 
                expand_readyA, expand_readyB, 
                retire_next_pc, expand_retire_mem_val, retire_mem_addr);
        end

        always @(*) begin
            rm_dpi(rm_assign_count);
        end
        // wire [31:0] expand_typ   = {30'b0,lsu_port_typ};
        // always @(posedge clock) begin
        //     if (lsu_port_mem_en && lsu_port_fcn == 1'b1) begin
        //         lsu_port(lsu_port_valid && lsu_port_mem_en,lsu_port_fcn,expand_typ, lsu_port_addr, lsu_port_wdata);
        //     end else if (lsu_port_valid && lsu_port_fcn == 1'b0) begin
        //         lsu_port(lsu_port_valid,lsu_port_fcn,expand_typ, lsu_port_addr, lsu_port_rdata);
        //     end else begin
        //         lsu_port(1'd0,1'd0,32'd0,32'd0,32'd0);
        //     end
        // end
     endmodule
     """.stripMargin
     )

}

class PerfEventPort() (implicit val conf: Config)extends BlackBox with HasBlackBoxInline{ 
     val io = IO(new Bundle {
        val clock       = Input(Clock())
        val reset       = Input(Bool()) 
        val lsu_port    = Flipped(new LSUDebugPort()) 
        val ifu_port    = Flipped(new IFUDebugPort())
        val wbu_port    = Flipped(new WBUDebugPort())
        val ctl_port    = Flipped(new CtrlDebugPort())
        val exu_port    = Flipped(new EXUDebugPort())
     })

     setInline("PerfEventPort.v",
     """
     import "DPI-C" function void perf_event_lsu(input int storeCount, input int loadCount);
     import "DPI-C" function void perf_event_ifu(input int instFetchCount);
     import "DPI-C" function void perf_event_icache(input int hit,input int miss);
     import "DPI-C" function void perf_event_wbu(input int wbCount);
     import "DPI-C" function void perf_event_ctrl(input int csrCount, input int loadCount, input int storeCount, 
         input int itype, input int rtype, input int jtype, input int btype, input int utype, input int other);
     import "DPI-C" function void perf_event_exe(input int predict_wrong, input int target_wrong, input int br_wrong,input int predict_hit,input int predict_count);
     module PerfEventPort(
        input clock,
        input reset,
        input ifu_port_valid,
        input [31:0] lsu_port_addr,
        input [31:0] lsu_port_rdata,
        input [31:0] lsu_port_wdata,
        input [1:0]  lsu_port_typ,
        input lsu_port_mem_en,
        input lsu_port_fcn,
        input lsu_port_valid,
        input [31:0] lsu_port_storeCount,
        input [31:0] lsu_port_loadCount,
        input [31:0] wbu_port_wbCount,
        input [31:0] ifu_port_instFetchCount,
        input [31:0] ifu_port_icache_hit_cnt,
        input [31:0] ifu_port_icache_miss_cnt,        
        input [31:0] ctl_port_csrCount,
        input [31:0] ctl_port_storeCount,
        input [31:0] ctl_port_loadCount,
        input [31:0] ctl_port_itypeCount,
        input [31:0] ctl_port_rtypeCount,
        input [31:0] ctl_port_jtypeCount,
        input [31:0] ctl_port_btypeCount,
        input [31:0] ctl_port_utypeCount,
        input [31:0] ctl_port_otherCount,
        input [31:0] exu_port_predict_wrong,
        input [31:0] exu_port_target_wrong,
        input [31:0] exu_port_br_wrong,
        input [31:0] exu_port_predict_hit,
        input [31:0] exu_port_predict_count
        );


        always @(posedge clock) begin
            perf_event_ctrl(ctl_port_csrCount, ctl_port_storeCount, 
                ctl_port_loadCount, ctl_port_itypeCount, ctl_port_rtypeCount,
                ctl_port_jtypeCount,ctl_port_btypeCount, ctl_port_utypeCount, ctl_port_otherCount);
            
            perf_event_exe(exu_port_predict_wrong, exu_port_target_wrong, exu_port_br_wrong,exu_port_predict_hit,exu_port_predict_count);
            perf_event_wbu(wbu_port_wbCount);
            if (lsu_port_valid)
                perf_event_lsu(lsu_port_storeCount, lsu_port_loadCount);
            if (ifu_port_valid) begin
                perf_event_ifu(ifu_port_instFetchCount);   
                perf_event_icache(ifu_port_icache_hit_cnt,ifu_port_icache_miss_cnt);             
            end

        end

     endmodule
     """.stripMargin
     )
}

class GPRPort(implicit val conf: Config) extends BlackBox with HasBlackBoxInline{ 
    val io = IO(new Bundle { 
        val gpr = Input(Vec(32,UInt(conf.xlen.W)))
    })
    setInline("GPRPort.v",
    """
    import "DPI-C" function void dpi_gpr(input int regfile_array[]);

    module GPRPort(
        input [31:0] gpr_0,
        input [31:0] gpr_1,
        input [31:0] gpr_2,
        input [31:0] gpr_3,
        input [31:0] gpr_4,
        input [31:0] gpr_5,
        input [31:0] gpr_6,
        input [31:0] gpr_7,
        input [31:0] gpr_8,
        input [31:0] gpr_9,
        input [31:0] gpr_10,
        input [31:0] gpr_11,
        input [31:0] gpr_12,
        input [31:0] gpr_13,
        input [31:0] gpr_14,
        input [31:0] gpr_15,
        input [31:0] gpr_16,
        input [31:0] gpr_17,
        input [31:0] gpr_18,
        input [31:0] gpr_19,
        input [31:0] gpr_20,
        input [31:0] gpr_21,
        input [31:0] gpr_22,
        input [31:0] gpr_23,
        input [31:0] gpr_24,
        input [31:0] gpr_25,
        input [31:0] gpr_26,
        input [31:0] gpr_27,
        input [31:0] gpr_28,
        input [31:0] gpr_29,
        input [31:0] gpr_30,
        input [31:0] gpr_31
        );
        integer  gpr_array [31:0];
        assign gpr_array[0]  = gpr_0;
        assign gpr_array[1]  = gpr_1;
        assign gpr_array[2]  = gpr_2;
        assign gpr_array[3]  = gpr_3;
        assign gpr_array[4]  = gpr_4;
        assign gpr_array[5]  = gpr_5;   
        assign gpr_array[6]  = gpr_6;
        assign gpr_array[7]  = gpr_7;
        assign gpr_array[8]  = gpr_8;
        assign gpr_array[9]  = gpr_9;   
        assign gpr_array[10] = gpr_10;  
        assign gpr_array[11] = gpr_11;
        assign gpr_array[12] = gpr_12;
        assign gpr_array[13] = gpr_13;
        assign gpr_array[14] = gpr_14;
        assign gpr_array[15] = gpr_15;
        assign gpr_array[16] = gpr_16; 
        assign gpr_array[17] = gpr_17;
        assign gpr_array[18] = gpr_18;
        assign gpr_array[19] = gpr_19;  
        assign gpr_array[20] = gpr_20;
        assign gpr_array[21] = gpr_21;
        assign gpr_array[22] = gpr_22; 
        assign gpr_array[23] = gpr_23;
        assign gpr_array[24] = gpr_24;
        assign gpr_array[25] = gpr_25; 
        assign gpr_array[26] = gpr_26;
        assign gpr_array[27] = gpr_27;
        assign gpr_array[28] = gpr_28;
        assign gpr_array[29] = gpr_29;  
        assign gpr_array[30] = gpr_30;
        assign gpr_array[31] = gpr_31;  

        always @(*) begin
            dpi_gpr(gpr_array);
        end
        endmodule
    """)
}