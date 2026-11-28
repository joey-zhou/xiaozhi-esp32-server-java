package com.xiaozhi.utils;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 文件内容的 SHA-256，十六进制小写 */
public final class FileHashUtil {

    private static final int BUFFER_SIZE = 8192;

    private FileHashUtil() {
    }

    public static String sha256(byte[] content) {
        return HexFormat.of().formatHex(digest().digest(content));
    }

    /** 读到流末尾，不关流 */
    public static String sha256(InputStream content) throws IOException {
        MessageDigest digest = digest();
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        while ((read = content.read(buffer)) != -1) {
            digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JDK 缺少 SHA-256", e);
        }
    }
}
