package com.carenest.repository.meals;

import com.carenest.entity.meals.MealPhotoWindow;
import com.carenest.utils.enums.MealSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MealPhotoWindowRepository extends JpaRepository<MealPhotoWindow, MealSession> {}
