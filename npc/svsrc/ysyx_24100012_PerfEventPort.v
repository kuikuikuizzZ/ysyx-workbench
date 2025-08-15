
     import "DPI-C" function void perf_event_lsu(input int storeCount, input int loadCount);
     import "DPI-C" function void perf_event_ifu(input int instFetchCount);
     import "DPI-C" function void perf_event_wbu(input int wbCount);
     import "DPI-C" function void perf_event_ctrl(input int csrCount, input int loadCount, input int storeCount, 
         input int itype, input int rtype, input int jtype,  input int utype, input int other);
     module ysyx_24100012_PerfEventPort(
        input clock,
        input reset,
        input finish,
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
        input [31:0] ctl_port_csrCount,
        input [31:0] ctl_port_storeCount,
        input [31:0] ctl_port_loadCount,
        input [31:0] ctl_port_itypeCount,
        input [31:0] ctl_port_rtypeCount,
        input [31:0] ctl_port_jtypeCount,
        input [31:0] ctl_port_utypeCount,
        input [31:0] ctl_port_otherCount
        );


        always @(posedge clock) begin
            if (finish) begin
                perf_event_ctrl(ctl_port_csrCount, ctl_port_storeCount, 
                    ctl_port_loadCount, ctl_port_itypeCount, ctl_port_rtypeCount,
                    ctl_port_jtypeCount,ctl_port_utypeCount, ctl_port_otherCount);
                perf_event_lsu(lsu_port_storeCount, lsu_port_loadCount);
                perf_event_wbu(wbu_port_wbCount);
                perf_event_ifu(ifu_port_instFetchCount);                
            end 
        end

     endmodule
     
