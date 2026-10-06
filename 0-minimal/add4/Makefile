# CROSS_COMPILE ?= riscv64-unknown-elf-
CROSS_COMPILE ?= /work_root/chipyard/.conda-env/riscv-tools/bin/riscv64-unknown-elf-

ASFLAGS = -march=rv32i -mabi=ilp32
CFLAGS  = -O0 -Wall \
          -march=rv32i -mabi=ilp32 \
          -ffreestanding -fno-pic -fno-pie \
          -fno-stack-protector \
          -fno-asynchronous-unwind-tables \
          -fno-unwind-tables

LDFLAGS = --no-relax --oformat=elf32-littleriscv

AS      := $(CROSS_COMPILE)as
CC      := $(CROSS_COMPILE)gcc
LD      := $(CROSS_COMPILE)ld
OBJCOPY := $(CROSS_COMPILE)objcopy

TARGET = add4
OBJS   = start.o add4.o

# Assembly source
start.o: start.S
	$(AS) $(ASFLAGS) -o $@ $<

# C source
add4.o: add4.c
	$(CC) $(CFLAGS) -c -o $@ $<

# Link
$(TARGET).elf: $(OBJS) link.lds
	$(LD) $(LDFLAGS) -T link.lds -o $@ $(OBJS)

# Raw binary for 0-minimal ROM loader
$(TARGET).asmbin: $(TARGET).elf
	$(OBJCOPY) -O binary -j .text -j .data $< $@

BINS = $(TARGET).asmbin

.DEFAULT_GOAL := all

all: $(BINS)

update: $(BINS)
	cp -f $(BINS) ../src/main/resources/

clean:
	$(RM) *.o *.elf *.asmbin