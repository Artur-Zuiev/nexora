package com.nexora.os.lead;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
@Entity
@Table(name="leads")
public class Lead {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @NotBlank public String name;
 @Email @NotBlank public String email;
 @Column(length=2000) public String note;
 @Enumerated(EnumType.STRING) @Column(nullable=false) public Status status=Status.NEW;
 public enum Status { NEW, CONTACTED, QUALIFIED, WON, LOST }
}