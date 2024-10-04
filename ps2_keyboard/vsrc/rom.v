module rom(
    input [7:0] addr,
    output reg [7:0] val
);

always@(*)
    case(addr)
        8'h15: val = 8'h71;
        8'h1c: val = 8'h61;
        8'h1a: val = 8'h7a;
        8'h1d: val = 8'h77;
        8'h1b: val = 8'h73;
        8'h22: val = 8'h78;
        8'h24: val = 8'h65;
        8'h23: val = 8'h64;
        8'h21: val = 8'h63;
        8'h2d: val = 8'h72;
        8'h2b: val = 8'h66;
        8'h2a: val = 8'h76;
        8'h2c: val = 8'h74;
        8'h34: val = 8'h67;
        8'h32: val = 8'h62;
        8'h35: val = 8'h79;
        8'h33: val = 8'h68;
        8'h31: val = 8'h6e;
        8'h3c: val = 8'h75;
        8'h3b: val = 8'h6a;
        8'h3a: val = 8'h6d;
        8'h43: val = 8'h69;
        8'h42: val = 8'h6b;
        8'h41: val = 8'h3c;
        8'h44: val = 8'h6f;
        8'h4b: val = 8'h6c;
        8'h49: val = 8'h3e;
        8'h4d: val = 8'h70;
        8'h4c: val = 8'h3b;
        8'h4a: val = 8'h2f;
        8'h54: val = 8'h5b;
        8'h52: val = 8'h27;
        8'h5b: val = 8'h3d;
        //num
        8'h0e: val = 8'h7e;
        8'h16: val = 8'h31;
        8'h1e: val = 8'h32;
        8'h26: val = 8'h33;
        8'h25: val = 8'h34;
        8'h2e: val = 8'h35;
        8'h36: val = 8'h36;
        8'h3d: val = 8'h37;
        8'h3e: val = 8'h38;
        8'h46: val = 8'h39;
        8'h45: val = 8'h30;
        8'h4e: val = 8'h2d;
        8'h55: val = 8'h2b;
        8'h5d: val = 8'h7c;
        default: val = 8'h00;
    endcase


endmodule