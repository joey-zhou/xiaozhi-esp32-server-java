package com.xiaozhi.utils;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 文件哈希计算工具类
 * <p>
 * 该类为 final 类型，并拥有一个私有构造函数，以防止被继承或实例化。
 * 所有方法均为静态方法，通过类名直接调用。
 */
public final class FileHashUtil {

    private static final int BUFFER_SIZE = 8192; // 8KB 缓冲区大小

    /**
     * 私有构造函数，防止该工具类被实例化。
     */
    private FileHashUtil() {
        // 抛出异常是更严格的单例模式实现，确保没有人能通过反射等方式创建实例
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * 计算 MultipartFile 的哈希值（推荐使用 SHA-256）。
     *
     * @param file 需要计算哈希值的文件
     * @return 文件的 SHA-256 哈希值，以十六进制字符串表示
     */
    public static String calculateSha256(MultipartFile file) {
        return calculateHash(file, "SHA-256");
    }

    /**
     * 计算 MultipartFile 的 MD5 哈希值。
     *
     * @param file 需要计算哈希值的文件
     * @return 文件的 MD5 哈希值，以十六进制字符串表示
     */
    public static String calculateMd5(MultipartFile file) {
        return calculateHash(file, "MD5");
    }

    /**
     * 计算 MultipartFile 哈希值的通用核心方法。
     *
     * @param file      需要计算哈希值的文件
     * @param algorithm 哈希算法，例如 "MD5", "SHA-1", "SHA-256"
     * @return 文件的哈希值，以十六进制字符串表示
     * @throws RuntimeException 如果文件为空、算法不受支持或发生 I/O 错误
     */
    public static String calculateHash(MultipartFile file, String algorithm) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空。");
        }

        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            try (InputStream is = file.getInputStream()) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            byte[] hashBytes = digest.digest();
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("不支持的哈希算法: " + algorithm, e);
        } catch (IOException e) {
            throw new RuntimeException("计算文件哈希时出错: " + file.getOriginalFilename(), e);
        }
    }

    /**
     * 将字节数组转换为十六进制字符串的辅助方法。
     *
     * @param hash 哈希计算后的字节数组
     * @return 十六进制表示的字符串
     */
    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
