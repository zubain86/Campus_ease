package com.example.campus_ease.response;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class StudentsJobsInfoRes {

    private Long id;

    @JsonIgnore
    ArrayList<Long> job_id;

    private String company_name;

    private String end_date;

    private String exp_ctc;

    private String file;

    private String job_description;

    private String job_profile;

    private String reg_link;

    private String start_date;

    private String minimum_percentage;

    private String job_location;

    private String website_url;

    @JsonIgnore
    private ArrayList<Long> branch_id;

    private ArrayList<String> branches = new ArrayList<>();
}
