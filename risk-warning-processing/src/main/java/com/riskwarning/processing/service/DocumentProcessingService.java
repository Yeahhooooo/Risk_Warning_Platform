package com.riskwarning.processing.service;

import com.riskwarning.processing.util.ContentExtractor;
import com.riskwarning.processing.util.FileGetter;
import com.riskwarning.processing.util.FileScanner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


@Slf4j
@Service
public class DocumentProcessingService {

    private final FileGetter fileGetter;
    private final FileScanner fileScanner;
    private final ContentExtractor contentExtractor;
    
    private final ExecutorService executorService = Executors.newFixedThreadPool(5);

    public DocumentProcessingService(FileGetter fileGetter, 
                                   FileScanner fileScanner, 
                                   ContentExtractor contentExtractor) {
        this.fileGetter = fileGetter;
        this.fileScanner = fileScanner;
        this.contentExtractor = contentExtractor;
    }

    
    public CompletableFuture<DocumentProcessingResult> processDocumentAsync(
            String type, MultipartFile file, String url, String path) {
        return CompletableFuture.supplyAsync(() -> {
            File documentFile = null;
            boolean needCleanup = false; // 是否需要清理临时文件
            
            try {
                
                // 步骤1: 根据type获取文件
                switch (type.toLowerCase()) {
                    case "upload":
                        if (file == null || file.isEmpty()) {
                            throw new RuntimeException("上传文件为空");
                        }
                        documentFile = fileGetter.getFromUpload(file);
                        needCleanup = true; // 上传的文件需要清理
                        break;
                        
                    case "url":
                        if (url == null || url.trim().isEmpty()) {
                            throw new RuntimeException("文件URL为空");
                        }
                        documentFile = fileGetter.getFromUrl(url);
                        needCleanup = true; // 下载的文件需要清理
                        break;
                        
                    case "path":
                        if (path == null || path.trim().isEmpty()) {
                            throw new RuntimeException("文件路径为空");
                        }
                        documentFile = fileGetter.getFromPath(path);
                        // 本地路径文件不需要清理
                        break;
                        
                    default:
                        throw new RuntimeException("不支持的文件获取类型: " + type + "，支持的类型: upload, url, path");
                }
                
                FileGetter.FileMetadata metadata = fileGetter.getFileMetadata(documentFile);
                
                // 验证文件
                if (!fileGetter.validateFile(documentFile, metadata.getFileType())) {
                    throw new RuntimeException("文件验证失败");
                }
                
                // 步骤2: 分页扫描
                List<FileScanner.PageContent> pages = fileScanner.scanByPage(documentFile);
                // 步骤3: 逐页提取文本片段（并行处理）
                List<ContentExtractor.TextSegment> allSegments = pages.parallelStream()
                        .flatMap(page -> {
                            // 为每页创建临时文档对象
                            FileScanner.ScannedDocument pageDoc = new FileScanner.ScannedDocument();
                            pageDoc.setFullText(page.getText());
                            pageDoc.setPages(Collections.singletonList(page));
                            pageDoc.setFileName(metadata.getFileName());
                            pageDoc.setTotalPages(pages.size());
                            
                            // 提取该页的文本片段
                            return contentExtractor.extract(pageDoc).stream();
                        })
                        .collect(java.util.stream.Collectors.toList());
                
                // 构建结果
                DocumentProcessingResult result = new DocumentProcessingResult();
                result.setFileName(metadata.getFileName());
                result.setFileType(metadata.getFileType());
                result.setFileSize(metadata.getFileSize());
                result.setTotalPages(pages.size());
                result.setTotalCharacters(pages.stream().mapToInt(p -> p.getText().length()).sum());
                result.setTextSegments(allSegments);
                result.setSuccess(true);
                result.setMessage("文档处理成功");
                return result;
                
            } catch (Exception e) {
                DocumentProcessingResult result = new DocumentProcessingResult();
                result.setSuccess(false);
                result.setMessage("文档处理失败: " + e.getMessage());
                return result;
            } finally {
                // 清理临时文件（仅上传和下载的文件需要清理）
                if (documentFile != null && needCleanup) {
                    fileGetter.cleanupTempFile(documentFile);
                }
            }
        }, executorService);
    }

    @lombok.Data
    public static class DocumentProcessingResult {
        private String fileName;
        private com.riskwarning.common.enums.FileTypeEnum fileType;
        private long fileSize;
        private int totalPages;
        private long totalCharacters;
        private List<ContentExtractor.TextSegment> textSegments;
        private boolean success;
        private String message;
    }
}

