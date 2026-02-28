package com.familyhobbies.associationservice.batch.writer;

import com.familyhobbies.associationservice.entity.Association;
import com.familyhobbies.associationservice.repository.AssociationRepository;
import com.familyhobbies.common.event.AssociationSyncedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Batch writer that upserts Association entities to PostgreSQL
 * and publishes sync events to Kafka.
 *
 * <p>Uses {@link AssociationRepository#saveAll} for batch persistence.
 */
@Component
public class HelloAssoItemWriter implements ItemWriter<Association> {

    private static final Logger log = LoggerFactory.getLogger(HelloAssoItemWriter.class);
    private static final String TOPIC = "family-hobbies.association.synced";

    private final AssociationRepository associationRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public HelloAssoItemWriter(AssociationRepository associationRepository,
                               KafkaTemplate<String, Object> kafkaTemplate) {
        this.associationRepository = associationRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void write(Chunk<? extends Association> chunk) {
        List<? extends Association> items = chunk.getItems();
        if (items.isEmpty()) {
            return;
        }

        List<Association> saved = associationRepository.saveAll((List<Association>) items);
        log.info("Batch wrote {} associations to database", saved.size());

        for (Association association : saved) {
            try {
                AssociationSyncedEvent event = new AssociationSyncedEvent(
                        association.getId(),
                        association.getHelloassoSlug(),
                        association.getName(),
                        association.getStatus() != null ? association.getStatus().name() : "UNKNOWN"
                );
                kafkaTemplate.send(TOPIC, association.getHelloassoSlug(), event);
                log.debug("Published AssociationSyncedEvent: slug={}",
                        association.getHelloassoSlug());
            } catch (Exception e) {
                log.error("Failed to publish AssociationSyncedEvent for slug={}: {}",
                        association.getHelloassoSlug(), e.getMessage(), e);
            }
        }
    }

}
