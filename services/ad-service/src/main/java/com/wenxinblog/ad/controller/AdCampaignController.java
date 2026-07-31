package com.wenxinblog.ad.controller;

import com.wenxinblog.ad.dto.CampaignRequest;
import com.wenxinblog.ad.dto.CampaignStats;
import com.wenxinblog.ad.dto.Result;
import com.wenxinblog.ad.entity.AdCampaign;
import com.wenxinblog.ad.service.AdCampaignService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/v1/campaigns")
@RequiredArgsConstructor
public class AdCampaignController {

    private final AdCampaignService campaignService;

    @PostMapping
    public Mono<Result<AdCampaign>> create(@RequestBody CampaignRequest req,
                                           @RequestHeader("X-User-Id") String advertiserId) {
        return campaignService.createCampaign(advertiserId, req)
                .map(Result::success);
    }

    @PutMapping("/{id}")
    public Mono<Result<AdCampaign>> update(@RequestHeader("X-User-Id") String advertiserId,
                                           @PathVariable Long id, @RequestBody CampaignRequest req) {
        return campaignService.updateCampaign(advertiserId, id, req)
                .map(Result::success);
    }

    @GetMapping("/{id}")
    public Mono<Result<AdCampaign>> get(@RequestHeader("X-User-Id") String advertiserId,
                                        @PathVariable Long id) {
        return campaignService.getCampaign(advertiserId, id)
                .map(Result::success);
    }

    @GetMapping
    public Mono<Result<List<AdCampaign>>> list(
            @RequestHeader("X-User-Id") String advertiserId,
            @RequestParam(required = false) String status) {
        return campaignService.listCampaigns(advertiserId, status)
                .collectList()
                .map(Result::success);
    }

    @PutMapping("/{id}/pause")
    public Mono<Result<AdCampaign>> pause(@RequestHeader("X-User-Id") String advertiserId,
                                          @PathVariable Long id) {
        return campaignService.pauseCampaign(advertiserId, id)
                .map(Result::success);
    }

    @PutMapping("/{id}/activate")
    public Mono<Result<AdCampaign>> activate(@RequestHeader("X-User-Id") String advertiserId,
                                             @PathVariable Long id) {
        return campaignService.activateCampaign(advertiserId, id)
                .map(Result::success);
    }

    @GetMapping("/{id}/stats")
    public Mono<Result<CampaignStats>> stats(@RequestHeader("X-User-Id") String advertiserId,
                                             @PathVariable Long id) {
        return campaignService.getCampaignStats(advertiserId, id)
                .map(Result::success);
    }
}
