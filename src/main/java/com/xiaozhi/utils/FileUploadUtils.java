package com.xiaozhi.utils;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.UUID;
import org.apache.commons.io.IOUtils;

/**
 * 文件上传工具类
 * 
 * @author Joey
 */
public class FileUploadUtils {

    /**
     * 默认大小 50MB
     */
    public static final long DEFAULT_MAX_SIZE = 50 * 1024 * 1024;

    /**
     * 默认的文件名最大长度 100
     */
    public static final int DEFAULT_FILE_NAME_LENGTH = 100;

    /**
     * 上传文件
     *
     * @param baseDir      基础目录
     * @param relativePath 相对路径
     * @param fileName     文件名
     * @param file         文件
     * @return 文件相对路径（相对于项目根目录）
     * @throws IOException
     */
    public static String uploadFile(String baseDir, String relativePath, String fileName, MultipartFile file)
            throws IOException {
        // 检查文件大小
        assertAllowed(file);

        // 创建目录
        String fullPath = baseDir;
        if (!relativePath.isEmpty()) {
            fullPath = fullPath + File.separator + relativePath;
        }
        
        // 确保目录存在
        File directory = new File(fullPath);
        if (!directory.exists()) {
            boolean created = directory.mkdirs();
            if (!created) {
                throw new IOException("无法创建目录: " + fullPath);
            }
        }

        // 保存文件
        File destFile = new File(directory, fileName);
        try (FileOutputStream fos = new FileOutputStream(destFile);
             InputStream inputStream = file.getInputStream()) {
            IOUtils.copy(inputStream, fos);
        }

        // 返回相对路径（统一使用正斜杠，便于URL访问）
        String relativeFilePath = baseDir + File.separator + relativePath + File.separator + fileName;
        return relativeFilePath.replace(File.separator, "/");
    }

    /**
     * 检查文件大小和类型
     *
     * @param file 上传的文件
     */
    public static void assertAllowed(MultipartFile file) {
        if (file.getSize() > DEFAULT_MAX_SIZE) {
            throw new IllegalArgumentException("文件大小超过限制，最大允许：" + (DEFAULT_MAX_SIZE / 1024 / 1024) + "MB");
        }
    }

    /**
     * 智能上传文件（根据配置决定上传到本地还是腾讯云COS）
     *
     * @param baseDir      本地基础目录
     * @param relativePath 相对路径
     * @param fileName     文件名
     * @param file         文件
     * @return 文件访问路径
     * @throws IOException 如果上传过程中发生IO错误
     */
    public static String smartUpload(String baseDir, String relativePath, String fileName,
            MultipartFile file) throws IOException {
        // 检查配置文件中是否有腾讯云COS的配置
        Properties properties = new Properties();
        try (InputStream inputStream = FileUploadUtils.class.getClassLoader()
                .getResourceAsStream("application-prod.properties")) {
            if (inputStream != null) {
                properties.load(inputStream);

                // 检查是否有腾讯云COS的必要配置
                String secretId = properties.getProperty("tencent.cos.secret-id");
                String secretKey = properties.getProperty("tencent.cos.secret-key");
                String region = properties.getProperty("tencent.cos.region");
                String bucketName = properties.getProperty("tencent.cos.bucket-name");

                // 如果所有必要的配置都存在，则上传到腾讯云COS
                if (secretId != null && !secretId.isEmpty() &&
                        secretKey != null && !secretKey.isEmpty() &&
                        region != null && !region.isEmpty() &&
                        bucketName != null && !bucketName.isEmpty()) {

                    // 获取COS路径前缀，如果没有配置则使用默认值
                    String cosPath = properties.getProperty("tencent.cos.path-prefix", "uploads/");
                    if (!cosPath.endsWith("/")) {
                        cosPath = cosPath + "/";
                    }

                    // 上传到COS
                    return uploadToCos(file, bucketName, secretId, secretKey, region,
                            cosPath + relativePath + "/");
                }
            }
        } catch (Exception e) {
            // 如果读取配置或上传到COS出错，则回退到本地上传
            return uploadFile(baseDir, relativePath, fileName, file);
        }

        // 如果没有COS配置或配置不完整，则上传到本地
        return uploadFile(baseDir, relativePath, fileName, file);
    }

    /**
     * 根据文件名确定内容类型
     *
     * @param fileName 文件名
     * @return 内容类型
     */
    private static String determineContentType(String fileName) {
        String extension = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        switch (extension) {
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "gif":
                return "image/gif";
            case "pdf":
                return "application/pdf";
            case "doc":
                return "application/msword";
            case "docx":
                return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls":
                return "application/vnd.ms-excel";
            case "xlsx":
                return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "txt":
                return "text/plain";
            case "html":
                return "text/html";
            case "mp3":
                return "audio/mpeg";
            case "mp4":
                return "video/mp4";
            case "wav":
                return "audio/wav";
            default:
                return "application/octet-stream";
        }
    }

    /**
     * 上传文件到腾讯云对象存储
     *
     * @param file       文件
     * @param bucketName 存储桶名称
     * @param secretId   腾讯云SecretId
     * @param secretKey  腾讯云SecretKey
     * @param region     地域信息，例如：ap-guangzhou
     * @param cosPath    COS路径，例如：folder/subfolder/
     * @return 文件访问URL
     * @throws Exception
     */
    public static String uploadToCos(MultipartFile file, String bucketName, String secretId, String secretKey,
            String region, String cosPath) throws Exception {
        // 检查文件大小
        assertAllowed(file);

        // 生成唯一文件名
        String originalFilename = file.getOriginalFilename();
        String suffix = originalFilename.substring(originalFilename.lastIndexOf("."));
        String fileName = UUID.randomUUID().toString().replaceAll("-", "") + suffix;

        // 构建完整的对象键（Key）
        String key = cosPath + fileName;

        // 创建 COSClient 实例
        COSCredentials cred = new BasicCOSCredentials(secretId, secretKey);
        ClientConfig clientConfig = new ClientConfig(new Region(region));
        COSClient cosClient = new COSClient(cred, clientConfig);

        try {
            // 上传文件
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            
            // 设置内容类型
            String contentType = file.getContentType();
            if (contentType == null || contentType.isEmpty()) {
                contentType = determineContentType(originalFilename);
            }
            metadata.setContentType(contentType);

            PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, key, file.getInputStream(), metadata);
            cosClient.putObject(putObjectRequest);

            // 生成文件访问URL
            return "https://" + bucketName + ".cos." + region + ".myqcloud.com/" + key;
        } finally {
            cosClient.shutdown();
        }
    }
}