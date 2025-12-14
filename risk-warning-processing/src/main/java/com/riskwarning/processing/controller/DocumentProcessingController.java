package com.riskwarning.processing.controller;

import com.riskwarning.common.result.Result;
import com.riskwarning.processing.service.DocumentProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.CompletableFuture;

/**
 * 文档处理控制器
 * 支持多种文件获取方式：上传、URL、本地路径
 */
@Slf4j
@RestController
@RequestMapping("/api/document")
@RequiredArgsConstructor
public class DocumentProcessingController {

    private final DocumentProcessingService documentProcessingService;

    @PostMapping("/process")
    public Result<DocumentProcessingService.DocumentProcessingResult> processDocument(
            @RequestParam(value = "type", defaultValue = "upload") String type,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "url", required = false) String url,
            @RequestParam(value = "path", required = false) String path) {
        try {
            CompletableFuture<DocumentProcessingService.DocumentProcessingResult> future = 
                    documentProcessingService.processDocumentAsync(type, file, url, path);
            
            // 异步等待结果
            DocumentProcessingService.DocumentProcessingResult result = future.get();
            
            if (result.isSuccess()) {
                return Result.success(result);
            } else {
                return Result.fail(result.getMessage());
            }
        } catch (Exception e) {
            return Result.fail("处理文档失败: " + e.getMessage());
        }
    }
}

