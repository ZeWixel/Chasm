package chasm;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import javax.microedition.lcdui.Display;
import javax.microedition.media.Manager;
import javax.microedition.media.Player;
import javax.microedition.media.control.VolumeControl;

/**
 * J2ME Audio & Vibration Manager (JSR-135 MMAPI + MIDP 2.0 Display.vibrate).
 * Handles General MIDI soundtrack playback and pre-cached 8kHz WAV sound effects,
 * with hardware-safe arbitration for Nokia S60 and Sony Ericsson feature phones.
 */
public final class SoundManager {
    public static SoundManager instance;

    public static final int SFX_RIFLE       = 0;
    public static final int SFX_SHOTGUN     = 1;
    public static final int SFX_SAW         = 2;
    public static final int SFX_ROCKET      = 3;
    public static final int SFX_MON_PAIN    = 4;
    public static final int SFX_MON_DIE     = 5;
    public static final int SFX_GIB         = 6;
    public static final int SFX_PLAYER_PAIN = 7;
    public static final int SFX_DOOR        = 8;
    public static final int SFX_PICKUP      = 9;
    public static final int NUM_SFX         = 10;

    public static void playSfx(int sfxId) {
        if (instance != null) {
            instance.playSfxInstance(sfxId);
        }
    }

    private static final String[] SFX_FILES = {
        "/s0.wav",
        "/s1.wav",
        "/s2.wav",
        "/s3.wav",
        "/s4.wav",
        "/s5.wav",
        "/s6.wav",
        "/s7.wav",
        "/s8.wav",
        "/s9.wav"
    };

    private static final String[] MUSIC_FILES = {
        "/m0.mid",
        "/m1.mid",
        "/m2.mid",
        "/m3.mid",
        "/m0.mid"
    };

    private final Display display;
    public boolean musicEnabled = true;
    public boolean sfxEnabled = true;
    public boolean vibraEnabled = true;
    // On strict single-stream Nokia S60 devices, pauses MIDI briefly during SFX if needed
    public boolean s60SafeAudio = false;

    private byte[][] sfxData;
    private Player musicPlayer;
    private Player activeSfxPlayer;
    private int currentMusicTrack = -1;
    private long lastSfxTime = 0L;

    public SoundManager(Display disp) {
        this.display = disp;
        instance = this;
        String platform = System.getProperty("microedition.platform");
        if (platform != null) {
            String p = platform.toUpperCase();
            if (p.indexOf("NOKIA") >= 0 && p.indexOf("SONYERICSSON") < 0) {
                s60SafeAudio = false; // Default simultaneous; user can toggle in menu
            }
        }
        preloadSfxBytes();
    }

    private void preloadSfxBytes() {
        sfxData = new byte[NUM_SFX][];
        byte[] tmp = new byte[1024];
        for (int i = 0; i < NUM_SFX; i++) {
            InputStream is = null;
            try {
                is = getClass().getResourceAsStream(SFX_FILES[i]);
                if (is != null) {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream(4096);
                    int n;
                    while ((n = is.read(tmp)) > 0) {
                        bos.write(tmp, 0, n);
                    }
                    sfxData[i] = bos.toByteArray();
                }
            } catch (Exception e) {
                // Ignore missing SFX
            } finally {
                if (is != null) {
                    try { is.close(); } catch (Exception ex) {}
                }
            }
        }
    }

    public synchronized void playMusic(int trackIdx) {
        currentMusicTrack = trackIdx;
        if (!musicEnabled || trackIdx < 0 || trackIdx >= MUSIC_FILES.length) {
            stopMusic();
            return;
        }
        stopMusic();
        InputStream is = null;
        try {
            is = getClass().getResourceAsStream(MUSIC_FILES[trackIdx]);
            if (is == null) return;
            musicPlayer = Manager.createPlayer(is, "audio/midi");
            musicPlayer.realize();
            musicPlayer.prefetch();
            musicPlayer.setLoopCount(-1);
            setPlayerVolume(musicPlayer, 75);
            musicPlayer.start();
        } catch (Exception e) {
            musicPlayer = null;
        }
    }

    public synchronized void stopMusic() {
        if (musicPlayer != null) {
            try {
                musicPlayer.stop();
            } catch (Exception e) {}
            try {
                musicPlayer.deallocate();
            } catch (Exception e) {}
            try {
                musicPlayer.close();
            } catch (Exception e) {}
            musicPlayer = null;
        }
    }

    public synchronized void pauseAll() {
        if (musicPlayer != null) {
            try { musicPlayer.stop(); } catch (Exception e) {}
        }
        closeActiveSfx();
    }

    public synchronized void resumeMusic() {
        if (musicEnabled && currentMusicTrack >= 0) {
            if (musicPlayer != null) {
                try {
                    musicPlayer.start();
                    return;
                } catch (Exception e) {}
            }
            playMusic(currentMusicTrack);
        }
    }

    private void closeActiveSfx() {
        if (activeSfxPlayer != null) {
            try { activeSfxPlayer.stop(); } catch (Exception e) {}
            try { activeSfxPlayer.close(); } catch (Exception e) {}
            activeSfxPlayer = null;
        }
    }

    public synchronized void playSfxInstance(int sfxId) {
        if (!sfxEnabled || sfxId < 0 || sfxId >= NUM_SFX) return;
        byte[] raw = sfxData[sfxId];
        if (raw == null) return;

        long now = System.currentTimeMillis();
        if (now - lastSfxTime < 90L && sfxId != SFX_RIFLE && sfxId != SFX_SHOTGUN && sfxId != SFX_ROCKET) {
            return;
        }
        lastSfxTime = now;

        if (s60SafeAudio && musicPlayer != null) {
            try { musicPlayer.stop(); } catch (Exception e) {}
        }

        closeActiveSfx();
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(raw);
            activeSfxPlayer = Manager.createPlayer(bais, "audio/x-wav");
            activeSfxPlayer.realize();
            setPlayerVolume(activeSfxPlayer, 95);
            activeSfxPlayer.start();
        } catch (Exception e) {
            activeSfxPlayer = null;
        }
    }

    public void vibrate(int durationMs) {
        if (!vibraEnabled || display == null) return;
        try {
            display.vibrate(durationMs);
        } catch (Exception e) {}
    }

    private static void setPlayerVolume(Player p, int level) {
        if (p == null) return;
        try {
            VolumeControl vc = (VolumeControl) p.getControl("VolumeControl");
            if (vc != null) {
                vc.setLevel(level);
            }
        } catch (Exception e) {}
    }
}
