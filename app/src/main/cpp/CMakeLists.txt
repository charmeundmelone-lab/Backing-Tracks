cmake_minimum_required(VERSION 3.22.1)
project(nativeusb)

# Native Bibliothek für den USB-Multitrack-Pfad (UAC2, CQ20B):
#  - usb_detach.c: Machbarkeits-Test der Kernel-Treiber-Loslösung (Kill-Kriterium 1)
#  - usb_tone.c:   isochroner Sinuston-Test auf einem diskreten Kanal (Kill-Kriterium 2)
add_library(nativeusb SHARED usb_detach.c usb_tone.c)

find_library(log-lib log)
target_link_libraries(nativeusb ${log-lib} m)
