package com.depromeet.stonebed.domain.feed.api;

import com.depromeet.stonebed.domain.feed.application.FeedService;
import com.depromeet.stonebed.domain.feed.dto.request.FeedBoostRequest;
import com.depromeet.stonebed.domain.feed.dto.request.FeedGetRequest;
import com.depromeet.stonebed.domain.feed.dto.response.FeedGetResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "7. [피드]", description = "피드 관련 API입니다.")
@RestController
@RequestMapping("/feed")
@RequiredArgsConstructor
public class FeedController {
    private final FeedService feedService;

    @Operation(summary = "피드 조회", description = "내 피드를 조회하는 API입니다.")
    @GetMapping
    public FeedGetResponse getFeed(
            @RequestParam(required = false) String cursor, @RequestParam int limit) {
        FeedGetRequest request = new FeedGetRequest(cursor, limit);
        return feedService.getFeed(request);
    }

    @Operation(summary = "부스트 생성", description = "미션 기록에 부스트를 생성하는 API입니다.")
    @PostMapping("/boost")
    public ResponseEntity<Void> postFeed(final @Valid @RequestBody FeedBoostRequest request) {
        feedService.createBoost(request.missionRecordId(), request.count());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
