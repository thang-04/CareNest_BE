package com.carenest.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.Date;

@Entity
@Table(name = "forgotpassword")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class ForgotPassword {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer fpid;

    @Column(nullable = false)
    private Integer otp;

    @Column(nullable = false)
    private Date expirationTime;

    @Column(name = "otp_verified", nullable = false)
    private boolean otpVerified;

    @OneToOne
    @JoinColumn(name = "user_id")
    private User user;
}
