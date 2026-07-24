cmake_minimum_required(VERSION 3.22.1)
project(nativeusb)

# Native Bibliothek für den USB-Detach-/Claim-Test (UAC2-Multitrack, CQ20B).
# Bewusst minimal: nur der Machbarkeits-Test der Kernel-Treiber-Loslösung.
add_library(nativeusb SHARED usb_detach.c)

find_library(log-lib log)
target_link_libraries(nativeusb ${log-lib})
