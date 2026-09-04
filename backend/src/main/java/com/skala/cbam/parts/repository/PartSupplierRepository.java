package com.skala.cbam.parts.repository;

import com.skala.cbam.parts.entity.PartSupplier;
import com.skala.cbam.parts.entity.PartSupplierStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PartSupplierRepository extends JpaRepository<PartSupplier, Long> {

    Optional<PartSupplier> findByPartIdAndSupplierIdAndStatus(
            Long partId, Long supplierId, PartSupplierStatus status);

    @Query("""
            select ps from PartSupplier ps
            join fetch ps.part p
            where p.id in :partIds
              and ps.supplierId in :supplierIds
              and ps.status = :status
            """)
    List<PartSupplier> findAllActiveRelations(
            @Param("partIds") Collection<Long> partIds,
            @Param("supplierIds") Collection<Long> supplierIds,
            @Param("status") PartSupplierStatus status);
}
