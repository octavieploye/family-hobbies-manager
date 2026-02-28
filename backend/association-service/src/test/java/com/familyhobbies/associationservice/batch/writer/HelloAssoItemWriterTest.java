package com.familyhobbies.associationservice.batch.writer;

import com.familyhobbies.associationservice.entity.Association;
import com.familyhobbies.associationservice.entity.enums.AssociationStatus;
import com.familyhobbies.associationservice.repository.AssociationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.item.Chunk;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HelloAssoItemWriterTest {

    @Mock
    private AssociationRepository associationRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private HelloAssoItemWriter writer;

    @BeforeEach
    void setUp() {
        writer = new HelloAssoItemWriter(associationRepository, kafkaTemplate);
    }

    @Test
    @DisplayName("Should saveAll and publish Kafka events for each association")
    void shouldSaveAllAndPublishEvents() throws Exception {
        Association assoc = new Association();
        assoc.setId(1L);
        assoc.setName("Test Association");
        assoc.setHelloassoSlug("test-slug");
        assoc.setStatus(AssociationStatus.ACTIVE);

        when(associationRepository.saveAll(any(List.class))).thenReturn(List.of(assoc));

        Chunk<Association> chunk = new Chunk<>(assoc);
        writer.write(chunk);

        verify(associationRepository).saveAll(any(List.class));
        verify(kafkaTemplate).send(anyString(), anyString(), any());
    }
}
