package com.familyhobbies.associationservice.batch.reader;

import com.familyhobbies.associationservice.adapter.HelloAssoClient;
import com.familyhobbies.associationservice.adapter.dto.HelloAssoDirectoryRequest;
import com.familyhobbies.associationservice.adapter.dto.HelloAssoDirectoryResponse;
import com.familyhobbies.associationservice.adapter.dto.HelloAssoOrganization;
import com.familyhobbies.associationservice.adapter.dto.HelloAssoPagination;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HelloAssoItemReaderTest {

    @Mock
    private HelloAssoClient helloAssoClient;

    private HelloAssoItemReader reader;

    @BeforeEach
    void setUp() {
        reader = new HelloAssoItemReader(helloAssoClient);
    }

    @Test
    @DisplayName("Should return organizations from first page")
    void shouldReturnOrganizationsFromFirstPage() throws Exception {
        HelloAssoOrganization org1 = HelloAssoOrganization.builder()
                .name("Club de Danse Paris").slug("club-danse-paris")
                .city("Paris").zipCode("75001").build();
        HelloAssoOrganization org2 = HelloAssoOrganization.builder()
                .name("Association Sport Lyon").slug("asso-sport-lyon")
                .city("Lyon").zipCode("69001").build();

        HelloAssoPagination pagination = new HelloAssoPagination(1, 50, 2, 1, null);
        HelloAssoDirectoryResponse response = new HelloAssoDirectoryResponse(
                List.of(org1, org2), pagination);

        when(helloAssoClient.searchOrganizations(any(HelloAssoDirectoryRequest.class)))
                .thenReturn(Mono.just(response));

        HelloAssoOrganization result1 = reader.read();
        HelloAssoOrganization result2 = reader.read();
        HelloAssoOrganization result3 = reader.read();

        assertThat(result1).isNotNull();
        assertThat(result1.name()).isEqualTo("Club de Danse Paris");
        assertThat(result2).isNotNull();
        assertThat(result2.name()).isEqualTo("Association Sport Lyon");
        assertThat(result3).isNull();
    }

    @Test
    @DisplayName("Should iterate across multiple pages using continuationToken")
    void shouldIterateMultiplePages() throws Exception {
        HelloAssoOrganization org1 = HelloAssoOrganization.builder()
                .name("Club de Danse Paris").slug("club-danse-paris").build();
        HelloAssoPagination page1Pagination = new HelloAssoPagination(
                1, 50, 100, 2, "token-page-2");
        HelloAssoDirectoryResponse page1Response = new HelloAssoDirectoryResponse(
                List.of(org1), page1Pagination);

        HelloAssoOrganization org2 = HelloAssoOrganization.builder()
                .name("Association Sport Lyon").slug("asso-sport-lyon").build();
        HelloAssoPagination page2Pagination = new HelloAssoPagination(
                2, 50, 100, 2, null);
        HelloAssoDirectoryResponse page2Response = new HelloAssoDirectoryResponse(
                List.of(org2), page2Pagination);

        when(helloAssoClient.searchOrganizations(any(HelloAssoDirectoryRequest.class)))
                .thenReturn(Mono.just(page1Response))
                .thenReturn(Mono.just(page2Response));

        HelloAssoOrganization result1 = reader.read();
        HelloAssoOrganization result2 = reader.read();
        HelloAssoOrganization result3 = reader.read();

        assertThat(result1).isNotNull();
        assertThat(result1.slug()).isEqualTo("club-danse-paris");
        assertThat(result2).isNotNull();
        assertThat(result2.slug()).isEqualTo("asso-sport-lyon");
        assertThat(result3).isNull();
    }

    @Test
    @DisplayName("Should return null immediately for empty directory")
    void shouldReturnNullForEmptyDirectory() throws Exception {
        HelloAssoPagination pagination = new HelloAssoPagination(1, 50, 0, 0, null);
        HelloAssoDirectoryResponse emptyResponse = new HelloAssoDirectoryResponse(
                List.of(), pagination);

        when(helloAssoClient.searchOrganizations(any(HelloAssoDirectoryRequest.class)))
                .thenReturn(Mono.just(emptyResponse));

        HelloAssoOrganization result = reader.read();
        assertThat(result).isNull();
    }
}
