%target cx16
%import textio
%zeropage basicsafe
%option no_sysinit
; simple line comment; consecutive lines can be folded
; TODO: comments with "TODO" or "FIXME" are highlited
; FIXME #31
main {
	str   input = "string literal with escapes: \r\n\"\\"
	ubyte c = 'x' ; character literal in bold
	byte  decimal = (0 + 1 - 2 * 3) / 4 % 5
	word[] numbers = [$80ea, %0101011, 23]
	const float pi = 3.1415
	const ubyte boolean = true or false and true xor not false
	uword dead = ($beef >> 1) | $cafe & $babe ^ ~%1010101 
	inline asmsub foo(ubyte char @A) clobbers(Y) -> ubyte @A {
		%asm {{
			a_label:
						nop			; comment inside asm
						bcc _done
						sec
			_done:		rts
		}} 
	}
	sub start(ubyte char) -> ubyte {
		ubyte @zp ch = min(numbers)
		void foo()
		if (true) {
			goto nirvana
		} else {
			return 0
		}
		repeat {
			ch = input[index+5]
			when ch {
				0 -> {
					break
				}
				else -> {
					temp[0] = ch
					txt.print(temp)
				}
			}
			index++
		}
		return
	}
}
