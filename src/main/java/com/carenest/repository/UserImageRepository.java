package com.carenest.repository;

import com.carenest.entity.UserImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserImageRepository extends JpaRepository<UserImage, Long> {

    List<UserImage> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<UserImage> findByIdAndUserId(Long id, Long userId);
}
