package com.trilong.kpibackend.modules.salepro.repository;

import com.trilong.kpibackend.modules.salepro.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {
}
