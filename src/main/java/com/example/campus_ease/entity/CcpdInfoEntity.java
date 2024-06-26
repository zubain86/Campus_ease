package com.example.campus_ease.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class CcpdInfoEntity {
    @Id
    private String userId;

    private String firstName;

    private String lastName;

    private String email;

}
