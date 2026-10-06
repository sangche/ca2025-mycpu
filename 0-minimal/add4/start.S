    .section .text.init
    .globl _start
    .type _start, @function

_start:
    # Memory = 8192 words = 0x8000 bytes
    # Stack grows downward.
    #
    # PC at _start = 0x1000
    # auipc sp, 0x7:
    #     sp = 0x1000 + 0x7000
    #        = 0x8000
    auipc sp, 0x7

    # main() 호출
    # Linker relaxation을 --no-relax로 막아
    # AUIPC + JALR 형태를 유지한다.
    call main

.Lhalt:
    # main이 return하면 이 위치로 돌아온다.
    #
    # auipc ra,0:
    #     ra = address of this instruction
    #
    # 다음 jalr가 ra로 돌아가므로
    # 결국 .Lhalt에서 무한 반복한다.
    auipc ra, 0
    jalr  zero, 0(ra)

    .size _start, .-_start
    