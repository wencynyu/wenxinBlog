package com.wenxinblog.ad.controller;

import com.wenxinblog.ad.dto.AdDecisionRequest;
import com.wenxinblog.ad.dto.AdDecisionResponse;
import com.wenxinblog.ad.dto.Result;
import com.wenxinblog.ad.service.AdDecisionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

// 经网关公开给前端（gateway ad-public 路由，无鉴权）；服务间直连也用同一路径
@RestController
@RequestMapping("/api/v1/ads")
@RequiredArgsConstructor
public class AdDecisionController {

    private final AdDecisionService adDecisionService;

    @PostMapping("/decision")
    public Mono<Result<List<AdDecisionResponse>>> decide(@RequestBody AdDecisionRequest request) {
        return adDecisionService.decide(request)
                .collectList()
                .map(Result::success);
    }
}
