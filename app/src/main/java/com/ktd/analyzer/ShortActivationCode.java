package com.ktd.analyzer;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Experimental offline 12 digit device-bound activation code.
 * WARNING: Secret distributed in client APK. Unlike RSA signatures, this is
 * NOT cryptographically robust against reverse engineering/forgery.
 * The legacy RSA activation remains supported in LicenseManager.
 */
public final class ShortActivationCode {
    private ShortActivationCode() {}

    private static final String[] SECRET_HEX_PARTS = {
        "698D9143C193C110", "D042E9DFAD6DB56E", "877B27C321897A24", "3A175098015519E9"
    };
    private static final BigInteger CODE_SPACE = new BigInteger("1000000000000");

    public static String normalizeDevice(String raw) {
        return raw == null ? "" : raw.toUpperCase(Locale.US).replaceAll("[^0-9A-F]", "");
    }

    public static boolean isValidDevice(String device) {
        return device != null && device.matches("[0-9A-F]{16}");
    }

    /** Returns exactly twelve decimal digits displayed in 4-4-4 groups. */
    public static String generate(String rawDevice) throws Exception {
        String device = normalizeDevice(rawDevice);
        if (!isValidDevice(device)) throw new IllegalArgumentException("16 haneli cihaz kodu gerekli");
        byte[] secret = fromHex(String.join("", SECRET_HEX_PARTS));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        byte[] value = mac.doFinal(("KTD2|" + device + "|LIFETIME").getBytes(StandardCharsets.UTF_8));
        BigInteger number = new BigInteger(1, value).mod(CODE_SPACE);
        String d = String.format(Locale.US, "%012d", number.longValue());
        return d.substring(0,4) + "-" + d.substring(4,8) + "-" + d.substring(8);
    }

    public static boolean verify(String rawDevice, String candidate) {
        if (candidate == null) return false;
        String s = candidate.trim();
        if (!s.matches("[0-9\\s-]+")) return false;
        s = s.replaceAll("[\\s-]", "");
        if (!s.matches("[0-9]{12}")) return false;
        try {
            String expected = generate(rawDevice).replace("-", "");
            return java.security.MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                s.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception e) { return false; }
    }

    private static byte[] fromHex(String x) {
        byte[] out = new byte[x.length()/2];
        for (int i=0;i<out.length;i++) {
            int a=Character.digit(x.charAt(2*i),16);
            int b=Character.digit(x.charAt(2*i+1),16);
            out[i]=(byte)((a<<4)|b);
        }
        return out;
    }
}
