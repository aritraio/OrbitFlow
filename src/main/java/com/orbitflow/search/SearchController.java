package com.orbitflow.search;

import com.orbitflow.common.security.UserPrincipal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {
    private final SearchService service;

    @GetMapping
    public List<SearchService.SearchHit> search(@AuthenticationPrincipal UserPrincipal p,
                                                @RequestParam("q") String q,
                                                @RequestParam(value = "projectId", required = false) UUID projectId) {
        return service.search(p.getId(), q, projectId);
    }
}
