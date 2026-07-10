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
    public Mono<Result<AdCampaign>> update(@PathVariable Long id, @RequestBody CampaignRequest req) {
        return campaignService.updateCampaign(id, req)
                .map(Result::success);
    }

    @GetMapping("/{id}")
    public Mono<Result<AdCampaign>> get(@PathVariable Long id) {
        return campaignService.getCampaign(id)
                .map(Result::success);
    }

    @GetMapping
    public Mono<Result<List<AdCampaign>>> list(
            @RequestParam(required = false) String advertiserId,
            @RequestParam(required = false) String status) {
        return campaignService.listCampaigns(advertiserId, status)
                .collectList()
                .map(Result::success);
    }

    @PutMapping("/{id}/pause")
    public Mono<Result<AdCampaign>> pause(@PathVariable Long id) {
        return campaignService.pauseCampaign(id)
                .map(Result::success);
    }

    @PutMapping("/{id}/activate")
    public Mono<Result<AdCampaign>> activate(@PathVariable Long id) {
        return campaignService.activateCampaign(id)
                .map(Result::success);
    }

    @GetMapping("/{id}/stats")
    public Mono<Result<CampaignStats>> stats(@PathVariable Long id) {
        return campaignService.getCampaignStats(id)
                .map(Result::success);
    }
}
