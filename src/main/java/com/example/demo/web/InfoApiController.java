package com.example.demo.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.info.RuntimeInfo;
import com.example.demo.info.RuntimeInfoService;

@RestController
@RequestMapping("/api")
public class InfoApiController {

    private final RuntimeInfoService runtimeInfoService;

    public InfoApiController(RuntimeInfoService runtimeInfoService) {
        this.runtimeInfoService = runtimeInfoService;
    }

    @GetMapping("/info")
    public RuntimeInfo info() {
        return runtimeInfoService.current();
    }
}
