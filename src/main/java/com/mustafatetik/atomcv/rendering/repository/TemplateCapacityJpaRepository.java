package com.mustafatetik.atomcv.rendering.repository;

import com.mustafatetik.atomcv.rendering.domain.TemplateCapacity;
import org.springframework.data.jpa.repository.JpaRepository;

/** Package-private; reached through {@link MeasuredCapacities}. */
interface TemplateCapacityJpaRepository extends JpaRepository<TemplateCapacity, String> {
}
