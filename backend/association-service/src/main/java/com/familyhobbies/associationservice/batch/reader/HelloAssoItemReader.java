package com.familyhobbies.associationservice.batch.reader;

import com.familyhobbies.associationservice.adapter.HelloAssoClient;
import com.familyhobbies.associationservice.adapter.dto.HelloAssoDirectoryRequest;
import com.familyhobbies.associationservice.adapter.dto.HelloAssoDirectoryResponse;
import com.familyhobbies.associationservice.adapter.dto.HelloAssoOrganization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemReader;
import org.springframework.stereotype.Component;

import java.util.LinkedList;
import java.util.Queue;

/**
 * Paginated reader for the HelloAsso organization directory.
 *
 * <p>Fetches pages of {@value #PAGE_SIZE} organizations via
 * {@link HelloAssoClient#searchOrganizations} and buffers them
 * in an internal queue. Returns {@code null} when all pages are
 * exhausted (signals end-of-data to Spring Batch).
 */
@Component
@StepScope
public class HelloAssoItemReader implements ItemReader<HelloAssoOrganization> {

    private static final Logger log = LoggerFactory.getLogger(HelloAssoItemReader.class);
    private static final int PAGE_SIZE = 50;

    private final HelloAssoClient helloAssoClient;

    private int currentPageIndex = 1;
    private String continuationToken = null;
    private boolean exhausted = false;
    private final Queue<HelloAssoOrganization> buffer = new LinkedList<>();
    private int totalRead = 0;

    public HelloAssoItemReader(HelloAssoClient helloAssoClient) {
        this.helloAssoClient = helloAssoClient;
    }

    @Override
    public HelloAssoOrganization read() {
        if (!buffer.isEmpty()) {
            totalRead++;
            return buffer.poll();
        }

        if (exhausted) {
            log.info("HelloAsso reader exhausted: totalRead={}", totalRead);
            return null;
        }

        fetchNextPage();

        if (buffer.isEmpty()) {
            log.info("HelloAsso reader exhausted (empty page): totalRead={}", totalRead);
            exhausted = true;
            return null;
        }

        totalRead++;
        return buffer.poll();
    }

    private void fetchNextPage() {
        HelloAssoDirectoryRequest request = HelloAssoDirectoryRequest.builder()
                .pageSize(PAGE_SIZE)
                .pageIndex(continuationToken == null ? currentPageIndex : null)
                .continuationToken(continuationToken)
                .build();

        log.debug("Fetching HelloAsso directory page: pageIndex={}, continuationToken={}",
                currentPageIndex, continuationToken != null ? "present" : "null");

        HelloAssoDirectoryResponse response = helloAssoClient
                .searchOrganizations(request)
                .block();

        if (response == null || response.data() == null || response.data().isEmpty()) {
            exhausted = true;
            return;
        }

        buffer.addAll(response.data());
        currentPageIndex++;

        if (response.pagination() != null
                && response.pagination().continuationToken() != null) {
            continuationToken = response.pagination().continuationToken();
        } else {
            exhausted = true;
        }

        log.debug("Fetched {} organizations from HelloAsso (page {}), exhausted={}",
                response.data().size(), currentPageIndex - 1, exhausted);
    }
}
