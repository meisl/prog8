%import textio
%zeropage basicsafe

; Note: this program is compatible with C64 and CX16.

main {
    const uword SCREEN_W = txt.DEFAULT_WIDTH
    const uword SCREEN_H = txt.DEFAULT_HEIGHT
    uword anglex
    uword angley
    ubyte ball_color
    const ubyte ball_char = 81

    sub start() {
        repeat {
            ubyte x = msb(sin8u(msb(anglex)) * SCREEN_W)
            ubyte y = msb(cos8u(msb(angley)) * SCREEN_H)
            txt.setcc(x, y, ball_char, ball_color)
            anglex += 366
            angley += 291
            ball_color++
        }
    }
}
