package com.trilong.kpibackend.modules.salepro.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectDetails implements Serializable {
    private String overview;
    private String locationMap;
    private List<String> trainingMaterials;
    private List<String> images360;
    private List<String> documents;
}
