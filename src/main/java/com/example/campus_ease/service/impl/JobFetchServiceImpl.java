package com.example.campus_ease.service.impl;

import com.example.campus_ease.dao.JobPostedRepo;
import com.example.campus_ease.dao.StudentInfoRepo;
import com.example.campus_ease.entity.JobPostedEntity;
import com.example.campus_ease.mapper.JobPostedMapper;
import com.example.campus_ease.response.*;
import com.example.campus_ease.service.JobFetchService;
import com.example.campus_ease.shared.dto.JobPostedDto;
import com.example.campus_ease.shared.utils.enums.Branch;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import org.hibernate.query.NativeQuery;
import org.springframework.boot.autoconfigure.batch.BatchProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class JobFetchServiceImpl implements JobFetchService {
    private JobPostedRepo jobPostedRepo;

    private JobPostedMapper jobPostedMapper;

   private StudentInfoRepo studentInfoRepo;

   private EntityManager entityManager;

    public JobFetchServiceImpl(JobPostedRepo jobPostedRepo, JobPostedMapper jobPostedMapper, StudentInfoRepo studentInfoRepo, EntityManager entityManager) {
        this.jobPostedRepo = jobPostedRepo;
        this.jobPostedMapper = jobPostedMapper;
        this.studentInfoRepo = studentInfoRepo;
        this.entityManager = entityManager;
    }

    @Override
    public JobRes getJobs (String user_id) {
        Long departmentId = studentInfoRepo.findById(user_id).get().getBranchId();
        ArrayList<JobPostedEntity> jobPostedEntities = jobPostedRepo.findByBranchId(departmentId);
        ArrayList<Long> unfilledID = new ArrayList<>();
        ArrayList<Long> filledID = new ArrayList<>();
        for (JobPostedEntity jobPostedEntity : jobPostedEntities) {
            ArrayList<String> appliedStudents = jobPostedEntity.getManagement().getAppliedStudents();
            if(!appliedStudents.contains(user_id)){
                unfilledID.add(jobPostedEntity.getId());
            }
            else
            {
                filledID.add(jobPostedEntity.getId());
            }
        }
        ArrayList<JobPostedDto> standardUnfilled = new ArrayList<>();
        for (Long job_id: unfilledID) {
            JobPostedEntity jobPostedEntity = jobPostedRepo.findById(job_id).get();
            standardUnfilled.add(jobPostedMapper.jobPostedEntityToJobPostedDto(jobPostedEntity));
        }

        ArrayList<JobPostedDto> standardFilled = new ArrayList<>();
        for (Long job_id: filledID) {
            JobPostedEntity jobPostedEntity = jobPostedRepo.findById(job_id).get();
            standardFilled.add(jobPostedMapper.jobPostedEntityToJobPostedDto(jobPostedEntity));
        }

        ArrayList<JobResponse> unfilled = new ArrayList<>();
        for (JobPostedDto jobPostedDto:standardUnfilled) {
            JobResponse jobResponse = jobPostedMapper.jobPostedDtoToJobResponse(jobPostedDto);
            jobResponse.setBranches(getBranchName(jobPostedDto));
            unfilled.add(jobResponse);
        }

        ArrayList<JobResponse> filled = new ArrayList<>();
        for (JobPostedDto jobPostedDto:standardFilled) {
            JobResponse jobResponse = jobPostedMapper.jobPostedDtoToJobResponse(jobPostedDto);
            jobResponse.setBranches(getBranchName(jobPostedDto));
            filled.add(jobResponse);
        }

        JobRes jobRes = new JobRes();
        jobRes.setFilled(filled);
        jobRes.setUnfilled(unfilled);

        return jobRes;
    }

    @Override
    public List<JobsCcpdRes> getCcpdJobs() {

        String query = "WITH pika1 AS(\n" +
                "SELECT company_name, COUNT(unnested_students) AS registered_candidates\n" +
                "FROM \"public\".job_posted_entity AS jp\n" +
                "JOIN \"public\".job_management_entity AS jm ON jp.management_id = jm.id\n" +
                "LEFT JOIN (SELECT id,unnested_students FROM \"public\".job_management_entity,unnest(applied_students) AS unnested_students\n" +
                ") AS p ON p.id = jm.id\n" +
                "GROUP BY company_name\n" +
                "),\n" +
                "pika2 AS(\n" +
                "SELECT company_name,  COUNT(first_name) AS total_candidates FROM \"public\".job_posted_entity AS jp\n" +
                "LEFT JOIN \"public\".student_info_entity  AS se ON jp.branch_id = se.branch_id\n" +
                "GROUP BY company_name \n" +
                "),\n" +
                "pika3 AS(\n" +
                "\tSELECT company_name, end_date FROM \"public\".job_posted_entity GROUP BY (company_name,end_date)\n" +
                "),\n" +
                "pika4 AS(\n" +
                "SELECT company_name, ARRAY_AGG(id) AS ids FROM \"public\".job_posted_entity GROUP BY company_name\n" +
                ")\n" +
                "SELECT JSON_BUILD_OBJECT('id',pika4.ids,'companyName',pika1.company_name,'registered', registered_candidates, 'pending', total_candidates-registered_candidates,'driveDate',end_date )AS jobs_json FROM pika1 JOIN pika2 ON pika1.company_name\n" +
                "= pika2.company_name JOIN pika3 ON pika3.company_name = pika1.company_name JOIN \n" +
                "pika4 ON pika4.company_name = pika1.company_name\n" +
                "\n";
        NativeQuery nativeQuery =  entityManager.createNativeQuery(query).unwrap(org.hibernate.query.NativeQuery.class);
        List<Object> resultList = nativeQuery.getResultList();

        List<JobsCcpdRes> result = new ArrayList<>();
        for (Object o : resultList) {
            ObjectMapper objectMapper = new ObjectMapper();
            try {
               JobsCcpdRes jobsCcpdRes = objectMapper.readValue(o.toString(), JobsCcpdRes.class);
                result.add(jobsCcpdRes);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
        return result;
    }

    @Override
    public JobsDataRes getJobsData() {
        Long placed = jobPostedRepo.findPlaced();
        Long unplaced = jobPostedRepo.findUnplaced();
        Long totalOffers = jobPostedRepo.findtotalOffers();
        JobsDataRes jobsDataRes = new JobsDataRes();
        jobsDataRes.setPlaced(placed);
        jobsDataRes.setUnplaced(unplaced);
        jobsDataRes.setUpcomingDrives(jobPostedRepo.findUpcomingDrives());
        jobsDataRes.setTotalOffers(totalOffers);
        return jobsDataRes;
    }

    @Override
    public JobsInfoRes getJobsInfo(ArrayList<Long> id) {
        String query = "WITH pika AS(\n" +
                "SELECT ARRAY_AGG(id) AS id, ARRAY_AGG(management_id) AS management_id, ARRAY_AGG(branch_id) AS branch_ids,company_name, end_date, expctc, file, job_description, job_profile\n" +
                ", reg_link, start_date, minimum_percentage, job_location, user_id, website_url FROM \"public\".job_posted_entity WHERE\n" +
                "id IN :id\n" +
                "GROUP BY (company_name, end_date, expctc, file, job_description, job_profile\n" +
                ", reg_link, start_date, minimum_percentage, job_location, user_id, website_url)\n" +
                "),\n" +
                "pika2 AS\n" +
                "(\n" +
                "\tSELECT *, ce.user_id AS ccpd_id FROM pika JOIN \"public\".ccpd_info_entity AS ce ON ce.user_id = pika.user_id\n" +
                "),\n" +
                "pika3 AS(\n" +
                "\tSELECT * FROM pika2, unnest(branch_ids) AS branch_id \n" +
                ")\n" +
                "SELECT JSON_BUILD_OBJECT('id',id,'management_id',management_id,'branch_id',branch_ids,'ccpd',JSON_BUILD_OBJECT('ccpd_id',ccpd_id,'email',pika3.email,'first_name',pika3.first_name,'last_name',pika3.last_name),'company_name',company_name,'end_date',end_date,'expctc',expctc,'file',file,'job_description',job_description,'job_profile',job_profile,\n" +
                "\t\t\t\t\t\t'reg_link',reg_link,'start_date',start_date,'minimum_percentage',minimum_percentage,'job_location',job_location,'website_url',website_url,'students',JSON_AGG(JSON_BUILD_OBJECT('student_id',se.user_id,'college_admission_number',college_admission_number,'email',se.email\n" +
                "\t\t\t\t\t\t,'first_name',se.first_name,'last_name',se.last_name,'percentage',percentage,'roll_number',roll_number,'sgpa',sgpa,'branch_id',se.branch_id))) FROM pika3 JOIN \"public\".student_info_entity AS se ON \n" +
                "pika3.branch_id = se.branch_id \n" +
                "GROUP BY (id, management_id, branch_ids, company_name, end_date, expctc, file, job_description, job_profile\n" +
                ", reg_link, start_date, minimum_percentage, job_location,website_url,ccpd_id,pika3.email,pika3.first_name,pika3.last_name)\n" +
                "\n";
        NativeQuery nativeQuery =  entityManager.createNativeQuery(query).unwrap(org.hibernate.query.NativeQuery.class);
        nativeQuery.setParameter("id",id);
        Object result = nativeQuery.getSingleResult();
        ObjectMapper objectMapper = new ObjectMapper();
        JobsInfoRes res;
        try {
             res = objectMapper.readValue(result.toString(), JobsInfoRes.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        return res;
    }

    @Override
    public StudentsJobsInfoRes getStudentsJobsInfo(Long id) {
        String query = "WITH pika AS(\n" +
                "SELECT company_name FROM \"public\".job_posted_entity WHERE id = :id\n" +
                ")\n" +
                "SELECT JSON_BUILD_OBJECT('id',ARRAY_AGG(id),'company_name',pika.company_name,'end_date',end_date,'expctc',expctc,'file',file,'job_description',job_description,'job_profile',job_profile,'reg_link',reg_link,'start_date',start_date,'minimum_percentage',minimum_percentage,'job_location',job_location,'website_url',website_url,'branch_id',ARRAY_AGG(branch_id)) FROM pika JOIN \"public\".job_posted_entity AS je ON pika.company_name\n" +
                "= je.company_name\n" +
                "GROUP BY (pika.company_name,end_date,expctc,file,job_description,job_profile,reg_link,start_date,minimum_percentage,job_location,website_url)\n";

    NativeQuery nativeQuery =  entityManager.createNativeQuery(query).unwrap(org.hibernate.query.NativeQuery.class);
    nativeQuery.setParameter("id",id);
    Object o = nativeQuery.getSingleResult();
    ObjectMapper objectMapper = new ObjectMapper();
    StudentsJobsInfoRes studentsJobsInfoRes;
        try {
            studentsJobsInfoRes = objectMapper.readValue(o.toString(), StudentsJobsInfoRes.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        ArrayList<Long> branch_id = studentsJobsInfoRes.getBranch_id();
        for (Long branchId:branch_id) {
           studentsJobsInfoRes.getBranches().add(getBranchNameByString(branchId));
        }

        return studentsJobsInfoRes;
    }


    String  getBranchNameByString(Long branchId)
    {
        String name;
        if(branchId.equals(Branch.CS.getBranchId()))
            name = "CS";
        else if(branchId .equals(Branch.IT.getBranchId()))
            name = "IT";
        else if(branchId.equals(Branch.EN.getBranchId()))
            name = "EN";
        else if (branchId.equals(Branch.ECE.getBranchId()))
            name = "ECE";
        else if(branchId.equals(Branch.ME.getBranchId()))
            name = "ME";
        else if(branchId.equals(Branch.CE.getBranchId()))
            name = "CE";
        else if(branchId.equals(Branch.CSE.getBranchId()))
            name = "CSE";
        else if(branchId.equals(Branch.CSE_AIML.getBranchId()))
            name = "CSE-AIML";
        else if(branchId.equals(Branch.CSE_DS.getBranchId()))
            name = "CSE-DS";
        else
            name = null;
        return name;
    }




    String  getBranchName(JobPostedDto jobPostedDto)
    {
        String name;
       if(jobPostedDto.getBranchId().equals(Branch.CS.getBranchId()))
           name = "CS";
         else if(jobPostedDto.getBranchId() .equals(Branch.IT.getBranchId()))
             name = "IT";
         else if(jobPostedDto.getBranchId().equals(Branch.EN.getBranchId()))
             name = "EN";
         else if (jobPostedDto.getBranchId().equals(Branch.ECE.getBranchId()))
             name = "ECE";
         else if(jobPostedDto.getBranchId().equals(Branch.ME.getBranchId()))
             name = "ME";
         else if(jobPostedDto.getBranchId().equals(Branch.CE.getBranchId()))
             name = "CE";
         else if(jobPostedDto.getBranchId().equals(Branch.CSE.getBranchId()))
                name = "CSE";
            else if(jobPostedDto.getBranchId().equals(Branch.CSE_AIML.getBranchId()))
                name = "CSE-AIML";
            else if(jobPostedDto.getBranchId().equals(Branch.CSE_DS.getBranchId()))
                name = "CSE-DS";
         else
             name = null;
         return name;
    }


}
