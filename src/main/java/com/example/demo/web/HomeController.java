package com.example.demo.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.example.demo.info.RuntimeInfoService;

@Controller
public class HomeController {

    private final RuntimeInfoService runtimeInfoService;

    public HomeController(RuntimeInfoService runtimeInfoService) {
        this.runtimeInfoService = runtimeInfoService;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("info", runtimeInfoService.current());
        return "index";
    }
}
