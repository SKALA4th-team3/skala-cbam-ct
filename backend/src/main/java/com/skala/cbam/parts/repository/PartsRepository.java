package com.skala.cbam.parts.repository;

import com.skala.cbam.parts.entity.Part;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PartsRepository extends JpaRepository<Part, Long>, JpaSpecificationExecutor<Part> {

    boolean existsByPartCode(String partCode);

    boolean existsByPartName(String partName);

    boolean existsByPartNameAndIdNot(String partName, Long id);
}
