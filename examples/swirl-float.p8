%import textio
%import floats
%zeropage basicsafe

; Note: this program is compatible with C64 and CX16.

main {

    sub start()  {
        const uword SCREEN_W = txt.DEFAULT_WIDTH
        const uword SCREEN_H = txt.DEFAULT_HEIGHT
        float time
        ubyte ball_color
        const ubyte ball_char = 81

        repeat {
            ubyte x = (sin(time) * SCREEN_W/2.1) + SCREEN_W/2.0 as ubyte
            ubyte y = (cos(time*1.1356) * SCREEN_H/2.1) + SCREEN_H/2.0 as ubyte
            txt.setcc(x, y, ball_char, ball_color)
            time += 0.08
            ball_color++
        }
    }
}
