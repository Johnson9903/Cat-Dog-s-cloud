package com.example.demo.service;

import com.example.demo.model.FileRecord;
import com.example.demo.repository.FileRecordRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.Optional;

@Service
public class FileService {

    private static final Logger logger = LoggerFactory.getLogger(FileService.class);

    @Value("${file.upload-dir}")
    private String uploadDir;

    private final FileRecordRepository fileRecordRepository;

    // 构造函数注入
    public FileService(FileRecordRepository fileRecordRepository) {
        this.fileRecordRepository = fileRecordRepository;
    }

    /**
     * 删除文件及数据库中的文件记录
     *
     * @param id 文件的数据库ID
     * @return 删除结果的描述信息
     */
    @Transactional
    public String deleteFile(Long id) throws IOException {
        // 查找数据库中的文件记录
        Optional<FileRecord> fileRecordOptional = fileRecordRepository.findById(id);

        if (fileRecordOptional.isPresent()) {
            FileRecord fileRecord = fileRecordOptional.get();
            Path path = Paths.get(fileRecord.getFilePath()).normalize();

            // 检查文件是否存在
            if (Files.exists(path)) {
                // 删除文件
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    throw new IOException("Failed to delete file: " + path.toString(), e);
                }

                // 删除数据库记录
                fileRecordRepository.deleteById(id);

                return "File and database record deleted successfully for: " + fileRecord.getFilename();
            } else {
                return "File not found at path: " + path.toString();
            }
        } else {
            return "No database record found for id: " + id;
        }
    }
}
