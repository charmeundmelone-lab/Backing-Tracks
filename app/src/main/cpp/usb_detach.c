// Nativer Detach-/Claim-Test für den UAC2-Multitrack-Pfad zum Allen & Heath CQ20B.
//
// Hintergrund (siehe CLAUDE.md, "Echtes Multitrack-Audio … CQ20B"):
// Androids Java-USB-API kann keine isochronen Transfers und der Kernel-Treiber
// snd-usb-audio hält das AudioStreaming-Interface belegt. Um den Endpoint später
// selbst isochron zu bespielen, muss die App den Treiber vom Interface lösen
// (USBDEVFS_DISCONNECT), das Interface claimen und Alt-Setting 1 setzen.
//
// Diese Datei macht NUR den Machbarkeits-Test dieses ersten, kritischsten Schritts
// (Kill-Kriterium 1: geht der Detach ohne Root?). Kein Audio, kein isochroner
// Transfer — das kommt erst, wenn dieser Test grün ist. Der Dateideskriptor kommt
// aus UsbDeviceConnection.getFileDescriptor() (Java-Seite), zeigt also auf den
// bereits geöffneten usbfs-Knoten des Geräts.

#include <jni.h>
#include <string.h>
#include <errno.h>
#include <stdio.h>
#include <sys/ioctl.h>
#include <linux/usbdevice_fs.h>

// Hängt eine Zeile "Label: OK" bzw. "Label: FEHLER (errno=… …)" an den Report an.
static void append_result(char *buf, size_t cap, const char *label, int rc, int err) {
    size_t len = strlen(buf);
    if (len >= cap) return;
    if (rc == 0) {
        snprintf(buf + len, cap - len, "%s: OK\n", label);
    } else {
        snprintf(buf + len, cap - len, "%s: FEHLER (errno=%d %s)\n",
                 label, err, strerror(err));
    }
}

JNIEXPORT jstring JNICALL
Java_de_livegigplayer_pro_audio_UsbDetachTester_detachClaimSetAlt(
        JNIEnv *env, jobject thiz, jint fd, jint iface, jint alt) {

    char report[1024];
    report[0] = '\0';
    size_t cap = sizeof(report);

    {
        size_t len = strlen(report);
        snprintf(report + len, cap - len,
                 "fd=%d  Interface=%d  Alt-Setting=%d\n", fd, iface, alt);
    }

    // 1) Kernel-Treiber (snd-usb-audio) vom Interface lösen.
    //    USBDEVFS_DISCONNECT wird über USBDEVFS_IOCTL an das Interface geschickt.
    struct usbdevfs_ioctl disconnect_cmd;
    disconnect_cmd.ifno = iface;
    disconnect_cmd.ioctl_code = USBDEVFS_DISCONNECT;
    disconnect_cmd.data = NULL;
    int rc = ioctl(fd, USBDEVFS_IOCTL, &disconnect_cmd);
    int err = errno;
    // ENODATA/ENODEV hier bedeutet meist: es war gar kein Treiber gebunden — für
    // unseren Zweck (Interface ist frei) ist das ebenfalls in Ordnung.
    if (rc != 0 && (err == ENODATA || err == ENODEV)) {
        size_t len = strlen(report);
        snprintf(report + len, cap - len,
                 "Detach (snd-usb-audio lösen): kein Treiber gebunden (errno=%d %s) — OK\n",
                 err, strerror(err));
    } else {
        append_result(report, cap, "Detach (snd-usb-audio lösen)", rc, err);
    }

    // 2) Interface für exklusiven Zugriff claimen.
    unsigned int claim_iface = (unsigned int) iface;
    rc = ioctl(fd, USBDEVFS_CLAIMINTERFACE, &claim_iface);
    err = errno;
    append_result(report, cap, "Interface claimen", rc, err);

    // 3) Alternate-Setting setzen (alt 1 = das Multitrack-Format mit 24ch OUT).
    struct usbdevfs_setinterface set_alt;
    set_alt.interface = (unsigned int) iface;
    set_alt.altsetting = (unsigned int) alt;
    rc = ioctl(fd, USBDEVFS_SETINTERFACE, &set_alt);
    err = errno;
    append_result(report, cap, "Alt-Setting setzen", rc, err);

    // Interface wieder freigeben, damit das Gerät nicht in einem halb-geclaimten
    // Zustand hängen bleibt (dieser Test soll nichts dauerhaft übernehmen).
    ioctl(fd, USBDEVFS_RELEASEINTERFACE, &claim_iface);

    {
        size_t len = strlen(report);
        snprintf(report + len, cap - len,
                 "\n→ Alle drei Schritte OK = Kill-Kriterium 1 bestanden (Detach ohne Root moeglich).\n"
                 "  Interface wurde danach wieder freigegeben.\n");
    }

    return (*env)->NewStringUTF(env, report);
}
