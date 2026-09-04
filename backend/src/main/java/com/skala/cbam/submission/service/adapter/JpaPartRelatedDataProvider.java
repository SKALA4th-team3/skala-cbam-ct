package com.skala.cbam.submission.service.adapter;

import com.skala.cbam.parts.entity.PartSupplier;
import com.skala.cbam.parts.entity.PartSupplierStatus;
import com.skala.cbam.submission.service.port.PartRelatedDataProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 현재 Parts 엔티티에서 Submission API에 필요한 공급 관계와 부품 정보를 읽는다. */
@Component
@Transactional(readOnly = true)
public class JpaPartRelatedDataProvider implements PartRelatedDataProvider {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<PartSupplierTarget> findActiveTargets(Long supplierId, Long partId) {
        return entityManager.createQuery("""
                        select ps
                          from PartSupplier ps
                          join fetch ps.part p
                         where ps.status = :status
                           and (:supplierId is null or ps.supplierId = :supplierId)
                           and (:partId is null or p.id = :partId)
                         order by p.partCode, ps.supplierId
                        """, PartSupplier.class)
                .setParameter("status", PartSupplierStatus.ACTIVE)
                .setParameter("supplierId", supplierId)
                .setParameter("partId", partId)
                .getResultList()
                .stream()
                .map(relation -> new PartSupplierTarget(
                        relation.getId(),
                        relation.getSupplierId(),
                        relation.getPart().getId(),
                        relation.getPart().getPartName()))
                .toList();
    }

    @Override
    public Optional<PartInfo> findPartInfo(Long partSupplierId) {
        return Optional.ofNullable(entityManager.find(PartSupplier.class, partSupplierId))
                .map(relation -> new PartInfo(
                        relation.getPart().getId(),
                        relation.getPart().getPartName(),
                        null));
    }
}
