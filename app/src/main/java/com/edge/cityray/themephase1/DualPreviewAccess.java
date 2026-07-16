package com.edge.cityray.themephase1;

import android.content.Context;
import android.util.Base64;

import java.security.MessageDigest;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Local presentation gate for the EDGE dual-screen concept previews. */
final class DualPreviewAccess {
    private static final String PREFS = "edge_dual_preview_access";
    private static final String KEY_UNLOCKED = "unlocked_v1";
    private static final int ROUNDS = 120_000;

    // Only a salted PBKDF2 verifier is shipped. The six-digit code is never stored as text.
    private static final String SALT = "9H/t3B" + "VAIxBZ" + "dmhXGcyqkw==";
    private static final String VERIFIER = "6KokxD97" + "j4ExcP8N" +
            "yr8y3Wda" + "Zr5fC/HF" + "qf9QsVV4lbA=";

    private DualPreviewAccess() {
    }

    static boolean isUnlocked(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_UNLOCKED, false);
    }

    static void unlock(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_UNLOCKED, true)
                .apply();
    }

    static void lock(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove(KEY_UNLOCKED)
                .apply();
    }

    static boolean verify(char[] candidate) {
        PBEKeySpec spec = null;
        try {
            byte[] salt = Base64.decode(SALT, Base64.NO_WRAP);
            byte[] expected = Base64.decode(VERIFIER, Base64.NO_WRAP);
            spec = new PBEKeySpec(candidate, salt, ROUNDS, expected.length * 8);
            byte[] actual = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
            return MessageDigest.isEqual(expected, actual);
        } catch (Throwable ignored) {
            return false;
        } finally {
            if (spec != null) spec.clearPassword();
            java.util.Arrays.fill(candidate, '\0');
        }
    }
}
