package com.riskwarning.processing.service;

import com.riskwarning.common.constants.Constants;
import com.riskwarning.common.utils.StringUtils;
import com.riskwarning.processing.entity.dto.DocumentProcessingResult;
import com.riskwarning.processing.util.ContentExtractor;
import com.riskwarning.processing.util.FileGetter;
import com.riskwarning.processing.util.FileScanner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


@Slf4j
@Service
public class DocumentProcessingService {

    @Autowired
    private FileGetter fileGetter;

    @Autowired
    private FileScanner fileScanner;

    @Autowired
    private ContentExtractor contentExtractor;

    
    public List<String> processDocument(Long projectId, List<String> urls, List<String> paths) {
        List<String> results = new ArrayList<>();
        for(String url : urls){
            results.add(process(fileGetter.getFromUrl(url), projectId));
        }
        for(String path : paths){
            results.add(process(fileGetter.getFromPath(path), projectId));
        }
        return results;
    }

    private String process(File documentFile, Long projectId){
        String targetInternalPath = Constants.getInternalDirPath(projectId);
        File targetInternalFile = new File(targetInternalPath, StringUtils.generateFileName(projectId, "") + ".txt");
        File parentDir = targetInternalFile.getParentFile();
        if(!parentDir.exists()){
            parentDir.mkdirs();
        }
        try (BufferedWriter writer = Files.newBufferedWriter(
                targetInternalFile.toPath(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        )){
            FileGetter.FileMetadata metadata = fileGetter.getFileMetadata(documentFile);
            // 验证文件
            if (!fileGetter.validateFile(documentFile, metadata.getFileType())) {
                throw new RuntimeException("文件验证失败");
            }

            // 步骤2: 分页扫描
            List<FileScanner.PageContent> pages = fileScanner.scanByPage(documentFile);
            log.info("[Document Extract] file={}, bytes={}, pages={}",
                    documentFile.getAbsolutePath(), documentFile.length(), pages.size());

            long totalExtractedCharacters = 0L;
            int totalSegments = 0;
            int writtenSegments = 0;
            // 步骤3: 逐页提取文本片段并顺序写入同一个文件
            // BufferedWriter is shared by all pages and is not thread-safe.
            for (FileScanner.PageContent page : pages) {
                String pageText = page.getText();
                int extractedCharacters = pageText == null ? 0 : pageText.length();
                totalExtractedCharacters += extractedCharacters;

                // 为每页创建临时文档对象
                FileScanner.ScannedDocument pageDoc = new FileScanner.ScannedDocument();
                pageDoc.setFullText(pageText);
                pageDoc.setPages(Collections.singletonList(page));
                pageDoc.setFileName(metadata.getFileName());
                pageDoc.setTotalPages(pages.size());

                // 提取该页的文本片段
                List<ContentExtractor.TextSegment> segments = contentExtractor.extract(pageDoc);
                totalSegments += segments.size();
                int pageWrittenSegments = 0;
                for (ContentExtractor.TextSegment segment : segments) {
                    if (segment != null && segment.getText() != null && !segment.getText().trim().isEmpty()) {
                        writer.write(segment.getText());
                        writer.newLine();
                        writtenSegments++;
                        pageWrittenSegments++;
                    }
                }
                log.info("[Document Extract] file={}, page={}, extractedChars={}, segments={}, writtenSegments={}",
                        documentFile.getAbsolutePath(), page.getPageNumber(), extractedCharacters,
                        segments.size(), pageWrittenSegments);
            }

            // 必须先刷新缓冲区，否则这里读取到的文件长度可能仍然是 0。
            writer.flush();
            long internalFileBytes = Files.size(targetInternalFile.toPath());
            log.info("[Document Extract] completed: source={}, internalFile={}, pages={}, "
                            + "extractedChars={}, segments={}, writtenSegments={}, internalFileBytes={}",
                    documentFile.getAbsolutePath(), targetInternalFile.getAbsolutePath(), pages.size(),
                    totalExtractedCharacters, totalSegments, writtenSegments, internalFileBytes);

            if (writtenSegments == 0 || internalFileBytes == 0) {
                throw new IllegalStateException(String.format(
                        "文档未提取到可供批处理的有效文本: file=%s, pages=%d, extractedChars=%d, "
                                + "segments=%d, writtenSegments=%d。可能原因：扫描件未经过OCR、Word内容位于表格/文本框中，或内容被过滤规则全部过滤。",
                        documentFile.getAbsolutePath(), pages.size(), totalExtractedCharacters,
                        totalSegments, writtenSegments));
            }
            return targetInternalFile.getAbsolutePath();
        } catch (Exception e) {
            log.error("[Document Extract] failed: source={}, internalFile={}, error={}",
                    documentFile.getAbsolutePath(), targetInternalFile.getAbsolutePath(), e.getMessage(), e);
            throw new RuntimeException("文档内容提取失败: " + documentFile.getAbsolutePath() + ", " + e.getMessage(), e);
        }
    }
}

