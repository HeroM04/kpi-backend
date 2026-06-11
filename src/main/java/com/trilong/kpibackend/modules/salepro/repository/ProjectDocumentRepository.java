package com.trilong.kpibackend.modules.salepro.repository;

import com.trilong.kpibackend.modules.salepro.entity.ProjectDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectDocumentRepository extends JpaRepository<ProjectDocument, Long> {
    List<ProjectDocument> findByProjectIdOrderBySortOrderAsc(Long projectId);
}
