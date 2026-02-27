package com.familyhobbies.associationservice.repository;

import com.familyhobbies.associationservice.entity.Association;
import com.familyhobbies.associationservice.entity.enums.AssociationCategory;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * JPA Specification builder for dynamic {@link Association} search queries.
 * Combines city, category, and keyword filters into a single WHERE clause.
 */
public final class AssociationSpecification {

    private AssociationSpecification() {
        // Utility class — no instantiation
    }

    /**
     * Builds a dynamic specification combining optional filters.
     *
     * @param city     filter by city (case-insensitive), nullable
     * @param category filter by association category, nullable
     * @param keyword  search across name and description (case-insensitive LIKE), nullable
     * @return a composed {@link Specification} with all non-null filters AND-ed together
     */
    public static Specification<Association> withFilters(String city,
                                                         AssociationCategory category,
                                                         String keyword) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (city != null && !city.isBlank()) {
                predicates.add(
                    criteriaBuilder.equal(
                        criteriaBuilder.lower(root.get("city")),
                        city.toLowerCase()
                    )
                );
            }

            if (category != null) {
                predicates.add(
                    criteriaBuilder.equal(root.get("category"), category)
                );
            }

            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.toLowerCase() + "%";
                Predicate nameLike = criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("name")),
                    pattern
                );
                Predicate descriptionLike = criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("description")),
                    pattern
                );
                predicates.add(criteriaBuilder.or(nameLike, descriptionLike));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
