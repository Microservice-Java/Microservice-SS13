package com.storex.report.controller;

import com.storex.report.dto.ReportResponse;
import com.storex.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/excel")
    public ResponseEntity<ReportResponse> downloadExcelReport(
            @RequestParam(defaultValue = "USER_001") String userId,
            @RequestParam(defaultValue = "SALES") String reportType) {

        ReportResponse response = reportService.generateExcelReport(userId, reportType);

        if (response.getHttpStatusCode() == 429) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(response);
        } else if (response.getHttpStatusCode() == 503) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        }

        return ResponseEntity.ok(response);
    }
}
