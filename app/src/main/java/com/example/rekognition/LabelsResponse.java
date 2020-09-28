package com.example.rekognition;

import java.util.List;
import java.util.Map;

public class LabelsResponse {

    private List<Map<String,String>> labels;

    public List<Map<String, String>> getLabels() {
        return labels;
    }
    public void setLabels(List<Map<String, String>> labels) {
        this.labels = labels;
    }
    @Override
    public String toString() {
        return "DetectLabelsInImageResponse [labels=" + labels + "]";
    }
}
