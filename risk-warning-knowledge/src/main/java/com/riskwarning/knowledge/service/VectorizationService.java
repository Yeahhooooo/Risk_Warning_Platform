package com.riskwarning.knowledge.service;

import java.util.List;


public interface VectorizationService {
    
   
    float[] vectorize(String text);
    
    
    List<float[]> batchVectorize(List<String> texts);
    
    
    default int getDimension() {
        return 768;
    }
}

