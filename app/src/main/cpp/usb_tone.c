// Isochroner Sinuston-Test für den UAC2-Multitrack-Pfad zum CQ20B.
//
// Phase 3 der Planung: beweist, dass wir nach dem Detach (Kill-Kriterium 1, siehe
// usb_detach.c) tatsächlich Audio isochron auf den 24ch-OUT-Endpoint (EP 0x01,
// IF1/alt1) schreiben können — und zwar auf GENAU EINEN Kanal, damit am Pult
// sichtbar wird, dass die Kanäle diskret getrennt ankommen (Kill-Kriterium 2).
//
// Bewusst simpel (MVP): frei laufend im Nenn-Takt (48 kHz), OHNE Auswertung des
// Feedback-Endpoints (0x81). Erste Frage ist nur "kommt ein sauberer Ton auf
// Kanal N an?". Falls es knackt/driftet, ist der nächste Schritt die
// Feedback-Synchronisation — das ist dann eine Verfeinerung, kein neues Risiko.
//
// Format laut Descriptor-Scan: 24 Kanäle, bSubslotSize=4 (4 Byte/Sample),
// bBitResolution=24 → 24-Bit-Sample, MSB-bündig in einem 32-Bit-Slot abgelegt.

#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include <errno.h>
#include <stdio.h>
#include <stdint.h>
#include <stdarg.h>
#include <math.h>
#include <pthread.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <linux/usbdevice_fs.h>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

#define TONE_SAMPLE_RATE   48000
#define TONE_CHANNELS      24
#define TONE_BYTES_PER_SMP 4
#define TONE_FRAME_BYTES   (TONE_CHANNELS * TONE_BYTES_PER_SMP)   // 96
#define TONE_SMP_PER_PKT   (TONE_SAMPLE_RATE / 8000)              // 6 (High-Speed µFrame)
#define TONE_PKT_BYTES     (TONE_SMP_PER_PKT * TONE_FRAME_BYTES)  // 576
#define TONE_NPACK         8                                      // Pakete je URB (~1 ms)
#define TONE_URB_BYTES     (TONE_NPACK * TONE_PKT_BYTES)          // 4608
#define TONE_NUM_URBS      8
#define TONE_EP_OUT        0x01
#define TONE_FREQ_HZ       440.0f
#define TONE_AMPLITUDE     0.2f

static volatile int g_running = 0;
static pthread_t    g_thread;
static int          g_fd    = -1;
static int          g_ifnum = 1;
static int          g_chan  = 0;      // 0-basierter Kanalindex im Frame
static uint64_t     g_sample = 0;

static struct usbdevfs_urb *g_urbs[TONE_NUM_URBS];
static unsigned char       *g_bufs[TONE_NUM_URBS];

// Statistik (nur der Streaming-Thread schreibt; gelesen nach pthread_join).
static volatile long g_reaped     = 0;
static volatile long g_urb_err    = 0;
static volatile long g_pkt_ok     = 0;
static volatile long g_pkt_err    = 0;
static volatile long g_submit_err = 0;
static volatile long g_bytes_sent = 0;
static volatile int  g_last_errno = 0;

// Füllt einen URB-Puffer mit dem nächsten Abschnitt der Sinuskurve — nur auf dem
// Zielkanal, alle anderen Kanäle bleiben Stille. Phase läuft global weiter.
static void fill_buffer(unsigned char *buf) {
    memset(buf, 0, TONE_URB_BYTES);
    int frames = TONE_NPACK * TONE_SMP_PER_PKT;
    for (int f = 0; f < frames; f++) {
        double t = (double) (g_sample++) / (double) TONE_SAMPLE_RATE;
        float s = sinf(2.0f * (float) M_PI * TONE_FREQ_HZ * (float) t);
        int32_t v = (int32_t) (s * TONE_AMPLITUDE * 8388607.0f); // 24-Bit-Bereich
        v <<= 8;                                                 // MSB-bündig im 32-Bit-Slot
        unsigned char *p = buf + f * TONE_FRAME_BYTES + g_chan * TONE_BYTES_PER_SMP;
        p[0] = (unsigned char) (v & 0xFF);
        p[1] = (unsigned char) ((v >> 8) & 0xFF);
        p[2] = (unsigned char) ((v >> 16) & 0xFF);
        p[3] = (unsigned char) ((v >> 24) & 0xFF);
    }
}

static void account(struct usbdevfs_urb *u) {
    g_reaped++;
    if (u->status != 0) g_urb_err++;
    for (int i = 0; i < u->number_of_packets; i++) {
        if (u->iso_frame_desc[i].status != 0) {
            g_pkt_err++;
        } else {
            g_pkt_ok++;
            g_bytes_sent += u->iso_frame_desc[i].actual_length;
        }
    }
}

static void *stream_thread(void *arg) {
    (void) arg;
    int outstanding = 0;

    // Alle URBs initial füllen und einreichen.
    for (int i = 0; i < TONE_NUM_URBS; i++) {
        fill_buffer(g_bufs[i]);
        if (ioctl(g_fd, USBDEVFS_SUBMITURB, g_urbs[i]) == 0) {
            outstanding++;
        } else {
            g_last_errno = errno;
            g_submit_err++;
        }
    }

    // Reap-Schleife: fertige URBs neu füllen und wieder einreichen, solange aktiv.
    while (outstanding > 0) {
        struct usbdevfs_urb *done = NULL;
        int r = ioctl(g_fd, USBDEVFS_REAPURB, &done);
        if (r < 0) {
            if (errno == EINTR) continue;
            g_last_errno = errno;
            break;
        }
        outstanding--;
        if (done) {
            account(done);
            if (g_running) {
                fill_buffer((unsigned char *) done->buffer);
                if (ioctl(g_fd, USBDEVFS_SUBMITURB, done) == 0) {
                    outstanding++;
                } else {
                    g_last_errno = errno;
                    g_submit_err++;
                }
            }
        }
    }
    return NULL;
}

static void append(char *buf, size_t cap, const char *fmt, ...) {
    size_t len = strlen(buf);
    if (len >= cap) return;
    va_list ap;
    __builtin_va_start(ap, fmt);
    vsnprintf(buf + len, cap - len, fmt, ap);
    __builtin_va_end(ap);
}

JNIEXPORT jstring JNICALL
Java_de_livegigplayer_pro_audio_UsbIsoToneTester_nativeStart(
        JNIEnv *env, jobject thiz, jint fd, jint ifnum, jint alt, jint chan) {

    char report[1024];
    report[0] = '\0';

    if (g_running) {
        return (*env)->NewStringUTF(env, "Streaming läuft bereits.");
    }

    g_fd = fd; g_ifnum = ifnum; g_chan = chan; g_sample = 0;
    g_reaped = g_urb_err = g_pkt_ok = g_pkt_err = g_submit_err = g_bytes_sent = 0;
    g_last_errno = 0;

    append(report, sizeof(report),
           "fd=%d  Interface=%d  Alt=%d  Kanal=%d (0-basiert)\n", fd, ifnum, alt, chan);

    // 1) Detach + Claim + Alt-Setting (Interface bleibt jetzt geclaimt).
    struct usbdevfs_ioctl disc; disc.ifno = ifnum; disc.ioctl_code = USBDEVFS_DISCONNECT; disc.data = NULL;
    int rc = ioctl(fd, USBDEVFS_IOCTL, &disc); int err = errno;
    if (rc != 0 && err != ENODATA && err != ENODEV)
        append(report, sizeof(report), "Detach: FEHLER (errno=%d %s)\n", err, strerror(err));
    else
        append(report, sizeof(report), "Detach: OK\n");

    unsigned int ci = (unsigned int) ifnum;
    if (ioctl(fd, USBDEVFS_CLAIMINTERFACE, &ci) != 0) {
        err = errno;
        append(report, sizeof(report), "Claim: FEHLER (errno=%d %s) — Abbruch.\n", err, strerror(err));
        return (*env)->NewStringUTF(env, report);
    }
    append(report, sizeof(report), "Claim: OK\n");

    struct usbdevfs_setinterface si; si.interface = (unsigned int) ifnum; si.altsetting = (unsigned int) alt;
    if (ioctl(fd, USBDEVFS_SETINTERFACE, &si) != 0) {
        err = errno;
        append(report, sizeof(report), "SetAlt: FEHLER (errno=%d %s) — Abbruch.\n", err, strerror(err));
        ioctl(fd, USBDEVFS_RELEASEINTERFACE, &ci);
        return (*env)->NewStringUTF(env, report);
    }
    append(report, sizeof(report), "SetAlt %d: OK\n", alt);

    // 2) URBs + Puffer anlegen.
    size_t urb_sz = sizeof(struct usbdevfs_urb)
                    + TONE_NPACK * sizeof(struct usbdevfs_iso_packet_desc);
    for (int i = 0; i < TONE_NUM_URBS; i++) {
        g_urbs[i] = (struct usbdevfs_urb *) calloc(1, urb_sz);
        g_bufs[i] = (unsigned char *) malloc(TONE_URB_BYTES);
        if (!g_urbs[i] || !g_bufs[i]) {
            append(report, sizeof(report), "Speicher-Allokation fehlgeschlagen.\n");
            for (int j = 0; j <= i; j++) { free(g_urbs[j]); free(g_bufs[j]); g_urbs[j] = NULL; g_bufs[j] = NULL; }
            ioctl(fd, USBDEVFS_RELEASEINTERFACE, &ci);
            return (*env)->NewStringUTF(env, report);
        }
        g_urbs[i]->type = USBDEVFS_URB_TYPE_ISO;
        g_urbs[i]->endpoint = TONE_EP_OUT;              // OUT (Bit 7 = 0)
        g_urbs[i]->flags = USBDEVFS_URB_ISO_ASAP;
        g_urbs[i]->buffer = g_bufs[i];
        g_urbs[i]->buffer_length = TONE_URB_BYTES;
        g_urbs[i]->number_of_packets = TONE_NPACK;
        for (int p = 0; p < TONE_NPACK; p++)
            g_urbs[i]->iso_frame_desc[p].length = TONE_PKT_BYTES;
    }

    // 3) Streaming-Thread starten.
    g_running = 1;
    if (pthread_create(&g_thread, NULL, stream_thread, NULL) != 0) {
        g_running = 0;
        append(report, sizeof(report), "Thread-Start fehlgeschlagen.\n");
        for (int i = 0; i < TONE_NUM_URBS; i++) { free(g_urbs[i]); free(g_bufs[i]); g_urbs[i] = NULL; g_bufs[i] = NULL; }
        ioctl(fd, USBDEVFS_RELEASEINTERFACE, &ci);
        return (*env)->NewStringUTF(env, report);
    }

    append(report, sizeof(report),
           "\n→ Streaming läuft. 440-Hz-Ton auf Kanal %d.\n"
           "  Am CQ20B prüfen: zeigt GENAU EIN Return Pegel? Dann diskret getrennt.\n"
           "  Danach \"Ton stoppen\" für die Statistik (Fehler/Knacken).\n", chan + 1);
    return (*env)->NewStringUTF(env, report);
}

JNIEXPORT jstring JNICALL
Java_de_livegigplayer_pro_audio_UsbIsoToneTester_nativeStop(
        JNIEnv *env, jobject thiz) {

    char report[512];
    report[0] = '\0';

    if (!g_running) {
        return (*env)->NewStringUTF(env, "Streaming läuft nicht.");
    }

    // Stoppen: laufende URBs verwerfen, damit REAPURB im Thread aufwacht.
    g_running = 0;
    for (int i = 0; i < TONE_NUM_URBS; i++)
        if (g_urbs[i]) ioctl(g_fd, USBDEVFS_DISCARDURB, g_urbs[i]);
    pthread_join(g_thread, NULL);

    unsigned int ci = (unsigned int) g_ifnum;
    ioctl(g_fd, USBDEVFS_RELEASEINTERFACE, &ci);

    append(report, sizeof(report),
           "Gestoppt.\n"
           "URBs reaped: %ld  (mit Fehlerstatus: %ld)\n"
           "Iso-Pakete OK: %ld   Fehler: %ld\n"
           "Bytes gesendet: %ld\n"
           "Submit-Fehler: %ld   letzter errno: %d %s\n",
           g_reaped, g_urb_err, g_pkt_ok, g_pkt_err, g_bytes_sent,
           g_submit_err, g_last_errno,
           g_last_errno ? strerror(g_last_errno) : "-");

    if (g_pkt_ok > 0 && g_pkt_err == 0 && g_urb_err == 0)
        append(report, sizeof(report), "\n→ Sauber: kein einziger Paket-/URB-Fehler.\n");
    else if (g_pkt_ok > 0)
        append(report, sizeof(report), "\n→ Ton lief, aber es gab Fehler → mögliche Ursache fürs Knacken (Feedback-Sync nötig).\n");
    else
        append(report, sizeof(report), "\n→ Kein einziges Paket sauber raus → Format/Rate/Endpoint prüfen.\n");

    for (int i = 0; i < TONE_NUM_URBS; i++) {
        free(g_urbs[i]); free(g_bufs[i]);
        g_urbs[i] = NULL; g_bufs[i] = NULL;
    }
    return (*env)->NewStringUTF(env, report);
}
