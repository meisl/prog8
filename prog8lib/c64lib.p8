; Prog8 definitions for the Commodore-64
; Including memory registers, I/O registers, Basic and Kernal subroutines.
;
; Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
;
; indent format: TABS, size=8


~ c64 {
		memory  ubyte  SCRATCH_ZPB1	= $02		; scratch byte 1 in ZP
		memory  ubyte  SCRATCH_ZPREG	= $03		; scratch register in ZP
		memory  ubyte  SCRATCH_ZPREGX	= $fa		; temp storage for X register (stack pointer)
		memory  uword  SCRATCH_ZPWORD1	= $fb		; scratch word in ZP ($fb/$fc)
		memory  uword  SCRATCH_ZPWORD2	= $fd		; scratch word in ZP ($fd/$fe)


		memory  ubyte  TIME_HI		= $a0		; software jiffy clock, hi byte
		memory  ubyte  TIME_MID		= $a1		;  .. mid byte
		memory  ubyte  TIME_LO		= $a2		;    .. lo byte. Updated by IRQ every 1/60 sec
		memory  ubyte  STKEY		= $91		; various keyboard statuses (updated by IRQ)
		memory  ubyte  SFDX		= $cb		; current key pressed (matrix value) (updated by IRQ)

		memory  ubyte  COLOR		= $0286		; cursor color
		memory  ubyte  HIBASE		= $0288		; screen base address / 256 (hi-byte of screen memory address)
		memory  uword  CINV		= $0314		; IRQ vector
		memory  uword  NMI_VEC		= $FFFA		; 6502 nmi vector, determined by the kernal if banked in
		memory  uword  RESET_VEC	= $FFFC		; 6502 reset vector, determined by the kernal if banked in
		memory  uword  IRQ_VEC		= $FFFE		; 6502 interrupt vector, determined by the kernal if banked in

		; the default addresses for the character screen chars and colors
		const   uword  Screen		= $0400		; @todo matrix/array? needs to support array size > 255
		const   uword  Colors		= $d800		; @todo matrix/array? needs to support array size > 255
		
		; the default locations of the 8 sprite pointers (store address of sprite / 64)
		memory  ubyte  SPRPTR0		= 2040
		memory  ubyte  SPRPTR1		= 2041
		memory  ubyte  SPRPTR2		= 2042
		memory  ubyte  SPRPTR3		= 2043
		memory  ubyte  SPRPTR4		= 2044
		memory  ubyte  SPRPTR5		= 2045
		memory  ubyte  SPRPTR6		= 2046
		memory  ubyte  SPRPTR7		= 2047
 

; ---- VIC-II registers ----

		memory  ubyte SP0X		= $d000
		memory  ubyte SP0Y		= $d001
		memory  ubyte SP1X		= $d002
		memory  ubyte SP1Y		= $d003
		memory  ubyte SP2X		= $d004
		memory  ubyte SP2Y		= $d005
		memory  ubyte SP3X		= $d006
		memory  ubyte SP3Y		= $d007
		memory  ubyte SP4X		= $d008
		memory  ubyte SP4Y		= $d009
		memory  ubyte SP5X		= $d00a
		memory  ubyte SP5Y		= $d00b
		memory  ubyte SP6X		= $d00c
		memory  ubyte SP6Y		= $d00d
		memory  ubyte SP7X		= $d00e
		memory  ubyte SP7Y		= $d00f

		memory  ubyte MSIGX		= $d010
		memory  ubyte SCROLY		= $d011
		memory  ubyte RASTER		= $d012
		memory  ubyte LPENX		= $d013
		memory  ubyte LPENY		= $d014
		memory  ubyte SPENA		= $d015
		memory  ubyte SCROLX		= $d016
		memory  ubyte YXPAND		= $d017
		memory  ubyte VMCSB		= $d018
		memory  ubyte VICIRQ		= $d019
		memory  ubyte IREQMASK		= $d01a
		memory  ubyte SPBGPR		= $d01b
		memory  ubyte SPMC		= $d01c
		memory  ubyte XXPAND		= $d01d
		memory  ubyte SPSPCL		= $d01e
		memory  ubyte SPBGCL		= $d01f

		memory  ubyte EXTCOL		= $d020		; border color
		memory  ubyte BGCOL0		= $d021		; screen color
		memory  ubyte BGCOL1		= $d022
		memory  ubyte BGCOL2		= $d023
		memory  ubyte BGCOL4		= $d024
		memory  ubyte SPMC0		= $d025
		memory  ubyte SPMC1		= $d026
		memory  ubyte SP0COL		= $d027
		memory  ubyte SP1COL		= $d028
		memory  ubyte SP2COL		= $d029
		memory  ubyte SP3COL		= $d02a
		memory  ubyte SP4COL		= $d02b
		memory  ubyte SP5COL		= $d02c
		memory  ubyte SP6COL		= $d02d
		memory  ubyte SP7COL		= $d02e

; ---- end of VIC-II registers ----

; ---- CIA 1 & 2 registers ----

		memory  ubyte CIA1PRA		= $DC00		; CIA 1 DRA, keyboard column drive
		memory  ubyte CIA1PRB		= $DC01		; CIA 1 DRB, keyboard row port
		memory  ubyte CIA1DDRA		= $DC02		; CIA 1 DDRA, keyboard column
		memory  ubyte CIA1DDRB		= $DC03		; CIA 1 DDRB, keyboard row
		memory  ubyte CIA1TALO		= $DC04		; CIA 1 timer A low byte
		memory  ubyte CIA1TAHI		= $DC05		; CIA 1 timer A high byte
		memory  ubyte CIA1TBLO		= $DC06		; CIA 1 timer B low byte
		memory  ubyte CIA1TBHI		= $DC07		; CIA 1 timer B high byte
		memory  ubyte CIA1TOD10		= $DC08		; time of day, 1/10 sec.
		memory  ubyte CIA1TODS		= $DC09		; time of day, seconds
		memory  ubyte CIA1TODM		= $DC0A		; time of day, minutes
		memory  ubyte CIA1TODH		= $DC0B		; time of day, hours
		memory  ubyte CIA1SDR		= $DC0C		; Serial Data Register
		memory  ubyte CIA1ICR		= $DC0D
		memory  ubyte CIA1CRA		= $DC0E
		memory  ubyte CIA1CRB		= $DC0F

		memory  ubyte CIA2PRA		= $DD00		; CIA 2 DRA, serial port and video address
		memory  ubyte CIA2PRB		= $DD01		; CIA 2 DRB, RS232 port / USERPORT
		memory  ubyte CIA2DDRA		= $DD02		; CIA 2 DDRA, serial port and video address
		memory  ubyte CIA2DDRB		= $DD03		; CIA 2 DDRB, RS232 port / USERPORT
		memory  ubyte CIA2TALO		= $DD04		; CIA 2 timer A low byte
		memory  ubyte CIA2TAHI		= $DD05		; CIA 2 timer A high byte
		memory  ubyte CIA2TBLO		= $DD06		; CIA 2 timer B low byte
		memory  ubyte CIA2TBHI		= $DD07		; CIA 2 timer B high byte
		memory  ubyte CIA2TOD10		= $DD08		; time of day, 1/10 sec.
		memory  ubyte CIA2TODS		= $DD09		; time of day, seconds
		memory  ubyte CIA2TODM		= $DD0A		; time of day, minutes
		memory  ubyte CIA2TODH		= $DD0B		; time of day, hours
		memory  ubyte CIA2SDR		= $DD0C		; Serial Data Register
		memory  ubyte CIA2ICR		= $DD0D
		memory  ubyte CIA2CRA		= $DD0E
		memory  ubyte CIA2CRB		= $DD0F

; ---- end of CIA registers ----

; @todo SID sound chip registers



; ---- C64 basic and kernal ROM float constants and functions ----

		; note: the fac1 and fac2 are working registers and take 6 bytes each,
		; floats in memory  (and rom) are stored in 5-byte MFLPT packed format.

		; constants in five-byte "mflpt" format in the BASIC ROM
		memory  float  FL_PIVAL		= $aea8  ; 3.1415926...
		memory  float  FL_N32768	= $b1a5  ; -32768
		memory  float  FL_FONE		= $b9bc  ; 1
		memory  float  FL_SQRHLF	= $b9d6  ; SQR(2) / 2
		memory  float  FL_SQRTWO	= $b9db  ; SQR(2)
		memory  float  FL_NEGHLF	= $b9e0  ; -.5
		memory  float  FL_LOG2		= $b9e5  ; LOG(2)
		memory  float  FL_TENC		= $baf9  ; 10
		memory  float  FL_NZMIL		= $bdbd  ; 1e9 (1 billion)
		memory  float  FL_FHALF		= $bf11  ; .5
		memory  float  FL_LOGEB2	= $bfbf  ; 1 / LOG(2)
		memory  float  FL_PIHALF	= $e2e0  ; PI / 2
		memory  float  FL_TWOPI		= $e2e5  ; 2 * PI
		memory  float  FL_FR4		= $e2ea  ; .25


; note: fac1/2 might get clobbered even if not mentioned in the function's name.
; note: for subtraction and division, the left operand is in fac2, the right operand in fac1.

; checked functions below:
asmsub	MOVFM		(uword mflpt @ AY) -> clobbers(A,Y) -> ()	= $bba2		; load mflpt value from memory  in A/Y into fac1
asmsub	FREADMEM	() -> clobbers(A,Y) -> ()			= $bba6		; load mflpt value from memory  in $22/$23 into fac1
asmsub	CONUPK		(uword mflpt @ AY) -> clobbers(A,Y) -> ()	= $ba8c		; load mflpt value from memory  in A/Y into fac2
asmsub	FAREADMEM	() -> clobbers(A,Y) -> ()			= $ba90		; load mflpt value from memory  in $22/$23 into fac2
asmsub	MOVFA		() -> clobbers(A,X) -> ()			= $bbfc		; copy fac2 to fac1
asmsub	MOVAF		() -> clobbers(A,X) -> ()			= $bc0c		; copy fac1 to fac2  (rounded)
asmsub	MOVEF		() -> clobbers(A,X) -> ()			= $bc0f		; copy fac1 to fac2
asmsub	MOVMF		(uword mflpt @ XY) -> clobbers(A,Y) -> ()	= $bbd4		; store fac1 to memory  X/Y as 5-byte mflpt

; fac1-> signed word in Y/A (might throw ILLEGAL QUANTITY)
; (tip: use c64flt.FTOSWRDAY to get A/Y output; lo/hi switched to normal little endian order)
asmsub	FTOSWORDYA	() -> clobbers(X) -> (ubyte @ Y, ubyte @ A)	= $b1aa

; fac1 -> unsigned word in Y/A (might throw ILLEGAL QUANTITY) (result also in $14/15)
; (tip: use c64flt.GETADRAY to get A/Y output; lo/hi switched to normal little endian order)
asmsub	GETADR		() -> clobbers(X) -> (ubyte @ Y, ubyte @ A)	= $b7f7

asmsub	QINT		() -> clobbers(A,X,Y) -> ()			= $bc9b		; fac1 -> 4-byte signed integer in 98-101 ($62-$65), with the MSB FIRST.
asmsub	AYINT		() -> clobbers(A,X,Y) -> ()			= $b1bf		; fac1-> signed word in 100-101 ($64-$65) MSB FIRST. (might throw ILLEGAL QUANTITY)

; GIVAYF: signed word in Y/A (note different lsb/msb order) -> float in fac1
; (tip: use c64flt.GIVAYFAY to use A/Y input; lo/hi switched to normal order)
; there is also c64flt.GIVUAYFAY - unsigned word in A/Y (lo/hi) to fac1
; there is also c64flt.FREADS32  that reads from 98-101 ($62-$65) MSB FIRST
; there is also c64flt.FREADUS32  that reads from 98-101 ($62-$65) MSB FIRST
; there is also c64flt.FREADS24AXY  that reads signed int24 into fac1 from A/X/Y (lo/mid/hi bytes)
asmsub	GIVAYF		(ubyte lo @ Y, ubyte hi @ A) -> clobbers(A,X,Y) -> ()	= $b391

asmsub	FREADUY		(ubyte value @ Y) -> clobbers(A,X,Y) -> ()	= $b3a2		; 8 bit unsigned Y -> float in fac1
asmsub	FREADSA		(byte value @ A) -> clobbers(A,X,Y) -> ()	= $bc3c		; 8 bit signed A -> float in fac1
asmsub	FREADSTR	(ubyte length @ A) -> clobbers(A,X,Y) -> ()	= $b7b5		; str -> fac1, $22/23 must point to string, A=string length
asmsub	FPRINTLN	() -> clobbers(A,X,Y) -> ()			= $aabc		; print string of fac1, on one line (= with newline) destroys fac1.  (consider FOUT + STROUT as well)
asmsub	FOUT		() -> clobbers(X) -> (uword @ AY)		= $bddd		; fac1 -> string, address returned in AY ($0100)

asmsub	FADDH		() -> clobbers(A,X,Y) -> ()			= $b849		; fac1 += 0.5, for rounding- call this before INT
asmsub	MUL10		() -> clobbers(A,X,Y) -> ()			= $bae2		; fac1 *= 10
asmsub	DIV10		() -> clobbers(A,X,Y) -> ()			= $bafe		; fac1 /= 10 , CAUTION: result is always positive!
asmsub	FCOMP		(uword mflpt @ AY) -> clobbers(X,Y) -> (ubyte @ A) = $bc5b		; A = compare fac1 to mflpt in A/Y, 0=equal 1=fac1 is greater, 255=fac1 is less than

asmsub	FADDT		() -> clobbers(A,X,Y) -> ()			= $b86a		; fac1 += fac2
asmsub	FADD		(uword mflpt @ AY) -> clobbers(A,X,Y) -> ()	= $b867		; fac1 += mflpt value from A/Y
asmsub	FSUBT		() -> clobbers(A,X,Y) -> ()			= $b853		; fac1 = fac2-fac1   mind the order of the operands
asmsub	FSUB		(uword mflpt @ AY) -> clobbers(A,X,Y) -> ()	= $b850		; fac1 = mflpt from A/Y - fac1
asmsub	FMULTT 		() -> clobbers(A,X,Y) -> ()			= $ba2b		; fac1 *= fac2
asmsub	FMULT		(uword mflpt @ AY) -> clobbers(A,X,Y) -> ()	= $ba28		; fac1 *= mflpt value from A/Y
asmsub	FDIVT 		() -> clobbers(A,X,Y) -> ()			= $bb12		; fac1 = fac2/fac1  (remainder in fac2)  mind the order of the operands
asmsub	FDIV  		(uword mflpt @ AY) -> clobbers(A,X,Y) -> ()	= $bb0f		; fac1 = mflpt in A/Y / fac1  (remainder in fac2)
asmsub	FPWRT		() -> clobbers(A,X,Y) -> ()			= $bf7b		; fac1 = fac2 ** fac1
asmsub	FPWR		(uword mflpt @ AY) -> clobbers(A,X,Y) -> ()	= $bf78		; fac1 = fac2 ** mflpt from A/Y

asmsub	NOTOP		() -> clobbers(A,X,Y) -> ()			= $aed4		; fac1 = NOT(fac1)
asmsub	INT		() -> clobbers(A,X,Y) -> ()			= $bccc		; INT() truncates, use FADDH first to round instead of trunc
asmsub	LOG		() -> clobbers(A,X,Y) -> ()			= $b9ea		; fac1 = LN(fac1)  (natural log)
asmsub	SGN		() -> clobbers(A,X,Y) -> ()			= $bc39		; fac1 = SGN(fac1), result of SIGN (-1, 0 or 1)
asmsub	SIGN		() -> clobbers() -> (ubyte @ A)			= $bc2b		; SIGN(fac1) to A, $ff, $0, $1 for negative, zero, positive
asmsub	ABS		() -> clobbers() -> ()				= $bc58		; fac1 = ABS(fac1)
asmsub	SQR		() -> clobbers(A,X,Y) -> ()			= $bf71		; fac1 = SQRT(fac1)
asmsub	SQRA		() -> clobbers(A,X,Y) -> ()			= $bf74		; fac1 = SQRT(fac2)
asmsub	EXP		() -> clobbers(A,X,Y) -> ()			= $bfed		; fac1 = EXP(fac1)  (e ** fac1)
asmsub	NEGOP		() -> clobbers(A) -> ()				= $bfb4		; switch the sign of fac1
asmsub	RND		() -> clobbers(A,X,Y) -> ()			= $e097		; fac1 = RND(fac1) float random number generator
asmsub	COS		() -> clobbers(A,X,Y) -> ()			= $e264		; fac1 = COS(fac1)
asmsub	SIN		() -> clobbers(A,X,Y) -> ()			= $e26b		; fac1 = SIN(fac1)
asmsub	TAN		() -> clobbers(A,X,Y) -> ()			= $e2b4		; fac1 = TAN(fac1)
asmsub	ATN		() -> clobbers(A,X,Y) -> ()			= $e30e		; fac1 = ATN(fac1)


; ---- C64 basic routines ----

asmsub	CLEARSCR	() -> clobbers(A,X,Y) -> ()		= $E544		; clear the screen
asmsub	HOMECRSR	() -> clobbers(A,X,Y) -> ()		= $E566		; cursor to top left of screen


; ---- end of C64 basic routines ----



; ---- C64 kernal routines ----

asmsub	STROUT   (uword strptr @ AY) -> clobbers(A, X, Y) -> ()	= $AB1E		; print null-terminated string (a bit slow, see if you can use c64scr.print_string instead)
asmsub	IRQDFRT  () -> clobbers(A,X,Y) -> ()			= $EA31		; default IRQ routine
asmsub	IRQDFEND () -> clobbers(A,X,Y) -> ()			= $EA81		; default IRQ end/cleanup
asmsub	CINT     () -> clobbers(A,X,Y) -> ()			= $FF81		; (alias: SCINIT) initialize screen editor and video chip
asmsub	IOINIT   () -> clobbers(A, X) -> ()			= $FF84		; initialize I/O devices (CIA, SID, IRQ)
asmsub	RAMTAS   () -> clobbers(A,X,Y) -> ()			= $FF87		; initialize RAM, tape buffer, screen
asmsub	RESTOR   () -> clobbers(A,X,Y) -> ()			= $FF8A		; restore default I/O vectors
asmsub	VECTOR   (ubyte dir @ Pc, uword userptr @ XY) -> clobbers(A,Y) -> ()	= $FF8D		; read/set I/O vector table
asmsub	SETMSG   (ubyte value @ A) -> clobbers() -> ()		= $FF90		; set Kernal message control flag
asmsub	SECOND   (ubyte address @ A) -> clobbers(A) -> ()	= $FF93		; (alias: LSTNSA) send secondary address after LISTEN
asmsub	TKSA     (ubyte address @ A) -> clobbers(A) -> ()	= $FF96		; (alias: TALKSA) send secondary address after TALK
asmsub	MEMTOP   (ubyte dir @ Pc, uword address @ XY) -> clobbers() -> (uword @ XY)	= $FF99		; read/set top of memory  pointer
asmsub	MEMBOT   (ubyte dir @ Pc, uword address @ XY) -> clobbers() -> (uword @ XY)	= $FF9C		; read/set bottom of memory  pointer
asmsub	SCNKEY   () -> clobbers(A,X,Y) -> ()			= $FF9F		; scan the keyboard
asmsub	SETTMO   (ubyte timeout @ A) -> clobbers() -> ()	= $FFA2		; set time-out flag for IEEE bus
asmsub	ACPTR    () -> clobbers() -> (ubyte @ A)			= $FFA5		; (alias: IECIN) input byte from serial bus
asmsub	CIOUT    (ubyte databyte @ A) -> clobbers() -> ()	= $FFA8		; (alias: IECOUT) output byte to serial bus
asmsub	UNTLK    () -> clobbers(A) -> ()			= $FFAB		; command serial bus device to UNTALK
asmsub	UNLSN    () -> clobbers(A) -> ()			= $FFAE		; command serial bus device to UNLISTEN
asmsub	LISTEN   (ubyte device @ A) -> clobbers(A) -> ()	= $FFB1		; command serial bus device to LISTEN
asmsub	TALK     (ubyte device @ A) -> clobbers(A) -> ()	= $FFB4		; command serial bus device to TALK
asmsub	READST   () -> clobbers() -> (ubyte @ A)			= $FFB7		; read I/O status word
asmsub	SETLFS   (ubyte logical @ A, ubyte device @ X, ubyte address @ Y) -> clobbers() -> () = $FFBA	; set logical file parameters
asmsub	SETNAM   (ubyte namelen @ A, str filename @ XY) -> clobbers() -> ()	= $FFBD		; set filename parameters
asmsub	OPEN     () -> clobbers(A,X,Y) -> ()			= $FFC0		; (via 794 ($31A)) open a logical file
asmsub	CLOSE    (ubyte logical @ A) -> clobbers(A,X,Y) -> ()	= $FFC3		; (via 796 ($31C)) close a logical file
asmsub	CHKIN    (ubyte logical @ X) -> clobbers(A,X) -> ()	= $FFC6		; (via 798 ($31E)) define an input channel
asmsub	CHKOUT   (ubyte logical @ X) -> clobbers(A,X) -> ()	= $FFC9		; (via 800 ($320)) define an output channel
asmsub	CLRCHN   () -> clobbers(A,X) -> ()			= $FFCC		; (via 802 ($322)) restore default devices
asmsub	CHRIN    () -> clobbers(Y) -> (ubyte @ A)		= $FFCF		; (via 804 ($324)) input a character (for keyboard, read a whole line from the screen) A=byte read.
asmsub	CHROUT   (ubyte char @ A) -> clobbers() -> ()		= $FFD2		; (via 806 ($326)) output a character
asmsub	LOAD     (ubyte verify @ A, uword address @ XY) -> clobbers() -> (ubyte @Pc, ubyte @ A, ubyte @ X, ubyte @ Y) = $FFD5	; (via 816 ($330)) load from device
asmsub	SAVE     (ubyte zp_startaddr @ A, uword endaddr @ XY) -> clobbers() -> (ubyte @ Pc, ubyte @ A) = $FFD8	; (via 818 ($332)) save to a device
asmsub	SETTIM   (ubyte low @ A, ubyte middle @ X, ubyte high @ Y) -> clobbers() -> ()	= $FFDB		; set the software clock
asmsub	RDTIM    () -> clobbers() -> (ubyte @ A, ubyte @ X, ubyte @ Y) = $FFDE	; read the software clock
asmsub	STOP     () -> clobbers(A,X) -> (ubyte @ Pz, ubyte @ Pc)	= $FFE1		; (via 808 ($328)) check the STOP key
asmsub	GETIN    () -> clobbers(X,Y) -> (ubyte @ A)		= $FFE4		; (via 810 ($32A)) get a character
asmsub	CLALL    () -> clobbers(A,X) -> ()			= $FFE7		; (via 812 ($32C)) close all files
asmsub	UDTIM    () -> clobbers(A,X) -> ()			= $FFEA		; update the software clock
asmsub	SCREEN   () -> clobbers() -> (ubyte @ X, ubyte @ Y)	= $FFED		; read number of screen rows and columns
asmsub	PLOT     (ubyte dir @ Pc, ubyte col @ Y, ubyte row @ X) -> clobbers() -> (ubyte @ X, ubyte @ Y)	= $FFF0		; read/set position of cursor on screen.  See c64scr.PLOT for a 'safe' wrapper that preserves X.
asmsub	IOBASE   () -> clobbers() -> (uword @ XY)		= $FFF3		; read base address of I/O devices

; ---- end of C64 kernal routines ----

}
