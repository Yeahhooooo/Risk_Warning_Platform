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
            // 步骤3: 逐页提取文本片段（并行处理）
            pages.parallelStream()
                    .forEach(page -> {
                        // 为每页创建临时文档对象
                        FileScanner.ScannedDocument pageDoc = new FileScanner.ScannedDocument();
                        pageDoc.setFullText(page.getText());
                        pageDoc.setPages(Collections.singletonList(page));
                        pageDoc.setFileName(metadata.getFileName());
                        pageDoc.setTotalPages(pages.size());

                        // 提取该页的文本片段
                        List<ContentExtractor.TextSegment> segments = contentExtractor.extract(pageDoc);
                        try {
                            for(ContentExtractor.TextSegment segment : segments){
                                if(segment != null && segment.getText() != null && !segment.getText().isEmpty()){
                                    writer.write(segment.getText());
                                    writer.newLine();
                                }
                            }
                        } catch (Exception e) {
                            log.error("Error writing text segments for page {}: {}", page.getPageNumber(), e.getMessage());
                            throw new RuntimeException(e);
                        }
                    });
            return targetInternalFile.getAbsolutePath();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

