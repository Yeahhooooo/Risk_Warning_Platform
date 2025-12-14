package com.riskwarning.processing.util;

import com.riskwarning.common.enums.FileTypeEnum;
import com.riskwarning.common.exception.BusinessException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Slf4j
@Component
public class FileGetter {

    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024L; // 100MB


    public File getFromUpload(MultipartFile multipartFile) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new BusinessException("上传文件为空");
        }

        // 验证文件大小
        if (multipartFile.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("文件大小超过限制（最大100MB）");
        }

        // 创建临时文件
        String originalFilename = multipartFile.getOriginalFilename();
        String extension = getFileExtension(originalFilename);
        
        try {
            // 创建临时目录
            Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"), "document-processing");
            Files.createDirectories(tempDir);
            
            // 创建临时文件
            String tempFileName = UUID.randomUUID().toString() + "." + extension;
            Path tempFilePath = tempDir.resolve(tempFileName);
            File tempFile = tempFilePath.toFile();
            
            // 保存文件
            multipartFile.transferTo(tempFile);
            return tempFile;
            
        } catch (IOException e) {
            throw new BusinessException("保存文件失败: " + e.getMessage());
        }
    }


    public File getFromPath(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new BusinessException("文件路径为空");
        }

        // 清理路径，去除首尾空白
        filePath = filePath.trim();
        
        File file;
        
        // 如果是相对路径，尝试从项目根目录查找
        if (!new File(filePath).isAbsolute()) {
            // 获取项目根目录（当前工作目录）
            String projectRoot = System.getProperty("user.dir");
            file = new File(projectRoot, filePath);
        } else {
            file = new File(filePath);
        }
        
        // 如果文件不存在，尝试其他可能的路径
        if (!file.exists()) {
            // 尝试从项目根目录查找（即使路径看起来是绝对路径）
            String projectRoot = System.getProperty("user.dir");
            File projectFile = new File(projectRoot, new File(filePath).getName());
            if (projectFile.exists()) {
                file = projectFile;
            } else {
                // 尝试处理路径中的特殊字符（如引号）
                String normalizedPath = filePath.replace("\"", "").replace("'", "");
                File normalizedFile = new File(normalizedPath);
                if (normalizedFile.exists()) {
                    file = normalizedFile;
                } else {
                    // 最后尝试：项目根目录 + 文件名
                    File lastTry = new File(projectRoot, new File(normalizedPath).getName());
                    if (lastTry.exists()) {
                        file = lastTry;
                    } else {
                        throw new BusinessException("文件不存在: " + filePath + 
                            "\n尝试的路径: " + file.getAbsolutePath() + 
                            "\n项目根目录: " + projectRoot);
                    }
                }
            }
        }

        if (!file.isFile()) {
            throw new BusinessException("路径不是文件: " + file.getAbsolutePath());
        }
        return file;
    }

    public File getFromUrl(String fileUrl) {
        if (fileUrl == null || fileUrl.trim().isEmpty()) {
            throw new BusinessException("文件URL为空");
        }

        try {
            URL url = new URL(fileUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(10000); // 10秒连接超时
            connection.setReadTimeout(30000);    // 30秒读取超时
            connection.setRequestMethod("GET");
            
            // 检查响应码
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new BusinessException("下载文件失败，HTTP响应码: " + responseCode);
            }

            // 获取文件名
            String fileName = getFileNameFromUrl(fileUrl, connection);
            String extension = getFileExtension(fileName);
            
            // 创建临时文件
            Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"), "document-processing");
            Files.createDirectories(tempDir);
            
            String tempFileName = UUID.randomUUID().toString() + "." + extension;
            Path tempFilePath = tempDir.resolve(tempFileName);
            File tempFile = tempFilePath.toFile();
            
            // 下载文件
            try (InputStream inputStream = connection.getInputStream();
                 FileOutputStream outputStream = new FileOutputStream(tempFile)) {
                
                byte[] buffer = new byte[4096];
                int bytesRead;
                long totalBytesRead = 0;
                
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;
                    
                    // 检查文件大小限制
                    if (totalBytesRead > MAX_FILE_SIZE) {
                        tempFile.delete();
                        throw new BusinessException("文件大小超过限制（最大100MB）");
                    }
                }
            }
            
            return tempFile;
            
        } catch (IOException e) {
            throw new BusinessException("下载文件失败: " + e.getMessage());
        }
    }


    private String getFileNameFromUrl(String fileUrl, HttpURLConnection connection) {
        // 尝试从Content-Disposition响应头获取文件名
        String contentDisposition = connection.getHeaderField("Content-Disposition");
        if (contentDisposition != null && contentDisposition.contains("filename=")) {
            int startIndex = contentDisposition.indexOf("filename=") + 9;
            String filename = contentDisposition.substring(startIndex);
            if (filename.startsWith("\"") && filename.endsWith("\"")) {
                filename = filename.substring(1, filename.length() - 1);
            }
            return filename;
        }
        
        // 从URL中提取文件名
        try {
            URL url = new URL(fileUrl);
            String path = url.getPath();
            int lastSlash = path.lastIndexOf('/');
            if (lastSlash >= 0 && lastSlash < path.length() - 1) {
                return path.substring(lastSlash + 1);
            }
        } catch (Exception e) {
        }
        
        // 默认文件名
        return "downloaded_file";
    }


    public boolean validateFile(File file, FileTypeEnum expectedType) {
        if (file == null || !file.exists()) {
            return false;
        }

        // 验证文件类型
        String extension = getFileExtension(file.getName());
        if (expectedType != null && !expectedType.getSuffix().equalsIgnoreCase(extension)) {
            return false;
        }

        // 验证文件大小
        if (file.length() == 0) {
            return false;
        }

        if (file.length() > MAX_FILE_SIZE) {
            return false;
        }

        return true;
    }

   
    public FileMetadata getFileMetadata(File file) {
        if (file == null || !file.exists()) {
            throw new BusinessException("文件不存在");
        }

        FileMetadata metadata = new FileMetadata();
        metadata.setFileName(file.getName());
        metadata.setFileSize(file.length());
        metadata.setFileExtension(getFileExtension(file.getName()));
        metadata.setFilePath(file.getAbsolutePath());
        metadata.setLastModified(file.lastModified());
        
        // 检测文件类型
        String extension = metadata.getFileExtension();
        if ("pdf".equalsIgnoreCase(extension)) {
            metadata.setFileType(FileTypeEnum.PDF);
        } else if ("docx".equalsIgnoreCase(extension) || "doc".equalsIgnoreCase(extension)) {
            metadata.setFileType(FileTypeEnum.WORD);
        }

        return metadata;
    }

    
    public void cleanupTempFile(File file) {
        if (file != null && file.exists()) {
            try {
                boolean deleted = file.delete();
            } catch (Exception e) {
            }
        }
    }

    
    private String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < filename.length() - 1) {
            return filename.substring(lastDotIndex + 1).toLowerCase();
        }
        return "";
    }

   
    @Data
    public static class FileMetadata {
        private String fileName;
        private long fileSize;
        private String fileExtension;
        private String filePath;
        private long lastModified;
        private FileTypeEnum fileType;
    }
}

