package com.wenxinblog.experiment.controller;

import com.wenxinblog.experiment.dto.AssignmentResponse;
import com.wenxinblog.experiment.dto.ExperimentRequest;
import com.wenxinblog.experiment.dto.ExperimentResponse;
import com.wenxinblog.experiment.dto.ExperimentResult;
import com.wenxinblog.experiment.service.ExperimentAnalyzer;
import com.wenxinblog.experiment.service.ExperimentManageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * 实验管理 + 分桶调试 REST 入口。直接返回 DTO（Spring 序列化），不做 Result 包裹。
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ExperimentController {

    private final ExperimentManageService manageService;
    private final ExperimentAnalyzer analyzer;

    @PostMapping("/experiments")
    public Mono<ExperimentResponse> create(@RequestBody ExperimentRequest req) {
        return manageService.create(req);
    }

    @GetMapping("/experiments")
    public Flux<ExperimentResponse> list(@RequestParam(required = false) String layer,
                                         @RequestParam(required = false) String status) {
        return manageService.list(layer, status);
    }

    @PostMapping("/experiments/{id}/start")
    public Mono<ExperimentResponse> start(@PathVariable UUID id) {
        return manageService.start(id);
    }

    @PostMapping("/experiments/{id}/stop")
    public Mono<ExperimentResponse> stop(@PathVariable UUID id) {
        return manageService.stop(id);
    }

    @GetMapping("/experiments/{id}/results")
    public Mono<ExperimentResult> results(@PathVariable UUID id) {
        return analyzer.analyze(id);
    }

    @GetMapping("/experiments/assign")
    public Mono<AssignmentResponse> assign(@RequestParam String userId, @RequestParam String layer) {
        return manageService.assign(userId, layer);
    }
}
