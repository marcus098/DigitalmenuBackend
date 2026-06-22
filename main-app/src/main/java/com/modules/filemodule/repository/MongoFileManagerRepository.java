package com.modules.filemodule.repository;

import com.modules.filemodule.FileManagerLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MongoFileManagerRepository extends MongoRepository<FileManagerLog<?>, String> {
}
