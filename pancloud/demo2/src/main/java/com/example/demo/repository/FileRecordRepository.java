package com.example.demo.repository;

import com.example.demo.model.FileRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileRecordRepository extends JpaRepository<FileRecord, Long> {
    void deleteById(Long id); // 删除通过 ID 查找的记录
}
