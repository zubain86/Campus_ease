package com.example.campus_ease.service;

import com.example.campus_ease.request.StudentArrayIds;
import com.example.campus_ease.shared.dto.JobPostedDto;

public interface JobPostedService {
    JobPostedDto addJob(JobPostedDto jobPostedDto);

    void jobFill(String userId, Long jobId);

    void addPlaced(StudentArrayIds studentArrayIds);
}
