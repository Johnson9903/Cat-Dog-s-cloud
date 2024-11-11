package com.example.demo.controllor;

import com.example.demo.model.FileRecord;
import com.example.demo.repository.FileRecordRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import java.io.*;
import java.nio.file.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/files")
public class FileController {

    private static final Logger logger = LoggerFactory.getLogger(FileController.class);

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Autowired
    private DataSource dataSource;

    private final FileRecordRepository fileRecordRepository;

    public FileController(FileRecordRepository fileRecordRepository) {
        this.fileRecordRepository = fileRecordRepository;
    }

    // 确保上传目录存在
    private void ensureUploadDirExists() throws IOException {
        Path path = Paths.get(uploadDir);
        if (!Files.exists(path)) {
            Files.createDirectories(path);
        }
    }

    // 文件上传
    @PostMapping("/upload")
    public ResponseEntity<String> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            ensureUploadDirExists();
            Path copyLocation = Paths.get(uploadDir).resolve(file.getOriginalFilename()).normalize();
            Files.copy(file.getInputStream(), copyLocation, StandardCopyOption.REPLACE_EXISTING);

            FileRecord fileRecord = new FileRecord();
            fileRecord.setFilename(file.getOriginalFilename());
            fileRecord.setFilePath(copyLocation.toString());
            fileRecordRepository.save(fileRecord);

            return ResponseEntity.status(HttpStatus.OK).body("上传成功： " + file.getOriginalFilename());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.EXPECTATION_FAILED).body("上传失败: " + file.getOriginalFilename());
        }
    }

    // 批量上传文件
    @PostMapping("/upload/batch")
    public ResponseEntity<String> uploadFiles(@RequestParam("files") List<MultipartFile> files) {
        StringBuilder resultMessage = new StringBuilder();
        files.forEach(file -> {
            ResponseEntity<String> response = uploadFile(file);
            resultMessage.append(response.getBody()).append("\n");
        });
        return ResponseEntity.status(HttpStatus.OK).body(resultMessage.toString());
    }

    // 文件下载
    @GetMapping("/download/{filename}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String filename) {
        try {
            Path path = Paths.get(uploadDir).resolve(filename).normalize();
            Resource resource = new ByteArrayResource(Files.readAllBytes(path));
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(resource);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }

    // 批量下载文件
    @GetMapping("/download/batch")
    public void downloadFiles(@RequestParam List<String> filenames, HttpServletResponse response) throws IOException {
        response.setContentType("application/zip");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=files.zip");

        try (ZipOutputStream zipOut = new ZipOutputStream(response.getOutputStream())) {
            for (String filename : filenames) {
                Path filePath = Paths.get(uploadDir).resolve(filename).normalize();
                if (Files.exists(filePath)) {
                    zipOut.putNextEntry(new ZipEntry(filePath.getFileName().toString()));
                    Files.copy(filePath, zipOut);
                    zipOut.closeEntry();
                } else {
                }
            }
            zipOut.finish();
        } catch (IOException e) {
        }
    }

    // 根据文件名删除文件
    @DeleteMapping("/delete/{filename}")
    public ResponseEntity<String> deleteFileAndRecord(@PathVariable String filename) {
        try {
            // 删除文件
            Path path = Paths.get(uploadDir).resolve(filename).normalize();
            if (Files.deleteIfExists(path)) {

                // 使用注入的 DataSource 获取数据库连接并执行删除操作
                Connection connection = DataSourceUtils.getConnection(dataSource);
                String sql = "DELETE FROM file_record WHERE filename = ?";
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, filename);  // 设置参数
                    int rowsAffected = stmt.executeUpdate(); // 执行删除操作

                    return ResponseEntity.status(HttpStatus.OK).body("文件删除成功: " + filename);

                } catch (SQLException e) {
                    return ResponseEntity.status(HttpStatus.EXPECTATION_FAILED).body("错误: " + filename);
                } finally {
                    DataSourceUtils.releaseConnection(connection, dataSource);  // 释放数据库连接
                }
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("文件在哪里？： " + filename);
            }
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.EXPECTATION_FAILED).body("删不了文件: " + filename);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.EXPECTATION_FAILED).body("删不了数据库: " + filename);
        }
    }
    // 列出所有文件
    @GetMapping
    public ResponseEntity<List<String>> listFiles() {
        try {
            ensureUploadDirExists();
            List<String> files = Files.list(Paths.get(uploadDir))
                    .filter(Files::isRegularFile) // 只处理普通文件
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(files);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
