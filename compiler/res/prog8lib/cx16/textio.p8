; Prog8 definitions for the Text I/O and Screen routines for the CommanderX16
;
; Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
;
; indent format: TABS, size=8

%target cx16
%import syslib
%import conv


txt {

const ubyte DEFAULT_WIDTH = 80
const ubyte DEFAULT_HEIGHT = 60


sub clear_screen() {
    txt.chrout(147)
}

sub home() {
    txt.chrout(19)
}

sub nl() {
    txt.chrout('\n')
}

sub spc() {
    txt.chrout(' ')
}

asmsub column(ubyte col @A) clobbers(A, X, Y) {
    ; ---- set the cursor on the given column (starting with 0) on the current line
    %asm {{
        sec
        jsr  c64.PLOT
        tay
        clc
        jmp  c64.PLOT
    }}
}

asmsub  fill_screen (ubyte char @ A, ubyte color @ Y) clobbers(A)  {
	; ---- fill the character screen with the given fill character and character color.
	%asm {{
	    sty  _ly+1
        phx
        pha
        jsr  c64.SCREEN             ; get dimensions in X/Y
        txa
        lsr  a
        lsr  a
        sta  _lx+1
        stz  cx16.VERA_CTRL
        lda  #%00010000
        sta  cx16.VERA_ADDR_H       ; enable auto increment by 1, bank 0.
        stz  cx16.VERA_ADDR_L       ; start at (0,0)
        stz  cx16.VERA_ADDR_M
        pla
_lx     ldx  #0                     ; modified
        phy
_ly     ldy  #1                     ; modified
-       sta  cx16.VERA_DATA0
        sty  cx16.VERA_DATA0
        sta  cx16.VERA_DATA0
        sty  cx16.VERA_DATA0
        sta  cx16.VERA_DATA0
        sty  cx16.VERA_DATA0
        sta  cx16.VERA_DATA0
        sty  cx16.VERA_DATA0
        dex
        bne  -
        ply
        dey
        beq  +
        stz  cx16.VERA_ADDR_L
        inc  cx16.VERA_ADDR_M       ; next line
        bra  _lx
+       plx
        rts
        }}
}

asmsub  clear_screenchars (ubyte char @ A) clobbers(Y)  {
	; ---- clear the character screen with the given fill character (leaves colors)
	;      (assumes screen matrix is at the default address)
	%asm {{
        phx
        pha
        jsr  c64.SCREEN             ; get dimensions in X/Y
        txa
        lsr  a
        lsr  a
        sta  _lx+1
        stz  cx16.VERA_CTRL
        lda  #%00100000
        sta  cx16.VERA_ADDR_H       ; enable auto increment by 2, bank 0.
        stz  cx16.VERA_ADDR_L       ; start at (0,0)
        stz  cx16.VERA_ADDR_M
        pla
_lx     ldx  #0                     ; modified
-       sta  cx16.VERA_DATA0
        sta  cx16.VERA_DATA0
        sta  cx16.VERA_DATA0
        sta  cx16.VERA_DATA0
        dex
        bne  -
        dey
        beq  +
        stz  cx16.VERA_ADDR_L
        inc  cx16.VERA_ADDR_M       ; next line
        bra  _lx
+       plx
        rts
        }}
}

asmsub  clear_screencolors (ubyte color @ A) clobbers(Y)  {
	; ---- clear the character screen colors with the given color (leaves characters).
	;      (assumes color matrix is at the default address)
	%asm {{
        phx
        sta  _la+1
        jsr  c64.SCREEN             ; get dimensions in X/Y
        txa
        lsr  a
        lsr  a
        sta  _lx+1
        stz  cx16.VERA_CTRL
        lda  #%00100000
        sta  cx16.VERA_ADDR_H       ; enable auto increment by 2, bank 0.
        lda  #1
        sta  cx16.VERA_ADDR_L       ; start at (1,0)
        stz  cx16.VERA_ADDR_M
_lx     ldx  #0                     ; modified
_la     lda  #0                     ; modified
-       sta  cx16.VERA_DATA0
        sta  cx16.VERA_DATA0
        sta  cx16.VERA_DATA0
        sta  cx16.VERA_DATA0
        dex
        bne  -
        dey
        beq  +
        lda  #1
        sta  cx16.VERA_ADDR_L
        inc  cx16.VERA_ADDR_M       ; next line
        bra  _lx
+       plx
        rts
        }}
}


ubyte[16] color_to_charcode = [$90,$05,$1c,$9f,$9c,$1e,$1f,$9e,$81,$95,$96,$97,$98,$99,$9a,$9b]

sub color (ubyte txtcol) {
    txtcol &= 15
    c64.CHROUT(color_to_charcode[txtcol])
}

sub color2 (ubyte txtcol, ubyte bgcol) {
    txtcol &= 15
    bgcol &= 15
    c64.CHROUT(color_to_charcode[bgcol])
    c64.CHROUT(1)       ; switch fg and bg colors
    c64.CHROUT(color_to_charcode[txtcol])
}

sub lowercase() {
    cx16.screen_set_charset(3, 0)  ; lowercase charset
}

sub uppercase() {
    cx16.screen_set_charset(2, 0)  ; uppercase charset
}

asmsub  scroll_left() clobbers(A, Y)  {
	; ---- scroll the whole screen 1 character to the left
	;      contents of the rightmost column are unchanged, you should clear/refill this yourself
	%asm {{
	    phx
	    jsr  c64.SCREEN
	    dex
	    stx  _lx+1
        dey
        sty  P8ZP_SCRATCH_B1    ; number of rows to scroll

_nextline
        stz  cx16.VERA_CTRL     ; data port 0: source column
        lda  #%00010000         ; auto increment 1
        sta  cx16.VERA_ADDR_H
        lda  #2
        sta  cx16.VERA_ADDR_L   ; begin in column 1
        ldy  P8ZP_SCRATCH_B1
        sty  cx16.VERA_ADDR_M
        lda  #1
        sta  cx16.VERA_CTRL     ; data port 1: destination column
        lda  #%00010000         ; auto increment 1
        sta  cx16.VERA_ADDR_H
        stz  cx16.VERA_ADDR_L
        sty  cx16.VERA_ADDR_M

_lx     ldx  #0                ; modified
-       lda  cx16.VERA_DATA0
        sta  cx16.VERA_DATA1    ; copy char
        lda  cx16.VERA_DATA0
        sta  cx16.VERA_DATA1    ; copy color
        dex
        bne  -
        dec  P8ZP_SCRATCH_B1
        bpl  _nextline

        lda  #0
        sta  cx16.VERA_CTRL
	    plx
	    rts
	}}
}

asmsub  scroll_right() clobbers(A)  {
	; ---- scroll the whole screen 1 character to the right
	;      contents of the leftmost column are unchanged, you should clear/refill this yourself
	%asm {{
	    phx
	    jsr  c64.SCREEN
	    dex
	    stx  _lx+1
	    txa
	    asl  a
	    dea
	    sta  _rcol+1
	    ina
	    ina
	    sta  _rcol2+1
        dey
        sty  P8ZP_SCRATCH_B1    ; number of rows to scroll

_nextline
        stz  cx16.VERA_CTRL     ; data port 0: source column
        lda  #%00011000         ; auto decrement 1
        sta  cx16.VERA_ADDR_H
_rcol   lda  #79*2-1             ; modified
        sta  cx16.VERA_ADDR_L   ; begin in rightmost column minus one
        ldy  P8ZP_SCRATCH_B1
        sty  cx16.VERA_ADDR_M
        lda  #1
        sta  cx16.VERA_CTRL     ; data port 1: destination column
        lda  #%00011000         ; auto decrement 1
        sta  cx16.VERA_ADDR_H
_rcol2  lda  #79*2+1            ; modified
        sta  cx16.VERA_ADDR_L
        sty  cx16.VERA_ADDR_M

_lx     ldx  #0                 ; modified
-       lda  cx16.VERA_DATA0
        sta  cx16.VERA_DATA1    ; copy char
        lda  cx16.VERA_DATA0
        sta  cx16.VERA_DATA1    ; copy color
        dex
        bne  -
        dec  P8ZP_SCRATCH_B1
        bpl  _nextline

        lda  #0
        sta  cx16.VERA_CTRL
	    plx
	    rts
	}}
}

asmsub  scroll_up() clobbers(A, Y)  {
	; ---- scroll the whole screen 1 character up
	;      contents of the bottom row are unchanged, you should refill/clear this yourself
	%asm {{
	colsToCopy = _nextline+1	; self-modify: write columns to copy directly into code
	rowsToCopy = P8ZP_SCRATCH_B1
	    phx
	    jsr  c64.SCREEN			; returns screen width in X (height in Y)
	    stx  colsToCopy
	    dey						; number of rows to copy = screen height - 1
        sty  rowsToCopy
        stz  cx16.VERA_CTRL		; data port 0 is source
        lda  #1
        sta  cx16.VERA_ADDR_M	; start at second line
        stz  cx16.VERA_ADDR_L	; ...at x-coord 0
        lda  #%00010000
        sta  cx16.VERA_ADDR_H	; enable auto increment (bit 3) by 1 (bits 4..7), bank 0 (bit 0)

        lda  #1
        sta  cx16.VERA_CTRL		; data port 1 is target
        stz  cx16.VERA_ADDR_M	; start at top line
        stz  cx16.VERA_ADDR_L	; ...at x-coord 0
        lda  #%00010000
        sta  cx16.VERA_ADDR_H	; enable auto increment (bit 3) by 1 (bits 4..7), bank 0 (bit 0)

_nextline
        ldx  #80        		; colsToCopy, modified at start
-       lda  cx16.VERA_DATA0
        sta  cx16.VERA_DATA1	; copy char
        lda  cx16.VERA_DATA0
        sta  cx16.VERA_DATA1	; copy color
        dex						; colsToCopy--
        bne  -
        dec  rowsToCopy
        beq  +
        stz  cx16.VERA_CTRL		; data port 0 (source)
        inc  cx16.VERA_ADDR_M	; y++
        stz  cx16.VERA_ADDR_L	; x = 0
        lda  #1
        sta  cx16.VERA_CTRL		; data port 1 (target)
        inc  cx16.VERA_ADDR_M	; y++
        stz  cx16.VERA_ADDR_L	; x = 0
        bra  _nextline

+       lda  #0
        sta  cx16.VERA_CTRL
	    plx
	    rts
	}}
}

asmsub  scroll_down() clobbers(A, Y)  {
	; ---- scroll the whole screen 1 character down
	;      contents of the top row are unchanged, you should refill/clear this yourself
	%asm {{
	    phx
	    jsr  c64.SCREEN
	    stx  _nextline+1
	    dey
        sty  P8ZP_SCRATCH_B1
        stz  cx16.VERA_CTRL         ; data port 0 is source
        dey
        sty  cx16.VERA_ADDR_M       ; start at line before bottom line
        stz  cx16.VERA_ADDR_L
        lda  #%00010000
        sta  cx16.VERA_ADDR_H       ; enable auto increment by 1, bank 0.

        lda  #1
        sta  cx16.VERA_CTRL         ; data port 1 is destination
        iny
        sty  cx16.VERA_ADDR_M       ; start at bottom line
        stz  cx16.VERA_ADDR_L
        lda  #%00010000
        sta  cx16.VERA_ADDR_H       ; enable auto increment by 1, bank 0.

_nextline
        ldx  #80        ; modified
-       lda  cx16.VERA_DATA0
        sta  cx16.VERA_DATA1        ; copy char
        lda  cx16.VERA_DATA0
        sta  cx16.VERA_DATA1        ; copy color
        dex
        bne  -
        dec  P8ZP_SCRATCH_B1
        beq  +
        stz  cx16.VERA_CTRL         ; data port 0
        stz  cx16.VERA_ADDR_L
        dec  cx16.VERA_ADDR_M
        lda  #1
        sta  cx16.VERA_CTRL         ; data port 1
        stz  cx16.VERA_ADDR_L
        dec  cx16.VERA_ADDR_M
        bra  _nextline

+       lda  #0
        sta  cx16.VERA_CTRL
	    plx
	    rts
	}}
}

romsub $FFD2 = chrout(ubyte char @ A)    ; for consistency. You can also use c64.CHROUT directly ofcourse.

asmsub  print (str text @ AY) clobbers(A,Y)  {
	; ---- print null terminated string from A/Y
	; note: the compiler contains an optimization that will replace
	;       a call to this subroutine with a string argument of just one char,
	;       by just one call to c64.CHROUT of that single char.
	%asm {{
		sta  P8ZP_SCRATCH_B1
		sty  P8ZP_SCRATCH_REG
		ldy  #0
-		lda  (P8ZP_SCRATCH_B1),y
		beq  +
		jsr  c64.CHROUT
		iny
		bne  -
+		rts
	}}
}

asmsub  print_ub0  (ubyte value @ A) clobbers(A,Y)  {
	; ---- print the ubyte in A in decimal form, with left padding 0s (3 positions total)
	%asm {{
		phx
		jsr  conv.ubyte2decimal
		pha
		tya
		jsr  c64.CHROUT
		pla
		jsr  c64.CHROUT
		txa
		jsr  c64.CHROUT
		plx
		rts
	}}
}

asmsub  print_ub  (ubyte value @ A) clobbers(A,Y)  {
	; ---- print the ubyte in A in decimal form, without left padding 0s
	%asm {{
		phx
		jsr  conv.ubyte2decimal
_print_byte_digits
		pha
		cpy  #'0'
		beq  +
		tya
		jsr  c64.CHROUT
		pla
		jsr  c64.CHROUT
		bra  _ones
+       pla
        cmp  #'0'
        beq  _ones
        jsr  c64.CHROUT
_ones   txa
		jsr  c64.CHROUT
		plx
		rts
	}}
}

asmsub  print_b  (byte value @ A) clobbers(A,Y)  {
	; ---- print the byte in A in decimal form, without left padding 0s
	%asm {{
		phx
		pha
		cmp  #0
		bpl  +
		lda  #'-'
		jsr  c64.CHROUT
+		pla
		jsr  conv.byte2decimal
		bra  print_ub._print_byte_digits
	}}
}

asmsub  print_ubhex  (ubyte value @ A, ubyte prefix @ Pc) clobbers(A,Y)  {
	; ---- print the ubyte in A in hex form (if Carry is set, a radix prefix '$' is printed as well)
	%asm {{
		phx
		bcc  +
		pha
		lda  #'$'
		jsr  c64.CHROUT
		pla
+		jsr  conv.ubyte2hex
		jsr  c64.CHROUT
		tya
		jsr  c64.CHROUT
		plx
		rts
	}}
}

asmsub  print_ubbin  (ubyte value @ A, ubyte prefix @ Pc) clobbers(A,Y)  {
	; ---- print the ubyte in A in binary form (if Carry is set, a radix prefix '%' is printed as well)
	%asm {{
		phx
		sta  P8ZP_SCRATCH_B1
		bcc  +
		lda  #'%'
		jsr  c64.CHROUT
+		ldy  #8
-		lda  #'0'
		asl  P8ZP_SCRATCH_B1
		bcc  +
		lda  #'1'
+		jsr  c64.CHROUT
		dey
		bne  -
		plx
		rts
	}}
}

asmsub  print_uwbin  (uword value @ AY, ubyte prefix @ Pc) clobbers(A,Y)  {
	; ---- print the uword in A/Y in binary form (if Carry is set, a radix prefix '%' is printed as well)
	%asm {{
		pha
		tya
		jsr  print_ubbin
		pla
		clc
		bra  print_ubbin
	}}
}

asmsub  print_uwhex  (uword value @ AY, ubyte prefix @ Pc) clobbers(A,Y)  {
	; ---- print the uword in A/Y in hexadecimal form (4 digits)
	;      (if Carry is set, a radix prefix '$' is printed as well)
	%asm {{
		pha
		tya
		jsr  print_ubhex
		pla
		clc
		bra  print_ubhex
	}}
}

asmsub  print_uw0  (uword value @ AY) clobbers(A,Y)  {
	; ---- print the uword in A/Y in decimal form, with left padding 0s (5 positions total)
	%asm {{
	    phx
		jsr  conv.uword2decimal
		ldy  #0
-		lda  conv.uword2decimal.decTenThousands,y
        beq  +
		jsr  c64.CHROUT
		iny
		bne  -
+		plx
		rts
	}}
}

asmsub  print_uw  (uword value @ AY) clobbers(A,Y)  {
	; ---- print the uword in A/Y in decimal form, without left padding 0s
	%asm {{
	    phx
		jsr  conv.uword2decimal
		plx
		ldy  #0
-		lda  conv.uword2decimal.decTenThousands,y
		beq  _allzero
		cmp  #'0'
		bne  _gotdigit
		iny
		bne  -

_gotdigit
		jsr  c64.CHROUT
		iny
		lda  conv.uword2decimal.decTenThousands,y
		bne  _gotdigit
		rts
_allzero
        lda  #'0'
        jmp  c64.CHROUT
	}}
}

asmsub  print_w  (word value @ AY) clobbers(A,Y)  {
	; ---- print the (signed) word in A/Y in decimal form, without left padding 0's
	%asm {{
		cpy  #0
		bpl  +
		pha
		lda  #'-'
		jsr  c64.CHROUT
		tya
		eor  #255
		tay
		pla
		eor  #255
		clc
		adc  #1
		bcc  +
		iny
+		bra  print_uw
	}}
}

asmsub  input_chars  (uword buffer @ AY) clobbers(A) -> ubyte @ Y  {
	; ---- Input a string (max. 80 chars) from the keyboard. Returns length in Y. (string is terminated with a 0 byte as well)
	;      It assumes the keyboard is selected as I/O channel!

	%asm {{
		sta  P8ZP_SCRATCH_W1
		sty  P8ZP_SCRATCH_W1+1
		ldy  #0				; char counter = 0
-		jsr  c64.CHRIN
		cmp  #$0d			; return (ascii 13) pressed?
		beq  +				; yes, end.
		sta  (P8ZP_SCRATCH_W1),y	; else store char in buffer
		iny
		bne  -
+		lda  #0
		sta  (P8ZP_SCRATCH_W1),y	; finish string with 0 byte
		rts

	}}
}

asmsub  setchr  (ubyte col @X, ubyte row @Y, ubyte character @A) clobbers(A)  {
	; ---- sets the character in the screen matrix at the given position
	%asm {{
            pha
            txa
            asl  a
            stz  cx16.VERA_CTRL
            stz  cx16.VERA_ADDR_H
            sta  cx16.VERA_ADDR_L
            sty  cx16.VERA_ADDR_M
            pla
            sta  cx16.VERA_DATA0
            rts
	}}
}

asmsub  getchr  (ubyte col @A, ubyte row @Y) -> ubyte @ A {
	; ---- get the character in the screen matrix at the given location
	%asm  {{
            asl  a
            stz  cx16.VERA_CTRL
            stz  cx16.VERA_ADDR_H
            sta  cx16.VERA_ADDR_L
            sty  cx16.VERA_ADDR_M
            lda  cx16.VERA_DATA0
            rts
	}}
}

asmsub  setclr  (ubyte col @X, ubyte row @Y, ubyte color @A) clobbers(A)  {
	; ---- set the color in A on the screen matrix at the given position
	;      note: on the CommanderX16 this allows you to set both Fg and Bg colors;
	;            use the high nybble in A to set the Bg color!
	%asm {{
            pha
            txa
            asl  a
            ina
            stz  cx16.VERA_CTRL
            stz  cx16.VERA_ADDR_H
            sta  cx16.VERA_ADDR_L
            sty  cx16.VERA_ADDR_M
            pla
            sta  cx16.VERA_DATA0
            rts
	}}
}

asmsub  getclr  (ubyte col @A, ubyte row @Y) -> ubyte @ A {
	; ---- get the color in the screen color matrix at the given location
	%asm  {{
            asl  a
            ina
            stz  cx16.VERA_CTRL
            stz  cx16.VERA_ADDR_H
            sta  cx16.VERA_ADDR_L
            sty  cx16.VERA_ADDR_M
            lda  cx16.VERA_DATA0
            rts
	}}
}

sub  setcc  (ubyte column, ubyte row, ubyte char, ubyte charcolor)  {
	; ---- set char+color at the given position on the screen
	;      note: color handling is the same as on the C64: it only sets the foreground color.
	;            use setcc2 if you want Cx-16 specific feature of setting both Bg+Fg colors.
	%asm {{
            phx
            lda  column
            asl  a
            tax
            ldy  row
            lda  charcolor
            and  #$0f
            sta  P8ZP_SCRATCH_B1
            stz  cx16.VERA_CTRL
            stz  cx16.VERA_ADDR_H
            stx  cx16.VERA_ADDR_L
            sty  cx16.VERA_ADDR_M
            lda  char
            sta  cx16.VERA_DATA0
            inx
            stz  cx16.VERA_ADDR_H
            stx  cx16.VERA_ADDR_L
            sty  cx16.VERA_ADDR_M
            lda  cx16.VERA_DATA0
            and  #$f0
            ora  P8ZP_SCRATCH_B1
            sta  cx16.VERA_DATA0
            plx
            rts
    }}
}

sub  setcc2  (ubyte column, ubyte row, ubyte char, ubyte colors)  {
	; ---- set char+color at the given position on the screen
	;      note: on the CommanderX16 this allows you to set both Fg and Bg colors;
	;            use the high nybble in A to set the Bg color!
	%asm {{
            phx
            lda  column
            asl  a
            tax
            ldy  row
            stz  cx16.VERA_CTRL
            stz  cx16.VERA_ADDR_H
            stx  cx16.VERA_ADDR_L
            sty  cx16.VERA_ADDR_M
            lda  char
            sta  cx16.VERA_DATA0
            inx
            stz  cx16.VERA_ADDR_H
            stx  cx16.VERA_ADDR_L
            sty  cx16.VERA_ADDR_M
            lda  colors
            sta  cx16.VERA_DATA0
            plx
            rts
    }}
}

asmsub  plot  (ubyte col @ Y, ubyte row @ A) clobbers(A) {
	; ---- safe wrapper around PLOT kernal routine, to save the X register.
	%asm  {{
		phx
		tax
		clc
		jsr  c64.PLOT
		plx
		rts
	}}
}

asmsub width() clobbers(X,Y) -> ubyte @A {
    ; -- returns the text screen width (number of columns)
    %asm {{
        jsr  c64.SCREEN
        txa
        rts
    }}
}

asmsub height() clobbers(X, Y) -> ubyte @A {
    ; -- returns the text screen height (number of rows)
    %asm {{
        jsr  c64.SCREEN
        tya
        rts
    }}
}

}
